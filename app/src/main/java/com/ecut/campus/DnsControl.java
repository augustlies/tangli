package com.ecut.campus;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.net.InetAddress;
import java.util.Locale;

/**
 * 拦截页 / 快捷开关 / 暂停恢复共用的开关逻辑。
 *
 * 只通过 Intent 启停 DnsFilterService，只读 DomainRules 的公开查询，不改这两个文件。
 */
public final class DnsControl {
    private DnsControl() {}

    private static final String SP = "dns_control";
    private static final String K_STARTED = "started_at";
    private static final String K_PAUSED_UNTIL = "paused_until";
    private static final int REQ_RESUME = 3301;
    private static final String CH_DNS = "dns_pause";

    public static boolean running() {
        return DnsFilterService.isRunning;
    }

    /** 已经拿到 VPN 授权时直接开；没授权返回 false，由调用方拉起授权页。 */
    public static boolean start(Context ctx) {
        if (VpnService.prepare(ctx) != null) return false;
        Intent svc = new Intent(ctx, DnsFilterService.class).setAction(DnsFilterService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(svc);
        else ctx.startService(svc);
        markStarted(ctx);
        return true;
    }

    /** 记下开启时间；手动开启也会取消正在进行的暂停。 */
    public static void markStarted(Context ctx) {
        sp(ctx).edit().putLong(K_STARTED, System.currentTimeMillis()).remove(K_PAUSED_UNTIL).apply();
        cancelResume(ctx);
        clearResumeNotice(ctx);
    }

    public static void stop(Context ctx) {
        ctx.startService(new Intent(ctx, DnsFilterService.class).setAction(DnsFilterService.ACTION_STOP));
        sp(ctx).edit().remove(K_STARTED).remove(K_PAUSED_UNTIL).apply();
        cancelResume(ctx);
        clearResumeNotice(ctx);
    }

    /** 暂停 N 分钟：先关，到点由闹钟拉起。 */
    public static void pause(Context ctx, int minutes) {
        ctx.startService(new Intent(ctx, DnsFilterService.class).setAction(DnsFilterService.ACTION_STOP));
        long until = System.currentTimeMillis() + minutes * 60_000L;
        sp(ctx).edit().remove(K_STARTED).putLong(K_PAUSED_UNTIL, until).apply();
        armResume(ctx, until);
    }

    private static void armResume(Context ctx, long at) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = resumeIntent(ctx);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else am.set(AlarmManager.RTC_WAKEUP, at, pi);
    }

    public static long pausedUntil(Context ctx) {
        long t = sp(ctx).getLong(K_PAUSED_UNTIL, 0);
        return t > System.currentTimeMillis() ? t : 0;
    }

    /**
     * 暂停时间已经过了、护盾却还没开回来（后台起服务被系统拒、通知被划掉、关机重启……）。
     * 这时界面要明说「还没恢复」，不能当作普通的关闭状态，让人以为护盾在工作。
     */
    public static boolean resumeOverdue(Context ctx) {
        long t = sp(ctx).getLong(K_PAUSED_UNTIL, 0);
        return t > 0 && t <= System.currentTimeMillis() && !running();
    }

    /** 通知权限没开时，到点恢复不了也没法提醒；暂停前提醒一句。 */
    public static boolean canNotify(Context ctx) {
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }

    /** 开着的话返回开了多久（毫秒）；不知道开启时间返回 -1。 */
    public static long uptimeMs(Context ctx) {
        if (!running()) return -1;
        long t = sp(ctx).getLong(K_STARTED, 0);
        return t <= 0 ? -1 : Math.max(0, System.currentTimeMillis() - t);
    }

    /** 「已守护 3 小时 12 分」这种文案。纯函数便于单测。 */
    static String uptimeLabel(long ms) {
        if (ms < 0) return "本地 DNS 拦截已开启";
        long min = ms / 60_000L;
        if (min < 1) return "刚刚开启";
        if (min < 60) return "已守护 " + min + " 分钟";
        long h = min / 60, m = min % 60;
        if (h < 24) return "已守护 " + h + " 小时" + (m > 0 ? " " + m + " 分" : "");
        return "已守护 " + (h / 24) + " 天 " + (h % 24) + " 小时";
    }

    static String pauseLabel(long until, long now) {
        long min = Math.max(1, (until - now + 59_999L) / 60_000L);
        return "已暂停，" + min + " 分钟后自动恢复";
    }

    private static PendingIntent resumeIntent(Context ctx) {
        Intent i = new Intent(ctx, ResumeReceiver.class);
        return PendingIntent.getBroadcast(ctx, REQ_RESUME, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelResume(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(resumeIntent(ctx));
    }

    private static android.content.SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ 自检 / 查域名

    public static final class Verdict {
        public final String title;
        public final String detail;
        public final boolean good;

        Verdict(String title, String detail, boolean good) {
            this.title = title;
            this.detail = detail;
            this.good = good;
        }
    }

    /** 把用户输入的网址整理成域名：去协议、路径、端口。 */
    static String normalizeHost(String input) {
        if (input == null) return "";
        String s = input.trim().toLowerCase(Locale.ROOT);
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        int cut = s.length();
        for (char c : new char[]{'/', '?', '#', ':'}) {
            int i = s.indexOf(c);
            if (i >= 0 && i < cut) cut = i;
        }
        s = s.substring(0, cut);
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** 查一个域名会怎么处理。会读规则（首次一两秒），别在主线程调。 */
    public static Verdict lookup(Context ctx, String input) {
        String host = normalizeHost(input);
        if (host.isEmpty() || !host.contains(".")) return new Verdict("这不像一个域名", "试试 example.com 这样的格式", false);
        DomainRules rules = DomainRules.get(ctx.getApplicationContext());
        boolean observe = Prefs.blockObserve(ctx);
        if (rules.shouldBlock(host, true) && !rules.shouldBlock(host, false)) {
            return observe
                    ? new Verdict("会被拦截 · 统计埋点", host + " 属于统计/埋点域名，当前会被拦掉", true)
                    : new Verdict("放行 · 统计埋点", host + " 是统计/埋点域名；打开下面的开关就会一起拦", false);
        }
        if (rules.shouldBlock(host, false)) {
            return new Verdict("会被拦截 · 广告", host + " 在广告规则里，解析会返回 0.0.0.0", true);
        }
        return new Verdict("放行", host + " 不在拦截规则里，或在业务白名单里", false);
    }

    /**
     * 一键自检：拿一个确定在规则里的广告域名去解析。
     * 拦住时系统会拿到 0.0.0.0（或干脆解析失败）。
     */
    public static Verdict selfTest(Context ctx) {
        if (!running()) return new Verdict("护盾没开", "先打开护盾再测试", false);
        DomainRules rules = DomainRules.get(ctx.getApplicationContext());
        String probe = null;
        for (String h : rules.blockSet()) {
            if (h != null && h.contains(".") && rules.shouldBlock(h, false)) { probe = h; break; }
        }
        if (probe == null) return new Verdict("没法测试", "规则还没加载出来", false);
        // 加一层随机前缀，躲开系统 DNS 缓存（规则按后缀匹配，子域名同样会被拦）
        String host = "t" + Long.toHexString(System.nanoTime() & 0xFFFFFL) + "." + probe;
        if (!rules.shouldBlock(host, false)) host = probe;
        try {
            InetAddress[] all = InetAddress.getAllByName(host);
            for (InetAddress a : all) {
                if (!a.isAnyLocalAddress() && !a.isLoopbackAddress()) {
                    return new Verdict("没拦住", "测试域名解析到了 " + a.getHostAddress()
                            + "。如果开了系统「私人 DNS」，它会绕过烫梨，关掉再试", false);
                }
            }
            return new Verdict("拦住了", "测试广告域名被解析成 0.0.0.0，护盾工作正常", true);
        } catch (java.net.UnknownHostException e) {
            return new Verdict("拦住了", "测试广告域名解析不出来，护盾工作正常", true);
        } catch (Exception e) {
            return new Verdict("测试失败", "网络异常：" + e.getClass().getSimpleName(), false);
        }
    }

    // ------------------------------------------------------------ 暂停到点恢复

    public static final class ResumeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (running()) {
                sp(context).edit().remove(K_PAUSED_UNTIL).apply();
                return;
            }
            boolean ok = false;
            try {
                ok = start(context); // 成功时 markStarted 会清掉暂停标记
            } catch (Exception ignored) {
                // Android 12+ 后台可能不许直接起前台服务：退回发一条常驻通知，点「立即恢复」就开
            }
            if (!ok) notifyResume(context); // 暂停标记保留，界面显示「暂停已结束，还没恢复」
        }
    }

    /** 关机重启会丢闹钟：开机后还在暂停就重新定闹钟，已经过点就提醒恢复。 */
    public static final class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
            long t = sp(context).getLong(K_PAUSED_UNTIL, 0);
            if (t <= 0) return;
            if (t > System.currentTimeMillis()) armResume(context, t);
            else if (!running()) notifyResume(context);
        }
    }

    /** 恢复成功（无论从哪儿开的）就把那条常驻通知收掉。 */
    static void clearResumeNotice(Context ctx) {
        NotificationManagerCompat.from(ctx).cancel(3303);
    }

    private static void notifyResume(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CH_DNS, "拦截暂停",
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(AppActions.EXTRA_ACTION, "dns")
                .putExtra(AppActions.EXTRA_VALUE, "start");
        PendingIntent pi = PendingIntent.getActivity(ctx, 3302, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH_DNS)
                .setSmallIcon(R.drawable.ic_nav_shield)
                .setContentTitle("暂停结束了，护盾还没开")
                .setContentText("系统没让烫梨在后台自动恢复，点一下立即恢复广告拦截")
                .setOngoing(true)        // 不恢复就一直在，不会被顺手划掉
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(R.drawable.ic_nav_shield, "立即恢复", pi)
                .setContentIntent(pi);
        try {
            NotificationManagerCompat.from(ctx).notify(3303, b.build());
        } catch (SecurityException ignored) {
        }
    }
}

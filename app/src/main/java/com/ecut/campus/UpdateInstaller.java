package com.ecut.campus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 应用内更新：依次从加速镜像 → 烫梨官网 → GitHub 直连下载新版 APK，
 * 校验 SHA-256（GitHub 给了的话）、包名、签名和版本号都对得上才交给系统安装。
 * 任何一项不对就换下一个来源；全部失败退回浏览器打开发布页。
 */
public final class UpdateInstaller {
    private UpdateInstaller() {}

    private static final String DIR = "updates";
    private static final String SITE = "https://tangli.sueexin.dev/download/tangli-";
    private static final String MIRROR = "https://ghfast.top/";

    static final class Source {
        final String label, url;
        Source(String label, String url) { this.label = label; this.url = url; }
    }

    static List<Source> sources(Updater.Release r) {
        List<Source> out = new ArrayList<>();
        String gh = r.apkUrl == null ? "" : r.apkUrl;
        if (gh.startsWith("https://github.com/")) out.add(new Source("加速镜像", MIRROR + gh));
        String ver = r.tag == null ? "" : r.tag.replaceFirst("^[vV]", "");
        if (!ver.isEmpty()) out.add(new Source("烫梨官网", SITE + ver + ".apk"));
        if (!gh.isEmpty()) out.add(new Source("GitHub", gh));
        return out;
    }

    private static File dir(Context c) {
        File d = new File(c.getCacheDir(), DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File target(Context c, Updater.Release r) {
        String safe = (r.tag == null ? "new" : r.tag).replaceAll("[^0-9A-Za-z._-]", "_");
        return new File(dir(c), "tangli-" + safe + ".apk");
    }

    /** 已经装上的版本留下的安装包、下到一半的文件都删掉。打开 App 时调用。 */
    public static void cleanup(Context c) {
        File d = new File(c.getCacheDir(), DIR);
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (!f.getName().endsWith(".apk")) { f.delete(); continue; }
            PackageInfo pi = archiveInfo(c, f);
            if (pi == null || versionCode(pi) <= BuildConfig.VERSION_CODE) f.delete();
        }
    }

    public static void start(Activity a, Updater.Release r) {
        File done = target(a, r);
        if (done.exists() && verify(a, done, r) == null) {
            install(a, done, r);
            return;
        }
        // 别的版本的旧包先清掉
        File[] old = dir(a).listFiles();
        if (old != null) for (File f : old) if (!f.equals(done)) f.delete();

        float dp = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(24 * dp);
        box.setPadding(p, Math.round(8 * dp), p, 0);
        LinearProgressIndicator bar = new LinearProgressIndicator(a);
        bar.setIndeterminate(true);
        bar.setMax(1000);
        box.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        TextView status = new TextView(a);
        status.setTextSize(13f);
        status.setTextColor(G.SUB);
        status.setPadding(0, Math.round(12 * dp), 0, 0);
        status.setText("正在连接…");
        box.addView(status);
        final boolean[] cancelled = {false};
        AlertDialog dlg = new AlertDialog.Builder(a)
                .setTitle("下载 " + r.tag)
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("取消", (d, w) -> cancelled[0] = true)
                .show();

        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String lastErr = "";
            for (Source s : sources(r)) {
                if (cancelled[0]) break;
                main.post(() -> {
                    bar.setIndeterminate(true);
                    status.setText("正在从" + s.label + "下载…");
                });
                File part = new File(dir(a), done.getName() + ".part");
                part.delete();
                String err = download(s, part, r.size, cancelled, (got, total) -> main.post(() -> {
                    if (total > 0) {
                        bar.setIndeterminate(false);
                        bar.setProgressCompat((int) (got * 1000 / total), false);
                    }
                    status.setText(String.format(Locale.US, "%s · %.1f / %s MB", s.label, got / 1048576.0,
                            total > 0 ? String.format(Locale.US, "%.1f", total / 1048576.0) : "?"));
                }));
                if (err == null) {
                    main.post(() -> status.setText("正在校验安装包…"));
                    err = verify(a, part, r);
                    if (err == null && part.renameTo(done)) {
                        main.post(() -> {
                            if (dlg.isShowing()) dlg.dismiss();
                            if (!a.isFinishing()) install(a, done, r);
                        });
                        return;
                    }
                    if (err == null) err = "保存失败";
                }
                part.delete();
                lastErr = s.label + "：" + err;
                DiagLog.log(a, "update", "下载失败 " + lastErr);
            }
            if (cancelled[0]) return;
            final String fe = lastErr;
            main.post(() -> {
                if (dlg.isShowing()) dlg.dismiss();
                if (a.isFinishing()) return;
                new AlertDialog.Builder(a)
                        .setTitle("没下载成功")
                        .setMessage((fe.isEmpty() ? "" : fe + "\n\n") + "可以去发布页用浏览器下载。")
                        .setNegativeButton("关闭", null)
                        .setPositiveButton("打开发布页", (d, w) -> Updater.open(a,
                                r.pageUrl == null || r.pageUrl.isEmpty() ? Updater.RELEASES_URL : r.pageUrl))
                        .show();
            });
        }, "update-download").start();
    }

    interface Progress { void on(long got, long total); }

    /** 成功返回 null，否则返回一句原因。 */
    static String download(Source s, File out, long expectSize, boolean[] cancelled, Progress cb) {
        HttpURLConnection c = null;
        try {
            String url = s.url;
            for (int hop = 0; hop < 6; hop++) { // 手动跟随跳转（GitHub → 存储域名）
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", "tangli-android");
                int code = c.getResponseCode();
                if (code / 100 == 3) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    if (loc == null) return "跳转地址为空";
                    url = new URL(new URL(url), loc).toString();
                    if (!url.startsWith("https://")) return "不安全的跳转";
                    continue;
                }
                if (code != 200) return "HTTP " + code;
                break;
            }
            long total = c.getContentLengthLong();
            if (total <= 0) total = expectSize;
            if (expectSize > 0 && total > 0 && total != expectSize) return "文件大小不对";
            long got = 0, lastTick = 0;
            try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(out)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancelled[0]) return "已取消";
                    fo.write(buf, 0, n);
                    got += n;
                    long now = System.currentTimeMillis();
                    if (now - lastTick > 150) { lastTick = now; cb.on(got, total); }
                    if (got > 200L * 1024 * 1024) return "文件太大";
                }
                fo.getFD().sync();
            }
            cb.on(got, total);
            if (total > 0 && got != total) return "下载不完整";
            return null;
        } catch (java.net.UnknownHostException e) {
            return "连不上";
        } catch (java.net.SocketTimeoutException e) {
            return "超时";
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** 校验通过返回 null。 */
    static String verify(Context c, File apk, Updater.Release r) {
        try {
            if (r.sha256 != null && !r.sha256.isEmpty() && !r.sha256.equalsIgnoreCase(sha256(apk))) {
                return "校验值不对（文件可能被改过）";
            }
            PackageInfo pi = archiveInfo(c, apk);
            if (pi == null) return "不是有效的安装包";
            if (!c.getPackageName().equals(pi.packageName)) return "不是烫梨的安装包";
            if (versionCode(pi) <= BuildConfig.VERSION_CODE) return "版本没有比现在新";
            PackageInfo me = Build.VERSION.SDK_INT >= 28
                    ? c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES)
                    : c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES);
            Set<String> mine = certs(me), theirs = certs(pi);
            if (mine.isEmpty() || !mine.equals(theirs)) return "签名和已安装的不一致";
            return null;
        } catch (Exception e) {
            return "校验出错";
        }
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo archiveInfo(Context c, File apk) {
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        return c.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), flags);
    }

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageInfo pi) {
        return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
    }

    @SuppressWarnings("deprecation")
    private static Set<String> certs(PackageInfo pi) throws Exception {
        Signature[] sigs;
        if (Build.VERSION.SDK_INT >= 28 && pi.signingInfo != null) {
            sigs = pi.signingInfo.hasMultipleSigners() ? pi.signingInfo.getApkContentsSigners()
                    : pi.signingInfo.getSigningCertificateHistory();
        } else {
            sigs = pi.signatures;
        }
        Set<String> out = new HashSet<>();
        if (sigs == null) return out;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (Signature s : sigs) out.add(hex(md.digest(s.toByteArray())));
        return out;
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.US, "%02x", x));
        return sb.toString();
    }

    private static void install(Activity a, File apk, Updater.Release r) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                    .setTitle("需要允许烫梨安装更新")
                    .setMessage("安装包已下载并校验好。系统要求先在设置里打开「允许安装未知应用」，"
                            + "打开后回来再点一次「更新」就会直接安装，不用重新下载。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("去打开", (d, w) -> {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName())));
                        } catch (Exception e) {
                            a.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                        }
                    })
                    .show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            Updater.open(a, r.pageUrl == null || r.pageUrl.isEmpty() ? Updater.RELEASES_URL : r.pageUrl);
        }
    }
}

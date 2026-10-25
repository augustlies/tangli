package com.ecut.campus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 仅接管 DNS 的 VpnService。
 *
 * 关键设计：路由只放 10.0.0.1/32，也就是「只有发往虚拟 DNS 的包进隧道」，
 * 其他流量（定位、上报、业务）完全走物理网卡，不受影响。
 * 这样就不需要实现 TCP/UDP 全转发（tun2socks），实现量和风险都大幅下降。
 */
public class DnsFilterService extends VpnService {

    private static final String TAG = "DnsFilter";
    public static final String ACTION_START = "com.ecut.campus.START_DNS";
    public static final String ACTION_STOP = "com.ecut.campus.STOP_DNS";

    private static final String VPN_ADDR = "10.0.0.2";
    private static final String VIRTUAL_DNS = "10.0.0.1";
    private static final int VPN_PREFIX = 32;

    // 上游 DNS：国内公共 DNS。也可后续做成可配置。
    private static final String UPSTREAM_PRIMARY = "223.5.5.5";
    private static final String UPSTREAM_SECONDARY = "119.29.29.29";
    private static final int DNS_PORT = 53;

    private static final String CHANNEL_ID = "dns_filter";
    private static final int NOTIF_ID = 1001;

    private Thread worker;
    private ParcelFileDescriptor tunFd;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public static volatile boolean isRunning = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopFilter();
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundSafely();
        startFilter();
        return START_STICKY;
    }

    private void startForegroundSafely() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "广告拦截", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification n = b.setContentTitle("广告拦截运行中")
                .setContentText("仅拦截 DNS 查询")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        try {
            startForeground(NOTIF_ID, n);
        } catch (Exception e) {
            Log.w(TAG, "startForeground failed", e);
        }
    }

    private void startFilter() {
        if (running.get()) return;

        Builder builder = new Builder();
        builder.setSession("Tangli DNS Filter");
        builder.addAddress(VPN_ADDR, VPN_PREFIX);
        builder.addDnsServer(VIRTUAL_DNS);
        // 关键：只把虚拟 DNS 地址本身接进 tun，其余流量不受影响
        builder.addRoute(VIRTUAL_DNS, VPN_PREFIX);
        // 不设 MTU 默认值，DNS 报文很小
        builder.setBlocking(true);

        try {
            ParcelFileDescriptor fd = builder.establish();
            if (fd == null) {
                Log.e(TAG, "establish() returned null (permission revoked?)");
                stopSelf();
                return;
            }
            tunFd = fd;
        } catch (Exception e) {
            Log.e(TAG, "establish failed", e);
            stopSelf();
            return;
        }

        running.set(true);
        isRunning = true;
        final DomainRules rules = DomainRules.get(this);
        final boolean blockObserve = Prefs.blockObserve(this);

        worker = new Thread(() -> pump(tunFd, rules, blockObserve), "dns-pump");
        worker.start();
        Log.i(TAG, "DNS filter started");
    }

    private void pump(ParcelFileDescriptor fd, DomainRules rules, boolean blockObserve) {
        FileInputStream in = new FileInputStream(fd.getFileDescriptor());
        FileOutputStream out = new FileOutputStream(fd.getFileDescriptor());
        byte[] buf = new byte[4096];

        while (running.get()) {
            int len;
            try {
                len = in.read(buf);   // blocking 模式
            } catch (Exception e) {
                if (running.get()) Log.e(TAG, "tun read error", e);
                break;
            }
            if (len <= 0) continue;

            // 只处理 IPv4 UDP 53（其他包理论不会进来，防御性跳过）
            int ipHeaderLen = (buf[0] & 0x0F) * 4;
            if (len < ipHeaderLen + 8) continue;
            int proto = buf[9] & 0xFF;
            if (proto != 17) continue;                        // UDP only
            int dstPort = ((buf[ipHeaderLen + 2] & 0xFF) << 8) | (buf[ipHeaderLen + 3] & 0xFF);
            if (dstPort != DNS_PORT) continue;

            byte[] dns = new byte[len - ipHeaderLen - 8];
            System.arraycopy(buf, ipHeaderLen + 8, dns, 0, dns.length);

            String host = DnsPacket.parseQueryName(dns, dns.length);
            int qtype = DnsPacket.parseQueryType(dns, dns.length);

            byte[] resp;
            if (host != null && rules.shouldBlock(host, blockObserve)) {
                resp = DnsPacket.buildBlockedResponse(dns, dns.length, qtype);
                Log.d(TAG, "BLOCK " + host + " type=" + qtype);
            } else {
                resp = forwardUpstream(dns, dns.length);
            }
            if (resp == null || resp.length == 0) continue;

            // 回写：重建 IP+UDP 头（直接交换源/目的）
            writeResponse(out, buf, ipHeaderLen, resp);
        }
    }

    private void writeResponse(FileOutputStream out, byte[] orig, int ipHeaderLen, byte[] payload) {
        try {
            byte[] pkt = new byte[ipHeaderLen + 8 + payload.length];
            System.arraycopy(orig, 0, pkt, 0, ipHeaderLen);
            // 交换 IP 源/目的
            System.arraycopy(orig, 16, pkt, 12, 4);   // src <- orig dst
            System.arraycopy(orig, 12, pkt, 16, 4);   // dst <- orig src
            pkt[8] = 64;                                // TTL
            int totalLen = ipHeaderLen + 8 + payload.length;
            pkt[2] = (byte) ((totalLen >> 8) & 0xFF);
            pkt[3] = (byte) (totalLen & 0xFF);
            pkt[10] = 0; pkt[11] = 0;
            int sum = checksum(pkt, 0, ipHeaderLen);
            pkt[10] = (byte) ((sum >> 8) & 0xFF);
            pkt[11] = (byte) (sum & 0xFF);

            // UDP 头
            int u = ipHeaderLen;
            pkt[u]     = orig[u + 2];   // srcPort <- orig dstPort (53)
            pkt[u + 1] = orig[u + 3];
            pkt[u + 2] = orig[u];       // dstPort <- orig srcPort
            pkt[u + 3] = orig[u + 1];
            int udpLen = 8 + payload.length;
            pkt[u + 4] = (byte) ((udpLen >> 8) & 0xFF);
            pkt[u + 5] = (byte) (udpLen & 0xFF);
            pkt[u + 6] = 0; pkt[u + 7] = 0;   // UDP checksum 可选，置 0
            System.arraycopy(payload, 0, pkt, u + 8, payload.length);

            out.write(pkt);
            out.flush();
        } catch (Exception e) {
            if (running.get()) Log.e(TAG, "write back failed", e);
        }
    }

    private static int checksum(byte[] data, int offset, int length) {
        long sum = 0;
        for (int i = 0; i < length; i += 2) {
            int hi = data[offset + i] & 0xFF;
            int lo = (i + 1 < length) ? (data[offset + i + 1] & 0xFF) : 0;
            sum += (hi << 8) | lo;
        }
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }

    private byte[] forwardUpstream(byte[] query, int len) {
        for (String up : new String[]{UPSTREAM_PRIMARY, UPSTREAM_SECONDARY}) {
            DatagramSocket sock = null;
            try {
                sock = new DatagramSocket();
                sock.setSoTimeout(2500);
                InetAddress addr = InetAddress.getByName(up);
                sock.send(new DatagramPacket(query, len, new InetSocketAddress(addr, DNS_PORT)));
                byte[] rb = new byte[4096];
                DatagramPacket rp = new DatagramPacket(rb, rb.length);
                sock.receive(rp);
                byte[] out = new byte[rp.getLength()];
                System.arraycopy(rp.getData(), rp.getOffset(), out, 0, rp.getLength());
                return out;
            } catch (Exception e) {
                Log.w(TAG, "upstream " + up + " failed: " + e.getMessage());
            } finally {
                if (sock != null) sock.close();
            }
        }
        return DnsPacket.buildServFail(query, len);
    }

    private void stopFilter() {
        running.set(false);
        isRunning = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        if (tunFd != null) {
            try { tunFd.close(); } catch (Exception ignored) {}
            tunFd = null;
        }
        try { stopForeground(true); } catch (Exception ignored) {}
        Log.i(TAG, "DNS filter stopped");
    }

    @Override
    public void onRevoke() {
        stopFilter();
        stopSelf();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopFilter();
        super.onDestroy();
    }
}

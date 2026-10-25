import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * DnsPacket 纯逻辑验证（不依赖 Android）。
 * 复制自 app 源码，仅去掉 package 声明以便直接 javac 编译。
 */
public class DnsPacketTest {

    // ===== 被测代码（与 app 中 DnsPacket 一致） =====
    static final int TYPE_A = 1;
    static final int TYPE_AAAA = 28;

    static String parseQueryName(byte[] buf, int len) {
        if (len < 12) return null;
        int qdcount = ((buf[4] & 0xFF) << 8) | (buf[5] & 0xFF);
        if (qdcount < 1) return null;
        StringBuilder sb = new StringBuilder(64);
        int pos = 12, safety = 0;
        while (pos < len) {
            int labelLen = buf[pos] & 0xFF;
            if (labelLen == 0) { pos++; break; }
            if ((labelLen & 0xC0) == 0xC0) return null;
            pos++;
            if (pos + labelLen > len) return null;
            if (sb.length() > 0) sb.append('.');
            for (int i = 0; i < labelLen; i++) sb.append((char) (buf[pos + i] & 0xFF));
            pos += labelLen;
            if (++safety > 128) return null;
        }
        String name = sb.toString().toLowerCase();
        return name.isEmpty() ? null : name;
    }

    static int skipQuestionName(byte[] buf, int len) {
        int pos = 12;
        while (pos < len) {
            int l = buf[pos] & 0xFF;
            if (l == 0) return pos + 1;
            if ((l & 0xC0) == 0xC0) return pos + 2;
            pos += 1 + l;
        }
        return -1;
    }

    static int parseQueryType(byte[] buf, int len) {
        int pos = skipQuestionName(buf, len);
        if (pos < 0 || pos + 2 > len) return -1;
        return ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
    }

    static byte[] buildBlockedResponse(byte[] query, int queryLen, int qtype) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(128);
        out.write(query[0]); out.write(query[1]);
        out.write(0x81); out.write(0x80);
        out.write(0x00); out.write(0x01);
        int answerCount = (qtype == TYPE_A || qtype == TYPE_AAAA) ? 1 : 0;
        out.write(0x00); out.write(answerCount);
        out.write(0x00); out.write(0x00);
        out.write(0x00); out.write(0x00);
        int qEnd = skipQuestionName(query, queryLen);
        if (qEnd < 0 || qEnd + 4 > queryLen) return null;
        int qEndFull = qEnd + 4;
        writeBytes(out, query, 12, qEndFull - 12);
        if (answerCount == 0) return out.toByteArray();
        out.write(0xC0); out.write(0x0C);
        out.write(0x00); out.write((byte) qtype);
        out.write(0x00); out.write(0x01);
        out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x3C);
        if (qtype == TYPE_A) {
            out.write(0x00); out.write(0x04);
            out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x00);
        } else {
            out.write(0x00); out.write(0x10);
            for (int i = 0; i < 16; i++) out.write(0x00);
        }
        return out.toByteArray();
    }

    static void writeBytes(ByteArrayOutputStream out, byte[] src, int off, int len) {
        for (int i = 0; i < len; i++) out.write(src[off + i] & 0xFF);
    }

    // ===== 测试辅助 =====
    static byte[] buildQuery(int id, String domain, int qtype) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write((id >> 8) & 0xFF); o.write(id & 0xFF);
        o.write(0x01); o.write(0x00);   // flags: RD
        o.write(0x00); o.write(0x01);   // QDCOUNT
        o.write(0x00); o.write(0x00);
        o.write(0x00); o.write(0x00);
        o.write(0x00); o.write(0x00);
        for (String label : domain.split("\\.")) {
            byte[] b = label.getBytes(StandardCharsets.US_ASCII);
            o.write(b.length);
            o.write(b, 0, b.length);
        }
        o.write(0x00);
        o.write((qtype >> 8) & 0xFF); o.write(qtype & 0xFF);
        o.write(0x00); o.write(0x01);   // CLASS IN
        return o.toByteArray();
    }

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("  PASS  " + name); }
        else { fail++; System.out.println("  FAIL  " + name + "   -> " + detail); }
    }

    public static void main(String[] args) {
        System.out.println("=== DnsPacket 逻辑测试 ===\n");

        // 1. 域名解析
        byte[] q = buildQuery(0x1234, "adservice.sigmob.cn", TYPE_A);
        String name = parseQueryName(q, q.length);
        check("解析 sigmob 域名", "adservice.sigmob.cn".equals(name), String.valueOf(name));

        byte[] q2 = buildQuery(0x5678, "adiu.amap.com", TYPE_A);
        check("解析 amap 域名", "adiu.amap.com".equals(parseQueryName(q2, q2.length)),
                String.valueOf(parseQueryName(q2, q2.length)));

        byte[] q3 = buildQuery(0x9ABC, "a.b.c.d.e.f.example.org", TYPE_A);
        check("解析多级域名", "a.b.c.d.e.f.example.org".equals(parseQueryName(q3, q3.length)),
                String.valueOf(parseQueryName(q3, q3.length)));

        // 2. qtype 解析
        check("解析 A 记录类型", parseQueryType(q, q.length) == 1,
                String.valueOf(parseQueryType(q, q.length)));
        byte[] qAAAA = buildQuery(0x1111, "adservice.sigmob.cn", TYPE_AAAA);
        check("解析 AAAA 记录类型", parseQueryType(qAAAA, qAAAA.length) == 28,
                String.valueOf(parseQueryType(qAAAA, qAAAA.length)));

        // 3. 构造拦截响应
        byte[] resp = buildBlockedResponse(q, q.length, TYPE_A);
        check("响应非空", resp != null && resp.length > 0, "null");
        check("响应 ID 与查询一致",
                ((resp[0] & 0xFF) << 8 | (resp[1] & 0xFF)) == 0x1234,
                Integer.toHexString(((resp[0] & 0xFF) << 8 | (resp[1] & 0xFF))));
        check("QR 位置 1 (响应)", (resp[2] & 0x80) != 0, Integer.toHexString(resp[2] & 0xFF));
        check("ANCOUNT = 1", ((resp[6] & 0xFF) << 8 | (resp[7] & 0xFF)) == 1,
                String.valueOf(((resp[6] & 0xFF) << 8 | (resp[7] & 0xFF))));
        check("RDLENGTH = 4 (A 记录)", (resp[resp.length - 6] & 0xFF) == 0 && (resp[resp.length - 5] & 0xFF) == 4,
                "got " + (resp[resp.length - 6] & 0xFF) + "," + (resp[resp.length - 5] & 0xFF));
        // 最后 4 字节应为 0.0.0.0
        boolean zero = true;
        for (int i = resp.length - 4; i < resp.length; i++) if (resp[i] != 0) zero = false;
        check("A 记录返回 0.0.0.0", zero, Arrays.toString(Arrays.copyOfRange(resp, resp.length - 4, resp.length)));

        // 4. AAAA 响应
        byte[] resp6 = buildBlockedResponse(qAAAA, qAAAA.length, TYPE_AAAA);
        check("AAAA 响应 RDLENGTH = 16",
                (resp6[resp6.length - 18] & 0xFF) == 0 && (resp6[resp6.length - 17] & 0xFF) == 16,
                "len=" + resp6.length);

        // 5. 畸形输入不崩
        byte[] tiny = new byte[5];
        check("过短报文返回 null", parseQueryName(tiny, tiny.length) == null, "not null");
        check("过短报文 qtype = -1", parseQueryType(tiny, tiny.length) == -1,
                String.valueOf(parseQueryType(tiny, tiny.length)));

        System.out.println("\n=== 结果: " + pass + " passed, " + fail + " failed ===");
        if (fail > 0) System.exit(1);
    }
}

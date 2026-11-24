package com.ecut.campus.data;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

import static org.junit.Assert.*;

public class TimetableShareBoundsTest {
    @Test public void compressedExpansionIsCappedAndOrdinarySharesStillDecode() throws Exception {
        Timetable timetable = new Timetable();
        Timetable.Course course = new Timetable.Course();
        course.name = "脱敏课程";
        Timetable.Session session = new Timetable.Session();
        session.dayOfWeek = 1;
        session.startSection = 1;
        session.endSection = 2;
        session.weeks.add(99);
        course.sessions.add(session);
        timetable.courses.add(course);
        String code = TimetableShare.encode(timetable, "张三");
        assertNotNull(TimetableShare.decode(code));
        assertEquals(Integer.valueOf(99), TimetableShare.decode(code).timetable.courses.get(0)
                .sessions.get(0).weeks.get(0));

        JSONObject oversizedSchedule = new JSONObject().put("f", "08:00").put("n", 17);
        JSONArray values = new JSONArray();
        for (int i = 0; i < 17; i++) values.put(45);
        oversizedSchedule.put("d", values).put("b", values);
        assertNull(TimetableShare.decode(TimetableShare.encode(new Timetable(), "张三", oversizedSchedule)));

        assertNull(TimetableShare.decode(TimetableShare.PREFIX
                + compress("[".repeat(1000) + "0" + "]".repeat(1000))));

        String bomb = "{\"o\":\"" + "x".repeat(TimetableStore.MAX_IMPORT_CHARS + 1024)
                + "\",\"s\":\"\",\"d\":\"\",\"c\":[]}";
        String hostileCode = TimetableShare.PREFIX + compress(bomb);
        assertNull(TimetableShare.decode(hostileCode));
    }

    private static String compress(String rawText) throws Exception {
        byte[] raw = rawText.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(raw);
        deflater.finish();
        byte[] packed = new byte[raw.length];
        int n = deflater.deflate(packed);
        deflater.end();
        assertTrue(n < raw.length);
        return TimetableShare.B64.encode(java.util.Arrays.copyOf(packed, n));
    }
}

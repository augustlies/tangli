package com.ecut.campus.fun;

import android.app.Activity;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.ecut.campus.FriendStore;
import com.ecut.campus.Prefs;
import com.ecut.campus.ScheduleSettings;
import com.ecut.campus.ScheduleSettingsStore;
import com.ecut.campus.data.GradesStore;
import com.ecut.campus.data.PairStats;
import com.ecut.campus.data.SemesterPersona;
import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.TimetableStats;
import com.ecut.campus.data.TimetableStore;

import java.util.List;

/** 把本地数据攒成卡片：学期人设卡、双人课表海报。只读本机课表/成绩/同学分享码，不联网。 */
public final class FunCards {
    private FunCards() {}

    public static void showPersona(Activity a) {
        Timetable t = TimetableStore.load(a);
        ScheduleSettings ss = ScheduleSettingsStore.load(a);
        int[][] times = times(ss, sectionsFor(ss, t, null));
        Double gpa = SemesterPersona.gpa(GradesStore.load(a));
        SemesterPersona.Card card = SemesterPersona.pick(
                SemesterPersona.stats(t, TimetableStats.busiestWeek(t), times[0], times[1], gpa));
        String sem = t.semester != null ? t.semester.name : "";
        CardPreview.show(a, CardPainter.personaCard(card, sem), "tangli-persona");
    }

    /** 选一个同学 → 双人海报。没有同学时提示去添加。 */
    public static void pickFriend(Activity a) {
        List<FriendStore.Friend> friends = FriendStore.load(a);
        if (friends.isEmpty()) {
            Toast.makeText(a, "先在「设置 → 同学」里加一个同学的课表", Toast.LENGTH_LONG).show();
            return;
        }
        if (friends.size() == 1) {
            showPair(a, friends.get(0));
            return;
        }
        String[] names = new String[friends.size()];
        for (int i = 0; i < names.length; i++) names[i] = friends.get(i).name;
        new AlertDialog.Builder(a).setTitle("和谁一起做海报")
                .setItems(names, (d, w) -> showPair(a, friends.get(w)))
                .setNegativeButton("取消", null)
                .show();
    }

    public static void showPair(Activity a, FriendStore.Friend f) {
        Timetable me = TimetableStore.load(a);
        Timetable them = f.shared.timetable != null ? f.shared.timetable : new Timetable();
        ScheduleSettings ss = ScheduleSettingsStore.load(a);
        CardPainter.PairInput in = new CardPainter.PairInput();
        in.me = me;
        in.them = them;
        String myName = Prefs.shareName(a);
        in.meName = myName.isEmpty() ? "我" : myName;
        in.themName = f.name;
        in.sections = sectionsFor(ss, me, them);
        int week = TimetableStats.busiestWeek(me.courses.isEmpty() ? them : me);
        in.days = hasWeekend(me, week) || hasWeekend(them, week) ? 7 : 5;
        int[][] times = times(ss, in.sections);
        in.stats = PairStats.compute(me, them, week, in.days, times[0], times[1]);
        CardPreview.show(a, CardPainter.pairPoster(in), "tangli-duo");
    }

    /** {开始分钟[], 结束分钟[]}，下标 1..sections。 */
    private static int[][] times(ScheduleSettings ss, int sections) {
        int[] st = new int[sections + 1], en = new int[sections + 1];
        for (int i = 1; i <= sections; i++) {
            st[i] = ss.startMinutes(i);
            en[i] = ss.endMinutes(i);
        }
        return new int[][]{st, en};
    }

    private static int sectionsFor(ScheduleSettings ss, Timetable a, Timetable b) {
        int n = Math.max(ss.sectionCount(), ScheduleSettings.maxSectionUsed(a));
        if (b != null) n = Math.max(n, ScheduleSettings.maxSectionUsed(b));
        return Math.min(ScheduleSettings.MAX_SECTIONS, Math.max(1, n));
    }

    private static boolean hasWeekend(Timetable t, int week) {
        if (t == null) return false;
        for (Timetable.Course c : t.courses)
            for (Timetable.Session s : c.sessions)
                if (s.dayOfWeek >= 6 && s.isActive(week)) return true;
        return false;
    }
}

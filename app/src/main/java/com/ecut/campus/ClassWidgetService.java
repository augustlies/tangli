package com.ecut.campus;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import androidx.core.content.ContextCompat;

import com.ecut.campus.data.WidgetDay;

import java.util.ArrayList;
import java.util.List;

/** 小组件「今日课程」里那一列课程条。内容由 {@link ClassWidget#card} 算，和标题同一份。 */
public final class ClassWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext(), intent.getData());
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context c;
        private final int cap, level, theme, chipAlpha;
        private long builtAt;
        private int nowMin;
        private List<WidgetDay.Chip> chips = new ArrayList<>();

        Factory(Context c, Uri data) {
            this.c = c;
            this.cap = intParam(data, "cap", 2);
            this.level = intParam(data, "w", WidgetDay.WIDE);
            this.theme = intParam(data, "theme", ClassWidget.THEME_SYSTEM);
            this.chipAlpha = Math.max(0, Math.min(255, intParam(data, "ca", 255))); // 有背景图时课程条半透明
        }

        private static int intParam(Uri u, String k, int def) {
            try {
                String v = u == null ? null : u.getQueryParameter(k);
                return v == null ? def : Integer.parseInt(v);
            } catch (Exception e) {
                return def;
            }
        }

        @Override public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            try {
                builtAt = System.currentTimeMillis();
                nowMin = ClassPlans.nowMinutes(builtAt);
                chips = new ArrayList<>(ClassWidget.card(c, builtAt, cap, level).chips);
            } catch (Exception e) {
                chips = new ArrayList<>();
            }
        }

        @Override public void onDestroy() { chips.clear(); }

        @Override public int getCount() { return chips.size(); }

        @Override
        public RemoteViews getViewAt(int position) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_class_item);
            if (position < 0 || position >= chips.size()) return v;
            WidgetDay.Chip ch = chips.get(position);
            int idx = Math.abs(ch.colorIndex) % ClassWidget.CHIP.length;
            v.setTextViewText(R.id.wi_name, ch.name);
            v.setTextViewText(R.id.wi_line, ch.line);
            v.setViewVisibility(R.id.wi_line, ch.line.isEmpty() ? View.GONE : View.VISIBLE);
            countdown(v, ch);
            v.setTextViewText(R.id.wi_tag, ch.tag);
            v.setViewVisibility(R.id.wi_tag, ch.tag.isEmpty() ? View.GONE : View.VISIBLE);
            if (theme == ClassWidget.THEME_SYSTEM) {
                v.setImageViewResource(R.id.wi_bg, ClassWidget.CHIP[idx]);
            } else {
                Context tc = ClassWidget.themed(c, theme);
                v.setImageViewResource(R.id.wi_bg, R.drawable.w_chip_shape);
                v.setInt(R.id.wi_bg, "setColorFilter", ContextCompat.getColor(tc, ClassWidget.PIG[idx]));
                v.setTextColor(R.id.wi_name, ContextCompat.getColor(tc, R.color.w_ink));
                v.setTextColor(R.id.wi_line, ContextCompat.getColor(tc, R.color.w_chip_sub));
                v.setTextColor(R.id.wi_tag, ContextCompat.getColor(tc, R.color.w_accent));
                v.setTextColor(R.id.wi_cd_live, ContextCompat.getColor(tc, R.color.w_accent));
                v.setTextColor(R.id.wi_cd_soon, ContextCompat.getColor(tc, R.color.w_chip_sub));
            }
            if (chipAlpha < 255) v.setInt(R.id.wi_bg, "setImageAlpha", chipAlpha);
            v.setOnClickFillInIntent(R.id.wi_root, new Intent());
            return v;
        }

        /**
         * 倒计时：Chronometer 倒着走（API 24+），基准 = 开机时钟 + 离下课/上课还有多久。到点后由小组件的边界闹钟翻页。
         * 进行中「还剩 mm:ss」（超过 1 小时是 h:mm:ss）朱砂色；下一门 60 分钟内「mm:ss 后上课」。没有倒计时两个都藏起来。
         */
        private void countdown(RemoteViews v, WidgetDay.Chip ch) {
            v.setViewVisibility(R.id.wi_cd_live, View.GONE);
            v.setViewVisibility(R.id.wi_cd_soon, View.GONE);
            v.setChronometer(R.id.wi_cd_live, SystemClock.elapsedRealtime(), null, false);
            v.setChronometer(R.id.wi_cd_soon, SystemClock.elapsedRealtime(), null, false);
            if (ch.cdKind == WidgetDay.Chip.CD_NONE || ch.cdMin < 0) return;
            if (Build.VERSION.SDK_INT < 24) { // 不会走秒：写成静态「还剩 34 分钟」接在时间后面
                String t = WidgetDay.countdownText(ch.cdKind, ch.cdMin, nowMin);
                v.setTextViewText(R.id.wi_line, ch.line.isEmpty() ? t : ch.line + "  " + t);
                v.setViewVisibility(R.id.wi_line, View.VISIBLE);
                return;
            }
            boolean live = ch.cdKind == WidgetDay.Chip.CD_LEFT;
            int id = live ? R.id.wi_cd_live : R.id.wi_cd_soon;
            long target = ClassPlans.at(ClassPlans.midnight(builtAt), ch.cdMin);
            long base = SystemClock.elapsedRealtime() + Math.max(0L, target - System.currentTimeMillis());
            v.setChronometer(id, base, live ? "还剩 %s" : "%s 后上课", true);
            v.setChronometerCountDown(id, true);
            v.setViewVisibility(id, View.VISIBLE);
        }

        @Override public RemoteViews getLoadingView() { return null; }

        @Override public int getViewTypeCount() { return 1; }

        @Override public long getItemId(int position) { return position; }

        @Override public boolean hasStableIds() { return false; }
    }
}

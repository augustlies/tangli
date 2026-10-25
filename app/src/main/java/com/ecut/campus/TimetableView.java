package com.ecut.campus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.animation.ValueAnimator;
import android.view.animation.PathInterpolator;

import androidx.annotation.Nullable;

import com.ecut.campus.data.Timetable;
import com.ecut.campus.data.SemesterMetaPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * 原生课表网格：10 节、一屏 8 节、固定双节块、七天/五天列、左右滑周。
 *
 * 手势方向锁：横向滑动切周，纵向滑动查看第 9-10 节，互不抢占。
 */
public class TimetableView extends View {

    public interface OnSessionClickListener {
        void onSessionClick(Timetable.Course course, Timetable.Session session, boolean activeThisWeek);
        void onEmptySlotClick(int dayOfWeek, int startSection);
        void onWeekSwipe(int delta);
        /** 松手判定翻页的瞬间回调，用于立即更新周数标题。 */
        default void onWeekSettling(int delta) {}
    }

    public interface OnVerticalScrollListener {
        void onVerticalScrollEnd();
    }

    /** 12 色轮转色板（背景色）。同名课程共享同色，不同课程名按序取未占用色。 */
    public static final int[] PALETTE_BG = {
            0xFFEFE2CC, 0xFFDDE5D6, 0xFFF0D9CF, 0xFFDCE1E6,
            0xFFE4DEEC, 0xFFD6E6E3, 0xFFF1DCDF, 0xFFE4E2DC,
            0xFFE9E3C9, 0xFFD9E0D0, 0xFFE8D5CC, 0xFFD8DCE4
    };

    /** 12 色轮转色板（文字与强调色） */
    public static final int[] PALETTE_FG = {
            0xFF6B4A1E, 0xFF3E5236, 0xFF7A3A24, 0xFF384756,
            0xFF4E4364, 0xFF2D5650, 0xFF7A3A47, 0xFF4F4D46,
            0xFF5C5222, 0xFF46553A, 0xFF6E3E2C, 0xFF3D4558
    };

    private static final int[] LIGHT_BG = PALETTE_BG.clone();
    private static final int[] LIGHT_FG = PALETTE_FG.clone();

    /** 深色模式下把暖色课块换成「深底 + 浅字」，色相保持一致。 */
    public static void applyPalette(boolean night) {
        for (int k = 0; k < PALETTE_BG.length; k++) {
            if (night) {
                PALETTE_BG[k] = G.mix(LIGHT_FG[k], 0xFF0E0E0D, 0.70f);
                PALETTE_FG[k] = G.mix(LIGHT_BG[k], 0xFFFFFFFF, 0.10f);
            } else {
                PALETTE_BG[k] = LIGHT_BG[k];
                PALETTE_FG[k] = LIGHT_FG[k];
            }
        }
    }

    /** 朱砂：只用于「现在」——今天、现在线、冲突角标。 */
    private final int ACCENT = G.ACCENT;
    private final int INK = G.INK;
    private final int INK_2 = G.SUB;
    private final int INK_3 = G.MUTE;
    private final int PAPER = G.CARD;
    private final int HAIRLINE = G.HAIR;

    private static final String[] DAY_NAMES = {"一", "二", "三", "四", "五", "六", "日"};
    private static final int TOTAL_SECTIONS = 10;
    private static final int VISIBLE_SECTIONS = 8;

    private Timetable timetable = new Timetable();
    private ScheduleSettings schedule = ScheduleSettings.createDefault();
    private String semesterStartDate = "";
    private int currentWeek = 1;
    private int totalWeeks = 30;
    private int displayDays = 7;
    private int todayDayOfWeek = 1;
    private boolean showInactiveCourses = false;
    private OnSessionClickListener listener;
    private OnVerticalScrollListener verticalScrollListener;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pageEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final List<HitBlock> hitBlocks = new ArrayList<>();
    private final int touchSlop;

    private float headerHeight;
    private float timeColWidth;
    private float rowHeight;
    private float scrollOffset;
    private float maxScrollOffset;
    private float bottomInset;

    private float downX;
    private float downY;
    private float lastY;
    private float dragDx;
    private int gestureMode = 0; // 0=未判定 1=横向切周 2=纵向滚动
    private boolean moved;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private VelocityTracker velocityTracker;
    private ValueAnimator pageAnimator;
    private boolean pageAnimating;
    /** 正在吸附、尚未提交的翻页方向；0 表示无。快速连续滑动时由新手势立即提交。 */
    private int pendingCommit;

    private static final class HitBlock {
        final RectF rect;
        final Timetable.Course course;
        final Timetable.Session session;
        final boolean active;

        HitBlock(RectF rect, Timetable.Course course, Timetable.Session session, boolean active) {
            this.rect = rect;
            this.course = course;
            this.session = session;
            this.active = active;
        }
    }

    public TimetableView(Context context) {
        this(context, null);
    }

    public TimetableView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        headerHeight = dp(52);
        timeColWidth = dp(48);
        rowHeight = dp(54);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        gridPaint.setColor(HAIRLINE);
        gridPaint.setStrokeWidth(Math.max(1f, dp(0.6f)));
        gridPaint.setStyle(Paint.Style.STROKE);

        pageEdgePaint.setStyle(Paint.Style.FILL);

        todayDayOfWeek = WeekMath.todayDayOfWeek();
        setWillNotDraw(false);
    }

    public void setSchedule(ScheduleSettings settings) {
        this.schedule = settings == null ? ScheduleSettings.createDefault() : settings;
        invalidate();
    }

    public void setSemesterStartDate(String startDate) {
        this.semesterStartDate = startDate == null ? "" : startDate;
        invalidate();
    }

    public void setDisplayDays(int days) {
        this.displayDays = days == 5 ? 5 : 7;
        invalidate();
    }

    public int getDisplayDays() {
        return displayDays;
    }

    public void setTimetable(Timetable t, int week) {
        this.timetable = (t != null) ? t : new Timetable();
        this.currentWeek = clampWeek(week);
        this.todayDayOfWeek = WeekMath.todayDayOfWeek();
        cancelPageAnimation();
        dragDx = 0;
        pageAnimating = false;
        requestLayout();
        invalidate();
    }

    public void setCurrentWeek(int week) {
        this.currentWeek = clampWeek(week);
        cancelPageAnimation();
        dragDx = 0;
        invalidate();
    }

    public int getCurrentWeek() {
        return currentWeek;
    }

    public void setTotalWeeks(int weeks) {
        this.totalWeeks = Math.max(1, Math.min(WeekMath.OPEN_WEEKS_MAX, weeks));
        this.currentWeek = clampWeek(currentWeek);
        invalidate();
    }

    public int getTotalWeeks() {
        return totalWeeks;
    }

    private int clampWeek(int week) {
        return Math.max(1, Math.min(totalWeeks, week));
    }

    public void setShowInactiveCourses(boolean show) {
        this.showInactiveCourses = show;
        invalidate();
    }

    public boolean isShowInactiveCourses() {
        return showInactiveCourses;
    }

    public void setOnSessionClickListener(OnSessionClickListener l) {
        this.listener = l;
    }

    public void setOnVerticalScrollListener(OnVerticalScrollListener l) {
        this.verticalScrollListener = l;
    }

    /** Dock 覆盖区域高度：课表背景仍延伸其下，但可视 8 节与滚动范围避开遮挡。 */
    public void setBottomInset(float px) {
        this.bottomInset = Math.max(0, px);
        if (getHeight() > 0) updateRowHeight(getHeight());
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateRowHeight(h);
    }

    private void updateRowHeight(int h) {
        float available = Math.max(dp(240), h - headerHeight - bottomInset);
        rowHeight = available / VISIBLE_SECTIONS;
        maxScrollOffset = Math.max(0, rowHeight * (TOTAL_SECTIONS - VISIBLE_SECTIONS));
        scrollOffset = Math.max(0, Math.min(maxScrollOffset, scrollOffset));
    }

    private float sectionTopY(int section) {
        return headerHeight + (section - 1) * rowHeight - scrollOffset;
    }

    private float sectionBottomY(int section) {
        return headerHeight + section * rowHeight - scrollOffset;
    }

    private int columnCount() {
        return displayDays;
    }

    private float columnWidth(int w) {
        return (w - timeColWidth) / (float) columnCount();
    }

    private float pageWidth() {
        return getWidth();
    }

    private int adjacentWeek(int direction) {
        return clampWeek(currentWeek + direction);
    }

    private boolean canSwipe(int direction) {
        int adjacent = adjacentWeek(direction);
        return direction != 0 && adjacent != currentWeek;
    }

    /**
     * 边缘周也保留约 18% 的跟手余量，松手回弹。
     * 这样手指仍有物理反馈，但不会拖出空白页。
     */
    private float resistDrag(float dx) {
        float width = pageWidth();
        if (width <= 0) return 0;
        if ((dx < 0 && !canSwipe(1)) || (dx > 0 && !canSwipe(-1))) {
            return dx * 0.18f;
        }
        float limit = width * 1.08f;
        return Math.max(-limit, Math.min(limit, dx));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        hitBlocks.clear();

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (rowHeight <= 0) updateRowHeight(h);

        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(G.CARD);
        canvas.drawRect(0, 0, w, h, bgPaint);

        int direction = dragDx < 0 ? 1 : (dragDx > 0 ? -1 : 0);
        int neighbor = direction == 0 ? currentWeek : adjacentWeek(direction);
        boolean drawNeighbor = direction != 0 && neighbor != currentWeek;
        float offset = dragDx;

        // 先画当前页，再画从侧边进入的相邻周。时间轴跟随整页移动。
        drawPage(canvas, currentWeek, offset, true);
        if (drawNeighbor) {
            float neighborOffset = offset + direction * pageWidth();
            drawPage(canvas, neighbor, neighborOffset, false);
            drawPageEdge(canvas, direction, neighborOffset, h);
        }
    }

    /** 翻页接缝：一条细边加短距离渐变，明确这是两页，而不是两张网格叠在一起。 */
    private void drawPageEdge(Canvas canvas, int direction, float neighborOffset, int h) {
        float edgeX = direction > 0 ? neighborOffset : neighborOffset + pageWidth();
        float shadowWidth = dp(12);
        float shadowStart = direction > 0 ? edgeX - shadowWidth : edgeX;
        float shadowEnd = direction > 0 ? edgeX : edgeX + shadowWidth;

        pageEdgePaint.setShader(new LinearGradient(
                shadowStart, 0, shadowEnd, 0,
                direction > 0
                        ? new int[]{0x00000000, G.HAIR}
                        : new int[]{G.HAIR, 0x00000000},
                null, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(Math.min(shadowStart, shadowEnd), headerHeight,
                Math.max(shadowStart, shadowEnd), h, pageEdgePaint);
        pageEdgePaint.setShader(null);

        pageEdgePaint.setColor(G.alpha(G.INK, 0x1F));
        canvas.drawRect(edgeX - dp(0.45f), 0, edgeX + dp(0.45f), h, pageEdgePaint);
    }

    private void drawPage(Canvas canvas, int week, float xOffset, boolean isPrimary) {
        int w = getWidth();
        int h = getHeight();
        float colWidth = columnWidth(w);
        int savedWeek = currentWeek;
        List<HitBlock> savedHits = isPrimary ? null : new ArrayList<>(hitBlocks);
        if (!isPrimary) hitBlocks.clear();
        currentWeek = week;

        canvas.save();
        canvas.translate(xOffset, 0);
        canvas.clipRect(-xOffset, 0, w - xOffset, h);

        drawTodayColumn(canvas, colWidth, h);
        canvas.save();
        canvas.clipRect(0, headerHeight, w, h);
        drawTimeColumnAndGrid(canvas, w, colWidth);
        if (showInactiveCourses) drawCourseBlocks(canvas, colWidth, false);
        drawCourseBlocks(canvas, colWidth, true);
        canvas.restore();
        drawWeekHeader(canvas, colWidth, w);
        if (isPrimary) drawScrollHint(canvas, w, h);

        canvas.restore();
        currentWeek = savedWeek;
        if (!isPrimary) {
            hitBlocks.clear();
            if (savedHits != null) hitBlocks.addAll(savedHits);
        }
    }

    private void drawTodayColumn(Canvas canvas, float colWidth, int h) {
        if (!isViewingRealCurrentWeek()) return;
        if (todayDayOfWeek < 1 || todayDayOfWeek > columnCount()) return;
        float tx = timeColWidth + (todayDayOfWeek - 1) * colWidth;
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0x0AFF4A1C);
        canvas.drawRect(tx, headerHeight, tx + colWidth, h, bgPaint);
    }

    private boolean isViewingRealCurrentWeek() {
        if (semesterStartDate == null || semesterStartDate.isEmpty()) return false;
        return currentWeek == WeekMath.currentWeek(semesterStartDate, 30);
    }

    private void drawWeekHeader(Canvas canvas, float colWidth, int w) {
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(G.CARD);
        canvas.drawRect(0, 0, w, headerHeight, bgPaint);

        String[] dateLabels = WeekMath.weekDates(semesterStartDate, currentWeek);

        subTextPaint.setColor(G.MUTE);
        subTextPaint.setTextSize(sp(9.5f));
        subTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        subTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("节次", timeColWidth / 2f, headerHeight / 2f + dp(4), subTextPaint);

        boolean highlightToday = isViewingRealCurrentWeek();
        for (int d = 1; d <= columnCount(); d++) {
            float cx = timeColWidth + (d - 0.5f) * colWidth;
            boolean isToday = highlightToday && d == todayDayOfWeek;

            if (isToday) {
                badgePaint.setColor(ACCENT);
                canvas.drawRect(cx - dp(9), headerHeight - dp(2.5f), cx + dp(9), headerHeight, badgePaint);
            }

            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(sp(12.5f));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(isToday ? ACCENT : INK);
            canvas.drawText("周" + DAY_NAMES[d - 1], cx, dp(23), textPaint);

            subTextPaint.setTypeface(Typeface.MONOSPACE);
            subTextPaint.setTextSize(sp(9.5f));
            subTextPaint.setTextAlign(Paint.Align.CENTER);
            subTextPaint.setColor(isToday ? ACCENT : INK_3);
            canvas.drawText(dateLabels[d - 1], cx, dp(39), subTextPaint);
        }

        canvas.drawLine(0, headerHeight, w, headerHeight, gridPaint);
    }

    private void drawTimeColumnAndGrid(Canvas canvas, int w, float colWidth) {
        for (int sec = 1; sec <= TOTAL_SECTIONS; sec++) {
            float top = sectionTopY(sec);
            float bottom = sectionBottomY(sec);
            float cy = (top + bottom) / 2f;

            if (bottom > headerHeight && top < getHeight()) {
                textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                textPaint.setTextSize(sp(11.5f));
                textPaint.setColor(G.INK);
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(String.valueOf(sec), timeColWidth / 2f, cy - dp(9), textPaint);

                subTextPaint.setTypeface(Typeface.MONOSPACE);
                subTextPaint.setTextSize(sp(7.5f));
                subTextPaint.setColor(G.MUTE);
                subTextPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(schedule.startTime(sec), timeColWidth / 2f, cy + dp(2), subTextPaint);
                canvas.drawText(schedule.endTime(sec), timeColWidth / 2f, cy + dp(11), subTextPaint);
            }

            canvas.drawLine(timeColWidth, bottom, w, bottom, gridPaint);
        }

        for (int d = 0; d <= columnCount(); d++) {
            float x = timeColWidth + d * colWidth;
            canvas.drawLine(x, headerHeight, x, getHeight(), gridPaint);
        }
    }

    private void drawCourseBlocks(Canvas canvas, float colWidth, boolean activePass) {
        if (timetable == null || timetable.courses == null) return;

        // 本周课程按真实起止位置（节次或自定义时间）算重叠；重叠的一簇按列并排，全部可点。
        java.util.Map<Timetable.Session, int[]> laneOf = new java.util.HashMap<>();
        List<List<Timetable.Session>> perDay = new ArrayList<>();
        for (int d = 0; d <= 7; d++) perDay.add(new ArrayList<>());
        for (Timetable.Course c : timetable.courses) {
            for (Timetable.Session s : c.sessions) {
                if (s.dayOfWeek < 1 || s.dayOfWeek > columnCount()) continue;
                if (s.isActive(currentWeek)) perDay.get(s.dayOfWeek).add(s);
            }
        }
        for (int d = 1; d <= 7; d++) assignLanes(perDay.get(d), laneOf);

        float padH = dp(1.6f);
        float padV = dp(1.6f);
        float radius = dp(3f);

        for (Timetable.Course course : timetable.courses) {
            int colorIdx = Math.abs(course.colorIndex) % PALETTE_BG.length;
            int bgColor = PALETTE_BG[colorIdx];
            int fgColor = PALETTE_FG[colorIdx];

            for (Timetable.Session s : course.sessions) {
                if (s.dayOfWeek < 1 || s.dayOfWeek > columnCount()) continue;
                boolean isActive = s.isActive(currentWeek);
                if (isActive != activePass) continue;

                float[] pos = position(s);

                if (!isActive) {
                    boolean overlapped = false;
                    for (Timetable.Session a : perDay.get(s.dayOfWeek)) {
                        float[] p = position(a);
                        if (p[0] < pos[1] - 0.01f && pos[0] < p[1] - 0.01f) { overlapped = true; break; }
                    }
                    if (overlapped) continue;
                }

                int lane = 0;
                int lanes = 1;
                if (isActive) {
                    int[] l = laneOf.get(s);
                    if (l != null) { lane = l[0]; lanes = l[1]; }
                }

                float cellLeft = timeColWidth + (s.dayOfWeek - 1) * colWidth;
                float laneW = colWidth / lanes;
                float left = cellLeft + lane * laneW + padH;
                float right = cellLeft + (lane + 1) * laneW - padH;
                float top = posY(pos[0]) + padV;
                float bottom = posY(pos[1]) - padV;
                RectF rect = new RectF(left, top, right, bottom);
                if (rect.bottom <= headerHeight || rect.top >= getHeight()) continue;

                if (isActive) {
                    blockPaint.setStyle(Paint.Style.FILL);
                    blockPaint.setColor(bgColor);
                    canvas.drawRoundRect(rect, radius, radius, blockPaint);

                    // 发丝书脊：左侧 2dp 实色
                    accentPaint.setStyle(Paint.Style.FILL);
                    accentPaint.setColor(fgColor);
                    canvas.drawRect(left, top, left + dp(2f), bottom, accentPaint);

                    if (lanes > 1) {
                        // 冲突角标：朱砂小方块 + 数量
                        float bs = dp(11);
                        RectF badge = new RectF(right - bs - dp(2), top + dp(2), right - dp(2), top + dp(2) + bs);
                        badgePaint.setColor(ACCENT);
                        canvas.drawRoundRect(badge, dp(2), dp(2), badgePaint);
                        subTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                        subTextPaint.setTextSize(sp(7.5f));
                        subTextPaint.setColor(PAPER);
                        subTextPaint.setAlpha(255);
                        subTextPaint.setTextAlign(Paint.Align.CENTER);
                        canvas.drawText(String.valueOf(lanes), badge.centerX(), badge.centerY() + dp(2.7f), subTextPaint);
                    }
                } else {
                    blockPaint.setStyle(Paint.Style.STROKE);
                    blockPaint.setStrokeWidth(Math.max(1f, dp(0.7f)));
                    blockPaint.setColor(G.alpha(G.INK, 0x24));
                    blockPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(3), dp(2.5f)}, 0));
                    canvas.drawRoundRect(rect, radius, radius, blockPaint);
                    blockPaint.setPathEffect(null);
                }

                drawBlockText(canvas, rect, course, s, isActive, isActive ? fgColor : INK_3);
                hitBlocks.add(new HitBlock(rect, course, s, isActive));
            }
        }
        if (activePass) drawNowLine(canvas, colWidth);
    }

    /** 第 p 节边界（0 = 第 1 节顶部）对应的 y。 */
    private float posY(float p) {
        return headerHeight + p * rowHeight - scrollOffset;
    }

    /**
     * 时段在网格里的起止位置，单位「节」（第 1 节顶部为 0）。
     * 普通课按真实节次（可以是单节、三节）；自定义时间按作息表换算，不必对齐节次。
     */
    private float[] position(Timetable.Session s) {
        if (s.hasCustomTime()) {
            float a = minutesToPos(Timetable.Session.minutesOf(s.timeStart));
            float b = minutesToPos(Timetable.Session.minutesOf(s.timeEnd));
            if (b - a < 0.35f) b = Math.min(TOTAL_SECTIONS, a + 0.35f);  // 太短也要能点到
            return new float[]{a, b};
        }
        int st = Math.max(1, Math.min(TOTAL_SECTIONS, s.startSection));
        int en = Math.max(st, Math.min(TOTAL_SECTIONS, s.endSection));
        return new float[]{st - 1, en};
    }

    /** 某个时刻落在网格的哪里：节内按比例，课间压到相邻边界上。 */
    private float minutesToPos(int m) {
        for (int sec = 1; sec <= TOTAL_SECTIONS; sec++) {
            int st = schedule.startMinutes(sec);
            int en = schedule.endMinutes(sec);
            if (m <= st) return sec - 1;
            if (m <= en) return sec - 1 + (m - st) / (float) Math.max(1, en - st);
        }
        return TOTAL_SECTIONS;
    }

    /** 同一天的时段按重叠分簇，簇内贪心分列；laneOf[s] = {第几列, 共几列}。 */
    private void assignLanes(List<Timetable.Session> list, java.util.Map<Timetable.Session, int[]> laneOf) {
        if (list.isEmpty()) return;
        List<Timetable.Session> sorted = new ArrayList<>(list);
        sorted.sort((x, y) -> Float.compare(position(x)[0], position(y)[0]));
        List<Timetable.Session> cluster = new ArrayList<>();
        List<Float> laneEnds = new ArrayList<>();
        float clusterEnd = -1;
        for (Timetable.Session s : sorted) {
            float[] p = position(s);
            if (!cluster.isEmpty() && p[0] >= clusterEnd - 0.01f) {
                for (Timetable.Session c : cluster) laneOf.get(c)[1] = laneEnds.size();
                cluster.clear();
                laneEnds.clear();
            }
            int lane = -1;
            for (int i = 0; i < laneEnds.size(); i++) {
                if (laneEnds.get(i) <= p[0] + 0.01f) { lane = i; break; }
            }
            if (lane < 0) { lane = laneEnds.size(); laneEnds.add(p[1]); } else laneEnds.set(lane, p[1]);
            laneOf.put(s, new int[]{lane, 1});
            cluster.add(s);
            clusterEnd = Math.max(clusterEnd, p[1]);
        }
        for (Timetable.Session c : cluster) laneOf.get(c)[1] = laneEnds.size();
    }

    /** 现在线：今天这一列里，按作息时间画一条朱砂发丝线 + 圆点。 */
    private void drawNowLine(Canvas canvas, float colWidth) {
        if (!isViewingRealCurrentWeek()) return;
        if (todayDayOfWeek < 1 || todayDayOfWeek > columnCount()) return;
        java.util.Calendar now = java.util.Calendar.getInstance();
        int m = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
        float y = -1;
        for (int sec = 1; sec <= TOTAL_SECTIONS; sec++) {
            int st = schedule.startMinutes(sec);
            int en = schedule.endMinutes(sec);
            float top = sectionTopY(sec);
            float bottom = sectionBottomY(sec);
            if (m < st) { y = top; break; }
            if (m <= en) {
                y = top + (bottom - top) * (m - st) / (float) Math.max(1, en - st);
                break;
            }
        }
        if (y < 0 || y <= headerHeight) return;
        float x0 = timeColWidth + (todayDayOfWeek - 1) * colWidth;
        accentPaint.setStyle(Paint.Style.FILL);
        accentPaint.setColor(ACCENT);
        canvas.drawRect(x0, y - dp(0.6f), x0 + colWidth, y + dp(0.6f), accentPaint);
        canvas.drawCircle(x0 + dp(1), y, dp(3), accentPaint);
    }

    private void drawBlockText(Canvas canvas, RectF rect, Timetable.Course course,
                               Timetable.Session s, boolean isActive, int textColor) {
        float innerLeft = rect.left + (isActive ? dp(6.2f) : dp(4.0f));
        float innerRight = rect.right - dp(2.2f);
        float innerWidth = Math.max(dp(16), innerRight - innerLeft);
        float innerTop = rect.top + dp(5f);
        float innerBottom = rect.bottom - dp(4f);

        canvas.save();
        canvas.clipRect(rect.left + dp(2), Math.max(rect.top + dp(2), headerHeight),
                rect.right - dp(2), rect.bottom - dp(2));

        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(sp(10.0f));
        textPaint.setColor(textColor);
        textPaint.setTextAlign(Paint.Align.LEFT);

        String title = course.name != null ? course.name : "";
        if (!isActive) title = "[非本周] " + title;

        StaticLayout titleLayout = StaticLayout.Builder
                .obtain(title, 0, title.length(), textPaint, Math.round(innerWidth))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.02f)
                .setIncludePad(false)
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();

        canvas.translate(innerLeft, innerTop);
        titleLayout.draw(canvas);

        float usedHeight = titleLayout.getHeight();

        // 自定义时间（如考试）直接写在课程名下面
        if (s.hasCustomTime() && innerTop + usedHeight + dp(12) <= innerBottom) {
            subTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            subTextPaint.setTextSize(sp(8.2f));
            subTextPaint.setColor(textColor);
            subTextPaint.setAlpha(isActive ? 235 : 165);
            subTextPaint.setTextAlign(Paint.Align.LEFT);
            String tl = s.timeStart + "–" + s.timeEnd;
            canvas.translate(0, usedHeight + dp(3f));
            StaticLayout timeLayout = StaticLayout.Builder
                    .obtain(tl, 0, tl.length(), subTextPaint, Math.round(innerWidth))
                    .setIncludePad(false).setMaxLines(2).build();
            timeLayout.draw(canvas);
            canvas.translate(0, -(usedHeight + dp(3f)));
            usedHeight += dp(3f) + timeLayout.getHeight();
        }

        // 教师名紧随课程名；按周换老师的课显示本周那位。为空时完全跳过。
        String teacher = s.teacherFor(currentWeek, course.teacher).trim();
        if (!teacher.isEmpty() && innerTop + usedHeight + dp(12) <= innerBottom) {
            subTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            subTextPaint.setTextSize(sp(8.2f));
            subTextPaint.setColor(textColor);
            subTextPaint.setAlpha(isActive ? 215 : 165);
            subTextPaint.setTextAlign(Paint.Align.LEFT);

            canvas.translate(0, usedHeight + dp(3f));
            StaticLayout teacherLayout = StaticLayout.Builder
                    .obtain(teacher, 0, teacher.length(), subTextPaint, Math.round(innerWidth))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.0f)
                    .setIncludePad(false)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build();
            teacherLayout.draw(canvas);
            usedHeight += dp(3f) + teacherLayout.getHeight();
        }

        String loc = (s.location != null && !s.location.isEmpty()) ? ("@" + s.location) : "";
        if (!loc.isEmpty() && innerTop + usedHeight + dp(12) <= innerBottom) {
            subTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            subTextPaint.setTextSize(sp(8.2f));
            subTextPaint.setColor(textColor);
            subTextPaint.setAlpha(isActive ? 215 : 165);
            subTextPaint.setTextAlign(Paint.Align.LEFT);

            canvas.translate(0, usedHeight + dp(3f));
            StaticLayout locLayout = StaticLayout.Builder
                    .obtain(loc, 0, loc.length(), subTextPaint, Math.round(innerWidth))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.0f)
                    .setIncludePad(false)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build();
            locLayout.draw(canvas);
        }

        canvas.restore();
    }

    private void drawScrollHint(Canvas canvas, int w, int h) {
        if (maxScrollOffset <= 0) return;
        float trackTop = headerHeight + dp(4);
        float trackBottom = h - dp(4);
        float trackHeight = trackBottom - trackTop;
        float thumbHeight = Math.max(dp(24), trackHeight * VISIBLE_SECTIONS / TOTAL_SECTIONS);
        float progress = maxScrollOffset <= 0 ? 0 : scrollOffset / maxScrollOffset;
        float thumbTop = trackTop + (trackHeight - thumbHeight) * progress;

        blockPaint.setStyle(Paint.Style.FILL);
        blockPaint.setColor(G.STROKE);
        float x = w - dp(3);
        canvas.drawRoundRect(new RectF(x - dp(1.5f), thumbTop, x + dp(1.5f), thumbTop + thumbHeight),
                dp(2), dp(2), blockPaint);
    }

    private void settlePage(int direction, float velocityX) {
        if (direction == 0 || !canSwipe(direction)) {
            animatePageTo(0f, direction, false);
            return;
        }

        float width = Math.max(1f, pageWidth());
        boolean commit = shouldCommitPage(dragDx, velocityX, width);
        if (commit && listener != null) listener.onWeekSettling(direction);
        animatePageTo(commit ? -direction * width : 0f, direction, commit);
    }

    private void animatePageTo(float target, int direction, boolean commit) {
        cancelPageAnimation();
        final float start = dragDx;
        final float distance = Math.abs(target - start);
        pageAnimating = true;
        pendingCommit = commit ? direction : 0;
        pageAnimator = ValueAnimator.ofFloat(0f, 1f);
        pageAnimator.setDuration(pageAnimatorDuration(distance, pageWidth()));
        pageAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        pageAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            dragDx = start + (target - start) * fraction;
            invalidate();
        });
        pageAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                pageAnimating = false;
                pageAnimator = null;
                pendingCommit = 0;
                dragDx = 0f;
                invalidate();
                if (commit && listener != null) listener.onWeekSwipe(direction);
            }
        });
        pageAnimator.start();
    }

    private void cancelPageAnimation() {
        if (pageAnimator != null) {
            pageAnimator.removeAllListeners();
            pageAnimator.cancel();
            pageAnimator = null;
        }
        pageAnimating = false;
        pendingCommit = 0;
    }

    /** 吸附动画还没走完又开始新手势：立刻提交上一次翻页，避免周数被吞掉。 */
    private void flushPendingCommit() {
        int d = pendingCommit;
        cancelPageAnimation();
        if (d != 0) {
            dragDx = 0f;
            if (listener != null) listener.onWeekSwipe(d);
        }
    }

    static long pageAnimatorDuration(float distance, float pageWidth) {
        if (distance <= 0f || pageWidth <= 0f) return 0L;
        float fraction = Math.max(0f, Math.min(1f, distance / pageWidth));
        return Math.round(140L + 120L * Math.sqrt(fraction));
    }

    static boolean shouldCommitPage(float dx, float velocityX, float pageWidth) {
        if (dx == 0f || pageWidth <= 0f) return false;
        int direction = dx < 0f ? -1 : 1;
        float projected = dx + velocityX * 0.11f;
        boolean sameDirection = projected != 0f && Math.signum(projected) == direction;
        return sameDirection && Math.abs(projected) >= pageWidth * 0.22f;
    }

    private static int pairStart(int section) {
        int s = Math.max(1, Math.min(TOTAL_SECTIONS, section));
        return (s % 2 == 1) ? s : s - 1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (pageAnimating) flushPendingCommit();
                activePointerId = event.getPointerId(0);
                downX = event.getX();
                downY = event.getY();
                lastY = downY;
                // 从吸附动画中途接管时，保留当前页位置，继续跟手。
                gestureMode = 0;
                moved = false;
                return true;

            case MotionEvent.ACTION_MOVE: {
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex < 0) return true;
                float dx = event.getX(pointerIndex) - downX;
                float dy = event.getY(pointerIndex) - downY;
                if (gestureMode == 0) {
                    if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        gestureMode = 1;
                        moved = true;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    } else if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                        gestureMode = 2;
                        moved = true;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (gestureMode == 1) {
                    dragDx = resistDrag(dx);
                    invalidate();
                } else if (gestureMode == 2) {
                    float y = event.getY(pointerIndex);
                    scrollOffset = Math.max(0, Math.min(maxScrollOffset, scrollOffset - (y - lastY)));
                    lastY = y;
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                if (gestureMode == 2 && verticalScrollListener != null) {
                    verticalScrollListener.onVerticalScrollEnd();
                }
                if (gestureMode == 1) {
                    velocityTracker.computeCurrentVelocity(1000, dp(6000));
                    float velocityX = velocityTracker.getXVelocity(activePointerId);
                    settlePage(dragDx < 0 ? 1 : (dragDx > 0 ? -1 : 0), velocityX);
                } else if (!moved) {
                    handleTap(event.getX(), event.getY());
                }
                finishGesture();
                gestureMode = 0;
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                if (gestureMode == 2 && verticalScrollListener != null) {
                    verticalScrollListener.onVerticalScrollEnd();
                }
                if (gestureMode == 1) settlePage(dragDx < 0 ? 1 : (dragDx > 0 ? -1 : 0), 0f);
                finishGesture();
                gestureMode = 0;
                return true;

            case MotionEvent.ACTION_POINTER_UP: {
                int upIndex = event.getActionIndex();
                if (event.getPointerId(upIndex) == activePointerId) {
                    int newIndex = upIndex == 0 ? 1 : 0;
                    activePointerId = event.getPointerId(newIndex);
                    downX = event.getX(newIndex) - dragDx;
                    downY = event.getY(newIndex);
                    lastY = downY;
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void finishGesture() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        activePointerId = MotionEvent.INVALID_POINTER_ID;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelPageAnimation();
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        super.onDetachedFromWindow();
    }

    private void handleTap(float x, float y) {
        if (listener == null) return;
        if (y < headerHeight) return;

        for (int i = hitBlocks.size() - 1; i >= 0; i--) {
            HitBlock b = hitBlocks.get(i);
            if (b.rect.contains(x, y)) {
                listener.onSessionClick(b.course, b.session, b.active);
                return;
            }
        }

        if (x > timeColWidth) {
            float colWidth = columnWidth(getWidth());
            int day = Math.min(columnCount(), Math.max(1, (int) ((x - timeColWidth) / colWidth) + 1));
            for (int sec = 1; sec <= TOTAL_SECTIONS; sec++) {
                if (y >= sectionTopY(sec) && y <= sectionBottomY(sec)) {
                    listener.onEmptySlotClick(day, sec);
                    return;
                }
            }
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}

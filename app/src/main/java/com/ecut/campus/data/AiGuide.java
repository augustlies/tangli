package com.ecut.campus.data;

/** 「复制给 AI 的说明」：格式说明 + 当前周次/作息 + 现有课表。任何 AI 读完就能写烫梨指令。 */
public final class AiGuide {

    private AiGuide() {}

    /**
     * @param sectionTimes 作息，一行一节，如「第1节 08:00-08:45」，可空
     */
    public static String build(Timetable t, TangliCommand.Env env, String sectionTimes) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是「烫梨」课表 App 的助手。请把我接下来说的要求（或我发的课表截图、文字）改写成「烫梨指令」。\n")
          .append("只输出指令本身，放在一个代码块里，不要解释。我复制后打开烫梨会自动识别，确认后才生效。\n\n");
        sb.append("【格式】\n")
          .append("第一行写：烫梨指令 v1\n")
          .append("（要整张重建课表时第一行写「烫梨指令 v1 整表」，会先清空现有课程）\n")
          .append("之后一行一条，字段用 | 分隔：\n")
          .append("+ 课程名 | 周三 | 3-4节 | 1-16周 | 地点 | 老师　　添加课（周次、地点、老师可省略，省略周次=整学期）\n")
          .append("+ 课程名 | 周三 | 14:30-16:30 | 第10周 | 地点　　考试、讲座等不按节次的安排，写真实时间\n")
          .append("- 课程名 | 周五 | 1-2节　　删除这门课这个时段；只写课程名=整门删除；再写周次=只删这些周\n")
          .append("停 课程名 | 第10周 | 周五 | 1-2节　　某一周停一次课\n")
          .append("调 课程名 | 第10周 | 周五 | 1-2节 -> 第11周 | 周六 | 3-4节 | 新地点　　某一次课调到别的时间\n")
          .append("假 2027-01-01 至 2027-01-03 | 元旦　　这几天放假，课自动置灰\n")
          .append("补 2027-01-04 | 2027-01-01　　这天补课，上第二个日期那天的课\n")
          .append("开学 2026-09-07　　第 1 周的周一\n\n");
        sb.append("【规则】\n")
          .append("- 星期写 周一…周日；节次写 3-4节 或 第5节；周次写 1-16周、1-15周(单)、2,4,6周、第10周\n")
          .append("- 「下周三」「这周五」「11月5日」这类说法，请按下面的当前信息换算成第几周、周几\n")
          .append("- 删除、停课、调课时，课程名必须和【现有课表】里的一字不差\n")
          .append("- 停课、调课一行只写一周；跨多周就写多行\n")
          .append("- 看不清或不确定的信息宁可省略，不要编造\n\n");
        sb.append("【当前信息】\n");
        if (Dates.valid(env.today)) {
            sb.append("今天：").append(env.today).append("（").append(Dates.cnDay(Dates.dayOfWeek(env.today))).append("）");
            if (Dates.valid(env.start)) {
                int w = Dates.weekOf(env.start, env.today);
                sb.append(w >= 1 ? "，本周是第 " + w + " 周" : "，还没开学");
            }
            sb.append('\n');
        }
        if (Dates.valid(env.start)) sb.append("第 1 周周一：").append(env.start).append('\n');
        else sb.append("还没设开学日期（知道的话请加一行「开学 yyyy-MM-dd」）\n");
        if (sectionTimes != null && !sectionTimes.trim().isEmpty()) {
            sb.append("作息：\n").append(sectionTimes.trim()).append('\n');
        }
        sb.append("\n【现有课表】\n");
        String cur = TangliCommand.export(t, false);
        sb.append(cur.trim().isEmpty() ? "（空）\n" : cur);
        sb.append("\n我的要求：");
        return sb.toString();
    }
}

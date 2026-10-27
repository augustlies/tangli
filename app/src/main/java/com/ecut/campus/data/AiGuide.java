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
          .append("开学 2026-09-07　　第 1 周的周一\n")
          .append("备注 课程名 | 周三前交第三章作业　　给课加备注（写「清空」删掉）\n")
          .append("设置 主题=深色 | 显示周末=否　　可改：主题(跟随系统/浅色/深色)、显示周末、显示非本周课程、成绩提醒、自动检查更新(开/关)\n")
          .append("作息 第1节 08:00-08:45 | 第2节 08:55-09:40　　改上课时间，后面的节次按原课间顺延\n")
          .append("同步 课表 / 同步 成绩 / 导出 诊断日志 / 打开 校园邮箱、图书馆、成绩、校历、地图　　让烫梨做一个动作\n\n");
        sb.append("【规则】\n")
          .append("- 星期写 周一…周日；节次写 3-4节 或 第5节；周次写 1-16周、1-15周(单)、2,4,6周、第10周\n")
          .append("- 「下周三」「这周五」「11月5日」这类说法，请按下面的当前信息换算成第几周、周几\n")
          .append("- 删除、停课、调课时，课程名必须和【现有课表】里的一字不差\n")
          .append("- 停课、调课一行只写一周；跨多周就写多行\n")
          .append("- 看不清或不确定的信息宁可省略，不要编造\n")
          .append("- 我说要「链接」时，把整段指令做 URL 编码，接在 tangli://cmd?t= 后面给我，点一下就能打开\n\n");
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

    /**
     * 「交给 AI 读」：烫梨读不了的教务页面，把页面文字（已去掉姓名学号）接在说明后面，
     * 让 AI 用「整表」指令把整张课表重建出来。
     */
    public static String buildFromPage(Timetable t, TangliCommand.Env env, String sectionTimes, String pageText) {
        return build(t, env, sectionTimes)
                + "下面是我学校教务网站课表页面上的文字（烫梨已去掉姓名、学号）。"
                + "请用「烫梨指令 v1 整表」把里面的所有课程重建出来：课程名、星期、节次、周次、地点、老师都照页面写；"
                + "页面上能看出每节课的上下课时间就加「作息」行，能看出开学日期就加「开学」行；"
                + "看不懂的格子宁可跳过，不要猜。\n\n"
                + "【课表页面】\n" + (pageText == null ? "" : pageText.trim()) + "\n";
    }
}

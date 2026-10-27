package com.ecut.campus.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把教务课表页面的文字整理成发给 AI 的样子：去空行、去重复，
 * 并抹掉姓名、学号、身份证号、手机号这类和课表无关的个人信息。纯 Java，方便单测。
 */
public final class PageTextForAi {

    private PageTextForAi() {}

    public static final int MAX_CHARS = 16000;
    static final String MASK = "＊";

    private static final Pattern LABELED = Pattern.compile(
            "(姓\\s*名|学\\s*号|学生|考生号|身份证号?|证件号|手机号?|电话|联系方式|账号|用户名?)(\\s*[:：]\\s*)([^\\s|，,；;]{1,24})");
    private static final Pattern GREETING = Pattern.compile(
            "(欢迎(?:您|你)?|您好|你好)(\\s*[，,：:、]?\\s*)([\\u4e00-\\u9fa5·]{2,6})(同学|老师)?");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern LONG_DIGITS = Pattern.compile("(?<![\\d.:-])\\d{8,}(?![\\d.:-])");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

    public static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\r", "").replace('\u00a0', ' ').replace('\u3000', ' ');
        StringBuilder out = new StringBuilder();
        String last = null;
        for (String line : s.split("\\n")) {
            String l = line.replaceAll("[ \\t]+", " ").trim();
            if (l.isEmpty() || l.equals(last)) continue;
            last = l;
            out.append(mask(l)).append('\n');
            if (out.length() > MAX_CHARS) {
                out.setLength(MAX_CHARS);
                out.append("\n…（后面太长，省略了）\n");
                break;
            }
        }
        return out.toString().trim();
    }

    static String mask(String line) {
        String l = replaceGroup(LABELED, line, 3);
        l = replaceGroup(GREETING, l, 3);
        l = ID_CARD.matcher(l).replaceAll(MASK);
        l = PHONE.matcher(l).replaceAll(MASK);
        l = EMAIL.matcher(l).replaceAll(MASK);
        l = LONG_DIGITS.matcher(l).replaceAll(MASK);
        return l;
    }

    private static String replaceGroup(Pattern p, String s, int group) {
        Matcher m = p.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String whole = m.group();
            int a = m.start(group) - m.start(), b = m.end(group) - m.start();
            String rep = whole.substring(0, a) + MASK + whole.substring(b);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** WebView 里跑的取字脚本：优先按表格逐行输出（格子用 | 分开），再补上页面正文。 */
    public static String collectJs() {
        return "(function(){try{var out=[];"
                + "function cell(c){return (c.innerText||c.textContent||'').replace(/\\s*\\n\\s*/g,' / ').trim();}"
                + "function grab(d){if(!d||!d.body)return;var ts=d.querySelectorAll('table');var tl=0;"
                + "for(var i=0;i<ts.length;i++){var rs=ts[i].rows;if(!rs||rs.length<2)continue;var buf=[];"
                + "for(var r=0;r<rs.length;r++){var cs=rs[r].cells,row=[];for(var k=0;k<cs.length;k++)row.push(cell(cs[k]));"
                + "var line=row.join(' | ');if(line.replace(/[|\\s]/g,'').length)buf.push(line);}"
                + "if(buf.length){out.push('[表格]');out=out.concat(buf);tl+=buf.join('').length;}}"
                + "if(tl<300){out.push('[页面文字]');out.push((d.body.innerText||'').substring(0,12000));}}"
                + "grab(document);"
                + "for(var f=0;f<window.frames.length;f++){try{grab(window.frames[f].document);}catch(e){}}"
                + "return out.join('\\n').substring(0,60000);}catch(e){return '';}})()";
    }
}

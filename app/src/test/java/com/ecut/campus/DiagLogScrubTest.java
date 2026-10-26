package com.ecut.campus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/** 诊断日志脱敏：查询参数、路径里的 jsessionid、长数字。 */
public class DiagLogScrubTest {

    @Test
    public void stripsJsessionidAndQuery() {
        String out = DiagLog.scrub("finished: http://jw.example.edu.cn/jsxsd/xskb/xskb_list.do;jsessionid=AB12CD34EF?x=1");
        assertFalse(out, out.contains("AB12CD34EF"));
        assertEquals("finished: http://jw.example.edu.cn/jsxsd/xskb/xskb_list.do;…?…", out);
        assertEquals("page: https://h/jwglxt/xtgl/index.html?…", DiagLog.scrub("page: https://h/jwglxt/xtgl/index.html?gnmkdm=N1&su=2023123456"));
    }
}

package com.ecut.campus.data;

/** Shared Zhengfang jqGrid protocol. Read DOM, never getGridParam('data'). */
public final class GradeGridQuery {
    private GradeGridQuery() {}

    public static String triggerJs() {
        return "(function(){try{var $=window.jQuery,g=$&&$('#tabGrid');"
                + "if(!g||!g.length||!g.jqGrid)return 'WAIT';"
                + "var t=g[0];if(t.grid&&t.grid.hDiv.loading)return 'WAIT';"
                + "var s={done:false,phase:0};window.__tangliGradeQuery=s;"
                + "g.off('.tangliGrades');"
                + "g.on('jqGridBeforeRequest.tangliGrades',function(){s.done=false;});"
                + "g.on('jqGridLoadError.tangliGrades',function(){s.done=false;s.failed=true;});"
                + "g.on('jqGridLoadComplete.tangliGrades',function(){"
                + "if(s.failed)return;if(s.phase===0){s.phase=1;"
                + "setTimeout(function(){g.jqGrid('setGridParam',{rowNum:500,page:1});g.trigger('reloadGrid');},0);"
                + "}else{s.done=true;}});"
                + "var y=document.getElementById('xnm'),q=document.getElementById('xqm');"
                + "if(y)y.value='';if(q)q.value='';"
                + "g.jqGrid('setGridParam',{rowNum:500,page:1});"
                + "var b=document.getElementById('search_go');if(!b)return 'WAIT';b.click();"
                + "return 'CLICKED';}catch(e){return 'ERR';}})()";
    }

    public static String readJs() {
        return "(function(){try{var s=window.__tangliGradeQuery,$=window.jQuery,g=$&&$('#tabGrid');"
                + "if(!s||!s.done||s.failed||!g||!g.length)return '';var t=g[0],p=t.p;"
                + "if(!p||(t.grid&&t.grid.hDiv.loading)||Number(p.rowNum)!==500||Number(p.page)!==1"
                + "||Number(p.lastpage)>1||!p.postData||p.postData.xnm||p.postData.xqm)return '';"
                + "var n=Number(p.records);if(!isFinite(n)||n<=0||n>500)return '';"
                + "var rows=[],rs=t.querySelectorAll('tr.jqgrow');if(rs.length!==n)return '';"
                + "for(var i=0;i<rs.length;i++){var row=[],cs=rs[i].cells;"
                + "for(var c=0;c<cs.length;c++)row.push((cs[c].innerText||'').trim());rows.push(row);}"
                + "return JSON.stringify({rows:rows,records:n});}catch(e){return '';}})()";
    }

    public static boolean complete(String json, int parsedCount) {
        try { return parsedCount > 0 && new org.json.JSONObject(json).getInt("records") == parsedCount; }
        catch (Exception e) { return false; }
    }
}

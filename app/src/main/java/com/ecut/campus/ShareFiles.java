package com.ecut.campus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;

/** 把 App 私有目录 export/ 下的文件交给系统分享（FileProvider）。ICS、诊断日志、分享卡片共用。 */
public final class ShareFiles {
    private ShareFiles() {}

    /** getExternalFilesDir/export/&lt;name&gt;，目录不存在就建。 */
    public static File exportFile(Context c, String name) {
        File dir = new File(c.getExternalFilesDir(null), "export");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }

    /** 弹系统分享面板。subject 可为 null。 */
    public static void send(Activity a, File file, String mime, String subject, String chooserTitle) {
        Uri uri = androidx.core.content.FileProvider.getUriForFile(
                a, a.getPackageName() + ".fileprovider", file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mime);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        if (subject != null) share.putExtra(Intent.EXTRA_SUBJECT, subject);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        a.startActivity(Intent.createChooser(share, chooserTitle));
    }
}

package com.ecut.campus.fun;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.ecut.campus.ShareFiles;
import com.ecut.campus.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** 卡片预览弹窗：看一眼 → 保存到相册（MediaStore，不要存储权限）或系统分享。 */
public final class CardPreview {
    private CardPreview() {}

    public static void show(Activity a, Bitmap bmp, String fileBase) {
        ImageView iv = new ImageView(a);
        iv.setAdjustViewBounds(true);
        iv.setImageBitmap(bmp);
        iv.setContentDescription("卡片预览");
        int p = Ui.dp(a, 16);
        iv.setPadding(p, p / 2, p, 0);
        ScrollView sv = new ScrollView(a);
        sv.addView(iv);
        new AlertDialog.Builder(a)
                .setView(sv)
                .setNegativeButton("关闭", null)
                .setNeutralButton("保存到相册", (d, w) -> save(a, bmp, fileBase))
                .setPositiveButton("分享", (d, w) -> share(a, bmp, fileBase))
                .show();
    }

    static void save(Activity a, Bitmap bmp, String fileBase) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 及以下写相册要存储权限，烫梨不申请：改走分享，让用户自己存
            Toast.makeText(a, "这个系统版本请点「分享」后选择保存", Toast.LENGTH_LONG).show();
            share(a, bmp, fileBase);
            return;
        }
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, fileBase + "-" + System.currentTimeMillis() + ".png");
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Tangli");
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        ContentResolver cr = a.getContentResolver();
        Uri uri = null;
        try {
            uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("相册不可用");
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os == null || !bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                    throw new IllegalStateException("写入失败");
                }
            }
            v.clear();
            v.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(uri, v, null, null);
            Toast.makeText(a, "已保存到相册 · Pictures/Tangli", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            if (uri != null) cr.delete(uri, null, null);
            Toast.makeText(a, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    static void share(Activity a, Bitmap bmp, String fileBase) {
        try {
            File out = ShareFiles.exportFile(a, fileBase + ".png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            ShareFiles.send(a, out, "image/png", null, "分享卡片");
        } catch (Exception e) {
            Toast.makeText(a, "分享失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

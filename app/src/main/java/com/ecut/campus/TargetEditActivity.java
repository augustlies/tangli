package com.ecut.campus;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 教务直达路径编辑页。
 */
public class TargetEditActivity extends AppCompatActivity {

    private LinearLayout container;
    private final List<TargetStore.Target> working = new ArrayList<>();
    private final List<View> rows = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        G.init(this);
        setContentView(R.layout.activity_target_edit);

        container = findViewById(R.id.edit_container);
        View backBtn = findViewById(R.id.btn_edit_back);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> onBackPressed());
        }

        for (TargetStore.Target t : TargetStore.load(this)) {
            working.add(new TargetStore.Target(t.key, t.title, t.path, t.entry, t.builtin));
        }
        render();

        findViewById(R.id.btn_add).setOnClickListener(v -> {
            collect();
            TargetStore.Target t = new TargetStore.Target(
                    "custom_" + UUID.randomUUID().toString().substring(0, 8),
                    "新功能", "", "", false);
            working.add(t);
            render();
        });

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            collect();
            List<TargetStore.Target> clean = new ArrayList<>();
            for (TargetStore.Target t : working) {
                if (t.title == null || t.title.trim().isEmpty()) continue;
                if (t.path == null || t.path.trim().isEmpty()) continue;
                t.title = t.title.trim();
                t.path = t.path.trim();
                clean.add(t);
            }
            TargetStore.save(this, clean);
            Toast.makeText(this, "已保存，共 " + clean.size() + " 项", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void render() {
        container.removeAllViews();
        rows.clear();
        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 0; i < working.size(); i++) {
            final TargetStore.Target t = working.get(i);
            View row = inf.inflate(R.layout.item_target_edit, container, false);
            EditText etTitle = row.findViewById(R.id.edit_title);
            EditText etPath = row.findViewById(R.id.edit_path);
            etTitle.setText(t.title);
            etPath.setText(t.path);

            Button del = row.findViewById(R.id.btn_del);
            if (t.builtin) {
                del.setEnabled(false);
                del.setText("内置");
            } else {
                del.setOnClickListener(v -> {
                    collect();
                    working.removeIf(x -> x.key.equals(t.key));
                    render();
                });
            }

            Button test = row.findViewById(R.id.btn_test);
            test.setOnClickListener(v -> {
                String p = etPath.getText().toString().trim();
                if (p.isEmpty()) {
                    Toast.makeText(this, "路径为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent testIntent = new Intent(this, WebShellActivity.class);
                testIntent.putExtra(WebShellActivity.EXTRA_RAW_PATH, p);
                startActivity(testIntent);
            });

            container.addView(row);
            rows.add(row);
        }
    }

    private void collect() {
        for (int i = 0; i < rows.size() && i < working.size(); i++) {
            View row = rows.get(i);
            EditText etTitle = row.findViewById(R.id.edit_title);
            EditText etPath = row.findViewById(R.id.edit_path);
            working.get(i).title = etTitle.getText().toString();
            working.get(i).path = etPath.getText().toString();
        }
    }

    private boolean hasUnsavedChanges() {
        List<TargetStore.Target> saved = TargetStore.load(this);
        if (saved.size() != working.size()) return true;
        for (int i = 0; i < saved.size(); i++) {
            TargetStore.Target s = saved.get(i);
            TargetStore.Target w = working.get(i);
            String wt = w.title == null ? "" : w.title.trim();
            String wp = w.path == null ? "" : w.path.trim();
            if (!wt.equals(s.title) || !wp.equals(s.path)) return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        collect();
        if (hasUnsavedChanges()) {
            new AlertDialog.Builder(this)
                    .setTitle("未保存修改")
                    .setMessage("有修改尚未保存，确定放弃并返回？")
                    .setNegativeButton("继续编辑", null)
                    .setPositiveButton("放弃", (d, w) -> finish())
                    .show();
        } else {
            super.onBackPressed();
        }
    }
}

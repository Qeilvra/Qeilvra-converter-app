package com.fileflow.app;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class RecentActivity extends AppCompatActivity {
    private LinearLayout container;
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_recent);
        ToolIo.insets(this);
        container = findViewById(R.id.historyContainer);
        findViewById(R.id.buttonBack).setOnClickListener(view -> finish());
        findViewById(R.id.buttonClear).setOnClickListener(view -> { HistoryStore.clear(this); render(); });
        render();
    }
    private void render() {
        container.removeAllViews();
        List<HistoryStore.Entry> entries = HistoryStore.read(this);
        if (entries.isEmpty()) {
            TextView empty = new TextView(this); empty.setText(R.string.no_recent_activity); empty.setGravity(android.view.Gravity.CENTER); empty.setPadding(16, 60, 16, 60); container.addView(empty); return;
        }
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        for (HistoryStore.Entry entry : entries) {
            TextView row = new TextView(this);
            row.setText(getString(R.string.recent_item, entry.outputName, entry.operation, bytes(entry.outputSize), format.format(new Date(entry.createdAt))));
            row.setTextColor(getColor(R.color.navy)); row.setTextSize(13); row.setBackgroundResource(R.drawable.bg_upload); row.setPadding(18, 16, 18, 16);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); params.bottomMargin = 9; container.addView(row, params);
        }
    }
    private static String bytes(long value) { if (value < 1024) return value + " B"; if (value < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", value / 1024f); return String.format(Locale.getDefault(), "%.1f MB", value / (1024f * 1024f)); }
}

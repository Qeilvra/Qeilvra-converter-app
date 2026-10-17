package com.fileflow.app;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import com.fileflow.app.processing.FileValidator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class DocumentToolActivity extends AppCompatActivity {
    public static final String EXTRA_TOOL = "tool";
    private static final long MAX_FILE_BYTES = 50L * 1024L * 1024L;
    private static final long MAX_COMBINED_BYTES = 80L * 1024L * 1024L;

    private DocumentToolViewModel model;
    private String tool;
    private TextView fileInfo;
    private TextView status;
    private TextInputLayout previewLayout;
    private TextInputEditText preview;
    private Spinner actionSpinner;
    private ProgressBar progress;
    private MaterialButton processButton;
    private LinearLayout resultActions;
    private LinearLayout pageContainer;

    private final ActivityResultLauncher<String[]> openSingle = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
        if (uri != null) acceptSelection(Collections.singletonList(uri));
    });
    private final ActivityResultLauncher<String[]> openMultiple = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), this::acceptSelection);
    private final ActivityResultLauncher<String> saveOutput = registerForActivityResult(new ActivityResultContracts.CreateDocument("*/*"), this::writeOutput);

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tool = getIntent().getStringExtra(EXTRA_TOOL);
        if (tool == null || !isKnownTool(tool)) { finish(); return; }
        setContentView(R.layout.activity_document_tool);
        model = new ViewModelProvider(this).get(DocumentToolViewModel.class);
        TextView title = findViewById(R.id.textTitle);
        TextView description = findViewById(R.id.textDescription);
        fileInfo = findViewById(R.id.textFileInfo);
        status = findViewById(R.id.textStatus);
        previewLayout = findViewById(R.id.previewLayout);
        preview = findViewById(R.id.inputPreview);
        actionSpinner = findViewById(R.id.spinnerAction);
        progress = findViewById(R.id.progress);
        processButton = findViewById(R.id.buttonProcess);
        resultActions = findViewById(R.id.resultActions);
        pageContainer = findViewById(R.id.pageContainer);
        configure(title, description);

        findViewById(R.id.buttonBack).setOnClickListener(view -> finish());
        findViewById(R.id.buttonChoose).setOnClickListener(view -> choose());
        processButton.setOnClickListener(view -> model.process(tool, actionSpinner.getSelectedItemPosition(), preview.getText() == null ? "" : preview.getText().toString()));
        findViewById(R.id.buttonSave).setOnClickListener(view -> { if (model.outputName != null) saveOutput.launch(model.outputName); });
        findViewById(R.id.buttonShare).setOnClickListener(view -> share());
        model.state().observe(this, this::render);
        restore();
    }

    private void configure(TextView title, TextView description) {
        switch (tool) {
            case "pdf-excel": title.setText(R.string.pdf_to_excel); description.setText(R.string.description_pdf_excel); break;
            case "pdf-word": title.setText(R.string.pdf_to_word); description.setText(R.string.description_pdf_word); break;
            case "word-pdf": title.setText(R.string.word_to_pdf); description.setText(R.string.description_word_pdf); break;
            case "pdf-compress": title.setText(R.string.pdf_compressor); description.setText(R.string.description_pdf_compress); break;
            case "pdf-editor":
                title.setText(R.string.pdf_editor); description.setText(R.string.description_pdf_editor);
                actionSpinner.setVisibility(View.VISIBLE);
                actionSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, getResources().getStringArray(R.array.pdf_editor_actions)));
                actionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { renderPages(); processButton.setEnabled(!model.uris.isEmpty() && (position != 0 || !model.pages.isEmpty())); }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                });
                ((MaterialButton) findViewById(R.id.buttonChoose)).setText(R.string.choose_pdfs);
                break;
        }
        if (needsPreview()) previewLayout.setVisibility(View.VISIBLE);
    }

    private void choose() {
        String[] types = "word-pdf".equals(tool)
                ? new String[] {"application/vnd.openxmlformats-officedocument.wordprocessingml.document"}
                : new String[] {"application/pdf"};
        if ("pdf-editor".equals(tool)) openMultiple.launch(types); else openSingle.launch(types);
    }

    private void acceptSelection(List<Uri> selected) {
        if (selected == null || selected.isEmpty()) return;
        try {
            if (!"pdf-editor".equals(tool) && selected.size() != 1) throw new IllegalArgumentException("Select one file.");
            if (selected.size() > 20) throw new IllegalArgumentException("Select at most 20 PDFs.");
            long total = 0;
            ArrayList<String> names = new ArrayList<>();
            FileValidator.FileKind expected = "word-pdf".equals(tool) ? FileValidator.FileKind.DOCX : FileValidator.FileKind.PDF;
            for (Uri uri : selected) {
                FileValidator.validate(getContentResolver(), uri, expected, MAX_FILE_BYTES);
                try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) {}
                Metadata metadata = metadata(uri);
                total += metadata.size;
                names.add(metadata.name);
            }
            if (total > MAX_COMBINED_BYTES) throw new IllegalArgumentException("The selected files exceed the 80 MB combined mobile limit.");
            model.select(selected);
            model.sourceName = selected.size() == 1 ? names.get(0) : selected.size() + "-pdfs";
            model.sourceSize = total;
            fileInfo.setText(getString(R.string.selected_files_size, String.join(" · ", names), formatBytes(total)));
            preview.setText("");
            preview.setTag(null);
            resultActions.setVisibility(View.GONE);
            if ("pdf-editor".equals(tool)) {
                actionSpinner.setSelection(selected.size() > 1 ? 1 : 0);
                if (selected.size() == 1) model.preparePages();
            }
            processButton.setEnabled(!needsPreview());
            if (needsPreview()) model.preparePreview(tool);
        } catch (Exception error) {
            status.setText(error.getMessage() == null ? "Unsupported file." : error.getMessage());
        }
    }

    private void render(DocumentToolViewModel.State state) {
        progress.setVisibility(state.working ? View.VISIBLE : View.GONE);
        processButton.setEnabled(!state.working && !model.uris.isEmpty() && (!needsPreview() || model.previewText != null));
        if (state.error != null) status.setText(state.error);
        else if (state.message != null) status.setText(state.message);
        if (model.previewText != null && preview.getTag() == null) { preview.setText(model.previewText); preview.setTag(Boolean.TRUE); }
        resultActions.setVisibility(model.outputBytes == null ? View.GONE : View.VISIBLE);
        if ("pdf-editor".equals(tool)) renderPages();
    }

    private void renderPages() {
        if (pageContainer == null) return;
        boolean visible = "pdf-editor".equals(tool) && actionSpinner.getSelectedItemPosition() == 0 && model.uris.size() == 1 && !model.pages.isEmpty();
        pageContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        pageContainer.removeAllViews();
        if (!visible) return;
        for (int index = 0; index < model.pages.size(); index++) {
            final int position = index;
            DocumentToolViewModel.PageEdit page = model.pages.get(index);
            LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setPadding(dp(10), dp(6), dp(5), dp(6));
            TextView label = new TextView(this); label.setText(page.rotation == 0 ? getString(R.string.page_number, page.pageIndex + 1) : getString(R.string.page_number_rotation, page.pageIndex + 1, page.rotation)); label.setTextColor(getColor(R.color.navy));
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(pageAction("←", () -> movePage(position, -1), index == 0));
            row.addView(pageAction("→", () -> movePage(position, 1), index == model.pages.size() - 1));
            row.addView(pageAction("↻", () -> { page.rotation = (page.rotation + 90) % 360; model.outputBytes = null; renderPages(); }, false));
            row.addView(pageAction("×", () -> { if (model.pages.size() > 1) { model.pages.remove(position); model.outputBytes = null; renderPages(); } }, model.pages.size() == 1));
            pageContainer.addView(row);
        }
    }

    private MaterialButton pageAction(String text, Runnable action, boolean disabled) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text); button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(dp(8), 0, dp(8), 0); button.setEnabled(!disabled); button.setOnClickListener(view -> action.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)));
        return button;
    }

    private void movePage(int index, int delta) {
        int target = index + delta; if (target < 0 || target >= model.pages.size()) return;
        Collections.swap(model.pages, index, target); model.outputBytes = null; renderPages();
    }

    private void restore() {
        if (!model.uris.isEmpty()) {
            fileInfo.setText(getString(R.string.selected_file_size, model.sourceName == null ? getString(R.string.choose_file) : model.sourceName, formatBytes(model.sourceSize)));
            if (model.previewText != null) { preview.setText(model.previewText); preview.setTag(Boolean.TRUE); }
        }
        resultActions.setVisibility(model.outputBytes == null ? View.GONE : View.VISIBLE);
    }

    private void writeOutput(@Nullable Uri uri) {
        if (uri == null || model.outputBytes == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) throw new IllegalStateException("The selected location is unavailable.");
            output.write(model.outputBytes); output.flush();
            Toast.makeText(this, R.string.file_saved_successfully, Toast.LENGTH_LONG).show();
        } catch (Exception error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void share() {
        if (model.outputBytes == null || model.outputName == null || model.outputMime == null) return;
        try {
            File directory = new File(getCacheDir(), "shared");
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not prepare the share cache.");
            File file = new File(directory, model.outputName.replaceAll("[^a-zA-Z0-9._-]", "-"));
            try (FileOutputStream output = new FileOutputStream(file, false)) { output.write(model.outputBytes); output.flush(); }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent intent = new Intent(Intent.ACTION_SEND).setType(model.outputMime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_output)));
        } catch (Exception error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private Metadata metadata(Uri uri) {
        String name = "document"; long size = 0;
        try (Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex);
            }
        }
        return new Metadata(name, size);
    }

    private boolean needsPreview() { return "pdf-excel".equals(tool) || "pdf-word".equals(tool) || "word-pdf".equals(tool); }
    private static boolean isKnownTool(String value) { return "pdf-excel".equals(value) || "pdf-word".equals(value) || "word-pdf".equals(value) || "pdf-compress".equals(value) || "pdf-editor".equals(value); }
    private static String formatBytes(long bytes) { if (bytes < 1024) return bytes + " B"; if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f); return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static final class Metadata { final String name; final long size; Metadata(String name, long size) { this.name = name; this.size = size; } }
}

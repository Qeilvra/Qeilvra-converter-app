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
        ToolIo.insets(this);
        model = new ViewModelProvider(this).get(DocumentToolViewModel.class);
        TextView title = findViewById(R.id.textTitle);
        TextView description = findViewById(R.id.textDescription);
        fileInfo = findViewById(R.id.textFileInfo);
        status = findViewById(R.id.textStatus);
        previewLayout = findViewById(R.id.previewLayout);
        preview = findViewById(R.id.inputPreview);
        preview.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(android.text.Editable value) {
                if (preview.getTag() != null && !model.isWorking()) { model.previewText = value.toString(); invalidateOutput(); }
            }
        });
        actionSpinner = findViewById(R.id.spinnerAction);
        progress = findViewById(R.id.progress);
        processButton = findViewById(R.id.buttonProcess);
        resultActions = findViewById(R.id.resultActions);
        pageContainer = findViewById(R.id.pageContainer);
        configure(title, description);
        com.google.android.material.checkbox.MaterialCheckBox ocr = findViewById(R.id.checkOfflineOcr);
        ocr.setVisibility(BuildConfig.BUNDLED_OFFLINE_OCR && ("pdf-word".equals(tool) || "pdf-excel".equals(tool)) ? View.VISIBLE : View.GONE);
        ocr.setChecked(model.allowOcr);
        ocr.setOnCheckedChangeListener((button, checked) -> {
            model.allowOcr = checked; invalidateOutput();
            if (needsPreview() && !model.uris.isEmpty()) { model.previewText = null; preview.setTag(null); preview.setText(""); model.preparePreview(tool); }
        });
        ((TextView) findViewById(R.id.textPrivacy)).setText(R.string.privacy_local_pdf);

        findViewById(R.id.buttonBack).setOnClickListener(view -> finish());
        findViewById(R.id.buttonChoose).setOnClickListener(view -> choose());
        processButton.setOnClickListener(view -> {
            if ("word-pdf".equals(tool) && actionSpinner.getSelectedItemPosition() == 0) openOfficeApp();
            else model.process(tool, actionSpinner.getSelectedItemPosition(), preview.getText() == null ? "" : preview.getText().toString());
        });
        findViewById(R.id.buttonSave).setOnClickListener(view -> { if (model.outputName != null) saveOutput.launch(model.outputName); });
        findViewById(R.id.buttonShare).setOnClickListener(view -> share());
        model.state().observe(this, this::render);
        restore();
    }

    private void configure(TextView title, TextView description) {
        switch (tool) {
            case "pdf-excel": title.setText(R.string.pdf_to_excel); description.setText(R.string.description_pdf_excel); break;
            case "pdf-word": title.setText(R.string.pdf_to_word); description.setText(R.string.description_pdf_word); break;
            case "word-pdf":
                title.setText(R.string.word_to_pdf); description.setText(R.string.description_word_pdf);
                actionSpinner.setVisibility(View.VISIBLE);
                ArrayList<String> engines = new ArrayList<>();
                engines.add(getString(R.string.word_pdf_office_app)); engines.add(getString(R.string.word_pdf_basic));
                if (!BuildConfig.PROCESSING_API_URL.trim().isEmpty()) engines.add(getString(R.string.word_pdf_service));
                actionSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, engines));
                actionSpinner.setSelection(model.wordPdfEngine);
                actionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                        model.wordPdfEngine = position; invalidateOutput();
                        processButton.setText(position == 0 ? R.string.word_pdf_open_app : position == 1 ? R.string.word_pdf_basic : R.string.process_file);
                        ((TextView)findViewById(R.id.textPrivacy)).setText(position == 2
                            ? getString(R.string.word_pdf_upload_notice, BuildConfig.PROCESSING_API_URL)
                            : getString(position == 0 ? R.string.word_pdf_office_notice : R.string.privacy_local_pdf));
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
                });
                break;
            case "pdf-compress":
                title.setText(R.string.pdf_compressor); description.setText(R.string.description_pdf_compress);
                actionSpinner.setVisibility(View.VISIBLE);
                actionSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, getResources().getStringArray(R.array.compression_levels)));
                actionSpinner.setSelection(1);
                break;
            case "pdf-editor":
                title.setText(R.string.pdf_editor); description.setText(R.string.description_pdf_editor);
                actionSpinner.setVisibility(View.VISIBLE);
                actionSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, getResources().getStringArray(R.array.pdf_editor_actions)));
                actionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { renderPages(); processButton.setEnabled(!model.isWorking() && !model.uris.isEmpty() && (position != 0 || !model.pages.isEmpty())); }
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

    private void openOfficeApp() {
        if (model.uris.isEmpty() || model.isWorking()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(model.uris.get(0), "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(android.content.ClipData.newRawUri("Word document", model.uris.get(0)));
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.word_pdf_open_app)));
            status.setText(R.string.word_pdf_export_instructions);
        } catch (android.content.ActivityNotFoundException missing) { status.setText(R.string.word_pdf_no_office_app); }
    }

    private void acceptSelection(List<Uri> selected) {
        if (selected == null || selected.isEmpty()) return;
        if (model.isWorking()) return;
        findViewById(R.id.buttonChoose).setEnabled(false);
        processButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        ToolIo.run(this, () -> {
            if (!"pdf-editor".equals(tool) && selected.size() != 1) throw new IllegalArgumentException("Select one file.");
            if (selected.size() > 20) throw new IllegalArgumentException("Select at most 20 PDFs.");
            long total = 0;
            ArrayList<String> names = new ArrayList<>();
            FileValidator.FileKind expected = "word-pdf".equals(tool) ? FileValidator.FileKind.DOCX : FileValidator.FileKind.PDF;
            for (Uri uri : selected) {
                long actualSize = FileValidator.validate(getContentResolver(), uri, expected, MAX_FILE_BYTES);
                try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) {}
                Metadata metadata = metadata(uri);
                total += actualSize;
                names.add(metadata.name);
            }
            if (total > MAX_COMBINED_BYTES) throw new IllegalArgumentException("The selected files exceed the 80 MB combined mobile limit.");
            return new Selection(names, total);
        }, selection -> {
            findViewById(R.id.buttonChoose).setEnabled(true);
            progress.setVisibility(View.GONE);
            model.select(selected);
            model.sourceName = selected.size() == 1 ? selection.names.get(0) : selected.size() + "-pdfs";
            model.sourceSize = selection.size;
            fileInfo.setText(getString(R.string.selected_files_size, String.join(" · ", selection.names), formatBytes(selection.size)));
            preview.setText("");
            preview.setTag(null);
            resultActions.setVisibility(View.GONE);
            if ("pdf-editor".equals(tool)) {
                actionSpinner.setSelection(selected.size() > 1 ? 1 : 0);
                if (selected.size() == 1) model.preparePages();
            }
            processButton.setEnabled(!model.isWorking() && !needsPreview());
            if (needsPreview()) model.preparePreview(tool);
        }, message -> {
            findViewById(R.id.buttonChoose).setEnabled(true);
            progress.setVisibility(View.GONE);
            processButton.setEnabled(!model.uris.isEmpty());
            status.setText(message);
        });
    }

    private void render(DocumentToolViewModel.State state) {
        progress.setVisibility(state.working ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonChoose).setEnabled(!state.working);
        actionSpinner.setEnabled(!state.working);
        preview.setEnabled(!state.working);
        findViewById(R.id.checkOfflineOcr).setEnabled(!state.working);
        processButton.setEnabled(!state.working && !model.uris.isEmpty() && (!needsPreview() || model.previewText != null));
        if (state.error != null) status.setText(state.error);
        else if (state.message != null) status.setText(state.message);
        if (model.previewText != null && preview.getTag() == null) { preview.setText(model.previewText); preview.setTag(Boolean.TRUE); }
        resultActions.setVisibility(model.outputBytes == null ? View.GONE : View.VISIBLE);
        if ("pdf-editor".equals(tool)) { renderPages(); if (state.working) ToolIo.enabled(pageContainer, false); }
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
            row.addView(pageAction("↻", () -> { page.rotation = (page.rotation + 90) % 360; invalidateOutput(); renderPages(); }, false));
            row.addView(pageAction("×", () -> { if (model.pages.size() > 1) { model.pages.remove(position); invalidateOutput(); renderPages(); } }, model.pages.size() == 1));
            pageContainer.addView(row);
        }
        if (model.isWorking()) ToolIo.enabled(pageContainer, false);
    }

    private MaterialButton pageAction(String text, Runnable action, boolean disabled) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text); button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(dp(8), 0, dp(8), 0); button.setEnabled(!disabled); button.setOnClickListener(view -> action.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)));
        return button;
    }

    private void movePage(int index, int delta) {
        int target = index + delta; if (target < 0 || target >= model.pages.size()) return;
        Collections.swap(model.pages, index, target); invalidateOutput(); renderPages();
    }

    private void invalidateOutput() { model.outputBytes = null; model.outputName = null; model.outputMime = null; resultActions.setVisibility(View.GONE); }

    private void restore() {
        if (!model.uris.isEmpty()) {
            fileInfo.setText(getString(R.string.selected_file_size, model.sourceName == null ? getString(R.string.choose_file) : model.sourceName, formatBytes(model.sourceSize)));
            if (model.previewText != null) { preview.setText(model.previewText); preview.setTag(Boolean.TRUE); }
        }
        resultActions.setVisibility(model.outputBytes == null ? View.GONE : View.VISIBLE);
    }

    private void writeOutput(@Nullable Uri uri) {
        if (uri != null) {
            if (model.outputBytes != null) ToolIo.save(this, uri, model.outputBytes);
            else ToolIo.missingResult(this, uri);
        }
    }

    private void share() {
        if (model.outputBytes != null && model.outputName != null && model.outputMime != null) ToolIo.share(this, model.outputBytes, model.outputName, model.outputMime);
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

    private boolean needsPreview() { return "pdf-excel".equals(tool); }
    private static boolean isKnownTool(String value) { return "pdf-excel".equals(value) || "pdf-word".equals(value) || "word-pdf".equals(value) || "pdf-compress".equals(value) || "pdf-editor".equals(value); }
    private static String formatBytes(long bytes) { if (bytes < 1024) return bytes + " B"; if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f); return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static final class Selection { final List<String> names; final long size; Selection(List<String> names, long size) { this.names = names; this.size = size; } }
    private static final class Metadata { final String name; final long size; Metadata(String name, long size) { this.name = name; this.size = size; } }
}

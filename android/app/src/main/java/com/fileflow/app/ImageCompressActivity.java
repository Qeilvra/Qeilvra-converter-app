package com.fileflow.app;

import android.database.Cursor;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
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
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class ImageCompressActivity extends AppCompatActivity {
    private CompressionViewModel model;
    private ImageView preview;
    private TextView fileInfo;
    private TextView status;
    private TextView qualityLabel;
    private View progress;
    private MaterialButton compressButton;
    private MaterialButton saveButton;
    private MaterialButton shareButton;
    private TextInputEditText customTarget;
    private ChipGroup targetGroup;
    private int quality = 82;

    private final ActivityResultLauncher<String[]> openImage = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onImageSelected);
    private final ActivityResultLauncher<String> saveImage = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("image/jpeg"), this::onSaveDestinationSelected);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_compress);
        ToolIo.insets(this);
        model = new ViewModelProvider(this).get(CompressionViewModel.class);
        preview = findViewById(R.id.imagePreview);
        fileInfo = findViewById(R.id.textFileInfo);
        status = findViewById(R.id.textStatus);
        qualityLabel = findViewById(R.id.textQuality);
        progress = findViewById(R.id.progressCompression);
        compressButton = findViewById(R.id.buttonCompress);
        saveButton = findViewById(R.id.buttonSave);
        shareButton = findViewById(R.id.buttonShare);
        customTarget = findViewById(R.id.inputTarget);
        targetGroup = findViewById(R.id.groupTarget);

        findViewById(R.id.buttonBack).setOnClickListener(view -> finish());
        findViewById(R.id.buttonChoose).setOnClickListener(view -> openImage.launch(new String[]{"image/jpeg", "image/png", "image/webp"}));
        compressButton.setOnClickListener(view -> compress());
        saveButton.setOnClickListener(view -> saveImage.launch(outputName()));
        shareButton.setOnClickListener(view -> shareOutput());
        targetGroup.check(R.id.target100);

        SeekBar seek = findViewById(R.id.seekQuality);
        seek.setProgress(quality - 25);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                quality = value + 25;
                qualityLabel.setText(getString(R.string.quality_value, quality));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        model.state().observe(this, uiState -> {
            setBusy(uiState.working);
            if (uiState.working) {
                status.setText(R.string.processing_locally);
            } else if (uiState.error != null) {
                status.setText(uiState.error);
            } else if (model.outputBytes != null) {
                showResult(uiState.targetKb);
            }
        });
        renderRetainedState();
    }

    private void onImageSelected(@Nullable Uri uri) {
        if (uri == null) return;
        findViewById(R.id.buttonChoose).setEnabled(false);
        compressButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        ToolIo.run(this, () -> {
            FileValidator.validateImage(getContentResolver(), uri, 30L * 1024L * 1024L);
            return com.fileflow.app.processing.BitmapProcessor.thumbnail(getContentResolver(), uri);
        }, thumbnail -> {
        findViewById(R.id.buttonChoose).setEnabled(true);
        progress.setVisibility(View.GONE);
        try {
            getContentResolver().takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some document providers grant access only for the activity lifetime.
        }
        model.sourceUri = uri;
        model.outputBytes = null;
        readMetadata(uri);
        preview.setImageBitmap(thumbnail);
        saveButton.setVisibility(View.GONE);
        shareButton.setVisibility(View.GONE);
        status.setText(R.string.ready_to_compress);
        compressButton.setEnabled(true);
        }, message -> {
            findViewById(R.id.buttonChoose).setEnabled(true);
            progress.setVisibility(View.GONE);
            compressButton.setEnabled(model.sourceUri != null);
            status.setText(message);
        });
    }

    private void readMetadata(Uri uri) {
        model.sourceName = "image";
        model.sourceSize = 0;
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0) model.sourceName = cursor.getString(nameIndex);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) model.sourceSize = cursor.getLong(sizeIndex);
            }
        }
        fileInfo.setText(String.format(Locale.getDefault(), "%s · %s", model.sourceName, formatBytes(model.sourceSize)));
    }

    private int targetKb() {
        int selected = targetGroup.getCheckedChipId();
        if (selected == R.id.target20) return 20;
        if (selected == R.id.target50) return 50;
        if (selected == R.id.target200) return 200;
        if (selected == R.id.target500) return 500;
        if (selected == R.id.targetCustom) {
            String text = customTarget.getText() == null ? "" : customTarget.getText().toString().trim();
            try { return Integer.parseInt(text); }
            catch (NumberFormatException ignored) { return -1; }
        }
        return 100;
    }

    private void compress() {
        Uri source = model.sourceUri;
        if (source == null) return;
        int target = targetKb();
        if (target < 10 || target > 10240) {
            customTarget.setError(getString(R.string.target_error));
            return;
        }
        model.compress(target, quality, 1920);
    }

    private void showResult(int targetKb) {
        setBusy(false);
        if (model.outputBytes == null) return;
        preview.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(model.outputBytes, 0, model.outputBytes.length));
        String measured = formatBytes(model.outputBytes.length);
        if (model.targetReached) status.setText(getString(R.string.compression_success, measured, model.outputWidth, model.outputHeight));
        else status.setText(getString(R.string.compression_target_missed, targetKb, measured));
        saveButton.setVisibility(View.VISIBLE);
        shareButton.setVisibility(View.VISIBLE);
    }

    private void onSaveDestinationSelected(@Nullable Uri uri) {
        if (uri != null) {
            if (model.outputBytes != null) ToolIo.save(this, uri, model.outputBytes);
            else ToolIo.missingResult(this, uri);
        }
    }

    private void shareOutput() {
        if (model.outputBytes != null) ToolIo.share(this, model.outputBytes, outputName(), "image/jpeg");
    }

    private void renderRetainedState() {
        if (model.sourceUri != null) {
            readMetadata(model.sourceUri);
            Uri source = model.sourceUri;
            ToolIo.run(this, () -> com.fileflow.app.processing.BitmapProcessor.thumbnail(getContentResolver(), source), bitmap -> { if (model.outputBytes == null) preview.setImageBitmap(bitmap); else bitmap.recycle(); }, message -> status.setText(message));
            compressButton.setEnabled(true);
        }
        if (model.outputBytes != null) showResult(targetKb());
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonChoose).setEnabled(!busy);
        saveButton.setVisibility(!busy && model.outputBytes != null ? View.VISIBLE : View.GONE);
        shareButton.setVisibility(!busy && model.outputBytes != null ? View.VISIBLE : View.GONE);
        compressButton.setEnabled(!busy && model.sourceUri != null);
    }

    private String outputName() {
        String source = model.sourceName == null ? "fileflow-image" : model.sourceName.replaceFirst("\\.[^.]+$", "");
        return source + "-compressed.jpg";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f));
    }

}

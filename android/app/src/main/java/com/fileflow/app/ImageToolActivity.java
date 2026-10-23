package com.fileflow.app;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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
import com.fileflow.app.processing.BitmapProcessor;
import com.fileflow.app.processing.FileValidator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

public final class ImageToolActivity extends AppCompatActivity {
    public static final String EXTRA_TOOL = "tool";
    private String tool;
    private ImageToolViewModel model;
    private LinearLayout itemsContainer;
    private LinearLayout cropControls;
    private LinearLayout resultActions;
    private ProgressBar progress;
    private TextView status;
    private MaterialButton processButton;
    private Spinner option;
    private TextInputEditText widthInput;
    private TextInputEditText heightInput;
    private TextInputEditText targetInput;
    private EditText cropTop;
    private EditText cropRight;
    private EditText cropBottom;
    private EditText cropLeft;
    private int cropIndex = -1;

    private final ActivityResultLauncher<String[]> openSingle = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) addUris(Collections.singletonList(uri), false); });
    private final ActivityResultLauncher<String[]> openMultiple = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> addUris(uris, true));
    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
        Uri uri = model.pendingCameraUri;
        model.pendingCameraUri = null;
        if (success && uri != null) addUris(Collections.singletonList(uri), true);
    });
    private final ActivityResultLauncher<String> saveOutput = registerForActivityResult(new ActivityResultContracts.CreateDocument("*/*"), this::writeOutput);

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tool = getIntent().getStringExtra(EXTRA_TOOL);
        if (!isKnown(tool)) { finish(); return; }
        setContentView(R.layout.activity_image_tool);
        ToolIo.insets(this);
        model = new ViewModelProvider(this).get(ImageToolViewModel.class);
        itemsContainer = findViewById(R.id.itemsContainer);
        cropControls = findViewById(R.id.cropControls);
        resultActions = findViewById(R.id.resultActions);
        progress = findViewById(R.id.progress);
        status = findViewById(R.id.textStatus);
        processButton = findViewById(R.id.buttonProcess);
        option = findViewById(R.id.spinnerOption);
        widthInput = findViewById(R.id.inputWidth);
        heightInput = findViewById(R.id.inputHeight);
        targetInput = findViewById(R.id.inputTarget);
        cropTop = findViewById(R.id.inputCropTop);
        cropRight = findViewById(R.id.inputCropRight);
        cropBottom = findViewById(R.id.inputCropBottom);
        cropLeft = findViewById(R.id.inputCropLeft);
        configure();
        findViewById(R.id.buttonBack).setOnClickListener(view -> finish());
        findViewById(R.id.buttonChoose).setOnClickListener(view -> choose());
        findViewById(R.id.buttonCamera).setOnClickListener(view -> capture());
        findViewById(R.id.buttonApplyCrop).setOnClickListener(view -> applyCrop());
        processButton.setOnClickListener(view -> model.process(tool, option.getSelectedItemPosition(), number(targetInput, 200), number(widthInput, 600), number(heightInput, 600)));
        findViewById(R.id.buttonSave).setOnClickListener(view -> { if (model.outputName != null) saveOutput.launch(model.outputName); });
        findViewById(R.id.buttonShare).setOnClickListener(view -> share());
        model.state().observe(this, this::renderState);
        renderItems();
    }

    private void configure() {
        TextView title = findViewById(R.id.textTitle);
        TextView description = findViewById(R.id.textDescription);
        MaterialButton camera = findViewById(R.id.buttonCamera);
        if ("image-convert".equals(tool)) {
            title.setText(R.string.image_converter); description.setText(R.string.description_image_convert);
            option.setVisibility(View.VISIBLE);
            option.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[] {"JPG", "PNG", "WebP"}));
        } else if ("prepare-upload".equals(tool)) {
            title.setText(R.string.prepare_upload); description.setText(R.string.description_prepare_upload);
            option.setVisibility(View.VISIBLE); findViewById(R.id.sizeControls).setVisibility(View.VISIBLE);
            option.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, getResources().getStringArray(R.array.upload_presets)));
            option.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { int[][] values = {{600,600,200},{413,531,100},{600,200,50},{1600,2200,500}}; widthInput.setText(String.valueOf(values[position][0])); heightInput.setText(String.valueOf(values[position][1])); targetInput.setText(String.valueOf(values[position][2])); }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        } else if ("scanner".equals(tool)) {
            title.setText(R.string.document_scanner); description.setText(R.string.description_scanner);
            camera.setVisibility(View.VISIBLE); option.setVisibility(View.VISIBLE);
            option.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[] {"Color", "Grayscale", "Black & white"}));
        } else {
            title.setText(R.string.image_to_pdf); description.setText(R.string.description_image_pdf);
        }
    }

    private void choose() {
        String[] types = {"image/jpeg", "image/png", "image/webp"};
        if ("image-convert".equals(tool) || "prepare-upload".equals(tool)) openSingle.launch(types); else openMultiple.launch(types);
    }

    private void capture() {
        try {
            File directory = new File(getCacheDir(), "camera");
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not prepare camera storage.");
            File file = new File(directory, "scan-" + System.currentTimeMillis() + ".jpg");
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            model.pendingCameraUri = uri;
            takePicture.launch(uri);
        } catch (Exception error) { status.setText(error.getMessage()); }
    }

    private void addUris(List<Uri> uris, boolean append) {
        if (uris == null || uris.isEmpty()) return;
        if (model.isWorking()) return;
        int initialCount = append ? model.items.size() : 0;
        findViewById(R.id.buttonChoose).setEnabled(false);
        findViewById(R.id.buttonCamera).setEnabled(false);
        processButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        ToolIo.run(this, () -> {
            java.util.ArrayList<ImageToolViewModel.Item> selected = new java.util.ArrayList<>();
            if (initialCount + uris.size() > 50) throw new IllegalArgumentException("Add no more than 50 images.");

            for (Uri uri : uris) {
                FileValidator.validateImage(getContentResolver(), uri, 30L * 1024L * 1024L);
                try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) {}
                ImageToolViewModel.Item item = new ImageToolViewModel.Item(uri, displayName(uri));
                if ("scanner".equals(tool)) {
                    float[] corners = BitmapProcessor.detectDocumentCorners(getContentResolver(), uri);
                    item.perspective = true;
                    item.topLeftX=corners[0]; item.topLeftY=corners[1]; item.topRightX=corners[2]; item.topRightY=corners[3];
                    item.bottomRightX=corners[4]; item.bottomRightY=corners[5]; item.bottomLeftX=corners[6]; item.bottomLeftY=corners[7];
                }
                selected.add(item);
            }
            return selected;
        }, selected -> {
            if (!append) model.items.clear();
            model.items.addAll(selected);
            findViewById(R.id.buttonChoose).setEnabled(true);
            findViewById(R.id.buttonCamera).setEnabled(true);
            model.changed(); cropIndex = -1; cropControls.setVisibility(View.GONE); renderItems();
        }, message -> {
            findViewById(R.id.buttonChoose).setEnabled(true);
            findViewById(R.id.buttonCamera).setEnabled(true);
            progress.setVisibility(View.GONE);
            processButton.setEnabled(!model.items.isEmpty());
            status.setText(message);
        });
    }

    private void renderItems() {
        itemsContainer.removeAllViews();
        for (int index = 0; index < model.items.size(); index++) {
            final int position = index;
            ImageToolViewModel.Item item = model.items.get(index);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10), dp(7), dp(6), dp(7));
            TextView label = new TextView(this); label.setText(item.rotation == 0 ? getString(R.string.image_item, index + 1, item.name) : getString(R.string.image_item_rotation, index + 1, item.name, item.rotation)); label.setMaxLines(2);
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(action("↑", () -> move(position, -1), index == 0));
            row.addView(action("↓", () -> move(position, 1), index == model.items.size() - 1));
            row.addView(action("↻", () -> { item.rotation = (item.rotation + 90) % 360; model.changed(); renderItems(); }, false));
            if ("scanner".equals(tool)) row.addView(action("Crop", () -> editCrop(position), false));
            row.addView(action("×", () -> { model.items.remove(position); model.changed(); cropControls.setVisibility(View.GONE); cropIndex = -1; renderItems(); }, false));
            itemsContainer.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        processButton.setEnabled(!model.isWorking() && !model.items.isEmpty());
        if (model.isWorking()) ToolIo.enabled(itemsContainer, false);
        resultActions.setVisibility(model.outputBytes == null ? View.GONE : View.VISIBLE);
    }

    private MaterialButton action(String text, Runnable runnable, boolean disabled) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text); button.setTextSize(11); button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(dp(8), 0, dp(8), 0); button.setEnabled(!disabled); button.setOnClickListener(view -> runnable.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)));
        return button;
    }

    private void move(int index, int delta) {
        int target = index + delta; if (target < 0 || target >= model.items.size()) return;
        Collections.swap(model.items, index, target); model.changed(); renderItems();
    }

    private void editCrop(int index) {
        cropIndex = index; ImageToolViewModel.Item item = model.items.get(index);
        if ("scanner".equals(tool)) {
            cropTop.setHint("Top left x,y"); cropRight.setHint("Top right x,y"); cropBottom.setHint("Bottom right x,y"); cropLeft.setHint("Bottom left x,y");
            cropTop.setText(point(item.topLeftX,item.topLeftY)); cropRight.setText(point(item.topRightX,item.topRightY)); cropBottom.setText(point(item.bottomRightX,item.bottomRightY)); cropLeft.setText(point(item.bottomLeftX,item.bottomLeftY));
        } else {
            cropTop.setText(String.valueOf(item.cropTop)); cropRight.setText(String.valueOf(item.cropRight)); cropBottom.setText(String.valueOf(item.cropBottom)); cropLeft.setText(String.valueOf(item.cropLeft));
        }
        ((TextView) findViewById(R.id.textCropTitle)).setText(getString(R.string.crop_page_number, index + 1)); cropControls.setVisibility(View.VISIBLE);
    }

    private void applyCrop() {
        if (cropIndex < 0 || cropIndex >= model.items.size()) return;
        ImageToolViewModel.Item item = model.items.get(cropIndex);
        if ("scanner".equals(tool)) {
            float[] tl=point(cropTop,item.topLeftX,item.topLeftY), tr=point(cropRight,item.topRightX,item.topRightY), br=point(cropBottom,item.bottomRightX,item.bottomRightY), bl=point(cropLeft,item.bottomLeftX,item.bottomLeftY);
            item.topLeftX=tl[0]; item.topLeftY=tl[1]; item.topRightX=tr[0]; item.topRightY=tr[1]; item.bottomRightX=br[0]; item.bottomRightY=br[1]; item.bottomLeftX=bl[0]; item.bottomLeftY=bl[1]; item.perspective=true;
        } else { item.cropTop = crop(cropTop); item.cropRight = crop(cropRight); item.cropBottom = crop(cropBottom); item.cropLeft = crop(cropLeft); }
        model.changed(); cropControls.setVisibility(View.GONE); cropIndex = -1; renderItems();
    }

    private void renderState(ImageToolViewModel.State state) {
        ResultUi.update(this,state.working,state.message,state.error,selectedNames(),model.outputFile,model.outputName,model.outputMime);
        progress.setVisibility(View.GONE); // The shared panel owns indeterminate processing feedback.
        findViewById(R.id.buttonChoose).setEnabled(!state.working);
        findViewById(R.id.buttonCamera).setEnabled(!state.working);
        option.setEnabled(!state.working);
        ToolIo.enabled(itemsContainer, !state.working);
        ToolIo.enabled(cropControls, !state.working);
        widthInput.setEnabled(!state.working); heightInput.setEnabled(!state.working); targetInput.setEnabled(!state.working);
        processButton.setEnabled(!state.working && !model.items.isEmpty());
        if (state.error != null) status.setText(state.error); else if (state.message != null) status.setText(state.message);
        resultActions.setVisibility(model.outputBytes == null ? View.GONE : View.VISIBLE);
    }

    private void writeOutput(@Nullable Uri uri) {
        if (uri != null) {
            if (model.outputBytes != null) ToolIo.save(this, uri, model.outputBytes);
            else ToolIo.missingResult(this, uri);
        }
    }

    private String selectedNames(){StringBuilder names=new StringBuilder();for(ImageToolViewModel.Item item:model.items){if(names.length()>0)names.append("\n");names.append(item.name);}return names.toString();}

    private void share() {
        if (model.outputBytes != null && model.outputName != null && model.outputMime != null) ToolIo.share(this, model.outputBytes, model.outputName, model.outputMime);
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) { int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (index >= 0 && !cursor.isNull(index)) return new File(cursor.getString(index)).getName(); }
        }
        return uri.getLastPathSegment() == null ? "image" : new File(uri.getLastPathSegment()).getName();
    }

    private static int number(TextInputEditText input, int fallback) { try { return Integer.parseInt(input.getText() == null ? "" : input.getText().toString()); } catch (NumberFormatException ignored) { return fallback; } }
    private static int crop(EditText input) { try { return Math.max(0, Math.min(45, Integer.parseInt(input.getText().toString()))); } catch (Exception ignored) { return 0; } }
    private static String point(float x,float y) { return Math.round(x)+","+Math.round(y); }
    private static float[] point(EditText input,float fallbackX,float fallbackY) { try { String[] values=input.getText().toString().split(","); return new float[] {Math.max(0,Math.min(100,Float.parseFloat(values[0].trim()))),Math.max(0,Math.min(100,Float.parseFloat(values[1].trim())))}; } catch(Exception ignored) { return new float[] {fallbackX,fallbackY}; } }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static boolean isKnown(@Nullable String value) { return "image-convert".equals(value) || "image-pdf".equals(value) || "scanner".equals(value) || "prepare-upload".equals(value); }
}

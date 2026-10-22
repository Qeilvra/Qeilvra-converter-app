package com.fileflow.app;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import android.widget.FrameLayout;
import com.google.android.gms.ads.AdView;

public final class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ToolIo.insets(this);
        MaterialCardView imageCompressor = findViewById(R.id.cardImageCompressor);
        imageCompressor.setOnClickListener(view -> startActivity(new Intent(this, ImageCompressActivity.class)));
        bindTool(R.id.buttonPdfExcel, "pdf-excel");
        bindTool(R.id.buttonPdfWord, "pdf-word");
        bindTool(R.id.buttonWordPdf, "word-pdf");
        bindTool(R.id.buttonPdfCompress, "pdf-compress");
        bindTool(R.id.buttonPdfEditor, "pdf-editor");
        bindImageTool(R.id.buttonImagePdf, "image-pdf");
        bindImageTool(R.id.buttonImageConvert, "image-convert");
        bindImageTool(R.id.buttonScanner, "scanner");
        bindImageTool(R.id.buttonPrepare, "prepare-upload");
        findViewById(R.id.buttonRecent).setOnClickListener(view -> startActivity(new Intent(this, RecentActivity.class)));
        findViewById(R.id.buttonSettings).setOnClickListener(view -> startActivity(new Intent(this, SettingsActivity.class)));
        AdsManager.requestConsentAndLoadTestBanner(this, (FrameLayout) findViewById(R.id.adContainer));
    }

    private void bindTool(int viewId, String tool) {
        MaterialButton button = findViewById(viewId);
        button.setOnClickListener(view -> startActivity(new Intent(this, DocumentToolActivity.class).putExtra(DocumentToolActivity.EXTRA_TOOL, tool)));
    }

    private void bindImageTool(int viewId, String tool) {
        MaterialButton button = findViewById(viewId);
        button.setOnClickListener(view -> startActivity(new Intent(this, ImageToolActivity.class).putExtra(ImageToolActivity.EXTRA_TOOL, tool)));
    }

    @Override protected void onDestroy() {
        FrameLayout container = findViewById(R.id.adContainer);
        if (container != null && container.getChildCount() > 0 && container.getChildAt(0) instanceof AdView) {
            ((AdView) container.getChildAt(0)).destroy();
            container.removeAllViews();
        }
        super.onDestroy();
    }
}

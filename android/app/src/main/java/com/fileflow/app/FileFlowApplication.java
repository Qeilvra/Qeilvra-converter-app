package com.fileflow.app;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

public final class FileFlowApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(getSharedPreferences("fileflow.settings", MODE_PRIVATE).getInt("nightMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        PDFBoxResourceLoader.init(getApplicationContext());
    }
}

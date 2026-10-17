package com.fileflow.app;

import android.app.Application;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

public final class FileFlowApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PDFBoxResourceLoader.init(getApplicationContext());
    }
}

package com.fileflow.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded background workers for provider reads, image inspection and document writes. */
final class ToolIo {
    interface Work<T> { T run() throws Exception; }
    interface Result<T> { void accept(T result); }
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private ToolIo() {}

    static void insets(AppCompatActivity activity) {
        View content = activity.findViewById(android.R.id.content);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(content);
    }

    static <T> void run(AppCompatActivity activity, Work<T> work, Result<T> success, Result<String> failure) {
        IO.execute(() -> {
            try {
                T result = work.run();
                MAIN.post(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) success.accept(result);
                    else if (result instanceof android.graphics.Bitmap) ((android.graphics.Bitmap) result).recycle();
                });
            } catch (Exception | OutOfMemoryError error) {
                String message = error instanceof OutOfMemoryError ? "Not enough memory. Try a smaller file or fewer pages." : error.getMessage();
                MAIN.post(() -> { if (!activity.isFinishing() && !activity.isDestroyed()) failure.accept(message == null ? "The operation failed. Try again." : message); });
            }
        });
    }

    static void enabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) enabled(group.getChildAt(i), enabled);
        }
    }

    static void save(AppCompatActivity activity, Uri uri, byte[] bytes) {
        run(activity, () -> {
            if (bytes.length == 0) throw new IllegalStateException("The output is empty. Process the file again before saving.");
            try (OutputStream output = activity.getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IllegalStateException("The selected location is unavailable.");
                output.write(bytes); output.flush();
            } catch (Exception error) {
                try { activity.getContentResolver().delete(uri, null, null); } catch (RuntimeException ignored) {}
                throw new java.io.IOException("Saving failed. Check free storage and choose a writable location. " + (error.getMessage() == null ? "" : error.getMessage()), error);
            }
            return true;
        }, ignored -> Toast.makeText(activity, R.string.file_saved_successfully, Toast.LENGTH_LONG).show(), message -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    static void missingResult(AppCompatActivity activity, Uri uri) {
        run(activity, () -> {
            // CreateDocument may return after Android killed the process and its in-memory result.
            try { activity.getContentResolver().delete(uri, null, null); } catch (RuntimeException ignored) {}
            return true;
        }, ignored -> Toast.makeText(activity, "The result is no longer available. Process the file again before saving.", Toast.LENGTH_LONG).show(), message -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    static void share(AppCompatActivity activity, byte[] bytes, String name, String mime) {
        run(activity, () -> {
            File directory = new File(activity.getCacheDir(), "shared");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Could not prepare the share cache.");
            String safe = name.replaceAll("[^a-zA-Z0-9._-]", "-");
            if (safe.length() > 120) safe = safe.substring(safe.length() - 120);
            // Each share retains its own bytes if another result is generated before the recipient reads it.
            File file = new File(directory, java.util.UUID.randomUUID() + "-" + safe);
            try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); output.flush(); }
            return FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", file);
        }, uri -> {
            Intent intent = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(android.content.ClipData.newRawUri(name, uri));
            try { activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_output))); }
            catch (android.content.ActivityNotFoundException error) { Toast.makeText(activity, "No app is available to share this file.", Toast.LENGTH_LONG).show(); }
        }, message -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }
}

package com.fileflow.app;

import android.app.Application;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.fileflow.app.processing.BitmapProcessor;
import com.fileflow.app.processing.ImageCompressor;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageToolViewModel extends AndroidViewModel {
    public static final class Item extends BitmapProcessor.ImageEdit {
        @NonNull public final String name;
        Item(@NonNull Uri uri, @NonNull String name) { super(uri); this.name = name; }
    }
    public static final class State {
        public final boolean working;
        @Nullable public final String error;
        @Nullable public final String message;
        State(boolean working, @Nullable String error, @Nullable String message) { this.working = working; this.error = error; this.message = message; }
    }

    final ArrayList<Item> items = new ArrayList<>();
    @Nullable byte[] outputBytes;
    @Nullable String outputName;
    @Nullable String outputMime;
    @Nullable Uri pendingCameraUri;
    private int generation;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, null, "Ready."));

    public ImageToolViewModel(@NonNull Application application) { super(application); }
    LiveData<State> state() { return state; }
    void changed() { outputBytes = null; outputName = null; outputMime = null; state.setValue(new State(false, null, "Ready to process.")); }

    void process(String tool, int option, int targetKb, int maxWidth, int maxHeight) {
        if (items.isEmpty()) return;
        final int request = ++generation;
        state.setValue(new State(true, null, "Processing locally…"));
        executor.execute(() -> {
            try {
                byte[] bytes;
                String name;
                String mime;
                String resultMessage = null;
                if ("image-convert".equals(tool)) {
                    String format = option == 1 ? "png" : option == 2 ? "webp" : "jpg";
                    bytes = BitmapProcessor.convert(getApplication().getContentResolver(), items.get(0), format, 90, 4096);
                    name = base(items.get(0).name) + "." + format;
                    mime = "jpg".equals(format) ? "image/jpeg" : "image/" + format;
                } else if ("prepare-upload".equals(tool)) {
                    if (targetKb < 10 || targetKb > 10240 || maxWidth < 100 || maxHeight < 100) throw new IllegalArgumentException("Use dimensions of at least 100 px and a target from 10 KB to 10 MB.");
                    ImageCompressor.Result result = ImageCompressor.compress(getApplication().getContentResolver(), items.get(0).uri, targetKb * 1024, 90, maxWidth, maxHeight);
                    bytes = result.bytes;
                    name = base(items.get(0).name) + "-ready.jpg";
                    mime = "image/jpeg";
                    if (!result.reachedTarget) resultMessage = "Target not reached; measured output is " + formatBytes(bytes.length) + ".";
                } else {
                    if ("scanner".equals(tool)) for (Item item : items) item.scanFilter = option;
                    bytes = BitmapProcessor.imagesToPdf(getApplication().getContentResolver(), new ArrayList<>(items));
                    name = "scanner".equals(tool) ? "fileflow-scan.pdf" : "images.pdf";
                    mime = "application/pdf";
                }
                if (request != generation) return;
                outputBytes = bytes; outputName = name; outputMime = mime;
                HistoryStore.add(getApplication(), operationName(tool), name, bytes.length);
                state.postValue(new State(false, null, resultMessage == null ? "Output ready · " + formatBytes(bytes.length) : resultMessage));
            } catch (Exception error) {
                if (request == generation) state.postValue(new State(false, error.getMessage() == null ? "Image processing failed." : error.getMessage(), null));
            }
        });
    }

    private static String base(String name) {
        String value = name.replaceFirst("\\.[^.]+$", "").replaceAll("[^a-zA-Z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return value.isEmpty() ? "fileflow-image" : value;
    }
    private static String operationName(String tool) { if ("image-convert".equals(tool)) return "Image conversion"; if ("prepare-upload".equals(tool)) return "Prepare for Upload"; if ("scanner".equals(tool)) return "Document scan"; return "Image to PDF"; }
    private static String formatBytes(long bytes) { if (bytes < 1024) return bytes + " B"; if (bytes < 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024f); return String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f)); }
    @Override protected void onCleared() { generation++; executor.shutdownNow(); }
}

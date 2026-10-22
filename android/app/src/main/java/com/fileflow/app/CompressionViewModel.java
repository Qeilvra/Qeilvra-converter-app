package com.fileflow.app;

import android.app.Application;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.fileflow.app.processing.ImageCompressor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CompressionViewModel extends AndroidViewModel {
    public static final class UiState {
        public final boolean working;
        @Nullable public final String error;
        public final int targetKb;

        private UiState(boolean working, @Nullable String error, int targetKb) {
            this.working = working;
            this.error = error;
            this.targetKb = targetKb;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<UiState> state = new MutableLiveData<>(new UiState(false, null, 100));
    @Nullable Uri sourceUri;
    @Nullable byte[] outputBytes;
    @Nullable String sourceName;
    long sourceSize;
    int outputWidth;
    int outputHeight;
    boolean targetReached;

    public CompressionViewModel(@NonNull Application application) {
        super(application);
    }

    LiveData<UiState> state() {
        return state;
    }

    void compress(int targetKb, int quality, int maxLongEdge) {
        Uri source = sourceUri;
        UiState current = state.getValue();
        if (source == null || (current != null && current.working)) return;
        state.setValue(new UiState(true, null, targetKb));
        outputBytes = null;
        executor.execute(() -> {
            try {
                ImageCompressor.Result result = ImageCompressor.compress(
                        getApplication().getContentResolver(), source, targetKb * 1024, quality, maxLongEdge);
                outputBytes = result.bytes;
                outputWidth = result.width;
                outputHeight = result.height;
                targetReached = result.reachedTarget;
                HistoryStore.add(getApplication(), "Image compression", (sourceName == null ? "image" : sourceName.replaceFirst("\\.[^.]+$", "")) + "-compressed.jpg", result.bytes.length);
                state.postValue(new UiState(false, null, targetKb));
            } catch (Exception | OutOfMemoryError error) {
                String message = error instanceof OutOfMemoryError ? "Not enough memory. Try a smaller image." : error.getMessage() == null ? "Compression failed. Try a smaller image." : error.getMessage();
                state.postValue(new UiState(false, message, targetKb));
            }
        });
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
    }
}

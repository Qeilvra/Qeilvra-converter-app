package com.fileflow.app;

import android.app.Application;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.fileflow.app.processing.OfficeOpenXml;
import com.fileflow.app.processing.PdfProcessor;
import com.fileflow.app.processing.LocalDocumentProcessor;
import com.fileflow.app.processing.LocalPdfCompressor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DocumentToolViewModel extends AndroidViewModel {
    public static final class PageEdit {
        public final int pageIndex;
        public int rotation;
        PageEdit(int pageIndex) { this.pageIndex = pageIndex; }
    }
    public static final class State {
        public final boolean working;
        @Nullable public final String error;
        @Nullable public final String message;
        private State(boolean working, @Nullable String error, @Nullable String message) {
            this.working = working; this.error = error; this.message = message;
        }
    }

    final ArrayList<Uri> uris = new ArrayList<>();
    final ArrayList<PageEdit> pages = new ArrayList<>();
    @Nullable byte[] outputBytes;
    @Nullable String outputName;
    @Nullable String outputMime;
    @Nullable String previewText;
    @Nullable String sourceName;
    long sourceSize;
    boolean allowOcr;
    int wordPdfEngine;
    private volatile int generation;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, null, null));

    public DocumentToolViewModel(@NonNull Application application) { super(application); }
    LiveData<State> state() { return state; }

    void select(List<Uri> selected) {
        generation++;
        uris.clear(); uris.addAll(selected);
        pages.clear();
        outputBytes = null; outputName = null; outputMime = null; previewText = null;
        state.setValue(new State(false, null, "Ready to process."));
    }

    void preparePages() {
        if (uris.size() != 1) return;
        final int request = ++generation;
        final Uri source = uris.get(0);
        state.setValue(new State(true, null, "Reading PDF pages…"));
        executor.execute(() -> {
            try {
                int count = PdfProcessor.pageCount(getApplication().getContentResolver(), source);
                if (request != generation) return;
                pages.clear();
                for (int index = 0; index < count; index++) pages.add(new PageEdit(index));
                state.postValue(new State(false, null, count + " pages ready to arrange."));
            } catch (Exception | OutOfMemoryError error) {
                if (request == generation) state.postValue(new State(false, message(error, "Could not read PDF pages."), null));
            }
        });
    }

    void preparePreview(String tool) {
        if (uris.isEmpty()) return;
        final int request = ++generation;
        final Uri source = uris.get(0);
        state.setValue(new State(true, null, "Analyzing securely…"));
        executor.execute(() -> {
            try {
                String text;
                if ("pdf-excel".equals(tool)) text = LocalDocumentProcessor.extractTables(getApplication().getContentResolver(), source, allowOcr, message -> { if (request == generation) state.postValue(new State(true, null, message)); });
                else throw new IllegalArgumentException("This conversion does not use a text preview.");
                if (request != generation) return;
                previewText = text;
                state.postValue(new State(false, null, "Review the extracted content, then create the output."));
            } catch (Exception | OutOfMemoryError error) {
                if (request == generation) state.postValue(new State(false, message(error, "Could not read this document."), null));
            }
        });
    }

    void process(String tool, int editorAction, String editedText) {
        if (uris.isEmpty() || isWorking()) return;
        final int request = ++generation;
        outputBytes = null; outputName = null; outputMime = null;
        state.setValue(new State(true, null, "word-pdf".equals(tool) && editorAction == 2 ? "Uploading to the selected office renderer…" : "Processing locally…"));
        executor.execute(() -> {
            try {
                byte[] bytes;
                String name;
                String mime;
                String base = safeBaseName(sourceName);
                String detail = "";
                switch (tool) {
                    case "pdf-excel":
                        bytes = OfficeOpenXml.createXlsxFromTsv(editedText);
                        name = base + ".xlsx";
                        mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                        break;
                    case "pdf-word":
                        bytes = LocalDocumentProcessor.pdfToDocx(getApplication().getContentResolver(), uris.get(0), allowOcr, message -> { if (request == generation) state.postValue(new State(true, null, message)); });
                        name = base + ".docx";
                        mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                        break;
                    case "word-pdf":
                        if (editorAction == 0) throw new IllegalArgumentException("Open this document in an installed office app and choose Export as PDF.");
                        if (editorAction == 2) {
                            if (BuildConfig.PROCESSING_API_URL.trim().isEmpty()) throw new IllegalArgumentException("No office rendering service is configured.");
                            bytes = com.fileflow.app.processing.ProcessingApiClient.docxToPdf(getApplication().getContentResolver(), uris.get(0));
                        } else bytes = LocalDocumentProcessor.docxToPdf(getApplication().getContentResolver(), uris.get(0));
                        name = base + ".pdf";
                        mime = "application/pdf";
                        break;
                    case "pdf-compress":
                        LocalPdfCompressor.Result compressed = LocalPdfCompressor.compress(getApplication().getContentResolver(), uris.get(0), editorAction);
                        bytes = compressed.bytes;
                        detail = compressed.reduced ? " · " + compressed.imagesOptimized + " images optimized" : " · No safe reduction found; original retained";
                        name = base + "-optimized.pdf";
                        mime = "application/pdf";
                        break;
                    case "pdf-editor":
                        if (editorAction == 0) {
                            if (uris.size() != 1) throw new IllegalArgumentException("Select one PDF to edit pages.");
                            ArrayList<Integer> indexes = new ArrayList<>();
                            ArrayList<Integer> rotations = new ArrayList<>();
                            for (PageEdit page : pages) { indexes.add(page.pageIndex); rotations.add(page.rotation); }
                            bytes = PdfProcessor.editPages(getApplication().getContentResolver(), uris.get(0), indexes, rotations);
                            name = base + "-edited.pdf"; mime = "application/pdf";
                        } else if (editorAction == 1) {
                            bytes = PdfProcessor.merge(getApplication().getContentResolver(), uris);
                            name = "merged.pdf"; mime = "application/pdf";
                        } else if (editorAction == 2) {
                            if (uris.size() != 1) throw new IllegalArgumentException("Select one PDF to rotate.");
                            bytes = PdfProcessor.rotateAll(getApplication().getContentResolver(), uris.get(0), 90);
                            name = base + "-rotated.pdf"; mime = "application/pdf";
                        } else {
                            if (uris.size() != 1) throw new IllegalArgumentException("Select one PDF to split.");
                            bytes = PdfProcessor.splitToZip(getApplication().getContentResolver(), uris.get(0));
                            name = base + "-pages.zip"; mime = "application/zip";
                        }
                        break;
                    default: throw new IllegalArgumentException("Unknown document tool.");
                }
                if (request != generation) return;
                outputBytes = bytes; outputName = name; outputMime = mime;
                HistoryStore.add(getApplication(), operationName(tool, editorAction), name, bytes.length);
                String ready = "Output ready · " + formatBytes(bytes.length) + detail;
                state.postValue(new State(false, null, ready));
            } catch (Exception | OutOfMemoryError error) {
                if (request == generation) state.postValue(new State(false, message(error, "Processing failed."), null));
            }
        });
    }

    boolean isWorking() { State value = state.getValue(); return value != null && value.working; }

    private static String message(Throwable error, String fallback) {
        if (error instanceof OutOfMemoryError) return "Not enough memory. Try a smaller document or fewer pages.";
        return error.getMessage() == null || error.getMessage().trim().isEmpty() ? fallback : error.getMessage();
    }

    private static String operationName(String tool, int editorAction) {
        if ("pdf-excel".equals(tool)) return "PDF to Excel";
        if ("pdf-word".equals(tool)) return "PDF to Word";
        if ("word-pdf".equals(tool)) return "Word to PDF";
        if ("pdf-compress".equals(tool)) return "PDF compression";
        if (editorAction == 0) return "Edit PDF pages";
        if (editorAction == 1) return "Merge PDFs";
        if (editorAction == 2) return "Rotate PDF";
        return "Split PDF";
    }

    private static String safeBaseName(@Nullable String name) {
        String value = name == null ? "fileflow-output" : name.replaceFirst("\\.[^.]+$", "").replaceAll("[^a-zA-Z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return value.isEmpty() ? "fileflow-output" : value;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024f);
        return String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f));
    }

    @Override protected void onCleared() { generation++; executor.shutdownNow(); }
}

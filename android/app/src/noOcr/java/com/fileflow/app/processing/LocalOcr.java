package com.fileflow.app.processing;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import java.io.IOException;
import java.util.List;

/** The default small APK has no OCR engine, binary or language-model dependency. */
final class LocalOcr {
    private LocalOcr() { }
    static List<LocalDocumentModel.Line> read(PDDocument pdf, int page) throws IOException {
        throw new IOException("This build does not include OCR. Use the optional offline OCR build or select a searchable PDF.");
    }
}

package com.fileflow.app.processing;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import org.junit.Test;

public final class WordPdfAvailabilityTest {
    @Test public void documentConversionNeverFallsThroughToTextReflowOrReadsTheSource() {
        IOException error = assertThrows(IOException.class, () -> LocalDocumentProcessor.docxToPdf(null, null));
        assertTrue(error.getMessage().contains("no embedded DOCX layout renderer"));
        assertTrue(error.getMessage().contains("No PDF was created"));
    }
}

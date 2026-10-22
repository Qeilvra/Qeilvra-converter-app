package com.fileflow.app.processing;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class ProcessingSafetyTest {
    @Test public void normalizesBackendOriginAndAcceptsDocumentedApiBase() throws Exception {
        assertEquals("https://processing.example/api/v1", ProcessingEndpoint.normalize(" https://processing.example/ ", false));
        assertEquals("https://processing.example/api/v1", ProcessingEndpoint.normalize("https://processing.example/api/v1/", false));
        assertEquals("http://10.0.2.2:8080/api/v1", ProcessingEndpoint.normalize("http://10.0.2.2:8080", true));
    }
    @Test public void rejectsCredentialAndEmulatorPrefixBypasses() {
        for (String url : new String[]{"http://10.0.2.2.evil.example/api/v1", "http://10.0.2.2@evil.example/api/v1", "https://user:password@example.com", "https://example.com?x=1", "https://example.com/#fragment", "https://example.com/wrong", "file:///document"}) {
            assertThrows(url, IOException.class, () -> ProcessingEndpoint.normalize(url, true));
        }
        assertThrows(IOException.class, () -> ProcessingEndpoint.normalize("http://10.0.2.2", false));
        assertThrows(IOException.class, () -> ProcessingEndpoint.normalize("", false));
    }
    @Test public void boundsUntrustedStreamsAndPreservesBytesAtLimit() throws Exception {
        byte[] data = "bounded document".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(data, BoundedStreams.read(new ByteArrayInputStream(data), data.length));
        assertThrows(IOException.class, () -> BoundedStreams.read(new ByteArrayInputStream(data), data.length - 1));
        assertEquals(0, BoundedStreams.copy(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), 1));
    }
    @Test public void rejectsHtmlAndUnrelatedZipAsWordOutput() throws Exception {
        assertThrows(IOException.class, () -> OfficeOpenXml.validateDocx("<html>404</html>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> OfficeOpenXml.validateDocx(OfficeOpenXml.createXlsxFromTsv("value")));
        OfficeOpenXml.validateDocx(OfficeOpenXml.createDocx("Editable text"));
    }

    @Test public void rejectsCrossedNonFiniteAndCollapsedScannerCorners() throws Exception {
        DocumentCorners.validate(new float[]{4,4,96,4,96,96,4,96});
        for (float[] points : new float[][]{{0,0,100,100,100,0,0,100}, {0,0,0,0,0,0,0,0}, {Float.NaN,0,100,0,100,100,0,100}, {-1,0,100,0,100,100,0,100}}) assertThrows(IOException.class, () -> DocumentCorners.validate(points));
    }
}

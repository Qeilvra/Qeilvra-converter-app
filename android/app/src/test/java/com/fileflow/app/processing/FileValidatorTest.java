package com.fileflow.app.processing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FileValidatorTest {
    @Test
    public void recognizesJpegPngAndWebpHeaders() {
        assertTrue(FileValidator.isSupportedImageHeader(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}, 3));
        assertTrue(FileValidator.isSupportedImageHeader(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a}, 8));
        assertTrue(FileValidator.isSupportedImageHeader(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}, 12));
    }

    @Test
    public void rejectsExtensionsAndMimeTypesWithoutMatchingBytes() {
        assertFalse(FileValidator.isSupportedImageHeader(new byte[] {'n', 'o', 't', '-', 'a', 'n', '-', 'i', 'm', 'a', 'g', 'e'}, 12));
        assertFalse(FileValidator.isSupportedImageHeader(new byte[] {(byte) 0xff, (byte) 0xd8}, 2));
    }

    @Test
    public void detectsPdfAndOpenXmlZipSignatures() {
        assertTrue(FileValidator.detectHeader(new byte[] {'%', 'P', 'D', 'F', '-'}, 5) == FileValidator.FileKind.PDF);
        assertTrue(FileValidator.detectHeader(new byte[] {'P', 'K', 3, 4}, 4) == FileValidator.FileKind.DOCX);
    }
}

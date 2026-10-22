package com.fileflow.server.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fileflow.server.config.ProcessingProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ResourceSafetyTest {
    @TempDir Path temp;
    private ProcessingProperties properties(Duration timeout) {
        return new ProcessingProperties(50_000_000, 500, timeout, 1, "http://localhost", "soffice", "qpdf", "pdf2docx {input} {output}");
    }
    @Test void rejectsZipThatOnlyPretendsToBeDocxAndUnsafePaths() throws Exception {
        var validator = new DocumentValidator(properties(Duration.ofSeconds(20)));
        for (String name : List.of("word/document.xml", "../word/document.xml")) {
            try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes)) {
                zip.putNextEntry(new ZipEntry(name)); zip.write("<invalid/>".getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); zip.finish();
                assertThrows(ProcessingException.class, () -> validator.require(new MockMultipartFile("file", bytes.toByteArray()), DocumentValidator.Kind.DOCX));
            }
        }
    }
    @Test void rejectsExternallyLinkedImageButAcceptsEmbeddedDocx() throws Exception {
        var validator = new DocumentValidator(properties(Duration.ofSeconds(20)));
        try (var document = new XWPFDocument(); var bytes = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Safe content"); document.write(bytes);
            assertArrayEquals(bytes.toByteArray(), validator.require(new MockMultipartFile("file", bytes.toByteArray()), DocumentValidator.Kind.DOCX));
            document.getPackagePart().addRelationship(java.net.URI.create("https://example.invalid/"), TargetMode.EXTERNAL,
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink");
            bytes.reset(); document.write(bytes);
            assertArrayEquals(bytes.toByteArray(), validator.require(new MockMultipartFile("file", bytes.toByteArray()), DocumentValidator.Kind.DOCX));
            document.getPackagePart().addRelationship(java.net.URI.create("https://example.invalid/private.png"), TargetMode.EXTERNAL,
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image");
            bytes.reset(); document.write(bytes);
            assertThrows(ProcessingException.class, () -> validator.require(new MockMultipartFile("file", bytes.toByteArray()), DocumentValidator.Kind.DOCX));
        }
    }
    @Test void workspaceCannotEscapeAndDeletesContentsOnClose() throws Exception {
        Path root;
        try (var workspace = JobWorkspace.create()) {
            root = workspace.root();
            Files.writeString(workspace.resolve("private.txt"), "Private content");
            assertThrows(IllegalArgumentException.class, () -> workspace.resolve("../escape.txt"));
            assertThrows(IllegalArgumentException.class, () -> workspace.resolve(root.getParent().resolve("escape.txt").toString()));
        }
        assertFalse(Files.exists(root));
    }
    @Test void rejectsMacroEnabledWordDisguisedAsDocx() throws Exception {
        var validator = new DocumentValidator(properties(Duration.ofSeconds(20)));
        try (var document = new XWPFDocument(); var bytes = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Macro-enabled package");
            document.getPackagePart().setContentType("application/vnd.ms-word.document.macroEnabled.main+xml");
            document.write(bytes);
            assertThrows(ProcessingException.class, () -> validator.require(new MockMultipartFile("file", "looks-safe.docx", "application/octet-stream", bytes.toByteArray()), DocumentValidator.Kind.DOCX));
        }
    }
    @Test void engineProbesAndTimeoutsAreTruthfulAndTimedOutProcessIsReaped() throws Exception {
        Path executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Process[] started = new Process[1];
        var runner = new ProcessRunner(properties(Duration.ofMillis(500))) {
            @Override protected Process start(List<String> command, Path workspace) throws java.io.IOException {
                return started[0] = super.start(command, workspace);
            }
        };
        assertTrue(runner.available(List.of(executable.toString(), "-version")));
        assertFalse(runner.available(List.of("fileflow-no-such-executable")));
        var error = assertThrows(ProcessingException.class, () -> runner.run(List.of(executable.toString(), "-cp",
                System.getProperty("java.class.path"), SlowEngine.class.getName(), temp.resolve("pid.txt").toString()), Path.of(System.getProperty("java.io.tmpdir")), "Test engine"));
        assertEquals(504, error.status());
        assertNotNull(started[0]);
        assertFalse(started[0].isAlive(), "A timed-out engine must terminate even if it never reached its main method");
        Path pid = temp.resolve("pid.txt");
        if (Files.exists(pid)) assertFalse(ProcessHandle.of(Long.parseLong(Files.readString(pid))).map(ProcessHandle::isAlive).orElse(false));
    }
    public static class SlowEngine {
        public static void main(String[] args) throws Exception {
            Files.writeString(Path.of(args[0]), Long.toString(ProcessHandle.current().pid()));
            Thread.sleep(30_000);
        }
    }
}

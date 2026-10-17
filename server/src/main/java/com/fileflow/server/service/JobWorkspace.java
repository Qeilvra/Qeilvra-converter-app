package com.fileflow.server.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class JobWorkspace implements AutoCloseable {
    private final Path root;

    private JobWorkspace(Path root) { this.root = root; }

    public static JobWorkspace create() {
        try {
            return new JobWorkspace(Files.createTempDirectory("fileflow-job-"));
        } catch (IOException error) {
            throw new ProcessingException("Could not create an isolated processing workspace.", error);
        }
    }

    public Path root() { return root; }
    public Path resolve(String name) { return root.resolve(name); }

    @Override
    public void close() {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}

package com.fileflow.server.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JobWorkspace implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(JobWorkspace.class);
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
    public Path resolve(String name) {
        Path resolved = root.resolve(name).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Workspace paths must remain inside the job directory.");
        return resolved;
    }

    @Override
    public void close() {
        int[] failures = {0};
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException error) { failures[0]++; }
            });
        } catch (IOException error) { failures[0]++; }
        // Do not include uploaded filenames, paths, or contents in operational logs.
        if (failures[0] > 0) LOG.error("Temporary document cleanup incomplete ({} filesystem operations failed). Operator cleanup is required.", failures[0]);
    }
}

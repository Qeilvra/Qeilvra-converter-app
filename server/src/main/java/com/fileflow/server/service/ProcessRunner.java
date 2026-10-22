package com.fileflow.server.service;

import com.fileflow.server.config.ProcessingProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ProcessRunner {
    private final ProcessingProperties properties;
    private final Semaphore slots;

    public ProcessRunner(ProcessingProperties properties) {
        this.properties = properties;
        this.slots = new Semaphore(Math.max(1, properties.concurrency()), true);
    }

    public void run(List<String> command, Path workspace, String operation) {
        run(command, workspace, operation, properties.timeout());
    }

    public void run(List<String> command, Path workspace, String operation, Duration timeout) {
        boolean acquired = false;
        Process process = null;
        try {
            acquired = slots.tryAcquire(10, TimeUnit.SECONDS);
            if (!acquired) throw new ProcessingException(503, "The conversion service is busy. Try again shortly.");
            process = start(command, workspace);
            boolean finished = process.waitFor(Math.max(1, Math.min(properties.timeout().toMillis(), timeout.toMillis())), TimeUnit.MILLISECONDS);
            if (!finished) {
                throw new ProcessingException(504, operation + " exceeded the processing time limit.");
            }
            if (process.exitValue() != 0) throw new ProcessingException(operation + " failed (engine exit " + process.exitValue() + ").");
        } catch (IOException error) {
            throw new ProcessingException(503, operation + " engine is not installed or could not be started.", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProcessingException(operation + " was interrupted.", error);
        } finally {
            try { stop(process); }
            finally { if (acquired) slots.release(); }
        }
    }

    protected Process start(List<String> command, Path workspace) throws IOException {
        return new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    }

    public boolean available(List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException error) {
            return false;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        } finally { stop(process); }
    }

    private static void stop(Process process) {
        if (process == null) return;
        // Conversion engines may spawn subprocesses; terminate those before releasing the job workspace.
        List<ProcessHandle> children = process.descendants().toList();
        children.forEach(child -> { if (child.isAlive()) child.destroyForcibly(); });
        if (process.isAlive()) process.destroyForcibly();
        boolean interrupted = Thread.interrupted();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            process.waitFor(10, TimeUnit.SECONDS);
            for (ProcessHandle child : children) {
                long remaining = deadline - System.nanoTime();
                if (child.isAlive() && remaining > 0) {
                    try { child.onExit().get(remaining, TimeUnit.NANOSECONDS); }
                    catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException error) { /* checked below */ }
                }
            }
            if (process.isAlive() || children.stream().anyMatch(ProcessHandle::isAlive))
                throw new ProcessingException(503, "A conversion engine could not be stopped. The service operator must inspect worker processes and temporary files.");
        } catch (InterruptedException error) {
            interrupted = true;
            throw new ProcessingException(503, "Engine cleanup was interrupted. The service operator must inspect worker processes and temporary files.");
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
}

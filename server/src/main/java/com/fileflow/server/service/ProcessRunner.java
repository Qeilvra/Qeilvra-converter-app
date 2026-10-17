package com.fileflow.server.service;

import com.fileflow.server.config.ProcessingProperties;
import java.io.IOException;
import java.nio.file.Path;
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
        boolean acquired = false;
        try {
            acquired = slots.tryAcquire(10, TimeUnit.SECONDS);
            if (!acquired) throw new ProcessingException("The conversion service is busy. Try again shortly.");
            Process process = new ProcessBuilder(command)
                    .directory(workspace.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ProcessingException(operation + " exceeded the processing time limit.");
            }
            if (process.exitValue() != 0) throw new ProcessingException(operation + " failed (engine exit " + process.exitValue() + ").");
        } catch (IOException error) {
            throw new ProcessingException(operation + " engine is not installed or could not be started.", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProcessingException(operation + " was interrupted.", error);
        } finally {
            if (acquired) slots.release();
        }
    }
}

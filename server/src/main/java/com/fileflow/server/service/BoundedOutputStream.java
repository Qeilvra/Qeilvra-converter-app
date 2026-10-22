package com.fileflow.server.service;

import java.io.ByteArrayOutputStream;

/** Avoid allocating an unbounded download while serializing PDFs and workbooks. */
final class BoundedOutputStream extends ByteArrayOutputStream {
    private static final long LIMIT = 100L * 1024 * 1024;
    private void requireCapacity(int added) {
        if ((long) count + added > LIMIT) throw new ProcessingException("The output exceeds the 100 MB safe download limit. Split the document and try again.");
    }
    @Override public synchronized void write(int value) { requireCapacity(1); super.write(value); }
    @Override public synchronized void write(byte[] values, int offset, int length) { requireCapacity(length); super.write(values, offset, length); }
}

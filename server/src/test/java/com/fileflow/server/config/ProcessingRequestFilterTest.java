package com.fileflow.server.config;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ProcessingRequestFilterTest {
    private ProcessingRequestFilter filter(int rate) {
        return new ProcessingRequestFilter(new ProcessingProperties(50_000_000, 500, Duration.ofSeconds(20), 1,
                "http://localhost", "soffice", "qpdf", "pdf2docx {input} {output}"), rate);
    }
    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/api/v1/pdf/tables/xlsx");
    }

    @Test void enforcesRateLimitAndSendsRetryHeader() throws Exception {
        var filter = filter(1);
        filter.doFilter(request(), new MockHttpServletResponse(), (req, res) -> {});
        var rejected = new MockHttpServletResponse();
        filter.doFilter(request(), rejected, (req, res) -> fail("Rate-limited request must not reach processing"));
        assertEquals(429, rejected.getStatus());
        assertEquals("60", rejected.getHeader("Retry-After"));
    }

    @Test void boundsConcurrentRequestsAndReleasesPermitAfterFailure() throws Exception {
        var filter = filter(30);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread first = new Thread(() -> {
            try { filter.doFilter(request(), new MockHttpServletResponse(), (req, res) -> {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test wait expired"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            }); } catch (Throwable error) { failure.set(error); }
        });
        first.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var busy = new MockHttpServletResponse();
            filter.doFilter(request(), busy, (req, res) -> fail("Busy request must not process"));
            assertEquals(503, busy.getStatus());
        } finally { release.countDown(); first.join(5000); }
        assertNull(failure.get());
        assertThrows(IOException.class, () -> filter.doFilter(request(), new MockHttpServletResponse(), (req, res) -> { throw new IOException("Simulated processing failure"); }));
        var recovered = new MockHttpServletResponse();
        filter.doFilter(request(), recovered, (req, res) -> res.getWriter().write("ok"));
        assertEquals("ok", recovered.getContentAsString());
    }

    @Test void capsJsonBothForKnownLengthAndChunkedRequests() throws Exception {
        var filter = filter(30);
        byte[] content = new byte[8 * 1024 * 1024 + 1];
        var known = request(); known.setContentType("application/json"); known.setContent(content);
        var rejected = new MockHttpServletResponse();
        filter.doFilter(known, rejected, (req, res) -> fail("Large JSON must not be parsed"));
        assertEquals(413, rejected.getStatus());
        var chunked = new MockHttpServletRequest("POST", "/api/v1/pdf/tables/xlsx") {
            @Override public long getContentLengthLong() { return -1; }
        };
        chunked.setContentType("application/json"); chunked.setContent(content);
        assertThrows(IOException.class, () -> filter.doFilter(chunked, new MockHttpServletResponse(), (req, res) -> req.getInputStream().readAllBytes()));
    }
}

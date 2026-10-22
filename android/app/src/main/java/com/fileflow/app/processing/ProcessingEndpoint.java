package com.fileflow.app.processing;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/** Shared URL validation; emulator HTTP is restricted to debug builds. */
public final class ProcessingEndpoint {
    private ProcessingEndpoint() {}

    public static String normalize(String value, boolean debug) throws IOException {
        String base = value == null ? "" : value.trim().replaceAll("/+$", "");
        if (base.isEmpty()) throw new IOException("Document processing is not configured. Set fileflowProcessingApiUrl to the HTTPS backend URL ending in /api/v1.");
        try {
            URI uri = new URI(base);
            boolean secure = "https".equalsIgnoreCase(uri.getScheme());
            boolean emulator = debug && "http".equalsIgnoreCase(uri.getScheme()) && "10.0.2.2".equals(uri.getHost());
            if ((!secure && !emulator) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IOException("The processing service must use an HTTPS URL without credentials, query parameters or a fragment.");
            }
            String path = uri.getPath();
            // Accept the deployment origin as well as the documented API base.
            if (path == null || path.isEmpty()) base += "/api/v1";
            else if (!path.endsWith("/api/v1")) throw new IOException("The processing service URL must end in /api/v1.");
            return base;
        } catch (URISyntaxException error) {
            throw new IOException("The processing service URL is invalid.", error);
        }
    }
}

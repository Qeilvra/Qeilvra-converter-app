package com.fileflow.app.processing;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.fileflow.app.BuildConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ProcessingApiClient {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 140_000;

    private ProcessingApiClient() {}

    @NonNull public static byte[] pdfToDocx(ContentResolver resolver, Uri uri) throws IOException { return upload(resolver, uri, "/convert/pdf-to-docx", "application/pdf"); }
    @NonNull public static byte[] docxToPdf(ContentResolver resolver, Uri uri) throws IOException { return upload(resolver, uri, "/convert/docx-to-pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"); }
    @NonNull public static byte[] compressPdf(ContentResolver resolver, Uri uri, @NonNull String level) throws IOException {
        return upload(resolver, uri, "/pdf/compress?level=" + level, "application/pdf", true);
    }

    @NonNull
    public static String extractTablesTsv(ContentResolver resolver, Uri uri) throws Exception {
        byte[] response = upload(resolver, uri, "/pdf/tables", "application/pdf");
        JSONObject root = new JSONObject(new String(response, StandardCharsets.UTF_8));
        if (root.optBoolean("scanned")) throw new IOException("This scanned PDF requires OCR before table extraction.");
        JSONArray tables = root.getJSONArray("tables");
        if (tables.length() == 0) throw new IOException("No table structure was detected.");
        StringBuilder output = new StringBuilder();
        for (int tableIndex = 0; tableIndex < tables.length(); tableIndex++) {
            if (tableIndex > 0) output.append("\n\n");
            JSONArray rows = tables.getJSONObject(tableIndex).getJSONArray("rows");
            for (int rowIndex = 0; rowIndex < rows.length(); rowIndex++) {
                if (rowIndex > 0) output.append('\n');
                JSONArray row = rows.getJSONArray(rowIndex);
                for (int column = 0; column < row.length(); column++) {
                    if (column > 0) output.append('\t');
                    output.append(row.getJSONObject(column).optString("value").replace('\t', ' ').replace('\n', ' '));
                }
            }
        }
        return output.toString();
    }

    @NonNull
    private static byte[] upload(ContentResolver resolver, Uri uri, String path, String contentType) throws IOException {
        return upload(resolver, uri, path, contentType, false);
    }

    @NonNull
    private static byte[] upload(ContentResolver resolver, Uri uri, String path, String contentType, boolean requireReduction) throws IOException {
        String base = BuildConfig.PROCESSING_API_URL == null ? "" : BuildConfig.PROCESSING_API_URL.trim().replaceAll("/+$", "");
        if (base.isEmpty()) throw new IOException("Secure document processing is not configured. Set fileflowProcessingApiUrl to the HTTPS service URL.");
        if (!base.startsWith("https://") && !base.startsWith("http://10.0.2.2")) throw new IOException("The processing service must use HTTPS.");
        String boundary = "FileFlow-" + java.util.UUID.randomUUID();
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/octet-stream, application/json");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try {
            try (OutputStream output = connection.getOutputStream(); InputStream input = resolver.openInputStream(uri)) {
                if (input == null) throw new IOException("The selected file is unavailable.");
                output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                byte[] buffer = new byte[32 * 1024]; int read;
                while ((read = input.read(buffer)) != -1) { if (Thread.currentThread().isInterrupted()) throw new IOException("Processing was cancelled."); output.write(buffer, 0, read); }
                output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            }
            int status = connection.getResponseCode();
            byte[] body = readAll(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                try { throw new IOException(new JSONObject(new String(body, StandardCharsets.UTF_8)).optString("error", "Processing failed.")); }
                catch (org.json.JSONException ignored) { throw new IOException("The processing service returned " + status + "."); }
            }
            if (requireReduction && !"true".equalsIgnoreCase(connection.getHeaderField("X-FileFlow-Reduced"))) {
                throw new IOException("No safe reduction was found. The original file was kept.");
            }
            if (body.length == 0) throw new IOException("The processing service returned an empty result.");
            return body;
        } finally { connection.disconnect(); }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        if (input == null) return new byte[0];
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32 * 1024]; int read;
            while ((read = stream.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }
}

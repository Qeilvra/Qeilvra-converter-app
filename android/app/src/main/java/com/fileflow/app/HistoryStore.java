package com.fileflow.app;

import android.content.Context;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class HistoryStore {
    private static final String PREFS = "fileflow.private";
    private static final String KEY = "history.v1";
    private HistoryStore() {}

    public static final class Entry {
        public final String operation;
        public final String outputName;
        public final long outputSize;
        public final long createdAt;
        Entry(String operation, String outputName, long outputSize, long createdAt) { this.operation = operation; this.outputName = outputName; this.outputSize = outputSize; this.createdAt = createdAt; }
    }

    public static synchronized void add(@NonNull Context context, @NonNull String operation, @NonNull String outputName, long outputSize) {
        try {
            JSONArray old = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            JSONArray next = new JSONArray();
            next.put(new JSONObject().put("operation", operation).put("outputName", outputName).put("outputSize", outputSize).put("createdAt", System.currentTimeMillis()));
            for (int index = 0; index < Math.min(29, old.length()); index++) next.put(old.get(index));
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    @NonNull public static synchronized List<Entry> read(@NonNull Context context) {
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                entries.add(new Entry(item.getString("operation"), item.getString("outputName"), item.getLong("outputSize"), item.getLong("createdAt")));
            }
        } catch (Exception ignored) {}
        return entries;
    }

    public static void clear(@NonNull Context context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply(); }
}

package com.fileflow.app;

import android.content.Context;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class HistoryStore {
    private static final String PREFS = "fileflow.private";
    private static final String KEY = "history.v1";
    private HistoryStore() {}

    public static final class Entry {
        public final String operation;
        public final String outputName;
        public final long outputSize;
        public final long createdAt;
        public final String id,mime;
        public final File file;
        Entry(String operation,String outputName,long outputSize,long createdAt,String id,String mime,File file){this.operation=operation;this.outputName=outputName;this.outputSize=outputSize;this.createdAt=createdAt;this.id=id;this.mime=mime;this.file=file;}
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
                String id=item.optString("id","legacy-"+index+"-"+item.getLong("createdAt"));
                entries.add(new Entry(item.getString("operation"),item.getString("outputName"),item.getLong("outputSize"),item.getLong("createdAt"),id,item.optString("mime","application/octet-stream"),managedFile(context,item.optString("file",""))));
            }
        } catch (Exception ignored) {}
        return entries;
    }

    /** Called only by existing background processing workers, never on the UI thread. */
    public static synchronized File addResult(Context context,String operation,String name,String mime,byte[] bytes) throws IOException {
        if(bytes==null || bytes.length==0 || bytes.length>100L*1024*1024)throw new IOException("Output is empty or exceeds the 100 MB private-history limit.");
        File directory=new File(context.getFilesDir(),"recent");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot prepare private output storage.");
        String id=java.util.UUID.randomUUID().toString();File file=new File(directory,id),partial=new File(directory,id+".part");
        try{
            try(FileOutputStream out=new FileOutputStream(partial)){out.write(bytes);out.flush();out.getFD().sync();}
            if(partial.length()!=bytes.length || !partial.renameTo(file))throw new IOException("Could not retain the generated file.");
            JSONArray previous=new JSONArray(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"[]")),next=new JSONArray();
            next.put(new JSONObject().put("id",id).put("file",id).put("mime",mime).put("operation",operation).put("outputName",name).put("outputSize",bytes.length).put("createdAt",System.currentTimeMillis()));
            long retained=bytes.length;java.util.ArrayList<File> pruned=new java.util.ArrayList<>();
            for(int i=0;i<previous.length();i++){JSONObject item=previous.getJSONObject(i);File old=managedFile(context,item.optString("file",""));long size=old!=null&&old.isFile()?old.length():0;
                if(next.length()<30 && retained+size<=100L*1024*1024){next.put(item);retained+=size;}else if(old!=null)pruned.add(old);}
            if(!context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,next.toString()).commit())throw new IOException("Could not save recent history.");
            for(File old:pruned)old.delete();
            return file;
        }catch(Exception error){partial.delete();file.delete();throw error instanceof IOException?(IOException)error:new IOException("Could not retain output.",error);}
    }
    private static File managedFile(Context c,String name){return name.matches("[a-f0-9-]{36}")?new File(new File(c.getFilesDir(),"recent"),name):null;}
    public static synchronized void remove(Context c,String id){try{JSONArray old=new JSONArray(c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"[]")),next=new JSONArray();for(int i=0;i<old.length();i++){JSONObject item=old.getJSONObject(i);String key=item.optString("id","legacy-"+i+"-"+item.getLong("createdAt"));if(key.equals(id)){File f=managedFile(c,item.optString("file",""));if(f!=null)f.delete();}else next.put(item);}c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,next.toString()).apply();}catch(Exception ignored){}}
    public static synchronized void clear(@NonNull Context context) {for(Entry entry:read(context))if(entry.file!=null)entry.file.delete();context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply(); }
}

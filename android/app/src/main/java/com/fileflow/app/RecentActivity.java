package com.fileflow.app;

import android.os.Bundle;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import com.fileflow.app.ui.*;
import androidx.core.content.FileProvider;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Calendar;

public final class RecentActivity extends AppCompatActivity {
    private ListView list;private TextView empty;private BaseAdapter adapter;
    private String filter="All",query="",pendingId;
    private final ArrayList<Object> rows=new ArrayList<>();
    private final ActivityResultLauncher<String> save=registerForActivityResult(new ActivityResultContracts.CreateDocument("*/*"),uri->{if(uri!=null){HistoryStore.Entry entry=find(pendingId);if(available(entry)){ToolIo.run(this,()->{long length=entry.file.length();if(length>100L*1024*1024)throw new java.io.IOException("File exceeds the supported size.");byte[] bytes=new byte[(int)length];try(java.io.DataInputStream in=new java.io.DataInputStream(new java.io.FileInputStream(entry.file))){in.readFully(bytes);}return bytes;},bytes->ToolIo.save(this,uri,bytes),message->Toast.makeText(this,message,Toast.LENGTH_LONG).show());}else ToolIo.missingResult(this,uri);}});
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_recent);
        ToolIo.insets(this);
        Ui.attachBottom(this,"recent");
        list=findViewById(R.id.historyList);empty=findViewById(R.id.recentEmpty);
        if(savedInstanceState!=null){filter=savedInstanceState.getString("filter","All");query=savedInstanceState.getString("query","");pendingId=savedInstanceState.getString("pendingId");}
        findViewById(R.id.buttonBack).setOnClickListener(view -> finish());
        findViewById(R.id.buttonClear).setOnClickListener(view -> new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.clear_history).setMessage("Remove private retained copies and history? Copies saved to other locations are kept.").setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.clear_history,(d,w)->{HistoryStore.clear(this);render();}).show());
        EditText search=findViewById(R.id.recentSearch);search.setText(query);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();render();}});
        findViewById(R.id.recentSearchButton).setOnClickListener(v->{search.setVisibility(search.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);if(search.getVisibility()==View.VISIBLE){search.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}});
        adapter=new BaseAdapter(){public int getCount(){return rows.size();}public Object getItem(int p){return rows.get(p);}public long getItemId(int p){return p;}public int getViewTypeCount(){return 2;}public int getItemViewType(int p){return rows.get(p) instanceof String?0:1;}public boolean isEnabled(int p){return rows.get(p) instanceof HistoryStore.Entry;}
            public View getView(int pos,View old,ViewGroup parent){Object item=rows.get(pos);if(item instanceof String){TextView t=old instanceof TextView?(TextView)old:Ui.text(RecentActivity.this,"",18,true);t.setText((String)item);t.setPadding(0,Ui.dp(RecentActivity.this,16),0,Ui.dp(RecentActivity.this,8));return t;}return fileRow((HistoryStore.Entry)item,old);}};
        list.setAdapter(adapter);list.setEmptyView(empty);renderFilters();render();
    }
    private void render() {
        if(adapter==null)return;rows.clear();String previous="";
        for(HistoryStore.Entry entry:HistoryStore.read(this)){String ext=extension(entry.outputName);boolean image=ext.matches("jpg|jpeg|png|webp");if(!filter.equals("All") && !(filter.equals("PDF")&&ext.equals("pdf")) && !(filter.equals("Images")&&image) && !(filter.equals("Docs")&&!image&&!ext.equals("pdf")))continue;if(!entry.outputName.toLowerCase(Locale.getDefault()).contains(query.toLowerCase(Locale.getDefault())))continue;String group=dateGroup(entry.createdAt);if(!group.equals(previous)){rows.add(group);previous=group;}rows.add(entry);}
        empty.setText(HistoryStore.read(this).isEmpty()?getString(R.string.ui_recent_empty)+"\n\n"+getString(R.string.ui_recent_hint):"No matching files");adapter.notifyDataSetChanged();
    }
    private void renderFilters(){LinearLayout group=findViewById(R.id.recentFilters);group.removeAllViews();for(String item:new String[]{"All","PDF","Images","Docs"}){TextView chip=Ui.filter(this,item,item.equals(filter),()->{filter=item;renderFilters();render();Ui.entrance(list,220);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,Ui.dp(this,48));p.setMarginEnd(Ui.dp(this,8));group.addView(chip,p);}}
    private View fileRow(HistoryStore.Entry entry,View old){LinearLayout row;if(old instanceof LinearLayout)row=(LinearLayout)old;else{row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,4),Ui.dp(this,12));row.setMinimumHeight(Ui.dp(this,82));row.setBackgroundResource(R.drawable.bg_glass);row.addView(new FileTile(this,0xFF087CFF,0),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));LinearLayout texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMarginStart(Ui.dp(this,10));row.addView(texts,p);TextView name=Ui.text(this,"",14,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);texts.addView(name);TextView meta=Ui.text(this,"",11,false);meta.setPadding(0,Ui.dp(this,5),0,0);texts.addView(meta);ImageButton more=new ImageButton(this);more.setImageResource(R.drawable.ic_more);more.setBackgroundColor(android.graphics.Color.TRANSPARENT);row.addView(more,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));Ui.press(row);}
        String ext=extension(entry.outputName);((FileTile)row.getChildAt(0)).setKind(ext.equals("pdf")?0xFFFF5267:ext.equals("xlsx")?0xFF00BF8F:0xFF087CFF,ext.matches("jpg|png|webp|jpeg")?1:ext.equals("xlsx")?2:0);LinearLayout texts=(LinearLayout)row.getChildAt(1);((TextView)texts.getChildAt(0)).setText(entry.outputName);((TextView)texts.getChildAt(1)).setText(ext.toUpperCase(Locale.ROOT)+" · "+bytes(entry.outputSize)+" · "+DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(entry.createdAt))+(available(entry)?"":" · Unavailable"));row.getChildAt(2).setContentDescription("Actions for "+entry.outputName);row.getChildAt(2).setOnClickListener(v->actions(v,entry));row.setOnClickListener(v->{if(available(entry))ResultUi.open(this,entry.file,entry.mime,entry.outputName);else new androidx.appcompat.app.AlertDialog.Builder(this).setMessage(R.string.ui_legacy_history).setPositiveButton(android.R.string.ok,null).show();});return row;
    }
    private void actions(View anchor,HistoryStore.Entry entry){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(0,1,0,R.string.ui_open).setEnabled(available(entry));
        menu.getMenu().add(0,2,1,R.string.save_output).setEnabled(available(entry));
        menu.getMenu().add(0,3,2,R.string.share_output).setEnabled(available(entry));
        menu.getMenu().add(0,4,3,R.string.ui_details);menu.getMenu().add(0,5,4,R.string.ui_remove);
        menu.setOnMenuItemClickListener(item->{
            switch(item.getItemId()){
                case 1:ResultUi.open(this,entry.file,entry.mime,entry.outputName);break;
                case 2:pendingId=entry.id;save.launch(entry.outputName);break;
                case 3:
                    android.net.Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",entry.file,entry.outputName);
                    Intent share=new Intent(Intent.ACTION_SEND).setType(entry.mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    share.setClipData(ClipData.newRawUri(entry.outputName,uri));
                    try{startActivity(Intent.createChooser(share,getString(R.string.share_output)));}
                    catch(ActivityNotFoundException ex){Toast.makeText(this,"No sharing app is installed.",Toast.LENGTH_LONG).show();}break;
                case 4:new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(entry.outputName).setMessage(entry.operation+"\n"+entry.mime+"\n"+bytes(entry.outputSize)+"\n"+DateFormat.getDateTimeInstance().format(new Date(entry.createdAt))+"\n"+(available(entry)?"Private on-device copy":getString(R.string.ui_legacy_history))).setPositiveButton(android.R.string.ok,null).show();break;
                case 5:HistoryStore.remove(this,entry.id);render();break;
            }return true;
        });menu.show();
    }
    private static boolean available(HistoryStore.Entry e){return e!=null&&e.file!=null&&e.file.isFile()&&e.file.length()>0;}
    private HistoryStore.Entry find(String id){for(HistoryStore.Entry e:HistoryStore.read(this))if(e.id.equals(id))return e;return null;}
    private String dateGroup(long time){Calendar today=Calendar.getInstance();today.set(Calendar.HOUR_OF_DAY,0);today.set(Calendar.MINUTE,0);today.set(Calendar.SECOND,0);today.set(Calendar.MILLISECOND,0);if(time>=today.getTimeInMillis())return getString(R.string.ui_today);today.add(Calendar.DAY_OF_MONTH,-1);return time>=today.getTimeInMillis()?getString(R.string.ui_yesterday):DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(time));}
    private static String extension(String n){int dot=n.lastIndexOf('.');return dot<0?"file":n.substring(dot+1).toLowerCase(Locale.ROOT);}
    @Override protected void onResume(){super.onResume();render();}
    @Override protected void onSaveInstanceState(Bundle b){b.putString("filter",filter);b.putString("query",query);b.putString("pendingId",pendingId);super.onSaveInstanceState(b);}
    static String bytes(long value) { if (value < 1024) return value + " B"; if (value < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", value / 1024f); return String.format(Locale.getDefault(), "%.1f MB", value / (1024f * 1024f)); }
}

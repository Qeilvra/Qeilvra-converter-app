package com.fileflow.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.util.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import com.fileflow.app.ui.Ui;

public final class MainActivity extends AppCompatActivity {
    private String screen="home",category="All",query="";
    private FrameLayout content;
    private android.widget.EditText search;
    private final ArrayList<ToolCatalog.Tool> filtered=new ArrayList<>();
    private BaseAdapter toolsAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ToolIo.insets(this);
        content=findViewById(R.id.screenContent);search=findViewById(R.id.toolSearch);
        if(savedInstanceState!=null){category=savedInstanceState.getString("category","All");query=savedInstanceState.getString("query","");screen=savedInstanceState.getString("screen","home");}else if("tools".equals(getIntent().getStringExtra("screen")))screen="tools";
        search.setText(query);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString();filter();}public void afterTextChanged(Editable e){}});
        findViewById(R.id.headerNavigation).setOnClickListener(v->{if(screen.equals("tools"))show("home");else Ui.quick(this);});
        findViewById(R.id.headerSearch).setOnClickListener(v->{show("tools");search.setVisibility(search.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);if(search.getVisibility()==View.VISIBLE){search.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}});
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){public void handleOnBackPressed(){if(screen.equals("tools")){show("home");return;}setEnabled(false);getOnBackPressedDispatcher().onBackPressed();}});
        show(screen);Ui.entrance(findViewById(R.id.shellTitle),580);
    }
    private void show(String value){
        releaseAd();screen=value;content.removeAllViews();boolean home=screen.equals("home");
        ((TextView)findViewById(R.id.shellTitle)).setText(home?R.string.app_name:R.string.ui_tools);
        if(home){android.text.SpannableString wordmark=new android.text.SpannableString(getString(R.string.app_name));if(wordmark.length()>=8)wordmark.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.blue_primary)),4,8,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);((TextView)findViewById(R.id.shellTitle)).setText(wordmark);}
        ((ImageButton)findViewById(R.id.headerNavigation)).setImageResource(home?R.drawable.ic_menu:R.drawable.ic_back);
        findViewById(R.id.headerNavigation).setContentDescription(getString(home?R.string.ui_menu:R.string.back));
        if(home){search.setVisibility(View.GONE);search.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);}
        getLayoutInflater().inflate(home?R.layout.screen_home:R.layout.screen_tools,content,true);
        Ui.bottom(this,findViewById(R.id.bottomNavigation),screen,key->{if(key.equals("home")||key.equals("tools"))show(key);else Ui.navigate(this,key);});
        if(home){
            if(getResources().getConfiguration().fontScale>=1.3f){findViewById(R.id.homeArtwork).setVisibility(View.GONE);TextView heading=findViewById(R.id.homeHeroHeading);heading.setPaddingRelative(0,0,0,0);((LinearLayout.LayoutParams)findViewById(R.id.homeHeroEyebrow).getLayoutParams()).topMargin=0;}
            findViewById(R.id.chooseTool).setOnClickListener(v->show("tools"));Ui.press(findViewById(R.id.chooseTool));findViewById(R.id.seeAllTools).setOnClickListener(v->show("tools"));
            LinearLayout grid=findViewById(R.id.popularGrid);
            // Two columns normally, one at large accessibility font sizes.
            int columns=getResources().getConfiguration().fontScale>=1.45f?1:2;
            for(int i=0;i<6;i+=columns){LinearLayout line=new LinearLayout(this);grid.addView(line);for(int j=i;j<Math.min(i+columns,6);j++){ToolCatalog.Tool tool=ToolCatalog.ALL[j];View card=ToolCatalog.row(this,tool,true,null);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.bottomMargin=Ui.dp(this,10);if(j>i)p.setMarginStart(Ui.dp(this,10));line.addView(card,p);card.setOnClickListener(v->ToolCatalog.open(this,tool));}}
            AdsManager.requestConsentAndLoadTestBanner(this,findViewById(R.id.adContainer));
        }else{
            toolsAdapter=new BaseAdapter(){public int getCount(){return filtered.size();}public Object getItem(int p){return filtered.get(p);}public long getItemId(int p){return filtered.get(p).id;}public View getView(int p,View old,ViewGroup parent){ToolCatalog.Tool tool=filtered.get(p);View row=ToolCatalog.row(MainActivity.this,tool,false,old);row.setOnClickListener(v->ToolCatalog.open(MainActivity.this,tool));return row;}};
            ListView list=findViewById(R.id.toolsList);list.setAdapter(toolsAdapter);list.setOnItemClickListener((p,v,pos,id)->ToolCatalog.open(this,filtered.get(pos)));renderFilters();filter();
        }
        Ui.entrance(content,240);
    }
    private void renderFilters(){LinearLayout group=findViewById(R.id.categoryFilters);group.removeAllViews();for(String item:new String[]{"All","PDF","Image","Document"}){TextView chip=Ui.filter(this,item,item.equals(category),()->{category=item;renderFilters();filter();Ui.entrance(findViewById(R.id.toolsList),220);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,Ui.dp(this,48));p.setMarginEnd(Ui.dp(this,8));group.addView(chip,p);}}
    private void filter(){if(!screen.equals("tools")||toolsAdapter==null)return;filtered.clear();for(ToolCatalog.Tool t:ToolCatalog.ALL)if((category.equals("All")||category.equals(t.category))&&(getString(t.title)+" "+getString(t.description)).toLowerCase(Locale.getDefault()).contains(query.toLowerCase(Locale.getDefault())))filtered.add(t);toolsAdapter.notifyDataSetChanged();ListView list=findViewById(R.id.toolsList);if(list!=null){View empty=findViewById(R.id.toolsEmpty);if(empty==null){TextView t=Ui.text(this,getString(R.string.ui_no_matching_tools),16,false);t.setId(R.id.toolsEmpty);t.setGravity(Gravity.CENTER);((ViewGroup)list.getParent()).addView(t,new LinearLayout.LayoutParams(-1,Ui.dp(this,80)));empty=t;}list.setEmptyView(empty);}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);show("tools".equals(intent.getStringExtra("screen"))?"tools":"home");}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("screen",screen);out.putString("category",category);out.putString("query",query);super.onSaveInstanceState(out);}
    private void releaseAd(){FrameLayout box=findViewById(R.id.adContainer);if(box!=null){for(int i=0;i<box.getChildCount();i++)if(box.getChildAt(i) instanceof com.google.android.gms.ads.AdView)((com.google.android.gms.ads.AdView)box.getChildAt(i)).destroy();box.removeAllViews();}}
    @Override protected void onPause(){super.onPause();FrameLayout box=findViewById(R.id.adContainer);if(box!=null)for(int i=0;i<box.getChildCount();i++)if(box.getChildAt(i) instanceof com.google.android.gms.ads.AdView)((com.google.android.gms.ads.AdView)box.getChildAt(i)).pause();}
    @Override protected void onResume(){super.onResume();FrameLayout box=findViewById(R.id.adContainer);if(box!=null)for(int i=0;i<box.getChildCount();i++)if(box.getChildAt(i) instanceof com.google.android.gms.ads.AdView)((com.google.android.gms.ads.AdView)box.getChildAt(i)).resume();}
    @Override protected void onDestroy(){releaseAd();super.onDestroy();}
}

package com.fileflow.app.ui;

import android.animation.ValueAnimator;
import android.content.*;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import com.fileflow.app.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public final class Ui {
    private Ui(){}
    public static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static boolean motionEnabled(Context c){return Build.VERSION.SDK_INT>=26?ValueAnimator.areAnimatorsEnabled():Settings.Global.getFloat(c.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;}
    public static void entrance(View view,long duration){if(motionEnabled(view.getContext())){view.setAlpha(0);view.setTranslationY(dp(view.getContext(),6));view.animate().alpha(1).translationY(0).setDuration(duration).start();}}
    public static void press(View view){view.setOnTouchListener((v,e)->{if(motionEnabled(v.getContext())){float s=e.getActionMasked()==MotionEvent.ACTION_DOWN?.97f:1;v.animate().scaleX(s).scaleY(s).setDuration(e.getActionMasked()==MotionEvent.ACTION_DOWN?160:220).start();}return false;});}
    public static TextView text(Context c,CharSequence value,int sp,boolean bold){TextView t=new TextView(c);t.setText(value);t.setTextSize(sp);t.setTextColor(c.getColor(bold?R.color.navy:R.color.secondary_text));if(bold)t.setTypeface(t.getTypeface(),android.graphics.Typeface.BOLD);return t;}
    public static GradientDrawable round(int color,float radius,Context c){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    public static void decorate(AppCompatActivity a){
        View content=a.findViewById(android.R.id.content);boolean night=(a.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        content.setBackground(new GlassBackdrop(a.getColor(R.color.background),night));
        new WindowInsetsControllerCompat(a.getWindow(),content).setAppearanceLightStatusBars(!night);
        new WindowInsetsControllerCompat(a.getWindow(),content).setAppearanceLightNavigationBars(!night);
    }
    public static void bottom(AppCompatActivity a,LinearLayout bar,String selected,java.util.function.Consumer<String> action){
        bar.removeAllViews();
        String[] names={a.getString(R.string.ui_home),a.getString(R.string.ui_recent),"",a.getString(R.string.ui_tools),a.getString(R.string.settings)};
        String[] keys={"home","recent","quick","tools","settings"};int[] icons={R.drawable.ic_home,R.drawable.ic_recent,R.drawable.ic_plus,R.drawable.ic_tools,R.drawable.ic_settings};
        for(int i=0;i<keys.length;i++){
            final String key=keys[i];LinearLayout cell=new LinearLayout(a);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);cell.setMinimumHeight(dp(a,64));
            bar.addView(cell,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1));
            if(i==2){ImageButton plus=new ImageButton(a);plus.setImageResource(icons[i]);plus.setImageTintList(ColorStateList.valueOf(Color.WHITE));plus.setBackgroundResource(R.drawable.bg_primary_button);plus.setElevation(dp(a,7));plus.setContentDescription(a.getString(R.string.ui_quick_tools));cell.addView(plus,new LinearLayout.LayoutParams(dp(a,54),dp(a,54)));plus.setOnClickListener(v->quick(a));press(plus);continue;}
            ImageView icon=new ImageView(a);icon.setImageResource(icons[i]);boolean active=key.equals(selected);icon.setImageTintList(ColorStateList.valueOf(a.getColor(active?R.color.blue_primary:R.color.secondary_text)));cell.addView(icon,new LinearLayout.LayoutParams(dp(a,23),dp(a,23)));
            TextView label=text(a,names[i],10,false);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(a,5),0,0);label.setTextColor(a.getColor(active?R.color.blue_deep:R.color.secondary_text));cell.addView(label);
            cell.setId(i==0?R.id.navHome:i==1?R.id.buttonRecent:i==3?R.id.navTools:R.id.buttonSettings);cell.setContentDescription(names[i]);cell.setFocusable(true);cell.setClickable(true);cell.setSelected(active);
            if(active){cell.setBackground(round(a.getColor(R.color.glass_end),18,a));if(motionEnabled(a))icon.animate().translationY(-dp(a,2)).setDuration(240).start();}
            cell.setOnClickListener(v->action.accept(key));press(cell);
        }
    }
    public static void navigate(AppCompatActivity a,String destination){
        if("quick".equals(destination)){quick(a);return;}
        Class<?> target="recent".equals(destination)?RecentActivity.class:"settings".equals(destination)?SettingsActivity.class:MainActivity.class;
        if(a.getClass()==target && !(a instanceof MainActivity))return;
        Intent intent=new Intent(a,target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);if(target==MainActivity.class)intent.putExtra("screen",destination);a.startActivity(intent);
        if(motionEnabled(a))a.overridePendingTransition(R.anim.screen_enter,R.anim.screen_exit);
        if(!(a instanceof MainActivity))a.finish();
    }
    /** Keep the original screen and IDs; only wrap it in a shared native shell. */
    public static void attachBottom(AppCompatActivity a,String selected){
        ViewGroup content=a.findViewById(android.R.id.content);View original=content.getChildAt(0);content.removeView(original);
        LinearLayout shell=new LinearLayout(a);shell.setOrientation(LinearLayout.VERTICAL);shell.addView(original,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bar=(LinearLayout)a.getLayoutInflater().inflate(R.layout.component_bottom_navigation,shell,false);shell.addView(bar);content.addView(shell,new ViewGroup.LayoutParams(-1,-1));bottom(a,bar,selected,key->navigate(a,key));
    }
    public static void quick(AppCompatActivity a){
        BottomSheetDialog sheet=new BottomSheetDialog(a);LinearLayout body=new LinearLayout(a);body.setPadding(dp(a,20),dp(a,20),dp(a,20),dp(a,28));body.setOrientation(LinearLayout.VERTICAL);body.setBackgroundResource(R.drawable.bg_glass);TextView title=text(a,a.getString(R.string.ui_quick_tools),22,true);body.addView(title);
        for(int index:new int[]{0,2,4,5}){ToolCatalog.Tool tool=ToolCatalog.ALL[index];View row=ToolCatalog.row(a,tool,false,null);body.addView(row,new LinearLayout.LayoutParams(-1,dp(a,76)));row.setOnClickListener(v->{sheet.dismiss();ToolCatalog.open(a,tool);});}
        sheet.setContentView(body);sheet.show();
    }
    public static TextView filter(Context c,String title,boolean selected,Runnable click){TextView chip=text(c,title,13,selected);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(c,18),0,dp(c,18),0);chip.setMinHeight(dp(c,48));chip.setBackground(selected?round(0xFF0757DA,16,c):c.getDrawable(R.drawable.bg_glass));if(selected)chip.setTextColor(Color.WHITE);chip.setFocusable(true);chip.setClickable(true);chip.setSelected(selected);chip.setOnClickListener(v->click.run());press(chip);return chip;}
}

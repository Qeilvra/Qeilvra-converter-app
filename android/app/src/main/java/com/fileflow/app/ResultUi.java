package com.fileflow.app;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import com.fileflow.app.ui.Ui;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

/** Genuine engine state only: no timer-driven percentages or invented per-file completion. */
final class ResultUi {
    private static final class PanelState {String phase="";final java.util.Map<View,Integer> hidden=new java.util.HashMap<>();ViewGroup actions,parent;int index;ViewGroup.LayoutParams params;}
    static void update(AppCompatActivity a,boolean working,String message,String error,String names,File output,String outputName,String mime){
        View panel=a.findViewById(R.id.processingPanel);if(panel==null)return;
        boolean ready=!working&&error==null&&output!=null&&output.isFile()&&output.length()>0;boolean show=working || error!=null || ready;
        PanelState state=panel.getTag() instanceof PanelState?(PanelState)panel.getTag():new PanelState();panel.setTag(state);
        if(!ready && state.actions!=null){((ViewGroup)state.actions.getParent()).removeView(state.actions);state.parent.addView(state.actions,Math.min(state.index,state.parent.getChildCount()),state.params);state.actions=null;state.parent=null;}
        if(working && state.hidden.isEmpty()){
            ViewGroup body=(ViewGroup)panel.getParent();for(int i=0;i<body.getChildCount();i++){View child=body.getChildAt(i);if(child==panel || i==0)continue;state.hidden.put(child,child.getVisibility());child.setVisibility(View.GONE);}
        }else if(!working && !state.hidden.isEmpty()){for(java.util.Map.Entry<View,Integer> e:state.hidden.entrySet())e.getKey().setVisibility(e.getValue());state.hidden.clear();}
        String phase=working?"working":ready?"success":"error";boolean changed=!state.phase.equals(phase);state.phase=phase;panel.setVisibility(show?View.VISIBLE:View.GONE);
        if(!ready){View open=a.findViewById(R.id.buttonOpen);if(open!=null)open.setVisibility(View.GONE);}
        if(!show)return;
        TextView heading=a.findViewById(R.id.processingHeading),detail=a.findViewById(R.id.processingDetail),files=a.findViewById(R.id.processingFiles);
        TextView title=a.findViewById(R.id.textTitle);
        CharSequence titleText=title==null?"":title.getText();
        heading.setText(working?(titleText.equals(a.getString(R.string.image_to_pdf)) || titleText.equals(a.getString(R.string.word_to_pdf))?"Converting to PDF":titleText):error!=null?"Couldn’t complete that":"✓  File ready");
        heading.setTextColor(a.getColor(error!=null?R.color.error:ready?R.color.success:R.color.navy));
        detail.setText(error!=null?error:ready?outputName:message==null?a.getString(R.string.processing_locally):message);
        files.setText(working?(names==null?"":names)+"\nProcessing as one operation…":ready?"✓  "+outputName+" · "+RecentActivity.bytes(output.length()):"");
        if(working && names!=null){android.text.SpannableStringBuilder labels=new android.text.SpannableStringBuilder();for(String name:names.split("\n")){if(name.isEmpty())continue;int start=labels.length();labels.append("\uFFFC ").append(name).append('\n');com.fileflow.app.ui.FileTypeDrawable icon=new com.fileflow.app.ui.FileTypeDrawable(name);icon.setBounds(0,0,Ui.dp(a,22),Ui.dp(a,22));labels.setSpan(new android.text.style.ImageSpan(icon,android.text.style.ImageSpan.ALIGN_BOTTOM),start,start+1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}labels.append("Processing as one operation…");files.setText(labels);}
        a.findViewById(R.id.processingBar).setVisibility(working?View.VISIBLE:View.GONE);
        a.findViewById(R.id.processingCancel).setVisibility(working?View.VISIBLE:View.GONE);a.findViewById(R.id.processingCancellationNote).setVisibility(working?View.VISIBLE:View.GONE);
        a.findViewById(R.id.processingArtwork).setVisibility(error==null?View.VISIBLE:View.GONE);
        if(changed){Ui.entrance(panel,ready?400:240);if(ready&&Ui.motionEnabled(a)){heading.setScaleX(.85f);heading.setScaleY(.85f);heading.animate().scaleX(1).scaleY(1).setInterpolator(new android.view.animation.OvershootInterpolator(.7f)).setDuration(400).start();}}
        if(ready){ViewGroup actions=a.findViewById(R.id.resultActions);if(actions!=null)actions.setVisibility(View.VISIBLE);
            if(actions!=null && actions.getParent()!=panel){state.actions=actions;state.parent=(ViewGroup)actions.getParent();state.index=state.parent.indexOfChild(actions);state.params=actions.getLayoutParams();state.parent.removeView(actions);((ViewGroup)panel).addView(actions,new LinearLayout.LayoutParams(-1,-2));}
            if(actions==null){View save=a.findViewById(R.id.buttonShare);if(save!=null)actions=(ViewGroup)save.getParent();}
            if(actions!=null){
                View open=a.findViewById(R.id.buttonOpen);
                if(open==null){com.google.android.material.button.MaterialButton button=new com.google.android.material.button.MaterialButton(a);button.setText(R.string.ui_open);button.setId(R.id.buttonOpen);if(actions instanceof LinearLayout && ((LinearLayout)actions).getOrientation()==LinearLayout.HORIZONTAL)actions.addView(button,Math.min(1,actions.getChildCount()),new LinearLayout.LayoutParams(0,-2,1));else actions.addView(button,new ViewGroup.LayoutParams(-1,-2));open=button;}
                ((TextView)a.findViewById(R.id.buttonSave)).setText(R.string.ui_save_short);((TextView)a.findViewById(R.id.buttonShare)).setText(R.string.ui_share_short);
                for(int i=0;i<actions.getChildCount();i++)if(actions.getChildAt(i) instanceof com.google.android.material.button.MaterialButton){com.google.android.material.button.MaterialButton button=(com.google.android.material.button.MaterialButton)actions.getChildAt(i);button.setInsetTop(0);button.setInsetBottom(0);button.setPadding(Ui.dp(a,6),Ui.dp(a,8),Ui.dp(a,6),Ui.dp(a,8));button.setMinimumHeight(Ui.dp(a,48));button.setMinHeight(Ui.dp(a,48));button.setMaxLines(2);button.getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;}
                open.setVisibility(View.VISIBLE);open.setOnClickListener(v->open(a,output,mime,outputName));
            }
        }
        else {View open=a.findViewById(R.id.buttonOpen);if(open!=null)open.setVisibility(View.GONE);}
    }
    static void open(AppCompatActivity a,File file,String mime,String displayName){
        if(file==null||!file.isFile()){Toast.makeText(a,"This file is no longer available.",Toast.LENGTH_LONG).show();return;}
        try{
            android.net.Uri uri=FileProvider.getUriForFile(a,a.getPackageName()+".files",file,displayName);
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri(displayName,uri));a.startActivity(intent);
        }catch(ActivityNotFoundException ex){Toast.makeText(a,"No installed app can open this file type. Save or share it instead.",Toast.LENGTH_LONG).show();}
        catch(IllegalArgumentException | SecurityException unavailable){Toast.makeText(a,"The private file cannot be opened here. Save a copy to your chosen location instead.",Toast.LENGTH_LONG).show();}
    }
}

package com.fileflow.app.ui;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/** Small vector scene. Motion animates the view transform, not a full-screen blur. */
public final class FileArtworkView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private ObjectAnimator floating;
    public FileArtworkView(Context c, AttributeSet a){super(c,a);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.save();float size=Math.min(getWidth(),getHeight());c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/240,size/240);
        p.setShader(new RadialGradient(110,105,108,new int[]{0x22FFFFFF,0x5570CDFF,0x559465F8,0x00FFFFFF},null,Shader.TileMode.CLAMP));c.drawOval(17,14,224,226,p);
        p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(0xDDFFFFFF);c.drawOval(17,14,224,226,p);p.setStyle(Paint.Style.FILL);
        p.setColor(0xAAFFFFFF);c.drawOval(38,27,80,38,p);p.setColor(0x449465F8);c.drawCircle(211,35,18,p);p.setColor(0x88FFFFFF);c.drawCircle(205,29,5,p);
        c.save();c.rotate(-14,85,96);tile(c,46,55,81,0xFFFF8596,0xFFFF315B,0);c.restore();
        c.save();c.rotate(13,160,117);tile(c,130,87,76,0xFF55D6FF,0xFF087CFF,1);c.restore();
        c.save();c.rotate(-12,121,167);tile(c,84,137,77,0xFF27E2B6,0xFF00A87E,2);c.restore();c.restore();
    }
    public static void tile(Canvas c,float x,float y,float s,int light,int dark,int kind){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor((dark&0xFFFFFF)|0x22000000);c.drawRoundRect(x-3,y+5,x+s+3,y+s+9,s*.24f,s*.24f,p);
        p.setAlpha(255);p.setShader(new LinearGradient(x,y,x+s,y+s,light,dark,Shader.TileMode.CLAMP));c.drawRoundRect(x,y,x+s,y+s,s*.23f,s*.23f,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.014f);p.setColor(0x88FFFFFF);c.drawRoundRect(x+1,y+1,x+s-1,y+s-1,s*.22f,s*.22f,p);
        c.save();c.translate(x+s*.28f,y+s*.23f);c.scale(s/54,s/54);p.setColor(Color.WHITE);p.setStrokeWidth(1.9f);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
        Path path=new Path();path.moveTo(1,1);path.lineTo(16,1);path.lineTo(23,8);path.lineTo(23,29);path.lineTo(1,29);path.close();c.drawPath(path,p);c.drawLine(16,1,16,9,p);c.drawLine(16,9,23,9,p);
        if(kind==1){c.drawCircle(7,11,2,p);path.reset();path.moveTo(2,24);path.lineTo(9,17);path.lineTo(14,21);path.lineTo(19,14);path.lineTo(23,21);c.drawPath(path,p);}
        else {c.drawLine(6,14,18,14,p);c.drawLine(6,19,18,19,p);c.drawLine(6,24,15,24,p);if(kind==2)c.drawLine(12,12,12,26,p);}
        c.restore();
    }
    private void motion(){if(floating!=null){floating.cancel();floating=null;}if(isShown() && Ui.motionEnabled(getContext())){floating=ObjectAnimator.ofFloat(this,"translationY",0,-6*getResources().getDisplayMetrics().density,0);floating.setDuration(6000);floating.setRepeatCount(ObjectAnimator.INFINITE);floating.start();}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();motion();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)motion();else if(floating!=null){floating.cancel();floating=null;}}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(isShown() && getWindowVisibility()==VISIBLE)motion();else if(floating!=null){floating.cancel();floating=null;}}
    @Override protected void onDetachedFromWindow(){if(floating!=null){floating.cancel();floating=null;}super.onDetachedFromWindow();}
}

package com.fileflow.app.ui;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import com.fileflow.app.R;

/** Indeterminate traveling segment; its position is never reported as completion. */
public final class GradientProgressView extends View {
    private final Paint track=new Paint(Paint.ANTI_ALIAS_FLAG),fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF segment=new RectF();private float phase=.5f;private ValueAnimator animator;
    public GradientProgressView(Context c,AttributeSet a){super(c,a);track.setColor(c.getColor(R.color.line));setContentDescription(c.getString(R.string.processing_locally));}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){fill.setShader(new LinearGradient(0,0,w,0,new int[]{0xFF23B6FF,0xFF087CFF,0xFF9465F8},null,Shader.TileMode.CLAMP));}
    @Override protected void onDraw(Canvas c){float r=getHeight()/2f;c.drawRoundRect(0,0,getWidth(),getHeight(),r,r,track);float x=(getWidth()*1.4f)*phase-getWidth()*.4f;segment.set(Math.max(0,x),0,Math.min(getWidth(),x+getWidth()*.4f),getHeight());c.drawRoundRect(segment,r,r,fill);}
    private void update(){if(animator!=null){animator.cancel();animator=null;}if(isShown()&&getWindowVisibility()==VISIBLE&&Ui.motionEnabled(getContext())){animator=ValueAnimator.ofFloat(0,1);animator.setDuration(1400);animator.setRepeatCount(ValueAnimator.INFINITE);animator.setInterpolator(new android.view.animation.LinearInterpolator());animator.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});animator.start();}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();update();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);update();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE){if(animator!=null){animator.cancel();animator=null;}}else update();}
    @Override protected void onDetachedFromWindow(){if(animator!=null){animator.cancel();animator=null;}super.onDetachedFromWindow();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.ProgressBar");}
}

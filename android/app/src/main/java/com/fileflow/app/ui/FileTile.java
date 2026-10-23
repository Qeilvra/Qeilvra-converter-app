package com.fileflow.app.ui;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
public final class FileTile extends View {
    private int light,dark,kind;
    public FileTile(Context c,int color,int kind){super(c);this.dark=color;this.light=blend(color);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    private static int blend(int color){return android.graphics.Color.rgb((android.graphics.Color.red(color)+255)/2,(android.graphics.Color.green(color)+255)/2,(android.graphics.Color.blue(color)+255)/2);}
    public void setKind(int color,int kind){this.dark=color;this.light=blend(color);this.kind=kind;invalidate();}
    @Override protected void onDraw(Canvas canvas){float s=Math.min(getWidth(),getHeight())*.82f;FileArtworkView.tile(canvas,(getWidth()-s)/2,(getHeight()-s)/2-2,s,light,dark,kind);}
}

package com.fileflow.app.ui;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
/** Static ambient light; no RenderEffect, bitmap blur, or continuous invalidation. */
public final class GlassBackdrop extends Drawable {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int base;private final boolean night;
    public GlassBackdrop(int base,boolean night){this.base=base;this.night=night;}
    @Override public void draw(@NonNull Canvas c){Rect b=getBounds();c.drawColor(base);float w=b.width(),h=b.height();if(w<=0||h<=0)return;
        p.setShader(new RadialGradient(w*.12f,h*.09f,w*.85f,night?0x55386ACD:0x9978D3FF,0x00000000,Shader.TileMode.CLAMP));c.drawRect(b,p);
        p.setShader(new RadialGradient(w*.98f,h*.60f,w*.8f,night?0x554E328A:0x889F9BFF,0x00000000,Shader.TileMode.CLAMP));c.drawRect(b,p);
        p.setShader(new RadialGradient(w*.1f,h*.8f,w*.8f,night?0x331E5B84:0x99C0F1FF,0x00000000,Shader.TileMode.CLAMP));c.drawRect(b,p);p.setShader(null);
    }
    @Override public void setAlpha(int a){p.setAlpha(a);}@Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
}

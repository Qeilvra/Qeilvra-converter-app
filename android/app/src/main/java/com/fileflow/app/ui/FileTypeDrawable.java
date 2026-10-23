package com.fileflow.app.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import java.util.Locale;

/** Inline file thumbnail using the same vector tile as tool/history cards. */
public final class FileTypeDrawable extends Drawable {
    private final int color,kind;
    public FileTypeDrawable(String name){
        String lower=name.toLowerCase(Locale.ROOT);
        kind=lower.matches(".*\\.(png|jpg|jpeg|webp)$")?1:lower.endsWith(".xlsx")?2:0;
        color=lower.endsWith(".pdf")?0xFFFF5267:kind==2?0xFF00BF8F:0xFF087CFF;
    }
    @Override public void draw(@NonNull Canvas canvas){Rect b=getBounds();float size=Math.min(b.width(),b.height())*.85f;FileArtworkView.tile(canvas,b.left,b.top,size,color,color,kind);}
    @Override public void setAlpha(int alpha){}
    @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

package com.fileflow.app;
import android.app.Activity;
import android.content.*;
import android.view.*;
import android.widget.*;
import com.fileflow.app.ui.*;

/** One route registry is shared by Home, search, filters and quick actions. */
public final class ToolCatalog {
    public static final class Tool {
        public final String key,category;public final int title,description,id,color,kind;
        Tool(String key,String category,int title,int description,int id,int color,int kind){this.key=key;this.category=category;this.title=title;this.description=description;this.id=id;this.color=color;this.kind=kind;}
    }
    public static final Tool[] ALL={
        new Tool("pdf-excel","PDF",R.string.pdf_to_excel,R.string.ui_desc_excel,R.id.buttonPdfExcel,0xFFFF5267,0),
        new Tool("pdf-word","PDF",R.string.pdf_to_word,R.string.ui_desc_word,R.id.buttonPdfWord,0xFF087CFF,0),
        new Tool("image-pdf","Image",R.string.image_to_pdf,R.string.ui_desc_image_pdf,R.id.buttonImagePdf,0xFF9465F8,1),
        new Tool("pdf-compress","PDF",R.string.pdf_compressor,R.string.ui_desc_compress,R.id.buttonPdfCompress,0xFF00BF8F,0),
        new Tool("scanner","Image",R.string.document_scanner,R.string.ui_desc_scan,R.id.buttonScanner,0xFFFF963F,0),
        new Tool("image-convert","Image",R.string.image_converter,R.string.ui_desc_convert,R.id.buttonImageConvert,0xFF087CFF,1),
        new Tool("image-compress","Image",R.string.image_compressor,R.string.compress_description,R.id.cardImageCompressor,0xFF00BF8F,1),
        new Tool("word-pdf","Document",R.string.word_to_pdf,R.string.ui_desc_word_pdf,R.id.buttonWordPdf,0xFF9465F8,0),
        new Tool("pdf-editor","PDF",R.string.pdf_editor,R.string.ui_desc_editor,R.id.buttonPdfEditor,0xFFFF5267,0),
        new Tool("prepare-upload","Image",R.string.prepare_upload,R.string.ui_desc_prepare,R.id.buttonPrepare,0xFFFF963F,1)
    };
    public static void open(Activity a,Tool tool){Class<?> target=tool.key.equals("image-compress")?ImageCompressActivity.class:tool.category.equals("Image")?ImageToolActivity.class:DocumentToolActivity.class;a.startActivity(new Intent(a,target).putExtra("tool",tool.key));}
    public static View row(Context c,Tool tool,boolean compact,View recycled){
        LinearLayout row;
        if(recycled instanceof LinearLayout){row=(LinearLayout)recycled;}else{
            row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(c,compact?8:12),Ui.dp(c,compact?8:10),Ui.dp(c,compact?8:12),Ui.dp(c,compact?8:10));row.setMinimumHeight(Ui.dp(c,compact?68:82));row.setBackgroundResource(R.drawable.bg_glass);row.setElevation(Ui.dp(c,1));row.setFocusable(true);row.setClickable(true);Ui.press(row);
            FileTile tile=new FileTile(c,tool.color,tool.kind);row.addView(tile,new LinearLayout.LayoutParams(Ui.dp(c,compact?42:54),Ui.dp(c,compact?42:54)));
            LinearLayout text=new LinearLayout(c);text.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(0,-2,1);textParams.setMarginStart(Ui.dp(c,compact?6:12));row.addView(text,textParams);
            TextView title=Ui.text(c,"",compact?14:15,true);text.addView(title);if(!compact){TextView subtitle=Ui.text(c,"",12,false);subtitle.setPadding(0,Ui.dp(c,4),0,0);text.addView(subtitle);ImageView arrow=new ImageView(c);arrow.setImageResource(R.drawable.ic_chevron);row.addView(arrow,new LinearLayout.LayoutParams(Ui.dp(c,18),Ui.dp(c,18)));}
        }
        row.setId(tool.id);((FileTile)row.getChildAt(0)).setKind(tool.color,tool.kind);LinearLayout text=(LinearLayout)row.getChildAt(1);((TextView)text.getChildAt(0)).setText(compact&&tool.key.equals("pdf-compress")?R.string.ui_compress_pdf:compact&&tool.key.equals("scanner")?R.string.ui_scan_document:tool.title);if(!compact)((TextView)text.getChildAt(1)).setText(tool.description);row.setContentDescription(c.getString(tool.title));return row;
    }
}

package com.fileflow.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.content.*;
import android.os.Looper;
import android.net.Uri;
import androidx.lifecycle.ViewModelProvider;
import androidx.appcompat.app.AppCompatDelegate;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, qualifiers="w390dp-h844dp-mdpi", shadows=NativeUiRenderTest.NoAds.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeUiRenderTest {
    @Before public void resetAndroidXProcessCachesBetweenSimulatedApplications() throws Exception {
        // A device has one Application per process. Robolectric creates a new
        // data directory per test while these AndroidX static caches survive.
        for(java.lang.reflect.Field f:ViewModelProvider.AndroidViewModelFactory.class.getDeclaredFields())if(java.lang.reflect.Modifier.isStatic(f.getModifiers())&&f.getType()==ViewModelProvider.AndroidViewModelFactory.class){f.setAccessible(true);f.set(null,null);}
        java.lang.reflect.Field cache=androidx.core.content.FileProvider.class.getDeclaredField("sCache");cache.setAccessible(true);((java.util.Map<?,?>)cache.get(null)).clear();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    @Implements(AdsManager.class) public static class NoAds {
        @Implementation public static void requestConsentAndLoadTestBanner(Activity a, android.widget.FrameLayout c) {}
    }
    @Test public void renderHome() throws Exception {
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            capture(controller.get(),"home");
        }
    }
    @Test public void toolsFilterSearchAndEveryRealRoute() throws Exception {
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();a.findViewById(R.id.navTools).performClick();capture(a,"tools-light");
            ListView list=a.findViewById(R.id.toolsList);assertEquals(10,list.getAdapter().getCount());
            for(int i=0;i<10;i++){
                View row=list.getAdapter().getView(i,null,list);row.performClick();Intent intent=Shadows.shadowOf(a).getNextStartedActivity();assertNotNull(intent);assertEquals(ToolCatalog.ALL[i].key,intent.getStringExtra("tool"));
                if(ToolCatalog.ALL[i].category.equals("Image")&&!ToolCatalog.ALL[i].key.equals("image-compress"))try(ActivityController<ImageToolActivity> tool=Robolectric.buildActivity(ImageToolActivity.class,intent).setup()){assertEquals(a.getString(ToolCatalog.ALL[i].title),((TextView)tool.get().findViewById(R.id.textTitle)).getText().toString());}
                else if(!ToolCatalog.ALL[i].key.equals("image-compress"))try(ActivityController<DocumentToolActivity> tool=Robolectric.buildActivity(DocumentToolActivity.class,intent).setup()){assertEquals(a.getString(ToolCatalog.ALL[i].title),((TextView)tool.get().findViewById(R.id.textTitle)).getText().toString());}
                else try(ActivityController<ImageCompressActivity> tool=Robolectric.buildActivity(ImageCompressActivity.class).setup()){assertNotNull(tool.get().findViewById(R.id.buttonCompress));}
            }
            LinearLayout filters=a.findViewById(R.id.categoryFilters);filters.getChildAt(1).performClick();assertEquals(4,list.getAdapter().getCount());
            filters.getChildAt(0).performClick();((EditText)a.findViewById(R.id.toolSearch)).setText("Word");assertEquals(2,list.getAdapter().getCount());
            ((EditText)a.findViewById(R.id.toolSearch)).setText("zzzz");assertEquals(0,list.getAdapter().getCount());assertEquals(View.VISIBLE,a.findViewById(R.id.toolsEmpty).getVisibility());
        }
    }
    @Test @Config(qualifiers="w390dp-h844dp-night-mdpi") public void renderDarkScreens() throws Exception {
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){capture(c.get(),"home-dark");c.get().findViewById(R.id.navTools).performClick();capture(c.get(),"tools-dark");}
        try(ActivityController<RecentActivity> c=Robolectric.buildActivity(RecentActivity.class).setup()){capture(c.get(),"recent-dark-empty");}
        try(ActivityController<SettingsActivity> c=Robolectric.buildActivity(SettingsActivity.class).setup()){capture(c.get(),"settings-dark");}
        pdfMerge("dark");
    }
    @Test @Config(qualifiers="w320dp-h740dp-mdpi") public void compactLargeFontsAndReducedMotion() throws Exception {
        ReflectionHelpers.callStaticMethod(android.animation.ValueAnimator.class,"setDurationScale",ReflectionHelpers.ClassParameter.from(float.class,0f));
        android.content.res.Configuration config=RuntimeEnvironment.getApplication().getResources().getConfiguration();config.fontScale=1.6f;RuntimeEnvironment.getApplication().getResources().updateConfiguration(config,null);
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            capture(c.get(),"home-320-large-font");assertFalse(com.fileflow.app.ui.Ui.motionEnabled(c.get()));
            TextView section=c.get().findViewById(R.id.popularSectionTitle);assertTrue(section.getHeight()>=section.getLayout().getLineBottom(section.getLineCount()-1));
            View bar=c.get().findViewById(R.id.bottomNavigation);assertTrue(bar.getWidth()<=320);c.get().findViewById(R.id.navTools).performClick();capture(c.get(),"tools-320-large-font");assertEquals(10,((ListView)c.get().findViewById(R.id.toolsList)).getAdapter().getCount());
        }
        ReflectionHelpers.callStaticMethod(android.animation.ValueAnimator.class,"setDurationScale",ReflectionHelpers.ClassParameter.from(float.class,1f));
    }
    @Test @Config(qualifiers="w480dp-h960dp-xhdpi") public void largePhoneDensity() throws Exception {try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){capture(c.get(),"home-480-xhdpi");}}
    @Test public void actualImageSelectionConversionAndRetainedRecent() throws Exception {
        ReflectionHelpers.callStaticMethod(android.animation.ValueAnimator.class,"setDurationScale",ReflectionHelpers.ClassParameter.from(float.class,0f));
        Context app=RuntimeEnvironment.getApplication();HistoryStore.clear(app);
        File source=new File(app.getCacheDir(),"synthetic-logo.png");Bitmap image=Bitmap.createBitmap(96,128,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(0xFF087CFF);try(FileOutputStream out=new FileOutputStream(source)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
        Intent intent=new Intent(app,ImageToolActivity.class).putExtra("tool","image-convert");
        try(ActivityController<ImageToolActivity> c=Robolectric.buildActivity(ImageToolActivity.class,intent).setup()){
            ImageToolActivity a=c.get();a.findViewById(R.id.buttonChoose).performClick();org.robolectric.shadows.ShadowActivity.IntentForResult picker=Shadows.shadowOf(a).getNextStartedActivityForResult();assertNotNull(picker);assertEquals(Intent.ACTION_OPEN_DOCUMENT,picker.intent.getAction());
            a.getActivityResultRegistry().dispatchResult(picker.requestCode,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(source)));
            await(()->a.findViewById(R.id.buttonProcess).isEnabled());capture(a,"image-selected");
            a.findViewById(R.id.buttonProcess).performClick();assertEquals(View.VISIBLE,a.findViewById(R.id.processingPanel).getVisibility());assertFalse(a.findViewById(R.id.processingCancel).isEnabled());capture(a,"processing-image-light");
            ImageToolViewModel model=new ViewModelProvider(a).get(ImageToolViewModel.class);await(()->!model.isWorking());assertNotNull(((TextView)a.findViewById(R.id.textStatus)).getText().toString(),model.outputBytes);assertTrue(model.outputFile.isFile());assertEquals("image/jpeg",model.outputMime);
            Bitmap decoded=android.graphics.BitmapFactory.decodeByteArray(model.outputBytes,0,model.outputBytes.length);assertNotNull(decoded);assertEquals(96,decoded.getWidth());decoded.recycle();
            capture(a,"success-image-light");assertEquals(View.VISIBLE,a.findViewById(R.id.resultActions).getVisibility());assertNotNull(a.findViewById(R.id.buttonOpen));
            a.findViewById(R.id.buttonSave).performClick();org.robolectric.shadows.ShadowActivity.IntentForResult saver=Shadows.shadowOf(a).getNextStartedActivityForResult();assertEquals(Intent.ACTION_CREATE_DOCUMENT,saver.intent.getAction());File destination=new File(app.getCacheDir(),"saved-test.jpg");a.getActivityResultRegistry().dispatchResult(saver.requestCode,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(destination)));await(()->destination.isFile()&&destination.length()==model.outputBytes.length);
            while(Shadows.shadowOf(a).getNextStartedActivity()!=null){}
            org.robolectric.shadows.ShadowToast.reset();a.findViewById(R.id.buttonShare).performClick();await(()->Shadows.shadowOf(a).peekNextStartedActivity()!=null || org.robolectric.shadows.ShadowToast.getTextOfLatestToast()!=null);
            Intent chooser=Shadows.shadowOf(a).getNextStartedActivity();if(chooser!=null)assertEquals(Intent.ACTION_CHOOSER,chooser.getAction());else assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().contains("configured root"));
        }
        try(ActivityController<RecentActivity> c=Robolectric.buildActivity(RecentActivity.class).setup()){
            RecentActivity a=c.get();capture(a,"recent-light");assertEquals(1,HistoryStore.read(a).size());ListView list=a.findViewById(R.id.historyList);assertEquals(2,list.getAdapter().getCount());HistoryStore.Entry entry=HistoryStore.read(a).get(0);assertTrue(entry.file.isFile());HistoryStore.remove(a,entry.id);assertFalse(entry.file.exists());assertTrue(HistoryStore.read(a).isEmpty());
        }
        ReflectionHelpers.callStaticMethod(android.animation.ValueAnimator.class,"setDurationScale",ReflectionHelpers.ClassParameter.from(float.class,1f));
    }
    @Test public void actualPdfMerge() throws Exception {pdfMerge("light");}
    private void pdfMerge(String mode) throws Exception {
        ReflectionHelpers.callStaticMethod(android.animation.ValueAnimator.class,"setDurationScale",ReflectionHelpers.ClassParameter.from(float.class,0f));
        Context app=RuntimeEnvironment.getApplication();HistoryStore.clear(app);
        File first=new File(app.getCacheDir(),"authored-first.pdf"),second=new File(app.getCacheDir(),"authored-second.pdf");
        for(File source:new File[]{first,second})try(com.tom_roush.pdfbox.pdmodel.PDDocument pdf=new com.tom_roush.pdfbox.pdmodel.PDDocument()){com.tom_roush.pdfbox.pdmodel.PDPage page=new com.tom_roush.pdfbox.pdmodel.PDPage();pdf.addPage(page);try(com.tom_roush.pdfbox.pdmodel.PDPageContentStream content=new com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pdf,page)){content.setNonStrokingColor(source==first?8:0,source==first?124:191,source==first?255:143);content.addRect(40,640,180,70);content.fill();}pdf.save(source);}
        try(ActivityController<DocumentToolActivity> c=Robolectric.buildActivity(DocumentToolActivity.class,new Intent(app,DocumentToolActivity.class).putExtra("tool","pdf-editor")).setup()){
            DocumentToolActivity a=c.get();a.findViewById(R.id.buttonChoose).performClick();org.robolectric.shadows.ShadowActivity.IntentForResult picker=Shadows.shadowOf(a).getNextStartedActivityForResult();
            Intent selection=new Intent();ClipData files=ClipData.newRawUri("first",Uri.fromFile(first));files.addItem(new ClipData.Item(Uri.fromFile(second)));selection.setClipData(files);a.getActivityResultRegistry().dispatchResult(picker.requestCode,Activity.RESULT_OK,selection);
            await(()->a.findViewById(R.id.buttonProcess).isEnabled());assertEquals(1,((Spinner)a.findViewById(R.id.spinnerAction)).getSelectedItemPosition());a.findViewById(R.id.buttonProcess).performClick();capture(a,"processing-"+mode);
            DocumentToolViewModel model=new ViewModelProvider(a).get(DocumentToolViewModel.class);await(()->!model.isWorking());assertNotNull(((TextView)a.findViewById(R.id.textStatus)).getText().toString(),model.outputBytes);assertTrue(model.outputFile.isFile());
            try(com.tom_roush.pdfbox.pdmodel.PDDocument pdf=com.tom_roush.pdfbox.pdmodel.PDDocument.load(model.outputBytes)){assertEquals(2,pdf.getNumberOfPages());}
            assertEquals("merged.pdf",model.outputName);capture(a,"success-"+mode);
            File evidence=new File(System.getProperty("fileflow.evidenceDir"),"verified-merged-"+mode+".pdf");try(FileOutputStream out=new FileOutputStream(evidence)){out.write(model.outputBytes);}
            while(Shadows.shadowOf(a).getNextStartedActivity()!=null){}org.robolectric.shadows.ShadowToast.reset();a.findViewById(R.id.buttonOpen).performClick();Intent open=Shadows.shadowOf(a).getNextStartedActivity();if(open!=null){assertEquals(Intent.ACTION_VIEW,open.getAction());assertTrue((open.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);}else assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().contains("cannot be opened"));
        }
        try(ActivityController<RecentActivity> c=Robolectric.buildActivity(RecentActivity.class).setup()){capture(c.get(),"recent-"+mode);assertTrue(HistoryStore.read(c.get()).get(0).file.isFile());}
        ReflectionHelpers.callStaticMethod(android.animation.ValueAnimator.class,"setDurationScale",ReflectionHelpers.ClassParameter.from(float.class,1f));
    }
    @Test public void errorAndMissingOutputsNeverShowSuccess() throws Exception {
        Intent intent=new Intent(RuntimeEnvironment.getApplication(),ImageToolActivity.class).putExtra("tool","image-pdf");try(ActivityController<ImageToolActivity> c=Robolectric.buildActivity(ImageToolActivity.class,intent).setup()){
            ImageToolActivity a=c.get();ImageToolViewModel model=new ViewModelProvider(a).get(ImageToolViewModel.class);model.items.add(new ImageToolViewModel.Item(Uri.fromFile(new File(a.getCacheDir(),"missing.png")),"missing.png"));model.changed();model.process("image-pdf",0,0,0,0);await(()->!model.isWorking());assertNull(model.outputFile);assertNull(model.outputBytes);capture(a,"error-light");assertEquals(View.GONE,a.findViewById(R.id.resultActions).getVisibility());
        }
    }
    @Test public void historyRetentionLegacyAndThemePersistence() throws Exception {
        Context app=RuntimeEnvironment.getApplication();HistoryStore.clear(app);HistoryStore.add(app,"Legacy","old.pdf",2);assertNull(HistoryStore.read(app).get(0).file);
        for(int i=0;i<31;i++)HistoryStore.addResult(app,"Test","synthetic-"+i+".pdf","application/pdf",new byte[]{1,2,3});assertEquals(30,HistoryStore.read(app).size());File directory=new File(app.getFilesDir(),"recent");assertEquals(30,directory.list().length);HistoryStore.clear(app);assertEquals(0,directory.list().length);
        try(ActivityController<SettingsActivity> c=Robolectric.buildActivity(SettingsActivity.class).setup()){((com.google.android.material.materialswitch.MaterialSwitch)c.get().findViewById(R.id.switchDark)).setChecked(true);assertEquals(AppCompatDelegate.MODE_NIGHT_YES,app.getSharedPreferences("fileflow.settings",0).getInt("nightMode",0));}
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    @Test @Config(sdk=30) public void systemBarsAndKeyboardInsetsAreAppliedWithoutAddingThemTwice() throws Exception {
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            View content=c.get().findViewById(android.R.id.content);
            androidx.core.view.WindowInsetsCompat insets=new androidx.core.view.WindowInsetsCompat.Builder().setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),androidx.core.graphics.Insets.of(0,24,0,24)).setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),androidx.core.graphics.Insets.of(0,0,0,260)).build();
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(content,insets);assertEquals(24,content.getPaddingTop());assertEquals(260,content.getPaddingBottom());
        }
    }
    private interface Condition{boolean ready();}
    private static void await(Condition condition) throws Exception {long end=System.currentTimeMillis()+15000;while(!condition.ready()&&System.currentTimeMillis()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(20);}Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue("Background operation did not finish",condition.ready());}
    static void capture(Activity activity,String name) throws Exception {
        View view=activity.findViewById(android.R.id.content);
        int w=Math.round(activity.getResources().getConfiguration().screenWidthDp*activity.getResources().getDisplayMetrics().density),h=Math.round(activity.getResources().getConfiguration().screenHeightDp*activity.getResources().getDisplayMetrics().density);
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);
        if(com.fileflow.app.ui.Ui.motionEnabled(activity))Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(700));
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(activity.getColor(R.color.background));view.draw(canvas);
        File dir=new File(System.getProperty("fileflow.evidenceDir","../../.tools/android-ui"),System.getProperty("fileflow.capture","after"));dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
}

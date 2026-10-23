package com.fileflow.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static org.hamcrest.Matchers.anything;
import static androidx.test.espresso.Espresso.pressBackUnconditionally;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MainNavigationTest {
    @Rule public final ActivityScenarioRule<MainActivity> activity = new ActivityScenarioRule<>(MainActivity.class);

    @Test public void everyDashboardToolOpensItsIntendedWorkflow() {
        onView(withId(R.id.navTools)).perform(click());
        for(int i=0;i<ToolCatalog.ALL.length;i++){
            onData(anything()).inAdapterView(withId(R.id.toolsList)).atPosition(i).perform(click());
            onView(withText(ToolCatalog.ALL[i].title)).check(matches(isDisplayed()));
            pressBackUnconditionally();
        }
        onView(withId(R.id.buttonRecent)).perform(click());
        onView(withId(R.id.recentFilters)).check(matches(isDisplayed()));pressBackUnconditionally();
        onView(withId(R.id.buttonSettings)).perform(click());
        onView(withId(R.id.switchDark)).check(matches(isDisplayed()));
    }

    @Test public void retainedOutputHasAnActualReadableFileProviderUri() throws Exception {
        android.content.Context context=androidx.test.core.app.ApplicationProvider.getApplicationContext();
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888);
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();
        java.io.File file=HistoryStore.addResult(context,"UI test","synthetic-test.png","image/png",bytes.toByteArray());
        android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(context,context.getPackageName()+".files",file);
        try(java.io.InputStream in=context.getContentResolver().openInputStream(uri)){
            android.graphics.Bitmap reopened=android.graphics.BitmapFactory.decodeStream(in);
            org.junit.Assert.assertNotNull(reopened);org.junit.Assert.assertEquals(32,reopened.getWidth());reopened.recycle();
        }finally{for(HistoryStore.Entry entry:HistoryStore.read(context))if(entry.file!=null&&entry.file.equals(file))HistoryStore.remove(context,entry.id);}
    }
}

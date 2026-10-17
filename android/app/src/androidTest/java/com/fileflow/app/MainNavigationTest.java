package com.fileflow.app;

import static androidx.test.espresso.Espresso.onView;
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
        openAndReturn(R.id.cardImageCompressor, R.string.image_compressor);
        openAndReturn(R.id.buttonPdfExcel, R.string.pdf_to_excel);
        openAndReturn(R.id.buttonPdfWord, R.string.pdf_to_word);
        openAndReturn(R.id.buttonWordPdf, R.string.word_to_pdf);
        openAndReturn(R.id.buttonPdfCompress, R.string.pdf_compressor);
        openAndReturn(R.id.buttonPdfEditor, R.string.pdf_editor);
        openAndReturn(R.id.buttonImagePdf, R.string.image_to_pdf);
        openAndReturn(R.id.buttonImageConvert, R.string.image_converter);
        openAndReturn(R.id.buttonScanner, R.string.document_scanner);
        openAndReturn(R.id.buttonPrepare, R.string.prepare_upload);
        openAndReturn(R.id.buttonRecent, R.string.recent_activity);
        openAndReturn(R.id.buttonSettings, R.string.settings);
    }

    private static void openAndReturn(int buttonId, int expectedTitle) {
        onView(withId(buttonId)).perform(scrollTo(), click());
        onView(withText(expectedTitle)).check(matches(isDisplayed()));
        pressBackUnconditionally();
    }
}

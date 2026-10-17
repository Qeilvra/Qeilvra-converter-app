package com.fileflow.app;

import android.app.Activity;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AdsManager {
    private static final String TEST_BANNER_ID = "ca-app-pub-3940256099942544/9214589741";
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private AdsManager() {}

    public static void requestConsentAndLoadTestBanner(@NonNull Activity activity, @NonNull FrameLayout container) {
        ConsentInformation consent = UserMessagingPlatform.getConsentInformation(activity);
        ConsentRequestParameters parameters = new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(activity, parameters, () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> {
            if (formError == null && consent.canRequestAds()) loadTestBanner(activity, container);
        }), requestError -> {
            // Do not request an ad when consent status could not be refreshed.
        });
        if (consent.canRequestAds()) loadTestBanner(activity, container);
    }

    private static void loadTestBanner(Activity activity, FrameLayout container) {
        Runnable load = () -> activity.runOnUiThread(() -> {
            if (activity.isFinishing() || container.getChildCount() > 0) return;
            int width = Math.max(320, Math.round(activity.getResources().getDisplayMetrics().widthPixels / activity.getResources().getDisplayMetrics().density) - 36);
            AdView ad = new AdView(activity);
            ad.setAdUnitId(TEST_BANNER_ID);
            ad.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, width));
            container.addView(ad);
            ad.loadAd(new AdRequest.Builder().build());
        });
        if (INITIALIZED.compareAndSet(false, true)) MobileAds.initialize(activity, status -> load.run()); else load.run();
    }
}

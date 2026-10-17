package com.fileflow.app;

import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.ump.UserMessagingPlatform;

public final class SettingsActivity extends AppCompatActivity {
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_settings);
        findViewById(R.id.buttonBack).setOnClickListener(view -> finish());
        MaterialSwitch dark = findViewById(R.id.switchDark);
        dark.setChecked(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES);
        dark.setOnCheckedChangeListener((button, checked) -> AppCompatDelegate.setDefaultNightMode(checked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO));
        MaterialSwitch urdu = findViewById(R.id.switchUrdu);
        urdu.setChecked(AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("ur"));
        urdu.setOnCheckedChangeListener((button, checked) -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(checked ? "ur" : "en")));
        findViewById(R.id.buttonClear).setOnClickListener(view -> { HistoryStore.clear(this); Toast.makeText(this, R.string.clear_history, Toast.LENGTH_SHORT).show(); });
        findViewById(R.id.buttonPrivacyOptions).setOnClickListener(view -> UserMessagingPlatform.showPrivacyOptionsForm(this, formError -> {
            if (formError != null) Toast.makeText(this, formError.getMessage(), Toast.LENGTH_LONG).show();
        }));
    }
}

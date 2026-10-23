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
        ToolIo.insets(this);
        com.fileflow.app.ui.Ui.attachBottom(this,"settings");
        android.view.ViewGroup settingsBody=(android.view.ViewGroup)((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0);
        com.fileflow.app.ui.Ui.entrance(settingsBody,350);
        findViewById(R.id.buttonBack).setOnClickListener(view -> finish());
        MaterialSwitch dark = findViewById(R.id.switchDark);
        dark.setChecked((getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        dark.setOnCheckedChangeListener((button, checked) -> {
            int mode = checked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            getSharedPreferences("fileflow.settings", MODE_PRIVATE).edit().putInt("nightMode", mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
        });
        MaterialSwitch urdu = findViewById(R.id.switchUrdu);
        urdu.setChecked(AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("ur"));
        urdu.setOnCheckedChangeListener((button, checked) -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(checked ? "ur" : "en")));
        findViewById(R.id.buttonClear).setOnClickListener(view -> new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.clear_history).setMessage(R.string.ui_clear_message).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.clear_history,(d,w)->{HistoryStore.clear(this);Toast.makeText(this,R.string.clear_history,Toast.LENGTH_SHORT).show();}).show());
        findViewById(R.id.buttonPrivacyOptions).setOnClickListener(view -> UserMessagingPlatform.showPrivacyOptionsForm(this, formError -> {
            if (formError != null) Toast.makeText(this, formError.getMessage(), Toast.LENGTH_LONG).show();
        }));
        android.view.ViewGroup parent=(android.view.ViewGroup)findViewById(R.id.buttonPrivacyOptions).getParent();
        com.google.android.material.button.MaterialButton system=new com.google.android.material.button.MaterialButton(this);system.setText(R.string.ui_system_theme);parent.addView(system);system.setOnClickListener(v->{getSharedPreferences("fileflow.settings",MODE_PRIVATE).edit().putInt("nightMode",AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM).apply();AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);});
        com.google.android.material.button.MaterialButton license=new com.google.android.material.button.MaterialButton(this);license.setText(R.string.ui_font_licenses);parent.addView(license);license.setOnClickListener(v->{try(java.io.InputStream input=getAssets().open("licenses/Inter-OFL.txt")){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];for(int n;(n=input.read(buffer))!=-1;)bytes.write(buffer,0,n);new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Inter · SIL Open Font License 1.1").setMessage(bytes.toString("UTF-8")).setPositiveButton(android.R.string.ok,null).show();}catch(java.io.IOException missing){Toast.makeText(this,"Font license could not be opened.",Toast.LENGTH_LONG).show();}});
    }
}

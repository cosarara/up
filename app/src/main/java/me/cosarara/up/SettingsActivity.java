package me.cosarara.up;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
        } else {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            if (id == R.id.load_teknik) {
                editor.putString("upload_url", "https://api.teknik.io/v1/Upload");
                editor.putString("parameter", "file");
                editor.putString("extractor", "teknik");
                editor.putString("prepend", "");
            } else if (id == R.id.load_uguu) {
                editor.putString("upload_url", "https://uguu.se/api.php?d=upload-tool");
                editor.putString("parameter", "file");
                editor.putString("extractor", "plain");
                editor.putString("prepend", "");
            } else if (id == R.id.load_pomfcat) {
                editor.putString("upload_url", "https://pomf.cat/upload.php");
                editor.putString("parameter", "files[]");
                editor.putString("extractor", "pomf");
                editor.putString("prepend", "https://a.pomf.cat/");
            } else if (id == R.id.load_upste) {
                editor.putString("upload_url", "https://pste.pw/api/upload");
                editor.putString("parameter", "file");
                editor.putString("extractor", "upste");
                editor.putString("prepend", "");
            }
            editor.commit();
            finish();
            startActivity(getIntent());
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }
    }
}
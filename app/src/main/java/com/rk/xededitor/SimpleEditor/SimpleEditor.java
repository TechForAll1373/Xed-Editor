package com.rk.xededitor.SimpleEditor;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rk.xededitor.Decompress;
import com.rk.xededitor.R;
import com.rk.xededitor.Settings.SettingsActivity;
import com.rk.xededitor.rkUtils;

import org.eclipse.tm4e.core.registry.IThemeSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;

public class SimpleEditor extends AppCompatActivity {
    private SimpleEditor activity;
    private CodeEditor editor;
    private Content content;
    private Uri uri;
    private Menu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_editor);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        activity = this;
        editor = findViewById(R.id.editor);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // for add back arrow in action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }


        if (!rkUtils.isDarkMode(this)) {
            //light mode
            getWindow().setNavigationBarColor(Color.parseColor("#FEF7FF"));
            View decorView = getWindow().getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decorView.setSystemUiVisibility(flags);
        }
        if (!new File(getExternalFilesDir(null) + "/unzip").exists()) {
            try {
                Decompress.unzipFromAssets(this, "files.zip", getExternalFilesDir(null) + "/unzip");
                new File(getExternalFilesDir(null) + "files").delete();
                new File(getExternalFilesDir(null) + "files.zip").delete();
                new File(getExternalFilesDir(null) + "textmate").delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        editor.setTypefaceText(Typeface.createFromAsset(getAssets(), "JetBrainsMono-Regular.ttf"));
        editor.setTextSize(14);
        ensureTextmateTheme();

        handleIntent(getIntent());
    }
    private String SearchText = "";

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            // Handle the back arrow click here
            onBackPressed();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.action_save) {
            save();
            return true;
        } else if (id == R.id.search) {
            View popuop_view = LayoutInflater.from(this).inflate(R.layout.popup_search, null);
            TextView searchBox = popuop_view.findViewById(R.id.searchbox);
            if (!SearchText.equals("")) {
                searchBox.setText(SearchText);
            }

            AlertDialog dialog =
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Search")
                            .setView(popuop_view)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton(
                                    "Search",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            CheckBox checkBox = popuop_view.findViewById(R.id.case_senstive);
                                            SearchText = searchBox.getText().toString();
                                            editor.getSearcher().search(SearchText, new EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, !checkBox.isChecked()));
                                            menu.findItem(R.id.search_next).setVisible(true);
                                            menu.findItem(R.id.search_previous).setVisible(true);
                                            menu.findItem(R.id.search_close).setVisible(true);
                                            menu.findItem(R.id.replace).setVisible(true);
                                        }
                                    })
                            .show();
        }else if (id == R.id.search_next) {
            editor.getSearcher().gotoNext();
            return true;
        } else if (id == R.id.search_previous) {
            editor.getSearcher().gotoPrevious();
            return true;
        } else if (id == R.id.search_close) {
             editor.getSearcher().stopSearch();
            menu.findItem(R.id.search_next).setVisible(false);
            menu.findItem(R.id.search_previous).setVisible(false);
            menu.findItem(R.id.search_close).setVisible(false);
            menu.findItem(R.id.replace).setVisible(false);
            SearchText = "";
            return true;
        }else if (id == R.id.replace) {
            View popuop_view = LayoutInflater.from(this).inflate(R.layout.popup_replace, null);
            AlertDialog dialog =
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Replace")
                            .setView(popuop_view)
                            .setNegativeButton("Cancel", null).setPositiveButton("Replace All", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    editor.getSearcher().replaceAll(((TextView) popuop_view.findViewById(R.id.replace_replacement)).getText().toString());
                                }
                            }).show();


        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.simple_mode_menu, menu);
        this.menu = menu;
        return true;
    }




    private void handleIntent(Intent intent) {
        if (intent != null
                && (Intent.ACTION_VIEW.equals(intent.getAction())
                || Intent.ACTION_EDIT.equals(intent.getAction()))) {
            uri = intent.getData();
            if (uri != null) {
                // Try to retrieve the file's display name
                String displayName = null;
                try (Cursor cursor = getContentResolver().query(uri, null, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) {
                            displayName = cursor.getString(nameIndex);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (displayName != null) {
                    if (displayName.length() > 13) {
                        displayName = displayName.substring(0, 10) + "...";
                    }
                    getSupportActionBar().setTitle(displayName);
                }

                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        content = ContentIO.createFrom(inputStream);
                        if (content != null) {
                            editor.setText(content); // Ensure content.toString() is what you intend to set

                        } else {
                            rkUtils.toast(this, "Error: Content is null");
                        }
                        inputStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public void save() {
        try {
            OutputStream outputStream = getContentResolver().openOutputStream(uri, "wt");
            if (outputStream != null) {
                ContentIO.writeTo(content, outputStream, true);
                rkUtils.toast(this, "saved!");
            } else {
                rkUtils.toast(this, "InputStream is null");
            }
        } catch (IOException e) {
            e.printStackTrace();
            rkUtils.toast(this, "Unknown Error \n" + e);
        }
    }

    private void ensureTextmateTheme() {

        var editorColorScheme = editor.getColorScheme();
        var themeRegistry = ThemeRegistry.getInstance();

        boolean darkMode = rkUtils.isDarkMode(this);
        try {

            if (darkMode) {
                String path;
                if (rkUtils.isOled(this)) {
                    path = getExternalFilesDir(null).getAbsolutePath() + "/unzip/textmate/black/darcula.json";
                } else {
                    path = getExternalFilesDir(null).getAbsolutePath() + "/unzip/textmate/darcula.json";
                }
                if (!new File(path).exists()) {
                    rkUtils.toast("theme file not found");
                }

                themeRegistry.loadTheme(
                        new ThemeModel(
                                IThemeSource.fromInputStream(
                                        FileProviderRegistry.getInstance().tryGetInputStream(path), path, null),
                                "darcula"));
                editorColorScheme = TextMateColorScheme.create(themeRegistry);

            } else {

                String path = getExternalFilesDir(null).getAbsolutePath() + "/unzip/textmate/quietlight.json";
                if (!new File(path).exists()) {
                    rkUtils.toast("theme file not found");
                }
                themeRegistry.loadTheme(
                        new ThemeModel(
                                IThemeSource.fromInputStream(
                                        FileProviderRegistry.getInstance().tryGetInputStream(path), path, null),
                                "quitelight"));
                editorColorScheme = TextMateColorScheme.create(themeRegistry);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (darkMode) {
            SharedPreferences pref = getApplicationContext().getSharedPreferences("MyPref", 0);
            themeRegistry.setTheme("darcula");
        } else {
            themeRegistry.setTheme("quietlight");
        }

        editor.setColorScheme(editorColorScheme);
    }

}
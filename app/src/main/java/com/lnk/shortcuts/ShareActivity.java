package com.lnk.shortcuts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Patterns;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/** מופיע בתפריט "שתף" של מנהלי קבצים ואפליקציות אחרות: יוצר קיצור דרך לקובץ או לקישור. */
public class ShareActivity extends Activity {

    private final List<File> files = new ArrayList<>();
    private String url;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(in.getAction())) {
            Uri u = in.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(in.getAction())) {
            ArrayList<Uri> l = in.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (l != null) uris.addAll(l);
        }
        for (Uri u : uris) {
            File f = FileUtils.fromUri(this, u);
            if (f != null) files.add(f);
        }
        if (files.isEmpty()) {
            CharSequence t = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (t != null) {
                Matcher m = Patterns.WEB_URL.matcher(t);
                if (m.find()) {
                    url = m.group();
                    if (!url.contains("://")) url = "https://" + url;
                }
            }
        }
        if (files.isEmpty() && url == null) {
            Toast.makeText(this, uris.isEmpty() ? "לא התקבל קובץ או קישור"
                            : "לא ניתן לזהות את מיקום הקובץ. פתח אותו דרך LinkIt וצור שם את הקיצור.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        showChoices();
    }

    private void showChoices() {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (url != null || files.size() == 1) {
            labels.add("\uD83C\uDFE0 קיצור למסך הבית");
            actions.add(this::addHome);
        }
        labels.add("\u2B50 הוסף למועדפים ב-LinkIt");
        actions.add(this::addFavorites);
        labels.add("\uD83D\uDCC4 צור קובץ קיצור בתיקיית LinkIt");
        actions.add(this::createFiles);

        String title = url != null ? url
                : files.size() == 1 ? files.get(0).getName() : files.size() + " קבצים";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    actions.get(which).run();
                    finish();
                })
                .setOnDismissListener(d -> finish())
                .show();
    }

    private void addHome() {
        if (url != null) {
            String host = Uri.parse(url).getHost();
            Opener.pin(this, "url", url, host != null ? host : url, Opener.appIcon(this));
        } else {
            Opener.pinPath(this, files.get(0));
        }
    }

    private void addFavorites() {
        if (url != null) {
            String host = Uri.parse(url).getHost();
            Favorites.add(this, new Favorites.Item(host != null ? host : url, "url", url));
        } else {
            for (File f : files) {
                Favorites.add(this, new Favorites.Item(f.getName(), "path", f.getAbsolutePath()));
            }
        }
        Toast.makeText(this, "נוסף למועדפים", Toast.LENGTH_SHORT).show();
    }

    private void createFiles() {
        if (!FileUtils.hasAccess(this)) {
            Toast.makeText(this, "יש לתת ל-LinkIt גישה לקבצים", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class));
            return;
        }
        File dir = new File(Environment.getExternalStorageDirectory(), "LinkIt");
        try {
            if (url != null) {
                String host = Uri.parse(url).getHost();
                ShortcutFile.create(dir, host != null ? host : "קישור", "url", url);
            } else {
                for (File f : files) ShortcutFile.create(dir, f.getName(), "path", f.getAbsolutePath());
            }
        } catch (Exception e) {
            Toast.makeText(this, "יצירת הקיצור נכשלה", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "הקיצור נוצר בתיקייה LinkIt – העבר אותו לכל מקום", Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_BROWSE)
                .putExtra("path", dir.getAbsolutePath()));
    }
}

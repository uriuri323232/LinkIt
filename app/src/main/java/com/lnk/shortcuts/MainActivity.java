package com.lnk.shortcuts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 1;
    private static final int REQ_DIR = 2;

    private EditText nameEdit;
    private EditText urlEdit;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("יצירת קיצור דרך");
        title.setTextSize(24);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("קיצור הדרך יתווסף למסך הבית. הכול עובד ללא אינטרנט.");
        hint.setPadding(0, dp(6), 0, dp(16));
        root.addView(hint);

        nameEdit = new EditText(this);
        nameEdit.setHint("שם לקיצור (אופציונלי)");
        nameEdit.setSingleLine(true);
        root.addView(nameEdit);

        urlEdit = new EditText(this);
        urlEdit.setHint("כתובת אתר / קישור (למשל example.com)");
        urlEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlEdit.setSingleLine(true);
        urlEdit.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.addView(urlEdit);

        root.addView(button("קיצור לקישור", v -> createUrl()));
        root.addView(button("קיצור לקובץ", v -> pick(REQ_FILE)));
        root.addView(button("קיצור לתיקייה", v -> pick(REQ_DIR)));
        root.addView(button("קיצור לאפליקציה", v -> pickApp()));

        setContentView(scroll);
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private void createUrl() {
        String url = urlEdit.getText().toString().trim();
        if (url.isEmpty()) {
            toast("יש להזין כתובת");
            return;
        }
        if (!url.contains("://")) url = "https://" + url;
        Uri u = Uri.parse(url);
        String name = nameEdit.getText().toString().trim();
        if (name.isEmpty()) name = u.getHost() != null ? u.getHost() : url;
        pin(name, "url", url, Icon.createWithResource(this, R.drawable.ic_launcher));
    }

    private void pick(int req) {
        Intent i = new Intent(req == REQ_FILE
                ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (req == REQ_FILE) {
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, req);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }

        String name = nameEdit.getText().toString().trim();
        Icon icon = Icon.createWithResource(this, R.drawable.ic_launcher);

        if (req == REQ_FILE) {
            if (name.isEmpty()) name = displayName(uri);
            pin(name, "file", uri.toString(), icon);
        } else if (req == REQ_DIR) {
            if (name.isEmpty()) {
                String id = DocumentsContract.getTreeDocumentId(uri);
                name = id.substring(Math.max(id.lastIndexOf(':'), id.lastIndexOf('/')) + 1);
                if (name.isEmpty()) name = "תיקייה";
            }
            pin(name, "dir", uri.toString(), icon);
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {
        }
        return "קובץ";
    }

    private void pickApp() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(main, 0));
        Collections.sort(apps, (a, b) -> a.loadLabel(pm).toString()
                .compareToIgnoreCase(b.loadLabel(pm).toString()));

        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).loadLabel(pm).toString();

        new AlertDialog.Builder(this)
                .setTitle("בחר אפליקציה")
                .setItems(labels, (d, which) -> {
                    ResolveInfo ri = apps.get(which);
                    String pkg = ri.activityInfo.packageName;
                    String name = nameEdit.getText().toString().trim();
                    if (name.isEmpty()) name = labels[which];
                    Icon icon = Icon.createWithBitmap(toBitmap(ri.loadIcon(pm)));
                    pin(name, "app", pkg, icon);
                })
                .show();
    }

    private Bitmap toBitmap(Drawable d) {
        int size = dp(96);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        d.setBounds(0, 0, size, size);
        d.draw(canvas);
        return bmp;
    }

    private void pin(String name, String type, String data, Icon icon) {
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            toast("המשגר (Launcher) שלך לא תומך ביצירת קיצורי דרך");
            return;
        }
        Intent launch = new Intent(this, LaunchActivity.class);
        launch.setAction("com.lnk.shortcuts.OPEN");
        launch.putExtra("type", type);
        launch.putExtra("data", data);

        ShortcutInfo info = new ShortcutInfo.Builder(this, type + "_" + data.hashCode())
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(icon)
                .setIntent(launch)
                .build();
        sm.requestPinShortcut(info, null);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}

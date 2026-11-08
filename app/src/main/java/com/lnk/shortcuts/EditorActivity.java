package com.lnk.shortcuts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** יצירה ועריכה של קיצור. */
public class EditorActivity extends Activity {

    private static final int REQ_FILE = 1, REQ_DIR = 2, REQ_IMG = 3, REQ_BROWSE_FILE = 4, REQ_BROWSE_DIR = 5;

    private interface OnApp {
        void pick(String pkg, String label);
    }

    private static final int[] COLORS = {
            0xFF3B5BDB, 0xFF1C7ED6, 0xFF1098AD, 0xFF0CA678, 0xFF2F9E44, 0xFFF59F00, 0xFFE8590C,
            0xFFE03131, 0xFFD6336C, 0xFF9C36B5, 0xFF5F3DC4, 0xFF495057, 0xFF212529, 0xFFFFFFFF};

    private static final String[] EMOJIS = {
            "🔗", "⭐", "❤️", "🏠", "🌐", "📞", "💬", "✉️", "📍", "📷", "📚", "📖", "🛒", "💼",
            "🧾", "🕯️", "🕍", "✡️", "🚗", "🚌", "🍽️", "💊", "🏦", "💡", "🔒", "🔔", "⏰", "📅",
            "🗂️", "📁", "📄", "✏️", "🔧", "☎️", "🎒", "🌳", "☀️", "🌙"};

    private Item item;
    private boolean isNew;
    private boolean saved;

    private LinearLayout form;
    private EditText nameEt, f1, f2, f3;
    private TextView pickLabel;
    private ImageView preview;
    private LinearLayout kindRow, colorRow, glyphPanel;
    private TextView imgBtn;
    private EditText glyphEt;
    private TextView openWithLabel;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.bg(this));
        getWindow().setNavigationBarColor(Ui.bg(this));
        if (!Ui.night(this)) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        Intent in = getIntent();
        String id = in.getStringExtra("id");
        if (id != null) item = Store.get(this, id);
        if (item == null) {
            isNew = true;
            item = new Item();
            String type = in.getStringExtra("type");
            item.type = type == null ? "url" : type;
            if (Intent.ACTION_SEND.equals(in.getAction())) {
                android.net.Uri stream = in.getParcelableExtra(Intent.EXTRA_STREAM);
                if (stream != null) {
                    String path = FileOps.pathFromUri(stream);
                    if (path != null && new File(path).exists()) {
                        File f = new File(path);
                        item.type = f.isDirectory() ? "folder" : "file";
                        item.d1 = path;
                        item.d2 = FileOps.displayName(this, f);
                        item.name = item.d2;
                    } else {
                        Ui.toast(this, "האפליקציה השולחת לא חושפת נתיב קבוע לקובץ. פתח אותו דרך \"LinkIt קבצים\" וצור משם קיצור");
                        finish();
                        return;
                    }
                } else {
                    String text = in.getStringExtra(Intent.EXTRA_TEXT);
                    String subject = in.getStringExtra(Intent.EXTRA_SUBJECT);
                    String url = firstUrl(text);
                    if (url != null) {
                        item.type = "url";
                        item.d1 = url;
                        if (subject != null) item.name = subject;
                    } else {
                        item.type = "text";
                        item.d1 = text == null ? "" : text;
                    }
                }
            }
            if (in.getStringExtra("d1") != null) item.d1 = in.getStringExtra("d1");
            if (in.getStringExtra("d2") != null) item.d2 = in.getStringExtra("d2");
            if (in.getStringExtra("d3") != null) item.d3 = in.getStringExtra("d3");
            if (in.getStringExtra("name") != null) item.name = in.getStringExtra("name");
            if ("folder".equals(item.type) && item.d3.isEmpty() && item.d1.startsWith("/")) item.d3 = "linkit";
            item.color = Types.get(item.type).color;
        }

        buildUi();
    }

    private static String firstUrl(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("(https?://|www\\.)\\S+").matcher(text);
        return m.find() ? m.group() : null;
    }

    // ------------------------------------------------------------------ UI

    private void add(View v) {
        form.addView(v, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
    }

    private void buildUi() {
        Types.T t = Types.get(item.type);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));
        Ui.rtl(root);

        // כותרת
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 4));
        TextView back = Ui.label(this, "→", 26, Ui.text(this), true);
        back.setGravity(Gravity.CENTER);
        back.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        head.addView(back);
        head.addView(Ui.label(this, (isNew ? "קיצור חדש · " : "עריכה · ") + t.emoji + " " + t.label,
                20, Ui.text(this), true));
        root.addView(head);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 24));
        sv.addView(form);
        root.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        // שם + תצוגת אייקון
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        preview = new ImageView(this);
        top.addView(preview, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        nameEt = Ui.input(this, "שם הקיצור (אם ריק - ייקבע אוטומטית)", item.name,
                InputType.TYPE_CLASS_TEXT, false, false);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        nlp.setMarginStart(Ui.dp(this, 12));
        top.addView(nameEt, nlp);
        form.addView(top, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 4));

        buildTarget();
        buildIconSection();

        // כפתורים תחתונים
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 14));
        bar.setBackgroundColor(Ui.surface(this));
        TextView saveBtn = Ui.button(this, "שמור", false, v -> save(false));
        TextView pinBtn = Ui.button(this, "שמור והוסף למסך הבית", true, v -> save(true));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        l1.setMarginEnd(Ui.dp(this, 10));
        bar.addView(saveBtn, l1);
        bar.addView(pinBtn, new LinearLayout.LayoutParams(0, Ui.WRAP, 2f));
        root.addView(bar);

        setContentView(root);
        refreshKinds();
        updatePreview();
    }

    private void buildTarget() {
        switch (item.type) {
            case "url":
                form.addView(Ui.section(this, "כתובת האתר"));
                f1 = Ui.input(this, "example.com או https://…", item.d1,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, false, true);
                add(f1);
                add(Ui.button(this, "📋  הדבק מהלוח", false, v -> {
                    String s = clip();
                    if (s != null) f1.setText(s.trim());
                }));
                addOpenWith();
                break;
            case "file":
                form.addView(Ui.section(this, "קובץ"));
                addPickLabel();
                add(Ui.button(this, "📂  בחר בדפדפן של LinkIt", false, v -> pickBrowser(REQ_BROWSE_FILE, "file")));
                add(Ui.button(this, "🗂️  בחר דרך בורר המערכת", false, v -> pickDoc(REQ_FILE)));
                addOpenWith();
                break;
            case "folder":
                form.addView(Ui.section(this, "תיקייה"));
                addPickLabel();
                add(Ui.button(this, "📂  בחר בדפדפן של LinkIt", false, v -> pickBrowser(REQ_BROWSE_DIR, "folder")));
                add(Ui.button(this, "🗂️  בחר דרך בורר המערכת", false, v -> pickDoc(REQ_DIR)));
                form.addView(Ui.section(this, "במה לפתוח את התיקייה"));
                form.addView(note("בחר מנהל קבצים מסוים כדי שהקיצור יפתח תמיד בו, בלי לעבור למנהל קבצים אחר."));
                addOpenWithRow();
                break;
            case "app":
                form.addView(Ui.section(this, "אפליקציה"));
                addPickLabel();
                add(Ui.button(this, "📱  בחר אפליקציה", false, v -> pickApp((pkg, label) -> {
                    item.d1 = pkg;
                    item.d2 = label;
                    if (nameEt.getText().toString().trim().isEmpty()) nameEt.setText(label);
                    updatePickLabel();
                    updatePreview();
                })));
                break;
            case "call":
                form.addView(Ui.section(this, "מספר טלפון"));
                f1 = Ui.input(this, "050-1234567", item.d1, InputType.TYPE_CLASS_PHONE, false, true);
                add(f1);
                form.addView(note("הטלפון יפתח את החייגן עם המספר, והחיוג עצמו יבוצע בלחיצה."));
                break;
            case "sms":
                form.addView(Ui.section(this, "מספר טלפון"));
                f1 = Ui.input(this, "050-1234567", item.d1, InputType.TYPE_CLASS_PHONE, false, true);
                add(f1);
                form.addView(Ui.section(this, "תוכן ההודעה (אופציונלי)"));
                f2 = Ui.input(this, "הודעה מוכנה…", item.d2, InputType.TYPE_CLASS_TEXT, true, false);
                add(f2);
                break;
            case "whatsapp":
                form.addView(Ui.section(this, "מספר טלפון"));
                f1 = Ui.input(this, "0501234567 או 972501234567", item.d1, InputType.TYPE_CLASS_PHONE, false, true);
                add(f1);
                form.addView(note("מספר שמתחיל ב-0 יומר אוטומטית לקידומת ישראל (972)."));
                form.addView(Ui.section(this, "הודעה מוכנה (אופציונלי)"));
                f2 = Ui.input(this, "הודעה…", item.d2, InputType.TYPE_CLASS_TEXT, true, false);
                add(f2);
                break;
            case "email":
                form.addView(Ui.section(this, "כתובת אימייל"));
                f1 = Ui.input(this, "name@example.com", item.d1,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, false, true);
                add(f1);
                form.addView(Ui.section(this, "נושא (אופציונלי)"));
                f2 = Ui.input(this, "נושא", item.d2, InputType.TYPE_CLASS_TEXT, false, false);
                add(f2);
                form.addView(Ui.section(this, "תוכן (אופציונלי)"));
                f3 = Ui.input(this, "תוכן המייל…", item.d3, InputType.TYPE_CLASS_TEXT, true, false);
                add(f3);
                break;
            case "map":
                form.addView(Ui.section(this, "כתובת, שם מקום או קואורדינטות"));
                f1 = Ui.input(this, "למשל: דיזנגוף 50 תל אביב, או 32.08,34.78", item.d1,
                        InputType.TYPE_CLASS_TEXT, false, false);
                add(f1);
                break;
            case "text":
                form.addView(Ui.section(this, "הטקסט להעתקה"));
                f1 = Ui.input(this, "כתובת, סיסמה זמנית, הודעה שחוזרת על עצמה…", item.d1,
                        InputType.TYPE_CLASS_TEXT, true, false);
                add(f1);
                form.addView(note("לחיצה על הקיצור תעתיק את הטקסט ללוח, מוכן להדבקה בכל מקום."));
                break;
            case "settings":
                form.addView(Ui.section(this, "הגדרה"));
                addPickLabel();
                add(Ui.button(this, "⚙️  בחר הגדרה", false, v -> pickSetting()));
                break;
            case "torch":
                form.addView(note("קיצור שמדליק או מכבה את הפנס בלחיצה אחת, בלי לפתוח אפליקציה."));
                break;
            case "timer": {
                form.addView(Ui.section(this, "משך הטיימר"));
                long secs = Types.parseSecs(item.d1);
                LinearLayout r = new LinearLayout(this);
                r.setOrientation(LinearLayout.HORIZONTAL);
                f1 = Ui.input(this, "דקות", secs == 0 ? "" : String.valueOf(secs / 60),
                        InputType.TYPE_CLASS_NUMBER, false, true);
                f2 = Ui.input(this, "שניות", secs == 0 ? "" : String.valueOf(secs % 60),
                        InputType.TYPE_CLASS_NUMBER, false, true);
                LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
                a.setMarginEnd(Ui.dp(this, 10));
                r.addView(f1, a);
                r.addView(f2, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
                add(r);
                form.addView(Ui.section(this, "תווית (אופציונלי)"));
                f3 = Ui.input(this, "למשל: תה", item.d2, InputType.TYPE_CLASS_TEXT, false, false);
                add(f3);
                break;
            }
            case "group":
                form.addView(Ui.section(this, "הקיצורים בתפריט"));
                addPickLabel();
                add(Ui.button(this, "🗂️  בחר קיצורים", false, v -> pickGroup()));
                form.addView(note("הקיצור יפתח חלון קטן עם הקיצורים שבחרת, לבחירה מהירה."));
                break;
        }
        updatePickLabel();
    }

    private TextView note(String s) {
        TextView t = Ui.label(this, s, 12, Ui.sub(this), false);
        t.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), Ui.dp(this, 4));
        return t;
    }

    private void addPickLabel() {
        pickLabel = Ui.label(this, "", 15, Ui.text(this), true);
        pickLabel.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), Ui.dp(this, 8));
        form.addView(pickLabel);
    }

    private void updatePickLabel() {
        if (pickLabel == null) return;
        String s;
        switch (item.type) {
            case "file":
                s = item.d1.isEmpty() ? "לא נבחר קובץ" : "✔ " + (item.d1.startsWith("/") ? item.d1 : item.d2);
                break;
            case "folder":
                s = item.d1.isEmpty() ? "לא נבחרה תיקייה" : "✔ " + (item.d1.startsWith("/") ? item.d1 : item.d2);
                break;
            case "app":
                s = item.d1.isEmpty() ? "לא נבחרה אפליקציה" : "✔ " + item.d2;
                break;
            case "settings":
                s = item.d1.isEmpty() ? "לא נבחרה הגדרה" : "✔ " + Types.settingLabel(item.d1);
                break;
            case "group": {
                int n = 0;
                for (String p : item.d1.split(",")) if (!p.isEmpty()) n++;
                s = n == 0 ? "לא נבחרו קיצורים" : "✔ " + n + " קיצורים נבחרו";
                break;
            }
            default:
                s = "";
        }
        pickLabel.setText(s);
    }

    // --------------------------------------------------------------- אייקון

    private void buildIconSection() {
        form.addView(Ui.section(this, "אייקון"));

        kindRow = new LinearLayout(this);
        kindRow.setOrientation(LinearLayout.HORIZONTAL);
        add(kindRow);

        colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView cs = new HorizontalScrollView(this);
        cs.setHorizontalScrollBarEnabled(false);
        cs.addView(colorRow);
        cs.setTag("colors");
        add(cs);

        glyphPanel = new LinearLayout(this);
        glyphPanel.setOrientation(LinearLayout.VERTICAL);
        glyphEt = Ui.input(this, "אימוג'י או 1-2 אותיות", item.glyph, InputType.TYPE_CLASS_TEXT, false, false);
        glyphPanel.addView(glyphEt, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
        Ui.watch(glyphEt, () -> {
            item.glyph = glyphEt.getText().toString().trim();
            updatePreview();
        });
        LinearLayout emojiRow = new LinearLayout(this);
        emojiRow.setOrientation(LinearLayout.HORIZONTAL);
        for (final String e : EMOJIS) {
            TextView tv = Ui.label(this, e, 24, Ui.text(this), false);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
            tv.setBackground(Ui.press(this, Ui.rect(this, 0x00000000, 12)));
            tv.setClickable(true);
            tv.setOnClickListener(v -> glyphEt.setText(e));
            emojiRow.addView(tv);
        }
        HorizontalScrollView es = new HorizontalScrollView(this);
        es.setHorizontalScrollBarEnabled(false);
        es.addView(emojiRow);
        glyphPanel.addView(es);
        add(glyphPanel);

        imgBtn = Ui.button(this, "🖼️  בחר תמונה", false, v -> pickImage());
        add(imgBtn);
    }

    private void refreshKinds() {
        kindRow.removeAllViews();
        String[][] kinds = {{"auto", "אוטומטי"}, {"gen", "עיצוב אישי"}, {"image", "תמונה"}};
        for (final String[] k : kinds) {
            TextView c = Ui.chip(this, k[1], k[0].equals(item.iconKind), v -> {
                item.iconKind = k[0];
                if ("image".equals(k[0]) && !IconMaker.imageFile(this, item.id).exists()) pickImage();
                refreshKinds();
                updatePreview();
            });
            kindRow.addView(c, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        }
        boolean gen = "gen".equals(item.iconKind);
        boolean img = "image".equals(item.iconKind);
        View cs = form.findViewWithTag("colors");
        if (cs != null) cs.setVisibility(gen || img ? View.VISIBLE : View.GONE);
        glyphPanel.setVisibility(gen ? View.VISIBLE : View.GONE);
        imgBtn.setVisibility(img ? View.VISIBLE : View.GONE);

        colorRow.removeAllViews();
        for (final int col : COLORS) {
            TextView dot = new TextView(this);
            boolean sel = col == item.color;
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(col);
            g.setStroke(Ui.dp(this, sel ? 3 : 1), sel ? Ui.primary(this) : Ui.outline(this));
            dot.setBackground(g);
            dot.setClickable(true);
            dot.setOnClickListener(v -> {
                item.color = col;
                refreshKinds();
                updatePreview();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38));
            lp.setMarginEnd(Ui.dp(this, 10));
            lp.topMargin = Ui.dp(this, 2);
            lp.bottomMargin = Ui.dp(this, 6);
            colorRow.addView(dot, lp);
        }
    }

    private void updatePreview() {
        preview.setImageBitmap(IconMaker.preview(this, item, Ui.dp(this, 64)));
    }

    // ------------------------------------------------------------- בחירות

    private String clip() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return null;
        ClipData cd = cm.getPrimaryClip();
        if (cd == null || cd.getItemCount() == 0) return null;
        CharSequence cs = cd.getItemAt(0).coerceToText(this);
        return cs == null ? null : cs.toString();
    }

    private void pickDoc(int req) {
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

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, REQ_IMG);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == RESULT_OK && data != null && (req == REQ_BROWSE_FILE || req == REQ_BROWSE_DIR)) {
            String path = data.getStringExtra("path");
            if (path != null) {
                File pf = new File(path);
                item.d1 = path;
                item.d2 = FileOps.displayName(this, pf);
                if (req == REQ_BROWSE_DIR && item.d3.isEmpty()) item.d3 = "linkit";
                if (nameEt.getText().toString().trim().isEmpty()) nameEt.setText(item.d2);
                updatePickLabel();
                updateOpenWithLabel();
                if (req == REQ_BROWSE_FILE && FileOps.mime(pf).startsWith("image/")
                        && "auto".equals(item.iconKind)
                        && IconMaker.saveImageIcon(this, item.id, Uri.fromFile(pf))) {
                    item.iconKind = "image";
                    refreshKinds();
                    updatePreview();
                }
            }
            return;
        }
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_IMG) {
            saveImage(uri);
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        item.d1 = uri.toString();
        if (req == REQ_DIR && "linkit".equals(item.d3) && FileOps.pathFromUri(uri) == null) item.d3 = "";
        if (req == REQ_FILE) {
            item.d2 = displayName(uri);
        } else {
            String id = DocumentsContract.getTreeDocumentId(uri);
            String n = id.substring(Math.max(id.lastIndexOf(':'), id.lastIndexOf('/')) + 1);
            item.d2 = n.isEmpty() ? "תיקייה" : n;
        }
        if (nameEt.getText().toString().trim().isEmpty()) nameEt.setText(item.d2);
        updatePickLabel();
        updateOpenWithLabel();
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

    private void saveImage(Uri uri) {
        if (IconMaker.saveImageIcon(this, item.id, uri)) {
            item.iconKind = "image";
            refreshKinds();
            updatePreview();
        } else {
            Ui.toast(this, "לא ניתן לטעון את התמונה");
        }
    }

    private static final class AppRow {
        String label, pkg;
        ResolveInfo ri;
        Drawable icon;
    }

    private void pickApp(final OnApp cb) {
        final PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<AppRow> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            AppRow r = new AppRow();
            r.label = ri.loadLabel(pm).toString();
            r.pkg = pkg;
            r.ri = ri;
            apps.add(r);
        }
        Collections.sort(apps, new Comparator<AppRow>() {
            @Override
            public int compare(AppRow a, AppRow b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        final List<AppRow> shown = new ArrayList<>(apps);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), 0);
        final EditText search = Ui.input(this, "🔍  חיפוש אפליקציה", "", InputType.TYPE_CLASS_TEXT, false, false);
        box.addView(search, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));
        ListView lv = new ListView(this);
        lv.setDividerHeight(0);
        box.addView(lv, new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(this, 380)));

        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                AppRow r = shown.get(pos);
                if (r.icon == null) r.icon = r.ri.loadIcon(pm);
                LinearLayout row = new LinearLayout(EditorActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, Ui.dp(EditorActivity.this, 8), 0, Ui.dp(EditorActivity.this, 8));
                ImageView iv = new ImageView(EditorActivity.this);
                iv.setImageDrawable(r.icon);
                row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(EditorActivity.this, 40), Ui.dp(EditorActivity.this, 40)));
                TextView tv = Ui.label(EditorActivity.this, r.label, 16, Ui.text(EditorActivity.this), false);
                tv.setMaxLines(1);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
                lp.setMarginStart(Ui.dp(EditorActivity.this, 14));
                row.addView(tv, lp);
                return row;
            }
        };
        lv.setAdapter(ad);

        Ui.watch(search, () -> {
            String q = search.getText().toString().trim().toLowerCase();
            shown.clear();
            for (AppRow r : apps) if (q.isEmpty() || r.label.toLowerCase().contains(q)) shown.add(r);
            ad.notifyDataSetChanged();
        });

        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("בחר אפליקציה")
                .setView(box)
                .setNegativeButton("ביטול", null)
                .create();
        lv.setOnItemClickListener((p, v, pos, id) -> {
            AppRow r = shown.get(pos);
            d.dismiss();
            cb.pick(r.pkg, r.label);
        });
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    private void pickSetting() {
        String[] labels = new String[Types.SETTINGS.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Types.SETTINGS[i][2] + "  " + Types.SETTINGS[i][1];
        }
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("בחר הגדרה")
                .setItems(labels, (dlg, which) -> {
                    item.d1 = Types.SETTINGS[which][0];
                    item.d2 = Types.SETTINGS[which][1];
                    if (nameEt.getText().toString().trim().isEmpty()) nameEt.setText(item.d2);
                    updatePickLabel();
                })
                .create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    private void pickGroup() {
        final List<Item> cand = new ArrayList<>();
        for (Item it : Store.all(this)) {
            if (!it.id.equals(item.id) && !"group".equals(it.type)) cand.add(it);
        }
        if (cand.isEmpty()) {
            Ui.toast(this, "צור קודם כמה קיצורים אחרים, ואז תוכל לאגד אותם");
            return;
        }
        Set<String> cur = new HashSet<>();
        for (String p : item.d1.split(",")) if (!p.isEmpty()) cur.add(p);
        String[] names = new String[cand.size()];
        final boolean[] checked = new boolean[cand.size()];
        for (int i = 0; i < cand.size(); i++) {
            Item it = cand.get(i);
            names[i] = Types.get(it.type).emoji + "  " + it.name;
            checked[i] = cur.contains(it.id);
        }
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("בחר קיצורים לתפריט")
                .setMultiChoiceItems(names, checked, (dlg, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("אישור", (dlg, w) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < cand.size(); i++) {
                        if (!checked[i]) continue;
                        if (sb.length() > 0) sb.append(',');
                        sb.append(cand.get(i).id);
                    }
                    item.d1 = sb.toString();
                    updatePickLabel();
                })
                .setNegativeButton("ביטול", null)
                .create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    // ------------------------------------------------------------ פתיחה באמצעות

    private void pickBrowser(int req, String kind) {
        startActivityForResult(new Intent(this, BrowserActivity.class).putExtra("pick", kind), req);
    }

    private void addOpenWith() {
        form.addView(Ui.section(this, "פתח באמצעות"));
        addOpenWithRow();
    }

    private void addOpenWithRow() {
        openWithLabel = Ui.label(this, "", 15, Ui.text(this), true);
        openWithLabel.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), Ui.dp(this, 8));
        form.addView(openWithLabel);
        add(Ui.button(this, "שנה…", false, v -> pickOpenWith()));
        updateOpenWithLabel();
    }

    private String describeOpenWith() {
        String d3 = item.d3 == null ? "" : item.d3;
        boolean folder = "folder".equals(item.type);
        if (d3.isEmpty()) {
            if (folder) return item.d1.startsWith("/") || item.d1.isEmpty()
                    ? "LinkIt (מובנה)" : "ברירת מחדל של המערכת";
            return "ברירת מחדל";
        }
        if ("linkit".equals(d3)) return "LinkIt (מובנה)";
        if ("*ask".equals(d3)) return "בחירה בכל פעם";
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(d3, 0)).toString();
        } catch (Exception e) {
            return d3 + " (לא מותקן)";
        }
    }

    private void updateOpenWithLabel() {
        if (openWithLabel != null) openWithLabel.setText("✔ " + describeOpenWith());
    }

    private void setOpenWith(String v) {
        item.d3 = v;
        updateOpenWithLabel();
    }

    private void pickOpenWith() {
        final boolean folder = "folder".equals(item.type);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        if (folder) {
            labels.add("📂  LinkIt (מובנה, בלי מעבר בין אפליקציות)");
            acts.add(() -> setOpenWith("linkit"));
        }
        labels.add("❓  בחירה בכל פעם");
        acts.add(() -> setOpenWith("*ask"));
        if (!folder || !item.d1.startsWith("/")) {
            labels.add("⚙️  ברירת מחדל של המערכת");
            acts.add(() -> setOpenWith(""));
        }
        if (folder) {
            for (FileOps.AppInfo a : FileOps.fileManagers(this)) {
                labels.add("🗂️  " + a.label);
                final String pkg = a.pkg;
                acts.add(() -> setOpenWith(pkg));
            }
        }
        labels.add("📱  אפליקציה אחרת…");
        acts.add(() -> pickApp((pkg, label) -> setOpenWith(pkg)));
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("פתח באמצעות")
                .setItems(labels.toArray(new String[0]), (dlg, which) -> acts.get(which).run())
                .create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    // ---------------------------------------------------------------- שמירה

    private static String s(EditText e) {
        return e == null ? "" : e.getText().toString().trim();
    }

    /** קורא את הטופס אל הפריט. מחזיר הודעת שגיאה או null. */
    private String collect() {
        String name = s(nameEt);
        switch (item.type) {
            case "url": {
                String v = s(f1);
                if (v.isEmpty()) return "הזן כתובת אתר";
                item.d1 = v;
                if (name.isEmpty()) {
                    String h = Uri.parse(Launcher.fixUrl(v)).getHost();
                    name = h != null ? h.replaceFirst("^www\\.", "") : v;
                }
                break;
            }
            case "file":
            case "folder":
            case "app":
                if (item.d1.isEmpty()) return "בחר יעד לקיצור";
                if (name.isEmpty()) name = item.d2;
                break;
            case "call": {
                String v = s(f1);
                if (v.isEmpty()) return "הזן מספר טלפון";
                item.d1 = v;
                if (name.isEmpty()) name = v;
                break;
            }
            case "sms": {
                String v = s(f1);
                if (v.isEmpty()) return "הזן מספר טלפון";
                item.d1 = v;
                item.d2 = s(f2);
                if (name.isEmpty()) name = "הודעה · " + v;
                break;
            }
            case "whatsapp": {
                String v = s(f1);
                if (Launcher.waNumber(v).isEmpty()) return "הזן מספר טלפון";
                item.d1 = v;
                item.d2 = s(f2);
                if (name.isEmpty()) name = "וואטסאפ · " + v;
                break;
            }
            case "email": {
                String v = s(f1);
                if (!v.contains("@")) return "הזן כתובת אימייל תקינה";
                item.d1 = v;
                item.d2 = s(f2);
                item.d3 = s(f3);
                if (name.isEmpty()) name = v;
                break;
            }
            case "map": {
                String v = s(f1);
                if (v.isEmpty()) return "הזן כתובת או מקום";
                item.d1 = v;
                if (name.isEmpty()) name = v;
                break;
            }
            case "text": {
                String v = f1 == null ? "" : f1.getText().toString();
                if (v.trim().isEmpty()) return "הזן טקסט";
                item.d1 = v;
                if (name.isEmpty()) {
                    String one = v.trim().replace('\n', ' ');
                    name = one.length() > 24 ? one.substring(0, 24) + "…" : one;
                }
                break;
            }
            case "settings":
                if (item.d1.isEmpty()) return "בחר הגדרה";
                if (name.isEmpty()) name = item.d2;
                break;
            case "torch":
                if (name.isEmpty()) name = "פנס";
                break;
            case "timer": {
                long m = Types.parseSecs(s(f1));
                long sec = Types.parseSecs(s(f2));
                long total = m * 60 + sec;
                if (total <= 0) return "הזן משך זמן";
                item.d1 = String.valueOf(total);
                item.d2 = s(f3);
                if (name.isEmpty()) name = "טיימר " + Types.fmtSecs(total);
                break;
            }
            case "group":
                if (item.d1.isEmpty()) return "בחר לפחות קיצור אחד";
                if (name.isEmpty()) name = "תפריט";
                break;
        }
        item.name = name.isEmpty() ? Types.get(item.type).label : name;
        return null;
    }

    private void save(boolean pin) {
        String err = collect();
        if (err != null) {
            Ui.toast(this, err);
            return;
        }
        Store.put(this, item);
        saved = true;
        Launcher.refresh(this);
        if (pin) Launcher.pin(this, item);
        else Ui.toast(this, isNew ? "הקיצור נשמר" : "הקיצור עודכן");
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!saved && isNew) IconMaker.imageFile(this, item.id).delete();
    }
}

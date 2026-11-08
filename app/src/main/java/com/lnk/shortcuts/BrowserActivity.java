package com.lnk.shortcuts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** מנהל הקבצים המובנה של LinkIt. עובד גם כבוחר תיקייה/קובץ עבור העורך. */
public class BrowserActivity extends Activity {

    private static final int REQ_PERM = 7;
    private static final List<File> CLIP = new ArrayList<>();
    private static boolean clipCut = false;

    private static final class Entry {
        File f;
        boolean dir;
        long size, mod;
    }

    private String pick;
    private File cur;
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private boolean showHidden;
    private String sort;
    private String query = "";
    private SharedPreferences prefs;

    private TextView titleTv, subTv, emptyTv, pasteInfo;
    private EditText searchEt;
    private ScrollView homeScroll;
    private LinearLayout homeBox, permView, pickBar, pasteBar;
    private FrameLayout listHolder;
    private ListView list;
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.bg(this));
        getWindow().setNavigationBarColor(Ui.bg(this));
        if (!Ui.night(this)) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        prefs = getSharedPreferences("linkit_fm", MODE_PRIVATE);
        showHidden = prefs.getBoolean("hidden", false);
        sort = prefs.getString("sort", "name");

        Intent in = getIntent();
        pick = in.getStringExtra("pick");
        String start = in.getStringExtra("path");
        if (start == null && in.getData() != null) start = FileOps.pathFromUri(in.getData());

        buildUi();

        if (start != null) {
            File f = new File(start);
            if (f.isFile()) f = f.getParentFile();
            if (f != null && f.isDirectory()) cur = f;
        }
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        render();
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));
        Ui.rtl(root);

        // כותרת
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 4));
        TextView back = Ui.label(this, "→", 26, Ui.text(this), true);
        back.setGravity(Gravity.CENTER);
        back.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4));
        back.setClickable(true);
        back.setOnClickListener(v -> onBackPressed());
        head.addView(back);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titleTv = Ui.label(this, "", 19, Ui.text(this), true);
        titleTv.setMaxLines(1);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        subTv = Ui.label(this, "", 11, Ui.sub(this), false);
        subTv.setMaxLines(1);
        subTv.setEllipsize(TextUtils.TruncateAt.START);
        subTv.setTextDirection(View.TEXT_DIRECTION_LTR);
        titles.addView(titleTv);
        titles.addView(subTv);
        head.addView(titles, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        TextView searchBtn = Ui.label(this, "🔍", 20, Ui.text(this), false);
        searchBtn.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        searchBtn.setClickable(true);
        searchBtn.setOnClickListener(v -> toggleSearch());
        head.addView(searchBtn);
        TextView menu = Ui.label(this, "⋮", 26, Ui.text(this), true);
        menu.setGravity(Gravity.CENTER);
        menu.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        menu.setClickable(true);
        menu.setOnClickListener(v -> showMenu());
        head.addView(menu);
        root.addView(head);

        searchEt = Ui.input(this, "חיפוש בתיקייה…", "", InputType.TYPE_CLASS_TEXT, false, false);
        searchEt.setVisibility(View.GONE);
        root.addView(searchEt, Ui.lp(this, Ui.MATCH, Ui.WRAP, 14, 4, 14, 4));
        Ui.watch(searchEt, () -> {
            query = searchEt.getText().toString().trim();
            applyFilter();
        });

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        // מסך הרשאה
        permView = new LinearLayout(this);
        permView.setOrientation(LinearLayout.VERTICAL);
        permView.setGravity(Gravity.CENTER);
        permView.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), Ui.dp(this, 40));
        TextView p1 = Ui.label(this, "📂", 56, Ui.text(this), false);
        p1.setGravity(Gravity.CENTER);
        TextView p2 = Ui.label(this, "נדרשת גישה לקבצים", 20, Ui.text(this), true);
        p2.setGravity(Gravity.CENTER);
        TextView p3 = Ui.label(this, "כדי לדפדף בקבצים ולפתוח תיקיות, LinkIt צריך הרשאת גישה לכל הקבצים. ההרשאה נשארת במכשיר בלבד, ולאפליקציה אין גישה לאינטרנט.", 14, Ui.sub(this), false);
        p3.setGravity(Gravity.CENTER);
        p3.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        permView.addView(p1);
        permView.addView(p2);
        permView.addView(p3);
        permView.addView(Ui.button(this, "הענק גישה", true, v -> Perm.ask(this)));
        content.addView(permView, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // מסך הבית (מיקומי אחסון)
        homeScroll = new ScrollView(this);
        homeBox = new LinearLayout(this);
        homeBox.setOrientation(LinearLayout.VERTICAL);
        homeBox.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 24));
        homeScroll.addView(homeBox);
        content.addView(homeScroll, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // רשימת קבצים
        listHolder = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 16));
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        listHolder.addView(list, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        emptyTv = Ui.label(this, "התיקייה ריקה", 16, Ui.sub(this), false);
        emptyTv.setGravity(Gravity.CENTER);
        listHolder.addView(emptyTv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        content.addView(listHolder, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                return rowFor(shown.get(pos));
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> openEntry(shown.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            if (pick == null) showEntryActions(shown.get(pos));
            return true;
        });

        // פס בחירה (במצב בורר)
        pickBar = new LinearLayout(this);
        pickBar.setOrientation(LinearLayout.HORIZONTAL);
        pickBar.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 14));
        pickBar.setBackgroundColor(Ui.surface(this));
        pickBar.addView(Ui.button(this, "✔  בחר תיקייה זו", true, v -> returnPath(cur)),
                new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        root.addView(pickBar);

        // פס הדבקה
        pasteBar = new LinearLayout(this);
        pasteBar.setOrientation(LinearLayout.HORIZONTAL);
        pasteBar.setGravity(Gravity.CENTER_VERTICAL);
        pasteBar.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 14));
        pasteBar.setBackgroundColor(Ui.surface(this));
        pasteInfo = Ui.label(this, "", 14, Ui.text(this), true);
        pasteBar.addView(pasteInfo, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        pasteBar.addView(Ui.button(this, "ביטול", false, v -> {
            CLIP.clear();
            updateBars();
        }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        pasteBar.addView(Ui.button(this, "הדבק כאן", true, v -> paste()));
        root.addView(pasteBar);

        setContentView(root);
    }

    private void toggleSearch() {
        if (cur == null) return;
        boolean show = searchEt.getVisibility() != View.VISIBLE;
        searchEt.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            searchEt.requestFocus();
        } else {
            searchEt.setText("");
        }
    }

    // ------------------------------------------------------------- ניווט

    private void go(File dir) {
        cur = dir;
        query = "";
        searchEt.setText("");
        searchEt.setVisibility(View.GONE);
        render();
    }

    private void up() {
        if (cur == null) return;
        File p = cur.getParentFile();
        if (FileOps.isVolumeRoot(this, cur) || p == null || !p.canRead()) {
            cur = null;
            render();
        } else {
            go(p);
        }
    }

    @Override
    public void onBackPressed() {
        if (searchEt.getVisibility() == View.VISIBLE) {
            toggleSearch();
        } else if (cur == null) {
            super.onBackPressed();
        } else {
            up();
        }
    }

    private void render() {
        boolean ok = Perm.has(this);
        permView.setVisibility(ok ? View.GONE : View.VISIBLE);
        homeScroll.setVisibility(ok && cur == null ? View.VISIBLE : View.GONE);
        listHolder.setVisibility(ok && cur != null ? View.VISIBLE : View.GONE);
        if (ok && cur == null) buildHome();
        else if (ok) loadDir();

        if (cur == null) {
            titleTv.setText(pick == null ? "LinkIt קבצים" : "בחר מיקום");
            subTv.setText(pick == null ? "מנהל הקבצים המובנה" : "");
        } else {
            titleTv.setText(FileOps.displayName(this, cur));
            subTv.setText(cur.getAbsolutePath());
        }
        updateBars();
    }

    private void updateBars() {
        boolean inDir = cur != null && Perm.has(this);
        pickBar.setVisibility("folder".equals(pick) && inDir ? View.VISIBLE : View.GONE);
        boolean paste = pick == null && inDir && !CLIP.isEmpty();
        pasteBar.setVisibility(paste ? View.VISIBLE : View.GONE);
        if (paste) {
            pasteInfo.setText((clipCut ? "✂️ " : "📋 ") + CLIP.size() + " פריטים");
        }
    }

    // ---------------------------------------------------------- מסך הבית

    private View homeRow(String emoji, String title, String sub, final Runnable r) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.setBackground(Ui.press(this, Ui.rect(this, Ui.surface(this), 16)));
        card.setClickable(true);
        card.setOnClickListener(v -> r.run());
        TextView em = Ui.label(this, emoji, 24, Ui.text(this), false);
        em.setGravity(Gravity.CENTER);
        em.setBackground(Ui.rect(this, Ui.fieldBg(this), 14));
        card.addView(em, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.label(this, title, 16, Ui.text(this), true);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = Ui.label(this, sub, 12, Ui.sub(this), false);
            s.setMaxLines(1);
            s.setEllipsize(TextUtils.TruncateAt.END);
            tx.addView(s);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        lp.setMarginStart(Ui.dp(this, 12));
        card.addView(tx, lp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        clp.bottomMargin = Ui.dp(this, 8);
        card.setLayoutParams(clp);
        return card;
    }

    private void buildHome() {
        homeBox.removeAllViews();
        homeBox.addView(Ui.section(this, "מיקומי אחסון"));
        for (final File v : FileOps.volumes(this)) {
            long total = v.getTotalSpace();
            String sub = total > 0
                    ? "פנוי " + FileOps.human(v.getFreeSpace()) + " מתוך " + FileOps.human(total) : "";
            String emoji = v.getAbsolutePath().equals(FileOps.root()) ? "📱" : "💾";
            homeBox.addView(homeRow(emoji, FileOps.displayName(this, v), sub, () -> go(v)));
        }

        homeBox.addView(Ui.section(this, "גישה מהירה"));
        String[][] quick = {
                {Environment.DIRECTORY_DOWNLOADS, "הורדות", "⬇️"},
                {Environment.DIRECTORY_DCIM, "מצלמה", "📷"},
                {Environment.DIRECTORY_DOCUMENTS, "מסמכים", "📑"},
                {Environment.DIRECTORY_PICTURES, "תמונות", "🖼️"}};
        for (String[] q : quick) {
            final File d = Environment.getExternalStoragePublicDirectory(q[0]);
            if (d != null && d.isDirectory()) {
                homeBox.addView(homeRow(q[2], q[1], d.getAbsolutePath(), () -> go(d)));
            }
        }

        boolean header = false;
        for (Item it : Store.all(this)) {
            if (!"folder".equals(it.type)) continue;
            String p = FileOps.pathOf(it.d1);
            if (p == null) continue;
            final File d = new File(p);
            if (!d.isDirectory()) continue;
            if (!header) {
                homeBox.addView(Ui.section(this, "תיקיות מהקיצורים שלי"));
                header = true;
            }
            homeBox.addView(homeRow("📌", it.name, p, () -> go(d)));
        }

        homeBox.addView(Ui.section(this, "LinkIt"));
        homeBox.addView(homeRow("🔗", "ספריית הקיצורים", "פתח את מסך הקיצורים הראשי",
                () -> startActivity(new Intent(this, MainActivity.class))));
    }

    // ----------------------------------------------------------- רשימה

    private void loadDir() {
        all.clear();
        File[] fs = cur.listFiles();
        if (fs == null) {
            Ui.toast(this, "אין גישה לתיקייה הזו");
        } else {
            for (File f : fs) {
                if (!showHidden && f.getName().startsWith(".")) continue;
                Entry e = new Entry();
                e.f = f;
                e.dir = f.isDirectory();
                e.size = e.dir ? 0 : f.length();
                e.mod = f.lastModified();
                all.add(e);
            }
        }
        applyFilter();
    }

    private void applyFilter() {
        shown.clear();
        String q = query.toLowerCase(Locale.ROOT);
        for (Entry e : all) {
            if (!q.isEmpty() && !e.f.getName().toLowerCase(Locale.ROOT).contains(q)) continue;
            shown.add(e);
        }
        final String s = sort;
        Collections.sort(shown, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                if (a.dir != b.dir) return a.dir ? -1 : 1;
                switch (s) {
                    case "date":
                        return Long.compare(b.mod, a.mod);
                    case "size":
                        return Long.compare(b.size, a.size);
                    default:
                        return a.f.getName().compareToIgnoreCase(b.f.getName());
                }
            }
        });
        if (emptyTv != null) {
            emptyTv.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
            emptyTv.setText(all.isEmpty() ? "התיקייה ריקה" : "לא נמצאו תוצאות");
        }
        adapter.notifyDataSetChanged();
    }

    private View rowFor(Entry e) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(0, 0, 0, Ui.dp(this, 6));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        card.setBackground(Ui.press(this, Ui.rect(this, Ui.surface(this), 14)));
        card.setDuplicateParentStateEnabled(true);

        TextView em = Ui.label(this, FileOps.emoji(e.f), 22, Ui.text(this), false);
        em.setGravity(Gravity.CENTER);
        em.setBackground(Ui.rect(this, Ui.fieldBg(this), 12));
        card.addView(em, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));

        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView n = Ui.label(this, e.f.getName(), 15, Ui.text(this), true);
        n.setMaxLines(1);
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        String date = DateFormat.format("dd/MM/yy HH:mm", e.mod).toString();
        TextView s = Ui.label(this, e.dir ? date : FileOps.human(e.size) + "  ·  " + date,
                12, Ui.sub(this), false);
        s.setMaxLines(1);
        tx.addView(n);
        tx.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        lp.setMarginStart(Ui.dp(this, 12));
        card.addView(tx, lp);
        cell.addView(card);
        return cell;
    }

    private void openEntry(Entry e) {
        if (e.dir) {
            go(e.f);
        } else if ("file".equals(pick)) {
            returnPath(e.f);
        } else if (pick != null) {
            Ui.toast(this, "בחר תיקייה");
        } else if (e.f.getName().toLowerCase(Locale.ROOT).endsWith(".linkit")) {
            runLinkit(e.f);
        } else {
            FileOps.openFile(this, e.f, "");
        }
    }

    private void runLinkit(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < 262144) {
                bos.write(buf, 0, n);
                total += n;
            }
            Item it = LinkitFile.parse(new String(bos.toByteArray(), StandardCharsets.UTF_8));
            if (it == null) {
                Ui.toast(this, "קובץ קיצור לא תקין");
                return;
            }
            Intent i = Launcher.launchIntent(this, it);
            startActivity(i);
        } catch (Exception e) {
            Ui.toast(this, "לא ניתן לקרוא את קובץ הקיצור");
        }
    }

    private void returnPath(File f) {
        if (f == null) return;
        setResult(RESULT_OK, new Intent().putExtra("path", f.getAbsolutePath()));
        finish();
    }

    // ------------------------------------------------------------- דיאלוגים

    private void showList(String title, List<String> labels, final List<Runnable> acts) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        if (title != null) b.setTitle(title);
        b.setItems(labels.toArray(new String[0]), (d, which) -> acts.get(which).run());
        AlertDialog d = b.create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    private void ask(String title, String initial, String positive, final java.util.function.Consumer<String> cb) {
        final EditText et = Ui.input(this, "", initial, InputType.TYPE_CLASS_TEXT, false, false);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        box.addView(et, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        AlertDialog d = new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton(positive, (dlg, w) -> cb.accept(et.getText().toString().trim()))
                .setNegativeButton("ביטול", null).create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    private void showMenu() {
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        if (cur != null && Perm.has(this)) {
            if (pick == null) {
                l.add("➕  קיצור לתיקייה הזו (מסך הבית)");
                a.add(() -> addToHome(cur));
                l.add("💾  שמור את התיקייה בספריית הקיצורים");
                a.add(() -> saveToLibrary(cur));
                l.add("📁  תיקייה חדשה");
                a.add(this::newFolder);
            }
            l.add("↕️  מיון");
            a.add(this::showSort);
            l.add(showHidden ? "🙈  הסתר קבצים מוסתרים" : "👁️  הצג קבצים מוסתרים");
            a.add(() -> {
                showHidden = !showHidden;
                prefs.edit().putBoolean("hidden", showHidden).apply();
                render();
            });
            l.add("📎  העתק נתיב");
            a.add(() -> copyPath(cur.getAbsolutePath()));
            l.add("🔄  רענן");
            a.add(this::render);
        }
        l.add("🔗  ספריית הקיצורים");
        a.add(() -> startActivity(new Intent(this, MainActivity.class)));
        showList(null, l, a);
    }

    private void showSort() {
        final String[] keys = {"name", "date", "size"};
        String[] labels = {"לפי שם", "לפי תאריך (חדשים קודם)", "לפי גודל (גדולים קודם)"};
        int curIdx = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(sort)) curIdx = i;
        AlertDialog d = new AlertDialog.Builder(this).setTitle("מיון")
                .setSingleChoiceItems(labels, curIdx, (dlg, which) -> {
                    sort = keys[which];
                    prefs.edit().putString("sort", sort).apply();
                    dlg.dismiss();
                    applyFilter();
                }).create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    private void showEntryActions(final Entry e) {
        final File f = e.f;
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        l.add("➕  הוסף למסך הבית");
        a.add(() -> addToHome(f));
        l.add("✏️  קיצור מותאם (שם ואייקון)…");
        a.add(() -> {
            Item it = makeItem(f);
            Intent i = new Intent(this, EditorActivity.class);
            i.putExtra("type", it.type).putExtra("d1", it.d1).putExtra("d2", it.d2)
                    .putExtra("d3", it.d3).putExtra("name", it.name);
            startActivity(i);
        });
        l.add("💾  שמור בספריית הקיצורים");
        a.add(() -> saveToLibrary(f));
        l.add("🔗  צור קובץ קיצור (.linkit) בתיקייה זו");
        a.add(() -> writeLinkit(f));
        if (!e.dir) {
            l.add("🔓  פתח באמצעות…");
            a.add(() -> FileOps.openFile(this, f, "*ask"));
            l.add("📤  שתף");
            a.add(() -> FileOps.shareFile(this, f));
        }
        l.add("✂️  גזור");
        a.add(() -> setClip(f, true));
        l.add("📋  העתק");
        a.add(() -> setClip(f, false));
        l.add("📝  שנה שם");
        a.add(() -> rename(f));
        l.add("🗑️  מחק");
        a.add(() -> confirmDelete(f));
        l.add("📎  העתק נתיב");
        a.add(() -> copyPath(f.getAbsolutePath()));
        l.add("ℹ️  פרטים");
        a.add(() -> showInfo(e));
        showList(f.getName(), l, a);
    }

    // -------------------------------------------------------------- פעולות

    private Item makeItem(File f) {
        Item it = new Item();
        it.type = f.isDirectory() ? "folder" : "file";
        it.name = FileOps.displayName(this, f);
        it.d1 = f.getAbsolutePath();
        it.d2 = it.name;
        it.d3 = f.isDirectory() ? "linkit" : "";
        it.color = Types.get(it.type).color;
        if (f.isFile() && FileOps.mime(f).startsWith("image/")
                && IconMaker.saveImageIcon(this, it.id, Uri.fromFile(f))) {
            it.iconKind = "image";
        }
        return it;
    }

    private void addToHome(File f) {
        Item it = makeItem(f);
        Store.put(this, it);
        Launcher.refresh(this);
        Launcher.pin(this, it);
    }

    private void saveToLibrary(File f) {
        Store.put(this, makeItem(f));
        Launcher.notifyDocs(this);
        Ui.toast(this, "נשמר בספריית הקיצורים");
    }

    private void writeLinkit(File f) {
        try {
            File dir = f.getParentFile() != null && cur != null ? cur : f.getParentFile();
            Item it = makeItem(f);
            Store.put(this, it);
            File out = FileOps.uniqueDest(dir, LinkitFile.fileName(it));
            Store.writeFile(out, LinkitFile.write(it).getBytes(StandardCharsets.UTF_8));
            Ui.toast(this, "נוצר: " + out.getName());
            loadDir();
        } catch (Exception ex) {
            Ui.toast(this, "לא ניתן ליצור את קובץ הקיצור");
        }
    }

    private void newFolder() {
        ask("תיקייה חדשה", "", "צור", name -> {
            if (name.isEmpty()) return;
            File d = new File(cur, name);
            if (d.exists()) Ui.toast(this, "כבר קיים פריט בשם הזה");
            else if (!d.mkdirs()) Ui.toast(this, "לא ניתן ליצור את התיקייה");
            loadDir();
        });
    }

    private void rename(final File f) {
        ask("שינוי שם", f.getName(), "שמור", name -> {
            if (name.isEmpty() || name.equals(f.getName())) return;
            File to = new File(f.getParentFile(), name);
            if (to.exists()) Ui.toast(this, "כבר קיים פריט בשם הזה");
            else if (!f.renameTo(to)) Ui.toast(this, "שינוי השם נכשל");
            loadDir();
        });
    }

    private void confirmDelete(final File f) {
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("למחוק את \"" + f.getName() + "\"?")
                .setMessage(f.isDirectory() ? "כל התוכן שבתיקייה יימחק. אי אפשר לשחזר." : "אי אפשר לשחזר.")
                .setPositiveButton("מחק", (dlg, w) -> {
                    if (!FileOps.deleteRecursive(f)) Ui.toast(this, "המחיקה נכשלה");
                    loadDir();
                })
                .setNegativeButton("ביטול", null).create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    private void setClip(File f, boolean cut) {
        CLIP.clear();
        CLIP.add(f);
        clipCut = cut;
        updateBars();
    }

    private void paste() {
        if (cur == null || CLIP.isEmpty()) return;
        final File dest = cur;
        final List<File> src = new ArrayList<>(CLIP);
        final boolean cut = clipCut;
        Ui.toast(this, "מעבד…");
        new Thread(() -> {
            String err = null;
            for (File s : src) {
                File d = FileOps.uniqueDest(dest, s.getName());
                try {
                    if (s.isDirectory() && d.getAbsolutePath().startsWith(s.getAbsolutePath() + "/")) {
                        err = "אי אפשר להעתיק תיקייה לתוך עצמה";
                        continue;
                    }
                    if (cut) {
                        if (!s.renameTo(d)) {
                            FileOps.copyRecursive(s, d);
                            FileOps.deleteRecursive(s);
                        }
                    } else {
                        FileOps.copyRecursive(s, d);
                    }
                } catch (Exception ex) {
                    err = "חלק מהפריטים לא הועברו";
                }
            }
            final String fe = err;
            runOnUiThread(() -> {
                if (cut) CLIP.clear();
                Ui.toast(this, fe == null ? "בוצע" : fe);
                updateBars();
                loadDir();
            });
        }).start();
    }

    private void copyPath(String p) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("path", p));
        Ui.toast(this, "הנתיב הועתק");
    }

    private void showInfo(Entry e) {
        File f = e.f;
        StringBuilder sb = new StringBuilder();
        sb.append("נתיב:\n").append(f.getAbsolutePath()).append("\n\n");
        if (e.dir) {
            String[] kids = f.list();
            sb.append("פריטים בתיקייה: ").append(kids == null ? "?" : String.valueOf(kids.length));
        } else {
            sb.append("גודל: ").append(FileOps.human(e.size)).append("\n");
            sb.append("סוג: ").append(FileOps.mime(f));
        }
        sb.append("\nעודכן: ").append(DateFormat.format("dd/MM/yyyy HH:mm", e.mod));
        sb.append("\nקריאה: ").append(f.canRead() ? "כן" : "לא")
                .append("   כתיבה: ").append(f.canWrite() ? "כן" : "לא");
        AlertDialog d = new AlertDialog.Builder(this).setTitle(f.getName())
                .setMessage(sb.toString()).setPositiveButton("סגור", null).create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }
}

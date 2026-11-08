package com.lnk.shortcuts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQ_EXPORT = 10, REQ_IMPORT = 11, REQ_EXPORT_ONE = 12, REQ_EXPORT_DIR = 13;
    private Item exportItem;

    private final List<Item> all = new ArrayList<>();
    private final List<Item> shown = new ArrayList<>();
    private String query = "";
    private String typeFilter = null;

    private ListView list;
    private BaseAdapter adapter;
    private TextView countTv;
    private LinearLayout chips;
    private HorizontalScrollView chipsScroll;
    private LinearLayout emptyView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.bg(this));
        getWindow().setNavigationBarColor(Ui.bg(this));
        if (!Ui.night(this)) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.bg(this));
        Ui.rtl(root);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // כותרת
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 4));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.label(this, "LinkIt", 28, Ui.text(this), true));
        countTv = Ui.label(this, "", 13, Ui.sub(this), false);
        titles.addView(countTv);
        head.addView(titles, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        TextView menu = Ui.label(this, "⋮", 28, Ui.text(this), true);
        menu.setGravity(Gravity.CENTER);
        menu.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        menu.setClickable(true);
        menu.setOnClickListener(v -> showMenu());
        TextView files = Ui.label(this, "📂", 22, Ui.text(this), false);
        files.setGravity(Gravity.CENTER);
        files.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        files.setClickable(true);
        files.setOnClickListener(v -> startActivity(new Intent(this, BrowserActivity.class)));
        head.addView(files);
        head.addView(menu);
        col.addView(head);

        // חיפוש
        EditText search = Ui.input(this, "🔍  חיפוש קיצורים…", "", InputType.TYPE_CLASS_TEXT, false, false);
        col.addView(search, Ui.lp(this, Ui.MATCH, Ui.WRAP, 16, 8, 16, 4));
        Ui.watch(search, () -> {
            query = search.getText().toString().trim();
            applyFilter();
        });

        // סינון לפי סוג
        chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 4));
        chipsScroll.addView(chips);
        col.addView(chipsScroll);

        // רשימה + מצב ריק
        FrameLayout holder = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 100));
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        holder.addView(list, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        emptyView = new LinearLayout(this);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), Ui.dp(this, 80));
        TextView e1 = Ui.label(this, "🔗", 56, Ui.text(this), false);
        e1.setGravity(Gravity.CENTER);
        TextView e2 = Ui.label(this, "אין עדיין קיצורים", 20, Ui.text(this), true);
        e2.setGravity(Gravity.CENTER);
        TextView e3 = Ui.label(this, "לחץ על \"＋ חדש\" כדי ליצור קיצור לאתר, קובץ, אפליקציה, איש קשר ועוד. אפשר גם לשתף קישור מכל אפליקציה אל LinkIt.", 14, Ui.sub(this), false);
        e3.setGravity(Gravity.CENTER);
        e3.setPadding(0, Ui.dp(this, 8), 0, 0);
        emptyView.addView(e1);
        emptyView.addView(e2);
        emptyView.addView(e3);
        TextView first = Ui.button(this, "＋  צור קיצור ראשון", true, v -> showTypePicker());
        emptyView.addView(first, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 20, 0, 0));
        emptyView.addView(Ui.creditCard(this), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 20, 0, 0));
        holder.addView(emptyView, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        col.addView(holder, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                Row r;
                if (cv == null) {
                    r = new Row();
                    cv = r.cell;
                    cv.setTag(r);
                } else {
                    r = (Row) cv.getTag();
                }
                r.bind(shown.get(pos));
                return cv;
            }
        };
        list.addFooterView(Ui.creditCard(this), null, false);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < shown.size()) showActions(shown.get(pos));
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            if (pos < 0 || pos >= shown.size()) return false;
            Launcher.run(this, shown.get(pos), false);
            return true;
        });

        // כפתור יצירה
        TextView fab = Ui.label(this, "＋  חדש", 17, Ui.onPrimary(this), true);
        fab.setGravity(Gravity.CENTER);
        fab.setPadding(Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 16));
        fab.setBackground(Ui.press(this, Ui.rect(this, Ui.primary(this), 28)));
        fab.setElevation(Ui.dp(this, 6));
        fab.setClickable(true);
        fab.setOnClickListener(v -> showTypePicker());
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP,
                Gravity.BOTTOM | Gravity.END);
        flp.setMargins(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 24));
        root.addView(fab, flp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IconMaker.clearCache();
        reload();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Launcher.syncDynamic(this);
    }

    private void reload() {
        all.clear();
        all.addAll(Store.all(this));
        // אם הסוג שסונן נעלם
        if (typeFilter != null) {
            boolean ok = false;
            for (Item it : all) if (it.type.equals(typeFilter)) ok = true;
            if (!ok) typeFilter = null;
        }
        buildChips();
        applyFilter();
    }

    private void buildChips() {
        chips.removeAllViews();
        Set<String> present = new LinkedHashSet<>();
        for (Types.T t : Types.ALL) {
            for (Item it : all) {
                if (it.type.equals(t.id)) {
                    present.add(t.id);
                    break;
                }
            }
        }
        chipsScroll.setVisibility(present.size() > 1 ? View.VISIBLE : View.GONE);
        addChip("הכל · " + all.size(), null);
        for (String id : present) {
            Types.T t = Types.get(id);
            int n = 0;
            for (Item it : all) if (it.type.equals(id)) n++;
            addChip(t.emoji + " " + t.label + " · " + n, id);
        }
    }

    private void addChip(String label, final String type) {
        boolean sel = (type == null && typeFilter == null) || (type != null && type.equals(typeFilter));
        TextView c = Ui.chip(this, label, sel, v -> {
            typeFilter = type;
            buildChips();
            applyFilter();
        });
        chips.addView(c, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
    }

    private void applyFilter() {
        shown.clear();
        String q = query.toLowerCase(Locale.ROOT);
        for (Item it : all) {
            if (typeFilter != null && !typeFilter.equals(it.type)) continue;
            if (!q.isEmpty()
                    && !(it.name.toLowerCase(Locale.ROOT).contains(q)
                    || it.d1.toLowerCase(Locale.ROOT).contains(q)
                    || it.d2.toLowerCase(Locale.ROOT).contains(q))) continue;
            shown.add(it);
        }
        final String sort = Store.sort(this);
        Collections.sort(shown, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                switch (sort) {
                    case "name":
                        return a.name.compareToIgnoreCase(b.name);
                    case "uses":
                        return Integer.compare(b.uses, a.uses);
                    case "new":
                        return Long.compare(b.created, a.created);
                    default:
                        return Long.compare(Math.max(b.lastUsed, b.created), Math.max(a.lastUsed, a.created));
                }
            }
        });
        countTv.setText(all.isEmpty() ? "הקיצורים שלך, תמיד זמינים" : all.size() + " קיצורים");
        emptyView.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (!shown.isEmpty()) {
            emptyView.setVisibility(View.GONE);
        } else if (!all.isEmpty()) {
            ((TextView) emptyView.getChildAt(1)).setText("לא נמצאו תוצאות");
            ((TextView) emptyView.getChildAt(2)).setText("נסה חיפוש אחר או הסר את הסינון.");
        } else {
            ((TextView) emptyView.getChildAt(1)).setText("אין עדיין קיצורים");
            ((TextView) emptyView.getChildAt(2)).setText("לחץ על \"＋ חדש\" כדי ליצור קיצור לאתר, קובץ, אפליקציה, מספר טלפון ועוד. אפשר גם לשתף קישור מכל אפליקציה אל LinkIt.");
        }
        adapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------ שורת רשימה

    private final class Row {
        final LinearLayout cell;
        final ImageView icon;
        final TextView name, sub, pin;
        Item item;

        Row() {
            cell = new LinearLayout(MainActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(0, 0, 0, Ui.dp(MainActivity.this, 8));

            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(Ui.dp(MainActivity.this, 12), Ui.dp(MainActivity.this, 12),
                    Ui.dp(MainActivity.this, 12), Ui.dp(MainActivity.this, 12));
            card.setBackground(Ui.press(MainActivity.this, Ui.rect(MainActivity.this, Ui.surface(MainActivity.this), 18)));
            card.setDuplicateParentStateEnabled(true);

            icon = new ImageView(MainActivity.this);
            card.addView(icon, new LinearLayout.LayoutParams(Ui.dp(MainActivity.this, 48), Ui.dp(MainActivity.this, 48)));

            LinearLayout texts = new LinearLayout(MainActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            name = Ui.label(MainActivity.this, "", 16, Ui.text(MainActivity.this), true);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            sub = Ui.label(MainActivity.this, "", 13, Ui.sub(MainActivity.this), false);
            sub.setMaxLines(1);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            texts.addView(sub);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
            tlp.setMarginStart(Ui.dp(MainActivity.this, 12));
            tlp.setMarginEnd(Ui.dp(MainActivity.this, 8));
            card.addView(texts, tlp);

            pin = Ui.label(MainActivity.this, "הוסף", 13, Ui.primary(MainActivity.this), true);
            pin.setGravity(Gravity.CENTER);
            pin.setPadding(Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 8),
                    Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 8));
            pin.setBackground(Ui.press(MainActivity.this, Ui.rect(MainActivity.this, Ui.tint(MainActivity.this), 16)));
            pin.setClickable(true);
            pin.setOnClickListener(v -> {
                if (item != null) Launcher.pin(MainActivity.this, item);
            });
            card.addView(pin);
            cell.addView(card);
        }

        void bind(Item it) {
            item = it;
            icon.setImageBitmap(IconMaker.preview(MainActivity.this, it, Ui.dp(MainActivity.this, 48)));
            name.setText(it.name);
            String s = Types.subtitle(it);
            if (it.uses > 0) s += "  ·  " + it.uses + "×";
            sub.setText(s);
        }
    }

    // ------------------------------------------------------------- פעולות

    private void showActions(final Item it) {
        String[] labels = {"➕  הוסף למסך הבית", "▶️  הפעל עכשיו", "✏️  עריכה", "📄  שכפול", "🗑️  מחיקה", "📤  שמור כקובץ .linkit"};
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(it.name)
                .setItems(labels, (dlg, which) -> {
                    switch (which) {
                        case 0:
                            Launcher.pin(this, it);
                            break;
                        case 1:
                            Launcher.run(this, it, false);
                            break;
                        case 2:
                            startActivity(new Intent(this, EditorActivity.class).putExtra("id", it.id));
                            break;
                        case 3:
                            Store.duplicate(this, it);
                            reload();
                            Ui.toast(this, "הקיצור שוכפל");
                            break;
                        case 4:
                            confirmDelete(it);
                            break;
                        case 5: {
                            exportItem = it;
                            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            i.setType("application/octet-stream");
                            i.putExtra(Intent.EXTRA_TITLE, LinkitFile.fileName(it));
                            startActivityForResult(i, REQ_EXPORT_ONE);
                            break;
                        }
                    }
                })
                .create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    private void confirmDelete(final Item it) {
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("למחוק את \"" + it.name + "\"?")
                .setMessage("אם הקיצור נמצא במסך הבית, הוא יושבת (ניתן להסיר אותו משם ידנית).")
                .setPositiveButton("מחק", (dlg, w) -> {
                    Store.delete(this, it.id);
                    Launcher.refresh(this);
                    reload();
                })
                .setNegativeButton("ביטול", null)
                .create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    private void showTypePicker() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        sv.addView(box);

        final AlertDialog d = new AlertDialog.Builder(this).setTitle("איזה קיצור ליצור?").setView(sv).create();

        LinearLayout row = null;
        for (int i = 0; i < Types.ALL.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
            }
            final Types.T t = Types.ALL[i];
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12));
            tile.setBackground(Ui.press(this, Ui.rect(this, 0x00000000, 14)));
            tile.setClickable(true);
            TextView em = Ui.label(this, t.emoji, 26, 0xFFFFFFFF, false);
            em.setGravity(Gravity.CENTER);
            em.setBackground(Ui.rect(this, t.color, 16));
            tile.addView(em, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)));
            TextView lb = Ui.label(this, t.label, 13, Ui.text(this), true);
            lb.setGravity(Gravity.CENTER);
            lb.setPadding(0, Ui.dp(this, 6), 0, 0);
            tile.addView(lb);
            tile.setOnClickListener(v -> {
                d.dismiss();
                startActivity(new Intent(this, EditorActivity.class).putExtra("type", t.id));
            });
            row.addView(tile, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        }
        // השלמת שורה אחרונה
        int rem = Types.ALL.length % 3;
        if (rem != 0 && row != null) {
            for (int k = rem; k < 3; k++) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    private void showMenu() {
        String[] labels = {"↕️  מיון הרשימה", "📤  ייצוא גיבוי", "📥  ייבוא גיבוי", "📦  ייצוא כל הקיצורים כקבצי .linkit לתיקייה", "📂  מנהל הקבצים", "📍  מנהל הקבצים המועדף שלי", "💡  איך זה עובד", "📋  צור קיצור מהלוח", "ℹ️  אודות"};
        AlertDialog d = new AlertDialog.Builder(this)
                .setItems(labels, (dlg, which) -> {
                    switch (which) {
                        case 0:
                            showSort();
                            break;
                        case 1: {
                            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            i.setType("application/json");
                            i.putExtra(Intent.EXTRA_TITLE, "linkit-backup.json");
                            startActivityForResult(i, REQ_EXPORT);
                            break;
                        }
                        case 2: {
                            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            i.setType("*/*");
                            startActivityForResult(i, REQ_IMPORT);
                            break;
                        }
                        case 3: {
                            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                            startActivityForResult(i, REQ_EXPORT_DIR);
                            break;
                        }
                        case 4:
                            startActivity(new Intent(this, BrowserActivity.class));
                            break;
                        case 5:
                            Launcher.pickPreferredFm(this);
                            break;
                        case 6:
                            showHelp();
                            break;
                        case 7:
                            fromClipboard();
                            break;
                        case 8:
                            showAbout();
                            break;
                    }
                })
                .create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    private void fromClipboard() {
        String text = null;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (cs != null) text = cs.toString().trim();
            }
        } catch (Exception ignored) {
        }
        if (text == null || text.isEmpty()) {
            Ui.toast(this, "הלוח ריק");
            return;
        }
        Intent i = new Intent(this, EditorActivity.class);
        i.setAction(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(i);
    }

    private void showAbout() {
        String ver = "";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 8));
        TextView t1 = Ui.label(this, "LinkIt", 26, Ui.text(this), true);
        TextView t2 = Ui.label(this, "גרסה " + ver, 13, Ui.sub(this), false);
        TextView t3 = Ui.label(this, "קיצורי דרך ומנהל קבצים. ללא הרשאת אינטרנט, הכול נשמר במכשיר בלבד.", 14, Ui.text(this), false);
        t3.setPadding(0, Ui.dp(this, 12), 0, 0);
        box.addView(t1);
        box.addView(t2);
        box.addView(t3);
        box.addView(Ui.creditCard(this));
        AlertDialog d = new AlertDialog.Builder(this)
                .setView(box)
                .setPositiveButton("סגור", null)
                .create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    private void showSort() {
        final String[] keys = {"recent", "uses", "name", "new"};
        String[] labels = {"אחרונים בשימוש", "הכי בשימוש", "לפי שם (א-ב)", "חדשים ראשונים"};
        int cur = 0;
        String s = Store.sort(this);
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(s)) cur = i;
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("מיון")
                .setSingleChoiceItems(labels, cur, (dlg, which) -> {
                    Store.setSort(this, keys[which]);
                    dlg.dismiss();
                    applyFilter();
                })
                .create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    private void showHelp() {
        String msg = "• לחץ \"＋ חדש\", בחר סוג וצור קיצור. הוא נשמר כאן וגם מתווסף למסך הבית.\n\n"
                + "• לחיצה ארוכה על קיצור ברשימה מפעילה אותו מיד.\n\n"
                + "• לחיצה על קיצור ברשימה: הוספה מחדש למסך הבית, הפעלה, עריכה, שכפול ומחיקה.\n\n"
                + "• עריכת קיצור (שם, אייקון, יעד) מעדכנת אוטומטית גם את הקיצור שכבר נמצא במסך הבית.\n\n"
                + "• \"תפריט\" הוא קיצור אחד שפותח חלון עם כמה קיצורים לבחירה.\n\n"
                + "• בכל אפליקציה: שתף קישור או טקסט ← LinkIt, ותוכל ליצור ממנו קיצור מיד.\n\n"
                + "• לחיצה ארוכה על אייקון LinkIt מציגה את הקיצורים האחרונים שלך.\n\n"
                + "• האפליקציה ללא הרשאת אינטרנט, והכול נשמר במכשיר בלבד. מומלץ לייצא גיבוי מדי פעם.\n\n"
                + "• קיצורי קובץ ותיקייה דורשים בחירה מחדש לאחר ייבוא גיבוי למכשיר אחר.\n\n"
                + "• מנהל הקבצים (📂): דפדוף, העתקה, העברה, מחיקה ויצירת קיצורים מכל קובץ או תיקייה.\n\n"
                + "• בקיצור תיקייה אפשר לבחור באיזה מנהל קבצים לפתוח. במצב \"אוטומטי\", קובץ קיצור שנפתח מתוך מנהל קבצים יפתח את התיקייה באותו מנהל, בלי לעבור לאחר.\n\n"
                + "• קיצורי קובץ ותיקייה נקראים בשם היעד עצמו, ואפשר להציג עליהם חץ קיצור כמו בווינדוס.\n\n"
                + "• קבצי .linkit: קיצור שנשמר כקובץ נפתח מכל מנהל קבצים. הקיצורים שלך מופיעים גם כאחסון \"LinkIt\" בתוך מנהלי קבצים שתומכים בספקי אחסון.\n\n"
                + "פותח על ידי יהודי לא פשוט @מתמחים טופ";
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("איך זה עובד")
                .setMessage(msg)
                .setPositiveButton("הבנתי", null)
                .create();
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_EXPORT_ONE && exportItem != null) {
                try (OutputStream o = getContentResolver().openOutputStream(uri, "wt")) {
                    o.write(LinkitFile.write(exportItem).getBytes(StandardCharsets.UTF_8));
                }
                Ui.toast(this, "קובץ הקיצור נשמר");
            } else if (req == REQ_EXPORT_DIR) {
                Uri parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                        uri, android.provider.DocumentsContract.getTreeDocumentId(uri));
                int n = 0;
                for (Item it : Store.all(this)) {
                    Uri f = android.provider.DocumentsContract.createDocument(
                            getContentResolver(), parent, "application/octet-stream", LinkitFile.fileName(it));
                    if (f == null) continue;
                    try (OutputStream o = getContentResolver().openOutputStream(f, "wt")) {
                        o.write(LinkitFile.write(it).getBytes(StandardCharsets.UTF_8));
                    }
                    n++;
                }
                Ui.toast(this, "נשמרו " + n + " קבצי קיצור");
            } else if (req == REQ_EXPORT) {
                String json = Store.exportJson(this);
                try (OutputStream o = getContentResolver().openOutputStream(uri, "wt")) {
                    o.write(json.getBytes(StandardCharsets.UTF_8));
                }
                Ui.toast(this, "הגיבוי נשמר");
            } else if (req == REQ_IMPORT) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                }
                int count = Store.importJson(this, new String(bos.toByteArray(), StandardCharsets.UTF_8));
                Launcher.refresh(this);
                reload();
                Ui.toast(this, "יובאו " + count + " קיצורים");
            }
        } catch (Exception e) {
            Ui.toast(this, "הפעולה נכשלה: " + (e.getMessage() == null ? "שגיאה" : e.getMessage()));
        }
    }
}

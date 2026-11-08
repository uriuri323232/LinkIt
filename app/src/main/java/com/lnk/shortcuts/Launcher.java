package com.lnk.shortcuts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** הפעלת קיצורים, יצירת קיצורי מסך-בית וסנכרון. */
final class Launcher {
    private Launcher() {}

    static final String ACTION_OPEN = "com.lnk.shortcuts.OPEN";
    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

    static String fixUrl(String s) {
        s = s.trim();
        return SCHEME.matcher(s).matches() ? s : "https://" + s;
    }

    /** מספר וואטסאפ בפורמט בינלאומי. 05x... הופך ל-9725x... */
    static String waNumber(String raw) {
        String d = raw.replaceAll("[^0-9]", "");
        if (d.startsWith("00")) d = d.substring(2);
        else if (d.startsWith("0")) d = "972" + d.substring(1);
        return d;
    }

    // ------------------------------------------------------------------ הפעלה

    /** מפעיל קיצור. closeAfter = לסגור את ה-Activity הנוכחי בסיום. */
    static void run(final Activity a, Item it, boolean closeAfter) {
        Store.touch(a, it.id);
        boolean keepOpen = false;
        try {
            switch (it.type) {
                case "url": {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(fixUrl(it.d1)));
                    startWith(a, i, it.d3, it.d1);
                    break;
                }
                case "file":
                    openFileItem(a, it);
                    break;
                case "folder":
                    openFolderItem(a, it);
                    break;
                case "app": {
                    Intent i = a.getPackageManager().getLaunchIntentForPackage(it.d1);
                    if (i == null) Ui.toast(a, "האפליקציה לא מותקנת");
                    else start(a, i);
                    break;
                }
                case "call":
                    start(a, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(it.d1))));
                    break;
                case "sms": {
                    Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(it.d1)));
                    if (!it.d2.isEmpty()) i.putExtra("sms_body", it.d2);
                    start(a, i);
                    break;
                }
                case "whatsapp": {
                    String u = "https://wa.me/" + waNumber(it.d1)
                            + (it.d2.isEmpty() ? "" : "?text=" + Uri.encode(it.d2));
                    start(a, new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
                    break;
                }
                case "email": {
                    Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + it.d1));
                    if (!it.d2.isEmpty()) i.putExtra(Intent.EXTRA_SUBJECT, it.d2);
                    if (!it.d3.isEmpty()) i.putExtra(Intent.EXTRA_TEXT, it.d3);
                    start(a, i);
                    break;
                }
                case "map":
                    start(a, new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(it.d1))));
                    break;
                case "text": {
                    ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText(it.name, it.d1));
                    Ui.toast(a, "הטקסט הועתק");
                    break;
                }
                case "settings": {
                    try {
                        start(a, new Intent(it.d1));
                    } catch (ActivityNotFoundException e) {
                        start(a, new Intent("android.settings.SETTINGS"));
                    }
                    break;
                }
                case "torch":
                    toggleTorch(a);
                    break;
                case "timer": {
                    Intent i = new Intent(AlarmClock.ACTION_SET_TIMER);
                    i.putExtra(AlarmClock.EXTRA_LENGTH, (int) Math.max(1, Types.parseSecs(it.d1)));
                    i.putExtra(AlarmClock.EXTRA_MESSAGE, it.d2);
                    i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                    start(a, i);
                    break;
                }
                case "group":
                    keepOpen = true;
                    showGroup(a, it, closeAfter);
                    break;
                default:
                    Ui.toast(a, "סוג קיצור לא מוכר");
            }
        } catch (ActivityNotFoundException e) {
            Ui.toast(a, "לא נמצאה אפליקציה מתאימה לפתיחה");
        } catch (SecurityException e) {
            Ui.toast(a, "אין גישה ליעד. ערוך את הקיצור ובחר אותו מחדש");
        } catch (Exception e) {
            Ui.toast(a, "לא ניתן לפתוח את היעד");
        }
        if (closeAfter && !keepOpen) a.finish();
    }

    /** פותח Intent. pref: "" = ברירת מחדל, "*ask" = חלון בחירה, אחרת חבילה. */
    private static void startWith(Activity a, Intent i, String pref, String title) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if ("*ask".equals(pref)) {
            Intent c = Intent.createChooser(i, title);
            c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(c);
            return;
        }
        if (pref != null && !pref.isEmpty()) {
            i.setPackage(pref);
            try {
                a.startActivity(i);
                return;
            } catch (ActivityNotFoundException e) {
                i.setPackage(null);
                Ui.toast(a, "האפליקציה שנבחרה לא יכולה לפתוח את זה, נפתח באפליקציה אחרת");
            }
        }
        a.startActivity(i);
    }

    private static void openFileItem(Activity a, Item it) {
        String path = it.d1.startsWith("/") ? it.d1 : null;
        if (path != null) {
            if (!Perm.has(a)) {
                Ui.toast(a, "נדרשת הרשאת גישה לקבצים");
                Perm.ask(a);
                return;
            }
            FileOps.openFile(a, new java.io.File(path), it.d3);
            return;
        }
        Uri u = Uri.parse(it.d1);
        String mime = a.getContentResolver().getType(u);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, mime == null ? "*/*" : mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startWith(a, i, it.d3, it.d2);
    }

    /**
     * פתיחת תיקייה. d3: "" ברירת מחדל, "linkit" המנהל המובנה, "*ask" בחירה בכל פעם,
     * או חבילה של מנהל קבצים ספציפי (כך אין "קפיצה" למנהל אחר).
     */
    private static void openFolderItem(Activity a, Item it) {
        String pref = it.d3 == null ? "" : it.d3;
        boolean saf = !it.d1.startsWith("/");
        String path = FileOps.pathOf(it.d1);
        Uri safDoc = saf ? FileOps.docUriFromSaf(Uri.parse(it.d1)) : null;

        if (pref.isEmpty()) pref = saf ? "system" : "linkit";

        if ("*ask".equals(pref)) {
            Uri doc = path != null ? FileOps.docUriForPath(path) : safDoc;
            Intent v = new Intent(Intent.ACTION_VIEW);
            if (doc != null) v.setDataAndType(doc, FileOps.DIR_MIME);
            else v.setDataAndType(Uri.fromFile(new java.io.File(path == null ? "/" : path)), "resource/folder");
            v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent c = Intent.createChooser(v, it.name);
            if (path != null) {
                c.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{
                        new Intent(a, BrowserActivity.class).putExtra("path", path)});
            }
            c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(c);
            return;
        }
        if ("linkit".equals(pref)) {
            if (path != null) {
                start(a, new Intent(a, BrowserActivity.class).putExtra("path", path));
                return;
            }
            pref = "system";
        }
        if ("system".equals(pref)) {
            if (!FileOps.openFolderIn(a, path, safDoc, "")) {
                Ui.toast(a, "אין אפליקציה שיכולה לפתוח תיקיות");
            }
            return;
        }
        // מנהל קבצים ספציפי
        if (!FileOps.openFolderIn(a, path, safDoc, pref)) {
            Intent li = a.getPackageManager().getLaunchIntentForPackage(pref);
            if (li == null) {
                Ui.toast(a, "מנהל הקבצים שנבחר לא מותקן");
            } else {
                if (path != null) {
                    ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("path", path));
                }
                start(a, li);
                Ui.toast(a, "מנהל הקבצים הזה לא תומך בפתיחה ישירה של תיקייה. הנתיב הועתק ללוח");
            }
        }
    }

    private static void start(Context c, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    private static void toggleTorch(Context ctx) throws Exception {
        final CameraManager cm = (CameraManager) ctx.getApplicationContext()
                .getSystemService(Context.CAMERA_SERVICE);
        String found = null;
        for (String id : cm.getCameraIdList()) {
            CameraCharacteristics cc = cm.getCameraCharacteristics(id);
            Boolean flash = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
            if (flash != null && flash && facing != null
                    && facing == CameraCharacteristics.LENS_FACING_BACK) {
                found = id;
                break;
            }
        }
        if (found == null) {
            Ui.toast(ctx, "לא נמצא פנס במכשיר");
            return;
        }
        final String fid = found;
        cm.registerTorchCallback(new CameraManager.TorchCallback() {
            private boolean done = false;

            @Override
            public void onTorchModeChanged(String cameraId, boolean enabled) {
                if (done || !cameraId.equals(fid)) return;
                done = true;
                try {
                    cm.setTorchMode(fid, !enabled);
                } catch (Exception ignored) {
                }
                cm.unregisterTorchCallback(this);
            }
        }, new Handler(Looper.getMainLooper()));
    }

    /** חלון בחירה של קיצור מסוג "תפריט". */
    private static void showGroup(final Activity a, Item g, final boolean close) {
        final List<Item> kids = new ArrayList<>();
        for (String id : g.d1.split(",")) {
            if (id.isEmpty()) continue;
            Item k = Store.get(a, id);
            if (k != null) kids.add(k);
        }
        if (kids.isEmpty()) {
            Ui.toast(a, "התפריט ריק");
            if (close) a.finish();
            return;
        }
        final boolean[] chosen = {false};
        ListView lv = new ListView(a);
        lv.setDividerHeight(0);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return kids.size(); }
            @Override public Object getItem(int i) { return kids.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                Item k = kids.get(pos);
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(a, 20), Ui.dp(a, 10), Ui.dp(a, 20), Ui.dp(a, 10));
                ImageView iv = new ImageView(a);
                iv.setImageBitmap(IconMaker.preview(a, k, Ui.dp(a, 44)));
                row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
                LinearLayout col = new LinearLayout(a);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView n = Ui.label(a, k.name, 16, Ui.text(a), true);
                n.setMaxLines(1);
                TextView s = Ui.label(a, Types.get(k.type).label, 12, Ui.sub(a), false);
                col.addView(n);
                col.addView(s);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
                lp.setMarginStart(Ui.dp(a, 14));
                row.addView(col, lp);
                return row;
            }
        });
        final AlertDialog d = new AlertDialog.Builder(a).setTitle(g.name).setView(lv).create();
        lv.setOnItemClickListener((p, v, pos, id) -> {
            chosen[0] = true;
            d.dismiss();
            run(a, kids.get(pos), close);
        });
        d.setOnDismissListener(x -> {
            if (close && !chosen[0]) a.finish();
        });
        d.show();
        Ui.rtl(d.getWindow().getDecorView());
    }

    // ------------------------------------------------------- מסך בית / דינמיים

    /** Intent שמפעיל קיצור דרך LaunchActivity (עם גיבוי נתונים בתוכו). */
    static Intent launchIntent(Context c, Item it) {
        Intent i = new Intent(c, LaunchActivity.class);
        i.setAction(ACTION_OPEN);
        i.putExtra("id", it.id);
        i.putExtra("name", it.name);
        i.putExtra("type", it.type);
        i.putExtra("d1", it.d1);
        i.putExtra("d2", it.d2);
        i.putExtra("d3", it.d3);
        return i;
    }

    static ShortcutInfo build(Context c, Item it) {
        String label = it.name.isEmpty() ? "קיצור" : it.name;
        return new ShortcutInfo.Builder(c, "lnk_" + it.id)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(IconMaker.icon(c, it))
                .setIntent(launchIntent(c, it))
                .build();
    }

    static boolean pin(Context c, Item it) {
        ShortcutManager sm = c.getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            Ui.toast(c, "המשגר (Launcher) שלך לא תומך בהוספת קיצורים למסך הבית");
            return false;
        }
        return sm.requestPinShortcut(build(c, it), null);
    }

    private static Item find(List<Item> items, String id) {
        for (Item it : items) if (it.id.equals(id)) return it;
        return null;
    }

    /** מעדכן קיצורים שכבר נמצאים במסך הבית, ומנטרל קיצורים שנמחקו. */
    static void refresh(Context c) {
        try {
            ShortcutManager sm = c.getSystemService(ShortcutManager.class);
            if (sm == null) return;
            List<Item> items = Store.all(c);
            List<ShortcutInfo> upd = new ArrayList<>();
            List<String> dead = new ArrayList<>();
            for (ShortcutInfo p : sm.getPinnedShortcuts()) {
                String sid = p.getId();
                if (!sid.startsWith("lnk_")) continue;
                Item it = find(items, sid.substring(4));
                if (it == null) {
                    if (p.isEnabled()) dead.add(sid);
                } else {
                    upd.add(build(c, it));
                }
            }
            if (!upd.isEmpty()) sm.updateShortcuts(upd);
            if (!dead.isEmpty()) sm.disableShortcuts(dead, "הקיצור נמחק מהאפליקציה");
            syncDynamic(c, sm, items);
        } catch (Exception ignored) {
        }
        notifyDocs(c);
    }

    static void notifyDocs(Context c) {
        try {
            c.getContentResolver().notifyChange(
                    android.provider.DocumentsContract.buildChildDocumentsUri(ShortcutDocsProvider.AUTH, "root"), null);
        } catch (Exception ignored) {
        }
    }

    /** הקיצורים האחרונים בשימוש יופיעו בלחיצה ארוכה על אייקון האפליקציה. */
    static void syncDynamic(Context c, ShortcutManager sm, List<Item> items) {
        try {
            List<Item> sorted = new ArrayList<>(items);
            Collections.sort(sorted, new Comparator<Item>() {
                @Override
                public int compare(Item a, Item b) {
                    return Long.compare(Math.max(b.lastUsed, b.created), Math.max(a.lastUsed, a.created));
                }
            });
            int max = Math.min(4, sm.getMaxShortcutCountPerActivity());
            List<ShortcutInfo> dyn = new ArrayList<>();
            for (int i = 0; i < sorted.size() && i < max; i++) dyn.add(build(c, sorted.get(i)));
            sm.setDynamicShortcuts(dyn);
        } catch (Exception ignored) {
        }
    }

    static void syncDynamic(Context c) {
        try {
            ShortcutManager sm = c.getSystemService(ShortcutManager.class);
            if (sm != null) syncDynamic(c, sm, Store.all(c));
        } catch (Exception ignored) {
        }
    }

    static Typeface bold() {
        return Typeface.DEFAULT_BOLD;
    }
}

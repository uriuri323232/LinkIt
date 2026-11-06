package com.lnk.shortcuts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** פתיחת יעדים ויצירת קיצורי דרך למסך הבית. */
final class Opener {

    static final String ACTION_OPEN = "com.lnk.shortcuts.OPEN";

    private Opener() {
    }

    /** Intent שמופעל מקיצור הדרך (עובר דרך LaunchActivity). */
    static Intent launchIntent(Context c, String type, String data) {
        Intent i = new Intent(c, LaunchActivity.class);
        i.setAction(ACTION_OPEN);
        i.putExtra("type", type);
        i.putExtra("data", data);
        return i;
    }

    /** ה-Intent האמיתי של היעד, או null אם לא נמצא. */
    /** פתיחה בתוך LinkIt (תיקיות נפתחות במנהל הקבצים של LinkIt). */
    static Intent target(Context c, String type, String data) {
        return target(c, type, data, false);
    }

    /** מזהה מסמך של ספק האחסון המובנה (primary:Download וכו') או null. */
    static String docId(File f) {
        String p = f.getAbsolutePath();
        String prim = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (p.equals(prim) || p.startsWith(prim + "/")) {
            return "primary:" + p.substring(prim.length()).replaceFirst("^/", "");
        }
        if (p.startsWith("/storage/")) {
            String rest = p.substring(9);
            int i = rest.indexOf('/');
            String vol = i < 0 ? rest : rest.substring(0, i);
            String rel = i < 0 ? "" : rest.substring(i + 1);
            if (vol.equals("emulated") || vol.equals("self") || vol.isEmpty()) return null;
            return vol + ":" + rel;
        }
        return null;
    }

    /** Intent שפותח תיקייה במנהל הקבצים שבו המשתמש משתמש (ברירת המחדל של המערכת). */
    static Intent externalFolderIntent(File dir) {
        String id = docId(dir);
        if (id == null) return null;
        Uri u = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", id);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, DocumentsContract.Document.MIME_TYPE_DIR);
        return i;
    }

    /**
     * @param external true = תיקיות נפתחות במנהל הקבצים החיצוני של המשתמש, false = בתוך LinkIt.
     */
    static Intent target(Context c, String type, String data, boolean external) {
        if (type == null || data == null) return null;
        switch (type) {
            case "url":
                return new Intent(Intent.ACTION_VIEW, Uri.parse(data));
            case "path": {
                File f = new File(data);
                if (!f.exists()) return null;
                if (ShortcutFile.is(f)) {
                    String[] t = ShortcutFile.read(f);
                    if (t == null || "path".equals(t[0]) && ShortcutFile.is(new File(t[1]))) return null;
                    return target(c, t[0], t[1], external);
                }
                if (f.isDirectory()) {
                    if (external) {
                        Intent ext = externalFolderIntent(f);
                        if (ext != null) return ext;
                    }
                    return new Intent(c, MainActivity.class)
                            .setAction(MainActivity.ACTION_BROWSE)
                            .putExtra("path", f.getAbsolutePath());
                }
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(FileUtils.uri(c, f), FileUtils.mime(f));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                return i;
            }
            case "file": { // תאימות לקיצורים ישנים (SAF)
                Uri uri = Uri.parse(data);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, c.getContentResolver().getType(uri));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                return i;
            }
            case "dir": { // תאימות לקיצורים ישנים (SAF)
                Uri tree = Uri.parse(data);
                Uri doc = DocumentsContract.buildDocumentUriUsingTree(
                        tree, DocumentsContract.getTreeDocumentId(tree));
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(doc, DocumentsContract.Document.MIME_TYPE_DIR);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                return i;
            }
            case "app":
                return c.getPackageManager().getLaunchIntentForPackage(data);
            default:
                return null;
        }
    }

    static void pin(Context c, String type, String data, String name, Icon icon) {
        ShortcutManager sm = c.getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            Toast.makeText(c, "המשגר (Launcher) שלך לא תומך ביצירת קיצורי דרך", Toast.LENGTH_LONG).show();
            return;
        }
        ShortcutInfo info = new ShortcutInfo.Builder(c, type + "_" + data.hashCode())
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(icon)
                .setIntent(launchIntent(c, type, data))
                .build();
        sm.requestPinShortcut(info, null);
    }

    static void pinPath(Context c, File f) {
        pin(c, "path", f.getAbsolutePath(), f.getName().isEmpty() ? "/" : f.getName(), fileIcon(f));
    }

    static Icon appIcon(Context c) {
        return Icon.createWithResource(c, R.drawable.ic_launcher);
    }

    static Bitmap toBitmap(Drawable d, int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        d.setBounds(0, 0, size, size);
        d.draw(canvas);
        return bmp;
    }

    /** אייקון לקובץ/תיקייה: תמונה ממוזערת לתמונות, אחרת אימוג'י על רקע צבעוני. */
    static Icon fileIcon(File f) {
        int s = 192;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        boolean thumb = false;
        if (f.isFile() && FileUtils.mime(f).startsWith("image/")) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), o);
                int sample = 1;
                while (o.outWidth / sample > s * 2 && o.outHeight / sample > s * 2) sample *= 2;
                o.inJustDecodeBounds = false;
                o.inSampleSize = sample;
                Bitmap t = BitmapFactory.decodeFile(f.getPath(), o);
                if (t != null) {
                    int side = Math.min(t.getWidth(), t.getHeight());
                    Rect src = new Rect((t.getWidth() - side) / 2, (t.getHeight() - side) / 2,
                            (t.getWidth() + side) / 2, (t.getHeight() + side) / 2);
                    cv.drawBitmap(t, src, new Rect(0, 0, s, s), p);
                    thumb = true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (!thumb) {
            p.setColor(f.isDirectory() ? 0xFFF9A825 : 0xFF1565C0);
            cv.drawRoundRect(0, 0, s, s, 40, 40, p);
            p.setTextSize(110);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFFFFFFF);
            Paint.FontMetrics fm = p.getFontMetrics();
            cv.drawText(FileUtils.icon(f), s / 2f, s / 2f - (fm.ascent + fm.descent) / 2f, p);
        }
        return Icon.createWithBitmap(b);
    }

    /** קיצורי דרך דינמיים (לחיצה ארוכה על אייקון האפליקציה) מתוך המועדפים. */
    static void refreshDynamic(Context c) {
        try {
            ShortcutManager sm = c.getSystemService(ShortcutManager.class);
            if (sm == null) return;
            List<Favorites.Item> favs = Favorites.load(c);
            int max = Math.min(4, sm.getMaxShortcutCountPerActivity());
            List<ShortcutInfo> list = new ArrayList<>();
            for (int i = 0; i < favs.size() && list.size() < max; i++) {
                Favorites.Item it = favs.get(i);
                Icon icon;
                if ("path".equals(it.type)) {
                    icon = fileIcon(new File(it.data));
                } else if ("app".equals(it.type)) {
                    try {
                        icon = Icon.createWithBitmap(toBitmap(
                                c.getPackageManager().getApplicationIcon(it.data), 192));
                    } catch (Exception e) {
                        icon = appIcon(c);
                    }
                } else {
                    icon = appIcon(c);
                }
                list.add(new ShortcutInfo.Builder(c, "fav_" + i)
                        .setShortLabel(it.name)
                        .setLongLabel(it.name)
                        .setIcon(icon)
                        .setIntent(launchIntent(c, it.type, it.data))
                        .build());
            }
            sm.setDynamicShortcuts(list);
        } catch (Exception ignored) {
        }
    }
}

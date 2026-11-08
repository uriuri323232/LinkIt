package com.lnk.shortcuts;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** כלי קבצים: נתיבים, פתיחה במנהלי קבצים, העתקה ועוד. */
final class FileOps {
    private FileOps() {}

    static final String EXT_AUTH = "com.android.externalstorage.documents";
    static final String DIR_MIME = "vnd.android.document/directory";

    /** מנהלי קבצים מוכרים (נבדקים אם הם מותקנים). */
    static final String[] KNOWN_FM = {
            "com.google.android.apps.nbu.files", "com.android.documentsui",
            "com.google.android.documentsui", "com.sec.android.app.myfiles",
            "com.mi.android.globalFileexplorer", "com.android.fileexplorer",
            "com.alphainventor.filemanager", "com.amaze.filemanager", "nextapp.fx",
            "com.lonelycatgames.Xplore", "com.ghisler.android.TotalCommander",
            "com.estrongs.android.pop", "pl.solidexplorer2", "com.mixplorer",
            "com.mixplorer.silver", "com.cxinventor.file.explorer", "com.speedsoftware.explorer",
            "org.openintents.filemanager", "me.zhanghai.android.files", "com.asus.filemanager",
            "com.huawei.hidisk", "com.coloros.filemanager", "com.oneplus.filemanager",
            "com.vivo.filemanager", "com.lenovo.FileBrowser2", "com.rhmsoft.fm"};

    static final class AppInfo {
        String label, pkg;
        Drawable icon;
    }

    // --------------------------------------------------------------- נתיבים

    static String root() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    static String normalize(String p) {
        if (p == null) return null;
        if (p.equals("/sdcard") || p.startsWith("/sdcard/")) return root() + p.substring(7);
        if (p.equals("/storage/self/primary") || p.startsWith("/storage/self/primary/")) {
            return root() + p.substring("/storage/self/primary".length());
        }
        return p;
    }

    private static String pathFromDocId(String id) {
        int c = id.indexOf(':');
        if (c < 0) return null;
        String vol = id.substring(0, c);
        String rel = id.substring(c + 1);
        String base;
        if (vol.equals("primary")) base = root();
        else if (vol.equals("home")) base = root() + "/Documents";
        else base = "/storage/" + vol;
        return rel.isEmpty() ? base : base + "/" + rel;
    }

    /** מנסה להפיק נתיב אמיתי מכתובת (file:// / מסמך של האחסון / FileProvider). */
    static String pathFromUri(Uri u) {
        if (u == null) return null;
        String sch = u.getScheme();
        if ("file".equals(sch)) return normalize(u.getPath());
        if (!"content".equals(sch)) return null;
        List<String> seg = u.getPathSegments();
        if (EXT_AUTH.equals(u.getAuthority())) {
            String docId = null, treeId = null;
            for (int i = 0; i + 1 < seg.size(); i++) {
                if ("document".equals(seg.get(i))) docId = seg.get(i + 1);
                if ("tree".equals(seg.get(i))) treeId = seg.get(i + 1);
            }
            String id = docId != null ? docId : treeId;
            return id == null ? null : pathFromDocId(id);
        }
        String p = u.getPath();
        if (p == null) return null;
        String cand = null;
        int i = p.indexOf("/storage/");
        if (i >= 0) cand = p.substring(i);
        if (cand == null) {
            i = p.indexOf("/sdcard/");
            if (i >= 0) cand = root() + p.substring(i + 7);
        }
        if (cand == null) {
            i = p.indexOf("/emulated/0/");
            if (i >= 0) cand = "/storage" + p.substring(i);
        }
        if (cand != null) {
            cand = normalize(cand);
            if (new File(cand).exists()) return cand;
        }
        return null;
    }

    /** d1 של קיצור: נתיב ישיר, או כתובת SAF שניתן להמיר לנתיב. */
    static String pathOf(String d1) {
        if (d1 == null || d1.isEmpty()) return null;
        if (d1.startsWith("/")) return d1;
        try {
            return pathFromUri(Uri.parse(d1));
        } catch (Exception e) {
            return null;
        }
    }

    static Uri docUriForPath(String path) {
        path = normalize(path);
        if (path == null) return null;
        String r = root();
        String docId;
        if (path.equals(r) || path.startsWith(r + "/")) {
            docId = "primary:" + (path.length() > r.length() ? path.substring(r.length() + 1) : "");
        } else if (path.startsWith("/storage/")) {
            String rest = path.substring(9);
            int i = rest.indexOf('/');
            String vol = i < 0 ? rest : rest.substring(0, i);
            String rel = i < 0 ? "" : rest.substring(i + 1);
            docId = vol + ":" + rel;
        } else {
            return null;
        }
        return DocumentsContract.buildDocumentUri(EXT_AUTH, docId);
    }

    static Uri treeUriForPath(String path) {
        Uri d = docUriForPath(path);
        if (d == null) return null;
        return DocumentsContract.buildTreeDocumentUri(EXT_AUTH, DocumentsContract.getDocumentId(d));
    }

    static Uri docUriFromSaf(Uri saf) {
        try {
            if (DocumentsContract.isTreeUri(saf)) {
                return DocumentsContract.buildDocumentUriUsingTree(saf,
                        DocumentsContract.getTreeDocumentId(saf));
            }
        } catch (Exception ignored) {
        }
        return saf;
    }

    static List<File> volumes(Context c) {
        List<File> out = new ArrayList<>();
        File prim = Environment.getExternalStorageDirectory();
        out.add(prim);
        File[] dirs = c.getExternalFilesDirs(null);
        if (dirs != null) {
            for (File d : dirs) {
                if (d == null) continue;
                String p = d.getAbsolutePath();
                int i = p.indexOf("/Android/");
                if (i > 0) {
                    File r = new File(p.substring(0, i));
                    if (!r.getAbsolutePath().equals(prim.getAbsolutePath()) && !out.contains(r)) out.add(r);
                }
            }
        }
        return out;
    }

    static boolean isVolumeRoot(Context c, File f) {
        for (File v : volumes(c)) if (v.getAbsolutePath().equals(f.getAbsolutePath())) return true;
        return false;
    }

    static String displayName(Context c, File f) {
        if (f.getAbsolutePath().equals(root())) return "אחסון פנימי";
        if (isVolumeRoot(c, f)) return "כרטיס אחסון " + f.getName();
        String n = f.getName();
        return n.isEmpty() ? f.getAbsolutePath() : n;
    }

    // ---------------------------------------------------------------- סוגים

    static String mime(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        int i = n.lastIndexOf('.');
        if (i >= 0) {
            String ext = n.substring(i + 1);
            if (ext.equals("linkit")) return "application/x-linkit-shortcut";
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (m != null) return m;
        }
        return "*/*";
    }

    static String emoji(File f) {
        if (f.isDirectory()) return "📁";
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".linkit")) return "🔗";
        String m = mime(f);
        if (m.startsWith("image/")) return "🖼️";
        if (m.startsWith("video/")) return "🎞️";
        if (m.startsWith("audio/")) return "🎵";
        if (m.equals("application/pdf")) return "📕";
        if (m.equals("application/vnd.android.package-archive")) return "📦";
        if (n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".7z") || n.endsWith(".tar")
                || n.endsWith(".gz")) return "🗜️";
        return "📄";
    }

    static String human(long b) {
        if (b < 1024) return b + " B";
        double v = b / 1024.0;
        if (v < 1024) return String.format(Locale.US, "%.0f KB", v);
        v /= 1024;
        if (v < 1024) return String.format(Locale.US, "%.1f MB", v);
        v /= 1024;
        return String.format(Locale.US, "%.2f GB", v);
    }

    // -------------------------------------------------------------- פתיחה

    private static Intent view(Uri u, String type) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        if (type != null) i.setDataAndType(u, type);
        else i.setData(u);
        return i;
    }

    /**
     * פותח תיקייה במנהל קבצים. אם pkg לא ריק - רק באפליקציה הזו (בלי "קפיצה" למנהל אחר).
     * מנסה כמה שיטות פתיחה שונות, כי כל מנהל קבצים תומך בשיטה אחרת.
     */
    static boolean openFolderIn(Activity a, String path, Uri safDoc, String pkg) {
        PackageManager pm = a.getPackageManager();
        List<Intent> tries = new ArrayList<>();
        Uri doc = path != null ? docUriForPath(path) : safDoc;
        Uri tree = path != null ? treeUriForPath(path) : null;
        Uri file = path != null ? Uri.fromFile(new File(path)) : null;

        if ("com.sec.android.app.myfiles".equals(pkg) && path != null) {
            Intent s = new Intent("samsung.myfiles.intent.action.LAUNCH_MY_FILES");
            s.putExtra("samsung.myfiles.intent.extra.START_PATH", path);
            tries.add(s);
        }
        if (doc != null) {
            tries.add(view(doc, DIR_MIME));
            Intent b = new Intent("android.provider.action.BROWSE");
            b.setDataAndType(doc, DIR_MIME);
            tries.add(b);
        }
        if (file != null) {
            tries.add(view(file, "resource/folder"));
            tries.add(view(file, "inode/directory"));
            tries.add(view(file, null));
        }
        if (tree != null) tries.add(view(tree, DIR_MIME));

        for (Intent i : tries) {
            if (pkg != null && !pkg.isEmpty()) i.setPackage(pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) == null
                    && pm.resolveActivity(i, 0) == null) continue;
            try {
                a.startActivity(i);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /** פותח קובץ מנתיב. pkg: "" = ברירת מחדל, "*ask" = חלון בחירה, אחרת חבילה ספציפית. */
    static void openFile(Activity a, File f, String pkg) {
        if (!f.exists()) {
            Ui.toast(a, "הקובץ לא קיים");
            return;
        }
        Uri u = FileShareProvider.uriFor(f);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, mime(f));
        i.setClipData(ClipData.newRawUri("", u));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        if ("*ask".equals(pkg)) {
            Intent c = Intent.createChooser(i, f.getName());
            c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(c);
            return;
        }
        boolean specific = pkg != null && !pkg.isEmpty();
        if (specific) i.setPackage(pkg);
        try {
            a.startActivity(i);
        } catch (ActivityNotFoundException e) {
            if (specific) {
                i.setPackage(null);
                try {
                    a.startActivity(i);
                    Ui.toast(a, "האפליקציה שנבחרה לא יכולה לפתוח את הקובץ, נפתח באפליקציה אחרת");
                    return;
                } catch (ActivityNotFoundException ignored) {
                }
            }
            Ui.toast(a, "אין אפליקציה שיכולה לפתוח את הקובץ הזה");
        }
    }

    static void shareFile(Activity a, File f) {
        Uri u = FileShareProvider.uriFor(f);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime(f));
        i.putExtra(Intent.EXTRA_STREAM, u);
        i.setClipData(ClipData.newRawUri("", u));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        a.startActivity(Intent.createChooser(i, f.getName()));
    }

    // ----------------------------------------------------- מנהלי קבצים מותקנים

    static List<AppInfo> fileManagers(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, AppInfo> map = new LinkedHashMap<>();
        Intent[] probes = {
                view(Uri.parse("content://" + EXT_AUTH + "/document/primary%3A"), DIR_MIME),
                view(Uri.fromFile(new File("/storage/emulated/0")), "resource/folder"),
                view(Uri.fromFile(new File("/storage/emulated/0")), "inode/directory")};
        for (Intent p : probes) {
            for (ResolveInfo ri : pm.queryIntentActivities(p, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || map.containsKey(pkg)) continue;
                AppInfo ai = new AppInfo();
                ai.pkg = pkg;
                ai.label = ri.loadLabel(pm).toString();
                ai.icon = ri.loadIcon(pm);
                map.put(pkg, ai);
            }
        }
        for (String k : KNOWN_FM) {
            if (map.containsKey(k)) continue;
            try {
                if (pm.getLaunchIntentForPackage(k) == null) continue;
                AppInfo ai = new AppInfo();
                ai.pkg = k;
                ai.label = pm.getApplicationLabel(pm.getApplicationInfo(k, 0)).toString();
                ai.icon = pm.getApplicationIcon(k);
                map.put(k, ai);
            } catch (Exception ignored) {
            }
        }
        List<AppInfo> out = new ArrayList<>(map.values());
        Collections.sort(out, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    // ----------------------------------------------------------- פעולות קבצים

    static File uniqueDest(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name, ext = "";
        int i = name.lastIndexOf('.');
        if (i > 0) {
            base = name.substring(0, i);
            ext = name.substring(i);
        }
        for (int n = 2; ; n++) {
            File c = new File(dir, base + " (" + n + ")" + ext);
            if (!c.exists()) return c;
        }
    }

    static void copyRecursive(File s, File d) throws IOException {
        if (s.isDirectory()) {
            if (!d.exists() && !d.mkdirs()) throw new IOException("mkdir");
            File[] kids = s.listFiles();
            if (kids != null) for (File k : kids) copyRecursive(k, new File(d, k.getName()));
        } else {
            try (InputStream in = new FileInputStream(s); OutputStream out = new FileOutputStream(d)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
    }

    static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        return f.delete();
    }
}

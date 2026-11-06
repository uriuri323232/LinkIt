package com.lnk.shortcuts;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** פעולות עזר על קבצים. */
final class FileUtils {

    private FileUtils() {
    }

    static boolean hasAccess(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return c.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** מנסה לזהות נתיב אמיתי מתוך Uri שמנהל קבצים אחר שיתף. null אם לא הצליח. */
    static File fromUri(Context c, Uri u) {
        try {
            if ("file".equals(u.getScheme())) {
                File f = new File(u.getPath());
                return f.exists() ? f : null;
            }
            if (!"content".equals(u.getScheme())) return null;
            if ("com.android.externalstorage.documents".equals(u.getAuthority())) {
                String id = DocumentsContract.getDocumentId(u);
                int i = id.indexOf(':');
                if (i > 0) {
                    String vol = id.substring(0, i);
                    File base = "primary".equalsIgnoreCase(vol)
                            ? Environment.getExternalStorageDirectory() : new File("/storage/" + vol);
                    File f = new File(base, id.substring(i + 1));
                    if (f.exists()) return f;
                }
            }
            try (Cursor cur = c.getContentResolver().query(u, new String[]{"_data"}, null, null, null)) {
                if (cur != null && cur.moveToFirst()) {
                    String p = cur.getString(0);
                    if (p != null && new File(p).exists()) return new File(p);
                }
            } catch (Exception ignored) {
            }
            String path = Uri.decode(u.getPath() == null ? "" : u.getPath());
            for (String m : new String[]{"/storage/", "/sdcard/", "/mnt/sdcard/"}) {
                int i = path.indexOf(m);
                if (i >= 0) {
                    File f = new File(path.substring(i));
                    if (f.exists()) return f;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static String formatSize(long b) {
        if (b < 1024) return b + " B";
        String[] u = {"KB", "MB", "GB", "TB"};
        double v = b;
        int i = -1;
        do {
            v /= 1024;
            i++;
        } while (v >= 1024 && i < u.length - 1);
        return String.format(Locale.US, v >= 100 ? "%.0f %s" : "%.1f %s", v, u[i]);
    }

    static String formatDate(long t) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(t));
    }

    static String ext(String name) {
        int i = name.lastIndexOf('.');
        return (i > 0 && i < name.length() - 1) ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }

    static String baseName(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    static String mime(File f) {
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext(f.getName()));
        return m != null ? m : "*/*";
    }

    static Uri uri(Context c, File f) {
        return FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", f);
    }

    static String icon(File f) {
        if (f.isDirectory()) return "\uD83D\uDCC1";
        String m = mime(f);
        String e = ext(f.getName());
        if (m.startsWith("image/")) return "\uD83D\uDDBC";
        if (m.startsWith("video/")) return "\uD83C\uDFAC";
        if (m.startsWith("audio/")) return "\uD83C\uDFB5";
        if (m.equals("application/pdf")) return "\uD83D\uDCD5";
        if (e.equals("apk")) return "\uD83E\uDD16";
        if (e.equals("zip") || e.equals("rar") || e.equals("7z") || e.equals("tar") || e.equals("gz"))
            return "\uD83D\uDDDC";
        return "\uD83D\uDCC4";
    }

    /** שם פנוי ביעד: name (1).ext */
    static File uniqueDest(File dir, String name, boolean isDir) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = isDir ? name : baseName(name);
        String ext = isDir ? "" : name.substring(base.length());
        for (int n = 1; ; n++) {
            f = new File(dir, base + " (" + n + ")" + ext);
            if (!f.exists()) return f;
        }
    }

    static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    static void copy(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.mkdirs() && !dst.isDirectory()) throw new IOException("לא ניתן ליצור " + dst.getName());
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) copy(k, new File(dst, k.getName()));
        } else {
            try (InputStream in = new BufferedInputStream(new FileInputStream(src));
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(dst))) {
                pipe(in, out);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        dst.setLastModified(src.lastModified());
    }

    static boolean deleteRecursive(File f) {
        if (f.isDirectory() && !java.nio.file.Files.isSymbolicLink(f.toPath())) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        return f.delete();
    }

    /** {גודל, קבצים, תיקיות} */
    static long[] stats(File f) {
        long[] r = new long[3];
        walk(f, r);
        return r;
    }

    private static void walk(File f, long[] r) {
        if (f.isDirectory()) {
            r[2]++;
            File[] kids = f.listFiles();
            if (kids != null && !java.nio.file.Files.isSymbolicLink(f.toPath()))
                for (File k : kids) walk(k, r);
        } else {
            r[1]++;
            r[0] += f.length();
        }
    }

    static boolean isInside(File child, File parent) {
        try {
            String c = child.getCanonicalPath();
            String p = parent.getCanonicalPath();
            return c.equals(p) || c.startsWith(p + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    static void zip(List<File> files, File out) throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            for (File f : files) addZip(z, f, f.getName(), out);
        }
    }

    private static void addZip(ZipOutputStream z, File f, String path, File out) throws IOException {
        if (f.equals(out)) return;
        if (f.isDirectory()) {
            z.putNextEntry(new ZipEntry(path + "/"));
            z.closeEntry();
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) addZip(z, k, path + "/" + k.getName(), out);
        } else {
            z.putNextEntry(new ZipEntry(path));
            try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
                pipe(in, z);
            }
            z.closeEntry();
        }
    }

    static int unzip(File zip, File dest) throws IOException {
        if (!dest.mkdirs() && !dest.isDirectory()) throw new IOException("לא ניתן ליצור תיקיית יעד");
        String base = dest.getCanonicalPath() + File.separator;
        int count = 0;
        try (ZipInputStream zi = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e;
            while ((e = zi.getNextEntry()) != null) {
                File o = new File(dest, e.getName());
                if (!o.getCanonicalPath().startsWith(base)) continue; // הגנה מפני zip-slip
                if (e.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    o.mkdirs();
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    o.getParentFile().mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(o))) {
                        pipe(zi, os);
                    }
                    count++;
                }
            }
        }
        return count;
    }

    /** שורשי אחסון: זיכרון פנימי + כרטיסי SD. */
    static List<File> storageRoots(Context c) {
        List<File> roots = new ArrayList<>();
        File internal = Environment.getExternalStorageDirectory();
        roots.add(internal);
        File[] dirs = c.getExternalFilesDirs(null);
        if (dirs != null) {
            for (File d : dirs) {
                if (d == null) continue;
                String p = d.getAbsolutePath();
                int i = p.indexOf("/Android/");
                if (i > 0) {
                    File root = new File(p.substring(0, i));
                    if (!root.equals(internal) && root.exists() && !roots.contains(root)) roots.add(root);
                }
            }
        }
        return roots;
    }
}

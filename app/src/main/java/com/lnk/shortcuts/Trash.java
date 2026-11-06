package com.lnk.shortcuts;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** סל מחזור פנימי: מחיקה בטוחה עם אפשרות שחזור (נמחק אוטומטית אחרי 30 יום). */
final class Trash {

    static final long KEEP_MS = 30L * 24 * 3600 * 1000;

    static final class Entry {
        String file, orig;
        long time;
    }

    private Trash() {
    }

    static File dir() {
        File d = new File(Environment.getExternalStorageDirectory(), ".LinkItTrash");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static boolean isInTrash(File f) {
        return FileUtils.isInside(f, dir());
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("trash", Context.MODE_PRIVATE);
    }

    static synchronized List<Entry> list(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.file = o.getString("f");
                e.orig = o.getString("o");
                e.time = o.getLong("t");
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void save(Context c, List<Entry> l) {
        try {
            JSONArray a = new JSONArray();
            for (Entry e : l) a.put(new JSONObject().put("f", e.file).put("o", e.orig).put("t", e.time));
            prefs(c).edit().putString("list", a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** מעביר פריט לסל. מחזיר false אם נכשל. */
    static synchronized boolean put(Context c, File f) {
        try {
            File t = FileUtils.uniqueDest(dir(), f.getName(), f.isDirectory());
            if (!f.renameTo(t)) {
                FileUtils.copy(f, t);
                if (!FileUtils.deleteRecursive(f)) return false;
            }
            List<Entry> l = list(c);
            Entry e = new Entry();
            e.file = t.getName();
            e.orig = f.getAbsolutePath();
            e.time = System.currentTimeMillis();
            l.add(e);
            save(c, l);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** משחזר למקום המקורי (או לשם חדש אם תפוס). מחזיר את הקובץ המשוחזר או null. */
    static synchronized File restore(Context c, Entry e) {
        File src = new File(dir(), e.file);
        List<Entry> l = list(c);
        try {
            File origin = new File(e.orig);
            File parent = origin.getParentFile();
            if (parent == null) return null;
            if (!parent.exists()) parent.mkdirs();
            File t = FileUtils.uniqueDest(parent, origin.getName(), src.isDirectory());
            if (!src.renameTo(t)) {
                FileUtils.copy(src, t);
                FileUtils.deleteRecursive(src);
            }
            for (int i = 0; i < l.size(); i++) if (l.get(i).file.equals(e.file)) { l.remove(i); break; }
            save(c, l);
            return t;
        } catch (Exception ex) {
            return null;
        }
    }

    static synchronized void remove(Context c, Entry e) {
        FileUtils.deleteRecursive(new File(dir(), e.file));
        List<Entry> l = list(c);
        for (int i = 0; i < l.size(); i++) if (l.get(i).file.equals(e.file)) { l.remove(i); break; }
        save(c, l);
    }

    static synchronized void empty(Context c) {
        for (Entry e : list(c)) FileUtils.deleteRecursive(new File(dir(), e.file));
        save(c, new ArrayList<>());
    }

    /** מנקה פריטים ישנים (מעל 30 יום) ורשומות יתומות. */
    static synchronized void purge(Context c) {
        List<Entry> l = list(c);
        long now = System.currentTimeMillis();
        List<Entry> keep = new ArrayList<>();
        for (Entry e : l) {
            File f = new File(dir(), e.file);
            if (!f.exists()) continue;
            if (now - e.time > KEEP_MS) FileUtils.deleteRecursive(f);
            else keep.add(e);
        }
        if (keep.size() != l.size()) save(c, keep);
    }
}

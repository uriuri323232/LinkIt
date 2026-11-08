package com.lnk.shortcuts;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/** שמירת הקיצורים בזיכרון האפליקציה (ללא רשת). */
final class Store {
    private Store() {}

    private static final String KEY = "items";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("linkit", Context.MODE_PRIVATE);
    }

    static synchronized List<Item> all(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Item.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    static synchronized void saveAll(Context c, List<Item> list) {
        JSONArray a = new JSONArray();
        for (Item it : list) a.put(it.toJson());
        sp(c).edit().putString(KEY, a.toString()).apply();
    }

    static Item get(Context c, String id) {
        if (id == null) return null;
        for (Item it : all(c)) if (it.id.equals(id)) return it;
        return null;
    }

    static synchronized void put(Context c, Item item) {
        List<Item> list = all(c);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(item.id)) {
                list.set(i, item);
                found = true;
                break;
            }
        }
        if (!found) list.add(item);
        saveAll(c, list);
    }

    static synchronized void delete(Context c, String id) {
        List<Item> list = all(c);
        List<Item> keep = new ArrayList<>();
        for (Item it : list) {
            if (it.id.equals(id)) continue;
            if ("group".equals(it.type)) {
                StringBuilder sb = new StringBuilder();
                for (String p : it.d1.split(",")) {
                    if (p.isEmpty() || p.equals(id)) continue;
                    if (sb.length() > 0) sb.append(',');
                    sb.append(p);
                }
                it.d1 = sb.toString();
            }
            keep.add(it);
        }
        saveAll(c, keep);
        IconMaker.imageFile(c, id).delete();
    }

    static synchronized void touch(Context c, String id) {
        List<Item> list = all(c);
        for (Item it : list) {
            if (it.id.equals(id)) {
                it.uses++;
                it.lastUsed = System.currentTimeMillis();
                saveAll(c, list);
                return;
            }
        }
    }

    static Item duplicate(Context c, Item src) {
        Item n = Item.fromJson(src.toJson());
        n.id = Item.newId();
        n.name = src.name + " (עותק)";
        n.uses = 0;
        n.lastUsed = 0;
        n.created = System.currentTimeMillis();
        File f = IconMaker.imageFile(c, src.id);
        if (f.exists()) {
            try {
                byte[] data = readFile(f);
                writeFile(IconMaker.imageFile(c, n.id), data);
            } catch (Exception e) {
                n.iconKind = "auto";
            }
        }
        put(c, n);
        return n;
    }

    // ---- מיון ----
    static String sort(Context c) {
        return sp(c).getString("sort", "recent");
    }

    static void setSort(Context c, String s) {
        sp(c).edit().putString("sort", s).apply();
    }

    // ---- גיבוי ----
    static String exportJson(Context c) throws Exception {
        JSONArray arr = new JSONArray();
        for (Item it : all(c)) {
            JSONObject o = it.toJson();
            File f = IconMaker.imageFile(c, it.id);
            if (f.exists()) o.put("img", Base64.encodeToString(readFile(f), Base64.NO_WRAP));
            arr.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("app", "LinkIt");
        root.put("v", 1);
        root.put("items", arr);
        return root.toString(1);
    }

    static synchronized int importJson(Context c, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.getJSONArray("items");
        int n = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Item it = Item.fromJson(o);
            String img = o.optString("img", "");
            if (!img.isEmpty()) {
                try {
                    writeFile(IconMaker.imageFile(c, it.id), Base64.decode(img, Base64.DEFAULT));
                } catch (Exception e) {
                    it.iconKind = "auto";
                }
            }
            put(c, it);
            n++;
        }
        return n;
    }

    static byte[] readFile(File f) throws Exception {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            byte[] b = new byte[(int) r.length()];
            r.readFully(b);
            return b;
        }
    }

    static void writeFile(File f, byte[] data) throws Exception {
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(data);
        }
    }
}

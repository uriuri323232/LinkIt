package com.lnk.shortcuts;

import org.json.JSONObject;

/** פורמט קובץ הקיצור (.linkit) שנפתח מכל מנהל קבצים. */
final class LinkitFile {
    private LinkitFile() {}

    static final String MIME = "application/x-linkit-shortcut";

    static String write(Item it) {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "LinkIt");
            root.put("v", 1);
            root.put("item", it.toJson());
            return root.toString(1);
        } catch (Exception e) {
            return "{}";
        }
    }

    static Item parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject io = o.optJSONObject("item");
            if (io == null) return null;
            Item it = Item.fromJson(io);
            if (it.type.isEmpty()) return null;
            return it;
        } catch (Exception e) {
            return null;
        }
    }

    /** שם היעד עצמו (שם התיקייה / הקובץ / האפליקציה), בלי תוספות כמו "קיצור דרך". */
    static String targetName(Item it) {
        String n = null;
        if ("file".equals(it.type) || "folder".equals(it.type)) {
            if (it.d1.startsWith("/")) {
                if (it.d1.equals(FileOps.root())) n = "אחסון פנימי";
                else n = new java.io.File(it.d1).getName();
            } else {
                n = it.d2;
            }
        } else if ("app".equals(it.type)) {
            n = it.d2;
        }
        if (n == null || n.trim().isEmpty()) n = it.name;
        return n;
    }

    static String fileName(Item it) {
        String n = targetName(it).replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (n.isEmpty()) n = "shortcut";
        return n + ".linkit";
    }
}

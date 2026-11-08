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

    static String fileName(Item it) {
        String n = it.name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (n.isEmpty()) n = "shortcut";
        return n + ".linkit";
    }
}

package com.lnk.shortcuts;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** קיצור דרך שמור באפליקציה. */
final class Item {
    String id = newId();
    String name = "";
    String type = "url";
    /** שדות נתונים - המשמעות תלויה בסוג (ראה Types). */
    String d1 = "", d2 = "", d3 = "";
    /** auto = לפי סוג/אפליקציה, gen = עיצוב עצמי, image = תמונה. */
    String iconKind = "auto";
    String glyph = "";
    int color = 0xFF3B5BDB;
    long created = System.currentTimeMillis();
    long lastUsed = 0;
    int uses = 0;
    /** חץ קיצור על האייקון (כמו בווינדוס). */
    boolean arrow = false;

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("type", type);
            o.put("d1", d1);
            o.put("d2", d2);
            o.put("d3", d3);
            o.put("iconKind", iconKind);
            o.put("glyph", glyph);
            o.put("color", color);
            o.put("created", created);
            o.put("lastUsed", lastUsed);
            o.put("uses", uses);
            o.put("arrow", arrow);
        } catch (JSONException ignored) {
        }
        return o;
    }

    static Item fromJson(JSONObject o) {
        Item it = new Item();
        it.id = o.optString("id", it.id);
        it.name = o.optString("name", "");
        it.type = o.optString("type", "url");
        it.d1 = o.optString("d1", "");
        it.d2 = o.optString("d2", "");
        it.d3 = o.optString("d3", "");
        it.iconKind = o.optString("iconKind", "auto");
        it.glyph = o.optString("glyph", "");
        it.color = o.optInt("color", 0xFF3B5BDB);
        it.created = o.optLong("created", it.created);
        it.lastUsed = o.optLong("lastUsed", 0);
        it.uses = o.optInt("uses", 0);
        it.arrow = o.optBoolean("arrow", false);
        return it;
    }
}

package com.lnk.shortcuts;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** רשימת מועדפים / קיצורים פנימיים באפליקציה. */
final class Favorites {

    static final class Item {
        String name, type, data;

        Item(String name, String type, String data) {
            this.name = name;
            this.type = type;
            this.data = data;
        }
    }

    private Favorites() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("favorites", Context.MODE_PRIVATE);
    }

    static List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Item(o.getString("n"), o.getString("t"), o.getString("d")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void save(Context c, List<Item> items) {
        try {
            JSONArray a = new JSONArray();
            for (Item it : items) {
                a.put(new JSONObject().put("n", it.name).put("t", it.type).put("d", it.data));
            }
            prefs(c).edit().putString("list", a.toString()).apply();
        } catch (Exception ignored) {
        }
        Opener.refreshDynamic(c);
    }

    /** מחזיר false אם כבר קיים. */
    static boolean add(Context c, Item item) {
        List<Item> items = load(c);
        for (Item it : items) {
            if (it.type.equals(item.type) && it.data.equals(item.data)) return false;
        }
        items.add(item);
        save(c, items);
        return true;
    }

    static void remove(Context c, int index) {
        List<Item> items = load(c);
        if (index >= 0 && index < items.size()) {
            items.remove(index);
            save(c, items);
        }
    }
}

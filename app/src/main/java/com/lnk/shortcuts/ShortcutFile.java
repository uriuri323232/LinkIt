package com.lnk.shortcuts;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** קובץ קיצור דרך (.lnkit) – כמו קובץ lnk בווינדוס: אפשר להעתיק, להעביר ולמחוק אותו כמו כל קובץ. */
final class ShortcutFile {

    static final String EXT = "lnkit";

    private ShortcutFile() {
    }

    static boolean is(File f) {
        return EXT.equals(FileUtils.ext(f.getName())) && f.isFile();
    }

    /** מחזיר {סוג, יעד} או null אם הקובץ פגום. */
    static String[] read(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[8192];
            int n = in.read(b);
            if (n <= 0) return null;
            JSONObject o = new JSONObject(new String(b, 0, n, StandardCharsets.UTF_8));
            return new String[]{o.getString("t"), o.getString("d")};
        } catch (Exception e) {
            return null;
        }
    }

    static File create(File dir, String label, String type, String data) throws IOException {
        String safe = label.replace('/', '_').trim();
        if (safe.isEmpty()) safe = "קיצור";
        File out = FileUtils.uniqueDest(dir, safe + " - קיצור דרך." + EXT, false);
        JSONObject o = new JSONObject();
        try {
            o.put("v", 1).put("t", type).put("d", data);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        try (FileOutputStream os = new FileOutputStream(out)) {
            os.write(o.toString().getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }
}

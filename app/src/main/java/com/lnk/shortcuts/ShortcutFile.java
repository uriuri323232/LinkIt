package com.lnk.shortcuts;

import android.content.Context;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** קובץ קיצור דרך (.lnkit) – כמו קובץ lnk בווינדוס: אפשר להעתיק, להעביר ולמחוק אותו כמו כל קובץ. */
final class ShortcutFile {

    static final String EXT = "lnkit";

    private ShortcutFile() {
    }

    static boolean is(File f) {
        return EXT.equals(FileUtils.ext(f.getName())) && f.isFile();
    }

    private static String[] readStream(InputStream in) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[2048];
            int n;
            while ((n = in.read(b)) > 0 && bo.size() < 16384) bo.write(b, 0, n);
            JSONObject o = new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            return new String[]{o.getString("t"), o.getString("d")};
        } catch (Exception e) {
            return null;
        }
    }

    /** מחזיר {סוג, יעד} או null אם הקובץ פגום. */
    static String[] read(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            return readStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** קריאה מ-Uri (כשמנהל קבצים אחר פותח קובץ קיצור). */
    static String[] readUri(Context c, Uri u) {
        try (InputStream in = c.getContentResolver().openInputStream(u)) {
            return in == null ? null : readStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    static File create(File dir, String label, String type, String data) throws IOException {
        String safe = label.replace('/', '_').trim();
        if (safe.isEmpty()) safe = "קיצור";
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
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

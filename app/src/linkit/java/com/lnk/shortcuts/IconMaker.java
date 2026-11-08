package com.lnk.shortcuts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;

import java.io.FileOutputStream;
import java.io.InputStream;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** יצירת אייקונים לקיצורים: אוטומטי, מעוצב או תמונה. */
final class IconMaker {
    private IconMaker() {}

    /** 108dp * 4 - גודל אייקון אדפטיבי. האזור הנראה הוא 288 במרכז. */
    static final int S = 432;
    private static final int VIS_START = 72, VIS_END = 360;

    private static final Map<String, Bitmap> cache = new HashMap<>();

    static void clearCache() {
        cache.clear();
    }

    static File imageFile(Context c, String id) {
        return new File(new File(c.getFilesDir(), "icons"), id + ".png");
    }


    /** שומר תמונה (מרובעת, 384px) כאייקון של פריט. מחזיר true בהצלחה. */
    static boolean saveImageIcon(Context c, String id, Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int s = 1;
            while (o.outWidth > 0 && (o.outWidth / s > 1024 || o.outHeight / s > 1024)) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            Bitmap bm;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                bm = BitmapFactory.decodeStream(in, null, o2);
            }
            if (bm == null) return false;
            int side = Math.min(bm.getWidth(), bm.getHeight());
            Bitmap sq = Bitmap.createBitmap(bm, (bm.getWidth() - side) / 2,
                    (bm.getHeight() - side) / 2, side, side);
            Bitmap out = Bitmap.createScaledBitmap(sq, 384, 384, true);
            File f = imageFile(c, id);
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (FileOutputStream fo = new FileOutputStream(f)) {
                out.compress(Bitmap.CompressFormat.PNG, 100, fo);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** אייקון לקיצור דרך (אדפטיבי - הלאנצ'ר מחיל את הצורה). */
    static Icon icon(Context c, Item it) {
        return Icon.createWithAdaptiveBitmap(adaptive(c, it));
    }

    static Bitmap adaptive(Context c, Item it) {
        Bitmap b = baseAdaptive(c, it);
        if (it.arrow) drawArrow(new Canvas(b));
        return b;
    }

    /** חץ קיצור בפינה השמאלית-תחתונה: ריבוע לבן מעוגל עם חץ כחול, כמו בווינדוס. */
    private static void drawArrow(Canvas cv) {
        float size = 108f;
        float bx = VIS_START + 2, by = VIS_END - 2 - size;
        float k = size / 100f;
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.WHITE);
        RectF box = new RectF(bx, by, bx + size, by + size);
        cv.drawRoundRect(box, 20, 20, fill);
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(3f);
        edge.setColor(0xFFB0B7C3);
        cv.drawRoundRect(box, 20, 20, edge);

        Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setColor(0xFF1565C0);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(11f * k);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        android.graphics.Path shaft = new android.graphics.Path();
        shaft.moveTo(bx + 26 * k, by + 78 * k);
        shaft.lineTo(bx + 26 * k, by + 52 * k);
        shaft.quadTo(bx + 26 * k, by + 36 * k, bx + 42 * k, by + 36 * k);
        shaft.lineTo(bx + 56 * k, by + 36 * k);
        cv.drawPath(shaft, ink);
        ink.setStyle(Paint.Style.FILL);
        android.graphics.Path head = new android.graphics.Path();
        head.moveTo(bx + 54 * k, by + 19 * k);
        head.lineTo(bx + 84 * k, by + 36 * k);
        head.lineTo(bx + 54 * k, by + 53 * k);
        head.close();
        cv.drawPath(head, ink);
    }

    private static Bitmap baseAdaptive(Context c, Item it) {
        Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Types.T t = Types.get(it.type);

        if ("image".equals(it.iconKind)) {
            File f = imageFile(c, it.id);
            Bitmap src = f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
            if (src != null) {
                cv.drawColor(it.color);
                int side = Math.min(src.getWidth(), src.getHeight());
                Rect sr = new Rect((src.getWidth() - side) / 2, (src.getHeight() - side) / 2,
                        (src.getWidth() + side) / 2, (src.getHeight() + side) / 2);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                cv.drawBitmap(src, sr, new Rect(VIS_START, VIS_START, VIS_END, VIS_END), p);
                return b;
            }
        }

        if ("auto".equals(it.iconKind) && "app".equals(it.type) && !it.d1.isEmpty()) {
            try {
                Drawable d = c.getPackageManager().getApplicationIcon(it.d1);
                if (d instanceof AdaptiveIconDrawable) {
                    AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
                    Drawable bg = ad.getBackground();
                    Drawable fg = ad.getForeground();
                    if (bg != null) {
                        bg.setBounds(0, 0, S, S);
                        bg.draw(cv);
                    }
                    if (fg != null) {
                        fg.setBounds(0, 0, S, S);
                        fg.draw(cv);
                    }
                } else {
                    cv.drawColor(Color.WHITE);
                    d.setBounds(84, 84, S - 84, S - 84);
                    d.draw(cv);
                }
                return b;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        boolean auto = "auto".equals(it.iconKind);
        int color = auto ? t.color : it.color;
        String glyph = (auto || it.glyph.isEmpty()) ? t.emoji : it.glyph;
        cv.drawColor(color);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextAlign(Paint.Align.CENTER);
        double lum = (0.299 * Color.red(color) + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255.0;
        p.setColor(lum > 0.72 ? 0xFF1B1F2A : Color.WHITE);
        int n = glyph.codePointCount(0, glyph.length());
        float ts = n <= 1 ? 170f : (n == 2 ? 130f : Math.max(50f, 330f / n));
        p.setTextSize(ts);
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = S / 2f - (fm.ascent + fm.descent) / 2f;
        cv.drawText(glyph, S / 2f, y, p);
        return b;
    }

    /** תצוגה מקדימה בתוך האפליקציה: ריבוע מעוגל. */
    static Bitmap preview(Context c, Item it, int px) {
        String key = it.id + "|" + it.type + "|" + it.iconKind + "|" + it.color + "|" + it.glyph
                + "|" + it.d1 + "|" + it.arrow + "|" + px + "|" + imageFile(c, it.id).lastModified();
        Bitmap hit = cache.get(key);
        if (hit != null) return hit;

        Bitmap full = adaptive(c, it);
        Bitmap crop = Bitmap.createBitmap(full, VIS_START, VIS_START, VIS_END - VIS_START,
                VIS_END - VIS_START);
        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Bitmap scaled = Bitmap.createScaledBitmap(crop, px, px, true);
        p.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float r = px * 0.28f;
        cv.drawRoundRect(new RectF(0, 0, px, px), r, r, p);
        cache.put(key, out);
        return out;
    }
}

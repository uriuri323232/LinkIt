package com.lnk.shortcuts;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LinkIt כספק אחסון: הקיצורים מופיעים כקבצי .linkit בתוך מנהלי קבצים
 * ובוחרי קבצים של המערכת. פתיחת קובץ כזה מפעילה את הקיצור.
 */
public class ShortcutDocsProvider extends DocumentsProvider {
    static final String AUTH = "com.lnk.shortcuts.docs";
    private static final String ROOT_DOC = "root";
    private static final String[] ROOT_COLS = {Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS,
            Root.COLUMN_ICON, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY, Root.COLUMN_DOCUMENT_ID};
    private static final String[] DOC_COLS = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE, Document.COLUMN_ICON};

    @Override
    public boolean onCreate() {
        return true;
    }

    /** מוסיף ערך רק אם הלקוח ביקש את העמודה (מונע קריסה כשמנהל קבצים מבקש פחות עמודות). */
    private static void put(MatrixCursor c, MatrixCursor.RowBuilder r, String col, Object v) {
        if (c.getColumnIndex(col) >= 0) r.add(col, v);
    }

    private static String[] cols(String[] projection, String[] def) {
        return projection != null ? projection : def;
    }

    /** שמות קבצים ייחודיים לכל פריט. */
    private Map<String, String> names(List<Item> items) {
        Map<String, String> out = new HashMap<>();
        Map<String, Integer> used = new HashMap<>();
        for (Item it : items) {
            String n = LinkitFile.fileName(it);
            Integer c = used.get(n);
            if (c != null) {
                used.put(n, c + 1);
                n = n.substring(0, n.length() - 7) + " (" + (c + 1) + ").linkit";
            } else {
                used.put(n, 1);
            }
            out.put(it.id, n);
        }
        return out;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor c = new MatrixCursor(cols(projection, ROOT_COLS));
        MatrixCursor.RowBuilder r = c.newRow();
        put(c, r, Root.COLUMN_ROOT_ID, "linkit");
        put(c, r, Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_IS_CHILD);
        put(c, r, Root.COLUMN_ICON, R.mipmap.ic_launcher);
        put(c, r, Root.COLUMN_TITLE, "LinkIt - קיצורים");
        put(c, r, Root.COLUMN_SUMMARY, "הקיצורים שלך כקבצים");
        put(c, r, Root.COLUMN_DOCUMENT_ID, ROOT_DOC);
        return c;
    }

    private void addRoot(MatrixCursor c) {
        MatrixCursor.RowBuilder r = c.newRow();
        put(c, r, Document.COLUMN_DOCUMENT_ID, ROOT_DOC);
        put(c, r, Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
        put(c, r, Document.COLUMN_DISPLAY_NAME, "LinkIt");
        put(c, r, Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
        put(c, r, Document.COLUMN_FLAGS, 0);
        put(c, r, Document.COLUMN_SIZE, 0);
    }

    private void addItem(MatrixCursor c, Item it, String name) {
        MatrixCursor.RowBuilder r = c.newRow();
        put(c, r, Document.COLUMN_DOCUMENT_ID, "i:" + it.id);
        put(c, r, Document.COLUMN_MIME_TYPE, LinkitFile.MIME);
        put(c, r, Document.COLUMN_DISPLAY_NAME, name);
        put(c, r, Document.COLUMN_LAST_MODIFIED, Math.max(it.lastUsed, it.created));
        put(c, r, Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_THUMBNAIL);
        put(c, r, Document.COLUMN_ICON, R.drawable.ic_shortcut_badge);
        put(c, r, Document.COLUMN_SIZE, (long) LinkitFile.write(it).getBytes(StandardCharsets.UTF_8).length);
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(cols(projection, DOC_COLS));
        if (ROOT_DOC.equals(documentId)) {
            addRoot(c);
            return c;
        }
        List<Item> items = Store.all(getContext());
        Map<String, String> nm = names(items);
        for (Item it : items) {
            if (("i:" + it.id).equals(documentId)) {
                addItem(c, it, nm.get(it.id));
                return c;
            }
        }
        throw new FileNotFoundException(documentId);
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        if (!ROOT_DOC.equals(parentDocumentId)) throw new FileNotFoundException(parentDocumentId);
        MatrixCursor c = new MatrixCursor(cols(projection, DOC_COLS));
        List<Item> items = Store.all(getContext());
        Map<String, String> nm = names(items);
        for (Item it : items) addItem(c, it, nm.get(it.id));
        return c;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        if (mode.contains("w")) throw new FileNotFoundException("read-only");
        for (Item it : Store.all(getContext())) {
            if (("i:" + it.id).equals(documentId)) {
                try {
                    File dir = new File(getContext().getCacheDir(), "docs");
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, it.id + ".linkit");
                    Store.writeFile(f, LinkitFile.write(it).getBytes(StandardCharsets.UTF_8));
                    return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
                } catch (Exception e) {
                    throw new FileNotFoundException(e.getMessage());
                }
            }
        }
        throw new FileNotFoundException(documentId);
    }

    /** תמונה ממוזערת: אייקון הקיצור עם סמל קיצור דרך, כך שגם בתוך מנהלי קבצים אחרים רואים שזה קיצור. */
    @Override
    public AssetFileDescriptor openDocumentThumbnail(String documentId, Point sizeHint, CancellationSignal signal)
            throws FileNotFoundException {
        for (Item it : Store.all(getContext())) {
            if (!("i:" + it.id).equals(documentId)) continue;
            try {
                int px = sizeHint == null ? 192 : Math.max(96, Math.min(sizeHint.x, 384));
                Bitmap b = IconMaker.thumbnail(getContext(), it, px);
                File dir = new File(getContext().getCacheDir(), "docs");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, "t_" + it.id + ".png");
                try (java.io.FileOutputStream fo = new java.io.FileOutputStream(f)) {
                    b.compress(Bitmap.CompressFormat.PNG, 100, fo);
                }
                b.recycle();
                return new AssetFileDescriptor(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY),
                        0, AssetFileDescriptor.UNKNOWN_LENGTH);
            } catch (Exception e) {
                throw new FileNotFoundException(e.getMessage());
            }
        }
        throw new FileNotFoundException(documentId);
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return ROOT_DOC.equals(parentDocumentId) && documentId != null && documentId.startsWith("i:");
    }
}

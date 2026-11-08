package com.lnk.shortcuts;

import android.database.Cursor;
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
            Document.COLUMN_SIZE};

    @Override
    public boolean onCreate() {
        return true;
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
        r.add(Root.COLUMN_ROOT_ID, "linkit");
        r.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_IS_CHILD);
        r.add(Root.COLUMN_ICON, R.mipmap.ic_launcher);
        r.add(Root.COLUMN_TITLE, "LinkIt - קיצורים");
        r.add(Root.COLUMN_SUMMARY, "הקיצורים שלך כקבצים");
        r.add(Root.COLUMN_DOCUMENT_ID, ROOT_DOC);
        return c;
    }

    private void addRoot(MatrixCursor c) {
        MatrixCursor.RowBuilder r = c.newRow();
        r.add(Document.COLUMN_DOCUMENT_ID, ROOT_DOC);
        r.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
        r.add(Document.COLUMN_DISPLAY_NAME, "LinkIt");
        r.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
        r.add(Document.COLUMN_FLAGS, 0);
        r.add(Document.COLUMN_SIZE, 0);
    }

    private void addItem(MatrixCursor c, Item it, String name) {
        MatrixCursor.RowBuilder r = c.newRow();
        r.add(Document.COLUMN_DOCUMENT_ID, "i:" + it.id);
        r.add(Document.COLUMN_MIME_TYPE, LinkitFile.MIME);
        r.add(Document.COLUMN_DISPLAY_NAME, name);
        r.add(Document.COLUMN_LAST_MODIFIED, Math.max(it.lastUsed, it.created));
        r.add(Document.COLUMN_FLAGS, 0);
        r.add(Document.COLUMN_SIZE, (long) LinkitFile.write(it).getBytes(StandardCharsets.UTF_8).length);
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

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return ROOT_DOC.equals(parentDocumentId) && documentId != null && documentId.startsWith("i:");
    }
}

package com.lnk.shortcuts;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** מגיש קבצים מהמנהל המובנה לאפליקציות אחרות (קריאה בלבד, רק בהרשאה זמנית). */
public class FileShareProvider extends ContentProvider {
    static final String AUTH = "com.lnk.shortcuts.files";

    static Uri uriFor(File f) {
        return new Uri.Builder().scheme("content").authority(AUTH)
                .encodedPath("/f" + Uri.encode(f.getAbsolutePath(), "/")).build();
    }

    private static File fileOf(Uri u) {
        String p = u.getPath();
        if (p == null || !p.startsWith("/f/")) return null;
        return new File(p.substring(2));
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        File f = fileOf(uri);
        if (f == null || !f.exists()) return null;
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        File f = fileOf(uri);
        return f == null ? null : FileOps.mime(f);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileOf(uri);
        if (f == null || !f.isFile()) throw new FileNotFoundException(String.valueOf(uri));
        if (mode.contains("w")) throw new FileNotFoundException("read-only");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}

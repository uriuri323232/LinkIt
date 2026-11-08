package com.lnk.shortcuts;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

/** הרשאת גישה לכל הקבצים (נדרשת למנהל הקבצים המובנה). */
final class Perm {
    private Perm() {}

    static boolean has(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return c.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
                && (Build.VERSION.SDK_INT > 29 || c.checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    static void ask(Activity a) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + a.getPackageName()));
                a.startActivity(i);
            } catch (Exception e) {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            a.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
        }
    }
}

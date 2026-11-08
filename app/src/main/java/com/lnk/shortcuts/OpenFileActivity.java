package com.lnk.shortcuts;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** נקודת הכניסה כשפותחים קובץ .linkit מכל מנהל קבצים. */
public class OpenFileActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Uri u = getIntent().getData();
        Item it = null;
        if (u != null) {
            try (InputStream in = getContentResolver().openInputStream(u)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n, total = 0;
                while (in != null && (n = in.read(buf)) > 0 && total < 262144) {
                    bos.write(buf, 0, n);
                    total += n;
                }
                it = LinkitFile.parse(new String(bos.toByteArray(), StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
        if (it == null) {
            Ui.toast(this, "קובץ קיצור לא תקין או שאין גישה אליו");
        } else {
            Intent i = Launcher.launchIntent(this, it);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
        finish();
        overridePendingTransition(0, 0);
    }
}

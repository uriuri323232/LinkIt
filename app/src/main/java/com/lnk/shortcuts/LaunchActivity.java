package com.lnk.shortcuts;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/** אקטיביטי שקוף: מופעל מקיצור במסך הבית, או כשמנהל קבצים אחר פותח קובץ .lnkit. */
public class LaunchActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String type = getIntent().getStringExtra("type");
            String data = getIntent().getStringExtra("data");
            Uri u = getIntent().getData();
            if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && u != null) {
                String[] t = ShortcutFile.readUri(this, u);
                if (t == null) {
                    Toast.makeText(this, "קובץ הקיצור פגום", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                type = t[0];
                data = t[1];
            }
            Intent i = Opener.target(this, type, data);
            if (i == null) {
                Toast.makeText(this, "היעד לא נמצא", Toast.LENGTH_SHORT).show();
            } else {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        } catch (Exception e) {
            Toast.makeText(this, "לא ניתן לפתוח את היעד", Toast.LENGTH_LONG).show();
        }
        finish();
    }
}

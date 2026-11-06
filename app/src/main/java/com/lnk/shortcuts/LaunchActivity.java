package com.lnk.shortcuts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** אקטיביטי שקוף שמופעל מקיצור הדרך ופותח את היעד. */
public class LaunchActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent i = Opener.target(this,
                    getIntent().getStringExtra("type"), getIntent().getStringExtra("data"));
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

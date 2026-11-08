package com.lnk.shortcuts;

import android.app.Activity;
import android.os.Bundle;

/** נקודת הכניסה של קיצור שנלחץ במסך הבית. שקוף, ונסגר מיד. */
public class LaunchActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String id = getIntent().getStringExtra("id");
        Item it = Store.get(this, id);

        if (it == null) {
            // הקיצור נמחק מהאפליקציה? ננסה להריץ מהנתונים שבתוך הקיצור עצמו.
            String type = getIntent().getStringExtra("type");
            if (id == null || type == null) {
                Ui.toast(this, "הקיצור לא נמצא");
                finish();
                return;
            }
            it = new Item();
            it.id = id;
            it.type = type;
            it.name = nz(getIntent().getStringExtra("name"));
            it.d1 = nz(getIntent().getStringExtra("d1"));
            it.d2 = nz(getIntent().getStringExtra("d2"));
            it.d3 = nz(getIntent().getStringExtra("d3"));
            if ("group".equals(type)) {
                Ui.toast(this, "התפריט נמחק מהאפליקציה");
                finish();
                return;
            }
        }
        Launcher.run(this, it, true);
        overridePendingTransition(0, 0);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

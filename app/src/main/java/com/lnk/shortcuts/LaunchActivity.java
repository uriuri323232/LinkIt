package com.lnk.shortcuts;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.Toast;

/** אקטיביטי שקוף שמופעל מקיצור הדרך ופותח את היעד. */
public class LaunchActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String type = getIntent().getStringExtra("type");
            String data = getIntent().getStringExtra("data");
            Intent i = null;

            if ("url".equals(type)) {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(data));
            } else if ("file".equals(type)) {
                Uri uri = Uri.parse(data);
                i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, getContentResolver().getType(uri));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else if ("dir".equals(type)) {
                Uri tree = Uri.parse(data);
                Uri doc = DocumentsContract.buildDocumentUriUsingTree(
                        tree, DocumentsContract.getTreeDocumentId(tree));
                i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(doc, DocumentsContract.Document.MIME_TYPE_DIR);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else if ("app".equals(type)) {
                i = getPackageManager().getLaunchIntentForPackage(data);
            }

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

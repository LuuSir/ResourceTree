package com.example.resouretree;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/** Test APK only. Plain Java keeps this separate process independent of the target APK's Kotlin runtime. */
public class ShareReceiveActivity extends Activity {
    @SuppressWarnings("deprecation")
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle result = new Bundle();
        try {
            result.putInt("uid", android.os.Process.myUid());
            result.putString("text", getIntent().getStringExtra(Intent.EXTRA_TEXT));
            result.putString("mime", getIntent().getType());
            Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Missing stream");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                byte[] bytes = output.toByteArray();
                result.putByteArray("digest", MessageDigest.getInstance("SHA-256").digest(bytes));
                result.putString("scheme", uri.getScheme());
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                result.putInt("width", bitmap == null ? 0 : bitmap.getWidth());
                if (bitmap != null) bitmap.recycle();
            }
        } catch (Exception e) { result.putString("error", e.toString()); }
        ResultReceiver receiver = getIntent().getParcelableExtra("verification");
        if (receiver != null) receiver.send(0, result);
        finish();
    }
}

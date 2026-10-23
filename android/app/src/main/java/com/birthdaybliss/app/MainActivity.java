package com.birthdaybliss.app;

import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ClipData;
import android.content.pm.PackageManager;
import android.webkit.JavascriptInterface;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.util.Base64;
import android.content.ContentValues;
import android.provider.MediaStore;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_CHOOSER_REQUEST = 4107;

    public class BirthdayBridge {
        @JavascriptInterface public void shareText(String text, String title) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, text);
                try { startActivity(Intent.createChooser(send, title == null ? "Send birthday wish" : title)); }
                catch (ActivityNotFoundException ex) { android.widget.Toast.makeText(MainActivity.this, "No sharing apps found", android.widget.Toast.LENGTH_SHORT).show(); }
            });
        }
        @JavascriptInterface public void sharePoster(String base64Png, String text, String title) {
            runOnUiThread(() -> {
                Uri imageUri = null;
                try {
                    byte[] bytes = Base64.decode(base64Png, Base64.DEFAULT);
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, "birthday-bliss-wish.png");
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BirthdayBliss");
                    imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (imageUri == null) throw new Exception("Could not create poster image");
                    try (OutputStream out = getContentResolver().openOutputStream(imageUri)) { out.write(bytes); }
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("image/png");
                    send.putExtra(Intent.EXTRA_STREAM, imageUri);
                    send.putExtra(Intent.EXTRA_TEXT, text);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    send.setClipData(ClipData.newUri(getContentResolver(), "Birthday poster", imageUri));
                    startActivity(Intent.createChooser(send, title == null ? "Share birthday poster and wish" : title));
                } catch (Exception ex) {
                    if (imageUri != null) getContentResolver().delete(imageUri, null, null);
                    android.widget.Toast.makeText(MainActivity.this, "Could not share poster: " + ex.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(23, 16, 47));
        getWindow().setNavigationBarColor(Color.rgb(16, 11, 34));

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new BirthdayBridge(), "BirthdayNative");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                Intent intent;
                try {
                    intent = params.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (RuntimeException ex) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileCallback != null) {
                Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                fileCallback.onReceiveValue(results);
                fileCallback = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}

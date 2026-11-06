package id.batanggaring.editor;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// Batang Garing Editor: editor foto & video (index.html di dalam APK) di WebView milik aplikasi.
// File dilayani dari assets lewat alamat https://appassets.androidplatform.net/ supaya WebGL,
// MediaRecorder, dan localStorage berjalan seperti di browser biasa.
public class MainActivity extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    static final String BASE = "https://" + HOST + "/";
    static final int REQ_FILE = 11, REQ_PERM = 12;

    WebView web;
    NativeBridge bridge;
    ValueCallback<Uri[]> fileCb;
    Uri sharedUri;          // file dari menu "Bagikan" Galeri
    boolean pageReady;
    Runnable afterPerm;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.parseColor("#1D6B45"));
        getWindow().setNavigationBarColor(Color.parseColor("#13211A"));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#F3F6E9"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);

        bridge = new NativeBridge(this);
        web.addJavascriptInterface(bridge, "BGApp");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        handleIntent(getIntent());
        web.loadUrl(BASE + "index.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    void handleIntent(Intent i) {
        if (i == null) return;
        Uri u = null;
        if (Intent.ACTION_SEND.equals(i.getAction())) u = i.getParcelableExtra(Intent.EXTRA_STREAM);
        else if (Intent.ACTION_EDIT.equals(i.getAction())) u = i.getData();
        if (u == null) return;
        sharedUri = u;
        if (pageReady) openShared();
    }

    // Beri tahu editor ada file kiriman; editor mengambilnya lewat alamat /__shared
    void openShared() {
        if (sharedUri == null) return;
        String mime = getContentResolver().getType(sharedUri);
        String name = displayName(sharedUri);
        String js = "window.BGEditorOpenShared && window.BGEditorOpenShared(" + JSONObject.quote(BASE + "__shared?t=" + System.currentTimeMillis())
                + "," + JSONObject.quote(mime == null ? "" : mime) + "," + JSONObject.quote(name) + ")";
        web.evaluateJavascript(js, null);
    }

    String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return "media";
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.BGEditorBack && window.BGEditorBack()) ? 1 : 0", r -> {
            if (!"1".equals(r)) MainActivity.super.onBackPressed();
        });
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    WebResourceResponse local(Uri u) {
        if (!"https".equals(u.getScheme()) || !HOST.equals(u.getHost())) return null;
        String path = u.getPath() == null ? "" : u.getPath().replaceFirst("^/", "");
        try {
            if (path.equals("__shared")) {
                if (sharedUri == null) return null;
                String mime = getContentResolver().getType(sharedUri);
                InputStream in = getContentResolver().openInputStream(sharedUri);
                WebResourceResponse r = new WebResourceResponse(mime == null ? "application/octet-stream" : mime, null, in);
                Map<String, String> h = new HashMap<>();
                h.put("Access-Control-Allow-Origin", "*");
                h.put("Cache-Control", "no-store");
                r.setResponseHeaders(h);
                return r;
            }
            if (path.isEmpty()) path = "index.html";
            InputStream in = getAssets().open("www/" + path);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
            if (path.endsWith(".svg")) mime = "image/svg+xml";
            if (path.endsWith(".html")) mime = "text/html";
            if (mime == null) mime = "application/octet-stream";
            boolean text = mime.startsWith("text/") || mime.endsWith("xml") || mime.endsWith("json");
            WebResourceResponse r = new WebResourceResponse(mime, text ? "utf-8" : null, in);
            Map<String, String> h = new HashMap<>();
            h.put("Cache-Control", "no-cache");
            r.setResponseHeaders(h);
            return r;
        } catch (IOException e) {
            return null;
        }
    }

    void openExternal(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            bridge.toast("Tidak ada aplikasi untuk membuka tautan ini");
        }
    }

    // Android 9 ke bawah butuh izin penyimpanan sebelum menyimpan ke Galeri
    boolean needsStoragePermission() {
        return Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
    }

    void requestStorage() {
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
    }

    @Override
    protected void onActivityResult(int req, int code, Intent data) {
        if (req == REQ_FILE && fileCb != null) {
            Uri[] r = null;
            if (code == RESULT_OK && data != null) {
                if (data.getData() != null) r = new Uri[]{data.getData()};
                else if (data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                    ClipData.Item it = data.getClipData().getItemAt(0);
                    r = new Uri[]{it.getUri()};
                }
            }
            fileCb.onReceiveValue(r);
            fileCb = null;
            return;
        }
        super.onActivityResult(req, code, data);
    }

    class Client extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) { return local(r.getUrl()); }

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            if (HOST.equals(r.getUrl().getHost())) return false;
            openExternal(r.getUrl().toString());
            return true;
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            pageReady = true;
            if (needsStoragePermission()) requestStorage();
            openShared();
        }
    }

    class Chrome extends WebChromeClient {
        // <input type="file">: buka Galeri / pengelola file sesuai jenis (foto, video)
        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
            if (fileCb != null) fileCb.onReceiveValue(null);
            fileCb = cb;
            String[] types = p.getAcceptTypes();
            java.util.ArrayList<String> mimes = new java.util.ArrayList<>();
            if (types != null) for (String t : types) for (String x : t.split(",")) if (!x.trim().isEmpty()) mimes.add(x.trim());
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            if (mimes.size() == 1) i.setType(mimes.get(0));
            else {
                i.setType("*/*");
                if (!mimes.isEmpty()) i.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray(new String[0]));
            }
            try {
                startActivityForResult(Intent.createChooser(i, "Pilih foto atau video"), REQ_FILE);
            } catch (ActivityNotFoundException e) {
                fileCb = null;
                return false;
            }
            return true;
        }
    }
}

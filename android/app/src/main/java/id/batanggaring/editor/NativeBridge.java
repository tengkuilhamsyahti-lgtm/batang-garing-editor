package id.batanggaring.editor;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Jembatan dari editor web ke Android (window.BGApp di JavaScript):
// menyimpan hasil ke Galeri (album BatangGaring) secara bertahap, lalu membagikannya.
public class NativeBridge {
    static final String ALBUM = "BatangGaring";

    final MainActivity act;
    final Handler ui = new Handler(Looper.getMainLooper());
    final Map<String, Pending> pending = new HashMap<>();
    int counter = 0;

    static class Pending {
        String name, mime;
        File tmp;
        OutputStream out;
    }

    NativeBridge(MainActivity a) { act = a; }

    @JavascriptInterface
    public String info() { return "android " + Build.VERSION.SDK_INT; }

    @JavascriptInterface
    public void toast(String msg) {
        ui.post(() -> Toast.makeText(act, msg, Toast.LENGTH_LONG).show());
    }

    @JavascriptInterface
    public synchronized String saveBegin(String name, String mime) {
        try {
            Pending p = new Pending();
            p.name = name.replaceAll("[^A-Za-z0-9._-]", "_");
            p.mime = mime;
            p.tmp = new File(act.getCacheDir(), "out_" + System.currentTimeMillis() + "_" + p.name);
            p.out = new FileOutputStream(p.tmp);
            String id = "s" + (++counter);
            pending.put(id, p);
            return id;
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public synchronized void saveChunk(String id, String b64) {
        Pending p = pending.get(id);
        if (p == null) return;
        try {
            p.out.write(Base64.decode(b64, Base64.DEFAULT));
        } catch (Exception e) {
            closeQuietly(p);
            p.out = null;
        }
    }

    // Memindahkan file sementara ke Galeri. Mengembalikan alamat content:// atau "ERR:pesan".
    @JavascriptInterface
    public String saveEnd(String id) {
        Pending p;
        synchronized (this) { p = pending.remove(id); }
        if (p == null) return "ERR:Data tidak ditemukan";
        if (p.out == null) { p.tmp.delete(); return "ERR:Gagal menulis file sementara"; }
        closeQuietly(p);
        try {
            Uri uri = publish(p);
            return uri.toString();
        } catch (Exception e) {
            return "ERR:" + (e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            p.tmp.delete();
        }
    }

    Uri publish(Pending p) throws Exception {
        boolean video = p.mime.startsWith("video");
        ContentResolver cr = act.getContentResolver();
        if (Build.VERSION.SDK_INT >= 29) {
            Uri coll = video ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, p.name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, p.mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES) + "/" + ALBUM);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = cr.insert(coll, v);
            if (uri == null) throw new Exception("Galeri menolak file baru");
            try (InputStream in = new FileInputStream(p.tmp); OutputStream out = cr.openOutputStream(uri)) {
                copy(in, out);
            } catch (Exception e) {
                cr.delete(uri, null, null);
                throw e;
            }
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, v, null, null);
            return uri;
        }
        if (act.needsStoragePermission()) {
            ui.post(act::requestStorage);
            throw new Exception("Izinkan akses penyimpanan, lalu tekan Simpan lagi");
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES), ALBUM);
        dir.mkdirs();
        File f = new File(dir, p.name);
        try (InputStream in = new FileInputStream(p.tmp); OutputStream out = new FileOutputStream(f)) {
            copy(in, out);
        }
        final Uri[] result = {Uri.fromFile(f)};
        CountDownLatch latch = new CountDownLatch(1);
        MediaScannerConnection.scanFile(act, new String[]{f.getAbsolutePath()}, new String[]{p.mime}, (path, uri) -> {
            if (uri != null) result[0] = uri;
            latch.countDown();
        });
        latch.await(5, TimeUnit.SECONDS);
        return result[0];
    }

    @JavascriptInterface
    public void share(String uri, String mime) {
        ui.post(() -> {
            try {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(mime);
                i.putExtra(Intent.EXTRA_STREAM, Uri.parse(uri));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                act.startActivity(Intent.createChooser(i, "Bagikan ke"));
            } catch (Exception e) {
                toast("Tidak bisa membagikan: " + e.getMessage());
            }
        });
    }

    static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    static void closeQuietly(Pending p) {
        try { if (p.out != null) p.out.close(); } catch (Exception ignored) { }
    }
}

# Batang Garing Editor

Editor foto dan video pribadi Batang Garing Coffee, untuk membuat konten media sosial yang menarik pelanggan.

## Fitur
- **Sempurnakan:** Auto Perbaiki Kualitas satu ketukan (cahaya, warna, kehangatan, ketajaman), dengan perbandingan sebelum/sesudah.
- **Filter khas kopi:** Menu Segar, Espresso, Latte, Senja, Pagi Cerah, Matcha, Vintage, Robusta Mono.
- **Atur:** kecerahan, kontras, area terang, area gelap, saturasi, warna hidup, kehangatan, hijau/magenta, ketajaman, vignet, matte.
- **Ukuran:** 1:1, 4:5 (Feed Instagram), 9:16 (Story, Reels, TikTok), 16:9; geser dan perbesar, atau tampil utuh dengan latar buram / warna brand.
- **Teks promo:** MENU BARU, PROMO HARI INI, harga, jam buka, dll. Tiga gaya (polos, label, garis), tiga jenis huruf, warna brand.
- **Logo:** logo BG bawaan atau logo sendiri (diingat).
- **Video:** semua efek di atas ikut diterapkan, potong durasi, suara asli on/off, simpan MP4 720p/1080p.
- Aplikasi Android menyimpan langsung ke Galeri (album `BatangGaring`) dan bisa membagikan ke Instagram/WhatsApp. Foto/video juga bisa dikirim ke editor dari menu **Bagikan** di Galeri.

## Struktur
| Path | Isi |
|---|---|
| `src/editor.html` | Sumber editor (satu file: HTML, CSS, JS, WebGL) |
| `index.html` | Hasil `scripts/build-web.sh`, dipakai GitHub Pages dan APK |
| `android/` | Pembungkus Android (WebView + simpan ke Galeri) |
| `.github/workflows/apk.yml` | Build APK otomatis dan terbit di Releases |

## Membuat APK
Setiap push ke `main` membangun APK dan menerbitkannya di halaman **Releases** (`BatangGaringEditor.apk`).
Setelah mengubah `src/editor.html`, jalankan `sh scripts/build-web.sh` (workflow juga menjalankannya).

Butuh Android 7.0 ke atas. Simpan video membutuhkan Android System WebView versi terbaru (perbarui lewat Play Store bila hasilnya WEBM).

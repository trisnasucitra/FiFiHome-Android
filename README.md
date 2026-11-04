# FiFiHome Android

Aplikasi Android native (Jetpack Compose) berbahasa Indonesia untuk server FiFiHome.

## Konfigurasi bawaan
- Server/API: `http://192.168.0.50/FiFiHome/`
- MQTT TCP: `192.168.0.50:1883`
- MQTT WebSocket pada dashboard web tetap menggunakan port `9001`; aplikasi Android menggunakan TCP `1883`.
- Koneksi jaringan lokal/Wi-Fi diperlukan.

## Build APK melalui GitHub Actions
Workflow `.github/workflows/android.yml` akan berjalan saat ada push ke `main` atau dijalankan manual melalui tab **Actions**. Hasil APK tersedia pada bagian **Artifacts** dengan nama `FiFiHome-debug-APK`.

## Catatan penting sebelum pengujian
1. Pastikan perangkat Android berada pada jaringan yang dapat menjangkau `192.168.0.50`.
2. API PHP saat ini belum menyediakan `action=server_time`. Tambahkan potongan pada `server-api-server-time.patch.txt` ke `api.php` di dalam blok GET, sebelum pemrosesan `zone`. Endpoint ini memakai jam dan zona waktu komputer server.
3. Aplikasi tidak menjalankan jadwal AUTO secara lokal. Waktu perangkat Android tidak dipakai untuk memicu jadwal. Penjadwalan tetap berada pada sistem dashboard/server sesuai implementasi yang ada; tahap penjadwalan ESP1 merupakan pengembangan terpisah.
4. Login memakai session PHP `index.php`. Pastikan endpoint login menghasilkan cookie sesi PHP dan tidak memerlukan CSRF/token tambahan.
5. Build debug belum dianggap lulus sampai workflow berhasil dan APK diuji pada tablet serta server/perangkat MQTT sebenarnya.
6. Jangan memasukkan `koneksi.php`, kata sandi database, token, atau kredensial server ke repository publik.

## Status fitur pada versi awal
- Login PHP dan sesi cookie.
- Koneksi MQTT TCP serta perintah dasar Kamar/Taman.
- Lapisan API untuk mengambil/menyimpan konfigurasi zona; layar pengaturan jadwal belum diselesaikan pada versi awal ini.
- Tampilan riwayat aktivitas dengan pagination API.
- Permintaan waktu server melalui endpoint tambahan.
- Pembacaan sensor MQTT dan tampilan nilai sensor masih perlu diselesaikan.
- Pengaturan jadwal Kamar/Taman di antarmuka Android masih perlu diselesaikan.

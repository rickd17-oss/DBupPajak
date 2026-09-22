# DBupPajak

Aplikasi Android pengarsipan dokumen pajak ke Google Drive. Dibuat untuk **NR17**.

## Fitur

- Bebas digunakan tanpa akun/login aplikasi.
- Konfigurasi API Google Drive saat pertama kali dibuka.
- Folder otomatis dengan pola `BLOK-NOP`, misalnya `02-0012`.
- Kebutuhan Mutasi Penuh, Mutasi Pecah, dan Perbaikan.
- Rincian nama dan status bangunan untuk setiap hasil pecahan.
- Dokumen KTP, KK, Sertifikat, SPPT, dan Lainnya.
- Ambil dokumen dari kamera, galeri, atau PDF.
- `data_pengajuan.txt` dan `catatan.txt` dibuat otomatis.
- Tema premium navy, emas, dan putih.

## Menyiapkan Google Drive

1. Buat sebuah folder baru di Google Drive, misalnya `DBupPajak`.
2. Salin ID folder dari URL Drive. Contoh URL `https://drive.google.com/drive/folders/ABC123...`; ID folder adalah bagian setelah `/folders/`.
3. Buka [Google Apps Script](https://script.google.com), lalu buat project baru.
4. Salin isi `google-apps-script/Code.gs` ke editor Apps Script.
5. Ganti nilai `ACCESS_CODE` dengan kode rahasia buatan Anda.
6. Pilih **Deploy > New deployment > Web app**.
7. Atur **Execute as: Me** dan **Who has access: Anyone**.
8. Berikan izin Google Drive, lalu salin URL Web App yang berakhiran `/exec`.
9. Buka DBupPajak dan masukkan URL Web App, ID folder, serta kode akses yang sama.
10. Tekan **Tes koneksi**, lalu **Simpan & Mulai**.

Jangan membagikan URL Web App dan kode akses kepada pihak yang tidak dipercaya. Jika bocor, ubah `ACCESS_CODE` lalu deploy versi baru.

## Membuat APK

1. Buka folder proyek ini melalui Android Studio.
2. Tunggu proses Gradle Sync selesai.
3. Pilih **Build > Build APK(s)**.
4. APK debug berada di `app/build/outputs/apk/debug/app-debug.apk`.

Untuk distribusi, gunakan **Build > Generate Signed Bundle / APK** dan simpan keystore dengan aman.

## Membuat APK langsung melalui HP

1. Ekstrak ZIP proyek menggunakan ZArchiver atau pengelola file.
2. Instal AndroidIDE dari sumber resminya, lalu selesaikan pemasangan build tools.
3. Di AndroidIDE, pilih **Open existing project** dan buka folder `DBupPajak`.
4. Izinkan Gradle Sync berjalan sampai selesai. Unduhan pertama memerlukan internet dan ruang kosong yang cukup besar.
5. Buka menu build dan jalankan **Assemble Debug**, atau jalankan `./gradlew assembleDebug` dari terminal proyek.
6. APK hasil build berada di `app/build/outputs/apk/debug/app-debug.apk`.

Jika muncul masalah izin pada terminal, jalankan `chmod +x gradlew`, lalu ulangi perintah build.

## Catatan batas ukuran

Apps Script memiliki batas ukuran permintaan. Gunakan hasil scan yang telah dikompresi; berkas tunggal sebaiknya tidak lebih dari sekitar 5 MB. Versi lanjutan dapat menambahkan kompresi gambar otomatis dan antrean unggahan.

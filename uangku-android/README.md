# UangKu — Android Finance Tracker

Aplikasi Android untuk mencatat **pemasukan dan pengeluaran harian** dengan tampilan modern dan data tersimpan secara lokal di perangkat.

## Fitur
- Dashboard saldo, total pemasukan, dan total pengeluaran
- Tambah, edit, dan hapus transaksi
- Kategori transaksi
- Pencarian riwayat
- Statistik pengeluaran per kategori
- Penyimpanan lokal (tanpa akun/server)
- Material 3 + Jetpack Compose

## Tech Stack
- Kotlin 2.3.20
- Jetpack Compose BOM 2026.08.00
- Android Gradle Plugin 9.4.0
- Gradle 9.6
- Min SDK 26 / Target SDK 37

## Menjalankan
Buka folder proyek ini di Android Studio versi stabil terbaru, tunggu Gradle sync, lalu jalankan konfigurasi `app` pada emulator/perangkat Android.

## Build APK
```bash
./gradlew assembleDebug
```
APK debug akan berada di `app/build/outputs/apk/debug/app-debug.apk`.

## Publikasi di GitHub

1. Buat repository publik, misalnya `uangku-android`.
2. Push seluruh isi proyek ke branch `main`.
3. Buat tag versi, contoh `v1.0.0`, lalu push tag tersebut.
4. Workflow `Release APK` akan membangun APK dan menerbitkannya sebagai GitHub Release sehingga pengguna lain dapat mengunduh APK dari halaman Releases.

## Lisensi
MIT

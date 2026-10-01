# Terminal

Aplikasi terminal emulator untuk Android — **shell sungguhan**, bukan simulasi.

Aplikasi ini menjalankan proses shell asli (`/system/bin/sh`) yang terhubung ke
pseudo-terminal (PTY) sehingga mendukung emulasi ANSI/VT100 penuh (warna,
kontrol kursor, dsb) — menggunakan mesin terminal open-source yang sama
dengan yang dipakai oleh [Termux](https://github.com/termux/termux-app)
(`terminal-emulator` & `terminal-view`). Tidak memerlukan root.

Perintah standar Android (toybox) seperti `ls`, `cd`, `pwd`, `cat`, `echo`,
`mkdir`, `ps`, `df`, `ping`, `am`, `pm`, dll semuanya berjalan nyata di
dalam sandbox aplikasi.

## Fitur

- Shell asli via PTY (bukan tampilan tiruan)
- Emulasi terminal penuh: warna, scrollback, cursor
- Baris tombol ekstra: `ESC`, `TAB`, `CTRL`, `ALT`, panah, `/`, `-`, `|`, `HOME`, `END`, `PASTE`
- Copy/paste ke clipboard Android
- Tidak perlu root

## Struktur proyek

Proyek Android Gradle standar:

```
app/                     modul aplikasi (Kotlin)
  src/main/java/...      MainActivity.kt
  src/main/res/...       layout, warna, tema, ikon
build.gradle, settings.gradle, gradle/   konfigurasi Gradle
.github/workflows/       CI untuk build APK otomatis
```

## Cara mendapatkan APK (build otomatis via GitHub Actions)

Sandbox pengembangan ini tidak memiliki akses jaringan ke server Android SDK
milik Google, sehingga APK tidak dikompilasi langsung di sini. Sebagai
gantinya, setiap push ke branch ini akan memicu **GitHub Actions**
(`.github/workflows/android-build.yml`) yang meng-compile APK secara penuh
menggunakan Android SDK resmi, lalu:

1. Mengunggah APK sebagai *workflow artifact*, dan
2. Membuat **GitHub Release** (prerelease, tag `build-<nomor-run>`) dengan
   file `app-debug.apk` terlampir — ini bisa diunduh langsung tanpa login.

Langkah untuk instalasi di HP Android:

1. Buka tab **Actions** di repo GitHub ini, tunggu run terbaru selesai
   (atau buka tab **Releases** untuk link unduhan langsung).
2. Unduh `app-debug.apk` ke HP Android Anda.
3. Izinkan "Install dari sumber tidak dikenal" untuk browser/file manager
   yang dipakai mengunduh.
4. Buka file APK tersebut untuk menginstal.

## Build lokal (opsional, jika Anda punya Android Studio)

```
./gradlew assembleDebug
```

APK hasil build akan berada di `app/build/outputs/apk/debug/app-debug.apk`.

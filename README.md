# 📱 AM Preset Player — Android App (Kotlin/Java)

Aplikasi Android Native (Kotlin/AndroidX) untuk **Alight Motion Preset Player & WebGL Runtime Engine**.

---

## ✨ Fitur Aplikasi

* 🚀 **Full Offline & Online Engine**: Semua shader efek WebGL (323+ efek), bentuk vektor, dan preset bawaan tertanam langsung di dalam aplikasi.
* ⚡ **Hardware Acceleration (Zero-Lag)**: Render grafis 60 FPS menggunakan GPU Android secara optimal.
* 📂 **Buka File XML Langsung**: Mendukung pembukaan file `.xml` Alight Motion langsung dari File Manager, WhatsApp, atau Telegram.
* 🔗 **Buka Link Preset Alight Motion**: Mendukung pembukaan link share Alight Motion (`alight.link` / `alightcreative.com/am/share/...`) dan Google Drive.
* 💾 **Simpan & Ekspor Video ke Galeri**: Terintegrasi langsung dengan Android MediaStore (`Downloads/AlightWeb` & `Movies/AlightWeb`).

---

## 🛠️ Cara Build APK di GitHub (Otomatis via GitHub Actions)

Proyek ini sudah dilengkapi dengan konfigurasi **GitHub Actions CI/CD** di `.github/workflows/build-apk.yml`.

### Langkah-langkah:
1. Buat repositori baru di GitHub (misalnya: `am-preset-player-android`).
2. Masuk ke folder proyek di terminal dan inisialisasi Git:
   ```bash
   cd /sdcard/am_android_app
   git init
   git add .
   git commit -m "Initial commit: AM Preset Player Android App"
   git branch -M main
   git remote add origin https://github.com/USERNAME/NAMA_REPO.git
   git push -u origin main
   ```
3. Buka repositori Anda di GitHub melalui browser.
4. Klik tab **Actions** di bagian atas.
5. Anda akan melihat workflow **Build Android APK** sedang berjalan secara otomatis.
6. Setelah selesai (sekitar 2–3 menit), klik workflow tersebut dan download file APK di bagian **Artifacts**:
   * 📥 **`AM-Preset-Player-Debug-APK`** (`app-debug.apk` — langsung bisa diinstall di HP tanpa perlu sign sertifikat!).

---

## 💻 Struktur Folder

```text
├── .github/workflows/
│   └── build-apk.yml       # GitHub Actions CI Workflow Auto-Build APK
├── app/
│   ├── src/main/
│   │   ├── java/com/alightweb/player/
│   │   │   ├── MainActivity.kt        # Activity Utama & WebView Controller
│   │   │   └── WebAppInterface.kt     # Native Android Bridge (Simpan File, Toast, Share)
│   │   ├── assets/                    # Engine WebGL, Shader, Presets & Shapes
│   │   ├── res/                       # Layout, Warna, dan Tema Android
│   │   └── AndroidManifest.xml        # Izin Akses & Intent Filter XML
│   └── build.gradle.kts               # Konfigurasi Build Module App
├── gradle/wrapper/                    # Gradle Wrapper 8.4
├── build.gradle.kts                   # Konfigurasi Build Root
├── settings.gradle.kts                # Konfigurasi Project & Plugin
└── README.md
```

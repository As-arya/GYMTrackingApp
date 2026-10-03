# GYMProject 0.1.0

Aplikasi gym Android dengan Kotlin, Jetpack Compose/Material 3, dan Room. Profil, plan berulang 1–4 minggu, pencatatan set, timer, kontribusi/streak, riwayat, dan backup bekerja offline. Tema Light/Dark/System, ilustrasi statis, serta ikon tersedia di resources aplikasi.

AI opsional memakai Groq dengan API key milik pengguna. Form akun tersedia, tetapi backend login/daftar/pemulihan belum terhubung; pilih **Lanjut tanpa akun** untuk memakai tracking.

## Build dan jalankan

Buka folder ini di Android Studio, lakukan Gradle Sync, pilih perangkat atau emulator, lalu Run app. Gunakan Gradle JDK 21. Project memakai minimum API 24, target SDK 36, dan compile SDK 37 minor API 2; pasang SDK yang diminta Android Studio.

Lokasi SDK dikonfigurasi secara lokal oleh Android Studio lewat `local.properties`. File tersebut tidak disertakan dalam repository. Jika memakai terminal, atur `JAVA_HOME` ke JDK 21 yang terpasang pada mesinmu.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Linux/macOS:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK debug berada di `app/build/outputs/apk/debug/app-debug.apk`. Output build tidak disertakan dalam Git; penandatanganan release belum dikonfigurasi.

## Mulai memakai

1. Pilih Lanjut tanpa akun dan isi profil bila diperlukan.
2. Buat plan dengan tanggal mulai, siklus 1–4 minggu, latihan dari katalog, dan target. Hari tanpa gerakan menjadi istirahat saat disimpan.
3. Mulai latihan dari Beranda, isi hasil aktual sesuai tipe gerakan, lalu simpan set. Izinkan notifikasi untuk kontrol countdown dari lock screen.
4. Akhiri sesi atau lanjutkan nanti. Satu set valid memenuhi streak hari terjadwal; target riwayat tetap memakai snapshot saat sesi dibuat.
5. Ekspor backup sebelum mengganti perangkat. Impor mengganti data setelah validasi dan konfirmasi; credential AI tidak disertakan.

Untuk AI, masukkan key Groq melalui pengaturan di aplikasi. Key dienkripsi menggunakan Android Keystore di perangkat, tidak tertanam di source, dan tidak masuk backup JSON. Konteks latihan dibagikan setelah izin pengguna; usulan perubahan ditinjau sebelum diterapkan.

## Status pengujian

Revisi UI telah diterapkan ke Compose. Build, unit checks, lint, pemeriksaan aset, dan alur utama pada emulator telah diperiksa. Pengujian perangkat nyata untuk layar terkunci, battery saver, lifecycle/OEM, TalkBack penuh, dan request AI live masih diperlukan. HyperIsland belum terintegrasi.

## Persiapan GitHub

Ikuti [SECURITY.md](SECURITY.md) sebelum commit atau push. Repo memuat source, Gradle Wrapper, schema Room, tes, artwork runtime, dan lisensi font. Catatan kerja pribadi, folder desain/referensi, screenshot pengujian, data aplikasi, kredensial, dan output build dikecualikan oleh `.gitignore`.

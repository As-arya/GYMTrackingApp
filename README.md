# GYMProject 0.1.0

Aplikasi gym Android dengan Kotlin, Jetpack Compose/Material 3, dan Room. Profil dengan foto, plan berulang 1–4 minggu, pencatatan set, timer, kontribusi/streak, riwayat, dan backup bekerja offline. Tema Light/Dark/System, ilustrasi statis, serta ikon tersedia di resources aplikasi.

Tidak ada akun atau login. Saat pertama dibuka, aplikasi langsung menampilkan onboarding singkat untuk menyiapkan profil, yang juga bisa dilewati. Antarmuka tersedia dalam Bahasa Indonesia dan English; secara bawaan mengikuti bahasa sistem dan bisa diganti dari onboarding atau **Pengaturan → Bahasa**. AI opsional memakai Groq dengan API key milik pengguna.

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

1. Ikuti onboarding: pilih bahasa, isi nama (foto opsional), goal, lalu usia/tinggi/berat. Tekan **Lewati** bila ingin mengisinya nanti dari Profil.
2. Buat plan dengan tanggal mulai, siklus 1–4 minggu, latihan dari katalog, dan target angka/rentang repetisi. Perubahan langsung berlaku hari ini, kecuali tanggal mulai di masa depan. Hari tanpa gerakan menjadi istirahat saat disimpan; sesi berjalan dan riwayat tetap memakai snapshot target awal.
3. Mulai latihan dari Beranda untuk membuka daftar gerakan hari ini. Pilih gerakan, isi hasil aktual sesuai tipe, lalu simpan set. Timer memakai isi hijau yang menyusut dengan riak; teks waktu dan gerakan berikutnya berada di luar kotak. Izinkan notifikasi untuk kontrol countdown dari lock screen.
   Saat waktu habis, alarm menunggu tombol **Hentikan alarm & lanjut**, lalu membuka set/gerakan berikutnya. Kontrol notifikasi juga dapat menghentikan alarm. Setelah seluruh set tercatat, simpan sesi; Beranda menampilkan hasil dan menghilangkan Langkah berikutnya.
4. Akhiri sesi atau lanjutkan nanti. Satu set valid memenuhi streak hari terjadwal; target riwayat tetap memakai snapshot saat sesi dibuat.
5. Ubah foto dari **Profil → Edit profil**. Foto yang sama tampil di header dan profil, disimpan di perangkat, serta ikut backup manual.
6. Tambah/koreksi/hapus berat dari **Riwayat berat badan** dan lihat grafiknya. Tanggal sesi yang sudah selesai dapat dikoreksi dari Detail sesi. Latihan di luar plan mendukung beberapa gerakan.
7. Ekspor backup sebelum mengganti perangkat. Backup schema 2 mencakup foto dan juga bekerja tanpa profil; schema 1 tetap dapat diimpor. Impor mengganti data setelah validasi dan konfirmasi; credential AI tidak disertakan.

Untuk AI, masukkan key Groq melalui pengaturan di aplikasi. Key dienkripsi menggunakan Android Keystore di perangkat, tidak tertanam di source, dan tidak masuk backup JSON. Konteks latihan dibagikan setelah izin pengguna; usulan perubahan ditinjau sebelum diterapkan.

## Status pengujian

Revisi UI telah diterapkan ke Compose. Build, unit checks, lint, pemeriksaan aset, dan alur utama pada emulator telah diperiksa. Pengujian perangkat nyata untuk layar terkunci, battery saver, lifecycle/OEM, TalkBack penuh, dan request AI live masih diperlukan. HyperIsland belum terintegrasi.

## Persiapan GitHub

Ikuti [SECURITY.md](SECURITY.md) sebelum commit atau push. Repo memuat source, Gradle Wrapper, schema Room, tes, artwork runtime, dan lisensi font. Catatan kerja pribadi, folder desain/referensi, screenshot pengujian, data aplikasi, kredensial, dan output build dikecualikan oleh `.gitignore`.

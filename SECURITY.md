# Persiapan upload GitHub

Upload dari folder **GYMProject**, bukan workspace induknya. Gunakan Git agar `.gitignore` diterapkan; upload folder lewat browser tidak menjalankan aturan ignore atau hooks. Arsip kandidat yang sudah diperiksa tersedia secara lokal di `tmp/GYMProject-github-ready.zip` setelah persiapan pertama.

## File yang boleh masuk

Source `app/src`, schema Room di `app/schemas` (struktur, bukan database pengguna), konfigurasi build, Gradle Wrapper termasuk JAR-nya, tes dengan data buatan, artwork runtime, dan lisensi Poppins diperlukan untuk membangun aplikasi.

`.gitignore` mengecualikan SDK path/IDE/cache, build/APK, `.env`, signing keys/password files, service-account config, database, preferences, backup/ekspor, log/arsip, `design/`, `tmp/`, dan catatan kerja `AGENTS.md`, `DESIGN.md`, serta `FIGMA_BRIEF.md`. Semua file lokal tetap tersedia; aturan ignore tidak menghapusnya.

Jangan memasukkan key Groq ke Kotlin, Gradle, resources, contoh konfigurasi, screenshot, atau issue. Masukkan key melalui aplikasi. Simpan data pribadi baru dalam `backups/`, `exports/`, atau `device-data/`; hindari menaruhnya di `app/src`.

## Pemeriksaan sebelum commit dan push

Gunakan [Gitleaks](https://github.com/gitleaks/gitleaks#installing) versi 8.30.1 atau lebih baru. Pada checkout lokal yang disiapkan pertama kali, executable 8.30.1 berada di `tmp/security-tools/gitleaks.exe`, dengan SHA-256 arsip yang telah diverifikasi. Executable ini tidak masuk repo. Pada mesin baru, pasang Gitleaks di PATH lalu aktifkan hooks:

```sh
git config --local core.hooksPath .githooks
```

Hook commit memeriksa isi **staged**, termasuk commit pertama, dan menolak file ignored yang dipaksakan masuk index. Hook push memeriksa seluruh riwayat lokal dan nama file pribadi yang pernah masuk commit. Keduanya menghentikan proses jika scanner tidak tersedia; nilai temuan disamarkan dengan `--redact=100`.

Pengujian pengaman dengan fixture sintetis (memerlukan Python 3, Git, dan Gitleaks): `python scripts/check_git_safety.py`. Script hanya membuat repository uji di `tmp/`; repository aplikasi tidak diubah.

Pemeriksaan manual pada PowerShell setelah staging:

```powershell
gi
git ls-files --cached --ignored --exclude-standard
& .\tmp\security-tools\gitleaks.exe git --pre-commit --staged --config .gitleaks.toml --redact=100 --no-banner --ignore-gitleaks-allow .
git diff --cached --stat
```

Daftar ignored yang tracked harus kosong dan scanner harus selesai dengan exit code 0. Review daftar file sebelum commit, termasuk nama file dan artwork. Pada mesin dengan Gitleaks di PATH, ganti executable lokal dengan `gitleaks`.

Identitas author juga tersimpan dalam commit. Jika email pribadi tidak ingin terlihat, gunakan alamat `noreply` yang tercantum pada Settings → Emails akun GitHub sebagai `user.email` lokal sebelum commit pertama; lihat [panduan email commit GitHub](https://docs.github.com/en/account-and-profile/how-tos/email-preferences/setting-your-commit-email-address).

Setelah commit, periksa riwayat sebelum push:

```powershell
& .\tmp\security-tools\gitleaks.exe git --log-opts=--all --config .gitleaks.toml --redact=100 --no-banner --ignore-gitleaks-allow .
```

Scanner tidak menjamin mendeteksi semua secret atau informasi pribadi, terutama dalam gambar dan file biner. Hooks juga dapat dilewati dan tidak otomatis aktif pada clone baru. Jangan memakai `git add --force`, `--no-verify`, atau pengecualian scanner untuk melewati temuan yang belum ditinjau.

## Jika ada temuan

Hapus file pribadi dari index dengan `git rm --cached -- <path>` tanpa menghapus file lokal, atau ganti literal credential pada source dengan input runtime. Menambahkan `.gitignore` saja tidak membersihkan file tracked atau riwayat. Jika credential pernah diunggah, cabut/rotasi credential tersebut dan bersihkan riwayat sebelum push berikutnya.

## Audit awal — 3 Oktober 2026

Folder ini belum memiliki repository atau commit saat audit dimulai, sehingga tidak ada riwayat lama untuk dibersihkan. Pemeriksaan awal teks lokal tidak menemukan credential dengan pola yang diperiksa. Source AI menggunakan input runtime dan Android Keystore; backup JSON aplikasi berisi data profil/latihan sehingga tetap bersifat pribadi walaupun tidak menyertakan key AI.

Pengujian hooks dengan data sintetis telah memverifikasi penolakan key pada index walaupun working copy sudah dibersihkan, backup profil dengan nama lain, password signing, file ignored yang dipaksakan, scanner yang hilang, serta key/file pribadi yang sudah dihapus tetapi masih ada di riwayat. Kandidat source lolos Gitleaks tanpa temuan; metadata gambar runtime yang diperiksa tidak memuat EXIF atau field pribadi.

Hasil akhir pemindaian kandidat upload dan daftar file disimpan secara lokal di `tmp/github-security-audit.json`. Ulangi pemeriksaan setelah menambahkan atau mengubah file.

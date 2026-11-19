# JefzEQ v0.8

Parametric stereo EQ (L/R terpisah), tone knobs, compressor, reverb/echo, RTA, generator tes.

## Build APK
**GitHub (tanpa Android Studio):**
1. Buat repo baru, upload seluruh isi folder ini (termasuk folder `.github`).
2. Tab **Actions** > **Build APK** > tunggu hijau.
3. Unduh artifact `JefzEQ-apk` > ekstrak > install `JefzEQ.apk` (izinkan "install dari sumber tidak dikenal").

**Android Studio:** Open folder > Build > Build APK(s).

## Pemakaian singkat
1. Nyalakan switch utama (kanan atas), lalu putar YouTube.
2. Tab **Bands** / **Tone** / **Master**: atur EQ.
3. Tab **FX**: nyalakan "Capture app", pilih app (YouTube), lalu Reverb/Echo. Chip FX di tab Tone memilih band yang dikirim ke efek.
4. Chip **RTA** (atas) menyalakan analyzer mikrofon. Tab **RTA**: opsi tampilan + spectrogram.
5. Tab **Test**: Pink Noise dan Tone 0-25 kHz.

## Izin
Rekam audio (RTA + capture internal), notifikasi, layanan foreground, capture layar/audio (dialog sistem untuk capture app).

## Baru di v0.6
- Tab bisa digeser kiri/kanan, tab bar di bawah.
- Tone: knob Vocal L/R (200 Hz-4 kHz). Master: slider vertikal level channel L/R.
- Preset (tab Preset): simpan/muat/hapus, import/export file JSON.
- FX tab tidak lagi lag (daftar app dimuat di background).

## Baru di v0.7
- Tombol Reset di tiap bagian (per band, Bands, Tone, Vocal, Master, FX, Test, RTA) + Reset Semua (tab Preset).
- Compressor per band (tombol "Compressor" di tiap band). Default band: kosong.
- Kunci Password saat pertama dibuka setelah install. Lupa password: pakai Kode Pemulihan.

## Baru di v0.8
- Update tanpa uninstall: APK kini ditandatangani dengan keystore tetap (`app/jefz.keystore`, ikut diupload ke repo). Build v0.8 ke atas bisa dipasang langsung di atas versi sebelumnya. (Pemasangan pertama dari v0.7 lama tetap butuh uninstall satu kali karena kunci tanda tangannya beda.)
- Status bar (notifikasi): tombol On/Off, Vol -10 / Vol +10, dan info preset yang dipakai (tanda "diubah" bila sudah berbeda dari preset).
- PIN diganti Password. Kode Pemulihan tidak berubah.
- Mode Cascade tidak lagi dibatasi 24 dB: gain tiap band dijumlahkan (mis. +5 dB + +5 dB di 1 kHz = +10 dB).
- RTA: sumbu EQ otomatis melebar sampai +-30 dB; garis merah putus-putus "CLIP +20" dan kurva memerah di atas +20 dB.
- Nama preset aktif tampil di header aplikasi.

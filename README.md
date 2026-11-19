# JefzEQ v0.6

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

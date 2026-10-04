# Review benchmark Smooth Fix — 4 Oktober 2026

Run `17594b0d-8b98-49e9-a300-3f6d3a906dc6`; build pengguna `1.0.5-perf-r2-hotfix2`. Sumber: satu JSON server dan sepuluh JSON client yang diunggah. Semua tahap selesai dalam 714.6 detik; `playerRestored=true`; Blood Moon Enhanced Celestials dinyatakan aktif sebelum pengukuran.

## Hasil yang benar-benar terukur

Setting seluruh tahap: cap 120 FPS, VSync off, render distance 10, simulation distance 5, framebuffer 1920×1009, Java 21.0.12. Maksimum heap client 4 GiB dan server 2 GiB. Ini bukan total Working Set proses dan tidak membuktikan batas RAM keseluruhan. Tidak ada run sebelum/sesudah dengan scene identik, sehingga manfaat patch lama tidak dapat dihitung dari hasil ini.

| Tahap | Server mean / p99 (ms) | Server max (ms) | Client FPS efektif rata-rata | Client interval p99 (ms) | Kerja sebelum present >8,33 ms | Entity loaded awal → akhir |
|---|---:|---:|---:|---:|---:|---:|
| baseline | 4.49 / 8.04 | 17.20 | 118.0 | 9.35 | 0.16% | 1 → 1 |
| village_32 | 6.62 / 11.75 | 23.20 | 117.8 | 9.36 | 0.23% | 33 → 33 |
| mobs_16 | 9.68 / 22.90 | 41.59 | 117.6 | 9.69 | 1.93% | 47 → 89 |
| mobs_32 | 11.91 / 22.06 | 320.09 | 116.9 | 10.56 | 5.56% | 64 → 111 |
| mobs_64 | 15.26 / 29.28 | 47.35 | 114.7 | 12.50 | 12.81% | 88 → 169 |
| positive_effects_64 | 16.07 / 28.81 | 111.06 | 114.6 | 12.62 | 11.62% | 93 → 169 |
| blood_moon | 19.82 / 36.85 | 43.82 | 113.7 | 13.49 | 12.25% | 88 → 159 |
| wither_10 | 6.93 / 14.04 | 100.83 | 117.9 | 9.35 | 0.38% | 19 → 41 |
| teleport | 3.93 / 8.15 | 12.29 | 118.0 | 9.34 | 0.20% | 1 → 1 |
| exploration | 10.20 / 23.38 | 35.84 | 117.6 | 9.70 | 1.63% | 9 → 105 |

FPS efektif di tabel = 1000/mean interval antarframe; nilainya mencakup cap/presentation/wait. Interval yang sedikit melewati 8,33 ms belum tentu berarti kerja render melebihi budget. Statistik kerja sebelum present adalah wall-clock render thread sebelum present, bukan pengukuran CPU hardware murni atau GPU. Sampel stack bukan persentase penggunaan CPU. p99 interval bukan rata-rata 1% low FPS.

## Temuan dan tindak lanjut

1. **Masalah transisi lama sudah terselesaikan pada run ini.** Seluruh sepuluh tahap selesai dan Blood Moon aktif. Laporan ini tidak menunjukkan benchmark abort atau membuktikan adanya crash aplikasi. Jika aplikasi keluar sesudahnya, penyebabnya tidak termuat dalam sebelas JSON ini.
2. **Beban entity menurunkan kestabilan frame.** Blood Moon menghasilkan mean kerja sebelum present 5,58 ms, p99 13,16 ms dan sekitar 12,25% frame kerja melebihi budget 8,33 ms. 64 mob mencapai sekitar 12,81%. Angka rata-rata yang dekat cap tidak berarti 120 FPS stabil.
3. **Spike server di jalur penyimpanan.** Tahap mobs_32 memiliki maksimum 320,09 ms. Sampel slow-tick tahap ini dominan melalui `ChunkSerializer.serialize`, `ThreadedAnvilChunkStorage.save` dan `PersistentStateManager.save`, termasuk codec/NBT/GZIP/file writes. Tahap Wither juga memiliki sampel save. Ini mendukung keterlibatan penyimpanan, tetapi tidak memisahkan biaya CPU serialisasi dari disk, antivirus atau kondisi storage. Statistik GC Old Generation tidak bertambah pada tahap mobs_32; Full GC bukan bukti penyebab spike 320 ms tersebut.
4. **Celah pengukuran server.** Pada eksplorasi, counter G1 Old Generation bertambah 1 collection dan 439 ms, sementara maksimum callback tick hanya 35,84 ms. Tidak tersedia timeline yang menghubungkan GC dengan frame/tick tertentu. Callback START/END saja tidak mencakup seluruh pacing server, task/jeda di luar callback, dan interval batas sesi. Laporan lama tidak cukup untuk menyatakan bahwa seluruh tahap bebas jeda panjang.
5. **Client memiliki overhead render dan inventory compatibility.** Dari 631 sampel slow-frame, 112 memuat `xaero.hud.minimap.module.MinimapRenderer.render`, 44 memuat `ImmutableDelegatingMap.entrySet`. Hitungan memakai satu kemunculan per stack; beberapa frame nama yang sama dapat muncul berulang di satu stack. Banyak sampel render melewati panggilan OpenGL. Kehadiran hook Xaero/Iris/Sodium pada stack tidak membuktikan mod tersebut adalah akar semua lag atau biaya GPU tertentu.
6. **Trinkets memiliki pekerjaan sementara yang bisa dikurangi secara terverifikasi.** TC Layer beta.14 membangun stream mapping dan collector baru pada setiap `entrySet`, kemudian dipakai berulang oleh pemeriksaan equipment. Revisi mengganti ekspresi bytecode yang terverifikasi dengan traversal spliterator + collector JDK yang sama, mempertahankan snapshot eager, urutan, mapping, null rejection dan immutability. Nilai inventory tetap dihitung ulang setiap panggilan.
7. **Jumlah entity total tidak sama dengan target mob.** Contohnya tahap 64 mob memiliki 88→169 loaded entities. JSON lama tidak memisahkan actor, drop, projectile atau natural spawns. Revisi mencatat entity menurut jenis dan actor/opponent yang hidup pada batas sesi agar run berikutnya dapat dibandingkan dengan jelas. Tidak ada bukti kebocoran hanya dari kenaikan total ini.

### GC per tahap (selisih counter client/server)

| Tahap | Client Young / Concurrent / Old (ms) | Server Young / Concurrent / Old (ms) |
|---|---:|---:|
| baseline | 67 / 19 / 0 | 9 / 9 / 0 |
| village_32 | 107 / 30 / 0 | 12 / 20 / 0 |
| mobs_16 | 102 / 23 / 0 | 37 / 18 / 0 |
| mobs_32 | 173 / 24 / 0 | 68 / 24 / 0 |
| mobs_64 | 198 / 19 / 0 | 64 / 23 / 0 |
| positive_effects_64 | 186 / 18 / 0 | 93 / 24 / 0 |
| blood_moon | 199 / 25 / 0 | 130 / 42 / 0 |
| wither_10 | 160 / 16 / 0 | 41 / 19 / 0 |
| teleport | 125 / 14 / 0 | 59 / 16 / 0 |
| exploration | 111 / 31 / 0 | 731 / 182 / 439 |

Concurrent collector time bukan durasi pause stop-the-world; jangan menjumlahkan ketiganya sebagai total waktu freeze. Heap client naik-turun dan tidak ada Full GC client selama sepuluh tahap. Data ini tidak cukup untuk menyatakan memory leak atau OOM.

## Revisi yang dikirim

Public version tetap **1.0.5-1.20.1**; build internal **1.0.5-perf-r2-review1**.

- Optimasi eager inventory snapshot TC Layer melalui ekspresi yang diperiksa; fallback jika bytecode upstream berubah. Toggle `trinketsSnapshotAllocationFix` default true.
- `tickStartIntervals`: pacing server awal-tick ke awal-tick, termasuk sleep normal sekitar 50 ms, queued work dan jeda JVM. Over 50 ms kecil dapat berasal dari scheduler jitter; ini bukan durasi kerja tick. First/last partial interval dikecualikan.
- `delayedTickBoundaryStacks`: sampler tambahan untuk server thread runnable di luar callback yang terlambat >100 ms sejak tick mulai. Thread yang sedang menunggu dikecualikan. GC tidak dapat diatribusikan hanya dari sampling karena sampler juga berhenti saat stop-the-world.
- `garbageCollectorDeltas` per recording client/server, dihitung berdasarkan nama collector dan menangani counter tidak didukung/reset.
- `worldAtStart/worldAtEnd`: dimension, posisi, loaded chunk counters, entity menurut jenis, actor utama/opponent hidup. Ini snapshot batas pengukuran; belum mengukur generation latency, chunk baru/disk reload atau puncak sepanjang tahap.
- Batas cakupan arena tertulis langsung di laporan dan panduan.

## Verifikasi revisi

- Build/remap Java 17 melalui Gradle 9.6.1, Loom 1.17.21, JDK 21.
- Bytecode TC Layer beta.14 asli: verifier, transform idempotent dan skip setelah ekspresi collector diubah. Regresi Bewitchment 10.000 kasus, regex 10.007 kasus serta timing 100.000 sampel tetap lulus.
- Headless produksi client dan server: 1.000 kasus snapshot dari kelas upstream yang benar-benar ditransformasi; hasil dan urutan set, snapshot setelah backing map berubah, immutable entries dan null rejection cocok.
- Uji cadence sintetis: satu delay tambahan 439 ms menghasilkan interval 489 ms, dan GC counter 150→589 ms menghasilkan delta 439 ms.
- Fixture server: seluruh sepuluh tahap, Blood Moon aktif, rincian entity cocok dengan total, chunk/cadence/GC tersedia, stop/timeout/recovery serta penyimpanan seluruh dimensi lulus. Fixture mempercepat timestamp dan menyimulasikan readiness; bukan pengukuran FPS graphical modpack.
- Microbenchmark helper (24 elemen, 100.000 panggilan ×3, JDK21) mengukur alokasi sekitar 2.216–2.265 byte/panggilan pada pipeline asal dan 2.032 byte pada revisi, sekitar 8–10% lebih rendah untuk operasi ini. Timing berubah antartrial; angka ini bukan peningkatan FPS modpack.

## Mengapa arena ini belum mewakili world asli

Mayoritas tahap berlangsung di flat arena. Village berarti 32 villager dan workstation; tidak ada bangunan desa lengkap. Wither memakai AI/explosion asli tetapi lantai bedrock tidak bisa dihancurkan. Eksplorasi menggunakan noise overworld generator di custom dimension, terbang y=180 sekitar 8 blok/detik; pengulangan rute memakai chunk yang sudah dibuat. Hook/tag dimension khusus overworld dapat menyebabkan struktur/fitur mod berbeda. Hasil ini berguna untuk regresi entity dan transisi, tetapi belum mencakup gameplay modpack keseluruhan.

Suite lanjutan yang diminta pengguna **belum diimplementasikan dalam revisi ini**. Cakupannya perlu memisahkan: world asli yang sudah dimuat, cold/new-chunk exploration, struktur mod yang benar-benar terverifikasi, chunk load vs generation/save, chest/Lootr open/loot, inventory open/sort/search, serta EMI query/index/recipe. Tiap skenario memerlukan indikator keberhasilan aksi, konteks world/seed/mod/settings dan fase warm/cold, agar fitur yang tidak aktif tidak dilabeli sebagai tes lulus.

## Langkah pengujian sekarang

Ganti JAR lama pada client dan server, lalu restart keduanya. Jangan memasang dua JAR Smooth Fix sekaligus. ZIP import PC/server lama masih berisi JAR sebelum revisi ini.

Untuk pengukuran representatif sambil menunggu suite lanjutan, gunakan salinan world modpack asli dan jalankan `/smoothfix profile 120` (server) serta `/smoothfixc profile 120` (client). Lakukan rute/aksi yang sama untuk satu sesi khusus desa/struktur, satu sesi chunk baru, dan satu sesi inventory/EMI. Profiler manual tetap merekam saat inventory/EMI dibuka; automatic stress suite sengaja menghentikan run saat menu dibuka. Kirim kedua report dan latest.log jika ada aplikasi keluar.

Spike save/GC dan sumber stall OpenGL belum dinyatakan hilang oleh revisi ini; diagnosis lanjutan membutuhkan pacing baru dan profil world asli. Tidak ada perubahan konten, kualitas visual, AI normal-play, speed-validation, RAM launcher atau daftar mod.

# Pembaca Putusan Pengadilan

Aplikasi desktop berbasis Java Swing untuk membaca file PDF putusan pengadilan (contoh: Direktori Putusan Mahkamah Agung RI). Teks diekstrak dengan Apache PDFBox, dibersihkan dari watermark dan footer, lalu ditampilkan dalam antarmuka yang rapi dan mudah dibaca.

## Fitur

- Ekstraksi teks PDF per halaman, disimpan dengan class generic `PdfContent<K, V>`.
- Penghapusan watermark diagonal dan footer disclaimer secara otomatis.
- Tab **Ringkasan**: identitas terdakwa, pasal, tuntutan jaksa, putusan hakim, denda, dan amar putusan.
- Tab **Baca Lengkap**: teks ditata per paragraf dengan judul dan penekanan kata kunci.
- Daftar halaman di sisi kiri untuk berpindah halaman dengan cepat.
- Pencarian kata dengan penyorotan hasil.
- Salin ke clipboard dan simpan ke file TXT.
- Mode gelap/terang dan pengaturan ukuran huruf.

## Teknologi

- Java 26 (sesuaikan dengan JDK yang terpasang)
- Maven
- Apache PDFBox 3.0.3
- Java Swing

## Cara Menjalankan

1. Clone repository:
```bash
   git clone https://github.com/dimasandreansaputra10-max/pembaca-putusan-pdf.git
```
2. Buka folder project di IntelliJ IDEA dan tunggu Maven selesai memuat dependency.
3. Letakkan file PDF putusan di root project, atau pilih lewat tombol **Buka PDF** di aplikasi.
4. Jalankan `Main.java`.

## Catatan

- File PDF putusan tidak disertakan di repository karena memuat data pribadi.
- Fitur ringkasan memakai pola teks khas putusan Pengadilan Negeri. Pada format dokumen lain, sebagian kolom ringkasan bisa berisi "-", sedangkan tab **Baca Lengkap** tetap berfungsi.

## Pembuat

Dimas Andrean Saputra
Teknik Informatika, Universitas Muhammadiyah Malang

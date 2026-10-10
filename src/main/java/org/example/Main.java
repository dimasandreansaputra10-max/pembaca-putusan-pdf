package org.example;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Wadah generic: K = tipe kunci, V = tipe nilai. Contoh: PdfContent<Integer, String> (halaman -> teks). */
class PdfContent<K, V> {
    private final Map<K, V> data = new LinkedHashMap<>();

    public void put(K key, V value) { data.put(key, value); }
    public V get(K key) { return data.get(key); }
    public int size() { return data.size(); }
    public Iterable<Map.Entry<K, V>> entries() { return data.entrySet(); }
}

/** Stripper yang mengabaikan teks miring (watermark diagonal). */
class StripperLurus extends PDFTextStripper {
    public StripperLurus() throws IOException { super(); }

    @Override
    protected void processTextPosition(TextPosition text) {
        Matrix m = text.getTextMatrix();
        boolean miring = Math.abs(m.getShearX()) > 0.01f || Math.abs(m.getShearY()) > 0.01f;
        if (miring) return;
        super.processTextPosition(text);
    }
}

/** Tombol bulat dengan efek hover. */
class TombolModern extends JButton {
    private Color dasar, hover;
    private boolean over = false;

    TombolModern(String teks, Color dasar, Color hover, Color fg) {
        super(teks);
        this.dasar = dasar;
        this.hover = hover;
        setForeground(fg);
        setFont(new Font("SansSerif", Font.BOLD, 13));
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { over = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { over = false; repaint(); }
        });
    }

    void setWarna(Color dasar, Color hover, Color fg) {
        this.dasar = dasar;
        this.hover = hover;
        setForeground(fg);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(over ? hover : dasar);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        g2.dispose();
        super.paintComponent(g);
    }
}

/** Header dengan warna gradasi biru. */
class HeaderGradasi extends JPanel {
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new GradientPaint(0, 0, new Color(0x1E3A8A), getWidth(), 0, new Color(0x2563EB)));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }
}

public class Main extends JFrame {

    private static final String DEFAULT_PDF = "putusan_1012_pid.sus_2026_pn_sby_20260928093625.pdf";

    // Pola untuk memecah paragraf
    private static final Pattern MARKER = Pattern.compile("^(\\d{1,2}\\.\\s|[a-z]\\.\\s|-\\s|=\\s)");
    private static final Pattern KATA = Pattern.compile(
            "^(Menimbang|Mengingat|Setelah|Ad\\.\\d|Kesimpulan|Keadaan|Dengan total|Terdakwa ditahan|Demikian|Dirampas|Dimusnahkan)");
    private static final Pattern KV = Pattern.compile("^[A-Z][\\p{L}/ ]{2,30}\\s:\\s.*");
    private static final Pattern TEBAL = Pattern.compile(
            "^(Menimbang|Mengingat|Setelah|Kesimpulan|Keadaan yang memberatkan|Keadaan yang meringankan|Ad\\.\\d+\\.|Bahwa)");

    // Komponen
    private final JLabel lblFile = new JLabel("Belum ada file");
    private final JLabel labelStatus = new JLabel("Siap.");
    private final JLabel lblDaftar = new JLabel("DAFTAR HALAMAN");
    private final JLabel lblUkuran = new JLabel("Ukuran huruf");
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> listHalaman = new JList<>(listModel);
    private final JEditorPane paneBaca = buatPane();
    private final JEditorPane paneRingkas = buatPane();
    private final JScrollPane scrollBaca = new JScrollPane(paneBaca);
    private final JScrollPane scrollRingkas = new JScrollPane(paneRingkas);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField fieldCari = new JTextField();
    private final JPanel sidebar = new JPanel(new BorderLayout());
    private final JPanel panelUkuran = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
    private final JPanel panelTengah = new JPanel(new BorderLayout());
    private final JPanel panelCari = new JPanel(new BorderLayout(8, 0));
    private final JPanel panelStatus = new JPanel(new BorderLayout());
    private TombolModern btnCari, btnKecil, btnBesar, btnTema;

    // Data
    private PdfContent<Integer, String> halaman;
    private String namaFile = "";
    private String teksSemua = "";
    private String teksSaatIni = "";
    private boolean sedangMengisi = false;
    private boolean gelap = false;
    private int ukuran = 13;
    private int posCari = 0;
    private String kataTerakhir = "";

    // Warna tema
    private Color cBg, cKartu, cTeks, cMuted, cAksen, cAksenSoft, cGaris;

    public Main() {
        super("Pembaca Putusan Pengadilan");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 780);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        bangunUi();
        terapkanTema();

        File pdf = new File(DEFAULT_PDF);
        if (pdf.exists()) {
            muatPdf(pdf);
        } else {
            labelStatus.setText("File default tidak ditemukan. Klik 'Buka PDF'.");
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }

    private static JEditorPane buatPane() {
        JEditorPane p = new JEditorPane();
        p.setContentType("text/html");
        p.setEditable(false);
        p.setBorder(BorderFactory.createEmptyBorder(16, 32, 16, 32));
        return p;
    }

    // ================= Membangun tampilan =================

    private void bangunUi() {
        // ---- Header ----
        HeaderGradasi header = new HeaderGradasi();
        header.setLayout(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 22));

        JLabel lblJudul = new JLabel("Pembaca Putusan Pengadilan");
        lblJudul.setFont(new Font("SansSerif", Font.BOLD, 21));
        lblJudul.setForeground(Color.WHITE);
        lblFile.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblFile.setForeground(new Color(0xBFDBFE));
        JPanel judul = new JPanel(new GridLayout(2, 1, 0, 2));
        judul.setOpaque(false);
        judul.add(lblJudul);
        judul.add(lblFile);

        Color putih = Color.WHITE;
        Color kaca = new Color(255, 255, 255, 45);
        Color kacaHover = new Color(255, 255, 255, 90);
        TombolModern btnBuka = new TombolModern("Buka PDF", putih, new Color(0xE0E7FF), new Color(0x1E3A8A));
        TombolModern btnSalin = new TombolModern("Salin", kaca, kacaHover, putih);
        TombolModern btnSimpan = new TombolModern("Simpan TXT", kaca, kacaHover, putih);
        btnTema = new TombolModern("Mode Gelap", kaca, kacaHover, putih);

        btnBuka.addActionListener(e -> pilihFile());
        btnSalin.addActionListener(e -> salin());
        btnSimpan.addActionListener(e -> simpan());
        btnTema.addActionListener(e -> { gelap = !gelap; terapkanTema(); });

        JPanel kanan = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        kanan.setOpaque(false);
        kanan.add(btnBuka);
        kanan.add(btnSalin);
        kanan.add(btnSimpan);
        kanan.add(btnTema);

        header.add(judul, BorderLayout.WEST);
        header.add(kanan, BorderLayout.EAST);

        // ---- Sidebar ----
        lblDaftar.setFont(new Font("SansSerif", Font.BOLD, 11));
        lblDaftar.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 8));

        listHalaman.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listHalaman.setFixedCellHeight(34);
        listHalaman.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean sel, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
                l.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 8));
                l.setFont(new Font("SansSerif", sel ? Font.BOLD : Font.PLAIN, 13));
                l.setOpaque(true);
                l.setBackground(sel ? cAksen : cKartu);
                l.setForeground(sel ? Color.WHITE : cTeks);
                return l;
            }
        });
        listHalaman.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !sedangMengisi && halaman != null) {
                tabs.setSelectedIndex(1);
                renderBaca();
            }
        });
        JScrollPane scrollList = new JScrollPane(listHalaman);
        scrollList.setBorder(BorderFactory.createEmptyBorder());

        lblUkuran.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnKecil = new TombolModern("A-", Color.GRAY, Color.DARK_GRAY, Color.WHITE);
        btnBesar = new TombolModern("A+", Color.GRAY, Color.DARK_GRAY, Color.WHITE);
        btnKecil.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        btnBesar.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        btnKecil.addActionListener(e -> ubahUkuran(-1));
        btnBesar.addActionListener(e -> ubahUkuran(1));
        panelUkuran.add(lblUkuran);
        panelUkuran.add(btnKecil);
        panelUkuran.add(btnBesar);

        sidebar.setPreferredSize(new Dimension(230, 0));
        sidebar.add(lblDaftar, BorderLayout.NORTH);
        sidebar.add(scrollList, BorderLayout.CENTER);
        sidebar.add(panelUkuran, BorderLayout.SOUTH);

        // ---- Bagian tengah: pencarian + tab ----
        fieldCari.setFont(new Font("SansSerif", Font.PLAIN, 14));
        fieldCari.addActionListener(e -> cari());
        btnCari = new TombolModern("Cari", Color.GRAY, Color.DARK_GRAY, Color.WHITE);
        btnCari.addActionListener(e -> cari());
        JLabel lblCari = new JLabel("Cari kata:");
        lblCari.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblCari.setName("lblCari");

        panelCari.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        panelCari.add(lblCari, BorderLayout.WEST);
        panelCari.add(fieldCari, BorderLayout.CENTER);
        panelCari.add(btnCari, BorderLayout.EAST);
        ((BorderLayout) panelCari.getLayout()).setHgap(10);

        scrollBaca.setBorder(BorderFactory.createEmptyBorder());
        scrollRingkas.setBorder(BorderFactory.createEmptyBorder());
        scrollBaca.getVerticalScrollBar().setUnitIncrement(24);
        scrollRingkas.getVerticalScrollBar().setUnitIncrement(24);

        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        tabs.addTab("Ringkasan", scrollRingkas);
        tabs.addTab("Baca Lengkap", scrollBaca);

        panelTengah.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        panelTengah.add(panelCari, BorderLayout.NORTH);
        panelTengah.add(tabs, BorderLayout.CENTER);

        // ---- Status ----
        labelStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        panelStatus.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        panelStatus.add(labelStatus, BorderLayout.WEST);

        getContentPane().setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(sidebar, BorderLayout.WEST);
        add(panelTengah, BorderLayout.CENTER);
        add(panelStatus, BorderLayout.SOUTH);
    }

    private void terapkanTema() {
        if (gelap) {
            cBg = new Color(0x111827); cKartu = new Color(0x1F2937); cTeks = new Color(0xE5E7EB);
            cMuted = new Color(0x9CA3AF); cAksen = new Color(0x3B82F6); cAksenSoft = new Color(0x1E3A5F);
            cGaris = new Color(0x374151);
        } else {
            cBg = new Color(0xF4F6FA); cKartu = Color.WHITE; cTeks = new Color(0x1F2937);
            cMuted = new Color(0x6B7280); cAksen = new Color(0x2563EB); cAksenSoft = new Color(0xDBEAFE);
            cGaris = new Color(0xE5E7EB);
        }
        getContentPane().setBackground(cBg);

        sidebar.setBackground(cKartu);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, cGaris));
        lblDaftar.setForeground(cMuted);
        lblUkuran.setForeground(cMuted);
        panelUkuran.setBackground(cKartu);
        listHalaman.setBackground(cKartu);
        listHalaman.setForeground(cTeks);

        panelTengah.setBackground(cBg);
        panelCari.setBackground(cBg);
        for (Component c : panelCari.getComponents()) {
            if ("lblCari".equals(c.getName())) c.setForeground(cTeks);
        }
        fieldCari.setBackground(cKartu);
        fieldCari.setForeground(cTeks);
        fieldCari.setCaretColor(cTeks);
        fieldCari.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(cGaris, 1, true), BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        Color hoverAksen = gelap ? cAksen.brighter() : cAksen.darker();
        btnCari.setWarna(cAksen, hoverAksen, Color.WHITE);
        btnKecil.setWarna(cAksen, hoverAksen, Color.WHITE);
        btnBesar.setWarna(cAksen, hoverAksen, Color.WHITE);
        btnTema.setText(gelap ? "Mode Terang" : "Mode Gelap");

        tabs.setBackground(cBg);
        scrollBaca.getViewport().setBackground(cKartu);
        scrollRingkas.getViewport().setBackground(cKartu);
        paneBaca.setBackground(cKartu);
        paneRingkas.setBackground(cKartu);

        panelStatus.setBackground(cKartu);
        panelStatus.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, cGaris),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)));
        labelStatus.setForeground(cMuted);
        listHalaman.repaint();

        if (halaman != null) {
            renderRingkas();
            renderBaca();
        }
    }

    private void ubahUkuran(int delta) {
        int baru = Math.max(9, Math.min(24, ukuran + delta));
        if (baru == ukuran) return;
        ukuran = baru;
        if (halaman != null) {
            renderRingkas();
            renderBaca();
        }
        labelStatus.setText("Ukuran huruf: " + ukuran);
    }

    // ================= Aksi tombol =================

    private void pilihFile() {
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setFileFilter(new FileNameExtensionFilter("File PDF", "pdf"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            muatPdf(fc.getSelectedFile());
        }
    }

    /** Teks yang akan disalin/disimpan: seleksi mouse jika ada, jika tidak seluruh tampilan aktif. */
    private String teksAktif() {
        boolean ringkas = tabs.getSelectedIndex() == 0;
        JEditorPane pane = ringkas ? paneRingkas : paneBaca;
        String sel = pane.getSelectedText();
        if (sel != null && !sel.isEmpty()) return sel;
        if (ringkas) {
            try {
                Document d = paneRingkas.getDocument();
                return d.getText(0, d.getLength()).trim();
            } catch (BadLocationException e) {
                return "";
            }
        }
        return teksSaatIni;
    }

    private void salin() {
        String teks = teksAktif();
        if (teks.isEmpty()) {
            labelStatus.setText("Belum ada teks untuk disalin.");
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(teks), null);
        labelStatus.setText("Berhasil disalin ke clipboard (tinggal Ctrl+V).");
    }

    private void simpan() {
        String teks = teksAktif();
        if (teks.isEmpty()) {
            labelStatus.setText("Belum ada teks untuk disimpan.");
            return;
        }
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setSelectedFile(new File("hasil_output.txt"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.write(fc.getSelectedFile().toPath(), teks.getBytes(StandardCharsets.UTF_8));
                labelStatus.setText("Disimpan ke: " + fc.getSelectedFile().getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Gagal menyimpan: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void cari() {
        String kata = fieldCari.getText().trim();
        Highlighter hl = paneBaca.getHighlighter();
        hl.removeAllHighlights();
        if (kata.isEmpty()) {
            labelStatus.setText("Ketik kata yang ingin dicari.");
            return;
        }
        if (halaman == null) return;
        tabs.setSelectedIndex(1);
        if (!kata.equals(kataTerakhir)) {
            posCari = 0;
            kataTerakhir = kata;
        }
        try {
            Document d = paneBaca.getDocument();
            String isi = d.getText(0, d.getLength()).toLowerCase();
            String k = kata.toLowerCase();
            DefaultHighlighter.DefaultHighlightPainter painter =
                    new DefaultHighlighter.DefaultHighlightPainter(new Color(253, 224, 71));
            List<Integer> hits = new ArrayList<>();
            int i = isi.indexOf(k);
            while (i >= 0) {
                hits.add(i);
                hl.addHighlight(i, i + k.length(), painter);
                i = isi.indexOf(k, i + k.length());
            }
            if (hits.isEmpty()) {
                labelStatus.setText("Tidak ditemukan: \"" + kata + "\"");
                return;
            }
            int target = hits.get(0);
            for (int h : hits) {
                if (h >= posCari) { target = h; break; }
            }
            posCari = target + 1;
            paneBaca.setCaretPosition(target);
            Rectangle2D r = paneBaca.modelToView2D(target);
            if (r != null) paneBaca.scrollRectToVisible(r.getBounds());
            labelStatus.setText(hits.size() + " hasil untuk \"" + kata + "\". Tekan Enter untuk hasil berikutnya.");
        } catch (BadLocationException ignored) { }
    }

    // ================= Membaca PDF =================

    private void muatPdf(File file) {
        labelStatus.setText("Membaca " + file.getName() + " ...");

        new SwingWorker<PdfContent<Integer, String>, Void>() {
            @Override
            protected PdfContent<Integer, String> doInBackground() throws Exception {
                return bacaHalaman(file);
            }

            @Override
            protected void done() {
                try {
                    halaman = get();
                    namaFile = file.getName();
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<Integer, String> e : halaman.entries()) {
                        sb.append(e.getValue()).append("\n");
                    }
                    teksSemua = sb.toString();

                    sedangMengisi = true;
                    listModel.clear();
                    listModel.addElement("Semua halaman");
                    for (int i = 1; i <= halaman.size(); i++) listModel.addElement("Halaman " + i);
                    listHalaman.setSelectedIndex(0);
                    sedangMengisi = false;

                    lblFile.setText(namaFile + "   |   " + halaman.size() + " halaman");
                    renderRingkas();
                    renderBaca();
                    tabs.setSelectedIndex(0);
                    labelStatus.setText("Selesai membaca " + halaman.size() + " halaman.");
                } catch (Exception ex) {
                    labelStatus.setText("Gagal membaca file.");
                    JOptionPane.showMessageDialog(Main.this, "Gagal membaca PDF:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static PdfContent<Integer, String> bacaHalaman(File file) throws IOException {
        PdfContent<Integer, String> hasil = new PdfContent<>();
        try (PDDocument doc = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new StripperLurus();
            stripper.setSortByPosition(true);
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                hasil.put(i, bersihkan(stripper.getText(doc)));
            }
        }
        return hasil;
    }

    private static String bersihkan(String teks) {
        StringBuilder sb = new StringBuilder();
        for (String baris : teks.split("\\R")) {
            String b = baris.trim();
            if (b.isEmpty()) continue;
            if (b.contains("Direktori Putusan Mahkamah Agung")) continue;
            if (b.contains("putusan.mahkamahagung.go.id")) continue;
            if (b.startsWith("Disclaimer")) continue;
            if (b.startsWith("Kepaniteraan Mahkamah Agung")) continue;
            if (b.startsWith("pelaksanaan fungsi peradilan")) continue;
            if (b.startsWith("Dalam hal Anda menemukan")) continue;
            if (b.startsWith("Email : kepaniteraan")) continue;
            if (b.matches("Halaman \\d+")) continue;
            sb.append(b).append("\n");
        }
        return sb.toString();
    }

    // ================= Membuat tampilan HTML =================

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String head() {
        return "<html><head><style>"
                + "body { font-family: Serif; font-size: " + ukuran + "pt; color: " + hex(cTeks)
                + "; background-color: " + hex(cKartu) + "; margin: 6px; }"
                + "p { margin-top: 0px; margin-bottom: 10px; }"
                + "h2 { font-family: SansSerif; font-size: " + (ukuran + 3) + "pt; margin-top: 14px; margin-bottom: 8px; }"
                + "h3 { font-family: SansSerif; font-size: " + (ukuran + 2) + "pt; margin-top: 18px; margin-bottom: 6px; }"
                + "td { font-size: " + ukuran + "pt; }"
                + "</style></head><body>";
    }

    private void renderBaca() {
        if (halaman == null) return;
        int p = listHalaman.getSelectedIndex();
        StringBuilder raw = new StringBuilder();
        StringBuilder h = new StringBuilder(head());

        if (p <= 0) {
            for (Map.Entry<Integer, String> e : halaman.entries()) {
                raw.append("--- Halaman ").append(e.getKey()).append(" ---\n").append(e.getValue()).append("\n");
                h.append(penandaHalaman(e.getKey())).append(keHtml(e.getValue()));
            }
        } else {
            raw.append("--- Halaman ").append(p).append(" ---\n").append(halaman.get(p));
            h.append(penandaHalaman(p)).append(keHtml(halaman.get(p)));
        }
        h.append("</body></html>");

        teksSaatIni = raw.toString();
        paneBaca.setText(h.toString());
        paneBaca.setCaretPosition(0);
        posCari = 0;
        kataTerakhir = "";
        SwingUtilities.invokeLater(() -> scrollBaca.getVerticalScrollBar().setValue(0));
    }

    private String penandaHalaman(int no) {
        return "<table width='100%' cellpadding='6' cellspacing='0'><tr><td bgcolor='" + hex(cAksenSoft)
                + "'><font color='" + hex(cAksen) + "'><b>HALAMAN " + no + "</b></font></td></tr></table><br>";
    }

    private static boolean isHeading(String b) {
        if (b.length() < 3 || b.length() > 60) return false;
        long huruf = b.chars().filter(Character::isLetter).count();
        return huruf >= 3 && b.equals(b.toUpperCase());
    }

    /** Mengubah teks mentah (baris terpotong) menjadi paragraf HTML yang enak dibaca. */
    private String keHtml(String teks) {
        List<String> daftar = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        for (String baris : teks.split("\\R")) {
            String b = baris.trim();
            if (b.isEmpty()) continue;

            boolean akhirKalimat = cur.length() > 0 && ".;:".indexOf(cur.charAt(cur.length() - 1)) >= 0;
            boolean bisaMulai = cur.length() == 0 || akhirKalimat;

            if (bisaMulai && isHeading(b)) {
                if (cur.length() > 0) { daftar.add("P" + cur); cur.setLength(0); }
                daftar.add("H" + b);
                continue;
            }
            if (cur.length() > 0) {
                boolean pisah = MARKER.matcher(b).find()
                        || (akhirKalimat && (KATA.matcher(b).find() || KV.matcher(b).matches()));
                if (pisah) { daftar.add("P" + cur); cur.setLength(0); }
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(b);
        }
        if (cur.length() > 0) daftar.add("P" + cur);

        StringBuilder h = new StringBuilder();
        for (String d : daftar) {
            String raw = d.substring(1);
            if (d.charAt(0) == 'H') {
                h.append("<h2 align='center'><font color='").append(hex(cAksen)).append("'>")
                        .append(esc(raw)).append("</font></h2>");
                continue;
            }
            boolean bullet = raw.startsWith("- ");
            boolean berdaftar = MARKER.matcher(raw).find();
            boolean kv = !berdaftar && KV.matcher(raw).matches();
            if (bullet) raw = raw.substring(2);

            String isi = esc(raw);
            if (kv) {
                int idx = isi.indexOf(" : ");
                if (idx > 0) isi = "<b>" + isi.substring(0, idx) + "</b>" + isi.substring(idx);
            } else {
                isi = TEBAL.matcher(isi).replaceFirst("<b>$1</b>");
            }
            if (bullet) isi = "&#8226; " + isi;

            String gaya = (bullet || berdaftar) ? " style='margin-left:24px'" : (kv ? " style='margin-left:12px'" : "");
            h.append("<p").append(gaya).append(">").append(isi).append("</p>");
        }
        return h.toString();
    }

    // ================= Ringkasan =================

    private static String ambil(String teks, String regex) {
        Matcher m = Pattern.compile(regex).matcher(teks);
        return m.find() ? m.group(1).trim() : "-";
    }

    private void seksi(StringBuilder h, String judul, String[][] baris) {
        h.append("<h3><font color='").append(hex(cAksen)).append("'>").append(judul).append("</font></h3>");
        h.append("<table width='100%' cellpadding='9' cellspacing='0'>");
        boolean selang = false;
        for (String[] r : baris) {
            h.append("<tr bgcolor='").append(hex(selang ? cBg : cKartu)).append("'>")
                    .append("<td width='30%' valign='top'><font color='").append(hex(cMuted)).append("'><b>")
                    .append(esc(r[0])).append("</b></font></td>")
                    .append("<td valign='top'>").append(esc(r[1])).append("</td></tr>");
            selang = !selang;
        }
        h.append("</table>");
    }

    private void renderRingkas() {
        if (halaman == null) return;
        String datar = teksSemua.replaceAll("\\s+", " ");

        String nomor = ambil(datar, "Nomor\\s+(\\d+/Pid\\.[A-Za-z]+/\\d{4}/PN\\s*\\w+)");
        String pengadilan = ambil(datar, "(Pengadilan Negeri \\w+)");
        String nama = ambil(datar, "Nama lengkap\\s*:\\s*(.+?);");
        String lahir = ambil(datar, "Tanggal lahir\\s*:\\s*(.+?);");
        String tinggal = ambil(datar, "Tempat tinggal\\s*:\\s*(.+?);");
        String kerja = ambil(datar, "Pekerjaan\\s*:\\s*(.+?);");
        String didik = ambil(datar, "Pendidikan\\s*:\\s*(.+?);");
        String pasal = ambil(datar, "(Pasal\\s+\\d+\\s+ayat\\s+\\(\\d+\\))");
        String tuntut = ambil(datar, "pidana penjara selama ([^;]{1,40}?) dikurangi");
        String vonis = ambil(datar, "pidana penjara selama ([^;]{1,60}?) dan Pidana Denda");
        String denda = ambil(datar, "Pidana Denda sebesar (Rp\\s*[\\d.]+(?:,\\d+|,-)?)");
        String tanggal = ambil(datar, "diucapkan dalam sidang terbuka untuk umum pada hari ([A-Za-z]+, tanggal \\d+ \\w+ \\d{4})");
        String berat = ambil(datar, "netto (\\d+,\\d+)");

        StringBuilder h = new StringBuilder(head());
        h.append("<h2 align='center'><font color='").append(hex(cAksen)).append("'>RINGKASAN PERKARA</font></h2>");
        h.append("<p align='center'><font color='").append(hex(cMuted)).append("'>")
                .append(esc(pengadilan)).append("  |  Nomor ").append(esc(nomor)).append("</font></p>");

        seksi(h, "Identitas Terdakwa", new String[][]{
                {"Nama lengkap", nama},
                {"Tanggal lahir / umur", lahir},
                {"Tempat tinggal", tinggal},
                {"Pekerjaan", kerja},
                {"Pendidikan", didik}
        });
        seksi(h, "Hasil Putusan", new String[][]{
                {"Pasal yang dilanggar", pasal + " UU No. 35 Tahun 2009 tentang Narkotika"},
                {"Tuntutan jaksa (penjara)", tuntut},
                {"Putusan hakim (penjara)", vonis},
                {"Denda", denda},
                {"Barang bukti narkotika", "-".equals(berat) ? "-" : berat + " gram"},
                {"Dibacakan pada", tanggal}
        });

        int a = teksSemua.indexOf("M E N G A D I L I");
        int z = teksSemua.indexOf("Demikian diputuskan");
        if (a >= 0) {
            String amar = z > a ? teksSemua.substring(a, z) : teksSemua.substring(a);
            h.append("<h3><font color='").append(hex(cAksen)).append("'>Amar Putusan</font></h3>");
            h.append(keHtml(amar));
        }
        h.append("<p><font color='").append(hex(cMuted)).append("' size='-1'>")
                .append("Ringkasan dibuat otomatis dari teks dokumen. Buka tab 'Baca Lengkap' untuk isi aslinya.")
                .append("</font></p></body></html>");

        paneRingkas.setText(h.toString());
        paneRingkas.setCaretPosition(0);
        SwingUtilities.invokeLater(() -> scrollRingkas.getVerticalScrollBar().setValue(0));
    }
}
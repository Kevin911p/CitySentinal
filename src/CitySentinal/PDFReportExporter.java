package CitySentinal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PDFReportExporter — writes a real, openable PDF using pure Java (no iText needed).
 * Builds a minimal PDF 1.4 structure manually so any PDF reader can open it.
 */
public class PDFReportExporter {

    private final ThreatDAO threatDAO;
    private final ZoneDAO   zoneDAO;

    public PDFReportExporter(ThreatDAO threatDAO, ZoneDAO zoneDAO) {
        this.threatDAO = threatDAO;
        this.zoneDAO   = zoneDAO;
    }

    public void export(File file) throws Exception {
        List<Zone>   zones   = zoneDAO.getAllZones();
        List<Threat> threats = threatDAO.getAllThreats();

        // Build report lines
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("SentinelCity - Threat Report");
        lines.add("Generated: " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        lines.add(" ");
        lines.add("=== ZONES (" + zones.size() + ") ===");
        for (Zone z : zones) {
            lines.add("  [Zone " + z.getZoneId() + "] " + z.getZoneName()
                    + "  |  Threat Level: " + z.getThreatLevel());
        }
        lines.add(" ");
        lines.add("=== THREATS (" + threats.size() + ") ===");

        // Build a fast zone lookup
        java.util.Map<Integer, String> zoneMap = new java.util.HashMap<>();
        for (Zone z : zones) zoneMap.put(z.getZoneId(), z.getZoneName());

        long critical = 0, warning = 0, info = 0, active = 0;
        for (Threat t : threats) {
            String zoneName = zoneMap.getOrDefault(t.getZoneId(), "Unknown");
            lines.add("  [#" + t.getThreatId() + "] "
                    + t.getThreatType()
                    + "  |  Zone: " + zoneName
                    + "  |  Severity: " + t.getSeverity()
                    + "  |  Status: " + t.getStatus()
                    + "  |  " + t.getThreatDate() + " " + t.getThreatTime());
            if ("Critical".equals(t.getSeverity()))    critical++;
            else if ("Warning".equals(t.getSeverity())) warning++;
            else if ("Info".equals(t.getSeverity()))    info++;
            if ("Active".equals(t.getStatus()))         active++;
        }
        lines.add(" ");
        lines.add("=== SUMMARY ===");
        lines.add("  Total threats : " + threats.size());
        lines.add("  Critical       : " + critical);
        lines.add("  Warning        : " + warning);
        lines.add("  Info           : " + info);
        lines.add("  Active now     : " + active);

        writePdf(file, lines);
    }

    // ── Minimal PDF 1.4 writer (no external libraries) ────────────────────
    private void writePdf(File file, List<String> lines) throws Exception {
        // We'll collect each PDF object as a byte array and track offsets for xref
        java.util.List<byte[]> objects = new java.util.ArrayList<>();
        java.util.List<Integer> offsets = new java.util.ArrayList<>();

        // Helper to build a PDF text stream from lines
        // Uses Helvetica (standard font, always available in PDF readers)
        StringBuilder stream = new StringBuilder();
        stream.append("BT\n");
        stream.append("/F1 11 Tf\n");   // font + size
        stream.append("40 780 Td\n");   // starting position (x=40, y=780)
        stream.append("14 TL\n");       // line height

        // Title line — larger
        String title = lines.get(0);
        stream.append("/F1 16 Tf\n");
        stream.append("(").append(escapePdf(title)).append(") Tj\n");
        stream.append("T*\n");
        stream.append("/F1 10 Tf\n");

        int lineCount = 1;
        int page = 1;
        // We'll fit ~52 lines per page (A4 at 14pt leading, 780 start, 40 margin bottom)
        int linesPerPage = 52;

        // For simplicity we write a single-page PDF (truncated at linesPerPage*pages)
        // A proper multi-page PDF needs more complex object references — we keep it simple
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("===")) {
                // Section header — bold-ish (larger size)
                stream.append("/F1 11 Tf\n");
                stream.append("(").append(escapePdf(line)).append(") Tj\n");
                stream.append("T*\n");
                stream.append("/F1 10 Tf\n");
            } else {
                stream.append("(").append(escapePdf(line)).append(") Tj\n");
                stream.append("T*\n");
            }
            lineCount++;
            if (lineCount > linesPerPage * 3) break; // safety cap at 3 pages worth
        }
        stream.append("ET\n");

        byte[] streamBytes = stream.toString().getBytes(StandardCharsets.ISO_8859_1);

        // Build PDF objects
        // Object 1: Catalog
        String obj1 = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n";
        // Object 2: Pages
        String obj2 = "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n";
        // Object 3: Page
        String obj3 = "3 0 obj\n<< /Type /Page /Parent 2 0 R"
                + " /MediaBox [0 0 595 842]"
                + " /Contents 4 0 R"
                + " /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n";
        // Object 4: Content stream
        String obj4Header = "4 0 obj\n<< /Length " + streamBytes.length + " >>\nstream\n";
        String obj4Footer = "\nendstream\nendobj\n";
        // Object 5: Font
        String obj5 = "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica"
                + " /Encoding /WinAnsiEncoding >>\nendobj\n";

        // Write PDF to file
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1);
            fos.write(header);
            int offset = header.length;

            // Write each object and record its offset
            String[] textObjs = {obj1, obj2, obj3};
            for (String obj : textObjs) {
                offsets.add(offset);
                byte[] b = obj.getBytes(StandardCharsets.ISO_8859_1);
                fos.write(b);
                offset += b.length;
            }

            // Object 4 (stream)
            offsets.add(offset);
            byte[] h4 = obj4Header.getBytes(StandardCharsets.ISO_8859_1);
            fos.write(h4);
            offset += h4.length;
            fos.write(streamBytes);
            offset += streamBytes.length;
            byte[] f4 = obj4Footer.getBytes(StandardCharsets.ISO_8859_1);
            fos.write(f4);
            offset += f4.length;

            // Object 5 (font)
            offsets.add(offset);
            byte[] b5 = obj5.getBytes(StandardCharsets.ISO_8859_1);
            fos.write(b5);
            offset += b5.length;

            // xref table
            int xrefOffset = offset;
            StringBuilder xref = new StringBuilder();
            xref.append("xref\n");
            xref.append("0 6\n");
            xref.append("0000000000 65535 f \n"); // object 0 (free)
            for (int off : offsets) {
                xref.append(String.format("%010d 00000 n \n", off));
            }
            xref.append("trailer\n<< /Size 6 /Root 1 0 R >>\n");
            xref.append("startxref\n").append(xrefOffset).append("\n%%EOF\n");

            fos.write(xref.toString().getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    /** Escape special PDF string characters */
    private String escapePdf(String s) {
        if (s == null) return "";
        // Strip non-latin characters (PDF Type1 fonts only support ISO-8859-1)
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == ')') sb.append('\\').append(c);
            else if (c == '\\')       sb.append("\\\\");
            else if (c >= 32 && c <= 126) sb.append(c);
            else if (c == '\t')       sb.append("  ");
            else sb.append(' ');
        }
        return sb.toString();
    }
}
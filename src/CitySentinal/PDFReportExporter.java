package CitySentinal;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PDFReportExporter — generates a professional PDF threat report.
 *
 * Requires Apache PDFBox 3.x on the classpath.
 * Maven / Gradle dependency:
 *
 *   <!-- Maven -->
 *   <dependency>
 *     <groupId>org.apache.pdfbox</groupId>
 *     <artifactId>pdfbox</artifactId>
 *     <version>3.0.2</version>
 *   </dependency>
 *
 *   // Gradle
 *   implementation 'org.apache.pdfbox:pdfbox:3.0.2'
 *
 * Usage from MainDashboard:
 *   PDFReportExporter exporter = new PDFReportExporter(threatDAO, zoneDAO);
 *   exporter.export(outputFile);   // throws IOException on failure
 */
public class PDFReportExporter {

    private final ThreatDAO threatDAO;
    private final ZoneDAO   zoneDAO;

    // Layout constants
    private static final float PAGE_W    = PDRectangle.A4.getWidth();
    private static final float PAGE_H    = PDRectangle.A4.getHeight();
    private static final float MARGIN    = 50f;
    private static final float COL_W    = PAGE_W - 2 * MARGIN;
    private static final float LINE_H   = 16f;
    private static final float ROW_H    = 18f;

    // Brand colours (R, G, B in 0..1 range)
    private static final float[] C_DARK   = {0.06f, 0.09f, 0.14f};  // #0f1623
    private static final float[] C_ACCENT = {0.31f, 0.27f, 0.90f};  // #4f46e5
    private static final float[] C_RED    = {0.94f, 0.27f, 0.27f};  // #ef4444
    private static final float[] C_AMBER  = {0.96f, 0.62f, 0.04f};  // #f59e0b
    private static final float[] C_BLUE   = {0.23f, 0.51f, 0.96f};  // #3b82f6
    private static final float[] C_GRAY   = {0.59f, 0.64f, 0.73f};  // #96a3bb
    private static final float[] C_GREEN  = {0.13f, 0.77f, 0.37f};  // #22c55e
    private static final float[] C_WHITE  = {1f, 1f, 1f};
    private static final float[] C_BLACK  = {0.12f, 0.16f, 0.22f};  // near-black text
    private static final float[] C_LIGHT  = {0.95f, 0.96f, 0.98f};  // table row alt

    public PDFReportExporter(ThreatDAO threatDAO, ZoneDAO zoneDAO) {
        this.threatDAO = threatDAO;
        this.zoneDAO   = zoneDAO;
    }

    public void export(File outputFile) throws IOException {
        List<Threat> threats = threatDAO.getAllThreats();
        List<Zone>   zones   = zoneDAO.getAllZones();

        // Zone lookup map
        Map<Integer, String> zoneNames = new HashMap<>();
        for (Zone z : zones) zoneNames.put(z.getZoneId(), z.getZoneName());

        // Stats
        long critical    = threats.stream().filter(t -> "Critical".equals(t.getSeverity())).count();
        long warning     = threats.stream().filter(t -> "Warning".equals(t.getSeverity())).count();
        long info        = threats.stream().filter(t -> "Info".equals(t.getSeverity())).count();
        long active      = threats.stream().filter(t -> "Active".equals(t.getStatus())).count();
        long resolved    = threats.stream().filter(t -> "Resolved".equals(t.getStatus())).count();
        long investing   = threats.stream().filter(t -> "Investigating".equals(t.getStatus())).count();

        try (PDDocument doc = new PDDocument()) {

            PDFont fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            // ── Page 1 : Cover + Summary ────────────────────────────────────
            PDPage page1 = new PDPage(PDRectangle.A4);
            doc.addPage(page1);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {

                // Dark header band
                fillRect(cs, 0, PAGE_H - 130, PAGE_W, 130, C_DARK);

                // Logo circle
                drawCircle(cs, MARGIN + 18, PAGE_H - 65, 14, C_ACCENT);

                // App title in header
                cs.beginText();
                cs.setFont(fontBold, 20);
                setFillColor(cs, C_WHITE);
                cs.newLineAtOffset(MARGIN + 40, PAGE_H - 55);
                cs.showText("SentinelCity");
                cs.endText();

                cs.beginText();
                cs.setFont(fontRegular, 10);
                setFillColor(cs, C_GRAY);
                cs.newLineAtOffset(MARGIN + 40, PAGE_H - 72);
                cs.showText("Urban Cyber Threat Monitor  ·  Surat Smart City");
                cs.endText();

                // Report date (right side of header)
                String now = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"));
                cs.beginText();
                cs.setFont(fontRegular, 9);
                setFillColor(cs, C_GRAY);
                cs.newLineAtOffset(PAGE_W - MARGIN - 120, PAGE_H - 60);
                cs.showText("Generated: " + now);
                cs.endText();

                // Report title
                cs.beginText();
                cs.setFont(fontBold, 26);
                setFillColor(cs, C_BLACK);
                cs.newLineAtOffset(MARGIN, PAGE_H - 175);
                cs.showText("Threat Intelligence Report");
                cs.endText();

                cs.beginText();
                cs.setFont(fontRegular, 12);
                setFillColor(cs, C_GRAY);
                cs.newLineAtOffset(MARGIN, PAGE_H - 196);
                cs.showText("Automated analysis of all logged threats");
                cs.endText();

                // Accent underline
                fillRect(cs, MARGIN, PAGE_H - 205, COL_W, 2, C_ACCENT);

                // ── Summary stat boxes ────────────────────────────────────
                float boxY  = PAGE_H - 265;
                float boxW  = 100f;
                float boxH  = 64f;
                float boxGap = 16f;
                float startX = MARGIN;

                Object[][] stats = {
                    {"Total",       String.valueOf(threats.size()), C_DARK},
                    {"Critical",    String.valueOf(critical),        C_RED},
                    {"Warning",     String.valueOf(warning),         C_AMBER},
                    {"Info",        String.valueOf(info),            C_BLUE},
                };

                for (Object[] stat : stats) {
                    float[] color = (float[]) stat[2];
                    fillRect(cs, startX, boxY - boxH + 10, boxW, boxH, color);

                    cs.beginText();
                    cs.setFont(fontBold, 24);
                    setFillColor(cs, C_WHITE);
                    cs.newLineAtOffset(startX + 12, boxY - 20);
                    cs.showText((String) stat[1]);
                    cs.endText();

                    cs.beginText();
                    cs.setFont(fontRegular, 9);
                    setFillColor(cs, C_WHITE);
                    cs.newLineAtOffset(startX + 12, boxY - 38);
                    cs.showText((String) stat[0]);
                    cs.endText();

                    startX += boxW + boxGap;
                }

                // ── Status breakdown ──────────────────────────────────────
                float statusY = boxY - boxH - 30;

                cs.beginText();
                cs.setFont(fontBold, 12);
                setFillColor(cs, C_BLACK);
                cs.newLineAtOffset(MARGIN, statusY);
                cs.showText("Status Breakdown");
                cs.endText();

                statusY -= LINE_H + 4;
                fillRect(cs, MARGIN, statusY - 2, COL_W, 1, C_GRAY);
                statusY -= 10;

                Object[][] statuses = {
                    {"Active",         String.valueOf(active),    C_RED},
                    {"Investigating",  String.valueOf(investing),  C_AMBER},
                    {"Resolved",       String.valueOf(resolved),   C_GREEN},
                };
                for (Object[] s : statuses) {
                    float[] c = (float[]) s[2];
                    drawCircle(cs, MARGIN + 6, statusY + 3, 5, c);
                    cs.beginText();
                    cs.setFont(fontRegular, 11);
                    setFillColor(cs, C_BLACK);
                    cs.newLineAtOffset(MARGIN + 18, statusY);
                    cs.showText((String) s[0] + ":  " + (String) s[1]);
                    cs.endText();
                    statusY -= ROW_H;
                }

                // ── Threat table header ───────────────────────────────────
                float tableY = statusY - 30;

                cs.beginText();
                cs.setFont(fontBold, 12);
                setFillColor(cs, C_BLACK);
                cs.newLineAtOffset(MARGIN, tableY);
                cs.showText("All Threats");
                cs.endText();

                tableY -= LINE_H + 4;

                // Column widths: ID, Zone, Type, Severity, Date, Status
                float[] colWidths = {35, 90, 130, 65, 75, 90};
                String[] colHeaders = {"ID", "Zone", "Threat Type", "Severity", "Date", "Status"};

                // Header row
                fillRect(cs, MARGIN, tableY - ROW_H + 4, COL_W, ROW_H, C_DARK);
                float cx = MARGIN + 6;
                for (int i = 0; i < colHeaders.length; i++) {
                    cs.beginText();
                    cs.setFont(fontBold, 8);
                    setFillColor(cs, C_WHITE);
                    cs.newLineAtOffset(cx, tableY - 9);
                    cs.showText(colHeaders[i]);
                    cs.endText();
                    cx += colWidths[i];
                }
                tableY -= ROW_H;

                // Data rows (up to 25 on page 1)
                int maxOnPage1 = 25;
                int row = 0;
                for (Threat t : threats) {
                    if (row >= maxOnPage1) break;
                    if (tableY < MARGIN + 20) break;

                    float[] rowBg = (row % 2 == 0) ? C_WHITE : C_LIGHT;
                    fillRect(cs, MARGIN, tableY - ROW_H + 4, COL_W, ROW_H, rowBg);

                    // Severity colour dot
                    float[] sevColor = severityColor(t.getSeverity());

                    String[] cells = {
                        String.valueOf(t.getThreatId()),
                        truncate(zoneNames.getOrDefault(t.getZoneId(), "?"), 14),
                        truncate(t.getThreatType().replace(" [AUTO-ESCALATED]", "*"), 20),
                        t.getSeverity(),
                        t.getThreatDate(),
                        t.getStatus()
                    };

                    cx = MARGIN + 6;
                    for (int i = 0; i < cells.length; i++) {
                        cs.beginText();
                        cs.setFont(i == 3 ? fontBold : fontRegular, 8);
                        setFillColor(cs, i == 3 ? sevColor : C_BLACK);
                        cs.newLineAtOffset(cx, tableY - 9);
                        cs.showText(cells[i]);
                        cs.endText();
                        cx += colWidths[i];
                    }
                    tableY -= ROW_H;
                    row++;
                }

                // Footer
                cs.beginText();
                cs.setFont(fontRegular, 8);
                setFillColor(cs, C_GRAY);
                cs.newLineAtOffset(MARGIN, 28);
                cs.showText("SentinelCity · Confidential · Page 1   * = Auto-escalated threat");
                cs.endText();

                fillRect(cs, MARGIN, 40, COL_W, 1, C_GRAY);
            }

            // ── Page 2+ : Overflow rows ─────────────────────────────────────
            if (threats.size() > maxOnPage1Ref()) {
                List<Threat> overflow = threats.subList(
                    Math.min(maxOnPage1Ref(), threats.size()), threats.size());
                addOverflowPage(doc, overflow, zoneNames, fontRegular, fontBold);
            }

            doc.save(outputFile);
        }
    }

    private int maxOnPage1Ref() { return 25; }

    private void addOverflowPage(PDDocument doc, List<Threat> threats,
                                  Map<Integer, String> zoneNames,
                                  PDFont fontRegular, PDFont fontBold) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            fillRect(cs, 0, PAGE_H - 50, PAGE_W, 50, C_DARK);
            cs.beginText();
            cs.setFont(fontBold, 13);
            setFillColor(cs, C_WHITE);
            cs.newLineAtOffset(MARGIN, PAGE_H - 30);
            cs.showText("SentinelCity — Threat Report (continued)");
            cs.endText();

            float[] colWidths = {35, 90, 130, 65, 75, 90};
            String[] colHeaders = {"ID", "Zone", "Threat Type", "Severity", "Date", "Status"};
            float tableY = PAGE_H - 75;

            fillRect(cs, MARGIN, tableY - ROW_H + 4, COL_W, ROW_H, C_DARK);
            float cx = MARGIN + 6;
            for (int i = 0; i < colHeaders.length; i++) {
                cs.beginText();
                cs.setFont(fontBold, 8);
                setFillColor(cs, C_WHITE);
                cs.newLineAtOffset(cx, tableY - 9);
                cs.showText(colHeaders[i]);
                cs.endText();
                cx += colWidths[i];
            }
            tableY -= ROW_H;

            int row = 0;
            for (Threat t : threats) {
                if (tableY < MARGIN + 20) break;
                float[] rowBg = (row % 2 == 0) ? C_WHITE : C_LIGHT;
                fillRect(cs, MARGIN, tableY - ROW_H + 4, COL_W, ROW_H, rowBg);

                String[] cells = {
                    String.valueOf(t.getThreatId()),
                    truncate(zoneNames.getOrDefault(t.getZoneId(), "?"), 14),
                    truncate(t.getThreatType().replace(" [AUTO-ESCALATED]", "*"), 20),
                    t.getSeverity(),
                    t.getThreatDate(),
                    t.getStatus()
                };

                cx = MARGIN + 6;
                for (int i = 0; i < cells.length; i++) {
                    cs.beginText();
                    cs.setFont(i == 3 ? fontBold : fontRegular, 8);
                    setFillColor(cs, i == 3 ? severityColor(t.getSeverity()) : C_BLACK);
                    cs.newLineAtOffset(cx, tableY - 9);
                    cs.showText(cells[i]);
                    cs.endText();
                    cx += colWidths[i];
                }
                tableY -= ROW_H;
                row++;
            }

            cs.beginText();
            cs.setFont(fontRegular, 8);
            setFillColor(cs, C_GRAY);
            cs.newLineAtOffset(MARGIN, 28);
            cs.showText("SentinelCity · Confidential · Page 2");
            cs.endText();
            fillRect(cs, MARGIN, 40, COL_W, 1, C_GRAY);
        }
    }

    // ── Drawing helpers ───────────────────────────────────────────────────
    private void fillRect(PDPageContentStream cs, float x, float y,
                           float w, float h, float[] rgb) throws IOException {
        cs.setNonStrokingColor(new PDColor(rgb, PDDeviceRGB.INSTANCE));
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private void drawCircle(PDPageContentStream cs, float cx, float cy,
                              float r, float[] rgb) throws IOException {
        cs.setNonStrokingColor(new PDColor(rgb, PDDeviceRGB.INSTANCE));
        final float k = 0.5523f * r;
        cs.moveTo(cx, cy + r);
        cs.curveTo(cx + k, cy + r, cx + r, cy + k, cx + r, cy);
        cs.curveTo(cx + r, cy - k, cx + k, cy - r, cx, cy - r);
        cs.curveTo(cx - k, cy - r, cx - r, cy - k, cx - r, cy);
        cs.curveTo(cx - r, cy + k, cx - k, cy + r, cx, cy + r);
        cs.fill();
    }

    private void setFillColor(PDPageContentStream cs, float[] rgb) throws IOException {
        cs.setNonStrokingColor(new PDColor(rgb, PDDeviceRGB.INSTANCE));
    }

    private float[] severityColor(String sev) {
        return switch (sev) {
            case "Critical" -> C_RED;
            case "Warning"  -> C_AMBER;
            case "Info"     -> C_BLUE;
            default         -> C_GRAY;
        };
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 1) + "…" : s;
    }

    // Keep local variable consistent
    private static final int maxOnPage1 = 25;
}

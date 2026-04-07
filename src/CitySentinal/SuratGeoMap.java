package CitySentinal;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.*;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.scene.shape.Circle;
import javafx.scene.text.*;
import javafx.util.Duration;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SuratGeoMap — polished Canvas-based Ahmedabad zone map.
 * No WebView, no module issues. Works on JavaFX 17–25.
 *
 * Features:
 *  - Named fixed positions for known zones so they never overlap
 *  - Colour-coded by worst active severity (Critical/Warning/Info/Clear)
 *  - Pulsing glow ring on Critical zones
 *  - Connection lines between adjacent zones
 *  - Hover tooltip showing zone name + threat details
 *  - Live refresh every 10 seconds
 */
public class SuratGeoMap {

    private final ThreatDAO threatDAO;
    private final ZoneDAO   zoneDAO;

    private static final double W = 800;
    private static final double H = 480;

    private final Canvas  canvas  = new Canvas(W, H);
    private final Pane    overlay = new Pane(); // for animated glow rings
    private Timeline      poller;

    private List<Zone>   zones   = new ArrayList<>();
    private List<Threat> threats = new ArrayList<>();

    // Zone name (lowercase) → [cx%, cy%] as fraction of W/H
    private static final Map<String, double[]> NAMED_POS = new LinkedHashMap<>();
    static {
        //                              cx%    cy%
        NAMED_POS.put("traffic grid",  new double[]{0.18, 0.28});
        NAMED_POS.put("water system",  new double[]{0.45, 0.18});
        NAMED_POS.put("water systems", new double[]{0.45, 0.18});
        NAMED_POS.put("power grid",    new double[]{0.74, 0.24});
        NAMED_POS.put("healthcare",    new double[]{0.30, 0.52});
        NAMED_POS.put("finance",       new double[]{0.55, 0.46});
        NAMED_POS.put("government",    new double[]{0.78, 0.55});
        NAMED_POS.put("transport",     new double[]{0.45, 0.75});
        NAMED_POS.put("public wifi",   new double[]{0.20, 0.72});
        NAMED_POS.put("administration",new double[]{0.65, 0.72});
        NAMED_POS.put("administrative",new double[]{0.65, 0.72});
        NAMED_POS.put("smart grid",    new double[]{0.88, 0.40});
        NAMED_POS.put("cctv network",  new double[]{0.10, 0.50});
    }

    // Fallback spiral positions for unknown zone names
    private static final double[][] FALLBACK = {
        {0.50, 0.40}, {0.35, 0.35}, {0.65, 0.35},
        {0.25, 0.62}, {0.75, 0.62}, {0.50, 0.60},
        {0.15, 0.40}, {0.85, 0.70}, {0.40, 0.85}, {0.60, 0.85}
    };

    private static final Map<String, Color> FILL   = new LinkedHashMap<>();
    private static final Map<String, Color> BORDER = new LinkedHashMap<>();
    private static final Map<String, Color> GLOW   = new LinkedHashMap<>();
    static {
        FILL  .put("Critical", Color.web("#7f1d1d"));
        FILL  .put("Warning",  Color.web("#78350f"));
        FILL  .put("Info",     Color.web("#1e3a5f"));
        FILL  .put("None",     Color.web("#1e293b"));
        BORDER.put("Critical", Color.web("#ef4444"));
        BORDER.put("Warning",  Color.web("#f59e0b"));
        BORDER.put("Info",     Color.web("#3b82f6"));
        BORDER.put("None",     Color.web("#475569"));
        GLOW  .put("Critical", Color.web("#ef4444", 0.55));
        GLOW  .put("Warning",  Color.web("#f59e0b", 0.40));
        GLOW  .put("Info",     Color.web("#3b82f6", 0.35));
        GLOW  .put("None",     Color.TRANSPARENT);
    }

    // Adjacency list for drawing edges (index pairs into NAMED_POS insertion order)
    private static final int[][] EDGES = {
        {0,1},{1,2},{0,3},{1,4},{2,5},{3,4},{4,5},{3,7},{4,7},{5,6},{5,8},{7,8}
    };

    public SuratGeoMap(ThreatDAO threatDAO, ZoneDAO zoneDAO) {
        this.threatDAO = threatDAO;
        this.zoneDAO   = zoneDAO;
    }

    public VBox getView() {
        VBox wrapper = new VBox(0);
        wrapper.setStyle("-fx-background-color: #0d1117;");

        // ── Header ────────────────────────────────────────────────────────
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 20, 10, 20));
        header.setStyle("-fx-background-color: #161b22; -fx-border-color: #30363d; -fx-border-width: 0 0 1 0;");

        Label title = new Label("Ahmedabad Zone Threat Map");
        title.setFont(Font.font("System", FontWeight.BOLD, 15));
        title.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox legend = new HBox(16);
        legend.setAlignment(Pos.CENTER);
        legend.getChildren().addAll(
            legendItem("#ef4444", "Critical"),
            legendItem("#f59e0b", "Warning"),
            legendItem("#3b82f6", "Info"),
            legendItem("#475569", "Clear")
        );

        Label refreshLabel = new Label("Live · updates every 10s");
        refreshLabel.setFont(Font.font("System", 10));
        refreshLabel.setTextFill(Color.web("#4b5563"));

        header.getChildren().addAll(title, spacer, legend, refreshLabel);

        // ── Map area ──────────────────────────────────────────────────────
        StackPane mapArea = new StackPane(canvas, overlay);
        mapArea.setStyle("-fx-background-color: #0d1117;");
        VBox.setVgrow(mapArea, Priority.ALWAYS);

        // Mouse hover for tooltips
        canvas.setOnMouseMoved(e -> handleHover(e.getX(), e.getY()));
        canvas.setOnMouseExited(e -> Tooltip.uninstall(canvas, null));

        wrapper.getChildren().addAll(header, mapArea);

        // Initial draw + start polling
        loadAndDraw();
        startPoller();

        return wrapper;
    
    }

    public void refresh() {
        loadAndDraw();
    }

    public void stop() {
        if (poller != null) poller.stop();
    }

    // ── Data loading ──────────────────────────────────────────────────────
    private void loadAndDraw() {
        new Thread(() -> {
            List<Zone>   z = zoneDAO.getAllZones();
            List<Threat> t = threatDAO.getAllThreats();
            // Deduplicate zones by name
            Map<String, Zone> seen = new LinkedHashMap<>();
            for (Zone zone : z) seen.putIfAbsent(zone.getZoneName().toLowerCase().trim(), zone);
            Platform.runLater(() -> {
                zones   = new ArrayList<>(seen.values());
                threats = t;
                draw();
            });
        }, "GeoMap-Load").start();
    }

    private void startPoller() {
        poller = new Timeline(new KeyFrame(Duration.seconds(10), e -> loadAndDraw()));
        poller.setCycleCount(Animation.INDEFINITE);
        poller.play();
    }

    // ── Drawing ───────────────────────────────────────────────────────────
    private void draw() {
        overlay.getChildren().clear();
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Background gradient
        gc.setFill(Color.web("#0d1117"));
        gc.fillRect(0, 0, W, H);

        // Subtle grid
        gc.setStroke(Color.web("#21262d", 0.8));
        gc.setLineWidth(0.5);
        for (double x = 0; x < W; x += 50) gc.strokeLine(x, 0, x, H);
        for (double y = 0; y < H; y += 50) gc.strokeLine(0, y, W, y);

        // City boundary hint — faint rounded rect
        gc.setStroke(Color.web("#30363d", 0.6));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(30, 30, W - 60, H - 60, 40, 40);

        // City name watermark
        gc.setFill(Color.web("#21262d"));
        gc.setFont(Font.font("System", FontWeight.BOLD, 60));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("AHMEDABAD", W / 2, H / 2 + 22);

        // Build severity map
        Map<Integer, String> worstSev    = new HashMap<>();
        Map<Integer, Long>   activeCnt   = new HashMap<>();
        Map<Integer, List<String>> names = new HashMap<>();
        for (Threat t : threats) {
            if (!"Active".equals(t.getStatus())) continue;
            int zid = t.getZoneId();
            String cur = worstSev.getOrDefault(zid, "None");
            if (sevRank(t.getSeverity()) > sevRank(cur)) worstSev.put(zid, t.getSeverity());
            activeCnt.merge(zid, 1L, Long::sum);
            names.computeIfAbsent(zid, k -> new ArrayList<>()).add(t.getThreatType());
        }

        // Compute positions
        List<double[]> positions = computePositions();
        if (positions.isEmpty()) {
            drawEmpty(gc);
            return;
        }

        // Draw edges
        drawEdges(gc, positions);

        // Draw zone nodes
        for (int i = 0; i < zones.size(); i++) {
            Zone z   = zones.get(i);
            double cx = positions.get(i)[0];
            double cy = positions.get(i)[1];
            String sev = worstSev.getOrDefault(z.getZoneId(), "None");
            long   cnt = activeCnt.getOrDefault(z.getZoneId(), 0L);
            drawZone(gc, cx, cy, z.getZoneName(), sev, cnt);
            if ("Critical".equals(sev)) addPulseRing(cx, cy);
        }
    }

    private void drawEdges(GraphicsContext gc, List<double[]> pos) {
        gc.setLineWidth(1.2);
        for (int[] edge : EDGES) {
            if (edge[0] < pos.size() && edge[1] < pos.size()) {
                double x1 = pos.get(edge[0])[0], y1 = pos.get(edge[0])[1];
                double x2 = pos.get(edge[1])[0], y2 = pos.get(edge[1])[1];
                gc.setStroke(Color.web("#30363d", 0.9));
                gc.strokeLine(x1, y1, x2, y2);
            }
        }
        // Also connect consecutive unknown zones
        for (int i = 0; i + 1 < pos.size(); i++) {
            boolean hasEdge = false;
            for (int[] e : EDGES) if ((e[0]==i&&e[1]==i+1)||(e[1]==i&&e[0]==i+1)) { hasEdge=true; break; }
            if (!hasEdge && i + 1 < pos.size()) {
                gc.setStroke(Color.web("#21262d", 0.7));
                gc.strokeLine(pos.get(i)[0], pos.get(i)[1], pos.get(i+1)[0], pos.get(i+1)[1]);
            }
        }
    }

    private void drawZone(GraphicsContext gc, double cx, double cy,
                           String name, String sev, long cnt) {
        double r = 42;

        Color fill   = FILL  .getOrDefault(sev, FILL.get("None"));
        Color border = BORDER.getOrDefault(sev, BORDER.get("None"));
        Color glow   = GLOW  .getOrDefault(sev, Color.TRANSPARENT);

        // Outer glow (for non-none severities)
        if (!"None".equals(sev)) {
            for (int g = 4; g >= 1; g--) {
                gc.setFill(glow.deriveColor(0, 1, 1, 0.08 * g));
                double rg = r + g * 6;
                gc.fillOval(cx - rg, cy - rg, rg * 2, rg * 2);
            }
        }

        // Circle fill
        gc.setFill(fill);
        gc.fillOval(cx - r, cy - r, r * 2, r * 2);

        // Border ring
        gc.setStroke(border);
        gc.setLineWidth("Critical".equals(sev) ? 3.0 : 2.0);
        gc.strokeOval(cx - r, cy - r, r * 2, r * 2);

        // Inner highlight (top gloss)
        gc.setFill(Color.web("#ffffff", 0.06));
        gc.fillOval(cx - r + 6, cy - r + 6, r - 4, r / 2);

        // Zone name — wrap long names
        gc.setFill(Color.WHITE);
        gc.setTextAlign(TextAlignment.CENTER);
        String[] words = name.split(" ");
        if (words.length == 1 || name.length() <= 10) {
            gc.setFont(Font.font("System", FontWeight.BOLD, 11));
            gc.fillText(truncate(name, 12), cx, cy + 4);
        } else {
            // Two lines
            String line1 = words[0];
            String line2 = String.join(" ", Arrays.copyOfRange(words, 1, words.length));
            gc.setFont(Font.font("System", FontWeight.BOLD, 10));
            gc.fillText(truncate(line1, 11), cx, cy - 3);
            gc.fillText(truncate(line2, 11), cx, cy + 11);
        }

        // Severity badge below zone
        String badge = "None".equals(sev) ? "Clear" : sev;
        gc.setFont(Font.font("System", 9));
        gc.setFill(border.brighter());
        gc.fillText(badge + (cnt > 0 ? " (" + cnt + ")" : ""), cx, cy + r + 14);
    }

    private void addPulseRing(double cx, double cy) {
        Circle ring = new Circle(cx, cy, 46);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(Color.web("#ef4444"));
        ring.setStrokeWidth(2);
        ring.setOpacity(0.8);

        FadeTransition fade = new FadeTransition(Duration.millis(1000), ring);
        fade.setFromValue(0.8);
        fade.setToValue(0.0);
        fade.setCycleCount(Animation.INDEFINITE);
        fade.setAutoReverse(true);

        ScaleTransition scale = new ScaleTransition(Duration.millis(1000), ring);
        scale.setFromX(1.0); scale.setToX(1.4);
        scale.setFromY(1.0); scale.setToY(1.4);
        scale.setCycleCount(Animation.INDEFINITE);
        scale.setAutoReverse(true);

        overlay.getChildren().add(ring);
        fade.play();
        scale.play();
    }

    private void drawEmpty(GraphicsContext gc) {
        gc.setFill(Color.web("#4b5563"));
        gc.setFont(Font.font("System", 14));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("No zones found. Add zones in the database.", W / 2, H / 2);
    }

    // ── Hover tooltip ─────────────────────────────────────────────────────
    private final Tooltip hoverTip = new Tooltip();

    private void handleHover(double mx, double my) {
        List<double[]> pos = computePositions();
        for (int i = 0; i < zones.size() && i < pos.size(); i++) {
            double cx = pos.get(i)[0], cy = pos.get(i)[1];
            if (Math.hypot(mx - cx, my - cy) <= 44) {
                Zone z = zones.get(i);
                // Build tooltip text
                long active = threats.stream()
                    .filter(t -> t.getZoneId() == z.getZoneId() && "Active".equals(t.getStatus()))
                    .count();
                String typeSummary = threats.stream()
                    .filter(t -> t.getZoneId() == z.getZoneId() && "Active".equals(t.getStatus()))
                    .map(Threat::getThreatType).distinct().limit(3)
                    .collect(Collectors.joining(", "));
                hoverTip.setText(
                    z.getZoneName() + "\n" +
                    "Active threats: " + active +
                    (typeSummary.isEmpty() ? "" : "\n" + typeSummary)
                );
                hoverTip.setStyle(
                    "-fx-background-color: #1e293b;" +
                    "-fx-text-fill: #e2e8f0;" +
                    "-fx-font-size: 12px;" +
                    "-fx-border-color: #475569;" +
                    "-fx-border-width: 1;" +
                    "-fx-border-radius: 6;" +
                    "-fx-background-radius: 6;"
                );
                Tooltip.install(canvas, hoverTip);
                return;
            }
        }
        Tooltip.uninstall(canvas, hoverTip);
    }

    // ── Position helpers ──────────────────────────────────────────────────
    private List<double[]> computePositions() {
        List<double[]> result = new ArrayList<>();
        int fallbackIdx = 0;
        for (Zone z : zones) {
            String key = z.getZoneName().toLowerCase().trim();
            double[] pct = NAMED_POS.get(key);
            if (pct == null) {
                // Partial match
                for (Map.Entry<String, double[]> e : NAMED_POS.entrySet()) {
                    if (key.contains(e.getKey()) || e.getKey().contains(key)) {
                        pct = e.getValue();
                        break;
                    }
                }
            }
            if (pct == null) {
                pct = FALLBACK[fallbackIdx % FALLBACK.length];
                fallbackIdx++;
            }
            result.add(new double[]{pct[0] * W, pct[1] * H});
        }
        return result;
    }

    private HBox legendItem(String hex, String label) {
        HBox row = new HBox(5);
        row.setAlignment(Pos.CENTER);
        Circle c = new Circle(6, Color.web(hex));
        Label l = new Label(label);
        l.setFont(Font.font("System", 11));
        l.setTextFill(Color.web("#94a3b8"));
        row.getChildren().addAll(c, l);
        return row;
    }

    private String truncate(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private int sevRank(String s) {
        return switch (s) {
            case "Critical" -> 3;
            case "Warning"  -> 2;
            case "Info"     -> 1;
            default         -> 0;
        };
    }
}
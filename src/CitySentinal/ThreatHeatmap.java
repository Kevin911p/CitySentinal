package CitySentinal;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.*;
import javafx.util.Duration;

import java.util.*;

/**
 * ThreatHeatmap — a live grid of zone cards whose colour
 * pulses based on the highest active threat severity in that zone.
 *
 * Usage in MainDashboard:
 *   ThreatHeatmap heatmap = new ThreatHeatmap(threatDAO, zoneDAO);
 *   someContainer.getChildren().add(heatmap.getView());
 *   heatmap.start();   // begin polling
 *   heatmap.stop();    // call on app exit
 */
public class ThreatHeatmap {

    private final ThreatDAO threatDAO;
    private final ZoneDAO   zoneDAO;

    private final FlowPane grid = new FlowPane(12, 12);
    private Timeline poller;

    // Zone name → card root so we can update colours in place
    private final Map<String, StackPane> zoneCards = new LinkedHashMap<>();

    // Severity → fill colour
    private static final Map<String, String> SEV_COLOR = new LinkedHashMap<>();
    static {
        SEV_COLOR.put("Critical",   "#991b1b");   // deep red
        SEV_COLOR.put("Warning",    "#92400e");   // amber-dark
        SEV_COLOR.put("Info",       "#1e3a5f");   // blue-dark
        SEV_COLOR.put("None",       "#1a2235");   // neutral dark
    }
    private static final Map<String, String> SEV_GLOW = new LinkedHashMap<>();
    static {
        SEV_GLOW.put("Critical",   "#ef4444");
        SEV_GLOW.put("Warning",    "#f59e0b");
        SEV_GLOW.put("Info",       "#3b82f6");
        SEV_GLOW.put("None",       "#2d3748");
    }
    private static final Map<String, String> SEV_LABEL_COLOR = new LinkedHashMap<>();
    static {
        SEV_LABEL_COLOR.put("Critical",   "#fca5a5");
        SEV_LABEL_COLOR.put("Warning",    "#fcd34d");
        SEV_LABEL_COLOR.put("Info",       "#93c5fd");
        SEV_LABEL_COLOR.put("None",       "#4b5563");
    }

    public ThreatHeatmap(ThreatDAO threatDAO, ZoneDAO zoneDAO) {
        this.threatDAO = threatDAO;
        this.zoneDAO   = zoneDAO;

        grid.setPadding(new Insets(4));
        grid.setAlignment(Pos.TOP_LEFT);
    }

    /** Returns the root node to embed in the dashboard. */
    public VBox getView() {
        VBox wrapper = new VBox(10);

        Label title = new Label("Live Zone Heatmap");
        title.setFont(Font.font("System", FontWeight.BOLD, 13));
        title.setTextFill(Color.web("#e2e8f0"));

        // Legend row
        HBox legend = new HBox(14);
        legend.setAlignment(Pos.CENTER_LEFT);
        for (Map.Entry<String, String> e : SEV_GLOW.entrySet()) {
            if ("None".equals(e.getKey())) continue;
            HBox dot = new HBox(5);
            dot.setAlignment(Pos.CENTER);
            Rectangle r = new Rectangle(10, 10);
            r.setArcWidth(3); r.setArcHeight(3);
            r.setFill(Color.web(e.getValue()));
            Label l = new Label(e.getKey());
            l.setFont(Font.font("System", 11));
            l.setTextFill(Color.web("#94a3b8"));
            dot.getChildren().addAll(r, l);
            legend.getChildren().add(dot);
        }

        wrapper.getChildren().addAll(title, legend, grid);
        wrapper.setStyle(
            "-fx-background-color: #0f1623;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: #2d3748;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 12;" +
            "-fx-padding: 16;"
        );
        return wrapper;
    }

    /** Start polling the DB every 5 seconds. */
    public void start() {
        refresh(); // immediate first load
        poller = new Timeline(new KeyFrame(Duration.seconds(5), e -> refresh()));
        poller.setCycleCount(Animation.INDEFINITE);
        poller.play();
    }

    /** Stop the polling timer (call on app shutdown). */
    public void stop() {
        if (poller != null) poller.stop();
    }

    // ── Core refresh ──────────────────────────────────────────────────────
    private void refresh() {
        new Thread(() -> {
            List<Zone>   zones   = zoneDAO.getAllZones();
            List<Threat> threats = threatDAO.getAllThreats();

            // Build a map: zoneId → worst active severity
            Map<Integer, String> worstSev = new HashMap<>();
            for (Threat t : threats) {
                if (!"Active".equals(t.getStatus())) continue;
                int zid = t.getZoneId();
                String cur = worstSev.getOrDefault(zid, "None");
                if (severityRank(t.getSeverity()) > severityRank(cur)) {
                    worstSev.put(zid, t.getSeverity());
                }
            }

            // Count active threats per zone
            Map<Integer, Long> activeCount = new HashMap<>();
            for (Threat t : threats) {
                if ("Active".equals(t.getStatus())) {
                    activeCount.merge(t.getZoneId(), 1L, Long::sum);
                }
            }

            Platform.runLater(() -> updateGrid(zones, worstSev, activeCount));
        }).start();
    }

    private void updateGrid(List<Zone> zones,
                             Map<Integer, String> worstSev,
                             Map<Integer, Long> activeCount) {
        Set<String> seen = new HashSet<>();

        for (Zone z : zones) {
            String name = z.getZoneName();
            String sev  = worstSev.getOrDefault(z.getZoneId(), "None");
            long   cnt  = activeCount.getOrDefault(z.getZoneId(), 0L);
            seen.add(name);

            if (zoneCards.containsKey(name)) {
                // Update existing card
                updateCard(zoneCards.get(name), name, sev, cnt);
            } else {
                // Create new card
                StackPane card = buildCard(name, sev, cnt);
                zoneCards.put(name, card);
                grid.getChildren().add(card);
            }
        }

        // Remove cards for deleted zones
        zoneCards.keySet().retainAll(seen);
        grid.getChildren().removeIf(n -> {
            if (n instanceof StackPane sp) {
                Object tag = sp.getUserData();
                return tag != null && !seen.contains(tag.toString());
            }
            return false;
        });
    }

    // ── Card builders ─────────────────────────────────────────────────────
    private StackPane buildCard(String zoneName, String severity, long activeThreats) {
        StackPane card = new StackPane();
        card.setUserData(zoneName);
        card.setPrefSize(155, 100);
        card.setMinSize(155, 100);

        // Background rect (we'll update its color)
        Rectangle bg = new Rectangle(155, 100);
        bg.setArcWidth(12); bg.setArcHeight(12);
        bg.setId("bg");

        // Border rect (glow effect via border)
        Rectangle border = new Rectangle(155, 100);
        border.setArcWidth(12); border.setArcHeight(12);
        border.setFill(Color.TRANSPARENT);
        border.setId("border");

        VBox content = new VBox(6);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(12));

        Label nameLabel = new Label(zoneName);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        nameLabel.setWrapText(true);
        nameLabel.setTextAlignment(TextAlignment.CENTER);
        nameLabel.setId("name");

        Label sevLabel = new Label();
        sevLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        sevLabel.setId("sev");

        Label countLabel = new Label();
        countLabel.setFont(Font.font("System", 10));
        countLabel.setId("count");

        content.getChildren().addAll(nameLabel, sevLabel, countLabel);
        card.getChildren().addAll(bg, border, content);

        applyColors(card, severity, activeThreats, false);
        return card;
    }

    private void updateCard(StackPane card, String zoneName, String severity, long activeThreats) {
        applyColors(card, severity, activeThreats, true);
    }

    private void applyColors(StackPane card, String severity, long count, boolean animate) {
        Rectangle bg     = (Rectangle) card.lookup("#bg");
        Rectangle border = (Rectangle) card.lookup("#border");
        Label nameLabel  = (Label) card.lookup("#name");
        Label sevLabel   = (Label) card.lookup("#sev");
        Label countLabel = (Label) card.lookup("#count");

        String fillHex  = SEV_COLOR.getOrDefault(severity, SEV_COLOR.get("None"));
        String glowHex  = SEV_GLOW.getOrDefault(severity, SEV_GLOW.get("None"));
        String textHex  = SEV_LABEL_COLOR.getOrDefault(severity, SEV_LABEL_COLOR.get("None"));

        Color fillColor = Color.web(fillHex);
        Color glowColor = Color.web(glowHex, 0.6);

        if (animate) {
            FillTransition ft = new FillTransition(Duration.millis(600), bg);
            ft.setToValue(fillColor);
            ft.play();
        } else {
            bg.setFill(fillColor);
        }

        border.setStroke(glowColor);
        border.setStrokeWidth(1.5);

        if (nameLabel != null) nameLabel.setTextFill(Color.WHITE);
        if (sevLabel != null) {
            sevLabel.setText("None".equals(severity) ? "No active threats" : "⚠ " + severity);
            sevLabel.setTextFill(Color.web(textHex));
        }
        if (countLabel != null) {
            countLabel.setText(count > 0 ? count + " active threat" + (count > 1 ? "s" : "") : "");
            countLabel.setTextFill(Color.web("#94a3b8"));
        }

        // Critical = pulsing animation
        card.getProperties().remove("pulse");
        if ("Critical".equals(severity)) {
            FadeTransition pulse = new FadeTransition(Duration.millis(900), border);
            pulse.setFromValue(0.4); pulse.setToValue(1.0);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.setAutoReverse(true);
            pulse.play();
            card.getProperties().put("pulse", pulse);
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────
    private int severityRank(String sev) {
        return switch (sev) {
            case "Critical" -> 3;
            case "Warning"  -> 2;
            case "Info"     -> 1;
            default         -> 0;
        };
    }
}

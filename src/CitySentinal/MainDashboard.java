package CitySentinal;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainDashboard {

    // ── DAOs & feature components ─────────────────────────────────────────
    private ThreatDAO  threatDAO;
    private ZoneDAO    zoneDAO;
    private SuratGeoMap geoMap;

    // ── UI references ─────────────────────────────────────────────────────
    private Label totalThreatsValue;
    private Label criticalCountValue;
    private Label alertLabel;
    private HBox  alertPill;
    private Circle alertDot;
    private TableView<ThreatRow> threatTable;
    private ObservableList<ThreatRow> masterData;
    private TextField searchField;
    private ComboBox<String> severityFilter;

    // ── Colour palette ────────────────────────────────────────────────────
    private static final String C_SIDEBAR    = "#0f1623";
    private static final String C_TOPBAR     = "#ffffff";
    private static final String C_BG         = "#f1f5f9";
    private static final String C_CARD_BG    = "#ffffff";
    private static final String C_ACCENT     = "#4f46e5";
    private static final String C_RED_BG     = "#fef2f2";
    private static final String C_RED_TEXT   = "#991b1b";
    private static final String C_AMB_BG     = "#fffbeb";
    private static final String C_AMB_TEXT   = "#92400e";
    private static final String C_BLUE_BG    = "#eff6ff";
    private static final String C_BLUE_TEXT  = "#1e40af";
    private static final String C_GREEN_BG   = "#f0fdf4";
    private static final String C_GREEN_TEXT = "#166534";

    // ─────────────────────────────────────────────────────────────────────
    public void start(Stage primaryStage) {
        threatDAO = new ThreatDAO();
        zoneDAO   = new ZoneDAO();
        geoMap    = new SuratGeoMap(threatDAO, zoneDAO);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + C_BG + ";");

        root.setTop(buildTopBar());
        root.setLeft(buildSidebar(primaryStage));
        root.setCenter(buildMainPanel());

        Scene scene = new Scene(root, 1200, 720);
        scene.getStylesheets().add(inlineStyle());

        primaryStage.setTitle("SentinelCity – Urban Cyber Threat Monitor");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(600);
        primaryStage.show();

        Platform.runLater(() -> {
            refreshTable();
            startAlertMonitor();
        });
    }

    // ── INLINE CSS ────────────────────────────────────────────────────────
    private String inlineStyle() {
        String css =
            ".table-view { -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 10; }" +
            ".table-view .column-header-background { -fx-background-color: #f8fafc; }" +
            ".table-view .column-header { -fx-background-color: transparent; -fx-border-color: transparent; }" +
            ".table-view .column-header .label { -fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-weight: bold; }" +
            ".table-row-cell { -fx-background-color: white; -fx-border-color: transparent transparent #f1f5f9 transparent; }" +
            ".table-row-cell:odd { -fx-background-color: #fafafa; }" +
            ".table-row-cell:selected { -fx-background-color: #ede9fe; }" +
            ".table-row-cell:hover { -fx-background-color: #f8fafc; }" +
            ".scroll-bar:vertical { -fx-background-color: transparent; -fx-pref-width: 8px; }" +
            ".scroll-bar:vertical .thumb { -fx-background-color: #cbd5e1; -fx-background-radius: 4; }" +
            ".scroll-bar:vertical .track { -fx-background-color: transparent; }" +
            ".combo-box { -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; }" +
            ".combo-box .list-cell { -fx-text-fill: #334155; -fx-font-size: 12px; }" +
            ".text-field { -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #334155; }" +
            ".text-field:focused { -fx-border-color: #4f46e5; }" +
            ".button { -fx-cursor: hand; }";
        try {
            java.io.File f = java.io.File.createTempFile("sentinelcity", ".css");
            f.deleteOnExit();
            try (FileWriter fw = new FileWriter(f)) { fw.write(css); }
            return f.toURI().toString();
        } catch (IOException e) {
            return "";
        }
    }

    // ── TOP BAR ──────────────────────────────────────────────────────────
    private HBox buildTopBar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 20, 0, 20));
        bar.setPrefHeight(56);
        bar.setStyle("-fx-background-color: " + C_TOPBAR + "; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        HBox brand = new HBox(8);
        brand.setAlignment(Pos.CENTER_LEFT);
        Circle dot = new Circle(5, Color.web("#4f46e5"));
        Label nameLabel = new Label("SentinelCity");
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 15));
        nameLabel.setTextFill(Color.web("#1e293b"));
        Label sep = new Label("·");
        sep.setTextFill(Color.web("#cbd5e1"));
        Label sub = new Label("Urban Cyber Threat Monitor");
        sub.setFont(Font.font("System", 12));
        sub.setTextFill(Color.web("#94a3b8"));
        brand.getChildren().addAll(dot, nameLabel, sep, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        alertPill = new HBox(6);
        alertPill.setAlignment(Pos.CENTER);
        alertPill.setPadding(new Insets(5, 12, 5, 12));
        alertPill.setStyle("-fx-background-color: #fef2f2; -fx-background-radius: 20; -fx-border-color: #fca5a5; -fx-border-radius: 20; -fx-border-width: 1;");
        alertDot = new Circle(4, Color.web("#ef4444"));
        alertLabel = new Label("Checking system...");
        alertLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        alertLabel.setTextFill(Color.web("#991b1b"));
        alertPill.getChildren().addAll(alertDot, alertLabel);

        Label dateLabel = new Label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d yyyy")));
        dateLabel.setFont(Font.font("System", 11));
        dateLabel.setTextFill(Color.web("#94a3b8"));

        bar.getChildren().addAll(brand, spacer, dateLabel, new Label("  "), alertPill);
        return bar;
    }

    // ── SIDEBAR ───────────────────────────────────────────────────────────
    private VBox buildSidebar(Stage stage) {
        VBox sidebar = new VBox(2);
        sidebar.setPrefWidth(190);
        sidebar.setPadding(new Insets(20, 0, 20, 0));
        sidebar.setStyle("-fx-background-color: " + C_SIDEBAR + ";");

        Label city = new Label("  Ahmedabad Smart City");
        city.setFont(Font.font("System", 11));
        city.setTextFill(Color.web("#4b5563"));
        city.setPadding(new Insets(0, 0, 12, 16));
        sidebar.getChildren().add(city);

        String[][] navItems = {
            {"Dashboard", "⊞"},
            {"Log Threat", "+"},
            {"View Zones", "◎"},
            {"Geo Map",    "⊕"},
            {"Reports",    "≡"},
            {"Exit",       "→"}
        };

        for (int i = 0; i < navItems.length; i++) {
            String  label   = navItems[i][0];
            String  icon    = navItems[i][1];
            boolean isFirst = (i == 0);

            HBox item = new HBox(10);
            item.setAlignment(Pos.CENTER_LEFT);
            item.setPadding(new Insets(10, 16, 10, 16));
            item.setCursor(javafx.scene.Cursor.HAND);

            Label iconLbl = new Label(icon);
            iconLbl.setFont(Font.font("System", 14));
            Label textLbl = new Label(label);
            textLbl.setFont(Font.font("System", 13));

            if (isFirst) {
                item.setStyle("-fx-background-color: rgba(99,102,241,0.15);");
                iconLbl.setTextFill(Color.web("#818cf8"));
                textLbl.setTextFill(Color.web("#a5b4fc"));
            } else {
                item.setStyle("-fx-background-color: transparent;");
                iconLbl.setTextFill(Color.web("#4b5563"));
                textLbl.setTextFill(Color.web("#9ca3af"));
            }

            item.getChildren().addAll(iconLbl, textLbl);

            item.setOnMouseEntered(e -> {
                item.setStyle("-fx-background-color: rgba(255,255,255,0.05);");
                iconLbl.setTextFill(Color.WHITE);
                textLbl.setTextFill(Color.WHITE);
            });
            item.setOnMouseExited(e -> {
                if (isFirst) {
                    item.setStyle("-fx-background-color: rgba(99,102,241,0.15);");
                    iconLbl.setTextFill(Color.web("#818cf8"));
                    textLbl.setTextFill(Color.web("#a5b4fc"));
                } else {
                    item.setStyle("-fx-background-color: transparent;");
                    iconLbl.setTextFill(Color.web("#4b5563"));
                    textLbl.setTextFill(Color.web("#9ca3af"));
                }
            });

            final String nav = label;
            item.setOnMouseClicked(e -> handleNav(nav, stage));
            sidebar.getChildren().add(item);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label version = new Label("v2.0 · JavaFX");
        version.setFont(Font.font("System", 10));
        version.setTextFill(Color.web("#374151"));
        version.setPadding(new Insets(0, 0, 0, 16));
        sidebar.getChildren().addAll(spacer, version);
        return sidebar;
    }

    // ── MAIN PANEL ────────────────────────────────────────────────────────
    private VBox buildMainPanel() {
        VBox panel = new VBox(14);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: " + C_BG + ";");

        VBox tableSection = buildTableSection();
        VBox.setVgrow(tableSection, Priority.ALWAYS);

        panel.getChildren().addAll(buildMetricsRow(), buildSearchBar(), tableSection);
        return panel;
    }

    // ── METRIC CARDS ──────────────────────────────────────────────────────
    private HBox buildMetricsRow() {
        HBox row = new HBox(12);
        row.setFillHeight(true);

        totalThreatsValue  = new Label("0");
        criticalCountValue = new Label("0");

        HBox c1 = metricCard("Total threats today",  totalThreatsValue,  "#fef2f2", "#fee2e2", "#991b1b", "⚠", "#ef4444");
        HBox c2 = metricCard("Critical incidents",   criticalCountValue, "#fffbeb", "#fde68a", "#92400e", "◉", "#f59e0b");
        HBox c3 = metricCard("City zones monitored", new Label("6"),     "#eff6ff", "#bfdbfe", "#1e40af", "◎", "#3b82f6");

        HBox.setHgrow(c1, Priority.ALWAYS);
        HBox.setHgrow(c2, Priority.ALWAYS);
        HBox.setHgrow(c3, Priority.ALWAYS);

        row.getChildren().addAll(c1, c2, c3);
        return row;
    }

    private HBox metricCard(String title, Label valueLabel,
                             String bgColor, String iconBg, String textColor,
                             String iconChar, String iconColor) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setStyle(
            "-fx-background-color: " + C_CARD_BG + ";" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 12;"
        );

        Label iconLbl = new Label(iconChar);
        iconLbl.setFont(Font.font("System", 18));
        iconLbl.setTextFill(Color.web(iconColor));
        StackPane iconBox = new StackPane(iconLbl);
        iconBox.setPrefSize(42, 42);
        iconBox.setMinSize(42, 42);
        iconBox.setStyle("-fx-background-color: " + iconBg + "; -fx-background-radius: 10;");

        VBox text = new VBox(3);
        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("System", 11));
        titleLbl.setTextFill(Color.web("#94a3b8"));
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 28));
        valueLabel.setTextFill(Color.web(textColor));
        text.getChildren().addAll(titleLbl, valueLabel);

        card.getChildren().addAll(iconBox, text);
        return card;
    }

    // ── SEARCH BAR ────────────────────────────────────────────────────────
    private HBox buildSearchBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 16, 12, 16));
        bar.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 10;"
        );

        Label tblLabel = new Label("Threat log");
        tblLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        tblLabel.setTextFill(Color.web("#1e293b"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label searchLbl = new Label("Search");
        searchLbl.setFont(Font.font("System", 12));
        searchLbl.setTextFill(Color.web("#64748b"));

        searchField = new TextField();
        searchField.setPromptText("Zone, threat type, status...");
        searchField.setPrefWidth(220);
        searchField.setPrefHeight(32);
        searchField.setFont(Font.font("System", 12));

        Label sevLbl = new Label("Severity");
        sevLbl.setFont(Font.font("System", 12));
        sevLbl.setTextFill(Color.web("#64748b"));

        severityFilter = new ComboBox<>(FXCollections.observableArrayList("All", "Critical", "Warning", "Info"));
        severityFilter.setValue("All");
        severityFilter.setPrefHeight(32);

        Button searchBtn = styledButton("Search", C_ACCENT, "white");
        searchBtn.setOnAction(e -> filterTable());

        Button clearBtn = styledButton("Clear", "#64748b", "white");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            severityFilter.setValue("All");
            refreshTable();
        });

        bar.getChildren().addAll(tblLabel, spacer, searchLbl, searchField, sevLbl, severityFilter, searchBtn, clearBtn);
        return bar;
    }

    // ── TABLE ─────────────────────────────────────────────────────────────
    private VBox buildTableSection() {
        masterData  = FXCollections.observableArrayList();
        threatTable = new TableView<>(masterData);
        threatTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        threatTable.setPlaceholder(new Label("No threats found."));
        VBox.setVgrow(threatTable, Priority.ALWAYS);

        TableColumn<ThreatRow, String> idCol     = strCol("ID",          "threatId",   60);
        TableColumn<ThreatRow, String> zoneCol   = strCol("Zone",        "zoneName",   130);
        TableColumn<ThreatRow, String> typeCol   = strCol("Threat type", "threatType", 130);
        TableColumn<ThreatRow, String> sevCol    = strCol("Severity",    "severity",   100);
        TableColumn<ThreatRow, String> dateCol   = strCol("Date",        "threatDate", 110);
        TableColumn<ThreatRow, String> timeCol   = strCol("Time",        "threatTime", 90);
        TableColumn<ThreatRow, String> statusCol = strCol("Status",      "status",     110);

        sevCol.setCellFactory(severityBadgeFactory());
        statusCol.setCellFactory(statusBadgeFactory());

        threatTable.getColumns().addAll(idCol, zoneCol, typeCol, sevCol, dateCol, timeCol, statusCol);

        VBox wrapper = new VBox(threatTable);
        VBox.setVgrow(threatTable, Priority.ALWAYS);
        wrapper.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 10;"
        );
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        return wrapper;
    }

    private TableColumn<ThreatRow, String> strCol(String title, String property, int minWidth) {
        TableColumn<ThreatRow, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        col.setMinWidth(minWidth);
        col.setStyle("-fx-alignment: CENTER-LEFT;");
        return col;
    }

    // ── BADGE FACTORIES ───────────────────────────────────────────────────
    private Callback<TableColumn<ThreatRow, String>, TableCell<ThreatRow, String>> severityBadgeFactory() {
        return col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label badge = new Label(item);
                badge.setFont(Font.font("System", FontWeight.BOLD, 10));
                switch (item) {
                    case "Critical": badge.setStyle(badgeStyle(C_RED_BG,  "#fca5a5", C_RED_TEXT));  break;
                    case "Warning":  badge.setStyle(badgeStyle(C_AMB_BG,  "#fcd34d", C_AMB_TEXT));  break;
                    case "Info":     badge.setStyle(badgeStyle(C_BLUE_BG, "#93c5fd", C_BLUE_TEXT)); break;
                    default:         badge.setStyle(badgeStyle("#f1f5f9",  "#cbd5e1", "#475569"));   break;
                }
                setGraphic(badge); setText(null);
            }
        };
    }

    private Callback<TableColumn<ThreatRow, String>, TableCell<ThreatRow, String>> statusBadgeFactory() {
        return col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label badge = new Label(item);
                badge.setFont(Font.font("System", FontWeight.BOLD, 10));
                switch (item) {
                    case "Active":        badge.setStyle(badgeStyle(C_RED_BG,   "#fca5a5", C_RED_TEXT));   break;
                    case "Resolved":      badge.setStyle(badgeStyle(C_GREEN_BG, "#86efac", C_GREEN_TEXT)); break;
                    case "Investigating": badge.setStyle(badgeStyle(C_AMB_BG,   "#fcd34d", C_AMB_TEXT));   break;
                    default:              badge.setStyle(badgeStyle("#f1f5f9",   "#cbd5e1", "#475569"));    break;
                }
                setGraphic(badge); setText(null);
            }
        };
    }

    private String badgeStyle(String bg, String border, String text) {
        return "-fx-background-color: " + bg + ";" +
               "-fx-border-color: " + border + ";" +
               "-fx-border-width: 1;" +
               "-fx-background-radius: 20;" +
               "-fx-border-radius: 20;" +
               "-fx-padding: 3 10 3 10;" +
               "-fx-text-fill: " + text + ";";
    }

    // ── REFRESH TABLE ─────────────────────────────────────────────────────
    public void refreshTable() {
        // DB work on background thread — only UI update on FX thread
        new Thread(() -> {
            List<Threat> threats = threatDAO.getAllThreats();
            List<Zone>   zones   = zoneDAO.getAllZones();

            Map<Integer, String> zoneMap = new HashMap<>();
            for (Zone z : zones) zoneMap.put(z.getZoneId(), z.getZoneName());

            int criticalCount = 0;
            ObservableList<ThreatRow> rows = FXCollections.observableArrayList();
            for (Threat t : threats) {
                String zoneName = zoneMap.getOrDefault(t.getZoneId(), "Unknown");
                rows.add(new ThreatRow(
                    t.getThreatId(), zoneName, t.getThreatType(),
                    t.getSeverity(), t.getThreatDate(), t.getThreatTime(), t.getStatus()
                ));
                if ("Critical".equals(t.getSeverity())) criticalCount++;
            }

            final int finalCritical = criticalCount;
            final int total         = threats.size();
            Platform.runLater(() -> {
                masterData.setAll(rows);
                totalThreatsValue.setText(String.valueOf(total));
                criticalCountValue.setText(String.valueOf(finalCritical));
            });
        }, "RefreshTable").start();
    }

    // ── FILTER TABLE ──────────────────────────────────────────────────────
    private void filterTable() {
        // Snapshot UI values on FX thread before going to background
        String kw  = searchField.getText().toLowerCase().trim();
        String sev = severityFilter.getValue();

        new Thread(() -> {
            List<Threat> threats = threatDAO.getAllThreats();
            List<Zone>   zones   = zoneDAO.getAllZones();

            Map<Integer, String> zoneMap = new HashMap<>();
            for (Zone z : zones) zoneMap.put(z.getZoneId(), z.getZoneName());

            ObservableList<ThreatRow> rows = FXCollections.observableArrayList();
            for (Threat t : threats) {
                String zoneName = zoneMap.getOrDefault(t.getZoneId(), "Unknown");
                boolean kMatch = kw.isEmpty()
                    || zoneName.toLowerCase().contains(kw)
                    || t.getThreatType().toLowerCase().contains(kw)
                    || t.getStatus().toLowerCase().contains(kw);
                boolean sMatch = "All".equals(sev) || t.getSeverity().equals(sev);
                if (kMatch && sMatch) {
                    rows.add(new ThreatRow(
                        t.getThreatId(), zoneName, t.getThreatType(),
                        t.getSeverity(), t.getThreatDate(), t.getThreatTime(), t.getStatus()
                    ));
                }
            }
            Platform.runLater(() -> masterData.setAll(rows));
        }, "FilterTable").start();
    }

    // ── ALERT MONITOR ─────────────────────────────────────────────────────
    private void startAlertMonitor() {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                List<Threat> threats = threatDAO.getCriticalThreats();
                long activeCount = threats.stream()
                    .filter(t -> "Active".equals(t.getStatus()))
                    .count();
                Platform.runLater(() -> {
                    if (activeCount > 0) {
                        alertLabel.setText(activeCount + " critical threat(s) active");
                        alertLabel.setTextFill(Color.web("#991b1b"));
                        alertPill.setStyle(
                            "-fx-background-color: #fef2f2; -fx-background-radius: 20;" +
                            "-fx-border-color: #fca5a5; -fx-border-radius: 20; -fx-border-width: 1;");
                        alertDot.setFill(Color.web("#ef4444"));
                    } else {
                        alertLabel.setText("All systems normal");
                        alertLabel.setTextFill(Color.web("#166534"));
                        alertPill.setStyle(
                            "-fx-background-color: #f0fdf4; -fx-background-radius: 20;" +
                            "-fx-border-color: #86efac; -fx-border-radius: 20; -fx-border-width: 1;");
                        alertDot.setFill(Color.web("#22c55e"));
                    }
                });
            }
        }, 0, 10_000);
    }

    // ── NAV HANDLER ───────────────────────────────────────────────────────
    private void handleNav(String item, Stage stage) {
        switch (item) {
            case "Dashboard":  refreshTable();                 break;
            case "Log Threat": showLogThreatDialog(stage);    break;
            case "View Zones": showZonesDialog(stage);         break;
            case "Geo Map":    showGeoMapDialog(stage);        break;
            case "Reports":    exportReport(stage);            break;
            case "Exit":       Platform.exit();                break;
        }
    }

    // ── LOG THREAT DIALOG ─────────────────────────────────────────────────
    private void showLogThreatDialog(Stage owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Log new threat");
        dialog.setWidth(440);
        dialog.setResizable(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: white;");

        Label title = new Label("Log new threat");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#1e293b"));

        // Load zones on background to avoid UI freeze
        List<Zone> zones = zoneDAO.getAllZones(); // small list, fast enough
        String[] zoneNames = zones.stream()
            .map(z -> z.getZoneId() + " - " + z.getZoneName())
            .toArray(String[]::new);

        ComboBox<String> zoneBox   = dialogCombo(zoneNames);
        ComboBox<String> typeBox   = dialogCombo("DDoS Attack", "Ransomware", "Phishing",
                                                  "Port Scan", "Unauthorized Login", "Malware",
                                                  "SQL Injection", "Man-in-Middle",
                                                  "Zero-Day Exploit", "Data Breach");
        ComboBox<String> sevBox    = dialogCombo("Critical", "Warning", "Info");
        ComboBox<String> statusBox = dialogCombo("Active", "Resolved", "Investigating");

        root.getChildren().addAll(
            title,
            dialogField("Zone",        zoneBox),
            dialogField("Threat type", typeBox),
            dialogField("Severity",    sevBox),
            dialogField("Status",      statusBox)
        );

        Button save = styledButton("Save threat", C_ACCENT, "white");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setPrefHeight(38);
        save.setOnAction(e -> {
            int idx    = zoneBox.getSelectionModel().getSelectedIndex();
            int zoneId = (idx >= 0 && idx < zones.size()) ? zones.get(idx).getZoneId() : 1;
            Threat threat = new Threat(0, zoneId,
                typeBox.getValue(), sevBox.getValue(),
                LocalDate.now().toString(),
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                statusBox.getValue());
            save.setDisable(true);
            save.setText("Saving...");
            new Thread(() -> {
                try {
                    threatDAO.addThreat(threat);
                    Platform.runLater(() -> {
                        dialog.close();
                        refreshTable();
                        showInfo(owner, "Success", "Threat logged successfully!");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        save.setDisable(false);
                        save.setText("Save threat");
                        showInfo(owner, "Error", "Could not save: " + ex.getMessage());
                    });
                }
            }, "SaveThreat").start();
        });
        root.getChildren().add(save);

        dialog.setScene(new Scene(root));
        dialog.showAndWait();
    }

    // ── VIEW ZONES DIALOG ─────────────────────────────────────────────────
    private void showZonesDialog(Stage owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("City zones");
        dialog.setWidth(440);
        dialog.setHeight(320);

        TableView<Zone> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Zone, Integer> idCol   = new TableColumn<>("Zone ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("zoneId"));
        TableColumn<Zone, String> nameCol  = new TableColumn<>("Zone name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("zoneName"));
        TableColumn<Zone, String> levelCol = new TableColumn<>("Threat level");
        levelCol.setCellValueFactory(new PropertyValueFactory<>("threatLevel"));

        table.getColumns().addAll(idCol, nameCol, levelCol);
        table.setPlaceholder(new Label("Loading zones..."));
        new Thread(() -> {
            List<Zone> allZones = zoneDAO.getAllZones();
            Platform.runLater(() -> table.getItems().addAll(allZones));
        }, "LoadZones").start();

        VBox root = new VBox(table);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: white;");
        VBox.setVgrow(table, Priority.ALWAYS);

        dialog.setScene(new Scene(root));
        dialog.showAndWait();
    }

    // ── GEO MAP DIALOG ────────────────────────────────────────────────────
    private void showGeoMapDialog(Stage owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        // NONE modality — WebView needs FX thread free to load tiles
        dialog.initModality(Modality.NONE);
        dialog.setTitle("Ahmedabad Zone Map");
        dialog.setWidth(860);
        dialog.setHeight(580);
        dialog.setMinWidth(700);
        dialog.setMinHeight(500);

        // Always create a fresh SuratGeoMap so getView() builds a new WebEngine
        SuratGeoMap freshMap = new SuratGeoMap(threatDAO, zoneDAO);
        VBox root = freshMap.getView();

        dialog.setScene(new Scene(root));
        dialog.show(); // show() not showAndWait() — lets WebView load async
    }

    // ── EXPORT REPORT (PDF) ───────────────────────────────────────────────
    private void exportReport(Stage owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save PDF Report");
        fc.setInitialFileName("SentinelCity_Report.pdf");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        java.io.File file = fc.showSaveDialog(owner);
        if (file == null) return;

        new Thread(() -> {
            try {
                new PDFReportExporter(threatDAO, zoneDAO).export(file);
                Platform.runLater(() -> {
                    // Auto-open the file with the system default PDF viewer
                    try {
                        if (java.awt.Desktop.isDesktopSupported()) {
                            java.awt.Desktop.getDesktop().open(file);
                        } else {
                            showInfo(owner, "Report Exported",
                                "PDF saved to:\n" + file.getAbsolutePath());
                        }
                    } catch (Exception openEx) {
                        showInfo(owner, "Report Exported",
                            "PDF saved to:\n" + file.getAbsolutePath() +
                            "\n\nCould not open automatically: " + openEx.getMessage());
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                    showInfo(owner, "Error", "PDF export failed:\n" + ex.getMessage()));
            }
        }).start();
    }

    // ── HELPERS ───────────────────────────────────────────────────────────
    private Button styledButton(String text, String bg, String fg) {
        Button b = new Button(text);
        b.setFont(Font.font("System", FontWeight.BOLD, 12));
        b.setStyle(
            "-fx-background-color: " + bg + ";" +
            "-fx-text-fill: " + fg + ";" +
            "-fx-background-radius: 7;" +
            "-fx-padding: 6 16 6 16;" +
            "-fx-cursor: hand;"
        );
        return b;
    }

    private ComboBox<String> dialogCombo(String... items) {
        ComboBox<String> cb = new ComboBox<>(FXCollections.observableArrayList(items));
        cb.setValue(items.length > 0 ? items[0] : "");
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setPrefHeight(34);
        return cb;
    }

    private HBox dialogField(String labelText, Control control) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(labelText);
        lbl.setFont(Font.font("System", 13));
        lbl.setTextFill(Color.web("#475569"));
        lbl.setMinWidth(100);
        HBox.setHgrow(control, Priority.ALWAYS);
        row.getChildren().addAll(lbl, control);
        return row;
    }

    private void showInfo(Stage owner, String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    // ── THREAT ROW ────────────────────────────────────────────────────────
    public static class ThreatRow {
        private final SimpleStringProperty threatId;
        private final SimpleStringProperty zoneName;
        private final SimpleStringProperty threatType;
        private final SimpleStringProperty severity;
        private final SimpleStringProperty threatDate;
        private final SimpleStringProperty threatTime;
        private final SimpleStringProperty status;

        public ThreatRow(int id, String zone, String type, String sev,
                         String date, String time, String status) {
            this.threatId   = new SimpleStringProperty(String.valueOf(id));
            this.zoneName   = new SimpleStringProperty(zone);
            this.threatType = new SimpleStringProperty(type);
            this.severity   = new SimpleStringProperty(sev);
            this.threatDate = new SimpleStringProperty(date);
            this.threatTime = new SimpleStringProperty(time);
            this.status     = new SimpleStringProperty(status);
        }

        public String getThreatId()   { return threatId.get(); }
        public String getZoneName()   { return zoneName.get(); }
        public String getThreatType() { return threatType.get(); }
        public String getSeverity()   { return severity.get(); }
        public String getThreatDate() { return threatDate.get(); }
        public String getThreatTime() { return threatTime.get(); }
        public String getStatus()     { return status.get(); }

        public SimpleStringProperty threatIdProperty()   { return threatId; }
        public SimpleStringProperty zoneNameProperty()   { return zoneName; }
        public SimpleStringProperty threatTypeProperty() { return threatType; }
        public SimpleStringProperty severityProperty()   { return severity; }
        public SimpleStringProperty threatDateProperty() { return threatDate; }
        public SimpleStringProperty threatTimeProperty() { return threatTime; }
        public SimpleStringProperty statusProperty()     { return status; }
    }

}
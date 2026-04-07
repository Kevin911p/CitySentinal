package CitySentinal;

import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ThreatTimelineChart — bar chart showing threats per day for the last 7 days,
 * broken down by severity (Critical / Warning / Info).
 *
 * Usage in MainDashboard:
 *   ThreatTimelineChart chart = new ThreatTimelineChart();
 *   someContainer.getChildren().add(chart.getView());
 *   chart.refresh();   // call whenever you want to reload
 */
public class ThreatTimelineChart {

    private BarChart<String, Number> barChart;
    private final DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MMM d");

    public VBox getView() {
        VBox wrapper = new VBox(10);
        wrapper.setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 12;" +
            "-fx-padding: 16;"
        );

        Label title = new Label("Threats — last 7 days");
        title.setFont(Font.font("System", FontWeight.BOLD, 13));
        title.setTextFill(Color.web("#1e293b"));

        // Axes
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("");
        xAxis.setTickLabelFont(Font.font("System", 10));

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Threats");
        yAxis.setTickLabelFont(Font.font("System", 10));
        yAxis.setMinorTickVisible(false);

        barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("");
        barChart.setLegendVisible(true);
        barChart.setAnimated(true);
        barChart.setBarGap(2);
        barChart.setCategoryGap(14);
        barChart.setPrefHeight(240);
        barChart.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-plot-background-color: #f8fafc;" +
            "-fx-horizontal-grid-lines-visible: true;" +
            "-fx-vertical-grid-lines-visible: false;"
        );

        VBox.setVgrow(barChart, Priority.ALWAYS);
        wrapper.getChildren().addAll(title, barChart);
        return wrapper;
    }

    /** Fetch data from DB and update the chart. Call from any thread. */
    public void refresh() {
        new Thread(() -> {
            Map<String, Map<String, Integer>> data = fetchData();
            Platform.runLater(() -> populateChart(data));
        }).start();
    }

    // ── Data fetching ─────────────────────────────────────────────────────
    private Map<String, Map<String, Integer>> fetchData() {
        // date (string) → { "Critical"→count, "Warning"→count, "Info"→count }
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();

        // Pre-fill last 7 days in order (oldest first)
        for (int i = 6; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).format(labelFmt);
            Map<String, Integer> sev = new LinkedHashMap<>();
            sev.put("Critical", 0);
            sev.put("Warning",  0);
            sev.put("Info",     0);
            result.put(day, sev);
        }

        String sql =
            "SELECT threat_date, severity, COUNT(*) AS cnt " +
            "FROM threats " +
            "WHERE threat_date >= CURDATE() - INTERVAL 6 DAY " +
            "GROUP BY threat_date, severity " +
            "ORDER BY threat_date";

        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                LocalDate date    = LocalDate.parse(rs.getString("threat_date"));
                String    day     = date.format(labelFmt);
                String    sev     = rs.getString("severity");
                int       cnt     = rs.getInt("cnt");

                if (result.containsKey(day) && result.get(day).containsKey(sev)) {
                    result.get(day).put(sev, cnt);
                }
            }
        } catch (SQLException e) {
            System.out.println("Timeline chart fetch error: " + e.getMessage());
        }

        return result;
    }

    // ── Chart population ──────────────────────────────────────────────────
    private void populateChart(Map<String, Map<String, Integer>> data) {
        barChart.getData().clear();

        XYChart.Series<String, Number> critSeries = new XYChart.Series<>();
        critSeries.setName("Critical");
        XYChart.Series<String, Number> warnSeries = new XYChart.Series<>();
        warnSeries.setName("Warning");
        XYChart.Series<String, Number> infoSeries = new XYChart.Series<>();
        infoSeries.setName("Info");

        for (Map.Entry<String, Map<String, Integer>> entry : data.entrySet()) {
            String day = entry.getKey();
            Map<String, Integer> sev = entry.getValue();
            critSeries.getData().add(new XYChart.Data<>(day, sev.getOrDefault("Critical", 0)));
            warnSeries.getData().add(new XYChart.Data<>(day, sev.getOrDefault("Warning",  0)));
            infoSeries.getData().add(new XYChart.Data<>(day, sev.getOrDefault("Info",     0)));
        }

        barChart.getData().addAll(critSeries, warnSeries, infoSeries);

        // Apply colours after data is added (nodes exist now)
        Platform.runLater(() -> applyBarColors());
    }

    private void applyBarColors() {
        String[][] colors = {
            {"Critical", "#ef4444", "#fca5a5"},
            {"Warning",  "#f59e0b", "#fcd34d"},
            {"Info",     "#3b82f6", "#93c5fd"},
        };
        int seriesIdx = 0;
        for (XYChart.Series<String, Number> series : barChart.getData()) {
            String fill   = colors[Math.min(seriesIdx, colors.length - 1)][1];
            String border = colors[Math.min(seriesIdx, colors.length - 1)][2];
            for (XYChart.Data<String, Number> d : series.getData()) {
                if (d.getNode() != null) {
                    d.getNode().setStyle(
                        "-fx-bar-fill: " + fill + ";" +
                        "-fx-border-color: " + border + ";" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 3;" +
                        "-fx-background-radius: 3;"
                    );
                }
            }
            seriesIdx++;
        }
    }
}

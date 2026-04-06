package CitySentinal;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainDashboard extends JFrame {

    private ThreatDAO threatDAO;
    private ZoneDAO zoneDAO;
    private JTable threatTable;
    private DefaultTableModel tableModel;
    private JLabel alertLabel;
    private JLabel totalThreatsLabel;
    private JLabel criticalCountLabel;

    public MainDashboard() {
        threatDAO = new ThreatDAO();
        zoneDAO = new ZoneDAO();

        setTitle("SentinelCity – Urban Cyber Threat Monitor");
        setSize(1000, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(30, 30, 45));
        topBar.setPreferredSize(new Dimension(1000, 55));

        JLabel titleLabel = new JLabel("  SentinelCity – Urban Cyber Threat Monitor");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));

        alertLabel = new JLabel("Checking system status...  ");
        alertLabel.setForeground(Color.YELLOW);
        alertLabel.setFont(new Font("Arial", Font.BOLD, 13));

        topBar.add(titleLabel, BorderLayout.WEST);
        topBar.add(alertLabel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // Sidebar
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(20, 20, 35));
        sidebar.setPreferredSize(new Dimension(160, 600));

        String[] navItems = {"Dashboard", "Log Threat", "View Zones", "Reports", "Exit"};
        for (String item : navItems) {
            JButton btn = new JButton(item);
            btn.setMaximumSize(new Dimension(160, 45));
            btn.setBackground(new Color(20, 20, 35));
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("Arial", Font.PLAIN, 13));
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> handleNav(item));
            sidebar.add(btn);
            sidebar.add(Box.createVerticalStrut(5));
        }
        add(sidebar, BorderLayout.WEST);

        // Main content panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(240, 242, 245));

        // Metric cards
        JPanel metricsPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        metricsPanel.setBackground(new Color(240, 242, 245));
        metricsPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));

        totalThreatsLabel = new JLabel("0", SwingConstants.CENTER);
        criticalCountLabel = new JLabel("0", SwingConstants.CENTER);
        JLabel zonesLabel = new JLabel("6", SwingConstants.CENTER);

        metricsPanel.add(createMetricCard("Total Threats Today", totalThreatsLabel, new Color(231, 76, 60)));
        metricsPanel.add(createMetricCard("Critical Incidents", criticalCountLabel, new Color(192, 57, 43)));
        metricsPanel.add(createMetricCard("City Zones Monitored", zonesLabel, new Color(41, 128, 185)));

        mainPanel.add(metricsPanel, BorderLayout.NORTH);

        // Threat table
        String[] columns = {"ID", "Zone ID", "Threat Type", "Severity", "Date", "Time", "Status"};
        tableModel = new DefaultTableModel(columns, 0);
        threatTable = new JTable(tableModel);
        threatTable.setRowHeight(28);
        threatTable.setFont(new Font("Arial", Font.PLAIN, 12));
        threatTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        threatTable.getTableHeader().setBackground(new Color(30, 30, 45));
        threatTable.getTableHeader().setForeground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(threatTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 15, 15, 15));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        add(mainPanel, BorderLayout.CENTER);

        // Load data and start alert monitor thread
        refreshTable();
        startAlertMonitor();

        setVisible(true);
    }

    // Create a metric card
    private JPanel createMetricCard(String title, JLabel valueLabel, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(color);
        card.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        card.setPreferredSize(new Dimension(200, 90));

        JLabel titleLbl = new JLabel(title, SwingConstants.CENTER);
        titleLbl.setForeground(Color.WHITE);
        titleLbl.setFont(new Font("Arial", Font.PLAIN, 12));

        valueLabel.setForeground(Color.WHITE);
        valueLabel.setFont(new Font("Arial", Font.BOLD, 28));

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    // Refresh threat table from database
    public void refreshTable() {
        tableModel.setRowCount(0);
        List<Threat> threats = threatDAO.getAllThreats();
        int criticalCount = 0;

        for (Threat t : threats) {
            tableModel.addRow(new Object[]{
                t.getThreatId(),
                t.getZoneId(),
                t.getThreatType(),
                t.getSeverity(),
                t.getThreatDate(),
                t.getThreatTime(),
                t.getStatus()
            });
            if (t.getSeverity().equals("Critical")) criticalCount++;
        }

        totalThreatsLabel.setText(String.valueOf(threats.size()));
        criticalCountLabel.setText(String.valueOf(criticalCount));
    }

    // Start background alert monitor thread
    private void startAlertMonitor() {
        AlertMonitor monitor = new AlertMonitor(alertLabel);
        Thread thread = new Thread(monitor);
        thread.setDaemon(true);
        thread.start();
    }

    // Handle sidebar navigation
    private void handleNav(String item) {
        switch (item) {
            case "Dashboard":
                refreshTable();
                break;
            case "Log Threat":
                showLogThreatDialog();
                break;
            case "View Zones":
                showZonesDialog();
                break;
            case "Reports":
                exportReport();
                break;
            case "Exit":
                System.exit(0);
                break;
        }
    }

    // Log a new threat dialog
    private void showLogThreatDialog() {
        JDialog dialog = new JDialog(this, "Log New Threat", true);
        dialog.setSize(400, 380);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new GridLayout(8, 2, 10, 10));
        dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        String[] zoneNames = {"1 - Traffic Grid", "2 - Water Systems", "3 - Power Grid",
                              "4 - Healthcare", "5 - Administration", "6 - Public WiFi"};
        String[] threatTypes = {"DDoS Attack", "Ransomware", "Phishing", "Port Scan",
                                "Unauthorized Login", "Malware"};
        String[] severities = {"Critical", "Warning", "Info"};
        String[] statuses = {"Active", "Resolved", "Investigating"};

        JComboBox<String> zoneBox = new JComboBox<>(zoneNames);
        JComboBox<String> typeBox = new JComboBox<>(threatTypes);
        JComboBox<String> severityBox = new JComboBox<>(severities);
        JComboBox<String> statusBox = new JComboBox<>(statuses);

        dialog.add(new JLabel("Zone:")); dialog.add(zoneBox);
        dialog.add(new JLabel("Threat Type:")); dialog.add(typeBox);
        dialog.add(new JLabel("Severity:")); dialog.add(severityBox);
        dialog.add(new JLabel("Status:")); dialog.add(statusBox);

        JButton saveBtn = new JButton("Save Threat");
        saveBtn.setBackground(new Color(30, 30, 45));
        saveBtn.setForeground(Color.WHITE);

        saveBtn.addActionListener(e -> {
            int zoneId = zoneBox.getSelectedIndex() + 1;
            String type = (String) typeBox.getSelectedItem();
            String severity = (String) severityBox.getSelectedItem();
            String status = (String) statusBox.getSelectedItem();
            String date = LocalDate.now().toString();
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

            Threat threat = new Threat(0, zoneId, type, severity, date, time, status);
            threatDAO.addThreat(threat);
            refreshTable();
            dialog.dispose();
            JOptionPane.showMessageDialog(this, "Threat logged successfully!");
        });

        dialog.add(new JLabel()); dialog.add(saveBtn);
        dialog.setVisible(true);
    }

    // View zones dialog
    private void showZonesDialog() {
        JDialog dialog = new JDialog(this, "City Zones", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);

        String[] cols = {"Zone ID", "Zone Name", "Threat Level"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        List<Zone> zones = zoneDAO.getAllZones();
        for (Zone z : zones) {
            model.addRow(new Object[]{z.getZoneId(), z.getZoneName(), z.getThreatLevel()});
        }

        JTable table = new JTable(model);
        dialog.add(new JScrollPane(table));
        dialog.setVisible(true);
    }

    // Export report to CSV using File I/O
    private void exportReport() {
        try {
            FileWriter fw = new FileWriter("SentinelCity_Report.csv");
            fw.write("ID,Zone ID,Threat Type,Severity,Date,Time,Status\n");

            List<Threat> threats = threatDAO.getAllThreats();
            for (Threat t : threats) {
                fw.write(t.getThreatId() + "," + t.getZoneId() + "," +
                         t.getThreatType() + "," + t.getSeverity() + "," +
                         t.getThreatDate() + "," + t.getThreatTime() + "," +
                         t.getStatus() + "\n");
            }
            fw.close();
            JOptionPane.showMessageDialog(this, "Report exported as SentinelCity_Report.csv!");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error exporting report: " + e.getMessage());
        }
    }
}
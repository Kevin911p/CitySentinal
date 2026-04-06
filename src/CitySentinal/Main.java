package CitySentinal;

import java.sql.Connection;

public class Main {

    public static void main(String[] args) {

        // Test database connection first
        Connection conn = DBConnection.getConnection();

        if (conn != null) {
            System.out.println("Database connected successfully!");

            // Insert default zones if not already present
            ZoneDAO zoneDAO = new ZoneDAO();
            if (zoneDAO.getAllZones().isEmpty()) {
                zoneDAO.addZone(new Zone(0, "Traffic Grid", "High"));
                zoneDAO.addZone(new Zone(0, "Water Systems", "High"));
                zoneDAO.addZone(new Zone(0, "Power Grid", "Medium"));
                zoneDAO.addZone(new Zone(0, "Healthcare", "Medium"));
                zoneDAO.addZone(new Zone(0, "Administration", "Low"));
                zoneDAO.addZone(new Zone(0, "Public WiFi", "Low"));
                System.out.println("Default zones added!");
            }

            // Launch the dashboard
            javax.swing.SwingUtilities.invokeLater(() -> {
                new MainDashboard();
            });

        } else {
            System.out.println("Failed to connect to database. Please check XAMPP is running.");
        }
    }
}

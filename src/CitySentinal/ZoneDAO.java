package CitySentinal;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ZoneDAO {

    public void addZone(Zone zone) {
        String sql = "INSERT INTO zones (zone_name, threat_level) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, zone.getZoneName());
            ps.setString(2, zone.getThreatLevel());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error adding zone: " + e.getMessage());
        }
    }

    public List<Zone> getAllZones() {
        List<Zone> zones = new ArrayList<>();
        String sql = "SELECT * FROM zones";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                zones.add(new Zone(
                    rs.getInt("zone_id"),
                    rs.getString("zone_name"),
                    rs.getString("threat_level")
                ));
            }
        } catch (SQLException e) {
            System.out.println("Error fetching zones: " + e.getMessage());
        }
        return zones;
    }

    public void deleteZone(int zoneId) {
        String sql = "DELETE FROM zones WHERE zone_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, zoneId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error deleting zone: " + e.getMessage());
        }
    }
}
package CitySentinal;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ThreatDAO {

    public void addThreat(Threat threat) {
        String sql = "INSERT INTO threats (zone_id, threat_type, severity, threat_date, threat_time, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, threat.getZoneId());
            ps.setString(2, threat.getThreatType());
            ps.setString(3, threat.getSeverity());
            ps.setString(4, threat.getThreatDate());
            ps.setString(5, threat.getThreatTime());
            ps.setString(6, threat.getStatus());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error adding threat: " + e.getMessage());
        }
    }

    public List<Threat> getAllThreats() {
        List<Threat> threats = new ArrayList<>();
        String sql = "SELECT * FROM threats ORDER BY threat_date DESC, threat_time DESC";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                threats.add(new Threat(
                    rs.getInt("threat_id"),
                    rs.getInt("zone_id"),
                    rs.getString("threat_type"),
                    rs.getString("severity"),
                    rs.getString("threat_date"),
                    rs.getString("threat_time"),
                    rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            System.out.println("Error fetching threats: " + e.getMessage());
        }
        return threats;
    }

    public List<Threat> getCriticalThreats() {
        List<Threat> threats = new ArrayList<>();
        String sql = "SELECT * FROM threats WHERE severity = 'Critical'";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                threats.add(new Threat(
                    rs.getInt("threat_id"),
                    rs.getInt("zone_id"),
                    rs.getString("threat_type"),
                    rs.getString("severity"),
                    rs.getString("threat_date"),
                    rs.getString("threat_time"),
                    rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            System.out.println("Error fetching critical threats: " + e.getMessage());
        }
        return threats;
    }

    public int getTotalThreatCount() {
        String sql = "SELECT COUNT(*) FROM threats";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.out.println("Error counting threats: " + e.getMessage());
        }
        return 0;
    }
}
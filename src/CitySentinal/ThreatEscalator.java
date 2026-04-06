package CitySentinal;

import java.sql.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * ThreatEscalator — background service that automatically escalates threats:
 *
 *   • Warning  → Critical  if Active for > WARN_TO_CRIT_MINUTES  (default 5 min)
 *   • Info     → Warning   if Active for > INFO_TO_WARN_MINUTES   (default 10 min)
 *
 * It also appends " [AUTO-ESCALATED]" to the threat_type so you can see
 * escalated threats clearly in the dashboard table.
 *
 * Usage in MainDashboard.start():
 *   ThreatEscalator escalator = new ThreatEscalator(() -> refreshTable());
 *   escalator.start();
 *   // on app close:
 *   escalator.stop();
 */
public class ThreatEscalator {

    private static final int WARN_TO_CRIT_MINUTES = 5;
    private static final int INFO_TO_WARN_MINUTES  = 10;
    private static final int POLL_INTERVAL_MS      = 60_000; // check every 1 minute

    private final Runnable onEscalation; // callback to refresh the UI
    private Timer timer;

    /**
     * @param onEscalation  Called (on the background thread) whenever at least
     *                      one threat is escalated. Wrap in Platform.runLater()
     *                      if you update UI here.
     */
    public ThreatEscalator(Runnable onEscalation) {
        this.onEscalation = onEscalation;
    }

    public void start() {
        timer = new Timer("ThreatEscalator", true); // daemon thread
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { runEscalationCheck(); }
        }, 10_000, POLL_INTERVAL_MS); // first run after 10s
        System.out.println("[ThreatEscalator] Started. " +
            "Warning→Critical after " + WARN_TO_CRIT_MINUTES + " min, " +
            "Info→Warning after " + INFO_TO_WARN_MINUTES + " min.");
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
            System.out.println("[ThreatEscalator] Stopped.");
        }
    }

    // ── Core logic ────────────────────────────────────────────────────────
    private void runEscalationCheck() {
        int escalated = 0;

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return;

            // Warning → Critical
            escalated += escalate(conn,
                "Warning", "Critical",
                WARN_TO_CRIT_MINUTES,
                "Warning threat active for >" + WARN_TO_CRIT_MINUTES + " min → escalated to Critical"
            );

            // Info → Warning
            escalated += escalate(conn,
                "Info", "Warning",
                INFO_TO_WARN_MINUTES,
                "Info threat active for >" + INFO_TO_WARN_MINUTES + " min → escalated to Warning"
            );

        } catch (SQLException e) {
            System.out.println("[ThreatEscalator] DB error: " + e.getMessage());
        }

        if (escalated > 0 && onEscalation != null) {
            System.out.println("[ThreatEscalator] Escalated " + escalated + " threat(s).");
            onEscalation.run();
        }
    }

    /**
     * Escalates all Active threats with {@code fromSev} severity whose
     * threat_time is older than {@code minutes} minutes (on today's date).
     *
     * Returns the number of rows updated.
     *
     * Note: This uses threat_date + threat_time columns (VARCHAR) and builds
     * a DATETIME string for comparison — works with the existing schema.
     */
    private int escalate(Connection conn,
                          String fromSev, String toSev,
                          int minutes, String logMsg) throws SQLException {

        // Build SQL that compares stored date+time against NOW() - interval
        String sql =
            "UPDATE threats " +
            "SET severity = ?, " +
            "    threat_type = CASE " +
            "        WHEN threat_type NOT LIKE '%[AUTO-ESCALATED]%' " +
            "        THEN CONCAT(threat_type, ' [AUTO-ESCALATED]') " +
            "        ELSE threat_type END " +
            "WHERE severity = ? " +
            "  AND status = 'Active' " +
            "  AND STR_TO_DATE(CONCAT(threat_date, ' ', threat_time), '%Y-%m-%d %H:%i:%s') " +
            "      < NOW() - INTERVAL ? MINUTE";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toSev);
            ps.setString(2, fromSev);
            ps.setInt(3, minutes);
            int rows = ps.executeUpdate();
            if (rows > 0) System.out.println("[ThreatEscalator] " + logMsg + " (" + rows + " rows)");
            return rows;
        }
    }
}

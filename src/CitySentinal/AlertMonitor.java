package CitySentinal;

import javax.swing.*;
import java.util.List;

public class AlertMonitor implements Runnable {

    private JLabel alertLabel;
    private ThreatDAO threatDAO;
    private volatile boolean running = true;

    public AlertMonitor(JLabel alertLabel) {
        this.alertLabel = alertLabel;
        this.threatDAO = new ThreatDAO();
    }

    @Override
    public void run() {
        while (running) {
            try {
                // Check for critical threats every 5 seconds
                List<Threat> criticalThreats = threatDAO.getCriticalThreats();

                if (criticalThreats.size() > 0) {
                    SwingUtilities.invokeLater(() -> {
                        alertLabel.setText("⚠ ALERT: " + criticalThreats.size() + " Critical Threat(s) Detected!");
                        alertLabel.setForeground(java.awt.Color.RED);
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        alertLabel.setText("✔ All Systems Normal");
                        alertLabel.setForeground(new java.awt.Color(0, 128, 0));
                    });
                }

                // Wait 5 seconds before checking again
                Thread.sleep(5000);

            } catch (InterruptedException e) {
                System.out.println("Alert Monitor stopped.");
                running = false;
            }
        }
    }

    // Call this to stop the thread
    public void stop() {
        running = false;
    }
}
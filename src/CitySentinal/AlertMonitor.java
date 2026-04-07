package CitySentinal;

import java.util.List;

// No javax.swing imports — this class is now JavaFX compatible
// The MainDashboard handles all UI updates via Platform.runLater()
public class AlertMonitor implements Runnable {

    public interface AlertCallback {
        void onUpdate(int criticalCount, boolean hasActive);
    }

    private final AlertCallback callback;
    private final ThreatDAO threatDAO;
    private volatile boolean running = true;

    public AlertMonitor(AlertCallback callback) {
        this.callback = callback;
        this.threatDAO = new ThreatDAO();
    }

    @Override
    public void run() {
        while (running) {
            try {
                List<Threat> criticalThreats = threatDAO.getCriticalThreats();
                long activeCount = criticalThreats.stream()
                    .filter(t -> "Active".equals(t.getStatus()))
                    .count();
                callback.onUpdate(criticalThreats.size(), activeCount > 0);
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                System.out.println("Alert Monitor stopped.");
                running = false;
            }
        }
    }

    public void stop() {
        running = false;
    }
}
package CitySentinal;

public class Threat {
    private int threatId;
    private int zoneId;
    private String threatType;
    private String severity;
    private String threatDate;
    private String threatTime;
    private String status;

    public Threat(int threatId, int zoneId, String threatType, String severity,
                  String threatDate, String threatTime, String status) {
        this.threatId = threatId;
        this.zoneId = zoneId;
        this.threatType = threatType;
        this.severity = severity;
        this.threatDate = threatDate;
        this.threatTime = threatTime;
        this.status = status;
    }

    public int getThreatId() { return threatId; }
    public int getZoneId() { return zoneId; }
    public String getThreatType() { return threatType; }
    public String getSeverity() { return severity; }
    public String getThreatDate() { return threatDate; }
    public String getThreatTime() { return threatTime; }
    public String getStatus() { return status; }

    public void setThreatId(int threatId) { this.threatId = threatId; }
    public void setZoneId(int zoneId) { this.zoneId = zoneId; }
    public void setThreatType(String threatType) { this.threatType = threatType; }
    public void setSeverity(String severity) { this.severity = severity; }
    public void setThreatDate(String threatDate) { this.threatDate = threatDate; }
    public void setThreatTime(String threatTime) { this.threatTime = threatTime; }
    public void setStatus(String status) { this.status = status; }
}
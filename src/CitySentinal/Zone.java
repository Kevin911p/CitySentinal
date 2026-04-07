package CitySentinal;

public class Zone {
    private int zoneId;
    private String zoneName;
    private String threatLevel;

    public Zone(int zoneId, String zoneName, String threatLevel) {
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.threatLevel = threatLevel;
    }

    public int getZoneId() { return zoneId; }
    public String getZoneName() { return zoneName; }
    public String getThreatLevel() { return threatLevel; }

    public void setZoneId(int zoneId) { this.zoneId = zoneId; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }
    public void setThreatLevel(String threatLevel) { this.threatLevel = threatLevel; }
}
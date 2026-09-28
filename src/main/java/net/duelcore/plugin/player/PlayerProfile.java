package net.duelcore.plugin.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** In-memory cache of one player's DuelCore data, backed by the database. */
public class PlayerProfile {

    private final UUID uuid;
    private String username;
    private long currency;
    private double overallPoints;
    private String countryCode;
    private String clientBrand;

    // kitId -> tier (e.g. "sword" -> "HT2")
    private final Map<String, String> kitTiers = new HashMap<>();
    // kitId -> points
    private final Map<String, Double> kitPoints = new HashMap<>();

    public PlayerProfile(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
    }

    public UUID getUuid() { return uuid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public long getCurrency() { return currency; }
    public void setCurrency(long currency) { this.currency = currency; }
    public void addCurrency(long amount) { this.currency += amount; }

    public double getOverallPoints() { return overallPoints; }
    public void setOverallPoints(double overallPoints) { this.overallPoints = overallPoints; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getClientBrand() { return clientBrand; }
    public void setClientBrand(String clientBrand) { this.clientBrand = clientBrand; }

    public String getTier(String kitId) {
        return kitTiers.getOrDefault(kitId, "NONE");
    }

    public void setTier(String kitId, String tier) {
        kitTiers.put(kitId, tier);
    }

    public double getKitPoints(String kitId) {
        return kitPoints.getOrDefault(kitId, 0.0);
    }

    public void setKitPoints(String kitId, double points) {
        kitPoints.put(kitId, points);
    }

    public Map<String, String> getAllTiers() {
        return kitTiers;
    }
}

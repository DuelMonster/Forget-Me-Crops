package com.forgetmecrops.config;

import com.forgetmecrops.enums.*;
import com.forgetmecrops.util.log.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Config: The grand spellbook of ForgetMeCrops (now in `config` package).
 */
public class Config {
    public Config() {}

    private static final Path CONFIG_DIR = Path.of("config");
    private static final Path SERVER_CONFIG_PATH = CONFIG_DIR.resolve("forget_me_crops-server.toml");
    private static final Path CLIENT_CONFIG_PATH = CONFIG_DIR.resolve("forget_me_crops-client.toml");

    private static int tickInterval = 300;
    private static int frameRediscoveryInterval = 150;
    private static int scanRangeX = 4;
    private static int scanRangeZ = 4;
    private static DurabilityMode durabilityMode = DurabilityMode.NORMAL;
    private static boolean mendingNegation = true;
    private static boolean debugLogging = false;
    private static int chestFullCooldownTicks = 300;
    private static int maxSpiralDurationTicks = 200;
    private static boolean harvestParticles = true;
    private static RotationMode rotationMode = RotationMode.FULL_ROTATION_PER_HARVEST;

    private static SeedClutterMode seedClutterMode = SeedClutterMode.REDUCED;
    private static int seedReservePerType = 80;

    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            loadServer();
            loadClient();
            logEffectiveConfig();
        } catch (Exception e) {
            LogUtils.logError("Failed to load config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            writeToml(SERVER_CONFIG_PATH, serverConfigValues(), serverHeader());
            writeToml(CLIENT_CONFIG_PATH, clientConfigValues(), clientHeader());
        } catch (Exception e) {
            LogUtils.logError("Failed to save config", e);
        }
    }

    public static void applyServerSettings(int tickInterval, int frameRediscoveryInterval, int scanRangeX, int scanRangeZ, DurabilityMode durabilityMode,
                                           boolean mendingNegation, boolean debugLogging,
                                           int chestFullCooldownTicks, int maxSpiralDurationTicks,
                                           RotationMode rotationMode, SeedClutterMode seedClutterMode,
                                           int seedReservePerType) {
        Config.tickInterval = tickInterval;
        Config.frameRediscoveryInterval = frameRediscoveryInterval;
        Config.scanRangeX = scanRangeX;
        Config.scanRangeZ = scanRangeZ;
        Config.durabilityMode = durabilityMode;
        Config.mendingNegation = mendingNegation;
        Config.debugLogging = debugLogging;
        Config.chestFullCooldownTicks = chestFullCooldownTicks;
        Config.maxSpiralDurationTicks = maxSpiralDurationTicks;
        Config.rotationMode = rotationMode;
        Config.seedClutterMode = seedClutterMode;
        Config.seedReservePerType = seedReservePerType;
    }

    public static void applyClientSettings(boolean harvestParticles) {
        Config.harvestParticles = harvestParticles;
    }

    // --- Getters ---
    public static int getTickInterval() { return tickInterval; }
    public static int getFrameRediscoveryInterval() { return frameRediscoveryInterval; }
    public static int getScanRangeX() { return scanRangeX; }
    public static int getScanRangeZ() { return scanRangeZ; }
    public static DurabilityMode getDurabilityMode() { return durabilityMode; }
    public static boolean isMendingNegation() { return mendingNegation; }
    public static boolean isDebugLogging() { return debugLogging; }
    public static int getChestFullCooldownTicks() { return chestFullCooldownTicks; }
    public static int getMaxSpiralDurationTicks() { return maxSpiralDurationTicks; }
    public static boolean isHarvestParticles() { return harvestParticles; }
    public static RotationMode getRotationMode() { return rotationMode; }
    public static SeedClutterMode getSeedClutterMode() { return seedClutterMode; }
    public static int getSeedReservePerType() { return seedReservePerType; }

    // --- Setters (used by config UI screens) ---
    public static void setTickInterval(int v) { tickInterval = v; }
    public static void setScanRangeX(int v) { scanRangeX = v; }
    public static void setScanRangeZ(int v) { scanRangeZ = v; }
    public static void setDurabilityMode(DurabilityMode v) { durabilityMode = v; }
    public static void setMendingNegation(boolean v) { mendingNegation = v; }
    public static void setDebugLogging(boolean v) { debugLogging = v; }
    public static void setChestFullCooldownTicks(int v) { chestFullCooldownTicks = v; }
    public static void setMaxSpiralDurationTicks(int v) { maxSpiralDurationTicks = v; }
    public static void setRotationMode(RotationMode v) { rotationMode = v; }
    public static void setSeedClutterMode(SeedClutterMode v) { seedClutterMode = v; }
    public static void setSeedReservePerType(int v) { seedReservePerType = v; }
    public static void setHarvestParticles(boolean v) { harvestParticles = v; }

    public static void logEffectiveConfig() {
        if (debugLogging) {
            LogUtils.logInfo("Debug config enabled: tickInterval={}, scanRangeX={}, scanRangeZ={}, rotationMode={}, seedClutterMode={}, seedReservePerType={}, harvestParticles=()", tickInterval, scanRangeX, scanRangeZ, rotationMode, seedClutterMode, seedReservePerType, harvestParticles);
        }
    }

    private static void loadServer() throws IOException {
        if (!Files.exists(SERVER_CONFIG_PATH)) {
            writeToml(SERVER_CONFIG_PATH, serverConfigValues(), serverHeader());
            return;
        }
        Map<String, String> values = readToml(SERVER_CONFIG_PATH);
        tickInterval = positiveInt(values, "tickInterval", tickInterval);
        frameRediscoveryInterval = positiveInt(values, "frameRediscoveryInterval", frameRediscoveryInterval);
        scanRangeX = positiveInt(values, "scanRangeX", scanRangeX);
        scanRangeZ = positiveInt(values, "scanRangeZ", scanRangeZ);
        durabilityMode = DurabilityMode.fromConfigValue(stringValue(values, "durabilityMode", durabilityMode.configValue()));
        mendingNegation = booleanValue(values, "mendingNegation", mendingNegation);
        debugLogging = booleanValue(values, "debugLogging", debugLogging);
        chestFullCooldownTicks = nonNegativeInt(values, "chestFullCooldownTicks", chestFullCooldownTicks);
        maxSpiralDurationTicks = positiveInt(values, "maxSpiralDurationTicks", maxSpiralDurationTicks);
        rotationMode = RotationMode.fromConfigValue(stringValue(values, "rotationMode", rotationMode.configValue()));
        seedClutterMode = SeedClutterMode.fromConfigValue(stringValue(values, "seedClutterMode", seedClutterMode.configValue()));
        seedReservePerType = nonNegativeInt(values, "seedReservePerType", seedReservePerType);
    }

    private static void loadClient() throws IOException {
        if (!Files.exists(CLIENT_CONFIG_PATH)) {
            writeToml(CLIENT_CONFIG_PATH, clientConfigValues(), clientHeader());
            return;
        }
        Map<String, String> values = readToml(CLIENT_CONFIG_PATH);
        harvestParticles = booleanValue(values, "harvestParticles", harvestParticles);
    }

    private static Map<String, Object> serverConfigValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tickInterval", tickInterval);
        values.put("frameRediscoveryInterval", frameRediscoveryInterval);
        values.put("scanRangeX", scanRangeX);
        values.put("scanRangeZ", scanRangeZ);
        values.put("durabilityMode", durabilityMode.configValue());
        values.put("mendingNegation", mendingNegation);
        values.put("debugLogging", debugLogging);
        values.put("chestFullCooldownTicks", chestFullCooldownTicks);
        values.put("maxSpiralDurationTicks", maxSpiralDurationTicks);
        values.put("rotationMode", rotationMode.configValue());
        values.put("seedClutterMode", seedClutterMode.configValue());
        values.put("seedReservePerType", seedReservePerType);
        return values;
    }

    private static Map<String, Object> clientConfigValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("harvestParticles", harvestParticles);
        return values;
    }

    private static List<String> serverHeader() {
        return List.of("# ForgetMeCrops server config", "# Gameplay and server-side debug behavior.", "");
    }

    private static List<String> clientHeader() {
        return List.of("# ForgetMeCrops client config", "# Client-side visual preferences.", "");
    }

    private static void writeToml(Path path, Map<String, Object> values, List<String> headerLines) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (String headerLine : headerLines) builder.append(headerLine).append(System.lineSeparator());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            builder.append(entry.getKey()).append(" = ").append(formatTomlValue(entry.getValue())).append(System.lineSeparator());
        }
        Files.writeString(path, builder.toString());
    }

    private static Map<String, String> readToml(Path path) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(path)) {
            String line = stripComments(rawLine).trim();
            if (line.isEmpty() || line.startsWith("[")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            values.put(key, val);
        }
        return values;
    }

    private static String stripComments(String line) {
        boolean quoted = false;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') quoted = !quoted;
            if (c == '#' && !quoted) break;
            out.append(c);
        }
        return out.toString();
    }

    private static String formatTomlValue(Object value) {
        if (value instanceof String stringValue) return '"' + stringValue + '"';
        return String.valueOf(value);
    }

    private static String stringValue(Map<String, String> values, String key, String fallback) {
        String rawValue = values.get(key);
        if (rawValue == null) return fallback;

        String trimmed = rawValue.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean booleanValue(Map<String, String> values, String key, boolean fallback) {
        String rawValue = values.get(key);
        if (rawValue == null) return fallback;
        if ("true".equalsIgnoreCase(rawValue)) return true;
        if ("false".equalsIgnoreCase(rawValue)) return false;
        LogUtils.logWarn("Ignoring invalid boolean for {} in {}: {}", key, configFileForKey(key), rawValue);
        return fallback;
    }

    private static int positiveInt(Map<String, String> values, String key, int fallback) {
        return boundedInt(values, key, fallback, 1);
    }

    private static int nonNegativeInt(Map<String, String> values, String key, int fallback) {
        return boundedInt(values, key, fallback, 0);
    }

    private static int boundedInt(Map<String, String> values, String key, int fallback, int minValue) {
        String rawValue = values.get(key);
        if (rawValue == null) return fallback;
        try {
            int v = Integer.parseInt(rawValue.trim());
            if (v < minValue) return fallback;
            return v;
        } catch (NumberFormatException ignored) {}
        LogUtils.logWarn("Ignoring invalid integer for {} in {}: {}", key, configFileForKey(key), rawValue);
        return fallback;
    }

    private static String configFileForKey(String key) {
        return "harvestParticles".equals(key) ? "forget_me_crops-client.toml" : "forget_me_crops-server.toml";
    }
}

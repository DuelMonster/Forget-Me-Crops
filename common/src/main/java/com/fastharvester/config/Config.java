package com.fastharvester.config;

import com.fastharvester.enums.*;
import com.fastharvester.Constants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Config: The grand spellbook of FastHarvester (now in `config` package).
 */
public class Config {
    public Config() {}

    private static final Path CONFIG_DIR = Path.of("config");
    private static final Path SERVER_CONFIG_PATH = CONFIG_DIR.resolve("fastharvester-server.toml");
    private static final Path CLIENT_CONFIG_PATH = CONFIG_DIR.resolve("fastharvester-client.toml");

    public static int tickInterval = 300;
    public static int frameRediscoveryInterval = 600;
    public static int scanRangeX = 4;
    public static int scanRangeZ = 4;
    public static DurabilityMode durabilityMode = DurabilityMode.NORMAL;
    public static boolean mendingNegation = true;
    public static boolean debugLogging = true;
    public static int chestFullCooldownTicks = 300;
    public static int maxSpiralDurationTicks = 200;
    public static boolean harvestParticles = true;
    public static RotationMode rotationMode = RotationMode.FULL_ROTATION_PER_HARVEST;

    public static SeedClutterMode seedClutterMode = SeedClutterMode.REDUCED;
    public static int seedReservePerType = 80;

    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            loadServer();
            loadClient();
            logEffectiveConfig();
        } catch (Exception e) {
            Constants.logError("Failed to load config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            writeToml(SERVER_CONFIG_PATH, serverConfigValues(), serverHeader());
            writeToml(CLIENT_CONFIG_PATH, clientConfigValues(), clientHeader());
        } catch (Exception e) {
            Constants.logError("Failed to save config", e);
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

    public static void logEffectiveConfig() {
        if (debugLogging) {
            Constants.logInfo("Debug config enabled: tickInterval={}, scanRangeX={}, scanRangeZ={}, rotationMode={}, seedClutterMode={}, seedReservePerType={}, harvestParticles=()", tickInterval, scanRangeX, scanRangeZ, rotationMode, seedClutterMode, seedReservePerType, harvestParticles);
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
        return List.of("# FastHarvester server config", "# Gameplay and server-side debug behavior.", "");
    }

    private static List<String> clientHeader() {
        return List.of("# FastHarvester client config", "# Client-side visual preferences.", "");
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

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) return value.substring(1, value.length() - 1);
        return value;
    }

    private static String stringValue(Map<String, String> values, String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    private static boolean booleanValue(Map<String, String> values, String key, boolean fallback) {
        String rawValue = values.get(key);
        if (rawValue == null) return fallback;
        if ("true".equalsIgnoreCase(rawValue)) return true;
        if ("false".equalsIgnoreCase(rawValue)) return false;
        Constants.logWarn("Ignoring invalid boolean for {} in {}: {}", key, configFileForKey(key), rawValue);
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
        Constants.logWarn("Ignoring invalid integer for {} in {}: {}", key, configFileForKey(key), rawValue);
        return fallback;
    }

    private static String configFileForKey(String key) {
        return "harvestParticles".equals(key) ? CLIENT_CONFIG_PATH.getFileName().toString() : SERVER_CONFIG_PATH.getFileName().toString();
    }
}

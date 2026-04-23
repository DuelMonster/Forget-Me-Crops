/**
 * Config: The grand spellbook of FastHarvester!
 * <p>
 * This class holds every tweakable knob, lever, and secret handshake for the mod. It loads, saves, and syncs config values
 * across all loaders, making sure your farm is always running at peak efficiency (or maximum chaos, if you prefer).
 * </p>
 * <p>
 * Why does this matter? Because a good config system is the difference between "Why isn't this working?" and "Wow, that was easy!"
 * </p>
 * <p>
 * Loader: Agnostic. Mood: Helpful. Attitude: "Let me handle the boring stuff, you go grow some crops!"
 * </p>
 */
package com.fastharvester;

// ⚙️ Config: the gentle puppet master of behavior. Tweak with care; it notices everything.
// Why it matters: changes here change how your virtual garden feels.

import com.fastharvester.enums.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);
    private static final Path CONFIG_DIR = Path.of("config");
    private static final Path SERVER_CONFIG_PATH = CONFIG_DIR.resolve("fastharvester-server.toml");
    private static final Path CLIENT_CONFIG_PATH = CONFIG_DIR.resolve("fastharvester-client.toml");

    public static int tickInterval = 300;
    public static int frameRediscoveryInterval = 100;
    public static int scanRange = 4; // 9x9 area
    public static DurabilityMode durabilityMode = DurabilityMode.NORMAL;
    public static boolean mendingNegation = true;
    public static boolean debugLogging = true;
    public static int chestFullCooldownTicks = 100;
    public static int maxSpiralDurationTicks = 100;
    public static boolean harvestParticles = true;
    public static RotationMode rotationMode = RotationMode.FOLLOW_HARVEST_SPIRAL;
    public static SeedClutterMode seedClutterMode = SeedClutterMode.REDUCED;
    public static int seedReservePerType = 80;

    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            loadServer();
            loadClient();
            logEffectiveConfig();
        } catch (Exception e) {
            LOGGER.error("[FastHarvester] Failed to load config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            writeToml(SERVER_CONFIG_PATH, serverConfigValues(), serverHeader());
            writeToml(CLIENT_CONFIG_PATH, clientConfigValues(), clientHeader());
        } catch (Exception e) {
            LOGGER.error("[FastHarvester] Failed to save config", e);
        }
    }


    /**
     * Apply server-side settings programmatically.
     * Humanized note: this lets tests and loader-specific code push config values.
     */
    public static void applyServerSettings(int tickInterval, int frameRediscoveryInterval, int scanRange, DurabilityMode durabilityMode,
                                           boolean mendingNegation, boolean debugLogging,
                                           int chestFullCooldownTicks, int maxSpiralDurationTicks,
                                           RotationMode rotationMode, SeedClutterMode seedClutterMode,
                                           int seedReservePerType) {
        Config.tickInterval = tickInterval;
        Config.frameRediscoveryInterval = frameRediscoveryInterval;
        Config.scanRange = scanRange;
        Config.durabilityMode = durabilityMode;
        Config.mendingNegation = mendingNegation;
        Config.debugLogging = debugLogging;
        Config.chestFullCooldownTicks = chestFullCooldownTicks;
        Config.maxSpiralDurationTicks = maxSpiralDurationTicks;
        Config.rotationMode = rotationMode;
        Config.seedClutterMode = seedClutterMode;
        Config.seedReservePerType = seedReservePerType;
    }

    /**
     * Apply client-side settings programmatically.
     */
    public static void applyClientSettings(boolean harvestParticles) {
        Config.harvestParticles = harvestParticles;
    }

    /**
     * Log the currently effective configuration values (debug only).
     */
    public static void logEffectiveConfig() {
        if (debugLogging) {
            LOGGER.info("[FastHarvester] Debug config enabled: tickInterval={}, scanRange={}, rotationMode={}, seedClutterMode={}, seedReservePerType={}, harvestParticles={}",
                    tickInterval, scanRange, rotationMode, seedClutterMode, seedReservePerType, harvestParticles);
        }
    }

    /**
     * Internal: read server config file and populate values.
     */
    private static void loadServer() throws IOException {
        if (!Files.exists(SERVER_CONFIG_PATH)) {
            writeToml(SERVER_CONFIG_PATH, serverConfigValues(), serverHeader());
            return;
        }

        Map<String, String> values = readToml(SERVER_CONFIG_PATH);
        tickInterval = positiveInt(values, "tickInterval", tickInterval);
        frameRediscoveryInterval = positiveInt(values, "frameRediscoveryInterval", frameRediscoveryInterval);
        scanRange = positiveInt(values, "scanRange", scanRange);
        durabilityMode = DurabilityMode.fromConfigValue(stringValue(values, "durabilityMode", durabilityMode.configValue()));
        mendingNegation = booleanValue(values, "mendingNegation", mendingNegation);
        debugLogging = booleanValue(values, "debugLogging", debugLogging);
        chestFullCooldownTicks = nonNegativeInt(values, "chestFullCooldownTicks", chestFullCooldownTicks);
        maxSpiralDurationTicks = positiveInt(values, "maxSpiralDurationTicks", maxSpiralDurationTicks);
        rotationMode = RotationMode.fromConfigValue(stringValue(values, "rotationMode", rotationMode.configValue()));
        seedClutterMode = SeedClutterMode.fromConfigValue(stringValue(values, "seedClutterMode", seedClutterMode.configValue()));
        seedReservePerType = nonNegativeInt(values, "seedReservePerType", seedReservePerType);
    }

    /**
     * Internal: read client config file and populate values.
     */
    private static void loadClient() throws IOException {
        if (!Files.exists(CLIENT_CONFIG_PATH)) {
            writeToml(CLIENT_CONFIG_PATH, clientConfigValues(), clientHeader());
            return;
        }

        Map<String, String> values = readToml(CLIENT_CONFIG_PATH);
        harvestParticles = booleanValue(values, "harvestParticles", harvestParticles);
    }

    /**
     * Helper: produce a map of server config keys and values for serialization.
     */
    private static Map<String, Object> serverConfigValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tickInterval", tickInterval);
        values.put("frameRediscoveryInterval", frameRediscoveryInterval);
        values.put("scanRange", scanRange);
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

    /**
     * Helper: produce a map of client config keys and values for serialization.
     */
    private static Map<String, Object> clientConfigValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("harvestParticles", harvestParticles);
        return values;
    }

    /**
     * Helper: header lines for server config TOML file.
     */
    private static List<String> serverHeader() {
        return List.of(
                "# FastHarvester server config",
                "# Gameplay and server-side debug behavior.",
                ""
        );
    }

    /**
     * Helper: header lines for client config TOML file.
     */
    private static List<String> clientHeader() {
        return List.of(
                "# FastHarvester client config",
                "# Client-side visual preferences.",
                ""
        );
    }

    /**
     * Internal: write a minimal TOML file with the provided key/value pairs.
     */
    private static void writeToml(Path path, Map<String, Object> values, List<String> headerLines) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (String headerLine : headerLines) {
            builder.append(headerLine).append(System.lineSeparator());
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            builder.append(entry.getKey())
                    .append(" = ")
                    .append(formatTomlValue(entry.getValue()))
                    .append(System.lineSeparator());
        }
        Files.writeString(path, builder.toString());
    }

    /**
     * Internal: read a minimal TOML-like file into a map of raw string values.
     */
    private static Map<String, String> readToml(Path path) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(path)) {
            String line = stripComments(rawLine).trim();
            if (line.isEmpty() || line.startsWith("[")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!key.isEmpty()) {
                values.put(key, unquote(value));
            }
        }
        return values;
    }

    /**
     * Remove comments from a line while respecting quoted strings.
     */
    private static String stripComments(String line) {
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"' && (index == 0 || line.charAt(index - 1) != '\\')) {
                quoted = !quoted;
            }
            if (ch == '#' && !quoted) {
                return line.substring(0, index);
            }
        }
        return line;
    }

    /**
     * Format a Java value into a TOML-like literal for writing.
     */
    private static String formatTomlValue(Object value) {
        if (value instanceof String stringValue) {
            return '"' + stringValue.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
        return String.valueOf(value);
    }

    /**
     * Remove surrounding quotes and unescape common sequences.
     */
    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            String content = value.substring(1, value.length() - 1);
            return content.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }

    /**
     * Helper: get a string value from parsed config map with fallback.
     */
    private static String stringValue(Map<String, String> values, String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    /**
     * Helper: parse a boolean value from parsed config map with fallback.
     */
    private static boolean booleanValue(Map<String, String> values, String key, boolean fallback) {
        String rawValue = values.get(key);
        if (rawValue == null) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(rawValue)) {
            return true;
        }
        if ("false".equalsIgnoreCase(rawValue)) {
            return false;
        }
        LOGGER.warn("[FastHarvester] Ignoring invalid boolean for {} in {}: {}", key, configFileForKey(key), rawValue);
        return fallback;
    }

    /**
     * Helper: parse a positive integer from parsed config map with fallback.
     */
    private static int positiveInt(Map<String, String> values, String key, int fallback) {
        return boundedInt(values, key, fallback, 1);
    }

    /**
     * Helper: parse a non-negative integer from parsed config map with fallback.
     */
    private static int nonNegativeInt(Map<String, String> values, String key, int fallback) {
        return boundedInt(values, key, fallback, 0);
    }

    /**
     * Helper: parse an integer with a minimum bound from parsed config map with fallback.
     */
    private static int boundedInt(Map<String, String> values, String key, int fallback, int minValue) {
        String rawValue = values.get(key);
        if (rawValue == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(rawValue);
            if (parsed >= minValue) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        LOGGER.warn("[FastHarvester] Ignoring invalid integer for {} in {}: {}", key, configFileForKey(key), rawValue);
        return fallback;
    }

    /**
     * Helper: determine which config file a key belongs to (client vs server).
     */
    private static String configFileForKey(String key) {
        return "harvestParticles".equals(key) ? CLIENT_CONFIG_PATH.getFileName().toString() : SERVER_CONFIG_PATH.getFileName().toString();
    }
}

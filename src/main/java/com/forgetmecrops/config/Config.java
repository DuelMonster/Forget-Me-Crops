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
 * Config: The grand spellbook of Forget-Me-Crops — all settings in one convenient static class!
 * <p>
 * Stores every configuration option as static fields (because the mod only ever runs one
 * configuration at a time, and instance management would just add ceremony for no benefit).
 * Handles TOML file loading and saving to separate server and client config files, validates
 * all values with sensible bounds, and provides getters for reads plus setters for the
 * in-game YACL config UI to push changes back through.
 * </p>
 * <p>
 * The server/client file split is intentional: server settings affect gameplay mechanics
 * that should ideally match across all players on a server, while client settings are
 * purely about visual preferences and don't need to be synchronized. Vibes, basically.
 * </p>
 */
public class Config {
    // Required for the Config() constructor pattern used by ForgetMeCrops.CONFIG; does nothing on its own
    public Config() {}

    // The paths to each config file. Server and client get separate files so they can have separate
    // lifecycles — server settings are serious business; client settings are just vibes.
    private static final Path CONFIG_DIR = Path.of("config");
    private static final Path SERVER_CONFIG_PATH = CONFIG_DIR.resolve("forgetmecrops-server.toml");
    private static final Path CLIENT_CONFIG_PATH = CONFIG_DIR.resolve("forgetmecrops-client.toml");

    // Server-side gameplay settings with their working defaults (copied from ConfigDefaults for boot safety)
    private static int tickInterval = ConfigDefaults.TICK_INTERVAL_DEFAULT;
    private static int frameRediscoveryInterval = ConfigDefaults.FRAME_REDISCOVERY_INTERVAL_DEFAULT;
    private static int scanRangeX = ConfigDefaults.SCAN_RANGE_X_DEFAULT;
    private static int scanRangeZ = ConfigDefaults.SCAN_RANGE_Z_DEFAULT;
    private static DurabilityMode durabilityMode = DurabilityMode.NORMAL;
    private static boolean mendingProtection = ConfigDefaults.MENDING_PROTECTION_DEFAULT;
    private static boolean debugLogging = ConfigDefaults.DEBUG_LOGGING_DEFAULT;
    private static int chestFullCooldownTicks = ConfigDefaults.CHEST_FULL_COOLDOWN_DEFAULT;
    private static int maxSpiralDurationTicks = ConfigDefaults.MAX_SPIRAL_DEFAULT;
    private static boolean harvestParticles = ConfigDefaults.HARVEST_PARTICLES_DEFAULT;
    private static RotationMode rotationMode = RotationMode.FULL_ROTATION;

    // Seed economy settings — how many seeds to keep and when to stop hoarding
    private static SeedClutterMode seedClutterMode = SeedClutterMode.REDUCED;
    private static int seedReservePerType = ConfigDefaults.SEED_RESERVE_DEFAULT;

    /**
     * Loads both server and client config files from disk (creating them with defaults if absent).
     * Call this during mod initialization before anything reads config values.
     * If loading fails, the working defaults stay in place — things will still work, just unconfigured.
     */
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

    /**
     * Saves the current settings back to both config files on disk.
     * Called by the config screen's save runnable when the player closes the UI.
     * If saving fails, it's logged and the in-memory state remains — the player's changes
     * survive this session but won't persist through a restart. Slightly tragic.
     */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            writeToml(SERVER_CONFIG_PATH, serverConfigValues(), serverHeader());
            writeToml(CLIENT_CONFIG_PATH, clientConfigValues(), clientHeader());
        } catch (Exception e) {
            LogUtils.logError("Failed to save config", e);
        }
    }

    /**
     * Bulk-applies all server settings in one call. Typically invoked after reading the TOML file
     * so that all fields update atomically rather than one-by-one. Avoids partial-state weirdness.
     *
     * @param tickInterval            ticks between farm scans
     * @param frameRediscoveryInterval ticks between frame rediscovery passes
     * @param scanRangeX              east-west scan radius in blocks
     * @param scanRangeZ              north-south scan radius in blocks
     * @param durabilityMode          how much wear tools take per harvest
        * @param mendingProtection       if true, Mending-enchanted hoes are protected from this mod's durability damage
     * @param debugLogging            if true, emit verbose DEBUG logs
     * @param chestFullCooldownTicks  ticks to wait before retrying a full chest
     * @param maxSpiralDurationTicks  max ticks for one spiral scan pass
     * @param rotationMode            how the frame rotates during harvest
     * @param seedClutterMode         how excess seeds are managed in the chest
     * @param seedReservePerType      minimum seeds to keep per crop type before discarding extras
     */
    public static void applyServerSettings(int tickInterval, int frameRediscoveryInterval, int scanRangeX, int scanRangeZ, DurabilityMode durabilityMode,
                                           boolean mendingProtection, boolean debugLogging,
                                           int chestFullCooldownTicks, int maxSpiralDurationTicks,
                                           RotationMode rotationMode, SeedClutterMode seedClutterMode,
                                           int seedReservePerType) {
        Config.tickInterval = clamp(tickInterval, ConfigDefaults.TICK_INTERVAL_MIN, ConfigDefaults.TICK_INTERVAL_MAX);
        Config.frameRediscoveryInterval = clamp(frameRediscoveryInterval, ConfigDefaults.FRAME_REDISCOVERY_INTERVAL_MIN, ConfigDefaults.FRAME_REDISCOVERY_INTERVAL_MAX);
        Config.scanRangeX = clamp(scanRangeX, ConfigDefaults.SCAN_RANGE_X_MIN, ConfigDefaults.SCAN_RANGE_X_MAX);
        Config.scanRangeZ = clamp(scanRangeZ, ConfigDefaults.SCAN_RANGE_Z_MIN, ConfigDefaults.SCAN_RANGE_Z_MAX);
        Config.durabilityMode = durabilityMode;
        Config.mendingProtection = mendingProtection;
        Config.debugLogging = debugLogging;
        Config.chestFullCooldownTicks = clamp(chestFullCooldownTicks, ConfigDefaults.CHEST_FULL_COOLDOWN_MIN, ConfigDefaults.CHEST_FULL_COOLDOWN_MAX);
        Config.maxSpiralDurationTicks = clamp(maxSpiralDurationTicks, ConfigDefaults.MAX_SPIRAL_MIN, ConfigDefaults.MAX_SPIRAL_MAX);
        Config.rotationMode = rotationMode;
        Config.seedClutterMode = seedClutterMode;
        Config.seedReservePerType = clamp(seedReservePerType, ConfigDefaults.SEED_RESERVE_MIN, ConfigDefaults.SEED_RESERVE_MAX);
    }

    /**
     * Applies the client-side visual preference settings. Currently just harvest particles.
     * Could grow larger over time if we add more client-only options. For now: do you want sparkles?
     *
     * @param harvestParticles true to show harvest dust/particle effects; false for a quiet, minimalist harvest
     */
    public static void applyClientSettings(boolean harvestParticles) {
        Config.harvestParticles = harvestParticles;
    }

    // --- Getters: what the rest of the mod reads when it needs a setting ---
    // All static: one config, one truth, one farm to rule them all.
    public static int getTickInterval() { return tickInterval; }
    /** Returns the frame rediscovery interval in ticks. After this many ticks, frames are re-scanned for validity. */
    public static int getFrameRediscoveryInterval() { return frameRediscoveryInterval; }
    public static int getScanRangeX() { return scanRangeX; }
    public static int getScanRangeZ() { return scanRangeZ; }
    public static DurabilityMode getDurabilityMode() { return durabilityMode; }
    /** True means Mending-enchanted hoes are protected from this mod's durability damage. */
    public static boolean isMendingProtection() { return mendingProtection; }
    /** True if verbose debug logging is enabled. Your console will know everything. */
    public static boolean isDebugLogging() { return debugLogging; }
    public static int getChestFullCooldownTicks() { return chestFullCooldownTicks; }
    public static int getMaxSpiralDurationTicks() { return maxSpiralDurationTicks; }
    /** True if harvest dust particles and effects should be shown. For the farmers who want the drama. */
    public static boolean isHarvestParticles() { return harvestParticles; }
    public static RotationMode getRotationMode() { return rotationMode; }
    public static SeedClutterMode getSeedClutterMode() { return seedClutterMode; }
    /** Returns the minimum number of seeds to keep per crop type before excess are discarded. */
    public static int getSeedReservePerType() { return seedReservePerType; }

    // --- Setters: used by the in-game config UI to push changes back through ---
    // These intentionally skip the server-side applyServerSettings bulk path for individual field updates.
    public static void setTickInterval(int v) { tickInterval = clamp(v, ConfigDefaults.TICK_INTERVAL_MIN, ConfigDefaults.TICK_INTERVAL_MAX); }
    public static void setFrameRediscoveryInterval(int v) { frameRediscoveryInterval = clamp(v, ConfigDefaults.FRAME_REDISCOVERY_INTERVAL_MIN, ConfigDefaults.FRAME_REDISCOVERY_INTERVAL_MAX); }
    public static void setScanRangeX(int v) { scanRangeX = clamp(v, ConfigDefaults.SCAN_RANGE_X_MIN, ConfigDefaults.SCAN_RANGE_X_MAX); }
    public static void setScanRangeZ(int v) { scanRangeZ = clamp(v, ConfigDefaults.SCAN_RANGE_Z_MIN, ConfigDefaults.SCAN_RANGE_Z_MAX); }
    public static void setDurabilityMode(DurabilityMode v) { durabilityMode = v; }
    public static void setMendingProtection(boolean v) { mendingProtection = v; }
    public static void setDebugLogging(boolean v) { debugLogging = v; }
    public static void setChestFullCooldownTicks(int v) { chestFullCooldownTicks = clamp(v, ConfigDefaults.CHEST_FULL_COOLDOWN_MIN, ConfigDefaults.CHEST_FULL_COOLDOWN_MAX); }
    public static void setMaxSpiralDurationTicks(int v) { maxSpiralDurationTicks = clamp(v, ConfigDefaults.MAX_SPIRAL_MIN, ConfigDefaults.MAX_SPIRAL_MAX); }
    public static void setRotationMode(RotationMode v) { rotationMode = v; }
    public static void setSeedClutterMode(SeedClutterMode v) { seedClutterMode = v; }
    public static void setSeedReservePerType(int v) { seedReservePerType = clamp(v, ConfigDefaults.SEED_RESERVE_MIN, ConfigDefaults.SEED_RESERVE_MAX); }
    public static void setHarvestParticles(boolean v) { harvestParticles = v; }

    /**
     * Dumps the current effective config to INFO if debug logging is on.
     * Useful at startup for confirming the config loaded the way you intended it to.
     * Warning: if you're confused about a behavior, this is the first place to look.
     */
    public static void logEffectiveConfig() {
        if (debugLogging) {
            LogUtils.logInfo("Debug config enabled: tickInterval={}, scanRangeX={}, scanRangeZ={}, rotationMode={}, seedClutterMode={}, seedReservePerType={}, harvestParticles=()", tickInterval, scanRangeX, scanRangeZ, rotationMode, seedClutterMode, seedReservePerType, harvestParticles);
        }
    }

    /**
     * Reads the server config file from disk, or creates a fresh one with defaults if missing.
     * Each key is parsed individually with fallback to the current default, so adding new keys
     * to a future version won't break existing user configs — unknown keys are simply ignored.
     *
     * @throws IOException if the file read or creation fails (handled by load() which logs and continues)
     */
    private static void loadServer() throws IOException {
        if (!Files.exists(SERVER_CONFIG_PATH)) {
            writeToml(SERVER_CONFIG_PATH, serverConfigValues(), serverHeader());
            return;
        }
        Map<String, String> values = readToml(SERVER_CONFIG_PATH);
        tickInterval = boundedInt(values, "tickInterval", tickInterval, ConfigDefaults.TICK_INTERVAL_MIN, ConfigDefaults.TICK_INTERVAL_MAX);
        frameRediscoveryInterval = boundedInt(values, "frameRediscoveryInterval", frameRediscoveryInterval, ConfigDefaults.FRAME_REDISCOVERY_INTERVAL_MIN, ConfigDefaults.FRAME_REDISCOVERY_INTERVAL_MAX);
        scanRangeX = boundedInt(values, "scanRangeX", scanRangeX, ConfigDefaults.SCAN_RANGE_X_MIN, ConfigDefaults.SCAN_RANGE_X_MAX);
        scanRangeZ = boundedInt(values, "scanRangeZ", scanRangeZ, ConfigDefaults.SCAN_RANGE_Z_MIN, ConfigDefaults.SCAN_RANGE_Z_MAX);
        durabilityMode = DurabilityMode.fromConfigValue(stringValue(values, "durabilityMode", durabilityMode.configValue()));
        mendingProtection = booleanValue(values, "mendingProtection", mendingProtection);
        debugLogging = booleanValue(values, "debugLogging", debugLogging);
        chestFullCooldownTicks = boundedInt(values, "chestFullCooldownTicks", chestFullCooldownTicks, ConfigDefaults.CHEST_FULL_COOLDOWN_MIN, ConfigDefaults.CHEST_FULL_COOLDOWN_MAX);
        maxSpiralDurationTicks = boundedInt(values, "maxSpiralDurationTicks", maxSpiralDurationTicks, ConfigDefaults.MAX_SPIRAL_MIN, ConfigDefaults.MAX_SPIRAL_MAX);
        rotationMode = RotationMode.fromConfigValue(stringValue(values, "rotationMode", rotationMode.configValue()));
        seedClutterMode = SeedClutterMode.fromConfigValue(stringValue(values, "seedClutterMode", seedClutterMode.configValue()));
        seedReservePerType = boundedInt(values, "seedReservePerType", seedReservePerType, ConfigDefaults.SEED_RESERVE_MIN, ConfigDefaults.SEED_RESERVE_MAX);
    }

    /**
     * Reads the client config file from disk, or creates a fresh one with defaults if missing.
     * Client settings are separate from server settings so singleplayer and multiplayer can have
     * independent visual preferences without one overwriting the other.
     *
     * @throws IOException if the file read or creation fails (handled by load() which logs and continues)
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
     * Returns an ordered map of all server-side config keys and their current values.
     * Used both for writing a fresh config file and for knowing which fields to save on demand.
     * Order matters — LinkedHashMap preserves insertion order for clean TOML output.
     */
    private static Map<String, Object> serverConfigValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tickInterval", tickInterval);
        values.put("frameRediscoveryInterval", frameRediscoveryInterval);
        values.put("scanRangeX", scanRangeX);
        values.put("scanRangeZ", scanRangeZ);
        values.put("durabilityMode", durabilityMode.configValue());
        values.put("mendingProtection", mendingProtection);
        values.put("debugLogging", debugLogging);
        values.put("chestFullCooldownTicks", chestFullCooldownTicks);
        values.put("maxSpiralDurationTicks", maxSpiralDurationTicks);
        values.put("rotationMode", rotationMode.configValue());
        values.put("seedClutterMode", seedClutterMode.configValue());
        values.put("seedReservePerType", seedReservePerType);
        return values;
    }

    /** Returns the single client-side config key (harvestParticles) and its current value. */
    private static Map<String, Object> clientConfigValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("harvestParticles", harvestParticles);
        return values;
    }

    /** Returns the TOML header comment lines for the server config file. */
    private static List<String> serverHeader() {
        return List.of("# Forget-Me-Crops server config", "# Gameplay and server-side debug behavior.", "");
    }

    /** Returns the TOML header comment lines for the client config file. */
    private static List<String> clientHeader() {
        return List.of("# Forget-Me-Crops client config", "# Client-side visual preferences.", "");
    }

    /**
     * Writes a flat TOML file with the given header lines followed by one key = value per entry.
     * Values are formatted via formatTomlValue so strings get quoted and booleans/ints are bare.
     *
     * @param path        where to write the file (will be created or overwritten)
     * @param values      ordered map of key → value to write
     * @param headerLines comment lines to prepend (typically two summary lines + a blank)
     * @throws IOException if the write fails
     */
    private static void writeToml(Path path, Map<String, Object> values, List<String> headerLines) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (String headerLine : headerLines) builder.append(headerLine).append(System.lineSeparator());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            builder.append(entry.getKey()).append(" = ").append(formatTomlValue(entry.getValue())).append(System.lineSeparator());
        }
        Files.writeString(path, builder.toString());
    }

    /**
     * Parses a flat TOML file into a string-keyed map. Strips comments, trims whitespace,
     * ignores blank lines and TOML section headers ([...]), and splits on the first {@code =}.
     * This is intentionally minimal — the config schema is flat, so full TOML parsing isn't needed.
     *
     * @param path the TOML file to read
     * @return map of key → raw string value (still needs type conversion by callers)
     * @throws IOException if the file read fails
     */
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

    /**
     * Strips inline TOML comments from a line, respecting quoted string boundaries.
     * A {@code #} character inside a quoted value is not treated as a comment marker.
     * Simple but correct for the flat config format we use.
     */
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

    /**
     * Formats a config value for TOML output: strings get double-quoted, booleans/ints are bare.
     * Used when writing fresh config files. Nothing fancy, because the config format isn't fancy.
     */
    private static String formatTomlValue(Object value) {
        if (value instanceof String stringValue) return '"' + stringValue + '"';
        return String.valueOf(value);
    }

    /**
     * Reads a string value from the parsed TOML map, stripping surrounding double-quotes.
     * Returns the fallback if the key is absent. No TOML 1.0 escape sequences are handled —
     * config values are simple ASCII strings, so this is fine.
     */
    private static String stringValue(Map<String, String> values, String key, String fallback) {
        String rawValue = values.get(key);
        if (rawValue == null) return fallback;

        String trimmed = rawValue.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Reads a boolean value from the parsed TOML map. Logs a warning and uses the fallback
     * if the value isn't exactly "true" or "false" (case-insensitive).
     */
    private static boolean booleanValue(Map<String, String> values, String key, boolean fallback) {
        String rawValue = values.get(key);
        if (rawValue == null) return fallback;
        if ("true".equalsIgnoreCase(rawValue)) return true;
        if ("false".equalsIgnoreCase(rawValue)) return false;
        LogUtils.logWarn("Ignoring invalid boolean for {} in {}: {}", key, configFileForKey(key), rawValue);
        return fallback;
    }

    /**
     * Core integer reader: parses the raw string, enforces inclusive min/max bounds,
     * logs a warning on parse failure or out-of-range values.
     */
    private static int boundedInt(Map<String, String> values, String key, int fallback, int minValue, int maxValue) {
        String rawValue = values.get(key);
        if (rawValue == null) return fallback;
        try {
            int v = Integer.parseInt(rawValue.trim());
            if (v < minValue || v > maxValue) return fallback;
            return v;
        } catch (NumberFormatException ignored) {}
        LogUtils.logWarn("Ignoring invalid integer for {} in {}: {}", key, configFileForKey(key), rawValue);
        return fallback;
    }

    /** Clamps a runtime value into inclusive bounds. */
    private static int clamp(int value, int minValue, int maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    /** Maps a config key to its owning TOML file name (for cleaner warning messages). */
    private static String configFileForKey(String key) {
        return "harvestParticles".equals(key) ? "forgetmecrops-client.toml" : "forgetmecrops-server.toml";
    }
}

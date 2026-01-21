package com.factioncore.util;

import com.factioncore.faction.FactionInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Constants;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * Utility class for checking the integrity of JSON files used by the plugin.
 * 
 * Validates that JSON files are:
 * - Valid JSON syntax
 * - Properly formatted
 * - Contain expected data structures
 * - Have consistent data (no orphaned references, etc.)
 */
public class JsonIntegrityUtil {
    
    private static HytaleLogger LOGGER;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
    
    /**
     * Gets the logger instance, initializing it lazily if needed.
     * 
     * @return The HytaleLogger instance (never null)
     */
    private static HytaleLogger getLogger() {
        if (LOGGER == null) {
            try {
                LOGGER = HytaleLogger.forEnclosingClass();
            } catch (Exception e) {
                // Fallback if forEnclosingClass fails
            }
            // If still null, use alternative initialization
            if (LOGGER == null) {
                LOGGER = HytaleLogger.getLogger().getSubLogger("JsonIntegrityUtil");
            }
        }
        return LOGGER;
    }
    
    /**
     * Result of an integrity check.
     */
    public static class IntegrityResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public IntegrityResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
            this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
    
    /**
     * Checks the integrity of the faction data JSON file.
     * 
     * @return IntegrityResult containing validation results
     */
    public static IntegrityResult checkFactionDataIntegrity() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Use Constants.UNIVERSE_PATH like hyfaction does
        Path basePath = Constants.UNIVERSE_PATH.resolve("FactionCore");
        Path factionDataPath = basePath.resolve("real_faction.json");
        File factionDataFile = factionDataPath.toFile();
        
        if (ConfigUtil.getInstance().isDebug()) {
            ConfigUtil.debug("Checking integrity of faction data file: " + factionDataPath);
        }
        
        // Check if file exists
        if (!factionDataFile.exists()) {
            warnings.add("Faction data file does not exist: " + factionDataPath + " (this is normal for new installations)");
            return new IntegrityResult(true, errors, warnings);
        }
        
        // Check if file is readable
        if (!factionDataFile.canRead()) {
            errors.add("Cannot read faction data file: " + factionDataPath);
            return new IntegrityResult(false, errors, warnings);
        }
        
        // Check if file is empty
        if (factionDataFile.length() == 0) {
            warnings.add("Faction data file is empty: " + factionDataPath);
            return new IntegrityResult(true, errors, warnings);
        }
        
        // Try to parse JSON
        try (FileReader reader = new FileReader(factionDataFile)) {
            // First, check if it's valid JSON
            JsonParser parser = new JsonParser();
            JsonElement jsonElement = parser.parse(reader);
            
            if (jsonElement == null || !jsonElement.isJsonObject()) {
                errors.add("Faction data file is not a valid JSON object");
                return new IntegrityResult(false, errors, warnings);
            }
            
            // Try to deserialize as faction data
            reader.close();
            try (FileReader reader2 = new FileReader(factionDataFile)) {
                Type type = new TypeToken<Map<String, FactionInfo>>(){}.getType();
                Map<String, FactionInfo> factions = GSON.fromJson(reader2, type);
                
                if (factions == null) {
                    warnings.add("Faction data file contains null data");
                    return new IntegrityResult(true, errors, warnings);
                }
                
                // Validate each faction
                Set<UUID> allPlayerUuids = new HashSet<>();
                Set<String> factionNames = new HashSet<>();
                
                for (Map.Entry<String, FactionInfo> entry : factions.entrySet()) {
                    String factionIdStr = entry.getKey();
                    FactionInfo faction = entry.getValue();
                    
                    // Validate faction ID
                    try {
                        UUID factionId = UUID.fromString(factionIdStr);
                        if (faction == null) {
                            errors.add("Faction with ID " + factionIdStr + " is null");
                            continue;
                        }
                        
                        // Validate faction ID matches
                        if (faction.getId() != null && !faction.getId().equals(factionId)) {
                            warnings.add("Faction ID mismatch: key=" + factionIdStr + ", faction.id=" + faction.getId());
                        }
                        
                        // Validate faction name
                        if (faction.getName() == null || faction.getName().trim().isEmpty()) {
                            errors.add("Faction " + factionIdStr + " has null or empty name");
                        } else {
                            String lowerName = faction.getName().toLowerCase();
                            if (factionNames.contains(lowerName)) {
                                warnings.add("Duplicate faction name found: " + faction.getName());
                            }
                            factionNames.add(lowerName);
                        }
                        
                        // Validate owner
                        if (faction.getOwner() == null) {
                            errors.add("Faction " + factionIdStr + " has null owner");
                        } else {
                            if (allPlayerUuids.contains(faction.getOwner())) {
                                warnings.add("Player " + faction.getOwner() + " is owner of multiple factions");
                            }
                            allPlayerUuids.add(faction.getOwner());
                        }
                        
                        // Validate members
                        UUID[] members = faction.getMembers();
                        if (members != null) {
                            Set<UUID> memberSet = new HashSet<>();
                            for (UUID member : members) {
                                if (member == null) {
                                    warnings.add("Faction " + factionIdStr + " has null member UUID");
                                    continue;
                                }
                                
                                // Check for duplicate members
                                if (memberSet.contains(member)) {
                                    warnings.add("Faction " + factionIdStr + " has duplicate member: " + member);
                                }
                                memberSet.add(member);
                                
                                // Check if member is also the owner
                                if (faction.getOwner() != null && member.equals(faction.getOwner())) {
                                    warnings.add("Faction " + factionIdStr + " has owner " + member + " also listed as member");
                                }
                                
                                // Check if player is in multiple factions
                                if (allPlayerUuids.contains(member)) {
                                    warnings.add("Player " + member + " is in multiple factions");
                                }
                                allPlayerUuids.add(member);
                            }
                        }
                        
                    } catch (IllegalArgumentException e) {
                        errors.add("Invalid faction ID format: " + factionIdStr);
                    }
                }
                
                if (ConfigUtil.getInstance().isDebug()) {
                    ConfigUtil.debug("Faction data integrity check completed: " + factions.size() + " factions checked, " + 
                                   errors.size() + " errors, " + warnings.size() + " warnings");
                }
                
                return new IntegrityResult(errors.isEmpty(), errors, warnings);
                
            } catch (Exception e) {
                errors.add("Failed to deserialize faction data: " + e.getMessage());
                if (ConfigUtil.getInstance().isDebug()) {
                    ConfigUtil.debug("Faction data deserialization error", e);
                }
                return new IntegrityResult(false, errors, warnings);
            }
            
        } catch (IOException e) {
            errors.add("Failed to read faction data file: " + e.getMessage());
            if (ConfigUtil.getInstance().isDebug()) {
                ConfigUtil.debug("Faction data file read error", e);
            }
            return new IntegrityResult(false, errors, warnings);
        } catch (Exception e) {
            errors.add("Invalid JSON syntax in faction data file: " + e.getMessage());
            if (ConfigUtil.getInstance().isDebug()) {
                ConfigUtil.debug("Faction data JSON parse error", e);
            }
            return new IntegrityResult(false, errors, warnings);
        }
    }
    
    /**
     * Checks the integrity of the config JSON file.
     * 
     * @return IntegrityResult containing validation results
     */
    public static IntegrityResult checkConfigIntegrity() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Use Constants.UNIVERSE_PATH like hyfaction does
        Path basePath = Constants.UNIVERSE_PATH.resolve("FactionCore");
        Path configPath = basePath.resolve("config.json");
        File configFile = configPath.toFile();
        
        if (ConfigUtil.getInstance().isDebug()) {
            ConfigUtil.debug("Checking integrity of config file: " + configPath);
        }
        
        // Check if file exists
        if (!configFile.exists()) {
            warnings.add("Config file does not exist: " + configPath + " (will be created with defaults)");
            return new IntegrityResult(true, errors, warnings);
        }
        
        // Check if file is readable
        if (!configFile.canRead()) {
            errors.add("Cannot read config file: " + configPath);
            return new IntegrityResult(false, errors, warnings);
        }
        
        // Check if file is empty
        if (configFile.length() == 0) {
            warnings.add("Config file is empty: " + configPath);
            return new IntegrityResult(true, errors, warnings);
        }
        
        // Try to parse JSON
        try (FileReader reader = new FileReader(configFile)) {
            JsonParser parser = new JsonParser();
            JsonElement jsonElement = parser.parse(reader);
            
            if (jsonElement == null || !jsonElement.isJsonObject()) {
                errors.add("Config file is not a valid JSON object");
                return new IntegrityResult(false, errors, warnings);
            }
            
            // Check for required fields
            if (!jsonElement.getAsJsonObject().has("debug")) {
                warnings.add("Config file missing 'debug' field (will use default: false)");
            } else {
                JsonElement debugElement = jsonElement.getAsJsonObject().get("debug");
                if (!debugElement.isJsonPrimitive() || !debugElement.getAsJsonPrimitive().isBoolean()) {
                    errors.add("Config file 'debug' field must be a boolean");
                }
            }
            
            if (ConfigUtil.getInstance().isDebug()) {
                ConfigUtil.debug("Config file integrity check completed: " + errors.size() + " errors, " + warnings.size() + " warnings");
            }
            
            return new IntegrityResult(errors.isEmpty(), errors, warnings);
            
        } catch (IOException e) {
            errors.add("Failed to read config file: " + e.getMessage());
            if (ConfigUtil.getInstance().isDebug()) {
                ConfigUtil.debug("Config file read error", e);
            }
            return new IntegrityResult(false, errors, warnings);
        } catch (Exception e) {
            errors.add("Invalid JSON syntax in config file: " + e.getMessage());
            if (ConfigUtil.getInstance().isDebug()) {
                ConfigUtil.debug("Config file JSON parse error", e);
            }
            return new IntegrityResult(false, errors, warnings);
        }
    }
    
    /**
     * Checks the integrity of all JSON files used by the plugin.
     * 
     * @return IntegrityResult containing validation results for all files
     */
    public static IntegrityResult checkAllJsonFiles() {
        List<String> allErrors = new ArrayList<>();
        List<String> allWarnings = new ArrayList<>();
        
        if (ConfigUtil.getInstance().isDebug()) {
            ConfigUtil.debug("Starting integrity check for all JSON files");
        }
        
        // Check config file
        IntegrityResult configResult = checkConfigIntegrity();
        allErrors.addAll(configResult.getErrors());
        allWarnings.addAll(configResult.getWarnings());
        
        // Check faction data file
        IntegrityResult factionResult = checkFactionDataIntegrity();
        allErrors.addAll(factionResult.getErrors());
        allWarnings.addAll(factionResult.getWarnings());
        
        boolean allValid = configResult.isValid() && factionResult.isValid();
        
        if (ConfigUtil.getInstance().isDebug()) {
            ConfigUtil.debug("All JSON files integrity check completed: " + 
                           (allValid ? "PASSED" : "FAILED") + 
                           " (" + allErrors.size() + " errors, " + allWarnings.size() + " warnings)");
        }
        
        return new IntegrityResult(allValid, allErrors, allWarnings);
    }
    
    /**
     * Logs the integrity check results.
     * 
     * @param result The integrity check result
     */
    public static void logIntegrityResult(IntegrityResult result) {
        if (result.hasErrors()) {
            getLogger().at(Level.SEVERE).log("JSON Integrity Check FAILED with " + result.getErrors().size() + " error(s):");
            for (String error : result.getErrors()) {
                getLogger().at(Level.SEVERE).log("  ERROR: " + error);
            }
        }
        
        if (result.hasWarnings()) {
            getLogger().at(Level.WARNING).log("JSON Integrity Check completed with " + result.getWarnings().size() + " warning(s):");
            for (String warning : result.getWarnings()) {
                getLogger().at(Level.WARNING).log("  WARNING: " + warning);
            }
        }
        
        if (!result.hasErrors() && !result.hasWarnings()) {
            getLogger().at(Level.INFO).log("JSON Integrity Check PASSED - all files are valid");
        } else if (!result.hasErrors()) {
            getLogger().at(Level.INFO).log("JSON Integrity Check PASSED with warnings");
        }
    }
    
    /**
     * Gson adapter for LocalDateTime serialization.
     * Same as the one used in FactionManager to ensure consistency.
     */
    private static class LocalDateTimeAdapter implements com.google.gson.JsonSerializer<LocalDateTime>,
            com.google.gson.JsonDeserializer<LocalDateTime> {
        
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        @Override
        public com.google.gson.JsonElement serialize(LocalDateTime src, Type typeOfSrc,
                com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(src.format(FORMATTER));
        }
        
        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement json, Type typeOfT,
                com.google.gson.JsonDeserializationContext context) throws com.google.gson.JsonParseException {
            return LocalDateTime.parse(json.getAsString(), FORMATTER);
        }
    }
}

package com.allylocator.systems;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Service that provides a unified interface to party/faction information from either
 * Hyfaction or SimpleClaims plugins.
 * 
 * Detects which plugin is available at runtime and uses the appropriate API.
 * If neither plugin is available, returns null (which causes all markers to be filtered).
 */
public class PartyService {
    
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    
    // Plugin detection flags
    private static Boolean hyfactionAvailable = null;
    private static Boolean simpleClaimsAvailable = null;
    
    // Cached method references for performance
    private static Method hyfactionGetPartyMethod = null;
    private static Method hyfactionGetFactionMethod = null;
    private static Method simpleClaimsGetPartyMethod = null;
    private static Object hyfactionClaimManager = null;
    private static Object simpleClaimsClaimManager = null;
    
    /**
     * Gets party/faction information for a player from either Hyfaction or SimpleClaims.
     * 
     * @param playerUuid The UUID of the player
     * @return PartyInfo/FactionInfo object if player is in a party/faction, null otherwise or if no plugin is available
     */
    @Nullable
    public static Object getPartyFromPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        
        // Try Hyfaction first
        Object partyInfo = getPartyFromHyfaction(playerUuid);
        if (partyInfo != null) {
            return partyInfo;
        }
        
        // Fall back to SimpleClaims
        partyInfo = getPartyFromSimpleClaims(playerUuid);
        if (partyInfo != null) {
            return partyInfo;
        }
        
        return null;
    }
    
    /**
     * Checks if a player is an owner or member of a party/faction.
     * 
     * @param partyInfo The party/faction info object (from either plugin)
     * @param playerUuid The UUID of the player to check
     * @return true if player is owner or member, false otherwise
     */
    public static boolean isOwnerOrMember(Object partyInfo, UUID playerUuid) {
        if (partyInfo == null || playerUuid == null) {
            return false;
        }
        
        try {
            // Both plugins use the same method name: isOwnerOrMember(UUID)
            Method method = partyInfo.getClass().getMethod("isOwnerOrMember", UUID.class);
            Object result = method.invoke(partyInfo, playerUuid);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Error checking party membership: " + e.getMessage());
            LOGGER.at(Level.FINE).log("Stack trace: ", e);
        }
        
        return false;
    }
    
    /**
     * Gets the owner UUID from a party/faction info object.
     * 
     * @param partyInfo The party/faction info object
     * @return The owner UUID, or null if not available
     */
    @Nullable
    public static UUID getPartyOwner(Object partyInfo) {
        if (partyInfo == null) {
            return null;
        }
        
        try {
            Method method = partyInfo.getClass().getMethod("getOwner");
            Object result = method.invoke(partyInfo);
            if (result instanceof UUID) {
                return (UUID) result;
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("Error getting party owner: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Gets the member UUIDs from a party/faction info object.
     * 
     * @param partyInfo The party/faction info object
     * @return Array of member UUIDs, or empty array if not available
     */
    public static UUID[] getPartyMembers(Object partyInfo) {
        if (partyInfo == null) {
            return new UUID[0];
        }
        
        try {
            Method method = partyInfo.getClass().getMethod("getMembers");
            Object result = method.invoke(partyInfo);
            if (result instanceof UUID[]) {
                return (UUID[]) result;
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("Error getting party members: " + e.getMessage());
        }
        
        return new UUID[0];
    }
    
    /**
     * Attempts to get party/faction info from Hyfaction plugin.
     * 
     * @param playerUuid The player UUID
     * @return PartyInfo/FactionInfo object or null if not available
     */
    @Nullable
    private static Object getPartyFromHyfaction(UUID playerUuid) {
        // Check if Hyfaction is available (lazy initialization)
        if (hyfactionAvailable == null) {
            hyfactionAvailable = checkHyfactionAvailable();
            if (hyfactionAvailable) {
                initializeHyfaction();
            }
        }
        
        if (!hyfactionAvailable) {
            return null;
        }
        
        // If availability is true but initialization failed (method or manager is null), try to re-initialize
        if (hyfactionGetPartyMethod == null || hyfactionGetFactionMethod == null || hyfactionClaimManager == null) {
            LOGGER.at(Level.WARNING).log("Hyfaction initialization incomplete, attempting re-initialization...");
            initializeHyfaction();
            
            if (hyfactionGetPartyMethod == null || hyfactionGetFactionMethod == null || hyfactionClaimManager == null) {
                return null;
            }
        }
        
        // Try to get faction first (Hyfaction uses factions, not parties)
        try {
            Object factionInfo = hyfactionGetFactionMethod.invoke(hyfactionClaimManager, playerUuid);
            if (factionInfo != null) {
                return factionInfo;
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Error getting faction from Hyfaction: " + e.getMessage());
            LOGGER.at(Level.FINE).log("Stack trace: ", e);
        }
        
        // Fall back to party if no faction found
        try {
            return hyfactionGetPartyMethod.invoke(hyfactionClaimManager, playerUuid);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Error getting party from Hyfaction: " + e.getMessage());
            LOGGER.at(Level.FINE).log("Stack trace: ", e);
            return null;
        }
    }
    
    /**
     * Attempts to get party info from SimpleClaims plugin.
     * 
     * @param playerUuid The player UUID
     * @return PartyInfo object or null if not available
     */
    @Nullable
    private static Object getPartyFromSimpleClaims(UUID playerUuid) {
        // Check if SimpleClaims is available (lazy initialization)
        if (simpleClaimsAvailable == null) {
            simpleClaimsAvailable = checkSimpleClaimsAvailable();
            if (simpleClaimsAvailable) {
                initializeSimpleClaims();
            }
        }
        
        if (!simpleClaimsAvailable) {
            return null;
        }
        
        // If availability is true but initialization failed (method or manager is null), try to re-initialize
        if (simpleClaimsGetPartyMethod == null || simpleClaimsClaimManager == null) {
            LOGGER.at(Level.WARNING).log("SimpleClaims initialization incomplete, attempting re-initialization...");
            initializeSimpleClaims();
            
            if (simpleClaimsGetPartyMethod == null || simpleClaimsClaimManager == null) {
                return null;
            }
        }
        
        try {
            return simpleClaimsGetPartyMethod.invoke(simpleClaimsClaimManager, playerUuid);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Error getting party from SimpleClaims: " + e.getMessage());
            LOGGER.at(Level.FINE).log("Stack trace: ", e);
            return null;
        }
    }
    
    /**
     * Checks if Hyfaction plugin classes are available.
     * 
     * @return true if Hyfaction is available, false otherwise
     */
    private static boolean checkHyfactionAvailable() {
        try {
            Class.forName("com.kaws.hyfaction.claim.party.PartyInfo");
            Class.forName("com.kaws.hyfaction.claim.ClaimManager");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Checks if SimpleClaims plugin classes are available.
     * 
     * @return true if SimpleClaims is available, false otherwise
     */
    private static boolean checkSimpleClaimsAvailable() {
        try {
            Class.forName("com.buuz135.simpleclaims.claim.party.PartyInfo");
            Class.forName("com.buuz135.simpleclaims.claim.ClaimManager");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Initializes Hyfaction API references.
     */
    private static void initializeHyfaction() {
        try {
            Class<?> claimManagerClass = Class.forName("com.kaws.hyfaction.claim.ClaimManager");
            Method getInstanceMethod = claimManagerClass.getMethod("getInstance");
            hyfactionClaimManager = getInstanceMethod.invoke(null);
            
            if (hyfactionClaimManager == null) {
                throw new IllegalStateException("ClaimManager.getInstance() returned null - plugin may not be fully loaded yet");
            }
            
            hyfactionGetPartyMethod = claimManagerClass.getMethod("getPartyFromPlayer", UUID.class);
            hyfactionGetFactionMethod = claimManagerClass.getMethod("getFactionFromPlayer", UUID.class);
            
            LOGGER.at(Level.INFO).log("Hyfaction plugin detected and initialized");
        } catch (NoSuchMethodException e) {
            LOGGER.at(Level.SEVERE).log("Failed to initialize Hyfaction API - method not found: " + e.getMessage());
            LOGGER.at(Level.FINE).log("Stack trace: ", e);
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("Failed to initialize Hyfaction API: " + e.getMessage());
            LOGGER.at(Level.FINE).log("Stack trace: ", e);
        }
    }
    
    /**
     * Initializes SimpleClaims API references.
     */
    private static void initializeSimpleClaims() {
        try {
            Class<?> claimManagerClass = Class.forName("com.buuz135.simpleclaims.claim.ClaimManager");
            Method getInstanceMethod = claimManagerClass.getMethod("getInstance");
            simpleClaimsClaimManager = getInstanceMethod.invoke(null);
            
            if (simpleClaimsClaimManager == null) {
                throw new IllegalStateException("ClaimManager.getInstance() returned null - plugin may not be fully loaded yet");
            }
            
            simpleClaimsGetPartyMethod = claimManagerClass.getMethod("getPartyFromPlayer", UUID.class);
            
            LOGGER.at(Level.INFO).log("SimpleClaims plugin detected and initialized");
        } catch (NoSuchMethodException e) {
            LOGGER.at(Level.SEVERE).log("Failed to initialize SimpleClaims API - method not found: " + e.getMessage());
            LOGGER.at(Level.FINE).log("Stack trace: ", e);
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("Failed to initialize SimpleClaims API: " + e.getMessage());
            LOGGER.at(Level.FINE).log("Stack trace: ", e);
        }
    }
    
    /**
     * Gets a summary of which plugins are available.
     * 
     * @return String describing plugin availability
     */
    public static String getPluginStatus() {
        if (hyfactionAvailable == null) {
            hyfactionAvailable = checkHyfactionAvailable();
        }
        if (simpleClaimsAvailable == null) {
            simpleClaimsAvailable = checkSimpleClaimsAvailable();
        }
        
        if (hyfactionAvailable) {
            return "Hyfaction";
        } else if (simpleClaimsAvailable) {
            return "SimpleClaims";
        } else {
            return "None (all markers will be filtered)";
        }
    }
}

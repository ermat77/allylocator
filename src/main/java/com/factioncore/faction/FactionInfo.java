package com.factioncore.faction;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Data class representing a faction.
 * 
 * Data class that holds all information about a faction.
 * It stores all faction data including members, owner, name, description, etc.
 */
public class FactionInfo {
    
    private UUID id;
    private UUID owner;
    private String name;
    private String description;
    private final Set<UUID> memberSet;
    private final Set<UUID> generalSet;  // Generals are special members with elevated permissions
    private int color;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
    
    // Member permissions: member UUID -> set of permission strings
    private Map<UUID, Set<String>> memberPermissions;
    
    /**
     * Creates a new FactionInfo with the given parameters.
     * 
     * @param id The unique faction ID
     * @param owner The UUID of the faction owner
     * @param name The faction name
     * @param description The faction description
     * @param members Array of member UUIDs
     * @param color The faction color (RGB integer)
     */
    public FactionInfo(UUID id, UUID owner, String name, String description, UUID[] members, int color) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.description = description;
        this.memberSet = new HashSet<>();
        this.generalSet = new HashSet<>();
        this.memberPermissions = new HashMap<>();
        if (members != null) {
            for (UUID member : members) {
                this.memberSet.add(member);
            }
        }
        this.color = color;
        this.createdDate = LocalDateTime.now();
        this.modifiedDate = LocalDateTime.now();
    }
    
    /**
     * Creates a new FactionInfo with default values.
     */
    public FactionInfo() {
        this.memberSet = new HashSet<>();
        this.generalSet = new HashSet<>();
        this.memberPermissions = new HashMap<>();
        this.id = UUID.randomUUID();
        this.owner = UUID.randomUUID();
        this.name = "";
        this.description = "";
        this.color = 0;
        this.createdDate = LocalDateTime.now();
        this.modifiedDate = LocalDateTime.now();
    }
    
    // Getters and Setters
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
        markModified();
    }
    
    public UUID getOwner() {
        return owner;
    }
    
    public void setOwner(UUID owner) {
        this.owner = owner;
        markModified();
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        if (name != null) {
            int maxLength = FactionManager.getMaxFactionNameLength();
            if (name.length() > maxLength) {
                throw new IllegalArgumentException("Faction name cannot exceed " + maxLength + " characters");
            }
        }
        this.name = name;
        markModified();
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
        markModified();
    }
    
    public UUID[] getMembers() {
        return memberSet.toArray(new UUID[0]);
    }
    
    public Set<UUID> getMemberSet() {
        return new HashSet<>(memberSet);
    }
    
    public void setMembers(UUID[] members) {
        memberSet.clear();
        if (members != null) {
            for (UUID member : members) {
                memberSet.add(member);
            }
        }
        markModified();
    }
    
    public int getColor() {
        return color;
    }
    
    public void setColor(int color) {
        this.color = color;
        markModified();
    }
    
    public LocalDateTime getCreatedDate() {
        return createdDate;
    }
    
    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }
    
    public LocalDateTime getModifiedDate() {
        return modifiedDate;
    }
    
    public void setModifiedDate(LocalDateTime modifiedDate) {
        this.modifiedDate = modifiedDate;
    }
    
    // Utility methods
    
    /**
     * Checks if the given UUID is the owner of this faction.
     * 
     * @param uuid The UUID to check
     * @return true if the UUID is the owner
     */
    public boolean isOwner(UUID uuid) {
        return owner != null && owner.equals(uuid);
    }
    
    /**
     * Checks if the given UUID is a member of this faction.
     * 
     * @param uuid The UUID to check
     * @return true if the UUID is a member
     */
    public boolean isMember(UUID uuid) {
        return memberSet.contains(uuid);
    }
    
    /**
     * Checks if the given UUID is either the owner or a member.
     * 
     * @param uuid The UUID to check
     * @return true if the UUID is owner or member
     */
    public boolean isOwnerOrMember(UUID uuid) {
        return isOwner(uuid) || isMember(uuid);
    }
    
    /**
     * Checks if the given UUID is a general of this faction.
     * 
     * @param uuid The UUID to check
     * @return true if the UUID is a general
     */
    public boolean isGeneral(UUID uuid) {
        return generalSet.contains(uuid);
    }
    
    /**
     * Gets the role of a player in this faction.
     * 
     * @param uuid The UUID to check
     * @return "owner", "general", "member", or null if not in faction
     */
    public String getPlayerRole(UUID uuid) {
        if (isOwner(uuid)) {
            return "owner";
        } else if (isGeneral(uuid)) {
            return "general";
        } else if (isMember(uuid)) {
            return "member";
        }
        return null;
    }
    
    /**
     * Gets all generals.
     * 
     * @return Array of general UUIDs
     */
    public UUID[] getGenerals() {
        return generalSet.toArray(new UUID[0]);
    }
    
    /**
     * Gets the generals set.
     * 
     * @return Copy of the generals set
     */
    public Set<UUID> getGeneralSet() {
        return new HashSet<>(generalSet);
    }
    
    /**
     * Adds a general to the faction.
     * 
     * @param uuid The UUID of the general to add
     */
    public void addGeneral(UUID uuid) {
        generalSet.add(uuid);
        // General is also a member
        memberSet.add(uuid);
        markModified();
    }
    
    /**
     * Removes a general from the faction.
     * 
     * @param uuid The UUID of the general to remove
     */
    public void removeGeneral(UUID uuid) {
        generalSet.remove(uuid);
        // Note: They remain as a member unless explicitly removed
        markModified();
    }
    
    /**
     * Adds a member to the faction.
     * 
     * @param uuid The UUID of the member to add
     */
    public void addMember(UUID uuid) {
        memberSet.add(uuid);
        markModified();
    }
    
    /**
     * Removes a member from the faction.
     * 
     * @param uuid The UUID of the member to remove
     */
    public void removeMember(UUID uuid) {
        memberSet.remove(uuid);
        markModified();
    }
    
    /**
     * Gets the total number of members (including owner).
     * 
     * @return The total member count
     */
    public int getMemberCount() {
        return memberSet.size() + (owner != null ? 1 : 0);
    }
    
    /**
     * Gets the maximum claim amount for this faction.
     * Reads from config via FactionManager.
     * 
     * @return The maximum number of chunks this faction can claim
     */
    public int getMaxClaimAmount() {
        // Delegate to FactionManager which has access to config
        com.factioncore.Main main = com.factioncore.Main.get();
        if (main != null && main.getClaimConfig() != null) {
            return main.getClaimConfig().get().getDefaultFactionClaimsAmount();
        }
        return 25; // Default
    }
    
    // Member Permissions Management
    
    /**
     * Gets the permissions for a member.
     * Delegates to FactionPermissionManager for runtime permission retrieval (uses cache).
     * 
     * @param memberUuid The UUID of the member
     * @return A set of permission strings, or empty set if member has no permissions
     */
    public Set<String> getMemberPermissions(UUID memberUuid) {
        // Delegate to FactionPermissionManager (runtime permission retrieval)
        return com.factioncore.permission.FactionPermissionManager.getInstance().getMemberPermissions(memberUuid, this.id);
    }
    
    /**
     * Sets the permissions for a member.
     * Delegates to FactionPermissionManager for runtime permission management.
     * Also updates local memberPermissions map for database serialization.
     * 
     * @param memberUuid The UUID of the member
     * @param permissions The set of permission strings to grant
     */
    public void setMemberPermissions(UUID memberUuid, Set<String> permissions) {
        // Delegate to FactionPermissionManager (runtime permission management)
        com.factioncore.permission.FactionPermissionManager.getInstance().setMemberPermissions(memberUuid, permissions, this.id);
        
        // Also update local map for database serialization (backward compatibility)
        if (memberPermissions == null) {
            memberPermissions = new HashMap<>();
        }
        if (permissions == null || permissions.isEmpty()) {
            memberPermissions.remove(memberUuid);
        } else {
            memberPermissions.put(memberUuid, new HashSet<>(permissions));
        }
        markModified();
    }
    
    /**
     * Grants a permission to a member.
     * Delegates to FactionPermissionManager for runtime permission management.
     * Also updates local memberPermissions map for database serialization.
     * 
     * @param memberUuid The UUID of the member
     * @param permission The permission string to grant
     */
    public void grantMemberPermission(UUID memberUuid, String permission) {
        // Delegate to FactionPermissionManager (runtime permission management)
        com.factioncore.permission.FactionPermissionManager.getInstance().grantPermission(memberUuid, permission, this.id);
        
        // Also update local map for database serialization (backward compatibility)
        if (memberPermissions == null) {
            memberPermissions = new HashMap<>();
        }
        memberPermissions.computeIfAbsent(memberUuid, k -> new HashSet<>()).add(permission);
        markModified();
    }
    
    /**
     * Revokes a permission from a member.
     * Delegates to FactionPermissionManager for runtime permission management.
     * Also updates local memberPermissions map for database serialization.
     * 
     * @param memberUuid The UUID of the member
     * @param permission The permission string to revoke
     */
    public void revokeMemberPermission(UUID memberUuid, String permission) {
        // Delegate to FactionPermissionManager (runtime permission management)
        com.factioncore.permission.FactionPermissionManager.getInstance().revokePermission(memberUuid, permission, this.id);
        
        // Also update local map for database serialization (backward compatibility)
        if (memberPermissions == null) {
            return;
        }
        Set<String> perms = memberPermissions.get(memberUuid);
        if (perms != null) {
            perms.remove(permission);
            if (perms.isEmpty()) {
                memberPermissions.remove(memberUuid);
            }
        }
        markModified();
    }
    
    /**
     * Checks if a member has a specific permission.
     * Delegates to FactionPermissionManager for runtime permission checks (uses cache + LuckPerms).
     * 
     * @param memberUuid The UUID of the member
     * @param permission The permission string to check
     * @return true if the member has the permission
     */
    public boolean hasMemberPermission(UUID memberUuid, String permission) {
        // Delegate to FactionPermissionManager (runtime permission checks)
        return com.factioncore.permission.FactionPermissionManager.getInstance().hasPermission(memberUuid, permission, this.id);
    }
    
    /**
     * Gets all member permissions map (for serialization).
     * 
     * @return A copy of the member permissions map
     */
    public Map<UUID, Set<String>> getMemberPermissionsMap() {
        if (memberPermissions == null) {
            return new HashMap<>();
        }
        Map<UUID, Set<String>> copy = new HashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : memberPermissions.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }
    
    /**
     * Sets the member permissions map (for deserialization).
     * 
     * @param permissions The member permissions map to set
     */
    public void setMemberPermissionsMap(Map<UUID, Set<String>> permissions) {
        if (memberPermissions == null) {
            memberPermissions = new HashMap<>();
        } else {
            memberPermissions.clear();
        }
        if (permissions != null) {
            for (Map.Entry<UUID, Set<String>> entry : permissions.entrySet()) {
                memberPermissions.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
        }
        markModified();
    }
    
    /**
     * Marks the faction as modified (updates modifiedDate).
     */
    private void markModified() {
        this.modifiedDate = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return "FactionInfo{id=" + id + ", owner=" + owner + ", name='" + name + 
               "', description='" + description + "', members=" + memberSet.size() + 
               ", color=" + color + "}";
    }
}

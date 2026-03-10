package com.factioncore.prism;

/**
 * Constants for the prism attack feature.
 * The attack prefab must include an NPC with this role id for "prism NPC killed" to trigger attackers win.
 */
public final class PrismAttackConstants {

    /** Role id of the prism NPC in the attack prefab. When this NPC is killed, attackers win. */
    public static final String PRISM_ATTACKED_NPC_ROLE_ID = "Prism_Attacked_NPC_Role";

    private PrismAttackConstants() {}
}

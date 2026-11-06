package com.vandorlabs.neoforge.door;

/** Actual door blocks supply the loaded linked-part query; absent lifecycle support cannot open this editor. */
public interface NativeDoorLinks {
    default boolean configurationAuthorized(net.minecraft.world.entity.player.Player player,NativeProgrammableDoorEntity root){return com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,root);}
    /** All cells needed to establish root/upper/mate membership are already loaded and structurally valid. */
    boolean configurationLinksLoaded(NativeProgrammableDoorEntity root);
    /** Owning-thread query. Return null only for an actually unpaired root, never by loading an absent chunk. */
    NativeProgrammableDoorEntity configurationMate(NativeProgrammableDoorEntity root);
}

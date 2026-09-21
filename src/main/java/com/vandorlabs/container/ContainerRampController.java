package com.vandorlabs.container;

import com.vandorlabs.tiles.TileEntityRampController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

public class ContainerRampController extends Container {
    public final TileEntityRampController controller;
    public ContainerRampController(TileEntityRampController controller) { this.controller=controller; }
    @Override public boolean canInteractWith(EntityPlayer player) { return controller.usable(player); }
}

package com.vandorlabs.container;

import com.vandorlabs.tiles.TileEntityTurntableController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

public class ContainerTurntableController extends Container {
    public final TileEntityTurntableController controller;
    public ContainerTurntableController(TileEntityTurntableController controller) { this.controller = controller; }
    @Override public boolean canInteractWith(EntityPlayer player) { return controller.usable(player); }
}

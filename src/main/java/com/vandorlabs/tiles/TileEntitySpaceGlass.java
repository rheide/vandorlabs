package com.vandorlabs.tiles;

import net.minecraft.tileentity.TileEntity;

/** Render marker only; no server tick, network state, or saved configuration. */
public class TileEntitySpaceGlass extends TileEntity {
    @Override public boolean shouldRenderInPass(int pass) { return pass == 1; }
}

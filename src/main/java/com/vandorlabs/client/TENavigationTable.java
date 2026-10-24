package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityNavigationTable;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

/** Casing is chunk-cached; only the single selected chip reader is dynamic. */
public final class TENavigationTable extends TileEntitySpecialRenderer<TileEntityNavigationTable> {
    @Override public void render(TileEntityNavigationTable tile,double x,double y,double z,float partial,int stage,float alpha){
        com.vandorlabs.blocks.MachineMountSurface mount=tile.mount();if(mount!=null)TETokenSlot.embedded(tile,mount,x-tile.getPos().getX(),y-tile.getPos().getY(),z-tile.getPos().getZ(),partial);
    }
}

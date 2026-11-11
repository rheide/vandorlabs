package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.tiles.GlassMaterials;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.block.Block;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Keep visibility collection without dispatching empty panes through per-tile GL/light setup. */
@SideOnly(Side.CLIENT)
public final class ProgrammableGlassPasses {
    private ProgrammableGlassPasses(){}

    public static boolean collectionOnly(TileEntityProgrammableGlass tile){
        if(MinecraftForgeClient.getRenderPass()!=0 || tile.getWorld()==null
                || !tile.getWorld().isRemote || tile.getWorld() instanceof VehicleWorld
                || GlassMaterials.opaque(tile.getMaterial(),tile.getTint()))return false;
        Block block=tile.getBlockType();
        if(!(block instanceof BlockProgrammableGlass))return false;
        if(!tile.isFramed())return true;
        if(GlassFrameChunks.selected(tile))return GlassFrameChunks.rendered(tile);
        if(!tile.isJoin()||GlassFrameChunks.rendered(tile))return true;
        // Native TileEntity metadata is invalidated by updateContainingBlockInfo.
        // VehicleWorld replacement uses different lifetime rules and stays on the
        // ordinary renderer. No world/position cache is added here.
        // Forge asks this twice for each visible tile. Eligibility may probe an
        // existing result, but must not discover neighbours twice on a cold or
        // over-budget view. Unknown panes warm through the ordinary renderer.
        return GlassSurfaceGroups.flatCached(tile,block,tile.getBlockMetadata());
    }

    static boolean emptyOpaquePass(TileEntityProgrammableGlass tile,BlockProgrammableWall.FlatCorner corner){
        if(GlassMaterials.opaque(tile.getMaterial(),tile.getTint()))return false;
        if(!tile.isFramed())return true;
        // A selected frame remains a TESR fallback until its exact native chunk
        // upload is acknowledged. Recycled RenderChunks cannot suppress it.
        if(GlassFrameChunks.selected(tile))return GlassFrameChunks.rendered(tile);
        if(!tile.isJoin())return true;
        return corner==null || GlassFrameChunks.rendered(tile);
    }
}

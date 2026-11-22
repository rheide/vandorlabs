package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableCornerGlass;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Corner glass and Corner Walls draw only through their tile renderers. Minecraft finds ordinary tile renderers in
 * each render chunk's compiled list, and stacked corners were measured missing from that list
 * even with their tile installed and after a full renderer reload, leaving them invisible (most
 * often with OptiFine). Corners therefore render as global tiles: every live corner is kept in
 * RenderGlobal's global tile set, drawn each pass with the usual frustum check.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class CornerGlassRefresh {
    private static final Set<TileEntity> pending=Collections.newSetFromMap(new IdentityHashMap<>());
    /** Live client corners; read from chunk compile threads through isCorner. Tiles compare by identity. */
    private static final Set<TileEntity> corners=Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static Field globalTiles;
    private CornerGlassRefresh(){}
    /** A Corner Glass or Corner Wall tile loaded on the client. */
    public static void loaded(TileEntity tile){
        if(tile.getWorld()==null||!tile.getWorld().isRemote||tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld)return;
        synchronized(pending){pending.add(tile);}
    }
    /** Whether the renderer should treat this tile as global; safe on chunk compile threads. */
    static boolean isCorner(TileEntity tile){return corners.contains(tile);}
    static int tracked(){return corners.size();}
    @SuppressWarnings("unchecked")
    private static Set<TileEntity> global(RenderGlobal render){
        try{
            if(globalTiles==null)globalTiles=ReflectionHelper.findField(RenderGlobal.class,"setTileEntities","field_181024_n");
            return (Set<TileEntity>)globalTiles.get(render);
        }catch(ReflectiveOperationException|RuntimeException failure){throw new IllegalStateException("RenderGlobal global tile set is unavailable",failure);}
    }
    private static boolean live(World world,TileEntity tile){
        BlockPos pos=tile.getPos();
        if(world==null||tile.getWorld()!=world||tile.isInvalid()||!world.isBlockLoaded(pos)||world.getTileEntity(pos)!=tile)return false;
        net.minecraft.block.Block block=world.getBlockState(pos).getBlock();
        return block instanceof BlockProgrammableCornerGlass||block instanceof com.vandorlabs.blocks.BlockProgrammableCornerWall;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();World world=mc.world;
        List<TileEntity> loaded;
        synchronized(pending){loaded=new ArrayList<>(pending);pending.clear();}
        for(TileEntity tile:loaded)if(live(world,tile))corners.add(tile);
        if(corners.isEmpty()||mc.renderGlobal==null)return;
        Set<TileEntity> global=global(mc.renderGlobal);
        synchronized(global){
            for(TileEntity tile:new ArrayList<>(corners)){
                if(live(world,tile))global.add(tile);
                else{corners.remove(tile);global.remove(tile);}
            }
        }
    }
}

package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Opaque wall artwork is rigid in craft coordinates; moving and translucent tiles stay live. */
final class VehicleOpaqueTileCache {
    static boolean enabled=true;
    private static final class Entry {int list,light,format,pass;}
    private final Map<TileEntity,Entry> entries=new IdentityHashMap<>();
    int builds,hits;
    static boolean eligible(TileEntity tile,TileEntitySpecialRenderer<?> renderer){
        if(!((Object)renderer instanceof TEAnimatedScreenSelector) || !(tile.getBlockType() instanceof BlockProgrammableWall))return false;
        if(tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector){
            com.vandorlabs.tiles.TileEntityAnimatedScreenSelector screen=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile;
            if(screen.fluids.any()||screen.hasRedstoneScreen(0)||screen.hasRedstoneScreen(1)||screen.hasVehicleStatus(0)||screen.hasVehicleStatus(1))return false;
        }
        BlockProgrammableWall wall=(BlockProgrammableWall)tile.getBlockType();
        return !wall.isPortholeShape() && !(wall instanceof BlockProgrammableDiagonalGlass);
    }
    boolean ready(TileEntity tile){Entry entry=entries.get(tile);return entry!=null&&entry.light==tile.getWorld().getCombinedLight(tile.getPos(),0)&&entry.format==BlockSurfaceFormat.get().getNextOffset()&&entry.pass==net.minecraftforge.client.MinecraftForgeClient.getRenderPass();}
    void draw(TileEntity tile,TileEntitySpecialRenderer<TileEntity> renderer,VehicleStructure structure,float partial){
        if(!enabled || !eligible(tile,renderer) || BlockSurfaceFormat.get().getNextOffset()!=32){renderer.render(tile,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ(),partial,-1,1);return;}
        // Texture binds can be omitted by GlStateManager when compiling a list.
        // Reestablish the atlas after unrelated chest, sign or screen renderers.
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1,1,1,1);
        GlStateManager.enableLighting();
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0),format=BlockSurfaceFormat.get().getNextOffset(),pass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
        Entry entry=entries.get(tile);
        if(entry==null || entry.light!=light || entry.format!=format || entry.pass!=pass){
            if(entry!=null)GLAllocation.deleteDisplayLists(entry.list);
            entry=new Entry();entry.list=GLAllocation.generateDisplayLists(1);entry.light=light;entry.format=format;entry.pass=pass;
            GL11.glNewList(entry.list,GL11.GL_COMPILE_AND_EXECUTE);
            try{renderer.render(tile,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ(),partial,-1,1);}
            catch(RuntimeException | LinkageError error){GL11.glEndList();GLAllocation.deleteDisplayLists(entry.list);entries.remove(tile);throw error;}
            GL11.glEndList();entries.put(tile,entry);builds++;
            return;
        }else hits++;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
        GL11.glCallList(entry.list);
    }
    void delete(){for(Entry entry:entries.values())GLAllocation.deleteDisplayLists(entry.list);entries.clear();}
    int size(){return entries.size();}
}

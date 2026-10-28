package com.vandorlabs.client;

import java.util.*;
import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

/** One driver submission per static opaque section; child meshes retain their own light keys. */
final class VehicleOpaqueTileBatch {
    private final List<TileEntity> members;
    private int list;
    private boolean disabled;
    final AxisAlignedBB bounds;
    VehicleOpaqueTileBatch(List<TileEntity> members){
        this.members=Collections.unmodifiableList(new ArrayList<>(members));AxisAlignedBB box=null;
        for(TileEntity tile:members){AxisAlignedBB next=tile.getRenderBoundingBox();box=box==null?next:box.union(next);}bounds=box;
    }
    boolean ready(VehicleOpaqueTileCache cache){if(list==0)return false;for(TileEntity tile:members)if(!cache.ready(tile))return false;return true;}
    void draw(VehicleOpaqueTileCache cache,VehicleStructure structure,float partial){
        if(disabled){individual(cache,structure,partial);return;}
        try{drawPrepared(cache,structure,partial);}catch(RuntimeException|LinkageError failure){delete();disabled=true;com.vandorlabs.vehicle.VehicleCompatibility.warn(members.get(0).getBlockType(),"opaque section cache",failure);individual(cache,structure,partial);}
    }
    private void individual(VehicleOpaqueTileCache cache,VehicleStructure structure,float partial){for(TileEntity tile:members){GlStateManager.pushMatrix();try{cache.draw(tile,TileEntityRendererDispatcher.instance.getRenderer(tile),structure,partial);}catch(RuntimeException|LinkageError failure){com.vandorlabs.vehicle.VehicleCompatibility.warn(tile.getBlockType(),"opaque tile fallback",failure);}finally{GlStateManager.popMatrix();}}}
    private void drawPrepared(VehicleOpaqueTileCache cache,VehicleStructure structure,float partial){
        for(TileEntity tile:members)if(!VehicleOpaqueTileCache.eligible(tile,TileEntityRendererDispatcher.instance.getRenderer(tile))){delete();individual(cache,structure,partial);return;}
        Minecraft.getMinecraft().getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1,1,1,1);GlStateManager.enableLighting();
        if(ready(cache)){GL11.glCallList(list);return;}
        delete();
        // Prime children outside the parent list: OpenGL forbids nested glNewList.
        // Their first draw is the visible draw for this frame.
        for(TileEntity tile:members){GlStateManager.pushMatrix();try{cache.draw(tile,TileEntityRendererDispatcher.instance.getRenderer(tile),structure,partial);}finally{GlStateManager.popMatrix();}}
        list=GLAllocation.generateDisplayLists(1);GL11.glNewList(list,GL11.GL_COMPILE);
        try{for(TileEntity tile:members){GL11.glPushMatrix();cache.call(tile);GL11.glPopMatrix();}}
        catch(RuntimeException|LinkageError failure){GL11.glEndList();delete();throw failure;}
        GL11.glEndList();
    }
    void delete(){if(list!=0){GLAllocation.deleteDisplayLists(list);list=0;}}
    int size(){return members.size();}
}

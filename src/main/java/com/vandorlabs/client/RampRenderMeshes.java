package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityControlledRamp;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;
import java.util.function.Consumer;
import org.lwjgl.opengl.GL11;

/** Resting opaque ramps retain their clipped atlas geometry, including shader records.
 * Moving ramps, escalators, glass and moats retain the live geometry path. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
final class RampRenderMeshes {
    private static final int LIMIT=8*1024*1024;
    private static final Map<TileEntityControlledRamp,Entry> CACHE=new WeakHashMap<>();
    private static final Map<Entry,Boolean> LRU=new LinkedHashMap<>(64,.75F,true);
    private static int bytes;
    static boolean enabled=true;
    private static final class Entry {
        final java.lang.ref.WeakReference<TileEntityControlledRamp> owner;final long revision;final IBlockState source,state;final int light;final double pose;final VertexFormat format;final VertexBuffer buffer;final int bytes;
        Entry(TileEntityControlledRamp tile,int light,double pose,Consumer<BufferBuilder> emit){
            owner=new java.lang.ref.WeakReference<>(tile);revision=tile.renderRevision;source=tile.source;state=tile.getWorld().getBlockState(tile.getPos());this.light=light;this.pose=pose;format=new VertexFormat(BlockSurfaceFormat.get());
            BufferBuilder b=new BufferBuilder(4096);b.begin(GL11.GL_QUADS,format);emit.accept(b);b.finishDrawing();bytes=b.getByteBuffer().remaining();buffer=new VertexBuffer(format);buffer.bufferData(b.getByteBuffer());
        }
        boolean matches(TileEntityControlledRamp tile,int light,double pose){return revision==tile.renderRevision&&source==tile.source&&state==tile.getWorld().getBlockState(tile.getPos())&&this.light==light&&this.pose==pose&&format.equals(BlockSurfaceFormat.get());}
        void delete(){buffer.deleteGlBuffers();}
    }
    static boolean eligible(TileEntityControlledRamp tile,float partial){double pose=tile.pose(partial);return enabled&&OpenGlHelper.useVbo()&&!tile.isMoving()&&tile.escalator==0&&!tile.moat&&(pose==0||pose==1)&&!com.vandorlabs.compat.RampMaterials.isFullGlass(tile.source);}
    static void draw(TileEntityControlledRamp tile,int light,float partial,Consumer<BufferBuilder> emit){
        double pose=tile.pose(partial);Entry entry=CACHE.get(tile);
        if(entry!=null&&!entry.matches(tile,light,pose)){CACHE.remove(tile);LRU.remove(entry);bytes-=entry.bytes;entry.delete();entry=null;}
        if(entry==null){
            entry=new Entry(tile,light,pose,emit);
            while(!LRU.isEmpty()&&(bytes+entry.bytes>LIMIT||LRU.size()>=512)){
                Iterator<Entry> it=LRU.keySet().iterator();Entry old=it.next();it.remove();CACHE.remove(old.owner.get(),old);bytes-=old.bytes;old.delete();
            }
            if(entry.bytes<=LIMIT){CACHE.put(tile,entry);bytes+=entry.bytes;}
        }
        if(entry.bytes<=LIMIT)LRU.put(entry,Boolean.TRUE);
        BlockMeshVbo.draw(entry.buffer,entry.format,BlockRenderLayer.SOLID);
        if(entry.bytes>LIMIT)entry.delete();
    }
    static void clear(){for(Entry entry:LRU.keySet())entry.delete();CACHE.clear();LRU.clear();bytes=0;}
    @SubscribeEvent public static void reload(net.minecraftforge.client.event.TextureStitchEvent.Post event){clear();}
    @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(RampRenderMeshes::clear);}
    private RampRenderMeshes(){}
}

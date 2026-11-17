package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityNavigationTable;
import com.vandorlabs.rack.EmbeddedRackMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Same shared cassette timing as embedded builder racks; one bounded pose per physical bay. */
public final class NavigationModuleRenderer {
    private static final com.vandorlabs.animation.VisualClock CLOCK=new com.vandorlabs.animation.VisualClock();
    private static final Map<net.minecraft.world.World,LinkedHashMap<BlockPos,Entry>> WORLDS=new WeakHashMap<>();
    private static final class Entry{final java.lang.ref.WeakReference<TileEntityNavigationTable> tile;final BlockPos storage;final EmbeddedRackMotion motion=new EmbeddedRackMotion();long sequence;Entry(TileEntityNavigationTable tile,BlockPos storage){this.tile=new java.lang.ref.WeakReference<>(tile);this.storage=storage;}}
    public static void clear(){WORLDS.clear();}
    private NavigationModuleRenderer(){}
    public static void render(TileEntityNavigationTable tile,double x,double y,double z){
        if(!tile.hasModuleBay())return;Minecraft mc=Minecraft.getMinecraft();double now=CLOCK.sample(System.nanoTime(),mc.isGamePaused());LinkedHashMap<BlockPos,Entry> poses=WORLDS.computeIfAbsent(tile.getWorld(),w->new LinkedHashMap<>(16,.75f,true));TileEntityNavigationTable storage=tile.moduleStorage();if(storage==null)return;Entry entry=poses.get(tile.getPos());if(entry==null||!entry.storage.equals(storage.getPos())||entry.tile.get()!=tile&&tile.moduleSequence()<entry.sequence){entry=new Entry(tile,storage.getPos());poses.put(tile.getPos(),entry);if(poses.size()>256)poses.remove(poses.keySet().iterator().next());}entry.sequence=Math.max(entry.sequence,tile.moduleSequence());EmbeddedRackMotion motion=entry.motion;
        motion.observe(tile.moduleStack().isEmpty()?0:1,tile.moduleSequence(),0,0,0,0,0,0,0,now);
        try(TurntableRenderState saved=new TurntableRenderState()){
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(7,DefaultVertexFormats.BLOCK);ModuleRackModel.emitNavigationBay(b,tile.moduleFacing(),x,y,z,motion.visible(0,now),motion.offset(0,now),tile.getWorld().getCombinedLight(tile.getPos(),0));Tessellator.getInstance().draw();
        }
    }
}

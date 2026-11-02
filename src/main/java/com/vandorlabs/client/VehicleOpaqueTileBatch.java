package com.vandorlabs.client;

import java.util.*;
import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.*;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

/** Shader-safe rigid wall sections. Retain immutable formats and bound total VBO bytes. */
final class VehicleOpaqueTileBatch {
    private static final long LIMIT=64L*1024*1024;
    private static final Map<VehicleOpaqueTileBatch,Integer> RETAINED=new LinkedHashMap<>(64,.75F,true);
    private static long retainedBytes;
    // Only the client render thread builds these meshes. Reuse native staging
    // buffers instead of allocating a direct ByteBuffer for every tile/relight.
    private static final BufferBuilder COMPONENT=new BufferBuilder(16384),JOINED=new BufferBuilder(65536);
    private final List<TileEntity> members;
    private final int[] lights,sampled,offsets;
    private int[] records;private WallLightRecords lightRecords;
    private VertexBuffer buffer;
    private VertexFormat format;
    private boolean disabled;
    int builds,relights;
    final AxisAlignedBB bounds;
    VehicleOpaqueTileBatch(List<TileEntity> members){
        this.members=Collections.unmodifiableList(new ArrayList<>(members));lights=new int[members.size()];sampled=new int[members.size()];offsets=new int[members.size()+1];AxisAlignedBB box=null;
        for(TileEntity tile:members){AxisAlignedBB next=tile.getRenderBoundingBox();box=box==null?next:box.union(next);}bounds=box;
    }
    static boolean eligible(TileEntity tile,TileEntitySpecialRenderer<?> renderer){
        if(!VehicleOpaqueTileCache.eligible(tile,renderer))return false;
        return com.vandorlabs.blocks.PanelJoins.flatBar(tile.getWorld().getBlockState(tile.getPos()),tile.getWorld(),tile.getPos())==null;
    }
    boolean ready(VehicleOpaqueTileCache ignored){
        boolean valid=buffer!=null&&format.equals(BlockSurfaceFormat.get());
        for(int i=0;i<members.size();i++){TileEntity tile=members.get(i);sampled[i]=tile.getWorld().getCombinedLight(tile.getPos(),0);if(sampled[i]!=lights[i])valid=false;}
        return valid;
    }
    void draw(VehicleOpaqueTileCache cache,VehicleStructure structure,float partial){
        if(disabled||!OpenGlHelper.useVbo()||BlockSurfaceFormat.get().getNextOffset()>32&&!VehicleShaderContext.available()){individual(cache,structure,partial);return;}
        try{
            for(TileEntity tile:members)if(!eligible(tile,TileEntityRendererDispatcher.instance.getRenderer(tile))){delete();individual(cache,structure,partial);return;}
            if(!ready(cache)){if(buffer!=null&&records!=null&&lightRecords!=null&&format.equals(BlockSurfaceFormat.get()))relight();else build(partial);}
            RETAINED.get(this);
            Minecraft.getMinecraft().getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.color(1,1,1,1);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.disableBlend();GlStateManager.depthMask(true);
            try{BlockMeshVbo.draw(buffer,format,BlockRenderLayer.SOLID);}finally{GlStateManager.enableCull();GlStateManager.enableLighting();}
        }catch(RuntimeException|LinkageError failure){delete();disabled=true;com.vandorlabs.vehicle.VehicleCompatibility.warn(members.get(0).getBlockType(),"opaque VBO section",failure);individual(cache,structure,partial);}
    }
    private void build(float partial){
        delete();format=new VertexFormat(BlockSurfaceFormat.get());List<int[]> components=new ArrayList<>();int bytes=0,member=0;boolean joining=false;
        BlockRenderLayer previous=net.minecraftforge.client.MinecraftForgeClient.getRenderLayer();
        try{
            net.minecraftforge.client.ForgeHooksClient.setRenderLayer(BlockRenderLayer.SOLID);
            for(TileEntity tile:members){
                offsets[member++]=bytes/4;
                COMPONENT.begin(GL11.GL_QUADS,format);
                boolean context=false;
                try{
                    context=VehicleShaderContext.push(tile,COMPONENT);
                    @SuppressWarnings("unchecked") TileEntitySpecialRenderer<TileEntity> renderer=TileEntityRendererDispatcher.instance.getRenderer(tile);
                    renderer.renderTileEntityFast(tile,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ(),partial,-1,1,COMPONENT);
                }finally{if(context)VehicleShaderContext.pop(COMPONENT);COMPONENT.finishDrawing();COMPONENT.setTranslation(0,0,0);}
                int[] record=COMPONENT.getVertexState().getRawBuffer();bytes=Math.addExact(bytes,record.length*4);
                if(bytes>8*1024*1024)throw new IllegalStateException("Opaque section exceeds 8 MiB");components.add(record);
            }
            offsets[member]=bytes/4;
            JOINED.begin(GL11.GL_QUADS,format);joining=true;
            // OptiFine addVertexData rewrites component block/entity IDs. A raw
            // vertex-state merge preserves each tile's captured extended payload.
            records=BlockMeshVbo.mergeRetained(JOINED,components,format);JOINED.finishDrawing();joining=false;
            int retained=Math.multiplyExact(bytes,2);lightRecords=WallLightRecords.forFormat(format);
            while(!RETAINED.isEmpty()&&retainedBytes+retained>LIMIT)RETAINED.keySet().iterator().next().delete();
            buffer=new VertexBuffer(format);buffer.bufferData(JOINED.getByteBuffer());builds++;System.arraycopy(sampled,0,lights,0,lights.length);RETAINED.put(this,retained);retainedBytes+=retained;
        }finally{if(joining)JOINED.finishDrawing();net.minecraftforge.client.ForgeHooksClient.setRenderLayer(previous);}
    }
    private void relight(){
        lightRecords.update(records,offsets,lights,sampled);
        JOINED.begin(GL11.GL_QUADS,format);
        try{JOINED.setVertexState(JOINED.new State(records,new VertexFormat(format)));}finally{JOINED.finishDrawing();}
        buffer.bufferData(JOINED.getByteBuffer());System.arraycopy(sampled,0,lights,0,lights.length);relights++;
    }
    int[] recordCopy(){return records.clone();}
    private void individual(VehicleOpaqueTileCache cache,VehicleStructure structure,float partial){for(TileEntity tile:members){GlStateManager.pushMatrix();try{cache.draw(tile,TileEntityRendererDispatcher.instance.getRenderer(tile),structure,partial);}catch(RuntimeException|LinkageError failure){com.vandorlabs.vehicle.VehicleCompatibility.warn(tile.getBlockType(),"opaque tile fallback",failure);}finally{GlStateManager.popMatrix();}}}
    void delete(){records=null;lightRecords=null;if(buffer!=null){buffer.deleteGlBuffers();buffer=null;}Integer bytes=RETAINED.remove(this);if(bytes!=null)retainedBytes-=bytes;}
    int size(){return members.size();}
}

package com.vandorlabs.gear.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.client.BlockMeshVbo;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.client.renderer.tileentity.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.*;
import org.lwjgl.opengl.GL11;

/** Native layered VBO submissions retain shader records and share the vehicle renderer's pointer setup. */
final class ArmPayloadRenderer {
    private static final Map<GearTileEntity,Mesh> CACHE=new IdentityHashMap<>();
    private static final class Mesh {
        final ArmPayload payload;final VertexFormat format=new VertexFormat(DefaultVertexFormats.BLOCK);
        final Map<BlockRenderLayer,VertexBuffer> buffers=new EnumMap<>(BlockRenderLayer.class);
        BufferBuilder.State translucent;Vec3d sorted;
        Mesh(ArmPayload payload){this.payload=payload;}
        void delete(){for(VertexBuffer b:buffers.values())b.deleteGlBuffers();buffers.clear();}
    }
    static void clear(){for(Mesh mesh:CACHE.values())mesh.delete();CACHE.clear();}
    private static Mesh mesh(GearTileEntity arm){
        Iterator<Map.Entry<GearTileEntity,Mesh>> i=CACHE.entrySet().iterator();while(i.hasNext()){Map.Entry<GearTileEntity,Mesh> entry=i.next();GearTileEntity old=entry.getKey();if(old.isInvalid()||old.getWorld()==null||old.getWorld().getTileEntity(old.getPos())!=old){entry.getValue().delete();i.remove();}}
        Mesh mesh=CACHE.get(arm);ArmPayload payload=arm.payload();
        if(mesh!=null&&(mesh.payload!=payload||!mesh.format.equals(DefaultVertexFormats.BLOCK))){mesh.delete();CACHE.remove(arm);mesh=null;}
        if(mesh!=null)return mesh;
        if(CACHE.size()>=128){Map.Entry<GearTileEntity,Mesh> oldest=CACHE.entrySet().iterator().next();oldest.getValue().delete();CACHE.remove(oldest.getKey());}
        mesh=new Mesh(payload);VehicleWorld view=payload.view(arm);
        try{for(BlockRenderLayer layer:BlockRenderLayer.values()){
            net.minecraftforge.client.ForgeHooksClient.setRenderLayer(layer);
            BufferBuilder joined=new BufferBuilder(65536);joined.begin(GL11.GL_QUADS,mesh.format);List<int[]> components=new ArrayList<>();
            for(VehicleStructure.Cell cell:payload.blocks.cells)if(cell.state.getBlock().canRenderInLayer(cell.state,layer)){
                BufferBuilder component=new BufferBuilder(4096);component.begin(GL11.GL_QUADS,mesh.format);
                Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(view.getBlockState(cell.pos),cell.pos,view,component);component.finishDrawing();
                if(component.getVertexCount()>0)components.add(component.getVertexState().getRawBuffer());
            }
            BlockMeshVbo.merge(joined,components,mesh.format);joined.finishDrawing();
            if(joined.getVertexCount()>0){if(layer==BlockRenderLayer.TRANSLUCENT)mesh.translucent=joined.getVertexState();VertexBuffer buffer=new VertexBuffer(mesh.format);buffer.bufferData(joined.getByteBuffer());mesh.buffers.put(layer,buffer);}
        }}catch(RuntimeException | LinkageError failure){mesh.delete();throw failure;}finally{net.minecraftforge.client.ForgeHooksClient.setRenderLayer(null);}
        CACHE.put(arm,mesh);return mesh;
    }
    static void verifyNativeRecords(GearTileEntity arm){
        Mesh mesh=mesh(arm);if(mesh.format==DefaultVertexFormats.BLOCK)throw new IllegalStateException("Payload VBO format is mutable global state");
        if(!mesh.buffers.containsKey(BlockRenderLayer.SOLID)||!mesh.buffers.containsKey(BlockRenderLayer.TRANSLUCENT))throw new IllegalStateException("Payload render layers were flattened");
        VehicleWorld view=arm.payload().view(arm);
        try{for(Map.Entry<BlockRenderLayer,VertexBuffer> entry:mesh.buffers.entrySet()){
            net.minecraftforge.client.ForgeHooksClient.setRenderLayer(entry.getKey());List<int[]> source=new ArrayList<>();int count=0;
            for(VehicleStructure.Cell cell:arm.payload().blocks.cells)if(cell.state.getBlock().canRenderInLayer(cell.state,entry.getKey())){BufferBuilder b=new BufferBuilder(4096);b.begin(GL11.GL_QUADS,mesh.format);Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(view.getBlockState(cell.pos),cell.pos,view,b);b.finishDrawing();if(b.getVertexCount()>0){int[] raw=b.getVertexState().getRawBuffer();source.add(raw);count+=raw.length;}}
            java.nio.ByteBuffer gpu=org.lwjgl.BufferUtils.createByteBuffer(count*4);entry.getValue().bindBuffer();try{org.lwjgl.opengl.GL15.glGetBufferSubData(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER,0,gpu);}finally{entry.getValue().unbindBuffer();}
            int at=0;for(int[] component:source)for(int value:component){if(gpu.getInt(at*4)!=value)throw new IllegalStateException("Payload VBO changed native vertex record at "+at+" in "+entry.getKey());at++;}
        }}finally{net.minecraftforge.client.ForgeHooksClient.setRenderLayer(null);}
    }
    private static Vec3d inverse(Vec3d point,Vec3d origin,Vec3d a,Vec3d b,Vec3d c){Vec3d p=point.subtract(origin);return new Vec3d(p.dotProduct(a),p.dotProduct(b),p.dotProduct(c));}
    static void render(GearTileEntity arm,double progress,double x,double y,double z,float partial){
        ArmPayload payload=arm.payload();if(payload==null||payload.materialized()||payload.hasOwnershipJournal())return;
        Vec3d origin=payload.transform(arm,progress,0,0,0),a=payload.transform(arm,progress,1,0,0).subtract(origin),b=payload.transform(arm,progress,0,1,0).subtract(origin),c=payload.transform(arm,progress,0,0,1).subtract(origin);
        TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;
        double oldX=dispatcher.entityX,oldY=dispatcher.entityY,oldZ=dispatcher.entityZ;net.minecraft.world.World old=dispatcher.world;int oldPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
        Vec3d localCamera=inverse(new Vec3d(-x,-y,-z),origin,a,b,c);
        java.nio.FloatBuffer matrix=org.lwjgl.BufferUtils.createFloatBuffer(16);matrix.put(new float[]{(float)a.x,(float)a.y,(float)a.z,0,(float)b.x,(float)b.y,(float)b.z,0,(float)c.x,(float)c.y,(float)c.z,0,(float)(x+origin.x),(float)(y+origin.y),(float)(z+origin.z),1});matrix.flip();
        GlStateManager.pushMatrix();
        try(com.vandorlabs.client.TurntableRenderState state=new com.vandorlabs.client.TurntableRenderState()){
            GlStateManager.multMatrix(matrix);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);
            Mesh mesh=mesh(arm);
            if(mesh.translucent!=null&&(mesh.sorted==null||mesh.sorted.squareDistanceTo(localCamera)>.25)){
                BufferBuilder sorted=new BufferBuilder(4096);sorted.begin(GL11.GL_QUADS,mesh.format);sorted.setVertexState(mesh.translucent);sorted.sortVertexData((float)localCamera.x,(float)localCamera.y,(float)localCamera.z);sorted.finishDrawing();mesh.buffers.get(BlockRenderLayer.TRANSLUCENT).bufferData(sorted.getByteBuffer());mesh.sorted=localCamera;
            }
            dispatcher.world=payload.view(arm);dispatcher.entityX=localCamera.x;dispatcher.entityY=localCamera.y;dispatcher.entityZ=localCamera.z;
            List<TileEntity> tiles=new ArrayList<>(payload.view(arm).tiles.values());tiles.sort((t,u)->Double.compare(u.getPos().distanceSq(localCamera.x,localCamera.y,localCamera.z),t.getPos().distanceSq(localCamera.x,localCamera.y,localCamera.z)));
            for(int pass=0;pass<2;pass++){
                net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);
                for(BlockRenderLayer layer:BlockRenderLayer.values())if((layer==BlockRenderLayer.TRANSLUCENT)==(pass==1)){
                    VertexBuffer buffer=mesh.buffers.get(layer);if(buffer==null)continue;
                    GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.enableTexture2D();Minecraft.getMinecraft().getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.color(1,1,1,1);
                    if(pass==1){GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.depthMask(false);}else{GlStateManager.disableBlend();GlStateManager.depthMask(true);}
                    net.minecraftforge.client.ForgeHooksClient.setRenderLayer(layer);BlockMeshVbo.draw(buffer,mesh.format,layer);
                }
                GlStateManager.depthMask(true);net.minecraftforge.client.ForgeHooksClient.setRenderLayer(null);
                for(TileEntity tile:tiles)if(tile.shouldRenderInPass(pass)){
                    TileEntitySpecialRenderer renderer=dispatcher.getRenderer(tile);if(renderer==null)continue;BlockPos p=tile.getPos();GlStateManager.pushMatrix();try{renderer.render(tile,p.getX(),p.getY(),p.getZ(),partial,-1,1);}finally{GlStateManager.popMatrix();}
                }
            }
        }finally{net.minecraftforge.client.ForgeHooksClient.setRenderPass(oldPass);net.minecraftforge.client.ForgeHooksClient.setRenderLayer(null);dispatcher.world=old;dispatcher.entityX=oldX;dispatcher.entityY=oldY;dispatcher.entityZ=oldZ;GlStateManager.popMatrix();}
    }
}

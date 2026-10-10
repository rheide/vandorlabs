package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.*;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Static geometry is uploaded once per layer; existing programmable tile renderers use local storage. */
public final class RenderGroundVehicle extends Render<EntityGroundVehicle> {
    private static final Map<EntityGroundVehicle,Mesh[]> meshes=new HashMap<>();
    private static int generation;
    private static final Set<EntityGroundVehicle> drawn=Collections.newSetFromMap(new IdentityHashMap<>());
    private static int fallbackDraws;
    public static int fallbackDraws(){return fallbackDraws;}
    public static void beginFrame(){drawn.clear();fallbackDraws=0;}
    public static void renderMissing(float partial) {
        Minecraft mc=Minecraft.getMinecraft();
        net.minecraft.entity.Entity camera=mc.getRenderViewEntity();
        if(mc.world==null || camera==null)return;
        net.minecraft.client.renderer.culling.Frustum frustum=new net.minecraft.client.renderer.culling.Frustum();
        frustum.setPosition(camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial,camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial,camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial);
        mc.entityRenderer.enableLightmap();
        for(EntityGroundVehicle craft:VehicleLookup.loaded(mc.world))
            if(!drawn.contains(craft) && frustum.isBoundingBoxInFrustum(craft.getRenderBoundingBox())){mc.getRenderManager().renderEntityStatic(craft,partial,false);fallbackDraws++;}
        mc.entityRenderer.disableLightmap();
    }
    private static final class Mesh {
        final List<VertexBuffer> buffers=new ArrayList<>();final List<BlockRenderLayer> layers=new ArrayList<>();
        final Map<Integer,BufferBuilder.State> translucent=new HashMap<>();
        double sortedX=Double.NaN,sortedY,sortedZ;
        VehicleStructure structure;int generation,light,level;
        void delete(){for(VertexBuffer b:buffers)b.deleteGlBuffers();buffers.clear();layers.clear();translucent.clear();sortedX=Double.NaN;}
    }
    public static void reload(){generation++;for(Mesh[] pair:meshes.values())for(Mesh mesh:pair)mesh.delete();meshes.clear();}
    public static void cleanup(net.minecraft.world.World world){Iterator<Map.Entry<EntityGroundVehicle,Mesh[]>> i=meshes.entrySet().iterator();while(i.hasNext()){Map.Entry<EntityGroundVehicle,Mesh[]> e=i.next();if(e.getKey().isDead || e.getKey().world!=world){for(Mesh mesh:e.getValue())mesh.delete();i.remove();}}}
    public RenderGroundVehicle(RenderManager manager){super(manager);shadowSize=0;}
    @Override protected ResourceLocation getEntityTexture(EntityGroundVehicle e){return TextureMap.LOCATION_BLOCKS_TEXTURE;}
    @Override public boolean shouldRender(EntityGroundVehicle e,net.minecraft.client.renderer.culling.ICamera camera,double x,double y,double z){return e.structure==null || camera.isBoundingBoxInFrustum(e.getRenderBoundingBox());}
    private Mesh mesh(EntityGroundVehicle e,boolean propulsion) {
        Mesh mesh=meshes.computeIfAbsent(e,k->new Mesh[]{new Mesh(),new Mesh()})[propulsion?1:0];
        int light=e.view.getCombinedLight(e.structure.seat,0);
        if(mesh.structure==e.structure && mesh.generation==generation && mesh.light==light && (!propulsion || mesh.level==e.view.propulsionLevel))return mesh;
        mesh.delete();mesh.structure=e.structure;mesh.generation=generation;mesh.light=light;mesh.level=e.view.propulsionLevel;
        Minecraft mc=Minecraft.getMinecraft();
        try {
            for(BlockRenderLayer layer:BlockRenderLayer.values()) {
                net.minecraftforge.client.ForgeHooksClient.setRenderLayer(layer);
                BufferBuilder builder=new BufferBuilder(65536);builder.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
                BufferBuilder component=new BufferBuilder(4096);
                for(VehicleStructure.Cell cell:e.structure.cells)if((cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockPropulsionLight)==propulsion) {
                    component.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
                    try {
                        if(cell.state.getBlock().canRenderInLayer(cell.state,layer))
                            mc.getBlockRendererDispatcher().renderBlock(e.view.getBlockState(cell.pos),cell.pos,e.view,component);
                    }catch(RuntimeException | LinkageError error) {
                        VehicleCompatibility.warn(cell.state.getBlock(),"block model",error);
                        component.finishDrawing();component.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
                        if(layer==BlockRenderLayer.SOLID)mc.getBlockRendererDispatcher().getBlockModelRenderer().renderModel(e.view,
                                mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(),cell.state,cell.pos,component,false);
                    }
                    component.finishDrawing();
                    if(component.getVertexCount()>0)builder.addVertexData(component.getVertexState().getRawBuffer());
                }
                builder.finishDrawing();
                if(builder.getVertexCount()>0){VertexBuffer buffer=new VertexBuffer(DefaultVertexFormats.BLOCK);buffer.bufferData(builder.getByteBuffer());
                    if(layer==BlockRenderLayer.TRANSLUCENT)mesh.translucent.put(mesh.buffers.size(),builder.getVertexState());
                    mesh.buffers.add(buffer);mesh.layers.add(layer);}
            }
        }finally{net.minecraftforge.client.ForgeHooksClient.setRenderLayer(null);}
        return mesh;
    }
    private void sortTranslucent(Mesh mesh,double x,double y,double z) {
        if(mesh.translucent.isEmpty())return;
        if(!Double.isNaN(mesh.sortedX) && (x-mesh.sortedX)*(x-mesh.sortedX)+(y-mesh.sortedY)*(y-mesh.sortedY)+(z-mesh.sortedZ)*(z-mesh.sortedZ)<1)return;
        mesh.sortedX=x;mesh.sortedY=y;mesh.sortedZ=z;
        for(Map.Entry<Integer,BufferBuilder.State> entry:mesh.translucent.entrySet()) {
            BufferBuilder builder=new BufferBuilder(4096);builder.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
            builder.setVertexState(entry.getValue());builder.sortVertexData((float)x,(float)y,(float)z);builder.finishDrawing();
            mesh.buffers.get(entry.getKey()).bufferData(builder.getByteBuffer());
        }
    }
    @Override public void doRender(EntityGroundVehicle e,double x,double y,double z,float yaw,float partial) {
        if(e.structure==null)return;
        drawn.add(e);
        e.view.origin=new BlockPos(e.posX,e.posY,e.posZ);Mesh[] pair={mesh(e,false),mesh(e,true)};
        net.minecraft.util.math.Vec3d correction=e.renderCorrection();x+=correction.x;y+=correction.y;z+=correction.z;
        float angle=e.prevRotationYaw+net.minecraft.util.math.MathHelper.wrapDegrees(e.rotationYaw-e.prevRotationYaw)*partial;
        net.minecraft.util.math.Vec3d localCamera=e.toLocal(new net.minecraft.util.math.Vec3d(e.posX-x,e.posY-y,e.posZ-z));
        for(Mesh mesh:pair)sortTranslucent(mesh,localCamera.x,localCamera.y,localCamera.z);
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);GlStateManager.translate(e.structure.bounds.maxX/2,0,e.structure.bounds.maxZ/2);GlStateManager.rotate(-angle,0,1,0);GlStateManager.translate(-e.structure.bounds.maxX/2,0,-e.structure.bounds.maxZ/2);GlStateManager.disableLighting();GlStateManager.enableTexture2D();bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1,1,1,1);
        for(Mesh mesh:pair)for(int i=0;i<mesh.buffers.size();i++) {
            if(mesh.layers.get(i)==BlockRenderLayer.TRANSLUCENT){GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.depthMask(false);}else {GlStateManager.disableBlend();GlStateManager.depthMask(true);}
            VertexBuffer buffer=mesh.buffers.get(i);buffer.bindBuffer();
            GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
            GlStateManager.glVertexPointer(3,GL11.GL_FLOAT,28,0);GlStateManager.glColorPointer(4,GL11.GL_UNSIGNED_BYTE,28,12);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);GlStateManager.glTexCoordPointer(2,GL11.GL_FLOAT,28,16);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);GlStateManager.glTexCoordPointer(2,GL11.GL_SHORT,28,24);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);buffer.drawArrays(GL11.GL_QUADS);buffer.unbindBuffer();
            GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        }
        GlStateManager.depthMask(true);GlStateManager.disableBlend();GlStateManager.enableLighting();
        // Each vehicle owns a separate World identity, isolating renderer adjacency and animation caches.
        TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;
        double cameraX=dispatcher.entityX,cameraY=dispatcher.entityY,cameraZ=dispatcher.entityZ;
        net.minecraft.world.World previousWorld=dispatcher.world;
        dispatcher.world=e.view;dispatcher.entityX=localCamera.x;dispatcher.entityY=localCamera.y;dispatcher.entityZ=localCamera.z;
        try {for(TileEntity tile:e.view.tiles.values()) {
            TileEntitySpecialRenderer<TileEntity> renderer=TileEntityRendererDispatcher.instance.getRenderer(tile);
            if(renderer!=null){BlockPos p=tile.getPos();GlStateManager.pushMatrix();
                try{renderer.render(tile,p.getX(),p.getY(),p.getZ(),partial,-1,1);}
                catch(RuntimeException | LinkageError error){VehicleCompatibility.warn(tile.getBlockType(),"tile renderer",error);}
                finally{GlStateManager.popMatrix();}
            }
        }}finally{dispatcher.world=previousWorld;dispatcher.entityX=cameraX;dispatcher.entityY=cameraY;dispatcher.entityZ=cameraZ;}
        GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();
    }
}

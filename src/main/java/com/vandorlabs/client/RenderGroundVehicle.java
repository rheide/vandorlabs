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
    private static final Map<EntityGroundVehicle,Regions> meshes=new HashMap<>();
    private static int generation;
    private static final Set<EntityGroundVehicle> drawn=Collections.newSetFromMap(new IdentityHashMap<>());
    private static int fallbackDraws,tileDraws;
    public static int tileDraws(){return tileDraws;}
    private static final long[] frameSamples=VehicleDiagnostics.ENABLED?new long[256]:null,meshSamples=VehicleDiagnostics.ENABLED?new long[256]:null,uploadSamples=VehicleDiagnostics.ENABLED?new long[256]:null;
    private static int frameCount,meshCount,uploadCount;private static long lastFrame;
    private static String percentile(String label,long[] samples,int count){if(count==0)return label+"=none";long[] sorted=Arrays.copyOf(samples,Math.min(count,samples.length));Arrays.sort(sorted);return label+"-us[p50/p95/p99]="+sorted[sorted.length/2]/1000+"/"+sorted[(sorted.length-1)*95/100]/1000+"/"+sorted[(sorted.length-1)*99/100]/1000;}
    public static String profile(){return !VehicleDiagnostics.ENABLED?"disabled":percentile("frame-interval",frameSamples,frameCount)+" "+percentile("mesh-work",meshSamples,meshCount)+" "+percentile("upload",uploadSamples,uploadCount);}
    public static int fallbackDraws(){return fallbackDraws;}
    public static void beginFrame(){if(VehicleDiagnostics.ENABLED){long now=System.nanoTime();if(lastFrame!=0 && !drawn.isEmpty())frameSamples[frameCount++%frameSamples.length]=now-lastFrame;lastFrame=now;}drawn.clear();fallbackDraws=tileDraws=0;buildNanos=2000000;buildCells=512;}
    private static long buildNanos;private static int buildCells;
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
    private static final class Regions {
        VehicleStructure structure;
        final Map<BlockPos,Mesh[]> sections=new LinkedHashMap<>();
        void delete(){for(Mesh[] pair:sections.values())for(Mesh mesh:pair)mesh.delete();sections.clear();}
    }
    private static final class Mesh {
        final List<VertexBuffer> buffers=new ArrayList<>();final List<BlockRenderLayer> layers=new ArrayList<>();
        final Map<Integer,BufferBuilder.State> translucent=new HashMap<>();
        double sortedX=Double.NaN,sortedY,sortedZ;
        List<VehicleStructure.Cell> cells=Collections.emptyList(),builtCells;
        int generation,light,level;
        Build pending;
        void delete(){for(VertexBuffer b:buffers)b.deleteGlBuffers();buffers.clear();layers.clear();translucent.clear();sortedX=Double.NaN;if(pending!=null){pending.result.delete();pending=null;}}
    }
    private static final class Build {
        final Mesh result=new Mesh();final List<VehicleStructure.Cell> cells;
        final BufferBuilder component=new BufferBuilder(4096);
        BufferBuilder builder;
        int layer,cell;
        Build(Mesh source,int light,int level){cells=source.cells;result.generation=generation;result.light=light;result.level=level;}
    }
    public static void reload(){generation++;for(Regions regions:meshes.values())regions.delete();meshes.clear();}
    public static void cleanup(net.minecraft.world.World world){Iterator<Map.Entry<EntityGroundVehicle,Regions>> i=meshes.entrySet().iterator();while(i.hasNext()){Map.Entry<EntityGroundVehicle,Regions> e=i.next();if(e.getKey().isDead || e.getKey().world!=world){e.getValue().delete();i.remove();}}}
    private static boolean same(List<VehicleStructure.Cell> a,List<VehicleStructure.Cell> b) {
        if(a.size()!=b.size())return false;
        for(int n=0;n<a.size();n++) {
            VehicleStructure.Cell x=a.get(n),y=b.get(n);if(x==y)continue;
            if(!x.pos.equals(y.pos) || x.state!=y.state)return false;
            if(x.state.getBlock() instanceof com.vandorlabs.blocks.BlockPilotSeat
                    && (x.tileData()==null?0:x.tileData().getInteger("PilotHeight"))!=(y.tileData()==null?0:y.tileData().getInteger("PilotHeight")))return false;
            if(!(x.state.getBlock() instanceof com.vandorlabs.blocks.BlockPilotSeat) && !Objects.equals(x.tileData(),y.tileData()))return false;
        }
        return true;
    }
    private Regions regions(EntityGroundVehicle e) {
        Regions regions=meshes.computeIfAbsent(e,k->new Regions());if(regions.structure==e.structure)return regions;
        Map<BlockPos,List<VehicleStructure.Cell>[]> groups=new LinkedHashMap<>();
        Set<BlockPos> dirty=new HashSet<>();
        if(regions.structure!=null) {
            for(VehicleStructure.Cell cell:e.structure.cells) {
                VehicleStructure.Cell before=regions.structure.at(cell.pos);
                if(before==null || !same(Collections.singletonList(before),Collections.singletonList(cell)))
                    for(net.minecraft.util.EnumFacing side:net.minecraft.util.EnumFacing.values())dirty.add(section(cell.pos.offset(side)));
            }
        }
        if(regions.structure!=null)for(VehicleStructure.Cell cell:regions.structure.cells)if(e.structure.at(cell.pos)==null)
            for(net.minecraft.util.EnumFacing side:net.minecraft.util.EnumFacing.values())dirty.add(section(cell.pos.offset(side)));
        for(VehicleStructure.Cell cell:e.structure.cells) {
            List<VehicleStructure.Cell>[] lists=groups.computeIfAbsent(section(cell.pos),k->new List[]{new ArrayList<>(),new ArrayList<>()});
            lists[cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockPropulsionLight?1:0].add(cell);
        }
        Iterator<Map.Entry<BlockPos,Mesh[]>> removed=regions.sections.entrySet().iterator();
        while(removed.hasNext()){Map.Entry<BlockPos,Mesh[]> entry=removed.next();if(!groups.containsKey(entry.getKey())){for(Mesh mesh:entry.getValue())mesh.delete();removed.remove();}}
        for(Map.Entry<BlockPos,List<VehicleStructure.Cell>[]> entry:groups.entrySet()) {
            Mesh[] pair=regions.sections.computeIfAbsent(entry.getKey(),k->new Mesh[]{new Mesh(),new Mesh()});
            for(int n=0;n<2;n++)if(dirty.contains(entry.getKey()) || !same(pair[n].cells,entry.getValue()[n])) {
                pair[n].cells=entry.getValue()[n];pair[n].builtCells=null;
                if(pair[n].pending!=null){pair[n].pending.result.delete();pair[n].pending=null;}
            }
        }
        regions.structure=e.structure;return regions;
    }
    private static BlockPos section(BlockPos p){return new BlockPos(p.getX()>>3,p.getY()>>3,p.getZ()>>3);}
    public RenderGroundVehicle(RenderManager manager){super(manager);shadowSize=0;}
    @Override protected ResourceLocation getEntityTexture(EntityGroundVehicle e){return TextureMap.LOCATION_BLOCKS_TEXTURE;}
    @Override public boolean shouldRender(EntityGroundVehicle e,net.minecraft.client.renderer.culling.ICamera camera,double x,double y,double z){return e.structure==null || camera.isBoundingBoxInFrustum(e.getRenderBoundingBox());}
    private Mesh mesh(EntityGroundVehicle e,Mesh mesh,boolean propulsion) {
        int light=e.view.getCombinedLight(e.structure.seat,0);
        if(mesh.builtCells==mesh.cells && mesh.generation==generation && mesh.light==light && (!propulsion || mesh.level==e.view.propulsionLevel))return mesh;
        if(buildCells<=0 || buildNanos<=0)return mesh;
        if(mesh.pending==null)mesh.pending=new Build(mesh,light,e.view.propulsionLevel);
        Build job=mesh.pending;long start=System.nanoTime(),deadline=start+buildNanos;
        Minecraft mc=Minecraft.getMinecraft();
        try {
            while(job.layer<BlockRenderLayer.values().length && buildCells>0 && System.nanoTime()<deadline) {
                BlockRenderLayer layer=BlockRenderLayer.values()[job.layer];net.minecraftforge.client.ForgeHooksClient.setRenderLayer(layer);
                if(job.builder==null){job.builder=new BufferBuilder(65536);job.builder.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);}
                while(job.cell<job.cells.size() && buildCells>0 && System.nanoTime()<deadline) {
                    VehicleStructure.Cell cell=job.cells.get(job.cell++);buildCells--;
                    job.component.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
                    try {if(cell.state.getBlock().canRenderInLayer(cell.state,layer))mc.getBlockRendererDispatcher().renderBlock(e.view.getBlockState(cell.pos),cell.pos,e.view,job.component);}
                    catch(RuntimeException | LinkageError error) {
                        VehicleCompatibility.warn(cell.state.getBlock(),"block model",error);job.component.finishDrawing();job.component.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
                        if(layer==BlockRenderLayer.SOLID)mc.getBlockRendererDispatcher().getBlockModelRenderer().renderModel(e.view,mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(),cell.state,cell.pos,job.component,false);
                    }
                    job.component.finishDrawing();if(job.component.getVertexCount()>0)job.builder.addVertexData(job.component.getVertexState().getRawBuffer());
                }
                if(job.cell<job.cells.size())break;
                job.builder.finishDrawing();
                if(job.builder.getVertexCount()>0) {
                    long upload=VehicleDiagnostics.ENABLED?System.nanoTime():0;
                    VertexBuffer buffer=new VertexBuffer(DefaultVertexFormats.BLOCK);buffer.bufferData(job.builder.getByteBuffer());
                    if(VehicleDiagnostics.ENABLED)uploadSamples[uploadCount++%uploadSamples.length]=System.nanoTime()-upload;
                    if(layer==BlockRenderLayer.TRANSLUCENT)job.result.translucent.put(job.result.buffers.size(),job.builder.getVertexState());
                    job.result.buffers.add(buffer);job.result.layers.add(layer);
                }
                job.builder=null;job.cell=0;job.layer++;
            }
        }finally{net.minecraftforge.client.ForgeHooksClient.setRenderLayer(null);long elapsed=System.nanoTime()-start;buildNanos-=elapsed;if(VehicleDiagnostics.ENABLED)meshSamples[meshCount++%meshSamples.length]=elapsed;}
        if(job.layer==BlockRenderLayer.values().length) {
            mesh.pending=null;mesh.delete();mesh.buffers.addAll(job.result.buffers);mesh.layers.addAll(job.result.layers);mesh.translucent.putAll(job.result.translucent);
            mesh.builtCells=mesh.cells;mesh.generation=generation;mesh.light=job.result.light;mesh.level=job.result.level;
        }
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
        e.view.origin=new BlockPos(e.posX,e.posY,e.posZ);Regions regions=regions(e);List<Mesh> pair=new ArrayList<>();
        for(Mesh[] section:regions.sections.values()){pair.add(section[0]);pair.add(section[1]);}
        // Finish new/changed geometry before refreshing light on already visible regions.
        for(int pass=0;pass<2;pass++)for(Mesh[] section:regions.sections.values())for(int n=0;n<2;n++)
            if((section[n].builtCells!=section[n].cells)==(pass==0))mesh(e,section[n],n==1);
        VehiclePose pose=e.visualPose(partial);
        double cameraXWorld=e.lastTickPosX+(e.posX-e.lastTickPosX)*partial-x;
        double cameraYWorld=e.lastTickPosY+(e.posY-e.lastTickPosY)*partial-y;
        double cameraZWorld=e.lastTickPosZ+(e.posZ-e.lastTickPosZ)*partial-z;
        x=pose.x-cameraXWorld;y=pose.y-cameraYWorld;z=pose.z-cameraZWorld;
        float angle=pose.yaw;
        net.minecraft.util.math.Vec3d localCamera=pose.local(new net.minecraft.util.math.Vec3d(cameraXWorld,cameraYWorld,cameraZWorld),e.structure.bounds.maxX/2,e.structure.bounds.maxZ/2);
        for(Mesh mesh:pair)sortTranslucent(mesh,localCamera.x,localCamera.y,localCamera.z);
        // Capture the camera planes before applying the craft's model transform.
        net.minecraft.client.renderer.culling.Frustum tileFrustum=new net.minecraft.client.renderer.culling.Frustum();
        tileFrustum.setPosition(cameraXWorld,cameraYWorld,cameraZWorld);
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);GlStateManager.translate(e.structure.bounds.maxX/2,0,e.structure.bounds.maxZ/2);GlStateManager.rotate(-angle,0,1,0);GlStateManager.translate(-e.structure.bounds.maxX/2,0,-e.structure.bounds.maxZ/2);GlStateManager.disableLighting();GlStateManager.enableTexture2D();bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1,1,1,1);
        pair.sort(Comparator.comparingDouble((Mesh mesh)->mesh.cells.isEmpty()?0:-localCamera.squareDistanceTo(new net.minecraft.util.math.Vec3d(section(mesh.cells.get(0).pos)).scale(8).addVector(4,4,4))));
        for(BlockRenderLayer layer:BlockRenderLayer.values())for(Mesh mesh:pair)for(int i=0;i<mesh.buffers.size();i++) {
            if(mesh.layers.get(i)!=layer)continue;
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
            try {
                net.minecraft.util.math.AxisAlignedBB box=tile.getRenderBoundingBox();
                if(box!=null && Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                        && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ)
                        && !tileFrustum.isBoundingBoxInFrustum(pose.world(box,e.structure.bounds.maxX/2,e.structure.bounds.maxZ/2)))continue;
            }catch(RuntimeException | LinkageError error){VehicleCompatibility.warn(tile.getBlockType(),"tile visibility",error);}
            TileEntitySpecialRenderer<TileEntity> renderer=TileEntityRendererDispatcher.instance.getRenderer(tile);
            if(renderer!=null){BlockPos p=tile.getPos();GlStateManager.pushMatrix();
                try{renderer.render(tile,p.getX(),p.getY(),p.getZ(),partial,-1,1);tileDraws++;}
                catch(RuntimeException | LinkageError error){VehicleCompatibility.warn(tile.getBlockType(),"tile renderer",error);}
                finally{GlStateManager.popMatrix();}
            }
        }}finally{dispatcher.world=previousWorld;dispatcher.entityX=cameraX;dispatcher.entityY=cameraY;dispatcher.entityZ=cameraZ;}
        GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();
    }
}

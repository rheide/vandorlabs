package com.vandorlabs.client;

import com.vandorlabs.turntable.*;
import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Atlas-baked inert contents with native tile/entity renderers, all under the same face rotation. */
public final class RenderTurntable extends Render<EntityTurntable> {
    private static final Map<EntityTurntable,Mesh> CACHE=new HashMap<>();
    private static long buildNanos;
    private static int buildCells;
    static boolean cacheWalls=true;
    static int wallBuilds(EntityTurntable entity){Mesh mesh=CACHE.get(entity);return mesh==null?0:mesh.opaqueTiles.builds;}
    public static void beginFrame(){buildNanos=2_000_000L;buildCells=512;}
    private static final class Mesh {
        TurntableSnapshot snapshot;int layer,cell,storedEntity,batchCells;
        BufferBuilder builder;
        final Map<BlockRenderLayer,List<Integer>> lists=new EnumMap<>(BlockRenderLayer.class);
        final List<Entity> entities=new ArrayList<>();final List<TileEntity> tiles=new ArrayList<>(),translucentTiles=new ArrayList<>();
        final Map<TileEntity,net.minecraft.util.math.Vec3d> centers=new IdentityHashMap<>();
        final VehicleOpaqueTileCache opaqueTiles=new VehicleOpaqueTileCache();
        void delete(){for(List<Integer> layer:lists.values())for(int list:layer)GL11.glDeleteLists(list,1);lists.clear();builder=null;opaqueTiles.delete();}
        void draw(BlockRenderLayer layer){List<Integer> ready=lists.get(layer);if(ready!=null)for(int list:ready)GL11.glCallList(list);}
    }
    public RenderTurntable(RenderManager manager){super(manager);shadowSize=0;}
    public static void clear(){for(Mesh mesh:CACHE.values())mesh.delete();CACHE.clear();}
    @Override protected ResourceLocation getEntityTexture(EntityTurntable entity){return TextureMap.LOCATION_BLOCKS_TEXTURE;}
    private Mesh mesh(EntityTurntable entity){
        Iterator<Map.Entry<EntityTurntable,Mesh>> entries=CACHE.entrySet().iterator();while(entries.hasNext()){Map.Entry<EntityTurntable,Mesh> entry=entries.next();if(entry.getKey().isDead||entry.getKey().world!=entity.world){entry.getValue().delete();entries.remove();}}
        Mesh mesh=CACHE.computeIfAbsent(entity,k->new Mesh());
        if(mesh.snapshot==entity.snapshot){build(entity,mesh);return mesh;}
        mesh.delete();mesh.entities.clear();mesh.tiles.clear();mesh.translucentTiles.clear();mesh.centers.clear();mesh.snapshot=entity.snapshot;
        mesh.layer=mesh.cell=mesh.storedEntity=mesh.batchCells=0;
        for(TileEntity tile:entity.view.tiles.values()){
            mesh.tiles.add(tile);net.minecraft.util.math.AxisAlignedBB bounds=new net.minecraft.util.math.AxisAlignedBB(tile.getPos());
            if(tile.shouldRenderInPass(1)||!tile.getClass().getName().startsWith("com.vandorlabs."))mesh.translucentTiles.add(tile);
            if(tile instanceof com.vandorlabs.tiles.TileEntityCanopy){
                List<net.minecraft.util.math.AxisAlignedBB> boxes=((com.vandorlabs.tiles.TileEntityCanopy)tile).boxes();
                if(!boxes.isEmpty()){bounds=boxes.get(0);for(int i=1;i<boxes.size();i++)bounds=bounds.union(boxes.get(i));}
            }
            mesh.centers.put(tile,bounds.getCenter());
        }
        build(entity,mesh);
        return mesh;
    }
    /** Private buffers survive frames; no global Tessellator or GL list stays open across frames. */
    private void build(EntityTurntable entity,Mesh mesh){
        if(buildCells<=0||buildNanos<=0)return;
        long began=System.nanoTime(),deadline=began+buildNanos;
        try{
            while(mesh.layer<BlockRenderLayer.values().length&&buildCells>0&&System.nanoTime()<deadline){
                BlockRenderLayer layer=BlockRenderLayer.values()[mesh.layer];net.minecraftforge.client.ForgeHooksClient.setRenderLayer(layer);
                if(mesh.builder==null){mesh.builder=new BufferBuilder(65536);mesh.builder.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);}
                while(mesh.cell<entity.snapshot.blocks.cells.size()&&mesh.batchCells<64&&buildCells>0&&System.nanoTime()<deadline){
                    VehicleStructure.Cell cell=entity.snapshot.blocks.cells.get(mesh.cell++);mesh.batchCells++;buildCells--;
                    if(cell.state.getBlock().canRenderInLayer(cell.state,layer))Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(entity.view.getBlockState(cell.pos),cell.pos,entity.view,mesh.builder);
                }
                if(mesh.cell==entity.snapshot.blocks.cells.size()||mesh.batchCells==64){
                    mesh.builder.finishDrawing();
                    if(mesh.builder.getVertexCount()>0){int list=GL11.glGenLists(1);GL11.glNewList(list,GL11.GL_COMPILE);
                        try{new WorldVertexBufferUploader().draw(mesh.builder);}finally{GL11.glEndList();}
                        mesh.lists.computeIfAbsent(layer,k->new ArrayList<>()).add(list);
                    }
                    mesh.builder=null;mesh.batchCells=0;
                    if(mesh.cell==entity.snapshot.blocks.cells.size()){mesh.cell=0;mesh.layer++;}
                }
            }
            while(mesh.storedEntity<entity.snapshot.entities.size()&&buildCells>0&&System.nanoTime()<deadline){
                TurntableSnapshot.FrozenEntity frozen=entity.snapshot.entities.get(mesh.storedEntity++);buildCells--;
                Entity copy=EntityList.createEntityFromNBT(frozen.data(),entity.view);if(copy!=null){copy.setPosition(copy.posX-entity.snapshot.origin.getX(),copy.posY-entity.snapshot.origin.getY(),copy.posZ-entity.snapshot.origin.getZ());copy.prevPosX=copy.lastTickPosX=copy.posX;copy.prevPosY=copy.lastTickPosY=copy.posY;copy.prevPosZ=copy.lastTickPosZ=copy.posZ;mesh.entities.add(copy);}
            }
        }finally{net.minecraftforge.client.ForgeHooksClient.setRenderLayer(null);buildNanos-=System.nanoTime()-began;}
    }
    @Override public void doRender(EntityTurntable entity,double x,double y,double z,float yaw,float partial){
        if(entity.snapshot==null||entity.view==null)return;Minecraft mc=Minecraft.getMinecraft();
        long profile=TurntablePerformance.start();
        GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){GlStateManager.translate(x,y,z);TurntableGeometry.Face face=entity.snapshot.layout.basis;GlStateManager.rotate((float)-entity.angle(partial),face.x,face.y,face.z);
            GlStateManager.translate(entity.snapshot.origin.getX()-entity.posX,entity.snapshot.origin.getY()-entity.posY,entity.snapshot.origin.getZ()-entity.posZ);
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.color(1,1,1,1);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableCull();RenderHelper.disableStandardItemLighting();
            long buildProfile=TurntablePerformance.start();
            Mesh mesh=mesh(entity);
            TurntablePerformance.end("render.mesh-build",buildProfile);
            for(BlockRenderLayer layer:BlockRenderLayer.values())if(layer!=BlockRenderLayer.TRANSLUCENT){GlStateManager.disableBlend();GlStateManager.depthMask(true);mesh.draw(layer);}
            TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;
            net.minecraft.world.World oldWorld=dispatcher.world;
            double oldX=dispatcher.entityX,oldY=dispatcher.entityY,oldZ=dispatcher.entityZ;
            int oldPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
            // TESRs sort glazing in their own local world, including wall/canopy faces.
            double[] camera=TurntableGeometry.rotate(-x,-y,-z,face,-entity.angle(partial));
            dispatcher.world=entity.view;
            dispatcher.entityX=camera[0]+entity.posX-entity.snapshot.origin.getX();
            dispatcher.entityY=camera[1]+entity.posY-entity.snapshot.origin.getY();
            dispatcher.entityZ=camera[2]+entity.posZ-entity.snapshot.origin.getZ();
            long tileProfile=TurntablePerformance.start();
            List<TileEntity> tiles=mesh.translucentTiles;
            tiles.sort((a,b)->{int distance=Double.compare(mesh.centers.get(b).squareDistanceTo(dispatcher.entityX,dispatcher.entityY,dispatcher.entityZ),mesh.centers.get(a).squareDistanceTo(dispatcher.entityX,dispatcher.entityY,dispatcher.entityZ));return distance!=0?distance:a.getPos().compareTo(b.getPos());});
            try{for(int pass=0;pass<2;pass++){
                net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);
                if(pass==1){bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.depthMask(false);mesh.draw(BlockRenderLayer.TRANSLUCENT);GlStateManager.depthMask(true);}
                for(TileEntity tile:pass==0?mesh.tiles:tiles){
                    if(!tile.shouldRenderInPass(pass))continue;
                    TileEntitySpecialRenderer<TileEntity> renderer=dispatcher.getRenderer(tile);if(renderer==null)continue;BlockPos p=tile.getPos();GlStateManager.pushMatrix();
                    long rendererProfile=TurntablePerformance.start();
                    try{
                        boolean rigid=cacheWalls&&pass==0&&VehicleOpaqueTileCache.eligible(tile,renderer);
                        if(rigid&&(mesh.opaqueTiles.ready(tile)||buildCells>0&&buildNanos>0)){
                            boolean ready=mesh.opaqueTiles.ready(tile);long began=ready?0:System.nanoTime();
                            mesh.opaqueTiles.draw(tile,renderer,entity.snapshot.blocks,partial);
                            if(!ready){buildNanos-=System.nanoTime()-began;buildCells--;}
                        }else renderer.render(tile,p.getX(),p.getY(),p.getZ(),partial,-1,1);
                    }catch(RuntimeException failure){com.vandorlabs.vehicle.VehicleCompatibility.warn(tile.getBlockType(),"turntable renderer",failure);}finally{GlStateManager.popMatrix();if(TurntablePerformance.ENABLED)TurntablePerformance.end("renderer."+renderer.getClass().getSimpleName(),rendererProfile);}
                }
            }}finally{net.minecraftforge.client.ForgeHooksClient.setRenderPass(oldPass);dispatcher.world=oldWorld;dispatcher.entityX=oldX;dispatcher.entityY=oldY;dispatcher.entityZ=oldZ;}
            TurntablePerformance.end("render.tile-renderers-and-sort",tileProfile);
            for(Entity copy:mesh.entities)mc.getRenderManager().renderEntity(copy,copy.posX,copy.posY,copy.posZ,copy.rotationYaw,partial,false);
        }finally{GlStateManager.popMatrix();TurntablePerformance.end(entity.angle(0)==entity.angle(1)?"render.paused":"render.rotating",profile);}
    }
}

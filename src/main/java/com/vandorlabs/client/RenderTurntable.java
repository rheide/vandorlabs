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
    private static final class Mesh {TurntableSnapshot snapshot;int list;final List<Entity> entities=new ArrayList<>();final List<TileEntity> tiles=new ArrayList<>();final Map<TileEntity,net.minecraft.util.math.Vec3d> centers=new IdentityHashMap<>();void delete(){if(list!=0)GL11.glDeleteLists(list,BlockRenderLayer.values().length);}}
    public RenderTurntable(RenderManager manager){super(manager);shadowSize=0;}
    public static void clear(){for(Mesh mesh:CACHE.values())mesh.delete();CACHE.clear();}
    @Override protected ResourceLocation getEntityTexture(EntityTurntable entity){return TextureMap.LOCATION_BLOCKS_TEXTURE;}
    private Mesh mesh(EntityTurntable entity){
        Iterator<Map.Entry<EntityTurntable,Mesh>> entries=CACHE.entrySet().iterator();while(entries.hasNext()){Map.Entry<EntityTurntable,Mesh> entry=entries.next();if(entry.getKey().isDead||entry.getKey().world!=entity.world){entry.getValue().delete();entries.remove();}}
        Mesh mesh=CACHE.computeIfAbsent(entity,k->new Mesh());if(mesh.snapshot==entity.snapshot)return mesh;mesh.delete();mesh.entities.clear();mesh.tiles.clear();mesh.centers.clear();mesh.snapshot=entity.snapshot;
        for(TileEntity tile:entity.view.tiles.values()){
            mesh.tiles.add(tile);net.minecraft.util.math.AxisAlignedBB bounds=new net.minecraft.util.math.AxisAlignedBB(tile.getPos());
            if(tile instanceof com.vandorlabs.tiles.TileEntityCanopy){
                List<net.minecraft.util.math.AxisAlignedBB> boxes=((com.vandorlabs.tiles.TileEntityCanopy)tile).boxes();
                if(!boxes.isEmpty()){bounds=boxes.get(0);for(int i=1;i<boxes.size();i++)bounds=bounds.union(boxes.get(i));}
            }
            mesh.centers.put(tile,bounds.getCenter());
        }
        mesh.list=GL11.glGenLists(BlockRenderLayer.values().length);
        try{for(BlockRenderLayer layer:BlockRenderLayer.values()){
            GL11.glNewList(mesh.list+layer.ordinal(),GL11.GL_COMPILE);
            net.minecraftforge.client.ForgeHooksClient.setRenderLayer(layer);BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
            for(VehicleStructure.Cell cell:entity.snapshot.blocks.cells)if(cell.state.getBlock().canRenderInLayer(cell.state,layer))Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(entity.view.getBlockState(cell.pos),cell.pos,entity.view,buffer);
            Tessellator.getInstance().draw();GL11.glEndList();
        }}finally{net.minecraftforge.client.ForgeHooksClient.setRenderLayer(null);}
        for(TurntableSnapshot.FrozenEntity frozen:entity.snapshot.entities){Entity copy=EntityList.createEntityFromNBT(frozen.data(),entity.view);if(copy!=null){copy.setPosition(copy.posX-entity.snapshot.origin.getX(),copy.posY-entity.snapshot.origin.getY(),copy.posZ-entity.snapshot.origin.getZ());copy.prevPosX=copy.lastTickPosX=copy.posX;copy.prevPosY=copy.lastTickPosY=copy.posY;copy.prevPosZ=copy.lastTickPosZ=copy.posZ;mesh.entities.add(copy);}}
        return mesh;
    }
    @Override public void doRender(EntityTurntable entity,double x,double y,double z,float yaw,float partial){
        if(entity.snapshot==null||entity.view==null)return;Minecraft mc=Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){GlStateManager.translate(x,y,z);TurntableGeometry.Face face=entity.snapshot.layout.basis;GlStateManager.rotate((float)-entity.angle(partial),face.x,face.y,face.z);
            GlStateManager.translate(entity.snapshot.origin.getX()-entity.posX,entity.snapshot.origin.getY()-entity.posY,entity.snapshot.origin.getZ()-entity.posZ);
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.color(1,1,1,1);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableCull();RenderHelper.disableStandardItemLighting();
            Mesh mesh=mesh(entity);
            for(BlockRenderLayer layer:BlockRenderLayer.values())if(layer!=BlockRenderLayer.TRANSLUCENT){GlStateManager.disableBlend();GlStateManager.depthMask(true);GL11.glCallList(mesh.list+layer.ordinal());}
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
            List<TileEntity> tiles=mesh.tiles;
            tiles.sort((a,b)->{int distance=Double.compare(mesh.centers.get(b).squareDistanceTo(dispatcher.entityX,dispatcher.entityY,dispatcher.entityZ),mesh.centers.get(a).squareDistanceTo(dispatcher.entityX,dispatcher.entityY,dispatcher.entityZ));return distance!=0?distance:a.getPos().compareTo(b.getPos());});
            try{for(int pass=0;pass<2;pass++){
                net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);
                if(pass==1){bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.depthMask(false);GL11.glCallList(mesh.list+BlockRenderLayer.TRANSLUCENT.ordinal());GlStateManager.depthMask(true);}
                for(TileEntity tile:tiles){
                    if(!tile.shouldRenderInPass(pass))continue;
                    TileEntitySpecialRenderer<TileEntity> renderer=dispatcher.getRenderer(tile);if(renderer==null)continue;BlockPos p=tile.getPos();GlStateManager.pushMatrix();
                    try{renderer.render(tile,p.getX(),p.getY(),p.getZ(),partial,-1,1);}catch(RuntimeException failure){com.vandorlabs.vehicle.VehicleCompatibility.warn(tile.getBlockType(),"turntable renderer",failure);}finally{GlStateManager.popMatrix();}
                }
            }}finally{net.minecraftforge.client.ForgeHooksClient.setRenderPass(oldPass);dispatcher.world=oldWorld;dispatcher.entityX=oldX;dispatcher.entityY=oldY;dispatcher.entityZ=oldZ;}
            for(Entity copy:mesh.entities)mc.getRenderManager().renderEntity(copy,copy.posX,copy.posY,copy.posZ,copy.rotationYaw,partial,false);
        }finally{GlStateManager.popMatrix();}
    }
}

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
    private static final class Mesh {TurntableSnapshot snapshot;int list;final List<Entity> entities=new ArrayList<>();void delete(){if(list!=0)GL11.glDeleteLists(list,1);}}
    public RenderTurntable(RenderManager manager){super(manager);shadowSize=0;}
    public static void clear(){for(Mesh mesh:CACHE.values())mesh.delete();CACHE.clear();}
    @Override protected ResourceLocation getEntityTexture(EntityTurntable entity){return TextureMap.LOCATION_BLOCKS_TEXTURE;}
    private Mesh mesh(EntityTurntable entity){
        Iterator<Map.Entry<EntityTurntable,Mesh>> entries=CACHE.entrySet().iterator();while(entries.hasNext()){Map.Entry<EntityTurntable,Mesh> entry=entries.next();if(entry.getKey().isDead||entry.getKey().world!=entity.world){entry.getValue().delete();entries.remove();}}
        Mesh mesh=CACHE.computeIfAbsent(entity,k->new Mesh());if(mesh.snapshot==entity.snapshot)return mesh;mesh.delete();mesh.entities.clear();mesh.snapshot=entity.snapshot;
        mesh.list=GL11.glGenLists(1);GL11.glNewList(mesh.list,GL11.GL_COMPILE);
        try{for(BlockRenderLayer layer:BlockRenderLayer.values()){
            net.minecraftforge.client.ForgeHooksClient.setRenderLayer(layer);BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
            for(VehicleStructure.Cell cell:entity.snapshot.blocks.cells)if(cell.state.getBlock().canRenderInLayer(cell.state,layer))Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(entity.view.getBlockState(cell.pos),cell.pos,entity.view,buffer);
            Tessellator.getInstance().draw();
        }}finally{net.minecraftforge.client.ForgeHooksClient.setRenderLayer(null);GL11.glEndList();}
        for(TurntableSnapshot.FrozenEntity frozen:entity.snapshot.entities){Entity copy=EntityList.createEntityFromNBT(frozen.data(),entity.view);if(copy!=null){copy.setPosition(copy.posX-entity.snapshot.origin.getX(),copy.posY-entity.snapshot.origin.getY(),copy.posZ-entity.snapshot.origin.getZ());copy.prevPosX=copy.lastTickPosX=copy.posX;copy.prevPosY=copy.lastTickPosY=copy.posY;copy.prevPosZ=copy.lastTickPosZ=copy.posZ;mesh.entities.add(copy);}}
        return mesh;
    }
    @Override public void doRender(EntityTurntable entity,double x,double y,double z,float yaw,float partial){
        if(entity.snapshot==null||entity.view==null)return;Minecraft mc=Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){GlStateManager.translate(x,y,z);TurntableGeometry.Face face=entity.snapshot.layout.basis;GlStateManager.rotate((float)-entity.angle(partial),face.x,face.y,face.z);
            GlStateManager.translate(entity.snapshot.origin.getX()-entity.posX,entity.snapshot.origin.getY()-entity.posY,entity.snapshot.origin.getZ()-entity.posZ);
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.color(1,1,1,1);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableCull();RenderHelper.disableStandardItemLighting();
            Mesh mesh=mesh(entity);GL11.glCallList(mesh.list);
            for(TileEntity tile:entity.view.tiles.values()){
                TileEntitySpecialRenderer<TileEntity> renderer=TileEntityRendererDispatcher.instance.getRenderer(tile);if(renderer==null)continue;BlockPos p=tile.getPos();GlStateManager.pushMatrix();
                try{renderer.render(tile,p.getX(),p.getY(),p.getZ(),partial,-1,1);}catch(RuntimeException failure){com.vandorlabs.vehicle.VehicleCompatibility.warn(tile.getBlockType(),"turntable renderer",failure);}finally{GlStateManager.popMatrix();}
            }
            for(Entity copy:mesh.entities)mc.getRenderManager().renderEntity(copy,copy.posX,copy.posY,copy.posZ,copy.rotationYaw,partial,false);
        }finally{GlStateManager.popMatrix();}
    }
}

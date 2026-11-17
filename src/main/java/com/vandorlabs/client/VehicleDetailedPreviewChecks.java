package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.tiles.TileEntityVehicleController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.io.File;
import java.awt.image.BufferedImage;
import java.util.*;
import org.lwjgl.opengl.GL11;

/** Actual material pixels and persistent preview transport, shared by both controller consumers. */
final class VehicleDetailedPreviewChecks {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("detailed preview: "+why);}
    static void run(Minecraft mc,File output)throws Exception{
        NBTTagCompound cargo=new NBTTagCompound();cargo.setString("PrivateInventory","must not reach preview");
        List<VehicleStructure.Cell> cells=Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),cargo),new VehicleStructure.Cell(BlockPos.ORIGIN.east(),Blocks.STONE_SLAB.getDefaultState(),null),new VehicleStructure.Cell(BlockPos.ORIGIN.up(),Blocks.GOLD_BLOCK.getDefaultState(),null));
        VehicleStructure structure=VehicleStructure.display(cells);NBTTagCompound saved=VehiclePreview.of(structure);
        VehicleStructure read=VehiclePreview.read(saved);need(read.cells.size()==3,"preview cell round trip");for(VehicleStructure.Cell c:read.cells)need(!c.hasTileData(),"no inventory/capability/private NBT");
        TileEntityVehicleController tile=new TileEntityVehicleController();tile.shape=VehicleShape.of(structure);tile.preview=saved;NBTTagCompound persisted=tile.writeToNBT(new NBTTagCompound());TileEntityVehicleController restored=new TileEntityVehicleController();restored.readFromNBT(persisted);need(Arrays.equals(saved.getByteArray("VehiclePreview"),restored.preview.getByteArray("VehiclePreview")),"controller save/reload");
        NBTTagCompound synced=tile.getUpdateTag();TileEntityVehicleController remote=new TileEntityVehicleController();remote.readFromNBT(synced);need(VehicleShapeRenderer.controller(remote)!=null,"client controller update material sample");
        need(VehicleShapeRenderer.controller(remote)==VehicleShapeRenderer.controller(remote),"status reuses immutable sample");
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(net.minecraft.block.Block chest:new net.minecraft.block.Block[]{Blocks.CHEST,Blocks.TRAPPED_CHEST}){
            VehicleStructure item=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,chest.getDefaultState().withProperty(net.minecraft.block.BlockChest.FACING,facing),null)));
            VehiclePreviewSampler.Sample sample=VehiclePreviewSampler.sample(item);need(sample!=null,"chest sample "+chest+" "+facing);int wood=0,magenta=0;for(int color:sample.colors)if(color!=0){int r=color>>16&255,g=color>>8&255,b=color&255;if(r>65&&r>g&&g>b)wood++;if(r>150&&b>150&&g<60)magenta++;}need(wood>20&&magenta==0,"entity chest artwork without missingno "+wood+"/"+magenta);
        }
        VehicleShape full=VehicleShapeRenderer.shape(structure),status=VehicleShapeRenderer.controller(remote);int before=VehicleShapeRenderer.meshBuilds;
        render(mc,full,new File(output,"vehicle-detailed-controller.png"));render(mc,status,new File(output,"vehicle-detailed-status.png"));int built=VehicleShapeRenderer.meshBuilds;need(built==before+2,"both consumers build detailed meshes");for(int i=0;i<30;i++)render(mc,status,null);need(VehicleShapeRenderer.meshBuilds==built,"animation never rebuilds geometry");
        NBTTagCompound invalid=saved.copy();invalid.setByteArray("VehiclePreview",new byte[VehiclePreview.MAX_BYTES+1]);try{VehiclePreview.read(invalid);need(false,"oversized transport rejected");}catch(java.io.IOException expected){need(true,"bounded transport");}
        System.out.println("[vandorlabs] vehicle-detailed-preview PASS checks="+checks+" controller/status material sampling chest/trapped four facings save/update/no-private-NBT mesh reuse bytes="+saved.getByteArray("VehiclePreview").length);
    }
    private static void render(Minecraft mc,VehicleShape shape,File output)throws Exception{
        Framebuffer frame=new Framebuffer(320,200,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,320,200,0,-100,100);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(.08F,.12F,.16F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.disableFog();VehicleShapeRenderer.draw(shape,160,100,290,170,35);
            if(output!=null){BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(320,200,frame);int gold=0,magenta=0;for(int y=0;y<200;y++)for(int x=0;x<320;x++){int c=image.getRGB(x,y),r=c>>16&255,g=c>>8&255,b=c&255;if(r>100&&g>80&&b<g*.6)gold++;if(r>170&&b>170&&g<60)magenta++;}need(gold>50&&magenta==0,"rendered material colors/gold no missingno");javax.imageio.ImageIO.write(image,"png",output);}
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
}

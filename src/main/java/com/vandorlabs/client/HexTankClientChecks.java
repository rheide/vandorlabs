package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/** Render the real native joining renderer, including partial construction stages. */
final class HexTankClientChecks {
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("joined tank renderer: "+text);}
    static void run(Minecraft mc,File output)throws Exception{
        for(int stage=1;stage<=4;stage++){
            int size=stage==1?1:stage==4?3:2;
            BufferedImage image=render(mc,size,EnumFacing.UP,stage==2);
            javax.imageio.ImageIO.write(image,"png",new File(output,"shot_hex_tank_step_"+stage+".png"));
        }
        for(EnumFacing face:EnumFacing.values())for(int size:new int[]{2,3})render(mc,size,face,false);
        System.out.println("[vandorlabs][reprolab] hex-tank-render PASS complete-2x2-3x3 six-axes single-root actual-shell-fluid-render partial-construction-four-images");
    }
    private static BufferedImage render(Minecraft mc,int size,EnumFacing face,boolean incomplete){
        Block block=Block.getBlockFromName("vandorlabs:hexagonal_fuel_tank");
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(int x=0;x<size;x++)for(int y=0;y<size;y++){
            if(incomplete&&x==size-1&&y==size-1)continue;
            BlockPos at=BlockPos.ORIGIN.offset(HexTankGroups.right(face.getAxis()),x).offset(HexTankGroups.up(face.getAxis()),y);
            TileEntityHexTank cell=new TileEntityHexTank();cell.setPos(at);cell.tank.setFluid(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,8000));
            cells.add(new VehicleStructure.Cell(at,block.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,face),cell.writeToNBT(new NBTTagCompound())));
        }
        VehicleWorld view=new VehicleWorld(mc.world,VehicleStructure.display(cells));int roots=0;
        for(VehicleStructure.Cell cell:cells){TileEntityHexTank tile=(TileEntityHexTank)view.getTileEntity(cell.pos);com.vandorlabs.render.ConnectedSquare.Part part=HexTankGroups.part(tile);require(part.size==(incomplete?1:size),"same geometry group on "+face);if(part.x==0&&part.y==0)roots++;}
        require(roots==(incomplete?cells.size():1),"each assembly has exactly one renderer root");
        Framebuffer frame=new Framebuffer(384,320,true);TEHexTank renderer=new TEHexTank();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);
        GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-size,size,-size*.85,size*.85,-20,20);
        GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();
            GlStateManager.rotate(25,1,0,0);GlStateManager.rotate(35,0,1,0);
            double sx=face.getAxis()==EnumFacing.Axis.X?1:size,sy=face.getAxis()==EnumFacing.Axis.Y?1:size,sz=face.getAxis()==EnumFacing.Axis.Z?1:size;
            GlStateManager.translate(-sx/2,-sy/2,-sz/2);
            for(VehicleStructure.Cell cell:cells)renderer.render((TileEntityHexTank)view.getTileEntity(cell.pos),cell.pos.getX(),cell.pos.getY(),cell.pos.getZ(),0,-1,1);
            org.lwjgl.opengl.GL11.glFinish();BufferedImage image=ScreenShotHelper.createScreenshot(384,320,frame);int ink=0;
            for(int x=0;x<384;x++)for(int y=0;y<320;y++)if((image.getRGB(x,y)&0xffffff)!=0x203040)ink++;
            require(ink>500,"joined shell visibly renders "+face+" "+size);return image;
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
}

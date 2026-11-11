package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockBattery;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityBattery;
import com.vandorlabs.vehicle.VehicleStructure;
import com.vandorlabs.vehicle.VehicleWorld;
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
import java.util.Collections;

/** Actual renderer coverage for shell reuse, material changes and the uncached fractional charge bars. */
final class BatteryCacheClientChecks {
    private static void need(boolean ok,String message){if(!ok)throw new IllegalStateException("battery shell cache: "+message);}
    static void run(Minecraft mc){
        BatteryGeometryCache cache=TEBattery.geometryCache();int cases=0;
        for(boolean block:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.values())for(boolean custom:new boolean[]{false,true}){
            cache.clear();BufferedImage cold=render(mc,block,facing,custom,45321);long builds=cache.builds();BufferedImage warm=render(mc,block,facing,custom,45321);
            need(difference(cold,warm)==0,"warm pixel parity "+block+"/"+facing+"/"+custom);need(cache.builds()==builds,"warm shell rebuilt");cases++;
        }
        cache.clear();BufferedImage low=render(mc,true,EnumFacing.UP,false,41000);long builds=cache.builds();BufferedImage high=render(mc,true,EnumFacing.UP,false,49000);
        need(difference(low,high)>0,"fractional charge within one segment did not change the actual indicator");need(cache.builds()==builds,"charge changes invalidated static shell geometry");
        BufferedImage empty=render(mc,true,EnumFacing.UP,false,0),full=render(mc,true,EnumFacing.UP,false,TileEntityBattery.CAPACITY);need(difference(empty,full)>0,"empty/full indicator transition");
        BufferedImage before=render(mc,false,EnumFacing.WEST,true,45321);builds=cache.builds();mc.refreshResources();need(cache.retainedBytes()==0,"real atlas reload retained static shell");
        BufferedImage after=render(mc,false,EnumFacing.WEST,true,45321);need(difference(before,after)==0,"resource reload changed battery appearance");need(cache.builds()>builds,"resource reload did not rebuild shell");
        System.out.println("[vandorlabs][reprolab] battery-cache-render PASS warm-pixel-parity="+cases+" both-shapes six-input-directions default/custom-materials fractional-charge-within-segment empty-full real-atlas-reload");
    }
    private static int difference(BufferedImage a,BufferedImage b){need(a.getWidth()==b.getWidth()&&a.getHeight()==b.getHeight(),"image dimensions");int changed=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))changed++;return changed;}
    private static BufferedImage render(Minecraft mc,boolean blockShape,EnumFacing facing,boolean custom,int energy){
        Block block=Block.getBlockFromName("vandorlabs:battery_block");TileEntityBattery source=new TileEntityBattery();source.setPos(BlockPos.ORIGIN);source.setBlockShape(blockShape);if(custom)source.setHousingTexture(ScreenHousingTextures.DEFAULT_STORAGE);
        NBTTagCompound data=source.writeToNBT(new NBTTagCompound());data.setInteger("Energy",energy);
        VehicleStructure.Cell cell=new VehicleStructure.Cell(BlockPos.ORIGIN,block.getDefaultState().withProperty(BlockBattery.FACING,facing),data);
        VehicleWorld view=new VehicleWorld(mc.world,VehicleStructure.display(Collections.singletonList(cell)));TileEntityBattery tile=(TileEntityBattery)view.getTileEntity(BlockPos.ORIGIN);
        Framebuffer frame=new Framebuffer(384,320,true);TEBattery renderer=new TEBattery();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);
        GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-1,1,-.85,.85,-20,20);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();
            GlStateManager.rotate(25,1,0,0);GlStateManager.rotate(35,0,1,0);GlStateManager.translate(-.5,-.5,-.5);renderer.render(tile,0,0,0,0,-1,1);
            org.lwjgl.opengl.GL11.glFinish();BufferedImage image=ScreenShotHelper.createScreenshot(384,320,frame);int ink=0;for(int y=0;y<320;y++)for(int x=0;x<384;x++)if((image.getRGB(x,y)&0xffffff)!=0x203040)ink++;need(ink>500,"battery visibly renders "+blockShape+"/"+facing);return image;
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
}

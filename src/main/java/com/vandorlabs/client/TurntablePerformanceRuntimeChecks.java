package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.*;
import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/** Paired rigid-wall rendering and isolated server motion timings, separate from transfer I/O. */
final class TurntablePerformanceRuntimeChecks {
    private static TurntableSnapshot snapshot(BlockPos root,int size,int rows){
        NBTTagCompound surface=new NBTTagCompound();surface.setInteger("Normal",EnumFacing.UP.getIndex());NBTTagList positions=new NBTTagList();
        for(int x=0;x<size;x++)for(int z=0;z<size;z++)positions.appendTag(new NBTTagLong(root.add(x,0,z).toLong()));surface.setTag("Cells",positions);
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(int x=0;x<size;x++)for(int y=0;y<rows;y++){TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setPos(new BlockPos(x,y,0));tile.setHousingTexture(14);cells.add(new VehicleStructure.Cell(tile.getPos(),ModBlocks.PROGRAMMABLE_WALL.getDefaultState(),tile.writeToNBT(new NBTTagCompound())));}
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Schema",1);tag.setTag("Surface",surface);tag.setLong("Origin",root.up().toLong());tag.setInteger("Height",rows);tag.setTag("Blocks",VehicleStructure.display(cells).write());return TurntableSnapshot.read(tag);
    }
    static void server(WorldServer world){
        if(!TurntablePerformance.ENABLED)return;
        BlockPos root=new BlockPos(512,200,512),at=root.west(24);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunkFromBlockCoords(root.add(x*16,0,z*16));
        world.setBlockState(at,net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_controller").getDefaultState(),2);
        TileEntityTurntableController owner=(TileEntityTurntableController)world.getTileEntity(at);EntityTurntable display=new EntityTurntable(world);TurntableSnapshot snapshot=snapshot(root,16,8);display.install(snapshot);display.controller=at;display.controllerKey=owner.key;owner.captureId=display.getUniqueID();owner.layout=snapshot.layout;owner.rotationMode=2;owner.rpm=2;
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)world.setBlockState(root.add(x,0,z),net.minecraft.init.Blocks.STONE.getDefaultState(),2);
        long[] samples=new long[128];
        try{for(int i=-32;i<samples.length;i++){double before=display.angle();long start=System.nanoTime();display.onUpdate();if(i>=0)samples[i]=System.nanoTime()-start;if(display.angle()==before)throw new IllegalStateException("Isolated turntable rotation blocked: "+owner.message);}Arrays.sort(samples);System.out.println("[vandorlabs][reprolab] turntable-server-profile blocks=128 floor-obstacles=256 median-us="+samples[64]/1000+" p95-us="+samples[120]/1000+" max-us="+samples[127]/1000);}
        finally{owner.captureId=null;world.setBlockToAir(at);for(int x=0;x<16;x++)for(int z=0;z<16;z++)world.setBlockToAir(root.add(x,0,z));display.setDead();}
    }
    static void render(Minecraft mc,File output)throws Exception{
        if(!TurntablePerformance.ENABLED||BlockSurfaceFormat.get().getNextOffset()!=32)return;
        EntityTurntable display=new EntityTurntable(mc.world);display.install(snapshot(BlockPos.ORIGIN,8,8));RenderTurntable renderer=new RenderTurntable(mc.getRenderManager());Framebuffer frame=new Framebuffer(320,240,true);
        long[][] cpu=new long[2][32];BufferedImage[] images=new BufferedImage[2];int oldPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-7,7,-5.25,5.25,-60,60);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{frame.bindFramebuffer(true);for(int mode=0;mode<2;mode++){
            RenderTurntable.cacheWalls=mode==1;
            for(int sample=-64;sample<32;sample++){
                display.setAngle((sample+64)*.5);GlStateManager.loadIdentity();GlStateManager.rotate(15,1,0,0);GlStateManager.rotate(20,0,1,0);GlStateManager.translate(-4,-5,-.5);GlStateManager.depthMask(true);GlStateManager.clearColor(.1F,.15F,.2F,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GlStateManager.enableDepth();GlStateManager.disableBlend();GL11.glFinish();RenderTurntable.beginFrame();long start=System.nanoTime();renderer.doRender(display,display.posX,display.posY,display.posZ,0,1);if(sample>=0)cpu[mode][sample]=System.nanoTime()-start;GL11.glFinish();
            }
            images[mode]=net.minecraft.util.ScreenShotHelper.createScreenshot(320,240,frame);javax.imageio.ImageIO.write(images[mode],"png",new File(output,"turntable-wall-cache-"+mode+".png"));
        }
            if(RenderTurntable.wallBuilds(display)!=64)throw new IllegalStateException("Turntable rigid walls rebuilt during rotation: "+RenderTurntable.wallBuilds(display));
            if(!Arrays.equals(images[0].getRGB(0,0,320,240,null,0,320),images[1].getRGB(0,0,320,240,null,0,320)))throw new IllegalStateException("Turntable wall cache changed rendered pixels");
            for(long[] samples:cpu)Arrays.sort(samples);System.out.println("[vandorlabs][reprolab] turntable-wall-cache PASS walls=64 identical-pixels builds=64 baseline-median-us="+cpu[0][16]/1000+" cached-median-us="+cpu[1][16]/1000+" baseline-p95-us="+cpu[0][30]/1000+" cached-p95-us="+cpu[1][30]/1000);
        }finally{RenderTurntable.cacheWalls=true;display.setDead();RenderTurntable.clear();net.minecraftforge.client.ForgeHooksClient.setRenderPass(oldPass);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);}
    }
}

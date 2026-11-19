package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.construction.SynthesisRuntimeChecks;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.*;
import java.io.File;
import java.awt.image.BufferedImage;
import org.lwjgl.opengl.GL11;

/** Exact assembled-world production followed by assertions on rendered meshes and real dialog. */
@SideOnly(Side.CLIENT)
final class SynthesisTowerLiveChecks {
    private static int stage,ticks;private static final BlockPos ROOT=new BlockPos(180,220,180);
    static boolean tick(Minecraft mc,File directory)throws Exception{
        if(stage==2){if(!SynthesisInteractionLiveChecks.tick(mc))return false;stage=3;}if(stage==3)return SynthesisDigitizeLiveChecks.tick(mc,directory);
        if(mc.getIntegratedServer()==null||mc.getIntegratedServer().getPlayerList().getPlayers().isEmpty())return false;
        if(stage==0){
            mc.getIntegratedServer().addScheduledTask(()->{
                net.minecraft.entity.player.EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                MatterInfuserRuntimeChecks.run(player.world);
                SynthesisRuntimeChecks.run(player.world,player);
                BlockConstructorCore core=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:synthesis_tower_core");
                Block matter=Block.getBlockFromName("vandorlabs:programmable_block");
                player.world.setBlockState(ROOT,core.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.NORTH));
                TileEntityConstructor tile=(TileEntityConstructor)player.world.getTileEntity(ROOT);
                for(int x=0;x<core.width;x++)for(int y=0;y<core.height;y++)for(int z=0;z<core.depth;z++)if(!ROOT.equals(tile.cell(x,y,z)))player.world.setBlockState(tile.cell(x,y,z),tile.frame(x,y,z)?matter.getDefaultState():net.minecraft.init.Blocks.AIR.getDefaultState());
                need(tile.assemble(),"capture frame assembles through actual player layout");
                ItemStack battery=new ItemStack(com.vandorlabs.items.ModItems.BATTERY_MODULE);com.vandorlabs.rack.ModuleBatteryData.receive(battery,10000,false);
                tile.synthesisInventory().setStackInSlot(2,battery);tile.synthesisInventory().setStackInSlot(0,new ItemStack(matter));
                for(int bay=1;bay<4;bay++){ItemStack module=new ItemStack(com.vandorlabs.items.ModItems.BATTERY_MODULE);com.vandorlabs.rack.ModuleBatteryData.receive(module,bay*2500,false);tile.synthesisInventory().setStackInSlot(2+bay,module);}
                for(int i=0;i<401;i++){player.world.setTotalWorldTime(player.world.getTotalWorldTime()+1);tile.update();}

                need(tile.processing()&&tile.buildProgress()>.45&&tile.buildProgress()<.60,"real RF produces partial core before capture");
                player.connection.setPlayerLocation(181.5,222,174,0,0);
                player.world.notifyBlockUpdate(ROOT,player.world.getBlockState(ROOT),player.world.getBlockState(ROOT),3);
            }).get();stage=1;return false;
        }
        if(++ticks>300)throw new IllegalStateException("Synthesis render capture client chunk/tile timeout stage="+stage+" pos="+mc.player.getPosition());
        if(!(mc.world.getTileEntity(ROOT) instanceof TileEntityConstructor))return false;
        TileEntityConstructor tile=(TileEntityConstructor)mc.world.getTileEntity(ROOT);
        if(!tile.renderReady()||!tile.processing()||!tile.working()||tile.buildProgress()<.45||ticks<25)return false;
        com.vandorlabs.client.constructors.SynthesisRenderChecks.run();
        CoreIconRenderChecks.capture(mc,directory);
        while(GL11.glGetError()!=GL11.GL_NO_ERROR){} // Attribute errors to the capture below, not preceding world frames.
        BufferedImage legacy=tower(mc,tile,20,155,2),effects=tower(mc,tile,20,155,1);
        javax.imageio.ImageIO.write(legacy,"png",new File(directory,"synthesis-tower-alpha-negative-control.png"));
        javax.imageio.ImageIO.write(effects,"png",new File(directory,"synthesis-tower-effects-only.png"));
        BufferedImage a=tower(mc,tile,20,155),b=tower(mc,tile,50,155);
        javax.imageio.ImageIO.write(a,"png",new File(directory,"synthesis-tower-processing.png"));
        javax.imageio.ImageIO.write(b,"png",new File(directory,"synthesis-tower-scanning.png"));
        javax.imageio.ImageIO.write(tower(mc,tile,20,-25),"png",new File(directory,"synthesis-tower-rear-ports.png"));
        net.minecraft.nbt.NBTTagCompound appearance=tile.getUpdateTag();
        try{net.minecraft.nbt.NBTTagCompound paused=appearance.copy();paused.setBoolean("SynthesisWorking",false);tile.handleUpdateTag(paused);BufferedImage pausedImage=tower(mc,tile,20,155);javax.imageio.ImageIO.write(pausedImage,"png",new File(directory,"synthesis-tower-paused.png"));need(redPixels(pausedImage)==0,"paused fabrication retains partial Core but emits no scanning lasers");}finally{tile.handleUpdateTag(appearance);}

        try{net.minecraft.nbt.NBTTagCompound idle=appearance.copy();idle.getCompoundTag("Synthesis").setBoolean("Active",false);tile.readFromNBT(idle);javax.imageio.ImageIO.write(tower(mc,tile,20,155),"png",new File(directory,"synthesis-tower-finished-front.png"));}finally{tile.handleUpdateTag(appearance);}
        int laserPixels=redPixels(a),isolated=redPixels(effects),negative=redPixels(legacy);
        System.out.println("[vandorlabs] synthesis-beam-pixels full="+laserPixels+" effectsOnly="+isolated+" alphaOffNegative="+negative);
        need(laserPixels>12&&isolated>12,"four scanning beams visible with and without chamber body count="+laserPixels+" isolated="+isolated);
        need(negative<laserPixels/4,"alpha-off transparent-depth negative control reproduces missing beams count="+negative);
        need(different(a,b,100,70,300,230)>10,"pause-aware laser/preview positions visibly advance");
        need(GL11.glGetError()==GL11.GL_NO_ERROR,"world tower renderer leaves no GL error");
        net.minecraft.client.gui.GuiScreen previous=mc.currentScreen;
        try{
            GuiSynthesisTower gui=new GuiSynthesisTower(mc.player.inventory,tile);mc.displayGuiScreen(gui);
            for(int design=0;design<3;design++){
                ((com.vandorlabs.container.ContainerSynthesisTower)gui.inventorySlots).values[0]=design;
                captureGui(mc,gui,directory,"synthesis-dialog-"+design+".png");
            }
            gui.actionPerformed(new net.minecraft.client.gui.GuiButton(101,0,0,"Redstone"));captureGui(mc,gui,directory,"synthesis-dialog-redstone.png");
        }finally{mc.displayGuiScreen(previous);}
        need(GL11.glGetError()==GL11.GL_NO_ERROR,"GUI previews leave no GL error");
        stage=2;System.out.println("[vandorlabs] synthesis-tower-render PASS powered production all designs save/reload container ownership rendered partial core four moving lasers native rack/chip and three textured GUI previews Redstone page");return false;
    }
    static BufferedImage tower(Minecraft mc,TileEntityConstructor tile,double now,float yaw){return tower(mc,tile,now,yaw,0);}
    private static BufferedImage tower(Minecraft mc,TileEntityConstructor tile,double now,float yaw,int mode){
        Framebuffer f=new Framebuffer(400,340,true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-3.7,3.7,-3.145,3.145,-20,20);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            f.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(.08F,.12F,.16F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableCull();
            GlStateManager.rotate(15,1,0,0);GlStateManager.rotate(yaw,0,1,0);GlStateManager.translate(-1.5,-2.5,-1.5);
            com.vandorlabs.client.constructors.TEConstructor renderer=(com.vandorlabs.client.constructors.TEConstructor)net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance.<TileEntityConstructor>getRenderer(tile);
            com.vandorlabs.client.constructors.MachineVisualMotion.at(now,()->{
                if(mode==0)renderer.render(tile,0,0,0,0,-1,1);
                else{
                    if(mode==2){
                        GlStateManager.disableAlpha();GlStateManager.enableBlend();GlStateManager.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
                        mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
                        BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.setTranslation(0,0,0);buffer.begin(GL11.GL_QUADS,net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);
                        renderer.renderTileEntityFast(tile,0,0,0,0,-1,1,buffer);Tessellator.getInstance().draw();
                    }
                    com.vandorlabs.client.constructors.SynthesisTowerRenderer.render(tile,0,0,0,0);
                }
            });GL11.glFinish();return ScreenShotHelper.createScreenshot(400,340,f);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);f.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    static void captureGui(Minecraft mc,GuiSynthesisTower gui,File directory,String name)throws Exception{
        mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();mc.entityRenderer.disableLightmap();gui.drawScreen(-1,-1,0);GL11.glFinish();
        BufferedImage shot=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());javax.imageio.ImageIO.write(shot,"png",new File(directory,name));
        need(gui.controlsFit(),"actual viewport fits all GUI controls and inventory slots");
        int scale=new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor(),left=gui.dialogLeft();
        if(!name.contains("redstone")){
            java.util.HashSet<Integer> colors=new java.util.HashSet<>();for(int y=(gui.previewTop()+2)*scale;y<(gui.previewBottom()-1)*scale;y++)for(int x=(left+178)*scale;x<(left+333)*scale;x++)if(x>=0&&y>=0&&x<shot.getWidth()&&y<shot.getHeight())colors.add(shot.getRGB(x,y));
            need(colors.size()>24,"textured preview produces varied nonempty pixels "+name+" colors="+colors.size());
        }
    }
    private static int redPixels(BufferedImage image){int count=0;for(int y=60;y<210;y++)for(int x=90;x<310;x++){int c=image.getRGB(x,y),r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;if(r>110&&r>g*1.5&&r>b*1.3)count++;}return count;}
    private static int different(BufferedImage a,BufferedImage b,int x0,int y0,int x1,int y1){int changed=0;for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)if(a.getRGB(x,y)!=b.getRGB(x,y))changed++;return changed;}
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Synthesis rendered: "+message);}
}

package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityMatterInfuser;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.rack.ModuleBatteryData;
import com.vandorlabs.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.init.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;
import java.io.File;
import java.awt.image.BufferedImage;

/** Small actual-player acceptance for the shared Source Infuser panel, independent of tower work. */
final class InfuserGuiLiveChecks {
    private static final BlockPos POS=new BlockPos(100,120,100);
    private static final Vec3d EYE=new Vec3d(102.5,122.2,97.5);
    private static int stage,ticks,capture;
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static TileEntityMatterInfuser tile(EntityPlayerMP player){return (TileEntityMatterInfuser)player.world.getTileEntity(POS);}
    static boolean tick(Minecraft mc,File out)throws Exception{
        if(mc.getIntegratedServer()==null||mc.getIntegratedServer().getPlayerList().getPlayers().isEmpty())return false;
        if(++ticks>300)throw new IllegalStateException("Source Infuser GUI timeout stage="+stage+" capture="+capture+" mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector()+" screen="+mc.currentScreen);
        if(stage==0){
            mc.playerController.setGameType(GameType.SURVIVAL);mc.player.inventory.currentItem=0;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=player(mc);p.interactionManager.setGameType(GameType.SURVIVAL);p.inventory.clear();p.inventory.currentItem=0;p.inventory.setInventorySlotContents(8,new ItemStack(ModItems.INFUSER_CORE));p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();
                p.world.setWorldTime(6000);p.world.getWorldInfo().setRaining(false);p.world.setBlockToAir(POS.up(3));p.world.setBlockState(POS,net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_matter_infuser").getDefaultState());
                p.connection.setPlayerLocation(EYE.x,EYE.y-p.getEyeHeight(),EYE.z,33.69F,25.23F);p.inventoryContainer.detectAndSendChanges();
            }).get();stage=1;ticks=0;return false;
        }
        if(stage==1){
            if(ticks<30||mc.currentScreen!=null||mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.inventory.currentItem!=0||!mc.player.getHeldItemMainhand().isEmpty()||mc.player.getPositionEyes(1).squareDistanceTo(EYE)>.025||!(mc.world.getTileEntity(POS) instanceof TileEntityMatterInfuser))return false;
            write(mc,out,"infuser-source-world.png");prepare(mc,0);stage=2;ticks=0;return false;
        }
        if(stage==2){
            if(ticks<12||!mc.player.getHeldItemMainhand().isEmpty()||mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.inventory.currentItem!=0)return false;
            boolean synced=mc.getIntegratedServer().callFromMainThread(()->{EntityPlayerMP p=player(mc);return p.interactionManager.getGameType()==GameType.SURVIVAL&&p.inventory.currentItem==0&&p.getHeldItemMainhand().isEmpty()&&p.getPositionEyes(1).squareDistanceTo(EYE)<.025;}).get();if(!synced)return false;
            need(mc.playerController.processRightClickBlock(mc.player,mc.world,POS,EnumFacing.NORTH,new Vec3d(100.5,120.5,100),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"ordinary Survival interaction accepted");stage=3;ticks=0;return false;
        }
        if(stage==3){
            if(!(mc.currentScreen instanceof GuiMatterInfuser)||ticks<10)return false;
            TileEntityMatterInfuser client=(TileEntityMatterInfuser)mc.world.getTileEntity(POS);
            if(capture==0&&(client.getField(2)!=73||client.hasSunlight()))return false;
            if(capture==1&&ModuleBatteryData.energy(client.getStackInSlot(0))!=4650)return false;
            if(capture==2&&!client.hasSunlight())return false;
            mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();GlStateManager.disableLighting();mc.currentScreen.drawScreen(-1,-1,0);
            BufferedImage image=write(mc,out,"infuser-source-"+new String[]{"iron-paused","battery","sunlight"}[capture]+".png");verify(mc,image,client);
            capture++;if(capture<3){prepare(mc,capture);ticks=0;return false;}
            mc.player.closeScreen();System.out.println("[vandorlabs] infuser-gui PASS actualSurvivalOpen sharedStandardHeader/logo/39slotFrames/workAndBatteryProgress/noFlames sunny+paused/sourcePNG");return true;
        }
        return false;
    }
    private static void prepare(Minecraft mc,int which)throws Exception{
        mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=player(mc);TileEntityMatterInfuser t=tile(p);p.world.setBlockState(POS.up(3),which==2?Blocks.AIR.getDefaultState():Blocks.STONE.getDefaultState());t.clear();if(which<2){ItemStack input=which==0?new ItemStack(Items.IRON_INGOT):new ItemStack(ModItems.BATTERY_MODULE);if(which==1)ModuleBatteryData.receive(input,4650,false);t.setInventorySlotContents(0,input);t.setField(0,100);t.setField(1,200);t.setField(2,73);}p.world.notifyBlockUpdate(POS,p.world.getBlockState(POS),p.world.getBlockState(POS),3);if(p.openContainer!=null)p.openContainer.detectAndSendChanges();}).get();
    }
    private static BufferedImage write(Minecraft mc,File out,String name)throws Exception{BufferedImage image=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());javax.imageio.ImageIO.write(image,"png",new File(out,name));return image;}
    private static void verify(Minecraft mc,BufferedImage image,TileEntityMatterInfuser client)throws Exception{
        int left=(mc.currentScreen.width-InfuserDialog.WIDTH)/2,top=(mc.currentScreen.height-InfuserDialog.HEIGHT)/2,scale=new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
        need(left>=0&&top>=0,"shared panel fits Source viewport");
        pixel(image,left,top,scale,4,4,InfuserDialog.STYLE.header);pixel(image,left,top,scale,4,50,InfuserDialog.STYLE.body);
        net.minecraft.client.gui.inventory.GuiContainer gui=(net.minecraft.client.gui.inventory.GuiContainer)mc.currentScreen;need(gui.inventorySlots.inventorySlots.size()==39,"actual39 physical slots");
        for(net.minecraft.inventory.Slot slot:gui.inventorySlots.inventorySlots)pixel(image,left,top,scale,slot.xPos-1,slot.yPos-1,InfuserDialog.FRAME);
        ItemStack battery=ModuleBatteryData.valid(client.getStackInSlot(0))?client.getStackInSlot(0):client.getStackInSlot(2);boolean charging=ModuleBatteryData.valid(battery);int fill=InfuserDialog.fill(client.getField(2),charging,ModuleBatteryData.energy(battery),ModuleBatteryData.CAPACITY);
        for(int x=0;x<InfuserDialog.PROGRESS_WIDTH;x+=3)pixel(image,left,top,scale,InfuserDialog.PROGRESS_X+x,InfuserDialog.PROGRESS_Y+5,x<fill?InfuserDialog.PROGRESS:InfuserDialog.SLOT);
        for(int y=48;y<52;y++)for(int x=56;x<70;x+=2)pixel(image,left,top,scale,x,y,InfuserDialog.STYLE.body);
        int h=DialogStyle.logoHeight(28),w=DialogStyle.logoWidth(28),logoX=DialogStyle.logoLeft(left,InfuserDialog.WIDTH,28),logoY=DialogStyle.logoTop(top,28),opaque=0;
        BufferedImage logo;try(java.io.InputStream input=mc.getResourceManager().getResource(new ResourceLocation(DialogStyle.LOGO)).getInputStream()){logo=javax.imageio.ImageIO.read(input);}
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double u=(x+.5)*logo.getWidth()/w,v=(y+.5)*logo.getHeight()/h;
            // A half-sized texel boundary can round either way in legacy float rasterization.
            // Compare exact opaque source texels strictly inside their sampling interval.
            if(Math.abs(u-Math.rint(u))<.001||Math.abs(v-Math.rint(v))<.001)continue;
            int expected=logo.getRGB(Math.min(logo.getWidth()-1,(int)u),Math.min(logo.getHeight()-1,(int)v));if((expected>>>24)!=255)continue;
            int actual=image.getRGB((int)((logoX+x+.5)*scale),(int)((logoY+y+.5)*scale));need((actual&0xffffff)==(expected&0xffffff),"Source logo authored opaque texel "+x+","+y);opaque++;
        }
        need(opaque>12,"Source logo has opaque authored samples");
    }
    private static void pixel(BufferedImage image,int left,int top,int scale,int x,int y,int expected){int actual=image.getRGB((int)((left+x+.5)*scale),(int)((top+y+.5)*scale));need((actual&0xffffff)==(expected&0xffffff),"Source shared pixel "+x+","+y+" actual="+Integer.toHexString(actual)+" expected="+Integer.toHexString(expected));}
    private static void need(boolean value,String why){if(!value)throw new IllegalStateException("Infuser GUI: "+why);}
}

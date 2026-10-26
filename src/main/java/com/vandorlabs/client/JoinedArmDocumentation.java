package com.vandorlabs.client;

import com.vandorlabs.gear.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.File;

/** Stateful construction captures, separate from movement acceptance tests. */
public final class JoinedArmDocumentation {
    private static final BlockPos ROOT=new BlockPos(32,80,32);
    private static int stage=-1,settle;
    private static String pending;
    private static boolean captured,done;
    private static File directory;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("joined-arm documentation: "+why);}
    public static boolean tick(Minecraft mc,File output)throws Exception{
        if(stage<0){mc.gameSettings.renderDistanceChunks=3;mc.gameSettings.clouds=0;mc.gameSettings.enableVsync=false;mc.gameSettings.limitFramerate=60;mc.gameSettings.fancyGraphics=false;}
        directory=output;if(stage>=6)return JoinedArmAnimationDocumentation.tick(mc,output);if(done)return true;
        if(stage<0||captured){stage++;captured=false;settle=0;
            if(stage==6)return JoinedArmAnimationDocumentation.tick(mc,output);
            mc.getIntegratedServer().callFromMainThread(()->{
                WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");world.setWorldTime(6000);world.getWorldInfo().setRaining(false);
                if(stage==0||stage==5){for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-8,-1,-8),ROOT.add(12,8,12)))world.setBlockState(at,at.getY()==79?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);}
                if(stage==0){for(int x:new int[]{0,4})for(int z:new int[]{0,4}){player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.GEAR_EXTENDER,2));for(int n=0;n<2;n++)require(player.getHeldItemMainhand().onItemUse(player,world,ROOT.add(x,n-1,z),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.SUCCESS,"ordinary supported stack");}}
                if(stage==1)for(int x=0;x<=4;x++)for(int z=0;z<=4;z++)if(x==0||x==4||z==0||z==4)world.setBlockState(ROOT.add(x,2,z),Blocks.CONCRETE.getDefaultState().withProperty(net.minecraft.block.BlockColored.COLOR,net.minecraft.item.EnumDyeColor.LIGHT_BLUE),2);
                if(stage==2){for(int x:new int[]{0,4})for(int z:new int[]{0,4}){GearTileEntity tile=(GearTileEntity)world.getTileEntity(ROOT.add(x,0,z));tile.rebuild();NBTTagCompound settings=tile.settings();settings.setBoolean("JoinArms",true);settings.setInteger("JoinRange",4);settings.setBoolean("CarryLoad",true);settings.setInteger("LoadHeight",2);tile.configure(settings);}world.setBlockState(ROOT.add(2,3,0),Blocks.CHEST.getDefaultState(),2);}
                if(stage==3)((GearTileEntity)world.getTileEntity(ROOT)).loadedController().target(false);
                if(stage==4)((GearTileEntity)world.getTileEntity(ROOT)).loadedController().target(true);
                if(stage==5){player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.GEAR_SWING_ARM,3));for(int n=0;n<3;n++)require(player.getHeldItemMainhand().onItemUse(player,world,ROOT.up(n-1),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.SUCCESS,"rotating stack");GearTileEntity tile=(GearTileEntity)world.getTileEntity(ROOT);tile.rebuild();NBTTagCompound settings=tile.settings();settings.setInteger("Direction",0);settings.setBoolean("RotatePayload",false);tile.configure(settings);world.setBlockState(ROOT.up(3),Blocks.CONCRETE.getDefaultState(),2);tile.target(false);}
                Vec3d target=new Vec3d(ROOT).addVector(stage==5?.5:2.5,1.8,stage==5?-1:2.5),eye=target.addVector(-6,4,-8);if(stage==4)eye=new Vec3d(ROOT).addVector(1.5,3,-3);
                Vec3d delta=target.subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
                player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();player.connection.setPlayerLocation(eye.x,eye.y-player.getEyeHeight(),eye.z,yaw,pitch);player.setHeldItem(EnumHand.MAIN_HAND,stage==4?new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER):ItemStack.EMPTY);player.motionX=player.motionY=player.motionZ=0;return null;
            }).get();mc.displayGuiScreen(null);mc.gameSettings.hideGUI=stage!=4;mc.gameSettings.thirdPersonView=0;mc.gameSettings.showDebugInfo=false;return false;
        }
        require(++settle<400,"bounded rendered state");if(settle<40)return false;
        GearTileEntity tile=(GearTileEntity)mc.world.getTileEntity(ROOT);if(tile==null||tile.loadedController()==null)return false;tile=tile.loadedController();
        if(stage==3||stage==5){if(tile.progress()!=0||tile.payload()==null)return false;}
        if(stage==2)require(tile.columns().size()==4,"documented four-corner group");
        if(stage==4){if(!tile.fullyDeployed())return false;if(!(mc.currentScreen instanceof GuiModularGear)){mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(ROOT,EnumFacing.UP,EnumHand.MAIN_HAND,.5F,.5F,.5F));return false;}}
        if(pending==null)pending="shot_joined_arm_"+(stage+1)+".png";return false;
    }
    public static void render(Minecraft mc){if(stage>=6){JoinedArmAnimationDocumentation.render(mc);return;}if(pending==null)return;try{javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(directory,pending));System.out.println("[vandorlabs] joined-arm-doc-stage "+(stage+1)+" captured");pending=null;captured=true;}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
    private JoinedArmDocumentation(){}
}

package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockIndustrialLever;
import com.vandorlabs.tiles.TileEntityTurntableController;
import com.vandorlabs.turntable.EntityTurntable;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.io.File;

/** Opt-in diagnosis in a disposable copy of an existing save. */
final class ExistingTurntableRuntimeChecks {
    private static int stage;
    private static long started;
    private static double angle;
    private static BlockPos input;
    private static BlockPos position(){String[] values=System.getProperty("vandorlabs.turntableTestPosition","").split(",");if(values.length!=3)throw new IllegalArgumentException("Set turntableTestPosition to x,y,z");return new BlockPos(Integer.parseInt(values[0]),Integer.parseInt(values[1]),Integer.parseInt(values[2]));}
    static boolean tick(Minecraft mc,int ticks,File directory)throws Exception{
        if(stage==3)return true;if(ticks%10!=1)return false;
        final int step=stage;
        boolean ready=mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);BlockPos at=position();world.getChunkFromBlockCoords(at);
            if(!(world.getTileEntity(at) instanceof TileEntityTurntableController))throw new IllegalStateException("No turntable controller at test position");
            TileEntityTurntableController controller=(TileEntityTurntableController)world.getTileEntity(at);EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());
            if(step==0){
                player.setSneaking(false);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.connection.setPlayerLocation(at.getX()-5,at.getY()+5,at.getZ()-6,-45,30);
                for(EnumFacing side:EnumFacing.values())if(world.getBlockState(at.offset(side)).getBlock() instanceof BlockIndustrialLever){input=at.offset(side);break;}
                if(input==null)throw new IllegalStateException("Place a Power Lever beside the controller for this check");
                BlockIndustrialLever lever=(BlockIndustrialLever)world.getBlockState(input).getBlock();if(world.getBlockState(input).getValue(BlockIndustrialLever.POWERED))lever.onBlockActivated(world,input,world.getBlockState(input),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
                if(controller.display()!=null)TurntableTransactionsChecks.restore(controller,player);
                controller.rotationMode=1;controller.rotationChannels=com.vandorlabs.redstone.ChannelList.EMPTY;TurntableTransactionsChecks.scan(controller,player);
                lever.onBlockActivated(world,input,world.getBlockState(input),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
                started=world.getTotalWorldTime();return true;
            }
            if(step==1){
                if(world.getTotalWorldTime()-started<(com.vandorlabs.turntable.TurntablePerformance.ENABLED?800:360))return false;
                EntityTurntable display=controller.display();angle=display==null?0:display.angle();
                System.out.println("[vandorlabs][reprolab] existing-turntable status="+controller.message+" angle="+angle+" powered="+controller.running());
                if(display==null||!controller.running()||angle<10||!controller.message.startsWith("Rotating"))throw new IllegalStateException("Existing turntable stopped: "+controller.message+" angle="+angle);
                BlockIndustrialLever lever=(BlockIndustrialLever)world.getBlockState(input).getBlock();lever.onBlockActivated(world,input,world.getBlockState(input),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);started=world.getTotalWorldTime();return true;
            }
            if(world.getTotalWorldTime()-started<(com.vandorlabs.turntable.TurntablePerformance.ENABLED?240:40))return false;
            EntityTurntable paused=controller.display();
            if(paused==null||controller.running()||Math.abs(((paused.angle()-angle+540)%360)-180)>.6)throw new IllegalStateException("Existing turntable did not retain its paused heading: "+controller.message);
            TurntableTransactionsChecks.restore(controller,player);if(controller.captureId!=null)throw new IllegalStateException("Existing turntable return retained: "+controller.message);
            return true;
        }).get();
        if(ready){stage++;if(stage==3){javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(directory,"shot_existing_turntable.png"));System.out.println("[vandorlabs][reprolab] existing-turntable PASS sustained Power Lever rotation angle="+angle+" and explicit Restore");return true;}}
        return false;
    }
}

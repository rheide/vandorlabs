package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityTurntableController;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Real client forward input while server-driven platform travel packets are arriving. */
final class TurntableMovementRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(340,31,130),OWNER=ROOT.west();
    private static int ticks;
    private static boolean done;
    private static double x,z,clientDistance,serverDistance;
    private static long stoppedAt;
    static boolean done(){return done;}
    static boolean step(Minecraft mc)throws Exception{
        if(done)return true;
        if(ticks==0){
            x=ROOT.getX()+2.5;z=ROOT.getZ()+2.5;
            mc.getIntegratedServer().addScheduledTask(()->{
                try{
                    WorldServer world=mc.getIntegratedServer().getWorld(0);
                    EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());
                    BlockTurntableSurface surface=(BlockTurntableSurface)Block.getBlockFromName("vandorlabs:turntable_surface");
                    for(BlockPos at:BlockPos.getAllInBox(ROOT.up(),ROOT.add(4,3,4)))world.setBlockToAir(at);
                    for(int u=0;u<5;u++)for(int v=0;v<5;v++)world.setBlockState(ROOT.add(u,0,v),surface.getDefaultState());
                    world.setBlockState(OWNER,Block.getBlockFromName("vandorlabs:turntable_controller").getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.EAST));
                    TileEntityTurntableController tile=(TileEntityTurntableController)world.getTileEntity(OWNER);tile.rotationMode=2;tile.rpm=2;tile.height=1;tile.scan(player);
                    player.capabilities.isFlying=false;player.sendPlayerAbilities();player.connection.setPlayerLocation(x,32,z,0,0);
                }catch(Exception failure){throw new IllegalStateException(failure);}
            }).get();
            mc.player.capabilities.isFlying=false;mc.player.setPositionAndRotation(x,32,z,0,0);mc.player.motionX=mc.player.motionY=mc.player.motionZ=0;mc.player.onGround=true;
        }
        // Allow the one setup teleport and new surface chunks to arrive before holding W.
        if(ticks==20)KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),true);
        if(++ticks<33)return false;
        if(ticks==33){
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);
            clientDistance=Math.hypot(mc.player.posX-x,mc.player.posZ-z);
            serverDistance=mc.getIntegratedServer().callFromMainThread(()->{
                EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());
                TileEntityTurntableController tile=(TileEntityTurntableController)mc.getIntegratedServer().getWorld(0).getTileEntity(OWNER);
                if(tile.display()==null||tile.display().angle()<1)throw new IllegalStateException("Movement fixture did not rotate");
                stoppedAt=p.world.getTotalWorldTime();tile.rotationMode=1;tile.changed();
                return Math.hypot(p.posX-x,p.posZ-z);
            }).get();
            return false;
        }
        if(ticks<43)return false;
        mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);
            TileEntityTurntableController tile=(TileEntityTurntableController)world.getTileEntity(OWNER);
            if(tile.captureId!=null||world.getTotalWorldTime()<=stoppedAt+2)throw new IllegalStateException("End-of-tick auto restore did not complete and continue ticking");
            tile.release();world.setBlockToAir(OWNER);
            for(int u=0;u<5;u++)for(int v=0;v<5;v++)world.setBlockToAir(ROOT.add(u,0,v));
        }).get();
        if(clientDistance<.6||serverDistance<.4||Math.abs(clientDistance-serverDistance)>1.5)throw new IllegalStateException("Turntable forward input lost: client="+clientDistance+" server="+serverDistance);
        done=true;
        System.out.println("[vandorlabs][reprolab] turntable-walking PASS actual forward key while rotating client="+clientDistance+" server="+serverDistance);
        return true;
    }
}

package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import java.io.File;

/** A header-only camera must not wait forever for off-screen lower chunk sections. */
final class SegmentedTallChecks {
    private static final BlockPos BOTTOM=new BlockPos(608,94,608),TOP=BOTTOM.up(31);
    private static int stage,ticks,wait;
    static boolean tick(Minecraft mc,File output)throws Exception{
        SegmentedDoorTile seen=SegmentedDoorTile.at(mc.world,TOP);
        if(++ticks>360)throw new IllegalStateException("segmented-door-tall stage="+stage+" root="+(seen==null?"missing":seen.height+" awaiting="+seen.clientAwaitingChunk+" baked="+seen.clientBaked+" suppressed="+seen.clientSuppressed));
        if(stage==0){mc.player.closeScreen();mc.playerController.setGameType(net.minecraft.world.GameType.CREATIVE);mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());p.setGameType(net.minecraft.world.GameType.CREATIVE);p.capabilities.isFlying=true;p.sendPlayerAbilities();
            net.minecraft.world.WorldServer world=p.getServerWorld();net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:segmented_door");world.setBlockState(BOTTOM.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);for(int row=0;row<32;row++)world.setBlockState(BOTTOM.up(row),block.getDefaultState(),3);
            SegmentedDoorTile root=SegmentedDoorTile.at(world,TOP);root.refreshColumn();root.configure(p,com.vandorlabs.redstone.ChannelList.EMPTY,5,false,true);p.connection.setPlayerLocation(TOP.getX()+.5,TOP.getY()-1,TOP.getZ()-3,0,0);
        }).get();stage=1;ticks=0;return false;}
        mc.player.rotationYaw=mc.player.prevRotationYaw=0;mc.player.rotationPitch=mc.player.prevRotationPitch=0;
        if(seen==null||!seen.ready()||seen.height!=32)return false;
        if(stage==1){if(!seen.clientSuppressed)return false;mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());SegmentedDoorTile.at(p.getServerWorld(),TOP).operate(p,true);}).get();stage=2;ticks=0;return false;}
        if(stage==2){if(seen.clientAwaitingChunk||com.vandorlabs.client.segmented.SegmentedDoorVisuals.pose(seen,0)<=.01)return false;if(++wait<20)return false;mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());SegmentedDoorTile.at(p.getServerWorld(),TOP).operate(p,false);}).get();stage=3;ticks=0;return false;}
        if(stage==3&&seen.clientSuppressed){for(int row=0;row<32;row++)if(!SegmentedDoorTile.at(mc.world,TOP.down(row)).clientBaked)throw new IllegalStateException("Tall door lost closed terrain section");System.out.println("[vandorlabs][reprolab] segmented-door-tall PASS height=32 chunk-sections=3 header-only-camera offscreen-upload opening reversal closed-terrain");return true;}return false;
    }
    private SegmentedTallChecks(){}
}

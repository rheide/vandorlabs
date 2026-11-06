package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.persistence.DisplayActivationData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.screen.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Vanilla block-use dispatch, current player view angles, and real channel outputs for all seven roles. */
final class NativeScreenInteractionChecks {
    private static int checks,profiles;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Screen player interaction: "+why);}
    private static Vec3 placed(Vec3 p,int face){double x=p.x/16-.5,y=p.y/16-.5,z=p.z/16-.5;return switch(face){case 5->new Vec3(.5-z,.5+y,.5+x);case 3->new Vec3(.5-x,.5+y,.5-z);case 4->new Vec3(.5+z,.5+y,.5-x);case 1->new Vec3(.5-x,.5-z,.5-y);case 0->new Vec3(.5+x,.5+z,.5-y);default->new Vec3(.5+x,.5+y,.5+z);};}
    private static BlockHitResult aim(ServerPlayer player,NativeScreenEntity tile,int slot,int row,double column){
        var settings=tile.screenSettings();var data=settings.scalar();var block=(NativeScreenBlock)tile.getBlockState().getBlock();var state=block.screenState(tile.getBlockState());var projection=ScreenControlLayout.of(tile.family(),state,slot,data.smallInput,data.wallPosition,settings.ceilingMounted(),settings.ceilingPosition(),false);var q=projection.surface;
        double f=(projection.rowTop+row*ScreenControlLayout.ROW_HEIGHT+4D)/projection.height;
        Vec3 local=new Vec3(q.topRight.x-(q.topRight.x-q.topLeft.x)*column/128,q.topRight.y+(q.bottomRight.y-q.topRight.y)*f,q.topRight.z+(q.bottomRight.z-q.topRight.z)*f);
        Vec3 target=placed(local,state.facing).add(Vec3.atLowerCornerOf(tile.getBlockPos()));Vec3 normal=placed(local.add(0,q.ny*16,q.nz*16),state.facing).add(Vec3.atLowerCornerOf(tile.getBlockPos())).subtract(target).normalize();Vec3 eye=target.add(normal.scale(1.5));player.setPos(eye.x,eye.y-player.getEyeHeight(),eye.z);Vec3 direction=target.subtract(eye).normalize();player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));player.setXRot((float)-Math.toDegrees(Math.asin(direction.y)));player.setYHeadRot(player.getYRot());return new BlockHitResult(target,Direction.getNearest((float)normal.x,(float)normal.y,(float)normal.z),tile.getBlockPos(),false);
    }
    private static boolean use(ServerPlayer player,ServerLevel level,BlockHitResult hit){return player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,hit).consumesAction();}
    static void run(ServerLevel level,java.util.Map<ScreenFamily,NativeScreenBlock> blocks){
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("f1c98272-655a-4fef-902c-982106ce12d9"),"ScreenClicks"));var pos=new BlockPos(5000,100,5000);level.getChunkAt(pos);
        for(ScreenFamily family:ScreenFamily.values())for(var state:blocks.get(family).getStateDefinition().getPossibleStates())for(int slot=0;slot<2;slot++)if(family.supportsSlot(slot)){
            player.setGameMode(GameType.SURVIVAL);player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);level.setBlock(pos,state,2);var tile=(NativeScreenEntity)level.getBlockEntity(pos);int channel=82000+profiles*4;
            var header=new NativeScreenControls(true,"Player controls",java.util.List.of(
                new NativeScreenControls.Row("Toggle",ChannelList.of(channel),false,false,5,15,0,java.util.Map.of(),ChannelList.EMPTY,false,0),
                new NativeScreenControls.Row("Slider",ChannelList.of(channel+1),true,false,5,15,0,java.util.Map.of(),ChannelList.EMPTY,false,0),
                new NativeScreenControls.Row("Pulse",ChannelList.of(channel+2),false,true,5,15,0,java.util.Map.of(),ChannelList.EMPTY,false,0),
                new NativeScreenControls.Row("Empty",ChannelList.EMPTY,false,false,5,15,0,java.util.Map.of(),ChannelList.EMPTY,false,0)));
            var tag=tile.saveWithoutMetadata(level.registryAccess());tag.put(slot==0?"RedstonePrimary":"RedstoneSecondary",header.configuration());new NativeScreenActivation(DisplayActivationData.LEGACY.withMode(0)).write(tag);tile.loadWithComponents(tag,level.registryAccess());tile.onLoad();NativeRedstoneChannels.flush(level);need(!tile.inert()&&tile.visual().appearance().surface(slot)==-2,"visible row owner "+family);
            var hit=aim(player,tile,slot,0,110);need(use(player,level,hit)&&NativeRedstoneChannels.level(level,pos,channel)==15,"vanilla use toggles on "+family+"/"+slot);need(use(player,level,hit)&&NativeRedstoneChannels.level(level,pos,channel)==0,"vanilla use toggles off");
            for(int step=0;step<4;step++){hit=aim(player,tile,slot,1,60+(step+.5)*15);need(use(player,level,hit)&&NativeRedstoneChannels.level(level,pos,channel+1)==step*5,"four actual slider steps "+family+"/"+step);}
            hit=aim(player,tile,slot,1,55);need(!NativeScreenInteractions.activate(level,pos,player,InteractionHand.MAIN_HAND)&&NativeRedstoneChannels.level(level,pos,channel+1)==15,"slider label does not change level");
            hit=aim(player,tile,slot,2,110);need(use(player,level,hit)&&NativeRedstoneChannels.level(level,pos,channel+2)==15&&tile.controls(slot).rows().get(2).pulseUntil()==level.getGameTime()+20,"actual pulse deadline");
            hit=aim(player,tile,slot,3,110);need(use(player,level,hit),"empty-channel row consumes click");
            hit=aim(player,tile,slot,0,110);need(!NativeScreenInteractions.activate(level,pos,player,InteractionHand.OFF_HAND),"offhand ignored");player.setShiftKeyDown(true);need(!NativeScreenInteractions.activate(level,pos,player,InteractionHand.MAIN_HAND),"sneak ignored by rows");player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));need(!NativeScreenInteractions.activate(level,pos,player,InteractionHand.MAIN_HAND)&&NativeRedstoneChannels.level(level,pos,channel)==0,"tool routes away from row");need(use(player,level,hit),"tool opens shared configuration path");player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setGameMode(GameType.CREATIVE);player.setShiftKeyDown(true);need(use(player,level,hit)&&NativeRedstoneChannels.level(level,pos,channel)==0,"ordinary configuration path avoids row");player.setGameMode(GameType.SURVIVAL);player.setShiftKeyDown(false);
            java.util.concurrent.atomic.AtomicReference<Throwable> denied=new java.util.concurrent.atomic.AtomicReference<>();Thread worker=new Thread(()->{try{NativeScreenInteractions.activate(level,pos,player,InteractionHand.MAIN_HAND);}catch(Throwable failure){denied.set(failure);}});worker.start();try{worker.join();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}need(denied.get() instanceof IllegalStateException&&NativeRedstoneChannels.level(level,pos,channel)==0,"worker refused before world access");
            player.setPos(pos.getX()+20,pos.getY(),pos.getZ());need(!NativeScreenInteractions.activate(level,pos,player,InteractionHand.MAIN_HAND),"distant owner refused");level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);need(!NativeScreenInteractions.activate(level,pos,player,InteractionHand.MAIN_HAND),"removed owner refused");profiles++;
        }
        System.out.println("[vandorlabs][screen-interactions] dedicated PASS profiles="+profiles+" checks="+checks+" vanilla use/current angles/toggle/slider/pulse/empty row/configuration/authority");
    }
}

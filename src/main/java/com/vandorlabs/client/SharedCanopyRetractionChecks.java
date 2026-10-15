package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.canopy.LargeCanopySettings;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Real world ticks enforce ordering throughout shared mechanism retraction, not just endpoints. */
final class SharedCanopyRetractionChecks {
    private static final BlockPos CANOPY=new BlockPos(1410,111,270),RAMP=CANOPY.add(2,-1,-2),LEVER=CANOPY.west(5).down();
    private static final int CHANNEL=1883;private static int stage,ticks;private static long since;private static Monitor monitor;
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("shared canopy retraction: "+text);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static TileEntityCanopy canopy(WorldServer world){return TileEntityCanopy.at(world,CANOPY);}
    private static TileEntityRampController ramp(WorldServer world){return (TileEntityRampController)world.getTileEntity(RAMP);}
    public static final class Monitor {
        final WorldServer world;boolean armed;int movingTicks;String failure;
        Monitor(WorldServer world){this.world=world;}
        @SubscribeEvent public void tick(TickEvent.WorldTickEvent event){if(!armed||event.world!=world||event.phase!=TickEvent.Phase.END)return;TileEntityCanopy canopy=canopy(world);TileEntityRampController ramp=ramp(world);if(canopy==null||ramp==null){failure="mechanism disappeared";return;}if(ramp.isMoving())movingTicks++;if((ramp.isMoving()||ramp.isOpen())&&canopy.progress!=1)failure="canopy started closing before ramp endpoint: canopy="+canopy.progress+" rampMoving="+ramp.isMoving()+" rampOpen="+ramp.isOpen()+" status="+ramp.status;}
    }
    private static void setup(Minecraft mc){WorldServer world=world(mc);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());actor.dismountRidingEntity();actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();
        for(BlockPos at:BlockPos.getAllInBox(CANOPY.add(-7,-4,-7),CANOPY.add(9,9,8))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:large_front_canopy_glass");LargeCanopySettings settings=new LargeCanopySettings();settings.depth=24;settings.scale=50;settings.hinge=2;settings.openable=true;settings.redstone=true;settings.channels=ChannelList.of(CHANNEL);ItemStack item=new ItemStack(block);NBTTagCompound data=new NBTTagCompound();settings.write(data);item.setTagCompound(data);world.setBlockState(CANOPY,block.getDefaultState().withProperty(BlockCanopy.FACING,EnumFacing.NORTH),3);block.onBlockPlacedBy(world,CANOPY,world.getBlockState(CANOPY),null,item);
        world.setBlockState(RAMP,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),3);for(int y=0;y<2;y++)world.setBlockState(RAMP.south().up(y),Blocks.IRON_BLOCK.getDefaultState(),3);actor.setPositionAndUpdate(RAMP.getX()+.5,RAMP.getY()+2,RAMP.getZ()-2);require(ramp(world).configureHalfOffsets(actor,0,0,8,true,false,false,EnumFacing.SOUTH,0,false,1,true,false,0,0,true),"configure exact shared tower: "+ramp(world).status);ramp(world).setRedstoneChannels(ChannelList.of(CHANNEL));
        BlockIndustrialLever lever=(BlockIndustrialLever)Block.getBlockFromName("vandorlabs:industrial_power_lever");world.setBlockState(LEVER.down(),Blocks.STONE.getDefaultState(),3);world.setBlockState(LEVER,lever.getDefaultState().withProperty(BlockIndustrialLever.FLOOR,true),3);((TileEntityRedstoneChannel)world.getTileEntity(LEVER)).setRedstoneChannels(ChannelList.of(CHANNEL));((TileEntityRedstoneChannel)world.getTileEntity(LEVER)).setLocalOn(true);actor.setPositionAndUpdate(CANOPY.getX()-4,CANOPY.getY()+5,CANOPY.getZ()-4);monitor=new Monitor(world);net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(monitor);
    }
    static boolean tick(Minecraft mc)throws Exception{if(stage==5)return true;require(++ticks<1200,"timeout stage="+stage);if(monitor!=null)require(monitor.failure==null,monitor.failure);
        if(stage==0){mc.getIntegratedServer().addScheduledTask(()->setup(mc)).get();stage=1;return false;}
        boolean[] ready={false};mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);TileEntityCanopy canopy=canopy(world);TileEntityRampController ramp=ramp(world);
            if(stage==1)ready[0]=canopy!=null&&canopy.progress==1&&ramp.isOpen()&&!ramp.isMoving();else if(stage==2)ready[0]=world.getTotalWorldTime()-since>=30;else if(stage==3)ready[0]=canopy.progress==0&&!canopy.waitingForReservations&&!ramp.attached()&&!ramp.isMoving();else if(stage==4)ready[0]=world.getTotalWorldTime()-since>=30;
        }).get();if(!ready[0])return false;
        if(stage==1){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);monitor.armed=true;canopy(world).toggle();since=world.getTotalWorldTime();}).get();stage=2;}
        else if(stage==2){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);TileEntityCanopy canopy=canopy(world);require(canopy.progress==1&&!canopy.targetOpen&&canopy.waitingForReservations,"manual close while shared HIGH stays fully open waiting");canopy.readFromNBT(canopy.writeToNBT(new NBTTagCompound()));canopy.onLoad();((TileEntityRedstoneChannel)world.getTileEntity(LEVER)).setLocalOn(false);}).get();stage=3;}
        else if(stage==3){mc.getIntegratedServer().addScheduledTask(()->{require(monitor.movingTicks>0,"monitor observed real retraction ticks");require(monitor.failure==null,monitor.failure);since=world(mc).getTotalWorldTime();}).get();stage=4;}
        else if(stage==4){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);require(canopy(world).progress==0&&!canopy(world).targetOpen&&!ramp(world).attached(),"closed endpoint stays settled after full source restore");require(monitor.failure==null,monitor.failure);monitor.armed=false;net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().unregister(monitor);for(BlockPos at:BlockPos.getAllInBox(CANOPY.add(-7,-4,-7),CANOPY.add(9,9,8)))world.setBlockToAir(at);}).get();stage=5;System.out.println("[vandorlabs][reprolab] shared-canopy-retraction PASS real-channel normal-world-ticks manual-HIGH-close waits-entire-retraction pending-reload per-server-tick-interlock final-source-restoration");return true;}
        return false;
    }
}

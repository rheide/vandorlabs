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
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Opt-in real chunk unload/load ordering of a canopy beside a shared Moat. */
final class CanopyChunkOrderChecks {
    private static final BlockPos CANOPY=new BlockPos(5008,111,5016),RAMP=CANOPY.add(-2,-1,-2),LEVER=CANOPY.east(4).down();
    private static final int CHANNEL=1889;private static int stage,ticks;private static long since;
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("canopy chunk order: "+text);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static TileEntityCanopy canopy(WorldServer world){return TileEntityCanopy.at(world,CANOPY);}
    private static TileEntityRampController ramp(WorldServer world){return (TileEntityRampController)world.getTileEntity(RAMP);}
    private static void setup(Minecraft mc){WorldServer world=world(mc);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());Vec3d previous=actor.getPositionVector();try{
        for(BlockPos at:BlockPos.getAllInBox(CANOPY.add(-7,-4,-7),CANOPY.add(9,9,8))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:large_front_canopy_glass");LargeCanopySettings settings=new LargeCanopySettings();settings.depth=24;settings.scale=50;settings.hinge=2;settings.openable=true;settings.redstone=true;settings.channels=ChannelList.of(CHANNEL);ItemStack item=new ItemStack(block);NBTTagCompound data=new NBTTagCompound();settings.write(data);item.setTagCompound(data);world.setBlockState(CANOPY,block.getDefaultState().withProperty(BlockCanopy.FACING,EnumFacing.WEST),3);block.onBlockPlacedBy(world,CANOPY,world.getBlockState(CANOPY),null,item);
        for(BlockPos cell:canopy(world).cells())require((cell.getX()>>4)==(CANOPY.getX()>>4),"canopy footprint entirely in retained chunk");
        world.setBlockState(RAMP,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.EAST),3);for(int y=0;y<2;y++)world.setBlockState(RAMP.east().up(y),Blocks.IRON_BLOCK.getDefaultState(),3);actor.setPosition(RAMP.getX()-2,RAMP.getY()+2,RAMP.getZ()+.5);require(ramp(world).configureHalfOffsets(actor,0,0,8,true,false,false,EnumFacing.EAST,0,false,1,true,false,0,0,true),"configure rotated exact shared tower: "+ramp(world).status);ramp(world).setRedstoneChannels(ChannelList.of(CHANNEL));
        BlockIndustrialLever lever=(BlockIndustrialLever)Block.getBlockFromName("vandorlabs:industrial_power_lever");world.setBlockState(LEVER.down(),Blocks.STONE.getDefaultState(),3);world.setBlockState(LEVER,lever.getDefaultState().withProperty(BlockIndustrialLever.FLOOR,true),3);((TileEntityRedstoneChannel)world.getTileEntity(LEVER)).setRedstoneChannels(ChannelList.of(CHANNEL));((TileEntityRedstoneChannel)world.getTileEntity(LEVER)).setLocalOn(true);
        require(actor.getDistanceSqToCenter(RAMP)<64,"temporary configuration reach valid");
        }finally{actor.setPosition(previous.x,previous.y,previous.z);}
    }
    static boolean tick(Minecraft mc)throws Exception{if(!Boolean.getBoolean("vandorlabs.canopyChunkOrderChecks")||stage==5)return true;require(++ticks<1000,"timeout stage="+stage);
        if(stage==0){mc.getIntegratedServer().addScheduledTask(()->setup(mc)).get();stage=1;return false;}
        boolean[] ready={false};mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);if(stage==1)ready[0]=canopy(world).progress==1&&ramp(world).isOpen()&&!ramp(world).isMoving();else if(stage==2)ready[0]=world.getTotalWorldTime()-since>=30;else if(stage==3)ready[0]=world.getTotalWorldTime()-since>=30;else if(stage==4)ready[0]=canopy(world).progress==0&&!canopy(world).waitingForReservations&&!ramp(world).isMoving()&&!ramp(world).isOpen();}).get();if(!ready[0])return false;
        if(stage==1){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);boolean localPart=false;for(BlockPos cell:ramp(world).vehicleCells())if((cell.getX()>>4)==(CANOPY.getX()>>4))localPart=true;require(localPart,"deployed journal has local parts in retained chunk");canopy(world).toggle();since=world.getTotalWorldTime();}).get();stage=2;}
        else if(stage==2){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);require(canopy(world).progress==1&&canopy(world).waitingForReservations,"manual close waits before controller unload");net.minecraft.world.chunk.Chunk controller=world.getChunkProvider().getLoadedChunk(RAMP.getX()>>4,RAMP.getZ()>>4);world.getChunkProvider().queueUnload(controller);world.getChunkProvider().tick();require(world.getChunkProvider().getLoadedChunk(RAMP.getX()>>4,RAMP.getZ()>>4)==null,"actual controller chunk unloaded without loading it back");require(world.getChunkProvider().getLoadedChunk(CANOPY.getX()>>4,CANOPY.getZ()>>4)!=null,"canopy and local part chunk retained");since=world.getTotalWorldTime();}).get();stage=3;}
        else if(stage==3){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);TileEntityCanopy canopy=canopy(world);boolean protectedWait=canopy.progress==1&&!canopy.targetOpen&&canopy.waitingForReservations;String diagnostic="progress="+canopy.progress+" target="+canopy.targetOpen+" wait="+canopy.waitingForReservations;world.getChunkFromBlockCoords(RAMP);require(protectedWait,"unloaded shared owner must not release pending close: "+diagnostic);((TileEntityRedstoneChannel)world.getTileEntity(LEVER)).setLocalOn(false);}).get();stage=4;}
        else if(stage==4){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);require(canopy(world).progress==0&&!ramp(world).attached(),"controller reload and OFF resume full retract then close");for(BlockPos at:BlockPos.getAllInBox(CANOPY.add(-7,-4,-7),CANOPY.add(9,9,8)))world.setBlockToAir(at);}).get();stage=5;System.out.println("[vandorlabs][reprolab] canopy-chunk-order PASS real-controller-unload local-parts-retained pending-close-protected loaded-owner-retry source-OFF retract-before-close");return true;}
        return false;
    }
}

package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
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

/** Joined canopy and a seat that recreates its yielded invisible backrest during real ticks. */
final class JoinedCanopySeatRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(1300,100,250),BACK=ROOT.west().north(),SEAT=BACK.down(),LEVER=ROOT.east(4);
    private static final int CHANNEL=1881;private static int stage,ticks,hold;private static NBTTagCompound seatData;
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("joined canopy seat: "+text);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static TileEntityCanopy root(WorldServer world){return TileEntityCanopy.at(world,ROOT);}
    private static void power(WorldServer world,boolean high){((TileEntityRedstoneChannel)world.getTileEntity(LEVER)).setLocalOn(high);}
    private static void shared(WorldServer world){require(world.getBlockState(BACK).getBlock()==ModBlocks.PILOT_SEAT&&world.getBlockState(BACK).getValue(BlockPilotSeat.UPPER),"shared cell preserves actual seat upper: state="+world.getBlockState(BACK)+" root="+root(world).writeToNBT(new NBTTagCompound()));require(world.getBlockState(SEAT).getBlock()==ModBlocks.PILOT_SEAT&&!world.getBlockState(SEAT).getValue(BlockPilotSeat.UPPER),"lower seat survives");require(seatData.equals(world.getTileEntity(SEAT).writeToNBT(new NBTTagCompound())),"seat settings unchanged");TileEntityCanopy member=TileEntityCanopy.at(world,ROOT.west());require(member.isReleasedCell(BACK)&&!member.reservedCells().contains(BACK),"shared backrest remains logically released through close");require(root(world).width()==3,"all three constituents retain their join");}
    private static void restoreSeat(WorldServer world){world.setBlockState(SEAT,ModBlocks.PILOT_SEAT.getDefaultState(),3);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,SEAT,world.getBlockState(SEAT),null,new ItemStack(ModBlocks.PILOT_SEAT));world.getTileEntity(SEAT).readFromNBT(seatData.copy());}
    private static void reload(WorldServer world){for(int x=0;x<3;x++){TileEntityCanopy member=TileEntityCanopy.at(world,ROOT.west(x));member.readFromNBT(member.writeToNBT(new NBTTagCompound()));member.onLoad();}}
    static boolean tick(Minecraft mc)throws Exception{
        if(stage==8)return true;require(++ticks<1500,"timeout stage="+stage);
        if(stage==0){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());actor.dismountRidingEntity();actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.setPositionAndUpdate(ROOT.getX()+2,ROOT.getY()+3,ROOT.getZ()-4);
            for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-5,-3,-4),ROOT.add(5,5,4))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
            BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:canopy");for(int x=0;x<3;x++){BlockPos at=ROOT.west(x);world.setBlockState(at,block.getDefaultState().withProperty(BlockCanopy.FACING,EnumFacing.SOUTH),2);ItemStack canopyItem=new ItemStack(block);NBTTagCompound shape=new NBTTagCompound();shape.setInteger("CanopyAngle",2);shape.setBoolean("CanopySlideForward",true);canopyItem.setTagCompound(shape);block.onBlockPlacedBy(world,at,world.getBlockState(at),null,canopyItem);}
            require(root(world).width()==3,"three-wide fixture joins");world.setBlockState(SEAT,ModBlocks.PILOT_SEAT.getDefaultState(),3);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,SEAT,world.getBlockState(SEAT),null,new ItemStack(ModBlocks.PILOT_SEAT));seatData=world.getTileEntity(SEAT).writeToNBT(new NBTTagCompound());
            world.setBlockState(LEVER.down(),Blocks.STONE.getDefaultState(),3);BlockIndustrialLever lever=(BlockIndustrialLever)Block.getBlockFromName("vandorlabs:industrial_power_lever");world.setBlockState(LEVER,lever.getDefaultState().withProperty(BlockIndustrialLever.FLOOR,true),3);((TileEntityRedstoneChannel)world.getTileEntity(LEVER)).setRedstoneChannels(ChannelList.of(CHANNEL));root(world).large.redstone=true;root(world).setRedstoneChannels(ChannelList.of(CHANNEL));power(world,true);
        }).get();stage=1;return false;}
        TileEntityCanopy client=TileEntityCanopy.at(mc.world,ROOT);if(client==null)return false;
        if(stage==1&&client.progress==1&&client.targetOpen){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);shared(world);CanopySweepRuntimeChecks.run(root(world));power(world,false); // Resume the exact saved pending-close shape, not merely an open reload.
            root(world).waitingForReservations=true;reload(world);
        }).get();stage=2;hold=0;}
        else if(stage==2&&client.progress==0&&!client.targetOpen&&!client.waitingForReservations){if(++hold>=30){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);shared(world);world.setBlockToAir(SEAT);}).get();stage=21;hold=0;}}
        else if(stage==21&&client.progress==0&&!client.targetOpen){if(++hold>=30){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);require(world.isAirBlock(SEAT)&&world.getBlockState(BACK).getBlock()==root(world).block(),"closed seat removal safely reacquires vacated canopy helper: back="+world.getBlockState(BACK)+" middle="+world.getBlockState(ROOT.west())+" root="+root(world).writeToNBT(new NBTTagCompound()));
            require(root(world).width()==3,"closed helper recovery preserves all three joined constituents");
            for(int x=0;x<3;x++){BlockPos anchor=ROOT.west(x);TileEntityCanopy member=TileEntityCanopy.at(world,anchor);require(member!=null&&anchor.equals(member.anchor)&&ROOT.equals(member.pair),"closed helper recovery retains constituent anchor/pair "+anchor);for(BlockPos cell:member.reservedCells()){net.minecraft.tileentity.TileEntity raw=world.getTileEntity(cell);require(raw instanceof TileEntityCanopy&&anchor.equals(((TileEntityCanopy)raw).anchor),"closed helper recovery retains owned helper "+cell);}}
            restoreSeat(world);reload(world);power(world,true);}).get();stage=3;hold=0;}}
        else if(stage==3&&client.progress==1&&client.targetOpen){mc.getIntegratedServer().addScheduledTask(()->{shared(world(mc));root(world(mc)).toggle();}).get();stage=4;hold=0;}
        else if(stage==4&&client.progress==0&&!client.targetOpen){if(++hold>=30){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);shared(world);require(RedstoneChannels.level(world,CHANNEL)==15,"manual close holds while powered");power(world,false);power(world,true);}).get();stage=5;hold=0;}}
        else if(stage==5&&client.progress==1&&client.targetOpen){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);shared(world);world.setBlockToAir(SEAT);BlockPos foreign=BACK;require(world.isAirBlock(foreign),"removed seat frees released backrest for ordinary blocker");world.setBlockState(foreign,Blocks.GOLD_BLOCK.getDefaultState(),3);power(world,false);}).get();stage=6;hold=0;}
        else if(stage==6&&client.waitingForReservations&&!client.targetOpen){if(++hold>=25){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);require(root(world).progress==1&&world.getBlockState(BACK).getBlock()==Blocks.GOLD_BLOCK,"ordinary foreign block still prevents closing");require(world.isAirBlock(SEAT),"foreign-block test did not recreate removed seat");world.setBlockToAir(BACK);restoreSeat(world);}).get();stage=7;hold=0;}}
        else if(stage==7&&client.progress==0&&!client.targetOpen&&!client.waitingForReservations){if(++hold>=40){mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);shared(world);for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-5,-3,-4),ROOT.add(5,5,4)))world.setBlockToAir(at);}).get();stage=8;System.out.println("[vandorlabs][reprolab] joined-canopy-seat PASS three-wide normal-world-ticks recreated-upper shared-closed-backrest pending-NBT-reload repeated-cycles manual-HIGH-close real-channel-OFF foreign-blocker authoritative-client");return true;}}
        return false;
    }
}

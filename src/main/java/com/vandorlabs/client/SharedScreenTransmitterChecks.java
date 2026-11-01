package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.items.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

final class SharedScreenTransmitterChecks {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Shared screen transmitter: "+message);}
    static void run(WorldServer world,EntityPlayerMP actor){
        BlockPos at=new BlockPos(1040,200,1040);EntityGroundVehicle craft=null;
        try{
            for(BlockPos pos:BlockPos.getAllInBox(at.add(-2,-2,-2),at.add(16,3,3)))world.getChunkFromBlockCoords(pos);
            world.setBlockState(at,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),3);TileEntityAnimatedScreenSelector screen=(TileEntityAnimatedScreenSelector)world.getTileEntity(at);
            screen.setSurfaceTexture(0,TileEntityAnimatedScreenSelector.REDSTONE_SURFACE);RedstoneScreenContents contents=screen.redstoneScreen(0);
            require(contents.configureRows(Arrays.asList("Doors","Lights"),Arrays.asList(ChannelList.of(28731),ChannelList.of(28732)),0),"configure independent row targets");
            TransmitterSlots.configure(screen,ChannelList.of(28733),256,true);
            com.vandorlabs.container.ContainerAnimatedScreenSelector container=new com.vandorlabs.container.ContainerAnimatedScreenSelector(actor.inventory,screen);InlineTransmitters.attach(container,actor);InlineTransmitters.Binding binding=InlineTransmitters.binding(container);
            require(container.inventorySlots.isEmpty()&&binding.source(0)==screen&&binding.source(1)==null,"built-in settings append no module or hotbar slots");
            contents.toggleRow(0);require(RedstoneChannels.level(world,at,28731)==15&&RedstoneChannels.level(world,at,28732)==0&&RedstoneChannels.level(world,at,28733)==0,"row target overrides the shared defaults without bridging the other row");
            contents.toggleRow(1);require(RedstoneChannels.level(world,at,28732)==15,"the shared settings broadcast a second distinct row");
            TransmitterSlots.configure(screen,ChannelList.EMPTY,4,true);
            require(screen.getBlockType().getWeakPower(world.getBlockState(at),world,at,EnumFacing.UP)==15,"shared toggle rows emit physical power when enabled");
            require(RedstoneChannels.level(world,at.east(8),28731)==0&&RedstoneChannels.level(world,at.east(8),28732)==0,"range reduction withdraws every distant row output");
            TransmitterSlots.configure(screen,ChannelList.EMPTY,16,false);
            require(screen.getBlockType().getWeakPower(world.getBlockState(at),world,at,EnumFacing.UP)==0,"shared toggle rows suppress physical power independently");
            require(RedstoneChannels.level(world,at,28731)==15&&RedstoneChannels.level(world,at,28732)==15,"increasing shared range restores saved ON row levels");
            require(contents.configureRows(Arrays.asList("Door bank","Light bank"),Arrays.asList(ChannelList.of(28731),ChannelList.of(28732)),0),"saving row labels retains targets");
            require(contents.rows().get(0).channels.equals(ChannelList.of(28731))&&contents.rows().get(1).channels.equals(ChannelList.of(28732)),"row save does not replace channels with transmitter defaults");
            NBTTagCompound saved=VehicleTileData.translated(screen.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(at));world.removeTileEntity(at);world.setBlockToAir(at);
            require(RedstoneChannels.level(world,at,28731)==0&&RedstoneChannels.level(world,at,28732)==0,"native source retires before carried ownership opens");
            VehicleStructure structure=new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),saved),new VehicleStructure.Cell(new BlockPos(1,0,0),ModBlocks.PILOT_SEAT.getDefaultState(),null)),new BlockPos(1,0,0),EnumFacing.NORTH,1);
            craft=new EntityGroundVehicle(world);craft.install(structure);craft.setPosition(at.getX()+8,at.getY(),at.getZ());craft.view.origin=new BlockPos(craft);craft.signals.open();craft.signals.tick();
            require(RedstoneChannels.level(world,new BlockPos(craft),28731)==15&&RedstoneChannels.level(world,new BlockPos(craft),28732)==15&&RedstoneChannels.level(world,new BlockPos(craft),28733)==0,"carried rows preserve both targets through shared settings");
            TileEntityAnimatedScreenSelector carried=(TileEntityAnimatedScreenSelector)craft.view.getTileEntity(BlockPos.ORIGIN);TransmitterSlots.configure(carried,ChannelList.EMPTY,4,false);craft.signals.tick();
            require(RedstoneChannels.level(world,new BlockPos(craft).east(8),28731)==0&&RedstoneChannels.level(world,new BlockPos(craft).east(8),28732)==0,"range change reaches every carried row");
            System.out.println("[vandorlabs][reprolab] shared-screen-transmitter PASS built-in-range no-item-slots independent-targets physical-toggle saved-labels native-carried-handoff range-change");
        }finally{if(craft!=null){craft.signals.close();craft.setDead();}world.setBlockToAir(at);}
    }
}

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
            ItemStack module=new ItemStack(ModItems.REDSTONE_TRANSMITTER,1,2);ItemRedstoneTransmitter.configure(module,ChannelList.of(28733));TransmitterSlots.set(screen,module);
            com.vandorlabs.container.ContainerAnimatedScreenSelector container=new com.vandorlabs.container.ContainerAnimatedScreenSelector(actor.inventory,screen);InlineTransmitters.attach(container,actor);InlineTransmitters.Binding binding=InlineTransmitters.binding(container);
            require(binding.modules.get(0).getHasStack(),"one real shared module slot");for(int i=1;i<=16;i++)require(!binding.modules.get(i).getHasStack(),"reserved old row slots do not alias the shared item");
            contents.toggleRow(0);require(RedstoneChannels.level(world,at,28731)==15&&RedstoneChannels.level(world,at,28732)==0&&RedstoneChannels.level(world,at,28733)==0,"row target overrides the module defaults without bridging the other row");
            contents.toggleRow(1);require(RedstoneChannels.level(world,at,28732)==15,"the same module broadcasts a second distinct row");
            TransmitterSlots.set(screen,ItemStack.EMPTY);
            require(RedstoneChannels.level(world,at,28731)==0&&RedstoneChannels.level(world,at,28732)==0,"removing shared module immediately withdraws every cached row output");
            TransmitterSlots.set(screen,module);
            require(RedstoneChannels.level(world,at,28731)==15&&RedstoneChannels.level(world,at,28732)==15,"reinserting shared module restores saved ON row levels");
            require(contents.configureRows(Arrays.asList("Door bank","Light bank"),Arrays.asList(ChannelList.of(28731),ChannelList.of(28732)),0),"saving row labels retains targets");
            require(contents.rows().get(0).channels.equals(ChannelList.of(28731))&&contents.rows().get(1).channels.equals(ChannelList.of(28732)),"row save does not replace channels with transmitter defaults");
            NBTTagCompound saved=VehicleTileData.translated(screen.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(at));world.removeTileEntity(at);world.setBlockToAir(at);
            require(RedstoneChannels.level(world,at,28731)==0&&RedstoneChannels.level(world,at,28732)==0,"native source retires before carried ownership opens");
            VehicleStructure structure=new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),saved),new VehicleStructure.Cell(new BlockPos(1,0,0),ModBlocks.PILOT_SEAT.getDefaultState(),null)),new BlockPos(1,0,0),EnumFacing.NORTH,1);
            craft=new EntityGroundVehicle(world);craft.install(structure);craft.setPosition(at.getX()+8,at.getY(),at.getZ());craft.view.origin=new BlockPos(craft);craft.signals.open();craft.signals.tick();
            require(RedstoneChannels.level(world,new BlockPos(craft),28731)==15&&RedstoneChannels.level(world,new BlockPos(craft),28732)==15&&RedstoneChannels.level(world,new BlockPos(craft),28733)==0,"carried rows preserve both targets through the shared module");
            TileEntityAnimatedScreenSelector carried=(TileEntityAnimatedScreenSelector)craft.view.getTileEntity(BlockPos.ORIGIN);TransmitterSlots.set(carried,ItemStack.EMPTY);craft.signals.tick();
            require(RedstoneChannels.level(world,new BlockPos(craft),28731)==0&&RedstoneChannels.level(world,new BlockPos(craft),28732)==0,"removing the one module stops every carried row");
            System.out.println("[vandorlabs][reprolab] shared-screen-transmitter PASS one-real-slot reserved-row-indices empty-aliases independent-targets saved-labels native-carried-handoff module-removal");
        }finally{if(craft!=null){craft.signals.close();craft.setDead();}world.setBlockToAir(at);}
    }
}

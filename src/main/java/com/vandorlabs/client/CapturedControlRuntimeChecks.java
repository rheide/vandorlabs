package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Parent-bank output, linked detents and timed captured push-button release. */
final class CapturedControlRuntimeChecks {
    private static final int LATCH=19372,PULSE=19373;
    private static final BlockPos LEVER=new BlockPos(2,1,1),SLIDER=new BlockPos(3,1,1),BUTTON=new BlockPos(4,1,1),PARENT=new BlockPos(120,120,120);
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("captured controls: "+what);}
    private static VehicleStructure.Cell cell(BlockPos pos,Block block,int channel){
        TileEntityRedstoneChannel tile=block instanceof BlockSignalControl?new TileEntitySignalControl():new TileEntityRedstoneChannel();tile.setPos(pos);tile.setRedstoneChannel(channel);
        return new VehicleStructure.Cell(pos,block.getDefaultState(),tile.writeToNBT(new NBTTagCompound()));
    }
    static void run(WorldServer world,EntityPlayerMP player){
        Block lever=null,slider=null,button=null;
        for(Block candidate:Block.REGISTRY){if(candidate instanceof BlockIndustrialLever)lever=candidate;if(candidate instanceof BlockSignalControl && ((BlockSignalControl)candidate).controlKind().equals("wall_slider"))slider=candidate;if(candidate instanceof BlockVandorSwitch && ((BlockVandorSwitch)candidate).isMomentary())button=candidate;}
        require(lever!=null && slider!=null && button!=null,"lever slider button registered");
        List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));cells.add(cell(LEVER,lever,LATCH));cells.add(cell(SLIDER,slider,LATCH));cells.add(cell(BUTTON,button,PULSE));
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));craft.setPosition(100,150,100);
        world.getChunkFromBlockCoords(PARENT);world.setBlockState(PARENT.south(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);world.setBlockState(PARENT,lever.getDefaultState(),2);TileEntityRedstoneChannel parent=(TileEntityRedstoneChannel)world.getTileEntity(PARENT);parent.setRedstoneChannel(LATCH);
        long time=world.getTotalWorldTime();boolean sneaking=player.isSneaking();player.setSneaking(false);
        try{
            craft.signals.tick();require(VehicleSignals.supports(craft.view,LEVER) && VehicleSignals.supports(craft.view,SLIDER) && VehicleSignals.supports(craft.view,BUTTON),"all captured controls interactive");
            require(craft.signals.click(LEVER,player),"lever click accepted");require(RedstoneChannels.level(world,LATCH)==15 && parent.getOutputLevel()==15,"lever publishes parent channel");
            require(craft.view.getBlockState(LEVER).getValue(BlockIndustrialLever.POWERED) && craft.view.getBlockState(SLIDER).getValue(BlockVandorSwitch.ON),"linked lever and slider visual states");
            require(((TileEntitySignalControl)craft.view.getTileEntity(SLIDER)).getStep()==3,"linked slider follows high level");
            require(craft.signals.click(SLIDER,player) && RedstoneChannels.level(world,LATCH)==0,"slider wraps high to off");
            require(craft.signals.click(SLIDER,player) && RedstoneChannels.level(world,LATCH)==5 && parent.getOutputLevel()==5,"slider low detent publishes analog5");
            parent.setLocalOn(false);craft.signals.tick();require(RedstoneChannels.level(world,LATCH)==0 && !craft.view.getBlockState(LEVER).getValue(BlockIndustrialLever.POWERED) && ((TileEntitySignalControl)craft.view.getTileEntity(SLIDER)).getStep()==0,"parent latch updates both captured controls");
            require(craft.signals.click(BUTTON,player) && RedstoneChannels.level(world,PULSE)==15,"button publishes momentary source");require(craft.view.getBlockState(BUTTON).getValue(BlockVandorSwitch.ON),"button shows pressed visual");
            restoredButton(world,(BlockVandorSwitch)button,craft.structure.at(BUTTON));
            world.setTotalWorldTime(time+19);craft.signals.tick();require(RedstoneChannels.level(world,PULSE)==15,"button remains high for19 ticks");
            world.setTotalWorldTime(time+20);craft.signals.tick();require(RedstoneChannels.level(world,PULSE)==0 && !craft.view.getBlockState(BUTTON).getValue(BlockVandorSwitch.ON),"button releases at20 ticks");
            require(craft.signals.click(BUTTON,player),"button second pulse");world.setTotalWorldTime(time+30);require(craft.signals.click(BUTTON,player),"held button refreshes pulse");world.setTotalWorldTime(time+49);craft.signals.tick();require(RedstoneChannels.level(world,PULSE)==15,"repeated press extends pulse");world.setTotalWorldTime(time+50);craft.signals.tick();require(RedstoneChannels.level(world,PULSE)==0,"repeated press eventually releases");
            require(craft.signals.click(SLIDER,player),"slider before snapshot");craft.signals.persist();VehicleWorld reloaded=new VehicleWorld(world,craft.structure);require(((TileEntitySignalControl)reloaded.getTileEntity(SLIDER)).getStep()==1 && reloaded.getBlockState(LEVER).getValue(BlockIndustrialLever.POWERED),"snapshot retains linked control values and visual states");
            parent.setLocalOn(false);craft.signals.tick();
        }finally{craft.setDead();world.setBlockToAir(PARENT);world.setBlockToAir(PARENT.south());world.setTotalWorldTime(time);player.setSneaking(sneaking);}
        require(RedstoneChannels.level(world,LATCH)==0 && RedstoneChannels.level(world,PULSE)==0,"despawn unregisters outputs");
        System.out.println("[vandorlabs][reprolab] captured-controls PASS lever wall-slider parent-network linked-controls analog-detents pressed-visual button20-tick-release repeated-press parked-button-reload scheduled-release snapshot cleanup");
    }
    private static void restoredButton(WorldServer world,BlockVandorSwitch button,VehicleStructure.Cell captured){
        BlockPos placed=PARENT.east(3);long time=world.getTotalWorldTime();int channel=19374;
        world.setBlockState(placed.south(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);
        world.setBlockState(placed,captured.state,2);
        TileEntityRedstoneChannel tile=(TileEntityRedstoneChannel)world.getTileEntity(placed);
        NBTTagCompound data=VehicleTileData.translated(captured.tileData(),placed.subtract(captured.pos));ChannelData.write(data,ChannelList.of(channel));
        try{
            tile.readFromNBT(data);tile.onLoad();DeferredTileLoad.flush(world);
            require(tile.isLocalOn() && world.getBlockState(placed).getValue(BlockVandorSwitch.ON) && RedstoneChannels.level(world,channel)==15,"parked/reloaded pressed button restores source and visual");
            require(world.isUpdateScheduled(placed,button),"reloaded pressed button schedules release");
            List<net.minecraft.world.NextTickListEntry> pending=world.getPendingBlockUpdates(world.getChunkFromBlockCoords(placed),false);
            require(pending!=null && pending.stream().anyMatch(entry->entry.position.equals(placed) && entry.getBlock()==button && entry.scheduledTime==time+button.tickRate(world)),"reloaded release uses normal button duration");
            world.setTotalWorldTime(time+button.tickRate(world));button.updateTick(world,placed,world.getBlockState(placed),world.rand);
            require(!tile.isLocalOn() && !world.getBlockState(placed).getValue(BlockVandorSwitch.ON) && RedstoneChannels.level(world,channel)==0,"scheduled placed-button tick releases visual and channel");
        }finally{world.setBlockToAir(placed);world.setBlockToAir(placed.south());world.setTotalWorldTime(time);}
    }
    private CapturedControlRuntimeChecks(){}
}

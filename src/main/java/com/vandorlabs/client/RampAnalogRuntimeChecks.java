package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Live-world signal levels, native stationary door support and saved intermediate deployment. */
final class RampAnalogRuntimeChecks {
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("analog ramp: "+text);}
    private static final class Source extends TileEntity implements RedstoneChannelMember {
        int level;
        public TileEntity channelTile(){return this;}
        public boolean isChannelMemberActive(){return world!=null&&!isInvalid();}
        public int getRedstoneChannel(){return 8190;}
        public void setRedstoneChannel(int channel){}
        public boolean hasLocalRedstoneSignal(){return level>0;}
        public int localSignalLevel(int channel){return level;}
        public void setChannelSignal(boolean value){}
        void level(int next){level=next;RedstoneChannels.inputChanged(this);}
    }
    static void run(World world,EntityPlayerMP actor){
        BlockPos root=new BlockPos(706,178,296);double x=actor.posX,y=actor.posY,z=actor.posZ;
        Source source=new Source();source.setWorld(world);source.setPos(root.west(4));
        TileEntityRampController controller=null;
        try{
            for(BlockPos at:BlockPos.getAllInBox(root.add(-5,-2,-2),root.add(5,6,6))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
            actor.setPosition(root.getX()+.5,root.getY()+1,root.getZ()-.5);
            world.setBlockState(root,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),3);
            for(int row=1;row<=3;row++)world.setBlockState(root.south(row),Blocks.STONE.getDefaultState(),3);
            controller=(TileEntityRampController)world.getTileEntity(root);controller.setOwner(actor);
            require(controller.configureHalfOffsets(actor,0,4,16,true,false,false,EnumFacing.SOUTH,0,false,1,true,false,0,0,false),"configure ordinary three-block platform");
            controller.setRedstoneChannels(ChannelList.of(8190));controller.setAnalogDeployment(true);
            ItemStack transmitter=new ItemStack(Item.getByNameOrId("vandorlabs:redstone_transmitter"),1,3);
            com.vandorlabs.items.ItemRedstoneTransmitter.configure(transmitter,ChannelList.of(8190));TransmitterSlots.set(source,transmitter);
            source.level(8);ControllerRuntimeChecks.elapsed(controller,controller.durationTicks()+1);
            require(controller.attached()&&!controller.isMoving()&&Math.abs(controller.pose(0)-8/15D)<1e-8,"level 8 stops at its intermediate deployment");
            NBTTagCompound saved=controller.writeToNBT(new NBTTagCompound());require(saved.getBoolean("RampAnalogDeployment")&&Math.abs(saved.getDouble("RampTargetPose")-8/15D)<1e-8,"analog option and target persist");
            for(BlockPos at:controller.vehicleCells())if(world.getTileEntity(at) instanceof TileEntityControlledRamp){TileEntityControlledRamp part=(TileEntityControlledRamp)world.getTileEntity(at);require(Math.abs(part.pose(0)-controller.pose(0))<1e-8,"controller and generated cells share analog endpoint");}
            require(world.isSideSolid(root.south(),EnumFacing.UP)&&Blocks.OAK_DOOR.canPlaceBlockAt(world,root.south().up()),"stationary nearest full-width tread supports ordinary doors");
            require(!world.isSideSolid(root.south(2),EnumFacing.UP),"moving tread cannot advertise stationary door support");
            source.level(12);ControllerRuntimeChecks.elapsed(controller,controller.durationTicks()+1);require(Math.abs(controller.pose(0)-12/15D)<1e-8,"same ON state with changed analog level retargets");
            source.level(4);ControllerRuntimeChecks.elapsed(controller,controller.durationTicks()+1);require(Math.abs(controller.pose(0)-4/15D)<1e-8,"analog reversal reaches lower target");
            ControllerRuntimeChecks.reload(controller);require(controller.analogDeployment&&Math.abs(controller.pose(0)-4/15D)<1e-8,"reload preserves intermediate pose");
            source.level(15);ControllerRuntimeChecks.elapsed(controller,controller.durationTicks()+1);require(controller.pose(0)==1,"level 15 fully deploys");
            source.level(0);ControllerRuntimeChecks.elapsed(controller,controller.durationTicks()+1);require(!controller.attached()&&world.getBlockState(root.south()).getBlock()==Blocks.STONE,"zero signal restores native source ownership");
            java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> carriedCells=new java.util.ArrayList<>();
            for(BlockPos at:java.util.Arrays.asList(root,root.south(),root.south(2),root.south(3))){TileEntity tile=world.getTileEntity(at);NBTTagCompound data=tile==null?null:com.vandorlabs.vehicle.VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),new BlockPos(-root.getX(),-root.getY(),-root.getZ()));if(tile instanceof TileEntityRampController)data=com.vandorlabs.vehicle.VehicleEscalators.paused(data);carriedCells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(at.subtract(root),world.getBlockState(at),data));}
            carriedCells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(new BlockPos(2,0,0),ModBlocks.PILOT_SEAT.getDefaultState(),null));
            controller.setRedstoneChannels(ChannelList.EMPTY);
            com.vandorlabs.vehicle.EntityGroundVehicle craft=new com.vandorlabs.vehicle.EntityGroundVehicle(world);
            try{
                craft.install(new com.vandorlabs.vehicle.VehicleStructure(carriedCells,new BlockPos(2,0,0),EnumFacing.NORTH,3));craft.setPosition(root.getX()+32,root.getY()+16,root.getZ());craft.view.origin=new BlockPos(craft);
                for(BlockPos at:BlockPos.getAllInBox(new BlockPos(craft).add(-2,-4,-2),new BlockPos(craft).add(6,4,6)))world.getChunkFromBlockCoords(at);
                craft.signals.open();BlockPos relative=new BlockPos(-2,0,0);
                for(int level:new int[]{8,12,4,15,0}){
                    source.level(level);craft.signals.tick();TileEntityRampController carried=(TileEntityRampController)craft.view.getTileEntity(craft.structure.seat.add(relative));
                    require(Math.abs(carried.pose(0)-level/15D)<1e-8,"carried analog level "+level+" became "+carried.pose(0));
                    for(TileEntity tile:craft.view.tiles.values())if(tile instanceof TileEntityControlledRamp)require(Math.abs(((TileEntityControlledRamp)tile).pose(0)-carried.pose(0))<1e-8,"carried generated cell lost analog pose");
                    for(int turn=0;turn<4;turn++){com.vandorlabs.vehicle.VehicleStructure turned=com.vandorlabs.vehicle.VehicleRotation.rotate(craft.structure,craft.view,turn);com.vandorlabs.vehicle.VehicleWorld view=new com.vandorlabs.vehicle.VehicleWorld(world,turned);for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:turned.cells)com.vandorlabs.vehicle.MovingBlockSupport.requireExactTileData(cell,view.getTileEntity(cell.pos));}
                }
            }finally{craft.signals.close();craft.setDead();}

        }finally{
            RedstoneChannels.unregister(source);if(controller!=null)controller.recover(true);
            for(BlockPos at:BlockPos.getAllInBox(root.add(-5,-2,-2),root.add(5,6,6)))world.setBlockToAir(at);
            actor.setPosition(x,y,z);
        }
        System.out.println("[vandorlabs][reprolab] ramp-analog PASS levels-0-4-8-12-15 same-on-retarget reversal persisted-target generated-cell-parity reload stationary-door-support moving-support-refused native-recovery carried-analog-retarget four-rotation-exact-NBT");
    }
}

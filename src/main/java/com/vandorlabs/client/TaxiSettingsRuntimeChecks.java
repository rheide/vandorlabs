package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityPilotSeat;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.BlockSlab;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Server-world regressions for slab contact, ramp boundaries, trigger polarity and height. */
public final class TaxiSettingsRuntimeChecks {
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("taxi settings: "+message);}
    private static final class Source extends TileEntity implements RedstoneChannelMember {
        boolean high;public TileEntity channelTile(){return this;}public int getRedstoneChannel(){return 5341;}
        public void setRedstoneChannel(int channel){}public boolean hasLocalRedstoneSignal(){return high;}public void setChannelSignal(boolean value){}
        void power(boolean value){high=value;RedstoneChannels.inputChanged(this);}
    }
    public static void run(WorldServer world){
        VehicleEscalatorRuntimeChecks.run(world);VehicleRotationRuntimeChecks.run(world);
        for(float yaw:new float[]{-372,-12,0,12,78,90,102,258,270,282,348,372})require(VehicleRotation.turns(yaw)==Math.floorMod(Math.round(yaw/90),4),"lenient cardinal heading "+yaw);
        for(float yaw:new float[]{13,45,77,103,Float.NaN}){boolean refused=false;try{VehicleRotation.turns(yaw);}catch(IllegalArgumentException expected){refused=true;}require(refused,"noncardinal heading "+yaw);}
        BlockPos at=new BlockPos(320,102,160);world.getChunkFromBlockCoords(at.west(4));world.getChunkFromBlockCoords(at.east(4));
        Source source=new Source();source.setWorld(world);source.setPos(at.east(5));RedstoneChannels.register(source);
        TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setWorld(world);seat.setPos(at);seat.setVehicleChannels(ChannelList.of(5341),world);
        try{
            seat.configure(1,0,world);require(seat.poll(world)==-1,"ON initial baseline");source.power(true);require(seat.poll(world)==1,"ON rising activation");require(seat.poll(world)==-1,"held signal does not retrigger");
            seat.configure(2,3,world);require(seat.poll(world)==-1,"OFF edit takes current baseline");source.power(false);require(seat.poll(world)==1,"OFF falling activation");
            seat.configure(0,2,world);source.power(true);require(seat.poll(world)==-1,"disabled ignores signal");
            for(int height=0;height<4;height++){seat.configure(1,height,world);TileEntityPilotSeat copy=new TileEntityPilotSeat();copy.readFromNBT(seat.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));require(copy.getHeight()==height && copy.getRedstoneMode()==1,"height and trigger save "+height);require(Math.abs(copy.seatHeight()-(7+height*2)/16D)<1e-9,"seat cushion height "+height);}
            seat.setVehicleChannels(ChannelList.EMPTY,world);seat.configure(2,0,world);require(seat.poll(world)==-1,"channel zero cannot activate OFF trigger");
        }finally{RedstoneChannels.unregister(source);}
        for(BlockPos p:BlockPos.getAllInBox(at.add(-3,-2,-2),at.add(3,1,2)))world.setBlockToAir(p);
        for(BlockPos p:BlockPos.getAllInBox(at.add(-3,-2,-2),at.add(3,-2,2)))world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
        world.setBlockState(at,ModBlocks.PILOT_SEAT.getDefaultState(),2);world.setBlockState(at.up(),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.UPPER,true),2);
        world.setBlockState(at.down(),Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF,BlockSlab.EnumBlockHalf.TOP),2);
        BlockTelescopicLandingGear gear=null;for(net.minecraft.block.Block block:net.minecraft.block.Block.REGISTRY)if(block instanceof BlockTelescopicLandingGear){gear=(BlockTelescopicLandingGear)block;break;}
        require(gear!=null,"telescopic landing gear fixture");BlockPos gearPos=at.down().east();
        world.setBlockState(gearPos,gear.getDefaultState().withProperty(BlockTelescopicLandingGear.LOWER,false).withProperty(BlockTelescopicLandingGear.EXTENDED,false),2);
        com.vandorlabs.tiles.TileEntityLandingGear gearTile=(com.vandorlabs.tiles.TileEntityLandingGear)world.getTileEntity(gearPos);
        require(gearTile!=null && gearTile.configure(0,0,0,0),"small retracted gear configures");gearTile.progress=gearTile.previous=0;
        require(gearTile.owner()==null && gear.root(world,gearPos)==gearTile,"retracted mount owns its root");
        for(EnumFacing side:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.SOUTH}){
            BlockPos extra=at.down().offset(side);world.setBlockState(extra,gear.getDefaultState().withProperty(BlockTelescopicLandingGear.LOWER,false).withProperty(BlockTelescopicLandingGear.EXTENDED,false),2);
            com.vandorlabs.tiles.TileEntityLandingGear extraTile=(com.vandorlabs.tiles.TileEntityLandingGear)world.getTileEntity(extra);
            require(extraTile!=null && extraTile.configure(0,0,0,0),"additional retracted support configures");extraTile.progress=extraTile.previous=0;
        }
        world.setBlockState(at.down().west(),ModBlocks.CONTROLLED_RAMP.getDefaultState(),2);world.setBlockState(at.down().west(2),Blocks.STONE.getDefaultState(),2);
        net.minecraft.block.state.IBlockState[] topSlabs={
            Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF,BlockSlab.EnumBlockHalf.TOP),
            ModBlocks.PROGRAMMABLE_SLAB.getDefaultState().withProperty(BlockProgrammableSlab.HALF,BlockSlab.EnumBlockHalf.TOP),
            ModBlocks.PROGRAMMABLE_LIGHT_SLAB.getDefaultState().withProperty(BlockProgrammableLightSlab.HALF,BlockSlab.EnumBlockHalf.TOP)};
        for(net.minecraft.block.state.IBlockState top:topSlabs){
            world.setBlockState(at.down(),top,2);
            require(!VehicleScan.touches(world,at.down(),top,EnumFacing.DOWN),"top slab does not touch floor: "+top.getBlock().getRegistryName());
            require(VehicleScan.touches(world,at,world.getBlockState(at),EnumFacing.DOWN),"pilot touches top slab");
            VehicleScan scan=new VehicleScan(world,at);for(int n=0;n<100 && !scan.step(64);n++);
            require(scan.error==null && scan.result!=null,"top slab craft captures without floor: "+scan.error);
            require(scan.result.cells.size()==6 && scan.result.gearCount==3,"only seat, upper, top slab and three gear roots captured");
            require(scan.result.at(at.down().west().subtract(scan.origin))==null && scan.result.at(at.down().west(2).subtract(scan.origin))==null,"ramp boundary excludes sections and blocks beyond");
            world.setBlockState(at.down(),top.getBlock().getDefaultState(),2);
            require(VehicleScan.touches(world,at.down(),world.getBlockState(at.down()),EnumFacing.DOWN),"bottom slab still touches floor: "+top.getBlock().getRegistryName());
        }
        for(BlockPos p:BlockPos.getAllInBox(at.add(-3,-2,-2),at.add(3,1,2)))world.setBlockToAir(p);
        System.out.println("[vandorlabs][reprolab] taxi-settings PASS slab-contact ramp-boundary lenient-parking trigger-disabled-on-off height-save");
    }
}

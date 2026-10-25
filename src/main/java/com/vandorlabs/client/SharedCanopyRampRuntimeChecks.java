package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.canopy.LargeCanopySettings;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Overlapping cabin canopy and Moat geometry in the disposable live-test world. */
final class SharedCanopyRampRuntimeChecks {
    private static final BlockPos RAMP=new BlockPos(-150,84,164),CANOPY=new BlockPos(-152,85,166),SHARED=new BlockPos(-150,85,166);
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("shared canopy/Moat: "+text);}
    private static void clear(World world){
        if(world.getTileEntity(RAMP) instanceof TileEntityRampController)((TileEntityRampController)world.getTileEntity(RAMP)).recover(false);
        for(BlockPos at:BlockPos.getAllInBox(RAMP.add(-9,-4,-8),RAMP.add(9,13,12))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
    }
    private static final class Source extends TileEntity implements RedstoneChannelMember {
        public boolean isChannelMemberActive(){return getWorld()!=null&&!isInvalid();}
        boolean high;public TileEntity channelTile(){return this;}public int getRedstoneChannel(){return 801;}public void setRedstoneChannel(int channel){}public boolean hasLocalRedstoneSignal(){return high;}public void setChannelSignal(boolean value){}
        void power(boolean value){high=value;RedstoneChannels.inputChanged(this);}
    }
    private static void signal(TileEntityRampController ramp,TileEntityCanopy canopy,boolean high,int order,Source source){if(order==2){source.power(high);return;}if(order==0){ramp.setChannelSignal(high);canopy.setChannelSignal(high);}else{canopy.setChannelSignal(high);ramp.setChannelSignal(high);}}
    static void run(World world,EntityPlayerMP actor){
        CanopyReservationRuntimeChecks.run(world,actor);
        Vec3d before=actor.getPositionVector();long time=world.getTotalWorldTime();
        Source source=new Source();source.setWorld(world);source.setPos(RAMP.west(12));
        try{for(int order=0;order<3;order++){boolean rampFirst=order==0;source.high=false;RedstoneChannels.register(source);
            clear(world);BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:large_front_canopy_glass");
            LargeCanopySettings settings=new LargeCanopySettings();settings.depth=24;settings.scale=50;settings.hinge=2;settings.openable=true;settings.redstone=true;settings.channels=ChannelList.of(801);
            ItemStack stack=new ItemStack(block);NBTTagCompound item=new NBTTagCompound();settings.write(item);stack.setTagCompound(item);
            IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,EnumFacing.NORTH);world.setBlockState(CANOPY,state,3);block.onBlockPlacedBy(world,CANOPY,state,null,stack);
            TileEntityCanopy canopy=TileEntityCanopy.at(world,CANOPY);require(canopy!=null&&canopy.cells().contains(SHARED),"exact cabin's closed canopy owns conflicting cell "+SHARED);
            world.setBlockState(RAMP,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),3);
            world.setBlockState(RAMP.south(),Blocks.IRON_BLOCK.getDefaultState(),3);world.setBlockState(RAMP.south().up(),Blocks.IRON_BLOCK.getDefaultState(),3);
            actor.setPosition(RAMP.getX()+.5,RAMP.getY()+2,RAMP.getZ()-2);
            TileEntityRampController ramp=(TileEntityRampController)world.getTileEntity(RAMP);
            require(ramp.configureHalfOffsets(actor,0,0,8,true,false,false,EnumFacing.SOUTH,0,false,1,true,false,0,0,true),"configure exact two-high tower");ramp.setRedstoneChannels(ChannelList.of(801));
            actor.setPosition(RAMP.getX()-8,RAMP.getY()+8,RAMP.getZ()-6);CanopyPreparationRuntimeChecks.await(canopy);canopy.update();
            signal(ramp,canopy,true,order,source);signal(ramp,canopy,false,order,source);
            for(int tick=0;tick<32;tick++)advance(world,canopy,ramp,rampFirst);
            require(!ramp.attached()&&!ramp.isWaitingForCanopy()&&canopy.progress==0,"rapid on/off cancels pending deployment without consuming sources");
            signal(ramp,canopy,true,order,source);
            require(!ramp.attached()&&ramp.isWaitingForCanopy()&&world.getBlockState(RAMP.south()).getBlock()==Blocks.IRON_BLOCK,"opening request waits without consuming original tower");
            // A load between callbacks and the first clear pose must retain the requested edge.
            ControllerRuntimeChecks.reload(ramp);
            for(int tick=0;tick<160&&!(canopy.progress==1&&ramp.isOpen()&&!ramp.isMoving()&&ramp.attached());tick++)advance(world,canopy,ramp,rampFirst);
            require(canopy.progress==1&&ramp.isOpen()&&!ramp.isMoving()&&ramp.attached(),"both callback orders finish opening automatically: order="+rampFirst+" canopy="+canopy.progress+" ramp="+ramp.status);
            require(world.getBlockState(SHARED).getBlock()!=block,"fully opened canopy releases empty helper cell");
            require(canopy.boxes().stream().noneMatch(box->box.intersects(new AxisAlignedBB(SHARED).shrink(.000001))),"released shared cell has no actual opened canopy shell");
            if(order==0)scanAndParking(world,actor,canopy);
            signal(ramp,canopy,false,order,source);
            require(canopy.progress==1,"canopy does not close immediately into extended ramp");
            for(int tick=0;tick<160&&!(canopy.progress==0&&!ramp.attached());tick++)advance(world,canopy,ramp,rampFirst);
            require(canopy.progress==0&&!ramp.attached(),"same-channel off restores tower before closed canopy reservation");
            require(world.getBlockState(RAMP.south()).getBlock()==Blocks.IRON_BLOCK&&world.getBlockState(RAMP.south().up()).getBlock()==Blocks.IRON_BLOCK,"two exact iron sources survive opening, closing and pending reload");
            require(TileEntityCanopy.at(world,SHARED)!=null&&TileEntityCanopy.at(world,SHARED).owner()==canopy,"closing reacquires only legitimate canopy helpers");
            // Foreign endpoint blocks must never be treated as transient canopy reservations.
            canopy.setChannelSignal(true);for(int tick=0;tick<32;tick++)advance(world,canopy,ramp,rampFirst);
            BlockPos obstacle=RAMP.south(2);require(world.isAirBlock(obstacle),"foreign obstruction fixture cell free");world.setBlockState(obstacle,Blocks.DIAMOND_BLOCK.getDefaultState(),3);
            ramp.setChannelSignal(true);for(int tick=0;tick<48;tick++)advance(world,canopy,ramp,rampFirst);
            require(!ramp.attached()&&world.getBlockState(obstacle).getBlock()==Blocks.DIAMOND_BLOCK&&world.getBlockState(RAMP.south().up()).getBlock()==Blocks.IRON_BLOCK,"genuine obstruction preserves foreign block and original tower");
            world.setBlockToAir(obstacle);ramp.setChannelSignal(false);
            // A placed block in released canopy space blocks closing without being overwritten.
            require(world.isAirBlock(SHARED),"opened helper space accepts ordinary placement");world.setBlockState(SHARED,Blocks.GOLD_BLOCK.getDefaultState(),3);canopy.setChannelSignal(false);
            canopy.readFromNBT(canopy.writeToNBT(new NBTTagCompound()));
            for(int tick=0;tick<32;tick++)advance(world,canopy,ramp,rampFirst);
            require(canopy.progress==1&&world.getBlockState(SHARED).getBlock()==Blocks.GOLD_BLOCK,"blocked close and pending reload preserve foreign placement");
            world.setBlockToAir(SHARED);for(int tick=0;tick<64&&canopy.progress!=0;tick++)advance(world,canopy,ramp,rampFirst);
            require(canopy.progress==0&&world.getBlockState(RAMP.south()).getBlock()==Blocks.IRON_BLOCK,"cleared close obstacle retries without losing tower");
            RedstoneChannels.unregister(source);
        }
        System.out.println("[vandorlabs][reprolab] shared-canopy-moat PASS exact-saved-cabin shared-channel real-bus-dispatch both-callback-orders rapid-cancellation release-empty-space wait-open retract-before-close source-NBT pending-reload foreign-obstacles safe-retry");
        }finally{RedstoneChannels.unregister(source);clear(world);world.setTotalWorldTime(time);actor.setPosition(before.x,before.y,before.z);}
    }
    private static void scanAndParking(World world,EntityPlayerMP actor,TileEntityCanopy canopy){
        BlockPos seat=CANOPY.west(3);for(int dx=0;dx<3;dx++)for(int dz=0;dz<3;dz++)world.setBlockState(seat.add(dx,-1,dz),Blocks.STONE.getDefaultState(),3);
        world.setBlockState(CANOPY.west(),Blocks.STONE.getDefaultState(),3);
        world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),3);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat,world.getBlockState(seat),actor,new ItemStack(ModBlocks.PILOT_SEAT));
        world.setBlockState(seat.down().north(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),3);world.setBlockState(seat.down().south(3),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),3);
        Block gear=null;for(Block block:Block.REGISTRY)if(block instanceof BlockTelescopicLandingGear && !(block instanceof com.vandorlabs.blocks.BlockNonWheeledLandingGear)){gear=block;break;}require(gear!=null,"vehicle support fixture available");
        for(int dx=0;dx<3;dx++){BlockPos at=seat.add(dx,-2,0);world.setBlockState(at,gear.getDefaultState(),3);((TileEntityLandingGear)world.getTileEntity(at)).configure(0,0,0,0);}
        VehicleScan scan=new VehicleScan(world,seat);for(int n=0;n<300&&!scan.step(256);n++){}
        require(scan.result!=null,"opened released canopy scans with actual retained helpers: "+scan.error);
        require(scan.result.at(CANOPY.subtract(scan.origin))!=null&&scan.result.at(SHARED.subtract(scan.origin))==null,"vehicle captures open canopy without reoccupying released shared cell");
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(VehicleStructure.read(scan.result.write()));craft.setPosition(scan.origin.getX(),scan.origin.getY(),scan.origin.getZ());
        try{VehicleRotation.Plan parking=VehicleRotation.plan(craft);require(parking.structure.at(SHARED.subtract(parking.origin))==null,"parking snapshot preserves released canopy space");
            TileEntityCanopy loaded=(TileEntityCanopy)craft.view.getTileEntity(CANOPY.subtract(scan.origin));require(loaded.progress==1&&loaded.hasReleasedCells(),"captured canopy save/reload retains open released ownership");
            VehicleStructure rotated=craft.structure;for(int turn=0;turn<4;turn++)rotated=VehicleRotation.rotate(rotated,new VehicleWorld(world,rotated),1);
            require(rotated.write().equals(craft.structure.write()),"four vehicle turns preserve released canopy ownership and source settings");
        }finally{craft.setDead();}
    }
    private static void advance(World world,TileEntityCanopy canopy,TileEntityRampController ramp,boolean rampFirst){world.setTotalWorldTime(world.getTotalWorldTime()+1);if(rampFirst){ramp.scheduledTick();CanopyPreparationRuntimeChecks.await(canopy);canopy.update();}else{CanopyPreparationRuntimeChecks.await(canopy);canopy.update();ramp.scheduledTick();}}
}

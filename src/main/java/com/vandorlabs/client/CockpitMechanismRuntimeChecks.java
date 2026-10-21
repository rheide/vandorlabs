package com.vandorlabs.client;

import com.vandorlabs.cockpit.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

final class CockpitMechanismRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("cockpit mechanism: "+message);}
    static void run(WorldServer world,EntityPlayerMP player,TileEntityCockpit tile){
        if(tile.design().equals("chair_siderests")){
            CockpitData original=CockpitData.get(tile.design());
            for(int height=0;height<4;height++){
                com.vandorlabs.container.ContainerCockpit dialog=new com.vandorlabs.container.ContainerCockpit(tile,null);require(dialog.canInteractWith(player),"height dialog reachable");
                NBTTagCompound casing=new NBTTagCompound();casing.setInteger("Height",height);casing.setInteger("SideTexture",tile.sideTexture());dialog.update(player,0,casing);
                NBTTagCompound pilot=new NBTTagCompound();pilot.setBoolean("Pilot",true);pilot.setBoolean("Taxi",false);pilot.setInteger("Trigger",tile.getRedstoneMode());pilot.setString("Channels",tile.getRedstoneChannels().toString());for(int mode=0;mode<3;mode++)pilot.setString("Mode"+mode,tile.modeChannels(mode).toString());dialog.update(player,0,pilot);
                dialog=new com.vandorlabs.container.ContainerCockpit(tile,null);require(dialog.tile.getHeight()==height,"height survives pilot page save and dialog reopening");double drop=(6-height*2)/16D;
                require(Math.abs(tile.data().seat.y-(original.seat.y-drop))<1e-8,"cushion height in two-pixel steps");
                require(Math.abs(tile.data().surfaces.get(0).origin.y-(original.surfaces.get(0).origin.y-drop))<1e-8,"armrest touch height follows seat");
                require(Math.abs(tile.seatHeight()-(original.riderY+.725-drop))<1e-8,"rider follows cushion");
                TileEntityCockpit copy=(TileEntityCockpit)net.minecraft.tileentity.TileEntity.create(world,tile.writeToNBT(new NBTTagCompound()));require(copy.getHeight()==height,"height survives save");
                for(AxisAlignedBB b:tile.data().boxes)require(b.minY>=0&&b.maxY>b.minY,"lowered supports stay above floor");
            }
            tile.setHeight(0);return;
        }
        if(!tile.design().equals("cockpit"))return;
        CockpitActuator skew=new CockpitActuator();skew.toggle(100);require(skew.sample(98)==0&&skew.sample(115)>.4&&skew.sample(130)==1,"small client clock skew holds initial pose");
        net.minecraft.block.Block block=world.getBlockState(tile.getPos()).getBlock();
        require(block.getStateForPlacement(world,tile.getPos(),net.minecraft.util.EnumFacing.UP,0,0,0,0,player).getValue(com.vandorlabs.blocks.BlockVandorDirectional.FACING)==player.getHorizontalFacing(),"cockpit places along player facing");
        require(tile.data().moving.values().stream().filter(part->part==1).count()<=32,"rigid seat collision boxes are not sliced redundantly");
        levers(tile,player);
        NBTTagCompound before=tile.writeToNBT(new NBTTagCompound());
        tile.toggleScreens();require(tile.screenLift.target(),"screen button retracts");finish(tile,"CockpitScreenLift",world.getTotalWorldTime());require(Math.abs(tile.screens(0)-1)<1e-8,"screen bank reaches housing");
        TileEntityCockpit saved=(TileEntityCockpit)net.minecraft.tileentity.TileEntity.create(world,tile.writeToNBT(new NBTTagCompound()));saved.setWorld(world);require(saved.screenLift.target()&&saved.screens(0)==1,"screen pose persists");
        tile.readFromNBT(before);
        java.util.Set<BlockPos> rear=CockpitReservations.rear(tile,tile.facing());require(!rear.isEmpty(),"seat has a rear sweep");BlockPos obstruction=rear.iterator().next();
        world.setBlockState(obstruction,net.minecraft.init.Blocks.STONE.getDefaultState(),2);require(!tile.toggleSeat(player)&&!tile.seatSlide.target(),"occupied rear refuses extension");world.setBlockToAir(obstruction);
        require(tile.toggleSeat(player),"clear seat extension");for(BlockPos p:rear)require(CockpitAssembly.root(world,p)==tile,"rear reservation owned");
        finish(tile,"CockpitSeatSlide",world.getTotalWorldTime());require(Math.abs(tile.seatLocation().z-tile.data().seat.z-1)<1e-8,"seat travels one block backwards");levers(tile,player);
        require(tile.toggleSeat(player),"seat returns");finish(tile,"CockpitSeatSlide",world.getTotalWorldTime());CockpitReservations.tick(tile);for(BlockPos p:rear)require(world.isAirBlock(p),"rear cells released after return");
        tile.readFromNBT(before);
        com.vandorlabs.entity.EntityChairSeat seat=new com.vandorlabs.entity.EntityChairSeat(world,tile.getPos(),tile.seatHeight());
        io.netty.buffer.ByteBuf spawn=io.netty.buffer.Unpooled.buffer();seat.writeSpawnData(spawn);com.vandorlabs.entity.EntityChairSeat received=new com.vandorlabs.entity.EntityChairSeat(world);received.readSpawnData(spawn);spawn.release();require(tile.getPos().equals(received.getChairPos()),"mount spawn carries exact chair anchor");
        for(boolean returning:new boolean[]{false,true})for(int age=0;age<=30;age++){
            NBTTagCompound motion=before.copy(),state=new NBTTagCompound();state.setBoolean("Target",!returning);state.setDouble("From",returning?1:0);state.setLong("Started",world.getTotalWorldTime()-age);motion.setTag("CockpitSeatSlide",state);tile.readFromNBT(motion);
            for(float partial:new float[]{0,.25F,.5F,.75F}){Vec3d expected=CockpitData.rotate(tile.seatLocation(partial),tile.facing()).add(new Vec3d(tile.getPos()));Vec3d rider=received.cockpitRiderPosition(player,partial);require(Math.abs(expected.x-rider.x)<1e-8&&Math.abs(expected.z-rider.z)<1e-8,"rider stays on cushion throughout reversible motion");}
        }
        tile.readFromNBT(before);
    }
    private static void finish(TileEntityCockpit tile,String key,long now){NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.getCompoundTag(key).setLong("Started",now-CockpitActuator.DURATION);tile.readFromNBT(tag);}
    static void levers(TileEntityCockpit tile,EntityPlayerMP player){
        Vec3d eye=new Vec3d(tile.seatLocation().x,tile.data().riderY+player.getEyeHeight(),tile.seatLocation().z);
        for(java.util.Map.Entry<String,Vec3d> part:tile.data().pivots.entrySet())if(part.getKey().startsWith("throttle_"))for(int step=0;step<4;step++){
            TileEntityCockpit.Control c=tile.control(part.getKey());c.kind=1;c.level=step*5;double angle=Math.toRadians(22.5-step*22.5);
            Vec3d target=part.getValue().addVector(0,Math.cos(angle)*4.5/16,Math.sin(angle)*4.5/16+tile.seatSlide(0)),end=eye.add(target.subtract(eye).normalize().scale(8));
            require(tile.controlRay(CockpitData.rotate(eye,tile.facing()),CockpitData.rotate(end,tile.facing()))!=null,"actual grip selection ray");
            require(tile.use(CockpitData.rotate(eye,tile.facing()),CockpitData.rotate(end,tile.facing()),player),"seated lever ray");require(c.level==(step+1)%4*5,"seated ray selects correct lever "+part.getKey()+" step "+step);
        }
    }
    static void vehicle(EntityGroundVehicle craft,TileEntityCockpit tile,EntityPlayerMP player){
        if(player.getRidingEntity()!=craft)require(craft.board(player,craft.structure.seat),"pilot seated for mechanism check");
        tile.toggleScreens();require(tile.screenLift.target(),"server-owned vehicle screen button");tile.toggleScreens();
        levers(tile,player);Vec3d before=craft.toWorld(new Vec3d(tile.getPos()).add(tile.data().seat));
        require(tile.toggleSeat(player),"vehicle seat extends with clear sweep: "+tile.seatBlockReason);TileEntityCockpit moved=(TileEntityCockpit)craft.view.getTileEntity(craft.structure.seat);
        Vec3d after=craft.toWorld(new Vec3d(moved.getPos()).add(moved.data().seat));require(before.squareDistanceTo(after)<1e-8,"expanding vehicle preserves anchor world pose");
        require(moved.seatSlide.target(),"vehicle extension target survives snapshot");finish(moved,"CockpitSeatSlide",craft.world.getTotalWorldTime());
        for(BlockPos p:CockpitReservations.rear(moved,moved.facing()))require(CockpitAssembly.root(craft.view,p)==moved,"vehicle contains rear seat reservations");
        require(moved.toggleSeat(player),"vehicle seat returns");finish(moved,"CockpitSeatSlide",craft.world.getTotalWorldTime());craft.signals.cockpitChanged(moved);
        System.out.println("[vandorlabs][reprolab] cockpit-mechanisms PASS four heights seated-lever-rays reversible-screen-lift blocked-seat-sweep rear-reservations pose-preserving-vehicle-extension");
    }
}

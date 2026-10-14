package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Real-world route following, pulse latching, waypoint stops, corners and failsafes. */
final class AutopilotRuntimeChecks {
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException("autopilot: "+reason);}
    static void run(WorldServer world)throws Exception{
        BlockPos base=new BlockPos(720,100,48);
        for(BlockPos p:BlockPos.getAllInBox(base.add(-4,-1,-4),base.add(10,5,10)))world.setBlockToAir(p);
        for(int x=0;x<=3;x++)world.setBlockState(base.add(x,-1,0),Blocks.CONCRETE.getDefaultState(),2);
        for(int z=1;z<=3;z++)world.setBlockState(base.add(3,-1,z),Blocks.CONCRETE.getDefaultState(),2);
        BlockPos waypoint=base.add(3,-1,0);world.setBlockState(waypoint,ModBlocks.WAYPOINT.getDefaultState(),2);
        TileEntityWaypoint stop=(TileEntityWaypoint)world.getTileEntity(waypoint);stop.autoDepart=false;
        TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setPos(new BlockPos(0,1,0));
        TileEntityAutopilot autopilot=new TileEntityAutopilot();autopilot.setPos(BlockPos.ORIGIN);
        java.util.List<VehicleStructure.Cell> cells=Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.AUTOPILOT.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.EAST),autopilot.writeToNBT(new NBTTagCompound())),new VehicleStructure.Cell(new BlockPos(0,1,0),ModBlocks.PILOT_SEAT.getDefaultState(),seat.writeToNBT(new NBTTagCompound())));
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,new BlockPos(0,1,0),EnumFacing.EAST,1));craft.setPosition(base.getX(),base.getY(),base.getZ());
        TileEntityAutopilot controller=craft.autopilot.controller();controller.setChannelSignal(true);controller.setChannelSignal(false);
        require(controller.enabled(),"short pulse latches journey");
        for(int i=0;i<60;i++)craft.autopilot.tick();
        require(controller.enabled()&&controller.status.equals("Parked at waypoint")&&Math.abs(craft.posX-base.getX()-3)<.025,"pulse parks at waypoint without disabling");
        double parkedX=craft.posX;for(int i=0;i<10;i++)craft.autopilot.tick();require(craft.posX==parkedX,"disabled auto departure stays parked");
        NBTTagCompound waitingState=craft.autopilot.write();waitingState.setLong("Arrived",world.getTotalWorldTime()-40);craft.autopilot.read(waitingState);craft=reload(craft);controller=craft.autopilot.controller();craft.autopilot.tick();require(craft.autopilot.write().getLong("Arrived")==waitingState.getLong("Arrived")&&craft.autopilot.write().getLong("Waiting")==waypoint.toLong(),"waypoint arrival timer and parked state survive reload");
        stop.stopMode=2;stop.hoverHeight=8;craft.hover=true;craft.view.hover=true;craft.hoverTarget=base.getY()+8;craft.setPosition(craft.posX,base.getY()+8,craft.posZ);
        for(int i=0;i<10;i++)craft.autopilot.tick();require(controller.enabled()&&controller.status.equals("Parked at waypoint")&&craft.hover,"stopped Hover waypoint remains active above four blocks");
        craft.hover=false;craft.view.hover=false;craft.setPosition(craft.posX,base.getY(),craft.posZ);stop.stopMode=1;
        controller.setChannelSignal(true);for(int i=0;i<10;i++)craft.autopilot.tick();require(craft.rotationYaw>0&&craft.rotationYaw<90,"midcorner reload fixture");craft=reload(craft);controller=craft.autopilot.controller();for(int i=0;i<90;i++)craft.autopilot.tick();
        require(!controller.enabled()&&controller.status.equals("End of route")&&Math.abs(craft.posZ-base.getZ()-3)<.025,"held signal passes waypoint, turns corner and stops at route end");
        craft.rotationYaw=0;craft.setPosition(base.getX(),base.getY()+16,base.getZ());controller.setEnabled(true);craft.autopilot.tick();require(controller.enabled(),"route accepts exactly sixteen blocks of sensor clearance");
        controller.setEnabled(false);craft.autopilot.tick();world.setBlockState(base.add(0,-1,1),Blocks.CONCRETE.getDefaultState(),2);craft.rotationYaw=90;craft.setPosition(base.getX(),base.getY()+16,base.getZ());controller.setEnabled(true);craft.autopilot.tick();require(craft.posZ>base.getZ()+.01,"restart derives route direction from current facing");
        craft.setPosition(base.getX(),base.getY()+17,base.getZ());craft.autopilot.tick();require(!controller.enabled()&&controller.status.equals("Ground out of range"),"route disables above sixteen blocks of sensor clearance");
        TileEntityAnimatedScreenSelector screen=new TileEntityAnimatedScreenSelector();screen.setSurfaceTexture(0,TileEntityAnimatedScreenSelector.VEHICLE_SURFACE);NBTTagCompound saved=screen.writeToNBT(new NBTTagCompound());TileEntityAnimatedScreenSelector restored=new TileEntityAnimatedScreenSelector();restored.readFromNBT(saved);require(restored.hasVehicleStatus(0),"Vehicle Status screen choice persists");
        craft.setDead();for(BlockPos p:BlockPos.getAllInBox(base.add(-4,-1,-4),base.add(10,5,10)))world.setBlockToAir(p);
        modeChannels();blockWaypoint(world,base.add(40,0,0));
        com.vandorlabs.VandorLabs.logger.info("Autopilot runtime checks PASS: pulse, waypoint stop, held signal, corner, route end, clearance, status persistence, mode channels and Block departure");
    }
    private static EntityGroundVehicle reload(EntityGroundVehicle previous){
        previous.signals.persist();NBTTagCompound navigation=previous.autopilot.write();EntityGroundVehicle restored=new EntityGroundVehicle(previous.world);restored.install(previous.structure);restored.rotationYaw=previous.rotationYaw;restored.setPosition(previous.posX,previous.posY,previous.posZ);restored.hover=previous.hover;restored.view.hover=previous.hover;restored.hoverTarget=previous.hoverTarget;restored.autopilot.read(navigation);previous.setDead();return restored;
    }
    private static void modeChannels(){
        TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setModeChannels(0,com.vandorlabs.redstone.ChannelList.of(19001));seat.setModeChannels(1,com.vandorlabs.redstone.ChannelList.of(19002,19003));seat.setModeChannels(2,com.vandorlabs.redstone.ChannelList.of(19004));
        TileEntityPilotSeat restored=new TileEntityPilotSeat();restored.readFromNBT(seat.writeToNBT(new NBTTagCompound()));for(int i=0;i<3;i++)require(restored.modeChannels(i).equals(seat.modeChannels(i)),"separate mode channels survive save");
        io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{com.vandorlabs.network.MessagePilotModes sent=new com.vandorlabs.network.MessagePilotModes(BlockPos.ORIGIN,new com.vandorlabs.redstone.ChannelList[]{seat.modeChannels(0),seat.modeChannels(1),seat.modeChannels(2)});sent.toBytes(bytes);com.vandorlabs.network.MessagePilotModes received=new com.vandorlabs.network.MessagePilotModes();received.fromBytes(bytes);io.netty.buffer.ByteBuf roundtrip=io.netty.buffer.Unpooled.buffer();try{received.toBytes(roundtrip);bytes.readerIndex(0);require(bytes.equals(roundtrip),"all three mode channel lists round-trip in network packet");}finally{roundtrip.release();}}finally{bytes.release();}
    }
    private static void blockWaypoint(WorldServer world,BlockPos base)throws Exception{
        net.minecraft.entity.player.EntityPlayerMP owner=world.getMinecraftServer().getPlayerList().getPlayers().get(0);
        net.minecraft.block.Block gear=null;for(net.minecraft.block.Block b:net.minecraft.block.Block.REGISTRY)if(b instanceof BlockTelescopicLandingGear){gear=b;break;}require(gear!=null,"gear fixture");
        for(BlockPos p:BlockPos.getAllInBox(base.add(-3,-1,-3),base.add(6,5,6)))world.setBlockToAir(p);
        for(BlockPos offset:Arrays.asList(BlockPos.ORIGIN,new BlockPos(2,0,0),new BlockPos(0,0,2))){BlockPos at=base.add(offset);world.setBlockState(at,gear.getDefaultState(),2);((TileEntityLandingGear)world.getTileEntity(at)).configure(0,0,0,0);}
        for(BlockPos offset:Arrays.asList(new BlockPos(0,1,0),new BlockPos(0,1,1),new BlockPos(0,1,2)))world.setBlockState(base.add(offset),Blocks.STONE.getDefaultState(),2);
        world.setBlockState(base.add(2,1,0),net.minecraft.block.Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),2);
        for(BlockPos offset:Arrays.asList(new BlockPos(1,1,1),new BlockPos(2,1,1),new BlockPos(1,1,2)))world.setBlockState(base.add(offset),net.minecraft.block.Block.getBlockFromName("vandorlabs:antigravity_plate").getDefaultState(),2);
        BlockPos controllerPos=base.add(1,1,0),seatPos=base.up(2),waypointPos=base.add(1,-1,0);
        world.setBlockState(controllerPos,ModBlocks.AUTOPILOT.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.EAST),2);
        world.setBlockState(waypointPos,ModBlocks.WAYPOINT.getDefaultState(),2);TileEntityWaypoint waypoint=(TileEntityWaypoint)world.getTileEntity(waypointPos);waypoint.stopMode=0;waypoint.delay=0;
        world.setBlockState(seatPos,ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seatPos,ModBlocks.PILOT_SEAT.getDefaultState(),owner,new net.minecraft.item.ItemStack(ModBlocks.PILOT_SEAT));
        VehicleScan scan=new VehicleScan(world,seatPos);for(int i=0;i<100&&!scan.step(128);i++);require(scan.error==null&&scan.result!=null,"block waypoint fixture scan: "+scan.error);
        VehicleService.authorizeAssembly(owner,scan.result,scan.origin);EntityGroundVehicle craft=VehicleTransfer.assemble(world,scan.result,scan.origin,owner);
        modeChannelIntegration(world,craft,base);
        waypoint.stopMode=2;waypoint.hoverHeight=16;waypoint.autoDepart=false;
        craft.autopilot.controller().setEnabled(true);craft.autopilot.tick();require(craft.hover,"waypoint selects Hover with real lift blocks");
        double targetHeight=craft.hoverTarget;craft.setPosition(craft.posX,targetHeight,craft.posZ);for(int i=0;i<10;i++)craft.autopilot.tick();
        require(craft.autopilot.enabled()&&craft.autopilot.controller().status.equals("Parked at waypoint")&&craft.hoverTarget==targetHeight,"sixteen-block Hover stop with gear below sensor remains parked without resetting height or timer");
        require(Math.abs(craft.toWorld(new net.minecraft.util.math.Vec3d(craft.autopilot.controller().getPos())).y-waypointPos.getY()-1-16)<.001,"waypoint height measures sensor clearance above waypoint top");
        craft.setPosition(base.getX(),base.getY(),base.getZ());require(craft.setHover(false),"fixture descends to Taxi before Block stop");waypoint.stopMode=0;waypoint.autoDepart=true;
        craft.autopilot.tick();require(craft.isDead,"Block waypoint restores actual blocks");
        TileEntityAutopilot parked=(TileEntityAutopilot)world.getTileEntity(controllerPos);require(parked!=null&&parked.departureSeat!=null,"Block stop persists pending departure");
        NBTTagCompound saved=parked.writeToNBT(new NBTTagCompound());TileEntityAutopilot restored=new TileEntityAutopilot();restored.readFromNBT(saved);require(restored.departureSeat.equals(seatPos)&&owner.getUniqueID().equals(restored.departureOwner),"pending departure survives save");
        parked.departureAt=world.getTotalWorldTime();for(int i=0;i<100&&world.getTileEntity(controllerPos)==parked;i++)VehicleAutopilot.depart(parked);
        EntityGroundVehicle departed=null;for(EntityGroundVehicle candidate:VehicleLookup.loaded(world))if(candidate!=craft&&candidate.getEntityBoundingBox().grow(2).contains(new net.minecraft.util.math.Vec3d(base).addVector(1,1,1)))departed=candidate;
        require(departed!=null&&!departed.isDead&&departed.autopilot.enabled(),"timed Block waypoint reassembles with autopilot enabled");departed.setDead();
        for(BlockPos p:BlockPos.getAllInBox(base.add(-3,-1,-3),base.add(6,5,6)))world.setBlockToAir(p);
    }
    private static final class ModeSource extends net.minecraft.tileentity.TileEntity implements com.vandorlabs.redstone.RedstoneChannelMember {
        int channel=19202;boolean high;
        public net.minecraft.tileentity.TileEntity channelTile(){return this;}public int getRedstoneChannel(){return channel;}public void setRedstoneChannel(int value){channel=value;}public boolean hasLocalRedstoneSignal(){return high;}public void setChannelSignal(boolean ignored){}
        void power(boolean value){high=value;com.vandorlabs.redstone.RedstoneChannels.inputChanged(this);}
    }
    private static void modeChannelIntegration(WorldServer world,EntityGroundVehicle craft,BlockPos base){
        TileEntityPilotSeat seat=(TileEntityPilotSeat)craft.view.getTileEntity(craft.structure.seat);seat.setModeChannels(1,com.vandorlabs.redstone.ChannelList.of(19201));seat.setModeChannels(2,com.vandorlabs.redstone.ChannelList.of(19202));
        ModeSource source=new ModeSource();source.setWorld(world);source.setPos(base.add(5,1,5));com.vandorlabs.redstone.RedstoneChannels.register(source);
        try{
            source.power(true);int mode=seat.pollMode(world);require(mode==2,"rising Hover channel selects Hover");VehicleService.modeSignal(seat,craft,mode);VehicleService.tick(new net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER,net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END,world));require(craft.hover&&!craft.isDead,"Hover channel service queue selects actual flight mode");require(seat.pollMode(world)==-1,"held mode channel does not repeat");
            source.power(false);seat.pollMode(world);com.vandorlabs.redstone.RedstoneChannels.unregister(source);source.channel=19201;com.vandorlabs.redstone.RedstoneChannels.register(source);source.power(true);mode=seat.pollMode(world);require(mode==1,"rising Taxi channel selects Taxi");VehicleService.modeSignal(seat,craft,mode);VehicleService.tick(new net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER,net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END,world));require(!craft.hover&&!craft.isDead,"Taxi channel service queue returns actual craft to ground mode");
            com.vandorlabs.VandorLabs.logger.info("Vehicle mode channel integration PASS: Hover and Taxi rising edges, held signal suppression, server queue transitions");
        }finally{com.vandorlabs.redstone.RedstoneChannels.unregister(source);}
    }
}

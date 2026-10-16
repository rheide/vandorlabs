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
        java.util.List<VehicleStructure.Cell> cells=Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.AUTOPILOT.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.WEST),autopilot.writeToNBT(new NBTTagCompound())),new VehicleStructure.Cell(new BlockPos(0,1,0),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.FACING,EnumFacing.EAST),seat.writeToNBT(new NBTTagCompound())));
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
        craft.autopilot.pulseDeparture();require(!controller.powered(),"screen departure does not latch redstone high");for(int i=0;i<10;i++)craft.autopilot.tick();require(craft.rotationYaw>0&&craft.rotationYaw<90,"midcorner reload fixture");craft=reload(craft);controller=craft.autopilot.controller();for(int i=0;i<90;i++)craft.autopilot.tick();
        require(!controller.enabled()&&controller.status.equals("End of route")&&Math.abs(craft.posZ-base.getZ()-3)<.025,"held signal passes waypoint, turns corner and stops at route end");
        craft.rotationYaw=0;craft.setPosition(base.getX(),base.getY()+16,base.getZ());controller.setEnabled(true);craft.autopilot.tick();require(controller.enabled(),"route accepts exactly sixteen blocks of sensor clearance");
        controller.setEnabled(false);craft.autopilot.tick();world.setBlockState(base.add(0,-1,1),Blocks.CONCRETE.getDefaultState(),2);craft.rotationYaw=90;craft.setPosition(base.getX(),base.getY()+16,base.getZ());controller.setEnabled(true);craft.autopilot.tick();require(craft.posZ>base.getZ()+.01,"restart derives route direction from current facing");
        craft.setPosition(base.getX(),base.getY()+17,base.getZ());craft.autopilot.tick();require(!controller.enabled()&&controller.status.equals("Ground out of range"),"route disables above sixteen blocks of sensor clearance");
        TileEntityAnimatedScreenSelector screen=new TileEntityAnimatedScreenSelector();screen.setSurfaceTexture(0,TileEntityAnimatedScreenSelector.VEHICLE_SURFACE);NBTTagCompound saved=screen.writeToNBT(new NBTTagCompound());TileEntityAnimatedScreenSelector restored=new TileEntityAnimatedScreenSelector();restored.readFromNBT(saved);require(restored.hasVehicleStatus(0),"Vehicle Status screen choice persists");
        craft.setDead();for(BlockPos p:BlockPos.getAllInBox(base.add(-4,-1,-4),base.add(10,5,10)))world.setBlockToAir(p);
        headingAndLookahead(world,base.add(100,0,0));modeChannels();blockWaypoint(world,base.add(40,0,0));
        com.vandorlabs.VandorLabs.logger.info("Autopilot runtime checks PASS: pulse, waypoint stop, held signal, corner, route end, clearance, Pilot Seat heading, granular lookahead, waypoint boundary, collision, status persistence, mode channels and Block departure");
    }
    private static EntityGroundVehicle routeCraft(WorldServer world,BlockPos base,EnumFacing forward,float yaw){return routeCraft(world,base,forward,yaw,false);}
    private static EntityGroundVehicle routeCraft(WorldServer world,BlockPos base,EnumFacing forward,float yaw,boolean asymmetric){
        TileEntityAutopilot tile=new TileEntityAutopilot();tile.setPos(BlockPos.ORIGIN);BlockPos seatPos=BlockPos.ORIGIN.up();TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setPos(seatPos);
        List<VehicleStructure.Cell> cells=new ArrayList<>(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.AUTOPILOT.getDefaultState().withProperty(BlockVandorDirectional.FACING,forward.getOpposite()),tile.writeToNBT(new NBTTagCompound())),new VehicleStructure.Cell(seatPos,ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.FACING,forward),seat.writeToNBT(new NBTTagCompound()))));if(asymmetric)cells.add(new VehicleStructure.Cell(new BlockPos(4,0,2),Blocks.IRON_BLOCK.getDefaultState(),null));
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,seatPos,forward,1));craft.rotationYaw=yaw;craft.setPosition(base.getX(),base.getY(),base.getZ());craft.autopilot.controller().setEnabled(true);return craft;
    }
    private static void clearRoute(WorldServer world,BlockPos base){for(BlockPos p:BlockPos.getAllInBox(base.add(-12,-1,-12),base.add(12,5,12)))world.setBlockToAir(p);}
    private static void marker(WorldServer world,BlockPos p){world.setBlockState(p,Blocks.CONCRETE.getDefaultState(),2);}
    private static net.minecraft.util.math.Vec3d sensor(EntityGroundVehicle craft){return craft.toWorld(new net.minecraft.util.math.Vec3d(craft.autopilot.controller().getPos()).addVector(.5,0,.5));}
    private static void headingAndLookahead(WorldServer world,BlockPos base)throws Exception{
        for(EnumFacing forward:EnumFacing.HORIZONTALS){
            clearRoute(world,base);for(int step=0;step<=3;step++)marker(world,base.down().offset(forward,step));EntityGroundVehicle craft=routeCraft(world,base,forward,0);
            try{craft.autopilot.tick();require(sensor(craft).subtract(new net.minecraft.util.math.Vec3d(base).addVector(.5,0,.5)).dotProduct(new net.minecraft.util.math.Vec3d(forward.getDirectionVec()))>.01,"oppositely facing Autopilot follows Pilot Seat forward "+forward);for(int n=0;n<50&&craft.autopilot.enabled();n++)craft.autopilot.tick();require(!craft.autopilot.enabled()&&craft.autopilot.controller().status.equals("End of route")&&sensor(craft).distanceTo(new net.minecraft.util.math.Vec3d(base.offset(forward,3)).addVector(.5,0,.5))<.025,"cardinal Pilot Seat route completes forward "+forward);}finally{craft.setDead();}
        }
        clearRoute(world,base);for(int step=0;step<=3;step++)marker(world,base.down().offset(EnumFacing.EAST,step));EntityGroundVehicle rotated=routeCraft(world,base,EnumFacing.NORTH,90);try{rotated.autopilot.tick();require(rotated.posX>base.getX()+.01&&Math.abs(rotated.rotationYaw-90)<.001,"rotated Pilot Seat forward agrees with manual drive");}finally{rotated.setDead();}
        // A staircase remains a face-connected route. Steering anticipates its
        // average direction without ever moving the sensor across diagonal gaps.
        for(EnumFacing forward:EnumFacing.HORIZONTALS){
            clearRoute(world,base);List<BlockPos> route=new ArrayList<>();BlockPos at=base.down();route.add(at);marker(world,at);EnumFacing side=forward.rotateY();
            for(int i=0;i<8;i++){at=at.offset(forward);route.add(at);marker(world,at);at=at.offset(side);route.add(at);marker(world,at);}
            BlockPos stopPos=route.get(8);world.setBlockState(stopPos,ModBlocks.WAYPOINT.getDefaultState(),2);TileEntityWaypoint waypoint=(TileEntityWaypoint)world.getTileEntity(stopPos);waypoint.stopMode=1;waypoint.autoDepart=false;
            EntityGroundVehicle craft=routeCraft(world,base,forward,0,true);require(craft.structure.bounds.maxX==5&&craft.structure.bounds.maxZ==3,"sensor is displaced two blocks from the asymmetric hull pivot");int granularMoving=0;Set<Integer> headings=new HashSet<>();boolean restored=false;
            try{
                for(int n=0;n<320&&!craft.autopilot.controller().status.equals("Parked at waypoint");n++){
                    net.minecraft.util.math.Vec3d before=sensor(craft);float yaw=craft.rotationYaw;craft.autopilot.tick();net.minecraft.util.math.Vec3d after=sensor(craft);
                    require(craft.autopilot.enabled(),"stair-step route stays enabled before waypoint "+forward+" status="+craft.autopilot.controller().status);
                    require(route.contains(new BlockPos(after.x,base.getY()-1,after.z)),"lookahead sensor stays over exact marked cells "+forward+" at="+after);
                    require(Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(craft.rotationYaw-yaw))<=3.001,"lookahead yaw change is bounded");
                    if(after.distanceTo(before)>.001&&Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(craft.rotationYaw)%90)>.5){granularMoving++;headings.add(Math.round(craft.rotationYaw));}
                    if(n==0)require(after.distanceTo(before)>.01&&craft.rotationYaw>0&&craft.rotationYaw<90,"lookahead steers and advances together instead of stopping for a cardinal turn");
                    if(n==20){craft=reload(craft);restored=true;}
                }
                require(restored&&granularMoving>20&&headings.size()>8,"staircase has granular moving headings and survives midroute reload "+forward);
                require(craft.autopilot.controller().status.equals("Parked at waypoint")&&sensor(craft).distanceTo(new net.minecraft.util.math.Vec3d(stopPos).addVector(.5,1,.5))<.025,"lookahead reaches exact waypoint center "+forward);
                net.minecraft.util.math.Vec3d stopped=sensor(craft);for(int n=0;n<10;n++)craft.autopilot.tick();require(sensor(craft).distanceTo(stopped)<1e-8,"lookahead never crosses stopped waypoint");
                craft.autopilot.controller().setChannelSignal(true);for(int n=0;n<320&&craft.autopilot.enabled();n++)craft.autopilot.tick();require(!craft.autopilot.enabled()&&craft.autopilot.controller().status.equals("End of route")&&sensor(craft).distanceTo(new net.minecraft.util.math.Vec3d(route.get(route.size()-1)).addVector(.5,1,.5))<.025,"held signal resumes staircase and stops at exact route end "+forward);
            }finally{craft.setDead();}
        }
        clearRoute(world,base);for(int i=0;i<=4;i++)marker(world,base.down().east(i));marker(world,base.down().east(2).north());EntityGroundVehicle fork=routeCraft(world,base,EnumFacing.EAST,0);try{for(int n=0;n<80&&fork.autopilot.enabled();n++)fork.autopilot.tick();require(!fork.autopilot.enabled()&&fork.autopilot.controller().status.equals("Ambiguous route")&&Math.abs(fork.posX-base.getX()-2)<.025,"lookahead cannot select or cut across a fork");}finally{fork.setDead();}
        clearRoute(world,base);for(int i=0;i<=3;i++)marker(world,base.down().east(i));world.setBlockState(base.east(),Blocks.STONE.getDefaultState(),2);EntityGroundVehicle blocked=routeCraft(world,base,EnumFacing.EAST,0);try{for(int n=0;n<40;n++)blocked.autopilot.tick();require(blocked.autopilot.enabled()&&blocked.autopilot.controller().status.equals("Blocked")&&sensor(blocked).x<base.getX()+1,"lookahead preserves collision stop");require(world.getBlockState(base.east()).getBlock()==Blocks.STONE,"blocking world terrain remains intact");}finally{blocked.setDead();clearRoute(world,base);}
        clearRoute(world,base);for(int i=0;i<=3;i++)marker(world,base.down().east(i));BlockPos bodyObstacle=base.add(5,0,2);world.setBlockState(bodyObstacle,Blocks.STONE.getDefaultState(),2);EntityGroundVehicle asymmetric=routeCraft(world,base,EnumFacing.EAST,0,true);try{for(int n=0;n<12;n++)asymmetric.autopilot.tick();require(asymmetric.autopilot.enabled()&&asymmetric.autopilot.controller().status.equals("Blocked")&&Math.abs(sensor(asymmetric).x-base.getX()-.5)<.001,"far asymmetric hull component stops against physical terrain while sensor route is clear");require(world.isAirBlock(base.east())&&world.getBlockState(bodyObstacle).getBlock()==Blocks.STONE,"physical hull obstacle stays intact outside sensor path");}finally{asymmetric.setDead();clearRoute(world,base);}
        unloadedLookaheadTarget(world);
        com.vandorlabs.VandorLabs.logger.info("Autopilot heading/lookahead PASS: four cardinal primary-seat directions, rotated facing, all-cardinal asymmetric-hull staircase granular moving yaw, exact off-pivot marker sensor, stopped waypoint, held departure, midroute save, fork, collision and unloaded saved-target no-load guard");
    }
    private static void unloadedLookaheadTarget(WorldServer world)throws Exception{
        BlockPos edge=null,target=null;EnumFacing direction=null;
        // Find a real loaded/unloaded boundary without requesting either chunk.
        outer:for(net.minecraft.world.chunk.Chunk chunk:world.getChunkProvider().getLoadedChunks())for(EnumFacing face:EnumFacing.HORIZONTALS){
            if(world.getChunkProvider().getLoadedChunk(chunk.x+face.getFrontOffsetX(),chunk.z+face.getFrontOffsetZ())!=null)continue;
            BlockPos candidate=new BlockPos((chunk.x<<4)+(face.getFrontOffsetX()>0?15:face.getFrontOffsetX()<0?0:8),100,(chunk.z<<4)+(face.getFrontOffsetZ()>0?15:face.getFrontOffsetZ()<0?0:8));
            if(!world.isBlockLoaded(candidate)||world.isBlockLoaded(candidate.offset(face)))continue;edge=candidate;target=candidate.offset(face);direction=face;break outer;
        }
        require(target!=null,"loaded world has an unloaded neighboring route boundary");int chunks=world.getChunkProvider().getLoadedChunkCount();EntityGroundVehicle craft=routeCraft(world,edge,direction,0);
        try{
            NBTTagCompound navigation=new NBTTagCompound();navigation.setInteger("Schema",1);navigation.setBoolean("Journey",true);navigation.setLong("Previous",edge.toLong());navigation.setLong("Target",target.toLong());navigation.setInteger("Direction",direction.getHorizontalIndex());craft.autopilot.read(navigation);require(craft.autopilot.write().getLong("Target")==target.toLong(),"saved target fixture remains initialized across unloaded boundary");
            java.lang.reflect.Method lookahead=VehicleAutopilot.class.getDeclaredMethod("lookahead",net.minecraft.util.math.Vec3d.class);lookahead.setAccessible(true);net.minecraft.util.math.Vec3d aim=(net.minecraft.util.math.Vec3d)lookahead.invoke(craft.autopilot,sensor(craft));
            require(aim.distanceTo(new net.minecraft.util.math.Vec3d(target).addVector(.5,0,.5))<1e-12,"lookahead stops before tile lookup on unloaded saved target");require(!world.isBlockLoaded(target)&&world.getChunkProvider().getLoadedChunkCount()==chunks,"lookahead never forces saved target chunk to load");
        }finally{craft.setDead();}
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
        for(BlockPos offset:Arrays.asList(BlockPos.ORIGIN,new BlockPos(2,0,0),new BlockPos(0,0,2))){BlockPos at=base.add(offset);world.setBlockState(at,gear.getDefaultState().withProperty(BlockTelescopicLandingGear.EXTENDED,true),2);((TileEntityLandingGear)world.getTileEntity(at)).configure(0,0,0,0);}
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
        craft.autopilot.tick();VehicleTransferRuntimeChecks.drain(world);require(craft.isDead,"Block waypoint restores actual blocks");
        TileEntityAutopilot parked=(TileEntityAutopilot)world.getTileEntity(controllerPos);require(parked!=null&&parked.departureSeat!=null,"Block stop persists pending departure");
        NBTTagCompound saved=parked.writeToNBT(new NBTTagCompound());TileEntityAutopilot restored=new TileEntityAutopilot();restored.readFromNBT(saved);require(restored.departureSeat.equals(seatPos)&&owner.getUniqueID().equals(restored.departureOwner),"pending departure survives save");
        parked.departureAt=Long.MAX_VALUE;parked.departureDirect=false;parked.pulseDeparture();require(parked.enabled()&&parked.departureDirect&&parked.departureAt==world.getTotalWorldTime()+1,"screen pulse reschedules already-enabled Block departure");parked.departureAt=world.getTotalWorldTime();for(int i=0;i<100&&world.getTileEntity(controllerPos)==parked;i++){VehicleAutopilot.depart(parked);VehicleTransferRuntimeChecks.drain(world);}
        EntityGroundVehicle departed=null;for(EntityGroundVehicle candidate:VehicleLookup.loaded(world))if(candidate!=craft&&candidate.getEntityBoundingBox().grow(2).contains(new net.minecraft.util.math.Vec3d(base).addVector(1,1,1)))departed=candidate;
        require(departed!=null&&!departed.isDead&&departed.autopilot.enabled(),"timed Block waypoint reassembles with autopilot enabled");departed.setDead();
        for(BlockPos p:BlockPos.getAllInBox(base.add(-3,-1,-3),base.add(6,5,6)))world.setBlockToAir(p);
    }
    private static final class ModeSource extends net.minecraft.tileentity.TileEntity implements com.vandorlabs.redstone.RedstoneChannelMember {
        public boolean isChannelMemberActive(){return getWorld()!=null&&!isInvalid();}
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

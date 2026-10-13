package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.tiles.CustomBlockMaterials;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Extended geometry, frozen belt phase, journals and exact transfer rollback. */
public final class VehicleEscalatorRuntimeChecks {
    private static String boxKey(net.minecraft.util.math.AxisAlignedBB b){return Math.round(b.minX*1e7)+","+Math.round(b.minY*1e7)+","+Math.round(b.minZ*1e7)+","+Math.round(b.maxX*1e7)+","+Math.round(b.maxY*1e7)+","+Math.round(b.maxZ*1e7);}
    private static List<String> geometry(net.minecraft.world.World world,Set<BlockPos> sections,BlockPos origin,double partial){
        List<String> result=new ArrayList<>();for(BlockPos pos:sections) {
            require(world.getTileEntity(pos) instanceof TileEntityControlledRamp,"posed section tile exists");
            for(net.minecraft.util.math.AxisAlignedBB box:((TileEntityControlledRamp)world.getTileEntity(pos)).boxes(partial))result.add(boxKey(box.offset(pos).offset(-origin.getX(),-origin.getY(),-origin.getZ())));
        }Collections.sort(result);return result;
    }
    private static List<String> rotateGeometry(VehicleWorld world,Set<BlockPos> sections,int depth){
        List<String> result=new ArrayList<>();for(BlockPos pos:sections)for(net.minecraft.util.math.AxisAlignedBB local:((TileEntityControlledRamp)world.getTileEntity(pos)).boxes(0)) {
            net.minecraft.util.math.AxisAlignedBB b=local.offset(pos);
            result.add(boxKey(new net.minecraft.util.math.AxisAlignedBB(depth-b.maxZ,b.minY,b.minX,depth-b.minZ,b.maxY,b.maxX)));
        }Collections.sort(result);return result;
    }
    private static String difference(WorldServer world,VehicleEscalators.WorldSnapshot snapshot){
        for(VehicleStructure.Cell cell:snapshot.structure.cells) {
            BlockPos p=snapshot.origin.add(cell.pos);
            if(world.getBlockState(p)!=cell.state)return "state at "+p;
            net.minecraft.tileentity.TileEntity tile=world.getTileEntity(p);
            NBTTagCompound expected=cell.tileData(),actual=tile==null?null:VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(snapshot.origin));
            if(!Objects.equals(expected,actual)) {
                if(expected==null || actual==null)return "tile presence at "+p;
                Set<String> keys=new TreeSet<>(expected.getKeySet());keys.addAll(actual.getKeySet());
                keys.removeIf(key->Objects.equals(expected.getTag(key),actual.getTag(key)));
                return "NBT at "+p+" keys="+keys+", running="+expected.getBoolean("EscalatorRunning")+"/"+actual.getBoolean("EscalatorRunning")+", phase="+expected.getDouble("EscalatorDistance")+"/"+actual.getDouble("EscalatorDistance");
            }
        }return "snapshot matches";
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("vehicle escalator: "+message);}
    private static final class Cancel {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public void spawn(net.minecraftforge.event.entity.EntityJoinWorldEvent event){if(event.getEntity() instanceof EntityGroundVehicle)event.setCanceled(true);}
    }
    private static final class CancelParking {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public void place(net.minecraftforge.event.world.BlockEvent.MultiPlaceEvent event){event.setCanceled(true);}
    }
    public static void run(WorldServer world){run(world,0,false);run(world,1,false);run(world,0,true);run(world,1,true);}
    private static void run(WorldServer world,int mode,boolean vanilla){try{
        BlockPos base=new BlockPos(320,110,200),seat=base.up(2),rampPos=base.add(2,2,0);
        for(BlockPos p:BlockPos.getAllInBox(base.add(-1,-1,-1),base.add(5,8,6)))world.setBlockToAir(p);
        EntityPlayerMP player=world.getMinecraftServer().getPlayerList().getPlayers().get(0);
        net.minecraft.util.math.Vec3d originalPosition=player.getPositionVector();boolean originallyFlying=player.capabilities.isFlying;
        player.capabilities.isFlying=true;player.setPosition(rampPos.getX()-2,rampPos.getY()+2,rampPos.getZ()+.5);
        BlockTelescopicLandingGear gear=null;for(net.minecraft.block.Block block:net.minecraft.block.Block.REGISTRY)if(block instanceof BlockTelescopicLandingGear){gear=(BlockTelescopicLandingGear)block;break;}
        require(gear!=null,"registered gear");for(int x=0;x<3;x++){BlockPos at=base.east(x);world.setBlockState(at,gear.getDefaultState(),2);require(((TileEntityLandingGear)world.getTileEntity(at)).configure(0,0,0,0),"retracted gear");}
        for(BlockPos p:BlockPos.getAllInBox(base.up(),base.add(3,1,2)))world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
        world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),2);world.setBlockState(seat.up(),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.UPPER,true),2);
        world.setBlockState(rampPos,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockRampController.FACING,EnumFacing.SOUTH),2);
        Map<BlockPos,NBTTagCompound> originalTiles=new LinkedHashMap<>();
        net.minecraft.block.Block sourceBlock=vanilla?Blocks.STONE:ModBlocks.PROGRAMMABLE_BLOCK;
        IBlockState sourceState=vanilla?Blocks.STONE.getDefaultState().withProperty(net.minecraft.block.BlockStone.VARIANT,net.minecraft.block.BlockStone.EnumType.ANDESITE):sourceBlock.getDefaultState();
        for(int z=1;z<=2;z++){
            BlockPos p=rampPos.south(z);world.setBlockState(p,sourceState,2);
            net.minecraft.tileentity.TileEntity raw=world.getTileEntity(p);
            if(raw instanceof TileEntityAnimatedScreenSelector)((TileEntityAnimatedScreenSelector)raw).setHousingTexture(CustomBlockMaterials.choice(new ItemStack(Blocks.GOLD_BLOCK)));
            originalTiles.put(p,raw==null?null:raw.writeToNBT(new NBTTagCompound()));
        }
        TileEntityRampController ramp=(TileEntityRampController)world.getTileEntity(rampPos);ramp.setOwner(player);
        require(ramp.configureHalfOffsets(player,0,4,8,true,false,false,EnumFacing.SOUTH,com.vandorlabs.ramp.RampGeometry.VERTICAL,false,1,true,false,0,mode),"ramp configures: "+ramp.status);
        ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);require(!ramp.isMoving(),"ramp settled: "+ramp.status);
        world.setBlockState(rampPos.north(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);ramp.updatePower();ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);ramp.refreshEscalatorActivity();
        require(ramp.attached() && ramp.isOpen() && !ramp.isMoving() && !ramp.error && (mode==0 || ramp.isEscalatorRunning()),"powered extended ramp before assembly: "+ramp.status);
        if(mode!=0)for(int n=0;n<7;n++){java.lang.reflect.Field tick=TileEntityRampController.class.getDeclaredField("lastStepTick");tick.setAccessible(true);tick.setLong(ramp,world.getTotalWorldTime()-1);ramp.scheduledTick();}
        Set<BlockPos> sections=new HashSet<>(ramp.vehicleCells());
        BlockPos remoteTerrain=Collections.max(sections,Comparator.comparingInt(BlockPos::getY)).east();
        require(world.isAirBlock(remoteTerrain),"remote terrain fixture is initially empty");world.setBlockState(remoteTerrain,Blocks.DIAMOND_BLOCK.getDefaultState(),2);
        VehicleScan scan=new VehicleScan(world,seat);for(int n=0;n<100 && !scan.step(64);n++);
        require(scan.error==null && scan.result!=null,"escalator assembly scan: "+scan.error);VehicleStructure craft=scan.result;require(craft.matches(world,scan.origin),"extended preview matches expanded world");
        for(BlockPos section:sections)require(craft.at(section.subtract(scan.origin))!=null && craft.at(section.subtract(scan.origin)).state.getBlock() instanceof BlockControlledRamp,"owned generated sections travel in snapshot");
        require(craft.at(remoteTerrain.subtract(scan.origin))==null,"owned ramp sections do not flood into neighboring terrain");
        world.setBlockToAir(remoteTerrain);
        VehicleWorld previewWorld=new VehicleWorld(world,craft);
        List<String> originalGeometry=geometry(world,sections,scan.origin,0);
        Set<BlockPos> localSections=new HashSet<>();for(BlockPos section:sections)localSections.add(section.subtract(scan.origin));
        require(originalGeometry.equals(geometry(previewWorld,localSections,BlockPos.ORIGIN,0)),"extended preview retains exact tread geometry");
        for(BlockPos source:originalTiles.keySet())if(world.isAirBlock(source))require(craft.at(source.subtract(scan.origin))==null,"vacated source air is journal-only, not a craft block");
        TileEntityRampController previewRamp=(TileEntityRampController)previewWorld.getTileEntity(rampPos.subtract(scan.origin));
        for(VehicleStructure.Cell source:VehicleEscalators.originals(previewRamp))require(source.state==sourceState && java.util.Objects.equals(source.tileData(),originalTiles.get(source.pos.add(scan.origin))==null?null:VehicleTileData.translated(originalTiles.get(source.pos.add(scan.origin)),BlockPos.ORIGIN.subtract(scan.origin))),"original source material and NBT retained in recovery journal");
        VehicleEscalators.WorldSnapshot expanded=VehicleEscalators.expandedWorldSnapshot(world,craft,scan.origin);
        require(VehicleStructure.read(craft.write()).write().equals(craft.write()),"frozen ramp snapshot survives recovery codec");
        require(VehicleStructure.read(expanded.structure.write()).write().equals(expanded.structure.write()),"live ramp source snapshot survives recovery codec");
        VehicleAuthorizationRuntimeChecks.check(player,craft,scan.origin,expanded,sections,rampPos.getY());
        boolean expandedRotationRejected=false;try{VehicleRotation.rotate(expanded.structure,new VehicleWorld(world,expanded.structure),1);}catch(IllegalArgumentException expected){expandedRotationRejected=true;}
        require(expandedRotationRejected,"expanded recovery journals cannot rotate as vehicle cells");
        Cancel cancel=new Cancel();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(cancel);boolean rejected=false;Exception cancellation=null;
        try{VehicleTransfer.assemble(world,craft,scan.origin);}catch(Exception expected){rejected=true;cancellation=expected;}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancel);}
        if(!(world.getTileEntity(rampPos) instanceof TileEntityRampController))throw new IllegalStateException("cancelled assembly failed to restore controller, mode="+mode+", vanilla="+vanilla+", "+difference(world,expanded),cancellation);
        require(rejected && !VehicleTransfer.pending(world) && expanded.structure.matches(world,expanded.origin),"cancelled assembly restores exact live snapshot before activity refresh, mode="+mode+", vanilla="+vanilla+", "+difference(world,expanded));
        ((TileEntityRampController)world.getTileEntity(rampPos)).refreshEscalatorActivity();
        require(rejected && !VehicleTransfer.pending(world) && expanded.structure.matches(world,expanded.origin),"cancelled assembly restores exact controller, sections and source journals");
        EntityGroundVehicle vehicle=VehicleTransfer.assemble(world,craft,scan.origin);
        for(BlockPos p:sections)require(world.isAirBlock(p),"no orphan section after assembly");
        TileEntityRampController paused=(TileEntityRampController)vehicle.view.getTileEntity(rampPos.subtract(scan.origin));require(paused.escalator==mode && paused.attached() && paused.isOpen() && !paused.isEscalatorRunning(),"vehicle ramp keeps extended pose with belt paused");
        require(originalGeometry.equals(geometry(vehicle.view,localSections,BlockPos.ORIGIN,.75)),"moving vehicle retains extended tread surfaces at every render partial tick");
        double frozenPhase=paused.beltDistance(0);paused.scheduledTick();paused.updatePower();paused.refreshEscalatorActivity();
        require(paused.beltDistance(200)==frozenPhase && originalGeometry.equals(geometry(vehicle.view,localSections,BlockPos.ORIGIN,200)),"vehicle clock and redstone callbacks cannot advance frozen tread geometry");
        NBTTagCompound before=vehicle.structure.write();for(int n=0;n<5;n++)vehicle.onUpdate();require(vehicle.structure.write().equals(before),"vehicle ticks never animate or mutate escalator");
        require(vehicle.structure.at(rampPos.north().subtract(scan.origin)).state.getBlock()==Blocks.REDSTONE_BLOCK,"power source travels with connected hull");
        VehicleStructure quarter=VehicleRotation.rotate(vehicle.structure,vehicle.view,1);
        Set<BlockPos> turnedSections=new HashSet<>();for(BlockPos section:localSections)turnedSections.add(VehicleRotation.cell(section,(int)vehicle.structure.bounds.maxX,(int)vehicle.structure.bounds.maxZ,1));
        require(rotateGeometry(vehicle.view,localSections,(int)vehicle.structure.bounds.maxZ).equals(geometry(new VehicleWorld(world,quarter),turnedSections,BlockPos.ORIGIN,0)),"quarter-turn rotates complete posed tread geometry");
        BlockPos turnedController=VehicleRotation.cell(rampPos.subtract(scan.origin),(int)vehicle.structure.bounds.maxX,(int)vehicle.structure.bounds.maxZ,1);
        require(EnumFacing.getHorizontal(quarter.at(turnedController).tileData().getInteger("RampDirection"))==net.minecraft.util.Rotation.CLOCKWISE_90.rotate(EnumFacing.SOUTH),"quarter-turn parked escalator uses rotated direction");
        VehicleStructure rotated=vehicle.structure;for(int n=0;n<4;n++)rotated=VehicleRotation.rotate(rotated,new VehicleWorld(world,rotated),1);require(rotated.write().equals(vehicle.structure.write()),"four rotations retain source NBT and escalator configuration");
        vehicle.setPosition(scan.origin.getX(),scan.origin.getY(),scan.origin.getZ());vehicle.stopForParking();vehicle.rotationYaw=90;
        VehicleRotation.Plan parking=VehicleRotation.plan(vehicle);
        CancelParking cancelParking=new CancelParking();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(cancelParking);boolean parkingRejected=false;java.util.UUID vehicleId=vehicle.getUniqueID();
        NBTTagCompound beforeParking=vehicle.structure.write();
        try{VehicleTransfer.park(world,vehicle,parking.origin,player,false);}catch(Exception expected){parkingRejected=true;}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancelParking);}
        vehicle=(EntityGroundVehicle)world.getEntityFromUuid(vehicleId);
        require(parkingRejected && vehicle!=null && !vehicle.isDead && vehicle.structure.write().equals(beforeParking) && !VehicleTransfer.pending(world),"cancelled parking restores complete paused vehicle and retires journal");
        for(VehicleStructure.Cell cell:parking.structure.cells)require(world.isAirBlock(parking.origin.add(cell.pos)),"cancelled parking removes every staged craft cell");
        for(BlockPos at:BlockPos.getAllInBox(base.add(-1,-1,-1),base.add(5,8,6)))require(!(world.getBlockState(at).getBlock() instanceof BlockControlledRamp),"cancelled parking leaves no newly generated escalator sections");
        vehicle.setPosition(vehicle.posX,vehicle.posY,vehicle.posZ);
        if(vanilla && mode==0){
            player.dismountRidingEntity();net.minecraft.util.math.Vec3d inside=vehicle.toWorld(new net.minecraft.util.math.Vec3d(1.5,2.1,1.5));player.setPosition(inside.x,inside.y,inside.z);
            require(vehicle.getEntityBoundingBox().grow(.01).contains(player.getPositionVector()),"nonriding actor fixture is inside craft bounds");
            VehicleTransfer.park(world,vehicle,parking.origin,player,true);
            require(player.getRidingEntity()==null && player.getPositionVector().squareDistanceTo(inside)<1e-12,"manual parking unexpectedly seats or moves a nonriding actor");
        }else if(vanilla && mode==1){
            player.dismountRidingEntity();require(vehicle.board(player),"driver boards before manual parking");
            VehicleTransfer.park(world,vehicle,parking.origin,player,true);
            require(player.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat && parking.origin.add(parking.structure.seat).equals(((com.vandorlabs.entity.EntityChairSeat)player.getRidingEntity()).getChairPos()),"existing driver is not transferred to the parked Pilot Seat");
            net.minecraft.entity.Entity parkedSeat=player.getRidingEntity();player.dismountRidingEntity();parkedSeat.setDead();
        }else VehicleTransfer.park(world,vehicle,parking.origin,null,false);
        Set<BlockPos> parkedSections=new HashSet<>();for(BlockPos section:turnedSections)parkedSections.add(parking.origin.add(section));
        require(geometry(new VehicleWorld(world,parking.structure),turnedSections,BlockPos.ORIGIN,0).equals(geometry(world,parkedSections,parking.origin,0)),"parking preserves extended posed geometry before world ticks");
        BlockPos parkedRamp=parking.origin.add(turnedController);
        ramp=(TileEntityRampController)world.getTileEntity(parkedRamp);ramp.onLoad();ramp.scheduledTick();ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
        require(ramp.escalator==mode && ramp.attached() && ramp.isOpen() && !ramp.error && !ramp.vehicleFrozen,"quarter-turn parked ramp remains extended and resumes world lifecycle: "+ramp.status);
        require(world.getBlockState(parkedRamp).getValue(BlockRampController.FACING)==net.minecraft.util.Rotation.CLOCKWISE_90.rotate(EnumFacing.SOUTH),"quarter-turn world controller points toward restored sources");
        List<VehicleStructure.Cell> parkedOriginals=VehicleEscalators.originals(ramp);
        require(parkedOriginals.size()==originalTiles.size(),"parked journal retains every source");
        require(ramp.recover(true),"cleanup expanded escalator");
        for(VehicleStructure.Cell source:parkedOriginals)require(world.getBlockState(source.pos)==source.state && java.util.Objects.equals(source.tileData(),world.getTileEntity(source.pos)==null?null:world.getTileEntity(source.pos).writeToNBT(new NBTTagCompound())),"retraction after rotated parking restores complete source state/NBT");
        for(BlockPos p:BlockPos.getAllInBox(base.add(-1,-1,-1),base.add(5,8,6)))world.setBlockToAir(p);
        player.capabilities.isFlying=originallyFlying;player.setPosition(originalPosition.x,originalPosition.y,originalPosition.z);
        System.out.println("[vandorlabs][reprolab] vehicle-ramp PASS mode="+mode+" vanilla="+vanilla+" extended-geometry frozen-phase journal-NBT exact-rollback no-orphans rotated-surfaces cancelled-parking extended-parking survival-realstate-upper-protection manual-park-actor-driver");
    }catch(Exception e){throw new IllegalStateException(e);}}
}

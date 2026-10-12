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

/** Exact rollback and paused source material/NBT transport for expanded escalators. */
public final class VehicleEscalatorRuntimeChecks {
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
        require(gear!=null,"registered gear");world.setBlockState(base,gear.getDefaultState(),2);require(((TileEntityLandingGear)world.getTileEntity(base)).configure(0,0,0,0),"retracted gear");
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
        Set<BlockPos> sections=new HashSet<>(ramp.vehicleCells());
        VehicleScan scan=new VehicleScan(world,seat);for(int n=0;n<100 && !scan.step(64);n++);
        require(scan.error==null && scan.result!=null,"escalator assembly scan: "+scan.error);VehicleStructure craft=scan.result;require(craft.matches(world,scan.origin),"virtual retracted preview matches expanded world");
        for(VehicleStructure.Cell cell:craft.cells)require(!(cell.state.getBlock() instanceof BlockControlledRamp),"no generated sections in snapshot");
        for(Map.Entry<BlockPos,NBTTagCompound> entry:originalTiles.entrySet()){VehicleStructure.Cell source=craft.at(entry.getKey().subtract(scan.origin));require(source!=null && source.state==sourceState,"ordinary source travels");require(java.util.Objects.equals(source.tileData(),entry.getValue()==null?null:VehicleTileData.translated(entry.getValue(),BlockPos.ORIGIN.subtract(scan.origin))),"source material and complete NBT preserved");}
        VehicleEscalators.WorldSnapshot expanded=VehicleEscalators.expandedWorldSnapshot(world,craft,scan.origin);
        VehicleAuthorizationRuntimeChecks.check(player,craft,scan.origin,expanded,sections,rampPos.getY());
        boolean expandedRotationRejected=false;try{VehicleRotation.rotate(expanded.structure,new VehicleWorld(world,expanded.structure),1);}catch(IllegalArgumentException expected){expandedRotationRejected=true;}
        require(expandedRotationRejected,"expanded recovery journals cannot rotate as vehicle cells");
        Cancel cancel=new Cancel();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(cancel);boolean rejected=false;
        try{VehicleTransfer.assemble(world,craft,scan.origin);}catch(Exception expected){rejected=true;}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancel);}
        require(world.getTileEntity(rampPos) instanceof TileEntityRampController,"cancelled assembly restores controller before refreshing activity");
        ((TileEntityRampController)world.getTileEntity(rampPos)).refreshEscalatorActivity();
        require(rejected && !VehicleTransfer.pending(world) && expanded.structure.matches(world,expanded.origin),"cancelled assembly restores exact controller, sections and source journals");
        EntityGroundVehicle vehicle=VehicleTransfer.assemble(world,craft,scan.origin);
        for(BlockPos p:sections)require(world.isAirBlock(p),"no orphan section after assembly");
        TileEntityRampController paused=(TileEntityRampController)vehicle.view.getTileEntity(rampPos.subtract(scan.origin));require(paused.escalator==mode && !paused.attached() && !paused.isEscalatorRunning(),"vehicle escalator paused without reservations");
        NBTTagCompound before=vehicle.structure.write();for(int n=0;n<5;n++)vehicle.onUpdate();require(vehicle.structure.write().equals(before),"vehicle ticks never animate or mutate escalator");
        require(vehicle.structure.at(rampPos.north().subtract(scan.origin)).state.getBlock()==Blocks.REDSTONE_BLOCK,"power source travels with connected hull");
        VehicleStructure quarter=VehicleRotation.rotate(vehicle.structure,vehicle.view,1);
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
        int restoredSources=0;for(BlockPos originalSource:originalTiles.keySet()){
            BlockPos turnedSource=VehicleRotation.cell(originalSource.subtract(scan.origin),(int)vehicle.structure.bounds.maxX,(int)vehicle.structure.bounds.maxZ,1);
            VehicleStructure.Cell cell=parking.structure.at(turnedSource);require(cell!=null && cell.state.getBlock()==sourceBlock,"rotated source cell retained");
            BlockPos at=parking.origin.add(cell.pos);restoredSources++;
            require(world.getBlockState(at)==cell.state && java.util.Objects.equals(world.getTileEntity(at)==null?null:world.getTileEntity(at).writeToNBT(new NBTTagCompound()),cell.tileData()==null?null:VehicleTileData.translated(cell.tileData(),parking.origin)),"quarter-turn parking restores source material NBT before redeploy");
        }
        require(restoredSources==originalTiles.size(),"quarter-turn retains all source blocks");
        BlockPos parkedRamp=parking.origin.add(turnedController);
        ramp=(TileEntityRampController)world.getTileEntity(parkedRamp);ramp.onLoad();ramp.scheduledTick();ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
        require(ramp.escalator==mode && ramp.attached() && ramp.isOpen() && !ramp.error,"quarter-turn parked escalator redeploys: "+ramp.status);
        require(world.getBlockState(parkedRamp).getValue(BlockRampController.FACING)==net.minecraft.util.Rotation.CLOCKWISE_90.rotate(EnumFacing.SOUTH),"quarter-turn world controller points toward restored sources");
        require(ramp.recover(true),"cleanup expanded escalator");for(BlockPos p:BlockPos.getAllInBox(base.add(-1,-1,-1),base.add(5,8,6)))world.setBlockToAir(p);
        player.capabilities.isFlying=originallyFlying;player.setPosition(originalPosition.x,originalPosition.y,originalPosition.z);
        System.out.println("[vandorlabs][reprolab] vehicle-ramp PASS mode="+mode+" vanilla="+vanilla+" virtual-preview exact-rollback no-orphans paused-source-NBT rotation cancelled-parking parked-redeploy survival-realstate-upper-protection manual-park-actor-driver");
    }catch(Exception e){throw new IllegalStateException(e);}}
}

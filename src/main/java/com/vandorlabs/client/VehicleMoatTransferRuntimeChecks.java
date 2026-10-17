package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Atomic world transfer with a live high channel and ordinary intervening server ticks. */
final class VehicleMoatTransferRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(1240,100,240),RAMP=BASE.add(3,2,0),CONTROL=BASE.up(2),LEVER=BASE.west(10).up(2);
    private static final int CHANNEL=1879;
    private static int stage,ticks;private static long since;private static EntityGroundVehicle craft;private static BlockPos origin;
    private static final Set<BlockPos> removed=new LinkedHashSet<>();
    private static final Map<BlockPos,IBlockState> sources=new LinkedHashMap<>();
    private static final Map<BlockPos,NBTTagCompound> sourceTags=new LinkedHashMap<>();
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("Moat vehicle transfer: "+text);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static TileEntityRampController ramp(WorldServer world){return (TileEntityRampController)world.getTileEntity(RAMP);}
    private static void setup(Minecraft mc){
        WorldServer world=world(mc);EntityPlayerMP actor=actor(mc);actor.dismountRidingEntity();actor.setSneaking(false);
        for(BlockPos at:BlockPos.getAllInBox(BASE.add(-12,-2,-4),BASE.add(10,9,10))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
        for(BlockPos floor:BlockPos.getAllInBox(BASE.add(-2,-1,-2),BASE.add(9,-1,8)))world.setBlockState(floor,Blocks.STONE.getDefaultState(),2);
        actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();
        Block gear=null;for(Block candidate:Block.REGISTRY)if(candidate instanceof BlockTelescopicLandingGear && !(candidate instanceof com.vandorlabs.blocks.BlockNonWheeledLandingGear)){gear=candidate;break;}require(gear!=null,"gear registered");
        for(int x:new int[]{0,3,6}){world.setBlockState(BASE.east(x),gear.getDefaultState(),2);((TileEntityLandingGear)world.getTileEntity(BASE.east(x))).configure(0,0,0,0);}
        for(int x=0;x<=6;x++)for(int z=0;z<=1;z++)world.setBlockState(BASE.add(x,1,z),Blocks.IRON_BLOCK.getDefaultState(),2);
        world.setBlockState(CONTROL,ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),3);
        BlockPos seat=BASE.add(2,2,0);world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),3);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat,world.getBlockState(seat),actor,new ItemStack(ModBlocks.PILOT_SEAT));((TileEntityPilotSeat)world.getTileEntity(seat)).setTaxiMode(false);
        world.setBlockState(BASE.add(6,2,0),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),3);
        world.setBlockState(RAMP,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),3);
        for(int y=0;y<3;y++){BlockPos at=RAMP.south().up(y);world.setBlockState(at,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),3);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(at);tile.setHousingTexture(CustomBlockMaterials.choice(new ItemStack(Blocks.GOLD_BLOCK)));sources.put(at,world.getBlockState(at));sourceTags.put(at,tile.writeToNBT(new NBTTagCompound()));}
        actor.setPositionAndUpdate(RAMP.getX()+.5,RAMP.getY()+2,RAMP.getZ()-2);
        require(ramp(world).configureHalfOffsets(actor,0,0,8,true,false,false,EnumFacing.SOUTH,0,false,1,true,false,0,0,true),"native vertical Moat configured: "+ramp(world).status);
        world.setBlockState(LEVER.down(),Blocks.STONE.getDefaultState(),3);BlockIndustrialLever lever=(BlockIndustrialLever)Block.getBlockFromName("vandorlabs:industrial_power_lever");world.setBlockState(LEVER,lever.getDefaultState().withProperty(BlockIndustrialLever.FLOOR,true),3);
        TileEntityRedstoneChannel signal=(TileEntityRedstoneChannel)world.getTileEntity(LEVER);signal.setRedstoneChannels(ChannelList.of(CHANNEL));ramp(world).setRedstoneChannels(ChannelList.of(CHANNEL));signal.setLocalOn(true);
        require(RedstoneChannels.level(world,CHANNEL)==15,"real registered latch powers channel");RedstoneTransferBarrierRuntimeChecks.install(world,BASE,CHANNEL);actor.setPositionAndUpdate(BASE.getX()-5,BASE.getY()+6,BASE.getZ()-3);since=world.getTotalWorldTime();
    }
    private static void capture(Minecraft mc)throws Exception{
        WorldServer world=world(mc);TileEntityRampController ramp=ramp(world);require(ramp.isOpen()&&!ramp.isMoving()&&ramp.attached(),"normal world ticks reached deployed endpoint: "+ramp.status);
        require(ramp.writeToNBT(new NBTTagCompound()).getInteger("MoatGeometryRevision")==2,"native revision captured");
        VehicleScan scan=new VehicleScan(world,CONTROL);for(int n=0;n<300&&!scan.step(256);n++){}require(scan.result!=null,"actual connected scan: "+scan.error);origin=scan.origin;RedstoneTransferBarrierRuntimeChecks.cancelOccupiedAssembly(world,scan.result,origin,actor(mc));
        for(VehicleStructure.Cell cell:scan.result.cells)removed.add(origin.add(cell.pos));removed.addAll(ramp.vehicleCells());removed.addAll(sources.keySet());
        craft=VehicleTransfer.assemble(world,scan.result,origin);require(craft!=null&&!VehicleTransfer.pending(world),"journaled actual assembly completes");since=world.getTotalWorldTime();
    }
    private static void empty(WorldServer world){
        RedstoneTransferBarrierRuntimeChecks.check(world,false);
        for(BlockPos at:removed)require(world.isAirBlock(at)&&world.getTileEntity(at)==null,"captured footprint remains empty after normal ticks at "+at+" state="+world.getBlockState(at)+" tile="+world.getTileEntity(at));
        for(BlockPos at:BlockPos.getAllInBox(BASE.add(-3,-1,-3),BASE.add(10,9,10)))require(!(world.getTileEntity(at) instanceof TileEntityControlledRamp),"no out-of-footprint retract part resurrected: "+at);
        require(RedstoneChannels.level(world,CHANNEL)==15,"external latch stays high during transfer");
    }
    private static boolean elapsed(Minecraft mc,boolean animation)throws Exception{boolean[] ready={false};mc.getIntegratedServer().addScheduledTask(()->{ready[0]=world(mc).getTotalWorldTime()-since>(animation?ramp(world(mc)).durationTicks()+30:45);}).get();return ready[0];}
    static boolean tick(Minecraft mc)throws Exception{
        if(stage==5)return true;require(++ticks<1600,"timeout stage="+stage);
        if(stage==0){mc.getIntegratedServer().addScheduledTask(()->setup(mc)).get();stage=1;return false;}
        if(stage==1){boolean ready=elapsed(mc,true);if(!ready)return false;mc.getIntegratedServer().addScheduledTask(()->{try{capture(mc);}catch(Exception failure){throw new RuntimeException(failure);}}).get();stage=2;return false;}
        if(stage==2){boolean ready=elapsed(mc,false);if(!ready)return false;mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);empty(world);try{craft=RedstoneTransferBarrierRuntimeChecks.cancelParking(world,craft,origin,actor(mc));empty(world);VehicleTransfer.park(world,craft,origin,null,false);RedstoneTransferBarrierRuntimeChecks.check(world,true);}catch(Exception failure){throw new RuntimeException(failure);}require(!VehicleTransfer.pending(world),"actual parking retires journal");since=world.getTotalWorldTime();}).get();stage=3;return false;}
        if(stage==3){boolean ready=elapsed(mc,false);if(!ready)return false;mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);TileEntityRampController placed=ramp(world);require(placed!=null&&placed.attached(),"parked controller owns actual parts after normal ticks");RedstoneTransferBarrierRuntimeChecks.check(world,true);RedstoneTransferBarrierRuntimeChecks.allowOff();((TileEntityRedstoneChannel)world.getTileEntity(BASE.add(5,2,0))).setLocalOn(false);if(world.getTileEntity(LEVER) instanceof TileEntityRedstoneChannel)((TileEntityRedstoneChannel)world.getTileEntity(LEVER)).setLocalOn(false);since=world.getTotalWorldTime();}).get();stage=4;return false;}
        if(stage==4){boolean ready=elapsed(mc,true);if(!ready)return false;mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);require(ramp(world).recover(false),"parked source journal recovers");for(BlockPos at:sources.keySet()){require(world.getBlockState(at).equals(sources.get(at)),"exact source state recovered: "+at);TileEntity tile=world.getTileEntity(at);require(tile!=null&&sourceTags.get(at).equals(tile.writeToNBT(new NBTTagCompound())),"exact source material/settings NBT recovered: "+at);}for(BlockPos at:BlockPos.getAllInBox(BASE.add(-3,-1,-3),BASE.add(10,9,10)))require(!(world.getTileEntity(at) instanceof TileEntityControlledRamp),"no orphan after parked recovery: "+at);RedstoneTransferBarrierRuntimeChecks.finish(world);for(BlockPos at:BlockPos.getAllInBox(BASE.add(-12,-2,-4),BASE.add(10,9,10)))world.setBlockToAir(at);}).get();stage=5;System.out.println("[vandorlabs][reprolab] moat-vehicle-transfer PASS real-channel-high native-extended actual-scan journaled-assemble normal-world-ticks no-resurrection actual-park exact-source-NBT recovery");return true;}
        return false;
    }
}

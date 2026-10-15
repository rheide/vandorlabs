package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;

/** Native mixed receivers plus a bus observer that rejects intermediate transfer callbacks. */
final class RedstoneTransferBarrierRuntimeChecks {
    private static final List<Probe> probes=new ArrayList<>();private static final List<BlockPos> anchors=new ArrayList<>();
    private static BlockPos external,carriedDoor,carriedTrap,carriedLamp;private static int channel;private static boolean armed;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("redstone transfer barrier: "+why);}
    private static final class Probe implements RedstoneChannelMember {
        final TileEntity tile;int callbacks;Probe(TileEntity tile){this.tile=tile;}
        public TileEntity channelTile(){return tile;}public int getRedstoneChannel(){return channel;}public void setRedstoneChannel(int unused){}public boolean hasLocalRedstoneSignal(){return false;}
        public void setChannelSignal(boolean high){if(!armed)return;callbacks++;require(isChannelMemberActive(),"removed receiver notified");require(high,"intermediate OFF during sole-source handoff");require(!VehicleTransfer.isMutating(tile.getWorld()),"receiver notified during partial transfer");boolean any=false,all=true;for(BlockPos anchor:anchors){boolean present=!tile.getWorld().isAirBlock(anchor);any|=present;all&=present;}require(!any||all,"callback observes incomplete anchor footprint");for(BlockPos anchor:anchors){IBlockState state=tile.getWorld().getBlockState(anchor);if(state.getBlock().hasTileEntity(state))require(tile.getWorld().getTileEntity(anchor)!=null,"callback observes missing tile at "+anchor);}}
    }
    private static Block ordinaryDoor(){Block block=Block.getBlockFromName("vandorlabs:programmable_door");require(block instanceof BlockVandorDoor,"registered programmable door available");return block;}
    private static void door(WorldServer world,BlockPos at){Block door=ordinaryDoor();world.setBlockState(at.down(),Blocks.IRON_BLOCK.getDefaultState(),2);world.setBlockState(at,door.getDefaultState().withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.LOWER),2);world.setBlockState(at.up(),door.getDefaultState().withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);TileEntitySpaceDoor configured=(TileEntitySpaceDoor)world.getTileEntity(at);configured.configure(configured.getDesign(),configured.getDetail(),configured.isFramed(),configured.getSlideDirection(),configured.isMiddle(),configured.isSliding(),configured.hasHinges(),com.vandorlabs.persistence.SpaceDoorData.TRIGGER_REDSTONE_ON,configured.hasPanel());configured.setRedstoneChannels(ChannelList.of(channel));RedstoneChannels.register((TileEntitySlidingDoor)world.getTileEntity(at));}
    private static void consumers(WorldServer world,BlockPos at){door(world,at);BlockPos trap=at.east(),lamp=at.east(2);world.setBlockState(trap,ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState(),3);((TileEntityProgrammableTrapdoor)world.getTileEntity(trap)).setRedstoneChannels(ChannelList.of(channel));RedstoneChannels.register((TileEntityProgrammableTrapdoor)world.getTileEntity(trap));world.setBlockState(lamp,ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(),3);TileEntityProgrammableLight light=(TileEntityProgrammableLight)world.getTileEntity(lamp);light.configure(0,15,false,ChannelList.of(channel),0,1);RedstoneChannels.register(light);}
    static void install(WorldServer world,BlockPos base,int selectedChannel){
        armed=false;channel=selectedChannel;external=base.west(20).up(2);for(BlockPos at:BlockPos.getAllInBox(external.add(-2,-2,-2),external.add(5,4,2))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
        BlockIndustrialLever lever=(BlockIndustrialLever)Block.getBlockFromName("vandorlabs:industrial_power_lever");BlockPos latch=base.add(5,2,0);world.setBlockState(latch,lever.getDefaultState().withProperty(BlockIndustrialLever.FLOOR,true),3);TileEntityRedstoneChannel tile=(TileEntityRedstoneChannel)world.getTileEntity(latch);tile.setRedstoneChannels(ChannelList.of(channel));tile.setLocalOn(true);
        // Remove the independent source after registering the carried high latch: every transfer must preserve sole-source ownership.
        world.setBlockToAir(base.west(10).up(2));require(RedstoneChannels.level(world,channel)==15,"carried latch alone retains high channel");
        consumers(world,external);carriedDoor=base.add(0,2,1);carriedTrap=carriedDoor.east();carriedLamp=carriedDoor.east(2);consumers(world,carriedDoor);
        anchors.clear();anchors.add(base.up(2));anchors.add(base.add(2,2,0));anchors.add(base.add(6,2,0));anchors.add(carriedDoor);anchors.add(carriedDoor.up());anchors.add(carriedTrap);anchors.add(carriedLamp);anchors.add(latch);
        for(BlockPos at:new BlockPos[]{external,external.east(),external.east(2),carriedDoor,carriedTrap,carriedLamp}){Probe probe=new Probe(world.getTileEntity(at));probes.add(probe);RedstoneChannels.register(probe);}
        armed=true;check(world,true);
    }
    static void check(WorldServer world,boolean carried){require(world.getBlockState(external).getValue(BlockVandorDoor.OPEN),"external door remains open");require(world.getBlockState(external.east()).getValue(BlockProgrammableTrapdoor.OPEN),"external trapdoor remains open");require(world.getTileEntity(external.east(2)) instanceof TileEntityProgrammableLight&&((TileEntityProgrammableLight)world.getTileEntity(external.east(2))).isOn(),"external lamp stays lit");if(carried){require(world.getBlockState(carriedDoor).getValue(BlockVandorDoor.OPEN),"carried door remains open");require(world.getBlockState(carriedTrap).getValue(BlockProgrammableTrapdoor.OPEN),"carried trapdoor remains open");require(world.getTileEntity(carriedLamp) instanceof TileEntityProgrammableLight&&((TileEntityProgrammableLight)world.getTileEntity(carriedLamp)).isOn(),"carried lamp stays lit");}}
    public static final class CancelAssembly {boolean fired;@SubscribeEvent public void joined(net.minecraftforge.event.entity.EntityJoinWorldEvent event){if(event.getEntity() instanceof EntityGroundVehicle){fired=true;event.setCanceled(true);}}}
    static void cancelOccupiedAssembly(WorldServer world,VehicleStructure structure,BlockPos origin,EntityPlayerMP actor)throws Exception{
        BlockPos seat=origin.add(structure.seat);actor.setSneaking(false);actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);
        ModBlocks.PILOT_SEAT.onBlockActivated(world,seat,world.getBlockState(seat),actor,net.minecraft.util.EnumHand.MAIN_HAND,net.minecraft.util.EnumFacing.NORTH,.5F,.3F,.5F);
        require(actor.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat,"pilot occupies original world chair before rejected assembly");net.minecraft.entity.Entity originalChair=actor.getRidingEntity();
        VehicleEscalators.WorldSnapshot expanded=VehicleEscalators.expandedWorldSnapshot(world,structure,origin);
        Map<BlockPos,IBlockState> states=new LinkedHashMap<>();Map<BlockPos,net.minecraft.nbt.NBTTagCompound> tags=new LinkedHashMap<>();
        for(VehicleStructure.Cell cell:expanded.structure.cells){BlockPos at=expanded.origin.add(cell.pos);states.put(at,world.getBlockState(at));TileEntity tile=world.getTileEntity(at);if(tile!=null)tags.put(at,tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));}
        CancelAssembly cancel=new CancelAssembly();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(cancel);boolean rejected=false;
        try{VehicleTransfer.assemble(world,structure,origin);}catch(java.io.IOException expected){rejected=true;}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancel);}
        require(rejected&&cancel.fired,"actual vehicle spawn cancellation rejected occupied assembly");require(!VehicleTransfer.pending(world),"rejected assembly retires recovery journal");
        require(actor.getRidingEntity()==originalChair&&!originalChair.isDead,"rejected assembly remounts original pilot chair");
        for(BlockPos at:states.keySet()){require(world.getBlockState(at).equals(states.get(at)),"rejected assembly restores exact blockstate: "+at);TileEntity tile=world.getTileEntity(at);if(tags.containsKey(at))require(tile!=null&&tags.get(at).equals(tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound())),"rejected assembly restores exact tile NBT: "+at);else require(tile==null,"rejected assembly creates no stray tile: "+at);}
        check(world,true);actor.dismountRidingEntity();actor.setPositionAndUpdate(origin.getX()-4,origin.getY()+6,origin.getZ()-3);
        System.out.println("[vandorlabs][reprolab] occupied-assembly-rollback PASS actual-pilot-chair cancelled-vehicle-spawn remounted-same-chair exact-world-state source-NBT journal-retired");
    }
    public static final class CancelParking {boolean fired;@SubscribeEvent public void placed(BlockEvent.MultiPlaceEvent event){fired=true;event.setCanceled(true);}}
    static EntityGroundVehicle cancelParking(WorldServer world,EntityGroundVehicle craft,BlockPos origin,EntityPlayerMP actor)throws Exception{
        CancelParking cancel=new CancelParking();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(cancel);boolean rejected=false;UUID id=craft.getUniqueID();try{VehicleTransfer.park(world,craft,origin,actor,false);}catch(java.io.IOException expected){rejected=true;}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancel);}require(rejected&&cancel.fired,"actual protected placement cancels and rolls back");require(!VehicleTransfer.pending(world),"rollback retires journal");check(world,false);for(EntityGroundVehicle restored:VehicleLookup.loaded(world))if(restored.getUniqueID().equals(id)&&!restored.isDead)return restored;throw new IllegalStateException("rollback did not restore captured vehicle");
    }
    static void allowOff(){armed=false;}
    static void finish(WorldServer world){armed=false;for(Probe probe:probes)RedstoneChannels.unregister(probe);probes.clear();for(BlockPos at:BlockPos.getAllInBox(external.add(-2,-2,-2),external.add(5,4,2)))world.setBlockToAir(at);System.out.println("[vandorlabs][reprolab] redstone-transfer-barrier PASS mixed-door-trapdoor-lamp linked-latch sole-carried-source-handoff stable-external-high no-partial-callback no-detached-callback actual-assembly parking-cancel-journal-rollback");}
}

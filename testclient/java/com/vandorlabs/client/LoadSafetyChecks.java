package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.turntable.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Adversarial load boundaries: no chunk iteration side effects, no partial live snapshots. */
final class LoadSafetyChecks {
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("load safety: "+text);}
    static void run(){
        upgradeBoundaries();
        scanHoldBoundaries();
        com.vandorlabs.VandorLabs.logger=org.apache.logging.log4j.LogManager.getLogger("load-safety");
        List<TileEntity> tiles=Arrays.asList(new TileEntityCanopy(),new TileEntityAutopilot(),new TileEntityCockpit(),new TileEntityTokenSlot(),new TileEntitySpaceDoor());
        int cases=0;
        for(TileEntity tile:tiles){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            BlockPos pos=new BlockPos(8,80,8);
            if(tile instanceof TileEntityAutopilot){NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("AutopilotEnabled",true);ChannelData.write(tag,ChannelList.of(123));tile.readFromNBT(tag);}
            if(tile instanceof TileEntityCockpit){TileEntityCockpit cockpit=(TileEntityCockpit)tile;String first=cockpit.data().surfaces.get(0).id;cockpit.control(first+"/0").channels=ChannelList.of(123);}
            tile.setWorld(world);tile.setPos(pos);world.tiles.put(pos,tile);
            world.states.put(pos,Blocks.STONE.getDefaultState());
            world.tileLoadGuard=true;
            try{tile.onLoad();}finally{world.tileLoadGuard=false;}
            // Replacing/removing a tile before settlement must discard its queued side effects.
            tile.invalidate();world.tiles.remove(pos);DeferredTileLoad.flush(world);cases++;
        }
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        TileEntity tile=new TileEntityAnimatedScreenSelector();BlockPos pos=new BlockPos(8,80,8);tile.setWorld(world);tile.setPos(pos);world.tiles.put(pos,tile);
        int[] callbacks={0};DeferredTileLoad.schedule(tile,()->callbacks[0]++);DeferredTileLoad.flush(world);require(callbacks[0]==1,"installed deferred callback missing");
        DeferredTileLoad.schedule(tile,()->callbacks[0]++);world.tiles.put(pos,new TileEntityAnimatedScreenSelector());DeferredTileLoad.flush(world);require(callbacks[0]==1,"stale tile callback ran");
        for(float progress:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-100,100}){
            NBTTagCompound tag=new NBTTagCompound();tag.setFloat("Progress",progress);TileEntityCanopy canopy=new TileEntityCanopy();canopy.readFromNBT(tag);require(Float.isFinite(canopy.progress)&&canopy.progress>=0&&canopy.progress<=1,"invalid saved canopy pose");
        }
        failedSnapshots();
        recoveryConflicts();
        System.out.println("Load safety PASS deferred-tiles="+cases+" installed-and-stale-callbacks finite-poses retained-failed-snapshots");
    }
    private static void upgradeBoundaries(){
        // An external capability owns its state references, even when its payload
        // happens to resemble one of our portable snapshots or ramp tiles.
        NBTTagCompound tile=new NBTTagCompound();tile.setString("id","vandorlabs:programmable_ramp");
        tile.setString("OriginalState","vandorlabs:smoked_cockpit_glass");
        NBTTagCompound caps=new NBTTagCompound(),foreign=new NBTTagCompound();
        foreign.setString("id","vandorlabs:controlled_ramp");foreign.setString("SourceState","vandorlabs:pale_cyan_cockpit_glass");
        caps.setTag("external:opaque",foreign);tile.setTag("ForgeCaps",caps);
        NBTTagCompound original=tile.copy(),migrated=com.vandorlabs.persistence.RetiredCockpitGlass.tileData(tile);
        require(migrated.getCompoundTag("ForgeCaps").equals(caps),"glass upgrade changed opaque ForgeCaps");
        require(tile.equals(original),"glass upgrade mutated its source");
        require(migrated.getString("OriginalState").equals("minecraft:stained_glass[color=gray]"),"owned glass material did not migrate");
        require(com.vandorlabs.persistence.LegacyBlockStates.decode("missing_mod:unavailable_block")==null,"unknown saved material silently became air");
        require(com.vandorlabs.persistence.LegacyBlockStates.decode("minecraft:stone[unknown=true]")==null,"unknown saved material property accepted");
        require(com.vandorlabs.persistence.LegacyBlockStates.decode("minecraft:air")==Blocks.AIR.getDefaultState(),"explicit air material refused");
        System.out.println("Upgrade boundaries PASS opaque capabilities original NBT unknown block/property refusal explicit air");
    }
    private static final class HoldWorld extends NonRenderingChecks.MemoryWorld {
        int unloadedReads;BlockPos failure;
        HoldWorld(){super(false);chunkLimit=true;}
        @Override public TileEntity getTileEntity(BlockPos at){
            if(!isBlockLoaded(at)){unloadedReads++;return null;}
            if(at.equals(failure))throw new IllegalStateException("injected tile lookup failure");
            return super.getTileEntity(at);
        }
    }
    private static void scanHoldBoundaries(){
        try{
            HoldWorld indicatorWorld=new HoldWorld();TileEntityVehicleController indicator=new TileEntityVehicleController();
            indicator.setWorld(indicatorWorld);indicator.setPos(new BlockPos(32,80,0));
            indicator.setConverting(true);indicator.setConverting(false);
            require(indicatorWorld.stateReads==0&&indicatorWorld.updates==0,"conversion indicator read an unloaded source chunk");
            indicator.setPos(new BlockPos(0,80,0));indicatorWorld.tiles.put(indicator.getPos(),new TileEntityVehicleController());
            indicator.setConverting(true);require(indicatorWorld.updates==0,"replaced conversion indicator published through its replacement");
            Class<?> type=Class.forName("com.vandorlabs.vehicle.VehicleTransfer$SourceHold");
            java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(net.minecraft.world.World.class,VehicleStructure.class,BlockPos.class);constructor.setAccessible(true);
            java.lang.reflect.Field pausedField=VehicleTransfer.class.getDeclaredField("PAUSED_TILES");pausedField.setAccessible(true);
            @SuppressWarnings("unchecked") Map<TileEntity,Integer> paused=(Map<TileEntity,Integer>)pausedField.get(null);
            for(boolean unloaded:new boolean[]{false,true}){
                HoldWorld world=new HoldWorld();BlockPos at=new BlockPos(0,80,0),other=unloaded?at.east(32):at.east();
                TileEntity tile=new net.minecraft.tileentity.TileEntityFurnace();tile.setWorld(world);tile.setPos(at);world.tiles.put(at,tile);world.tickableTileEntities.add(tile);
                if(!unloaded)world.failure=other;
                VehicleStructure source=VehicleStructure.display(Arrays.asList(new VehicleStructure.Cell(at,Blocks.FURNACE.getDefaultState(),null),new VehicleStructure.Cell(other,Blocks.STONE.getDefaultState(),null)));
                boolean refused=false;AutoCloseable hold=null;
                try{hold=(AutoCloseable)constructor.newInstance(world,source,BlockPos.ORIGIN);}
                catch(java.lang.reflect.InvocationTargetException expected){require(expected.getCause() instanceof IllegalStateException||expected.getCause() instanceof IllegalArgumentException,"unexpected scan hold failure");refused=true;}
                finally{if(hold!=null)hold.close();}
                boolean leaked=paused.containsKey(tile);paused.remove(tile);
                require(refused&&world.unloadedReads==0,"scan hold read or accepted an unloaded source chunk");
                require(!leaked&&world.tickableTileEntities.contains(tile),"failed scan hold leaked a paused-tile lease");
            }
            HoldWorld world=new HoldWorld();BlockPos at=new BlockPos(0,80,0);
            TileEntity tile=new net.minecraft.tileentity.TileEntityFurnace();tile.setWorld(world);tile.setPos(at);world.tiles.put(at,tile);world.tickableTileEntities.add(tile);
            VehicleStructure source=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(at,Blocks.FURNACE.getDefaultState(),null)));
            AutoCloseable first=(AutoCloseable)constructor.newInstance(world,source,BlockPos.ORIGIN),second=(AutoCloseable)constructor.newInstance(world,source,BlockPos.ORIGIN);
            try{
                require(paused.get(tile)==2&&!world.tickableTileEntities.contains(tile),"overlapping scan holds did not pause once");
                first.close();first.close();require(paused.get(tile)==1&&!world.tickableTileEntities.contains(tile),"one scan hold released another owner's lease");
                second.close();second.close();require(!paused.containsKey(tile)&&Collections.frequency(world.tickableTileEntities,tile)==1,"completed scan holds did not resume exactly once");
            }finally{first.close();second.close();}
            System.out.println("Scan hold safety PASS unloaded source refused without reads tile-lookup failure leaves no pause lease nested leases resume once");
        }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}catch(Exception failure){throw new IllegalStateException(failure);}
    }
    private static final class FailingWorld extends NonRenderingChecks.MemoryWorld {
        boolean fail;
        FailingWorld(){super(false);}
        @Override public net.minecraft.world.storage.WorldInfo getWorldInfo(){if(fail)throw new IllegalStateException("injected view construction failure");return super.getWorldInfo();}
    }
    private static void recoveryConflicts(){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos origin=new BlockPos(8,80,8);
        world.setBlockState(origin,Blocks.CHEST.getDefaultState(),2);
        net.minecraft.tileentity.TileEntityChest chest=(net.minecraft.tileentity.TileEntityChest)world.getTileEntity(origin);
        chest.setInventorySlotContents(0,new net.minecraft.item.ItemStack(net.minecraft.init.Items.DIAMOND,7));
        NBTTagCompound data=VehicleTileData.translated(chest.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin));
        VehicleStructure snapshot=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),data)));
        require(recoveryAllowed(world,snapshot,origin),"unchanged saved chest rejected");
        chest.setInventorySlotContents(0,new net.minecraft.item.ItemStack(net.minecraft.init.Items.EMERALD,11));
        require(!recoveryAllowed(world,snapshot,origin),"newer chest contents could be overwritten");
        require(chest.getStackInSlot(0).getItem()==net.minecraft.init.Items.EMERALD&&chest.getStackInSlot(0).getCount()==11,"recovery check changed inventory");
        world.setBlockState(origin,Blocks.OBSIDIAN.getDefaultState(),2);require(!recoveryAllowed(world,snapshot,origin),"replacement block could be overwritten");
        world.setBlockState(origin,Blocks.AIR.getDefaultState(),2);require(recoveryAllowed(world,snapshot,origin),"empty interrupted transfer rejected");
        System.out.println("Recovery conflicts PASS unchanged-inventory changed-inventory replacement-block empty-cell");
    }
    private static boolean recoveryAllowed(net.minecraft.world.World world,VehicleStructure snapshot,BlockPos origin){
        try{
            java.lang.reflect.Method method=VehicleTransfer.class.getDeclaredMethod("requireUnchangedRecovery",net.minecraft.world.World.class,VehicleStructure.class,BlockPos.class);method.setAccessible(true);method.invoke(null,world,snapshot,origin);return true;
        }catch(java.lang.reflect.InvocationTargetException failure){if(failure.getCause() instanceof java.io.IOException)return false;throw new IllegalStateException(failure.getCause());}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    private static void failedSnapshots(){
        FailingWorld world=new FailingWorld();
        net.minecraftforge.registries.ForgeRegistry<net.minecraft.block.Block> registry=(net.minecraftforge.registries.ForgeRegistry<net.minecraft.block.Block>)net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS;
        boolean locked=registry.isLocked();registry.unfreeze();BlockPilotSeat seat=new BlockPilotSeat();registry.register(seat);if(locked)registry.freeze();ModBlocks.PILOT_SEAT=seat;
        VehicleStructure craft=new VehicleStructure(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,seat.getDefaultState(),null)),BlockPos.ORIGIN,EnumFacing.NORTH,1);
        NBTTagCompound original=craft.write(),save=new NBTTagCompound();save.setTag("Craft",original.copy());
        EntityGroundVehicle entity=new EntityGroundVehicle(world);
        world.fail=true;entity.readFromNBT(save);world.fail=false;
        require(entity.structure==null&&entity.view==null,"partial vehicle remained active");
        require(entity.writeToNBT(new NBTTagCompound()).getCompoundTag("Craft").equals(original),"failed vehicle lost original snapshot");
        entity.onUpdate();
        NBTTagCompound surface=new NBTTagCompound();surface.setInteger("Normal",EnumFacing.UP.getIndex());NBTTagList cells=new NBTTagList();cells.appendTag(new NBTTagLong(BlockPos.ORIGIN.toLong()));surface.setTag("Cells",cells);
        NBTTagCompound snapshot=new NBTTagCompound();snapshot.setInteger("Schema",1);snapshot.setInteger("Height",1);snapshot.setTag("Surface",surface);snapshot.setTag("Blocks",VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null))).write());
        save=new NBTTagCompound();save.setTag("Snapshot",snapshot.copy());EntityTurntable turntable=new EntityTurntable(world);
        world.fail=true;turntable.readFromNBT(save);world.fail=false;
        require(turntable.snapshot==null&&turntable.view==null,"partial turntable remained active");
        require(turntable.writeToNBT(new NBTTagCompound()).getCompoundTag("Snapshot").equals(snapshot),"failed turntable lost original snapshot");
    }
}

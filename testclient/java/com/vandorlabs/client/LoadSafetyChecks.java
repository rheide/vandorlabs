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

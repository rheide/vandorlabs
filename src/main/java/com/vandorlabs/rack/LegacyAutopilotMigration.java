package com.vandorlabs.rack;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.copier.CopyWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Same-cell compatibility conversion. Saved vehicle snapshots and journal preimages retain their original IDs. */
public final class LegacyAutopilotMigration {
    public static final String BACKUP="RackLegacyAutopilot";
    private LegacyAutopilotMigration() { }
    public static NBTTagCompound migrated(NBTTagCompound original) {
        if(!"vandorlabs:autopilot".equals(original.getString("id")))throw new IllegalArgumentException("Not a legacy Autopilot");
        TileEntityModuleRack rack=new TileEntityModuleRack();
        rack.setPos(new BlockPos(original.getInteger("x"),original.getInteger("y"),original.getInteger("z")));
        ItemStack module=new ItemStack(com.vandorlabs.items.ModItems.AUTOPILOT_MODULE);
        ModuleAutopilotData.legacy(module,original);
        ModuleAutopilotData.settings(module); // Refuse malformed old settings before replacing any physical block.
        if(!rack.insertModule(0,module,false))throw new IllegalStateException("Cannot prepare navigation module");
        NBTTagCompound result=rack.writeToNBT(new NBTTagCompound());result.setString("id","vandorlabs:module_rack");
        // The physical host owns the provider; the item carries only the previous controller's own-field backup.
        if(original.hasKey("ForgeCaps",10))result.setTag("ForgeCaps",original.getTag("ForgeCaps").copy());
        if(original.hasKey("ForgeData",10))result.setTag("ForgeData",original.getTag("ForgeData").copy());
        result.setTag(BACKUP,original.copy());
        return new LegacyAutopilotData(opaqueOwn(original),new NBTTagCompound()).preserve(result);
    }
    public static LegacyAutopilotData preservation(NBTTagCompound saved,NBTTagCompound nativeAtRead) {
        if(!saved.hasKey(BACKUP,10))return null;
        return new LegacyAutopilotData(opaqueOwn(saved),nativeAtRead);
    }
    private static NBTTagCompound opaqueOwn(NBTTagCompound saved) {
        NBTTagCompound opaque=saved.copy();
        for(String key:new String[]{"Items","RackHousing","RackEmit","ModuleMask","ModuleKinds","Revision","Insert0","Insert1","Insert2","Insert3","RackEpoch","RackTransmission",BACKUP})opaque.removeTag(key);
        return opaque;
    }
    public static TileEntityModuleRack convert(TileEntityAutopilot old) {
        if(old.getClass()!=TileEntityAutopilot.class||!(old.getWorld() instanceof WorldServer)
                ||old.isInvalid()||VehicleTransfer.isMutating(old.getWorld()))return null;
        WorldServer world=(WorldServer)old.getWorld();BlockPos pos=old.getPos();
        if(!world.isCallingFromMinecraftThread()||!world.isBlockLoaded(pos,false)||world.getTileEntity(pos)!=old)return null;
        IBlockState before=world.getBlockState(pos);
        if(before.getBlock()!=ModBlocks.AUTOPILOT)return null;
        VehicleStructure source,target;Map<BlockPos,TileEntity> prepared,rollbackPrepared;
        try {
            NBTTagCompound original=old.writeToNBT(new NBTTagCompound());
            net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:module_rack");
            if(!(block instanceof BlockModuleRack))return null;
            IBlockState after=block.getDefaultState().withProperty(BlockModuleRack.FACING,before.getValue(BlockVandorDirectional.FACING));
            NBTTagCompound next=migrated(original);
            source=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,before,
                    VehicleTileData.translated(original,BlockPos.ORIGIN.subtract(pos)))));
            target=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,after,
                    VehicleTileData.translated(next,BlockPos.ORIGIN.subtract(pos)))));
            rollbackPrepared=CopyWorld.prepare(world,source,pos);
            prepared=CopyWorld.prepare(world,target,pos);
        }catch(RuntimeException failure){com.vandorlabs.VandorLabs.logger.warn("Autopilot conversion preflight deferred at "+pos,failure);return null;}
        UUID actor=WorldActionAuthorization.actorId(old);
        try(AutoCloseable transaction=VehicleTransfer.atomic(world)) {
            if(world.getTileEntity(pos)!=old||world.getBlockState(pos)!=before)throw new IllegalStateException("Autopilot changed before conversion");
            CopyWorld.install(world,target,pos,prepared);
            TileEntityModuleRack installed=(TileEntityModuleRack)world.getTileEntity(pos);
            WorldActionAuthorization.grant(installed,actor);
            CopyWorld.notify(world,target,pos);
            return installed;
        }catch(Exception failure){
            try {CopyWorld.install(world,source,pos,rollbackPrepared);WorldActionAuthorization.grant(world.getTileEntity(pos),actor);CopyWorld.notify(world,source,pos);}
            catch(RuntimeException rollback){failure.addSuppressed(rollback);}
            com.vandorlabs.VandorLabs.logger.warn("Autopilot conversion deferred at "+pos,failure);return null;
        }
    }
}

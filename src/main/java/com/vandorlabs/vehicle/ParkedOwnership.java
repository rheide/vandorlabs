package com.vandorlabs.vehicle;

import com.vandorlabs.tiles.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.io.IOException;
import java.util.UUID;

/** Evidence that an interrupted parking operation already installed its complete native owner. */
final class ParkedOwnership {
    static boolean complete(WorldServer world,UUID id,VehicleStructure parked,BlockPos origin)throws IOException{
        if(!VehicleOwnership.retired(world,id)||world.getEntityFromUuid(id)!=null)return false;
        for(VehicleStructure.Cell cell:parked.cells){
            BlockPos at=origin.add(cell.pos);
            if(!world.isBlockLoaded(at))return false;
            net.minecraft.block.state.IBlockState state=world.getBlockState(at);
            if(state.getBlock()==cell.state.getBlock()&&state.getBlock() instanceof com.vandorlabs.blocks.BlockSignalControl)
                state=state.withProperty(com.vandorlabs.blocks.BlockVandorSwitch.ON,cell.state.getValue(com.vandorlabs.blocks.BlockVandorSwitch.ON));
            if(state!=cell.state){
                com.vandorlabs.VandorLabs.logger.debug("Park recovery at {} expected {} found {}",at,cell.state,state);
                return false;
            }
            TileEntity tile=world.getTileEntity(at);
            NBTTagCompound saved=VehiclePaintings.nativeTile(cell.tileData());
            if(tile==null||saved==null){if(tile!=null||saved!=null)return false;continue;}
            NBTTagCompound live=BuildingBlockCompat.canonical(cell.state,
                    VehicleTileData.translated(TransferNetworkCompat.write(tile),BlockPos.ORIGIN.subtract(origin)));
            if(saved.equals(live))continue;
            // Compare persistent identities, configuration, inventory and opaque mod data. The
            // live native owner keeps its current readings and fuel when this comparison passes.
            if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).maxwell()){
                NBTTagCompound oldFuel=saved.getCompoundTag("MaxwellFuel"),newFuel=live.getCompoundTag("MaxwellFuel");
                if(newFuel.getInteger("Amount")<0||newFuel.getInteger("Amount")>oldFuel.getInteger("Amount"))return false;
                if(!newFuel.hasNoTags()){
                    NBTTagCompound expected=oldFuel.copy();
                    expected.setInteger("Amount",newFuel.getInteger("Amount"));
                    if(!expected.equals(newFuel))return false;
                }
                saved.removeTag("MaxwellFuel");live.removeTag("MaxwellFuel");
            }
            runtimeFields(tile,saved);runtimeFields(tile,live);
            if(!saved.equals(live)){
                java.util.Set<String> changed=new java.util.TreeSet<>(saved.getKeySet());changed.addAll(live.getKeySet());
                changed.removeIf(key->java.util.Objects.equals(saved.getTag(key),live.getTag(key)));
                com.vandorlabs.VandorLabs.logger.debug("Park recovery at {} found changed {} fields {}",at,state.getBlock().getRegistryName(),changed);
                return false;
            }
        }
        NBTTagList paintings=VehiclePaintings.entries(parked);
        for(int i=0;i<paintings.tagCount();i++){
            NBTTagCompound data=paintings.getCompoundTagAt(i);
            BlockPos at=origin.add(data.getInteger("TileX"),data.getInteger("TileY"),data.getInteger("TileZ"));
            NBTTagList pos=data.getTagList("Pos",6);
            world.getChunkFromBlockCoords(new BlockPos(origin.getX()+pos.getDoubleAt(0),origin.getY()+pos.getDoubleAt(1),origin.getZ()+pos.getDoubleAt(2)));
            Entity entity=world.getEntityFromUuid(data.getUniqueId("UUID"));
            if(!(entity instanceof EntityPainting)||entity.isDead)return false;
            EntityPainting painting=(EntityPainting)entity;
            if(!at.equals(painting.getHangingPosition())||!painting.art.title.equals(data.getString("Motive"))
                    ||painting.facingDirection.getHorizontalIndex()!=data.getByte("Facing"))return false;
        }
        return true;
    }
    private static void runtimeFields(TileEntity tile,NBTTagCompound tag){
        if(tile instanceof TileEntityPilotSeat)VehicleSeatSettings.clearSignalEdges(tag);
        if(tile instanceof TileEntityVehicleController){
            tag.removeTag("VehicleWeight");tag.removeTag("VehiclePreview");
        }
        if(tile instanceof TileEntitySignalControl){
            tag.removeTag("LatchedChannels");tag.removeTag("LatchedLevels");tag.removeTag("LocalOn");
        }
        if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).maxwell()){
            tag.removeTag("NavigationScreenOn");tag.removeTag("MaxwellActiveLevel");tag.removeTag("MaxwellBurnUnits");
        }
        if(tile instanceof TileEntityAnimatedScreenSelector)for(int slot=0;slot<TileEntityAnimatedScreenSelector.REDSTONE_SLOTS;slot++){
            NBTTagList rows=tag.getCompoundTag(TileEntityAnimatedScreenSelector.redstoneTag(slot)).getTagList("Rows",10);
            for(int i=0;i<rows.tagCount();i++){
                NBTTagCompound row=rows.getCompoundTagAt(i);
                for(String key:new String[]{"Active","Levels","LatchedChannels","Level","PulseUntil"})row.removeTag(key);
            }
        }
    }
    private ParkedOwnership(){}
}

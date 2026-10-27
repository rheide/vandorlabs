package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.persistence.SaveSchema;
import com.vandorlabs.persistence.LegacyBlockStates;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Capture owned ramp geometry in place, freezing its clock while carried. */
public final class VehicleEscalators {
    private VehicleEscalators(){}
    public static boolean active(TileEntity tile){return tile instanceof TileEntityRampController;}
    public static NBTTagCompound paused(NBTTagCompound saved){
        NBTTagCompound tag=saved.copy();tag.setBoolean("VehicleRampFrozen",true);
        tag.setBoolean("EscalatorRunning",false);tag.setLong("EscalatorTick",0);
        // Generated ramp cells have no controller step clock. Do not introduce
        // fields that their tile factory cannot preserve during exact-data validation.
        if(tag.hasKey(SaveSchema.Ramp.LAST_STEP_TICK,4))tag.setLong(SaveSchema.Ramp.LAST_STEP_TICK,0);
        return tag;
    }
    /** Compatibility with snapshots produced by the earlier virtual-source format. */
    public static NBTTagCompound legacyPaused(NBTTagCompound saved){
        NBTTagCompound tag=saved.copy();
        tag.setTag(SaveSchema.Ramp.SOURCES,new NBTTagList());tag.setTag(SaveSchema.Ramp.CELLS,new NBTTagList());tag.setTag("RampSourceTiles",new NBTTagList());
        for(String key:new String[]{SaveSchema.Ramp.OPEN,SaveSchema.Ramp.MOVING,SaveSchema.Ramp.ERROR,SaveSchema.Ramp.RECOVERY_PENDING,SaveSchema.Ramp.SIGNAL_KNOWN,SaveSchema.Ramp.LATCHED,"EscalatorRunning"})tag.setBoolean(key,false);
        tag.setDouble(SaveSchema.Ramp.START_POSE,0);tag.setLong(SaveSchema.Ramp.START_TICK,0);tag.setLong(SaveSchema.Ramp.LAST_STEP_TICK,0);tag.setLong("EscalatorTick",0);tag.setDouble("EscalatorDistance",0);
        tag.setString(SaveSchema.Ramp.STATUS,"Ramp paused for vehicle");return tag;
    }
    private static BlockPos position(NBTTagCompound tag){return tag.hasKey(SaveSchema.Ramp.X)?new BlockPos(tag.getInteger(SaveSchema.Ramp.X),tag.getInteger(SaveSchema.Ramp.Y),tag.getInteger(SaveSchema.Ramp.Z)):BlockPos.fromLong(tag.getLong(SaveSchema.Ramp.POSITION));}
    public static List<VehicleStructure.Cell> originals(TileEntityRampController ramp){
        NBTTagCompound tag=ramp.writeToNBT(new NBTTagCompound());IBlockState state=LegacyBlockStates.decode(tag.getString(SaveSchema.Ramp.ORIGINAL_STATE));
        if(state==null)state=NBTUtil.readBlockState(tag.getCompoundTag(SaveSchema.Ramp.ORIGINAL));
        Map<BlockPos,NBTTagCompound> tiles=new HashMap<>();NBTTagList data=tag.getTagList("RampSourceTiles",10);
        for(int n=0;n<data.tagCount();n++){NBTTagCompound entry=data.getCompoundTagAt(n);tiles.put(position(entry),entry.getCompoundTag("Tile").copy());}
        List<VehicleStructure.Cell> cells=new ArrayList<>();for(BlockPos p:ramp.vehicleSources()){
            NBTTagCompound tile=tiles.get(p);IBlockState original=com.vandorlabs.compat.RampStairs.state(state,tile);
            cells.add(new VehicleStructure.Cell(p,original,original.getBlock().hasTileEntity(original)?tile:null));
        }return cells;
    }
    /** Return null for ordinary matching, or the virtual original for a saved escalator source. */
    public static VehicleStructure.Cell original(World world,VehicleStructure craft,BlockPos origin,BlockPos p){
        VehicleStructure.Cell savedCell=craft.at(p.subtract(origin));if(savedCell==null || savedCell.state.getBlock() instanceof BlockControlledRamp)return null;
        TileEntity raw=world.getTileEntity(p);
        if(raw instanceof TileEntityControlledRamp){
            TileEntityControlledRamp part=(TileEntityControlledRamp)raw;
            TileEntity owner=world.getTileEntity(part.controller);
            if(active(owner) && ((TileEntityRampController)owner).owns(part))return original(craft,origin,(TileEntityRampController)owner,p);
            return null;
        }
        if(!world.isAirBlock(p))return null;
        // Ordinary ramps vacate source cells as their treads rise. The controller
        // journal still owns their original material even when no generated cell remains.
        for(VehicleStructure.Cell cell:craft.cells)if(cell.state.getBlock() instanceof BlockRampController){
            TileEntity owner=world.getTileEntity(origin.add(cell.pos));
            if(active(owner)){
                VehicleStructure.Cell original=original(craft,origin,(TileEntityRampController)owner,p);
                if(original!=null)return original;
            }
        }
        return null;
    }
    private static VehicleStructure.Cell original(VehicleStructure craft,BlockPos origin,TileEntityRampController ramp,BlockPos p){
        VehicleStructure.Cell savedOwner=craft.at(ramp.getPos().subtract(origin));
        if(savedOwner==null || savedOwner.tileData()==null || savedOwner.tileData().getBoolean("VehicleRampFrozen"))return null;
        for(VehicleStructure.Cell cell:originals(ramp))if(cell.pos.equals(p))return cell;
        return null;
    }
    public static final class WorldSnapshot {public final VehicleStructure structure;public final BlockPos origin;WorldSnapshot(VehicleStructure s,BlockPos p){structure=s;origin=p;}}
    /** Thaw only the world copy after all cells have been placed atomically. */
    public static NBTTagCompound parked(NBTTagCompound saved,long tick){
        NBTTagCompound tag=saved.copy();
        if(tag.getBoolean("VehicleRampFrozen")) {
            tag.removeTag("VehicleRampFrozen");tag.setBoolean("EscalatorRunning",false);
            tag.setLong("EscalatorTick",tick);
            if(tag.hasKey(SaveSchema.Ramp.LAST_STEP_TICK,4))tag.setLong(SaveSchema.Ramp.LAST_STEP_TICK,tick);
            // Preserve the ramp's captured signal baseline. Its one-shot load
            // check already reevaluates local power, and unchanged power must
            // not issue another request or replace the captured ramp pose.
        }
        return tag;
    }
    /** The carry snapshot freezes clocks even for retracted ramps. Journal their
     * original live NBT as well, so a cancelled assembly restores every field. */
    public static boolean needsExpandedSnapshot(World world,VehicleStructure craft,BlockPos origin){for(VehicleStructure.Cell cell:craft.cells){TileEntity tile=world.getTileEntity(origin.add(cell.pos));if(active(tile))return true;}return false;}
    public static WorldSnapshot expandedWorldSnapshot(World world,VehicleStructure craft,BlockPos origin) throws java.io.IOException {
        LinkedHashSet<BlockPos> positions=new LinkedHashSet<>();
        for(VehicleStructure.Cell cell:craft.cells){BlockPos p=origin.add(cell.pos);positions.add(p);TileEntity tile=world.getTileEntity(p);if(active(tile))for(BlockPos section:((TileEntityRampController)tile).vehicleCells()){
            if(!world.isBlockLoaded(section))throw new IllegalArgumentException("Load all ramp chunks before assembly.");
            TileEntity part=world.getTileEntity(section);if(!(part instanceof TileEntityControlledRamp) || !((TileEntityRampController)tile).owns((TileEntityControlledRamp)part))throw new IllegalArgumentException("Ramp changed; inspect the craft again.");positions.add(section);
        }}
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;for(BlockPos p:positions){minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());}
        BlockPos expandedOrigin=new BlockPos(minX,minY,minZ);
        List<VehicleStructure.Cell> cells=new ArrayList<>();for(BlockPos p:positions){if(world.isAirBlock(p))continue;TileEntity tile=world.getTileEntity(p);cells.add(new VehicleStructure.Cell(p.subtract(expandedOrigin),world.getBlockState(p),tile==null?null:VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(expandedOrigin))));}
        VehicleStructure expanded=new VehicleStructure(cells,origin.add(craft.seat).subtract(expandedOrigin),craft.facing,craft.gearCount);expanded.encode();
        return new WorldSnapshot(expanded,expandedOrigin);
    }
}

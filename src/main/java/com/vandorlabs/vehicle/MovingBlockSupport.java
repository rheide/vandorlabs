package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import java.util.*;

/** One lossless block policy shared by vehicle, turntable and copier preflight. */
public final class MovingBlockSupport {
    private MovingBlockSupport(){}
    public static void validate(VehicleStructure source,VehicleWorld view){VehicleRotation.validate(source,view);}
    public static boolean supported(VehicleStructure.Cell cell,TileEntity tile) {
        if(cell.tileData()!=null && tile==null)return false;
        String domain=cell.state.getBlock().getRegistryName().getResourceDomain();
        String name=tile==null?"":tile.getClass().getName();
        // Malisis may replace vanilla registry entries while keeping the minecraft namespace.
        if((domain.equals("minecraft") || domain.equals("malisisdoors")) &&
                (name.equals("net.malisis.doors.tileentity.DoorTileEntity") || name.equals("net.malisis.doors.tileentity.TrapDoorTileEntity")))return true;
        if((domain.equals("minecraft")||domain.equals("malisisdoors"))&&tile==null&&upperDoor(cell.state)
                &&cell.state.getBlock().getClass().getName().equals("net.malisis.doors.block.Door"))return true;
        if(domain.equals("minecraft"))return cell.state.getBlock().getClass().getName().startsWith("net.minecraft.block.");
        if(domain.equals("vandorlabs")) {
            if(tile instanceof com.vandorlabs.gear.GearTileEntity){com.vandorlabs.gear.GearTileEntity gear=(com.vandorlabs.gear.GearTileEntity)tile;if(gear.hasRetainedPayload()&&gear.payload()==null||gear.payload()!=null&&gear.payload().hasOwnershipJournal())return false;}
            if(tile instanceof com.vandorlabs.surface.SurfaceController&&((com.vandorlabs.surface.SurfaceController)tile).surfaceLocked())return false;
            if(tile instanceof com.vandorlabs.copier.CopyControllerTile){com.vandorlabs.copier.CopyControllerTile controller=(com.vandorlabs.copier.CopyControllerTile)tile;if(controller.input!=null||controller.output!=null||controller.busy())return false;}
            if(tile instanceof TileEntityTurntableController){TileEntityTurntableController controller=(TileEntityTurntableController)tile;return controller.captureId==null&&controller.layout==null&&!com.vandorlabs.turntable.TurntableTransactions.busy(controller);}
            if(tile instanceof TileEntityTurntableSurface)return ((TileEntityTurntableSurface)tile).owner==null;
            if(tile instanceof TileEntityRampController){
                TileEntityRampController ramp=(TileEntityRampController)tile;
                return !ramp.isMoving() && !cell.tileData().getBoolean("RecoveryPending")
                        && (cell.tileData().getBoolean("VehicleRampFrozen") || !ramp.attached() && ramp.vehicleCells().isEmpty());
            }
            return true;
        }
        if(domain.equals("immersiveengineering")&&tile==null&&cell.state.getBlock().hasTileEntity(cell.state))return false;
        if(tile==null && !cell.hasTileData())return inertBuilding(cell.state)||simpleState(cell.state);
        if(domain.equals("immersiveengineering"))return name.equals("blusunrize.immersiveengineering.common.blocks.TileEntityIESlab")
                || name.equals("blusunrize.immersiveengineering.common.blocks.metal.TileEntityLantern")
                || name.equals("blusunrize.immersiveengineering.common.blocks.metal.TileEntityStructuralArm")
                || name.equals("blusunrize.immersiveengineering.common.blocks.metal.TileEntityLadder")
                || name.equals("blusunrize.immersiveengineering.common.blocks.metal.TileEntityCapacitorLV")
                || name.equals("blusunrize.immersiveengineering.common.blocks.metal.TileEntityCapacitorMV")
                || name.equals("blusunrize.immersiveengineering.common.blocks.metal.TileEntityCapacitorHV")
                ||name.equals("blusunrize.immersiveengineering.common.blocks.wooden.TileEntityWindmill")
                ||name.equals("blusunrize.immersiveengineering.common.blocks.wooden.TileEntityWatermill")
                ||name.equals("blusunrize.immersiveengineering.common.blocks.wooden.TileEntityWoodenPost")
                ||name.equals("blusunrize.immersiveengineering.common.blocks.wooden.TileEntityWallmount")
                ||name.equals("blusunrize.immersiveengineering.common.blocks.metal.TileEntityToolbox");
        if(domain.equals("mekanism"))return name.equals("mekanism.common.tile.TileEntityEnergyCube")||name.equals("mekanism.common.tile.TileEntityGlowPanel")||name.equals("mekanism.common.tile.TileEntityFluidTank")||name.equals("mekanism.common.tile.TileEntityBin")||name.equals("mekanism.common.tile.TileEntityGasTank")||name.equals("mekanism.common.tile.TileEntityPersonalChest");
        return domain.equals("malisisdoors") && tile==null && upperDoor(cell.state);
    }
    /** These connection flags are derived from neighbours, not private orientation/network data. */
    static boolean inertBuilding(IBlockState state){
        net.minecraft.block.Block block=state.getBlock();net.minecraft.util.ResourceLocation id=block.getRegistryName();
        if(id==null||block.hasTileEntity(state))return false;
        String type=block.getClass().getName();
        if(id.getResourceDomain().equals("immersiveengineering")){
            int meta=block.getMetaFromState(state);
            return (type.equals("blusunrize.immersiveengineering.common.blocks.metal.BlockMetalDecoration1")||type.equals("blusunrize.immersiveengineering.common.blocks.metal.BlockMetalDecoration0"))&&meta>=0&&meta<=7
                    ||type.equals("blusunrize.immersiveengineering.common.blocks.wooden.BlockWoodenDecoration")&&meta>=0&&meta<=1;
        }
        return id.getResourceDomain().equals("mekanism")&&(type.equals("mekanism.common.block.BlockPlasticFence")
                || block.getClass().getSuperclass().getName().equals("mekanism.common.block.BlockBasic")
                && (id.getResourcePath().equals("basicblock")||id.getResourcePath().equals("basicblock2")));
    }
    /** Tile-free building materials need no private NBT or external network relocation.
     * Directional states must demonstrate the expected quarter-turn, rather than
     * treating the default Block.withRotation no-op as a rotation implementation. */
    private static boolean simpleState(IBlockState source){
        boolean directional=false;
        IBlockState turned=source.withRotation(Rotation.CLOCKWISE_90);
        for(IProperty<?> property:source.getPropertyKeys()){
            Comparable<?> value=source.getValue(property);
            String name=property.getName();
            if(value instanceof EnumFacing){
                directional=true;
                if(turned.getValue(property)!=Rotation.CLOCKWISE_90.rotate((EnumFacing)value))return false;
            }else if(value instanceof EnumFacing.Axis){
                directional=true;
                EnumFacing.Axis axis=(EnumFacing.Axis)value;
                EnumFacing.Axis expected=axis==EnumFacing.Axis.X?EnumFacing.Axis.Z:axis==EnumFacing.Axis.Z?EnumFacing.Axis.X:axis;
                if(turned.getValue(property)!=expected)return false;
            }else {
                if(!Arrays.asList("variant","variation","type","color","half","shape","seamless","wet","snowy","decayable","check_decay").contains(name))return false;
                // A custom enum can hide compass directions in an otherwise
                // familiar property such as variant; do not mistake it for material data.
                for(Object allowed:property.getAllowedValues())if(allowed.toString().toLowerCase(Locale.ROOT).matches(".*(?:^|_)(?:north|south|east|west|x|z)(?:_|$).*"))return false;
            }
        }
        if(!directional&&!source.isFullCube()&&!(source.getBlock() instanceof net.minecraft.block.BlockSlab))return false;
        IBlockState cycle=source;
        for(int i=0;i<4;i++)cycle=cycle.withRotation(Rotation.CLOCKWISE_90);
        return cycle==source;
    }
    private static boolean upperDoor(IBlockState state) {
        return state.getBlock() instanceof net.minecraft.block.BlockDoor
                && state.getValue(net.minecraft.block.BlockDoor.HALF)==net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER;
    }
    public static void requireExactTileData(VehicleStructure.Cell cell,TileEntity tile){
        NBTTagCompound before=cell.tileData();
        if(before==null)return;
        if(tile==null)throw new IllegalArgumentException("Missing block data for "+cell.state.getBlock().getRegistryName());
        boolean carriedCapability=tile.getWorld() instanceof VehicleWorld
                && before.hasKey("ForgeCaps",10) && supported(cell,tile);
        NBTTagCompound after;
        try{after=tile.writeToNBT(new NBTTagCompound());}
        catch(LinkageError capabilityFailure){
            boolean serializingCapabilities=false;
            for(StackTraceElement frame:capabilityFailure.getStackTrace())
                if(frame.getClassName().equals("net.minecraftforge.common.capabilities.CapabilityDispatcher")
                        && frame.getMethodName().equals("serializeNBT"))serializingCapabilities=true;
            if(!carriedCapability||!serializingCapabilities)throw capabilityFailure;
            // The real tile already loaded this data. Keep its original opaque
            // capability tag in the immutable snapshot instead of requiring an
            // unrelated mod to serialize it inside the synthetic vehicle world.
            VehicleCompatibility.warn(cell.state.getBlock(),"synthetic capability check",capabilityFailure);
            return;
        }
        if(carriedCapability){before.removeTag("ForgeCaps");after.removeTag("ForgeCaps");}
        if(!before.equals(after))throw new IllegalArgumentException("Cannot preserve all block data for "+cell.state.getBlock().getRegistryName()+"; operation cancelled before changing the structure");
    }
}

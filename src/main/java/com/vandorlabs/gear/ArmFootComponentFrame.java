package com.vandorlabs.gear;

import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** The native four-cell footprint is independent of an arm's mounting direction. */
public final class ArmFootComponentFrame {
    public static final String KEY="ArmFootComponentFrame",SOURCE_KEY="GearSourceFootComponentFrame";
    private static final ThreadLocal<EnumFacing> PLACEMENT=new ThreadLocal<>(),FACING=new ThreadLocal<>();
    public static EnumFacing frame(IBlockAccess world,BlockPos pos){
        TileEntity tile=world==null?null:world.getTileEntity(pos);
        if(tile instanceof GearTileEntity)return ((GearTileEntity)tile).componentFrame();
        if(tile!=null&&world instanceof net.minecraft.world.WorldServer&&com.vandorlabs.copier.CopyWorld.frozen((net.minecraft.world.WorldServer)world,pos)){
            NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());if(!valid(data))throw new IllegalArgumentException("Unknown retained landing component frame");
            return data.hasKey(KEY,8)?EnumFacing.valueOf(data.getString(KEY)):EnumFacing.DOWN;
        }
        return EnumFacing.DOWN;
    }
    static EnumFacing frame(VehicleStructure.Cell cell){return cell.hasTileKey(KEY,8)?EnumFacing.valueOf(cell.tileString(KEY)):EnumFacing.DOWN;}
    static boolean valid(NBTTagCompound data){return valid(data,KEY)&&valid(data,SOURCE_KEY);}
    private static boolean valid(NBTTagCompound data,String key){if(!data.hasKey(key))return true;if(!data.hasKey(key,8))return false;try{EnumFacing.valueOf(data.getString(key));return true;}catch(IllegalArgumentException unknown){return false;}}

    /** DOWN remains absent so previous native snapshots retain their exact fields. */
    static void write(NBTTagCompound data,EnumFacing frame){if(frame==EnumFacing.DOWN)data.removeTag(KEY);else data.setString(KEY,frame.name());}
    /** New regular feet retain even DOWN explicitly: presence marks local-plane metadata. */
    static void writeFoot(NBTTagCompound data,net.minecraft.block.state.IBlockState state,EnumFacing frame){if(ArmLooseGearData.largeWheel(state))write(data,frame);else data.setString(KEY,frame.name());}
    public static BlockPos offset(EnumFacing frame,EnumFacing facing,int x,int z){return ArmOrientation.vector(frame,LargeGearBlock.offset(facing,x,z));}
    static EnumFacing placement(){EnumFacing frame=PLACEMENT.get();return frame==null?EnumFacing.DOWN:frame;}
    static boolean posedPlacement(){return PLACEMENT.get()!=null;}
    static EnumFacing placementFacing(){return FACING.get();}
    public static Placement placing(EnumFacing frame){return new Placement(frame,null);}
    public static Placement placing(EnumFacing frame,EnumFacing facing){if(facing==null||!facing.getAxis().isHorizontal())throw new IllegalArgumentException("Large Wheel facing must be horizontal in its own frame");return new Placement(frame,facing);}
    public static final class Placement implements AutoCloseable {
        private final EnumFacing previous,previousFacing;
        private boolean closed;
        private Placement(EnumFacing frame,EnumFacing facing){java.util.Objects.requireNonNull(frame);previous=PLACEMENT.get();previousFacing=FACING.get();PLACEMENT.set(frame);if(facing==null)FACING.remove();else FACING.set(facing);}
        @Override public void close(){if(closed)return;closed=true;if(previous==null)PLACEMENT.remove();else PLACEMENT.set(previous);if(previousFacing==null)FACING.remove();else FACING.set(previousFacing);}
    }
    private ArmFootComponentFrame(){}
}

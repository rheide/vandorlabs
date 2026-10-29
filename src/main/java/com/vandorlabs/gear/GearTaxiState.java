package com.vandorlabs.gear;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Only removed native arm sections are owned here; live wheels and tracks remain owned by the craft. */
public final class GearTaxiState {
    public static final String KEY="TaxiArm";
    private GearTaxiState(){}
    /** Assembly and retained-state loading must agree on eligible ground contacts. */
    public static boolean supportsFoot(com.vandorlabs.gear.core.GearTopology.FootKind kind){
        return kind==com.vandorlabs.gear.core.GearTopology.FootKind.WHEEL||kind==com.vandorlabs.gear.core.GearTopology.FootKind.TRACK;
    }
    public static void validate(NBTTagCompound state){
        for(String key:state.getKeySet())if(!java.util.Arrays.asList("Schema","Phase","Height","Amount","Level","Expand","Sections","Settle").contains(key))throw new IllegalArgumentException("Unknown Taxi arm recovery field");
        if(state.hasKey("Sections",10))for(String key:state.getCompoundTag("Sections").getKeySet())if(!key.equals("Offset")&&!key.equals("Blocks"))throw new IllegalArgumentException("Unknown retained arm section field");
        int settling=state.getInteger("Settle");if(settling<0||settling>12)throw new IllegalArgumentException("Invalid Taxi arm visual settling interval");
        int phase=state.getInteger("Phase"),amount=state.getInteger("Amount"),height=state.getInteger("Height");double level=state.getDouble("Level");
        if(state.getInteger("Schema")!=1||phase<0||phase>2||amount<1||height<2||height>8||amount>=height||!Double.isFinite(level)||level<0||level>amount)
            throw new IllegalArgumentException("Invalid retained Taxi arm state");
        if(phase==1){if(Math.abs(level-amount)>1e-7||!state.hasKey("Sections",10))throw new IllegalArgumentException("Incomplete retained Taxi arm sections");java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=ArmStoredSections.retained(state.getCompoundTag("Sections"));if(cells.size()!=amount)throw new IllegalArgumentException("Wrong retained Taxi arm material count");for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:cells)if(cell.pos.getX()!=0||cell.pos.getZ()!=0||cell.pos.getY()>-(height-amount)||cell.pos.getY()<=-height||cell.tileData().hasKey(KEY))throw new IllegalArgumentException("Invalid retained Taxi arm section position");}
        else if(state.hasKey("Sections"))throw new IllegalArgumentException("Taxi arm sections have two owners");
    }
    public static NBTTagCompound rotate(NBTTagCompound state,int turns,World parent){validate(state);NBTTagCompound copy=state.copy();if(copy.hasKey("Sections",10))copy.setTag("Sections",ArmStoredSections.rotate(copy.getCompoundTag("Sections"),turns,parent));return copy;}
}

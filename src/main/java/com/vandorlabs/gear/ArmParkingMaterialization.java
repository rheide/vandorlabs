package com.vandorlabs.gear;

import com.vandorlabs.gear.core.*;
import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Detached chosen-candidate composition. Eligibility queries never clone tile data or instantiate tiles. */
public final class ArmParkingMaterialization {
    private static boolean anyKey(VehicleStructure.Cell cell,String key){for(int type=1;type<=12;type++)if(cell.hasTileKey(key,type))return true;return false;}
    private static boolean section(GearTileEntity arm,VehicleStructure.Cell cell,BlockPos relative){
        ResourceLocation id=TileEntity.getKey(GearTileEntity.class);
        return id!=null&&cell.state.getBlock() instanceof GearBlock&&((GearBlock)cell.state.getBlock()).kind()==GearBlock.Kind.EXTENDER
                &&cell.hasTileData()&&cell.hasTileKey("x",99)&&cell.hasTileKey("y",99)&&cell.hasTileKey("z",99)&&cell.tileInteger("x")==cell.pos.getX()&&cell.tileInteger("y")==cell.pos.getY()&&cell.tileInteger("z")==cell.pos.getZ()&&id.toString().equals(cell.tileString("id"))&&!cell.tileBoolean("GearController")
                &&cell.hasTileKey("GearOwnerOffset",4)&&cell.tileLong("GearOwnerOffset")==BlockPos.ORIGIN.subtract(relative).toLong()
                &&!anyKey(cell,"ArmPayload")&&!anyKey(cell,"ArmStoredSections")&&!anyKey(cell,GearTaxiState.KEY);
    }
    /** Source canStore policy at the chosen pose: NEW displaced sections are admitted only at phase zero.
     * nativeSection positions are in the containing snapshot frame, not relative to the controller. */
    public static boolean mayDisplace(GearTileEntity arm,ArmParkingCandidates.Candidate selected,VehicleStructure.Cell nativeSection){
        if(arm==null||selected==null||nativeSection==null||selected.phase!=0||!arm.isOperational()||arm.assembly()==null||arm.assembly().kind()!=GearTopology.FootKind.NONE||arm.mode()!=GearMotion.Mode.EXTEND||arm.isSwingArmController())return false;
        BlockPos at=nativeSection.pos,relative=at.subtract(arm.getPos());
        return !at.equals(arm.getPos())&&arm.members().contains(at)&&!arm.columns().contains(relative)
                &&nativeSection.state.getBlock()==arm.getBlockType()&&section(arm,nativeSection,relative);
    }
    /** Previously proven stored ownership may remain under the selected payload at any legal phase.
     * storedSection positions are controller-relative, exactly as storedSectionCells() exposes them. */
    public static boolean mayRetainStored(GearTileEntity arm,ArmParkingCandidates.Candidate selected,VehicleStructure.Cell storedSection){
        if(arm==null||selected==null||storedSection==null||!arm.isOperational()||arm.storedSections==null||arm.mode()!=GearMotion.Mode.EXTEND||arm.isSwingArmController())return false;
        BlockPos relative=storedSection.pos;
        return !relative.equals(BlockPos.ORIGIN)&&arm.storedSections.at(relative)==storedSection&&arm.members().contains(arm.getPos().add(relative))&&!arm.columns().contains(relative)&&section(arm,storedSection,relative);
    }
    private ArmParkingMaterialization(){}
}

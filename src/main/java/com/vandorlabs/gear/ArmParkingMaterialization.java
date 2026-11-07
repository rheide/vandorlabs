package com.vandorlabs.gear;

import com.vandorlabs.gear.core.*;
import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import com.vandorlabs.vehicle.VehicleTileData;
import java.util.*;
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
                &&!anyKey(cell,"ArmPayload")&&!anyKey(cell,"ArmStoredSections")&&!anyKey(cell,GearTaxiState.KEY)&&!anyKey(cell,"GearParkingHold");
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
    /** A detached patch, not a whole-layout overlap verdict or an ownership publication. */
    public static final class Plan {
        public final List<VehicleStructure.Cell> payloadCells,restoredSectionCells;
        public final Set<BlockPos> removedSourcePositions;
        public final VehicleStructure.Cell updatedRootCell;
        public final boolean wholeLayoutValidationDeferred=true;
        private Plan(List<VehicleStructure.Cell> payload,List<VehicleStructure.Cell> restored,Set<BlockPos> removed,VehicleStructure.Cell root){
            payloadCells=Collections.unmodifiableList(new ArrayList<>(payload));restoredSectionCells=Collections.unmodifiableList(new ArrayList<>(restored));
            removedSourcePositions=Collections.unmodifiableSet(new LinkedHashSet<>(removed));updatedRootCell=root;
        }
        public NBTTagCompound rootData(){return updatedRootCell.tileData();}
    }
    /** Clones ownership data only for the selected candidate. No world access, factories or journals. */
    public static Plan prepare(ArmParkingSource source,ArmParkingCandidates.Candidate selected){
        if(source==null||!source.stillOwned())throw new IllegalArgumentException("Arm parking source changed");
        Plan plan=compose(source.source(),source.owner(),selected,source.payload());
        if(!source.stillOwned())throw new IllegalArgumentException("Arm parking source changed during composition");return plan;
    }
    public static Plan prepare(VehicleStructure source,GearTileEntity arm,ArmParkingCandidates.Candidate selected){
        if(source==null||arm==null||arm.payload()==null)throw new IllegalArgumentException("Arm snapshot unavailable");
        return compose(source,arm,selected,arm.payload().snapshotPlaced(arm,source));
    }
    private static Plan compose(VehicleStructure source,GearTileEntity arm,ArmParkingCandidates.Candidate selected,ArmPayload payload){
        if(source==null||arm==null||selected==null||!arm.isOperational()||arm.hasFailedArmSnapshot()||arm.payload()==null||arm.payload().hasOwnershipJournal())throw new IllegalArgumentException("Arm snapshot unavailable");
        VehicleStructure.Cell root=source.at(arm.getPos());if(root==null||!root.hasTileData()||root.state.getBlock()!=arm.getBlockType())throw new IllegalArgumentException("Arm root changed");
        ArmGridMapping mapping=payload.gridMapping(arm,selected.phase);
        if(mapping==null)throw new IllegalArgumentException("Selected arm phase is no longer grid aligned");
        for(VehicleStructure.Cell cell:payload.blocks.cells)if(!mapping.cell(cell.pos).equals(selected.mapping().cell(cell.pos)))throw new IllegalArgumentException("Selected arm mapping changed");
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)if(mapping.basis()[row*3+col]!=selected.basis(row,col))throw new IllegalArgumentException("Selected arm basis changed");
        ArmGridPlacement placement=payload.gridPlacement(arm,selected.phase);if(placement==null)throw new IllegalArgumentException("Selected arm tiles cannot be mapped");
        Set<BlockPos> removed=new LinkedHashSet<>(),occupied=new HashSet<>();
        if(payload.materialized())for(int i=0;i<payload.blocks.cells.size();i++){
            BlockPos at=payload.placedCellPosition(arm,i);VehicleStructure.Cell before=source.at(at);
            if(before==null||before.state!=payload.placedCellState(i)||!removed.add(at))throw new IllegalArgumentException("Materialized arm ownership changed");
        }
        for(VehicleStructure.Cell cell:placement.cells)if(!occupied.add(cell.pos)||cell.pos.equals(arm.getPos()))throw new IllegalArgumentException("Duplicate or root payload overlap");
        List<VehicleStructure.Cell> restored=new ArrayList<>(),stored=new ArrayList<>();Set<BlockPos> retained=new HashSet<>();
        for(VehicleStructure.Cell old:arm.storedSectionCells()){
            if(!mayRetainStored(arm,selected,old))throw new IllegalArgumentException("Stored arm ownership changed");
            BlockPos at=arm.getPos().add(old.pos);
            if(occupied.contains(at)){stored.add(old);retained.add(at);}else{
                restored.add(new VehicleStructure.Cell(at,old.state,VehicleTileData.translated(old.tileData(),arm.getPos())));
            }
        }
        for(VehicleStructure.Cell cell:placement.cells){VehicleStructure.Cell before=source.at(cell.pos);
            if(before==null||removed.contains(cell.pos)||retained.contains(cell.pos))continue;
            if(arm.columns().contains(before.pos.subtract(arm.getPos())))throw new IllegalArgumentException("Payload overlaps an arm column");
            if(!mayDisplace(arm,selected,before))continue; // Another body may move away in the whole-layout solution.
            BlockPos relative=before.pos.subtract(arm.getPos());stored.add(new VehicleStructure.Cell(relative,before.state,VehicleTileData.translated(before.tileData(),BlockPos.ORIGIN.subtract(arm.getPos()))));removed.add(before.pos);
        }
        NBTTagCompound data=arm.currentData(root.tileData());data.setTag("ArmPayload",placement.payloadTag());
        if(stored.isEmpty())data.removeTag("ArmStoredSections");else data.setTag("ArmStoredSections",ArmStoredSections.detachedTag(stored));
        data.setDouble("GearProgress",selected.phase);data.setInteger("GearRevision",data.getInteger("GearRevision")+1);data.setBoolean("GearParkingHold",true);data.setBoolean("GearSignalKnown",false);data.setBoolean("GearBlocked",false);data.setString("GearError","");
        if(selected.phase==0||selected.phase==1)data.removeTag("SurfaceDepartureActive");
        ArmPayload.validateRootSyncBudget(data);
        return new Plan(placement.cells,restored,removed,new VehicleStructure.Cell(root.pos,root.state,data));
    }
    private ArmParkingMaterialization(){}
}

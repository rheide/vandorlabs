package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import com.vandorlabs.physics.GridCouplerParking;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Compose only the accepted layout. Source cells and live arm state stay untouched; final
 * native validation and the ordinary vehicle ownership journal remain the caller's responsibility. */
final class VehicleArmParkingComposition {
    private static final class Entry {
        final VehicleStructure.Cell cell;final BlockPos translated;
        Entry(VehicleStructure.Cell cell,BlockPos translated){this.cell=cell;this.translated=translated;}
    }
    private final VehicleStructure source;private final VehicleArmParkingGeometry geometry;private final GridCouplerParking.Result pose;
    private final List<ArmParkingMaterialization.Plan> patches=new ArrayList<>();
    private final Set<BlockPos> removed=new HashSet<>(),destinations=new HashSet<>();private final Map<BlockPos,VehicleStructure.Cell> roots=new HashMap<>();
    private final List<Entry> entries=new ArrayList<>();private final List<VehicleStructure.Cell> output=new ArrayList<>();
    private final Set<BlockPos> sourceSeats;private final Map<BlockPos,BlockPos> translatedSeats=new LinkedHashMap<>();private Map<BlockPos,BlockPos> seatMap;
    private int stage,index,patchIndex,cellIndex;private boolean restoring;private int minX=Integer.MAX_VALUE,minY=minX,minZ=minX,maxX=Integer.MIN_VALUE,maxY=maxX,maxZ=maxX;
    private BlockPos seat;private VehicleStructure result;
    VehicleArmParkingComposition(VehicleStructure source,VehicleArmParkingGeometry geometry,GridCouplerParking.Result pose){
        if(source==null||geometry==null||pose==null||pose.bodies()!=geometry.topology.sections.size()||pose.variables()!=geometry.arms.size())throw new IllegalArgumentException("Chosen arm parking ownership changed");this.source=source;this.geometry=geometry;this.pose=pose;sourceSeats=new HashSet<>(source.passengerSeats());
    }
    boolean step(int budget,long deadline){
        if(budget<1)throw new IllegalArgumentException("Composition slice budget");if(result!=null)return true;
        if(stage==0){
            if(index<geometry.arms.size()){
                VehicleArmParkingGeometry.Arm arm=geometry.arms.get(index);if(arm.owner.payload()!=arm.payload)throw new IllegalArgumentException("Chosen arm owner changed");
                ArmParkingMaterialization.Plan patch=ArmParkingMaterialization.prepare(source,arm.owner,arm.options.candidates.get(pose.option(index++)));
                if(roots.put(patch.updatedRootCell.pos,patch.updatedRootCell)!=null)throw new IllegalArgumentException("Duplicate chosen arm root");
                for(BlockPos at:patch.removedSourcePositions)if(!removed.add(at))throw new IllegalArgumentException("Two arms claim one removed source cell");patches.add(patch);return false;
            }
            for(BlockPos root:roots.keySet())if(removed.contains(root))throw new IllegalArgumentException("A payload claims another arm controller");stage=1;index=0;return false;
        }
        int used=0;
        if(stage==1){
            while(index<source.cells.size()&&used++<budget){
                VehicleStructure.Cell original=source.cells.get(index++);if(!removed.contains(original.pos)){
                    Integer body=geometry.topology.section.get(original.pos);if(body==null)throw new IllegalArgumentException("Native parking body disappeared");VehicleStructure.Cell cell=roots.getOrDefault(original.pos,original);BlockPos at=append(cell,body);if(original.pos.equals(source.seat))seat=at;if(sourceSeats.contains(original.pos))translatedSeats.put(original.pos,at);
                }
                if(expired(deadline))return false;
            }
            if(index<source.cells.size())return false;stage=2;return false;
        }
        if(stage==2){
            while(patchIndex<patches.size()&&used++<budget){
                ArmParkingMaterialization.Plan patch=patches.get(patchIndex);VehicleArmParkingGeometry.Arm arm=geometry.arms.get(patchIndex);List<VehicleStructure.Cell> cells=restoring?patch.restoredSectionCells:patch.payloadCells;
                if(cellIndex<cells.size()){
                    int originalIndex=cellIndex;BlockPos at=append(cells.get(cellIndex++),arm.body);
                    if(!restoring&&arm.payload.materialized()){BlockPos original=arm.payload.placedCellPosition(arm.owner,originalIndex);if(original.equals(source.seat))seat=at;if(sourceSeats.contains(original))translatedSeats.put(original,at);}
                }else if(!restoring){restoring=true;cellIndex=0;}else{patchIndex++;restoring=false;cellIndex=0;}
                if(expired(deadline))return false;
            }
            if(patchIndex<patches.size())return false;
            if(entries.isEmpty()||!source.displayOnly&&seat==null)throw new IllegalArgumentException("Chosen arm layout lost the Pilot Seat");stage=3;index=0;return false;
        }
        while(index<entries.size()&&used++<budget){
            Entry entry=entries.get(index++);BlockPos at=entry.translated.add(-minX,-minY,-minZ),delta=at.subtract(entry.cell.pos);
            if(delta.equals(BlockPos.ORIGIN))output.add(entry.cell);
            else{NBTTagCompound data=entry.cell.tileData();output.add(new VehicleStructure.Cell(at,entry.cell.state,data==null?null:VehicleTileData.translated(data,delta)));}
            if(expired(deadline))return false;
        }
        if(index<entries.size())return false;
        VehicleStructure prepared=source.displayOnly?VehicleStructure.display(output):new VehicleStructure(output,seat.add(-minX,-minY,-minZ),source.facing,source.gearCount);
        Map<BlockPos,BlockPos> normalized=new LinkedHashMap<>();for(Map.Entry<BlockPos,BlockPos> entry:translatedSeats.entrySet())normalized.put(entry.getKey(),entry.getValue().add(-minX,-minY,-minZ));if(normalized.size()!=sourceSeats.size())throw new IllegalArgumentException("Chosen arm layout lost a passenger seat");seatMap=Collections.unmodifiableMap(normalized);result=prepared;return true;
    }
    private BlockPos append(VehicleStructure.Cell cell,int body){
        if(entries.size()>=VehicleStructure.MAX_BLOCKS)throw new IllegalArgumentException("Chosen arm layout exceeds native block capacity");
        long x=Math.addExact((long)cell.pos.getX(),pose.translation(body,0)),y=Math.addExact((long)cell.pos.getY(),pose.translation(body,1)),z=Math.addExact((long)cell.pos.getZ(),pose.translation(body,2));
        if(x< -1_000_000_000L||x>1_000_000_000L||y< -1_000_000_000L||y>1_000_000_000L||z< -1_000_000_000L||z>1_000_000_000L)throw new IllegalArgumentException("Chosen arm coordinates exceed native bounds");
        BlockPos at=new BlockPos((int)x,(int)y,(int)z);if(!destinations.add(at))throw new IllegalArgumentException("Chosen arm layout has duplicate native owners");
        minX=Math.min(minX,at.getX());minY=Math.min(minY,at.getY());minZ=Math.min(minZ,at.getZ());maxX=Math.max(maxX,at.getX());maxY=Math.max(maxY,at.getY());maxZ=Math.max(maxZ,at.getZ());
        if((long)maxX-minX>=VehicleStructure.MAX_AXIS||(long)maxY-minY>=VehicleStructure.MAX_AXIS||(long)maxZ-minZ>=VehicleStructure.MAX_AXIS)throw new IllegalArgumentException("Chosen arm layout exceeds native dimensions");entries.add(new Entry(cell,at));return at;
    }
    VehicleStructure result(){if(result==null)throw new IllegalStateException("Chosen arm composition is pending");return result;}
    Map<BlockPos,BlockPos> seatMap(){if(seatMap==null)throw new IllegalStateException("Chosen seat mapping is pending");return seatMap;}
    private static boolean expired(long deadline){return deadline!=Long.MAX_VALUE&&System.nanoTime()-deadline>=0;}
}

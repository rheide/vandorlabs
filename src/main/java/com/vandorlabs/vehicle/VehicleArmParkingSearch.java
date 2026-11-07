package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import com.vandorlabs.physics.GridCouplerParking;
import java.util.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Read-only planning under the existing vehicle-transfer hold. Each cold preparation stage
 * yields separately; only finite-domain trials are resumable within their supplied deadline. */
final class VehicleArmParkingSearch {
    private final EntityGroundVehicle craft;private final VehicleStructure source;private final VehicleWorld view;
    private final List<GearTileEntity> roots=new ArrayList<>();private final List<ArmPayload> payloads=new ArrayList<>();private final int[] revisions;
    private final Map<BlockPos,ArmParkingCandidates.Result> domains=new LinkedHashMap<>();private final VehicleArmParkingCells.Displacement displacement;
    private final Map<BlockPos,ArmParkingSource> prepared=new LinkedHashMap<>();private final boolean currentOnly,requireHold;
    private VehicleArmParkingGeometry geometry;private GridCouplerParking.Search search;private int index,stage;private boolean done;
    VehicleArmParkingSearch(EntityGroundVehicle craft,VehicleArmParkingCells.Displacement displacement){
        this(craft,displacement,false);
    }
    VehicleArmParkingSearch(EntityGroundVehicle craft,VehicleArmParkingCells.Displacement displacement,boolean currentOnly){
        this(craft,displacement,currentOnly,true);
    }
    /** A synchronous CURRENT preview may inspect the exact server-thread source without acquiring ownership. */
    VehicleArmParkingSearch(EntityGroundVehicle craft,VehicleArmParkingCells.Displacement displacement,boolean currentOnly,boolean requireHold){
        if(!requireHold&&!currentOnly)throw new IllegalArgumentException("Curve search requires a vehicle transfer hold");
        if(craft==null||requireHold&&!craft.transferInProgress||craft.structure==null||craft.view==null||craft.view.structure!=craft.structure||craft.view.craft!=craft||craft.view.parent!=craft.world)throw new IllegalArgumentException("Arm parking search requires a stable vehicle source");
        this.craft=craft;source=craft.structure;view=craft.view;this.displacement=Objects.requireNonNull(displacement);this.currentOnly=currentOnly;this.requireHold=requireHold;
        for(TileEntity tile:view.tiles.values())if(tile instanceof GearTileEntity){GearTileEntity arm=(GearTileEntity)tile;if(arm.hasFailedArmSnapshot()||arm.hasTaxiData()&&!arm.isOperational()||arm.hasStoredSections()&&arm.payload()==null)throw new IllegalArgumentException("An arm payload needs recovery before parking");if(arm.payload()!=null)roots.add(arm);}
        roots.sort(Comparator.comparing(GearTileEntity::getPos));if(roots.size()>VehicleArmParkingGeometry.MAX_ARMS)throw new IllegalArgumentException("Too many arms for a bounded parking search");
        revisions=new int[roots.size()];for(int i=0;i<roots.size();i++){payloads.add(roots.get(i).payload());revisions[i]=roots.get(i).motionRevision();}
    }
    boolean step(int trials,long deadline){
        requireStable();if(done)return true;
        if(stage==0){
            if(index<roots.size()){
                GearTileEntity arm=roots.get(index);ArmParkingSource token=prepared.get(arm.getPos());
                if(token==null){prepared.put(arm.getPos(),ArmParkingSource.capture(source,arm));return false;}
                ArmParkingCandidates.Result result=currentOnly?ArmParkingCandidates.currentPrepared(token):ArmParkingCandidates.enumeratePrepared(token);
                if(result.status!=ArmParkingCandidates.Status.COMPLETE||result.candidates.isEmpty())throw new IllegalArgumentException(currentOnly?"An arm is not on a supported full-block pose; use Safe Park or align the arms first":"An arm has no complete supported native parking poses; original craft preserved");domains.put(arm.getPos(),result);index++;return false;
            }
            stage=1;return false;
        }
        if(stage==1){Map<BlockPos,ArmPayload> payloads=new HashMap<>();for(Map.Entry<BlockPos,ArmParkingSource> entry:prepared.entrySet())payloads.put(entry.getKey(),entry.getValue().payload());VehicleArmCouplers.Snapshot heads=VehicleArmCouplers.observe(source,view,payloads);VehicleCouplerTopology topology=VehicleCouplerTopology.forParking(source,view,VehicleArmCouplerPairs.discoverForParking(heads));geometry=VehicleArmParkingGeometry.project(source,view,topology,domains,prepared);stage=2;return false;}
        if(stage==2){VehicleArmParkingCells cells=new VehicleArmParkingCells(source,geometry,displacement);search=new GridCouplerParking.Search(geometry.problem(),cells::fits,100000);stage=3;return false;}
        GridCouplerParking.Status status=search.step(trials,deadline);
        if(status==GridCouplerParking.Status.PENDING)return false;
        if(status==GridCouplerParking.Status.LIMIT)throw new IllegalArgumentException("Arm parking exceeded its bounded search; original craft preserved. Simplify the coupled layout and retry.");
        if(status!=GridCouplerParking.Status.SOLVED)throw new IllegalArgumentException("No supported native arm layout satisfies every closed Coupler and block owner; original craft preserved");
        done=true;return true;
    }
    VehicleArmParkingGeometry geometry(){if(!done)throw new IllegalStateException("Arm parking search is pending");requireStable();return geometry;}
    GridCouplerParking.Result result(){if(!done)throw new IllegalStateException("Arm parking search is pending");requireStable();return search.result();}
    void requireStable(){
        if(craft.isDead||craft.ownershipBlocked||requireHold&&!craft.transferInProgress||craft.structure!=source||craft.view!=view||view.structure!=source||view.craft!=craft||view.parent!=craft.world)throw new IllegalArgumentException("Vehicle changed during arm parking preparation");
        for(int i=0;i<roots.size();i++){
            GearTileEntity arm=roots.get(i);if(view.tiles.get(arm.getPos())!=arm||arm.payload()!=payloads.get(i)||arm.motionRevision()!=revisions[i])throw new IllegalArgumentException("Arm changed during parking preparation");
            ArmParkingSource token=prepared.get(arm.getPos());if(token!=null&&!token.stillOwned())throw new IllegalArgumentException("Prepared arm source changed during parking preparation");
        }
    }
}

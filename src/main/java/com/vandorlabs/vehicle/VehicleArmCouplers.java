package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.physics.RigidTransform;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Loaded head observations only. Does not move arms, authorize parking or transfer ownership. */
public final class VehicleArmCouplers {
    public static final int MAX_HEADS=VehicleStructure.MAX_BLOCKS;
    public static final class Address {
        public final BlockPos arm,cell;
        private Address(BlockPos arm,BlockPos cell){this.arm=arm==null?null:arm.toImmutable();this.cell=cell.toImmutable();}
        public static Address nativeCell(BlockPos cell){return new Address(null,cell);}
        public static Address payloadCell(BlockPos arm,BlockPos cell){return new Address(Objects.requireNonNull(arm),cell);}
        @Override public boolean equals(Object other){return other instanceof Address&&Objects.equals(arm,((Address)other).arm)&&cell.equals(((Address)other).cell);}
        @Override public int hashCode(){return 31*(arm==null?0:arm.hashCode())+cell.hashCode();}
        @Override public String toString(){return arm==null?"native:"+cell:"arm:"+arm+"/"+cell;}
    }
    /** Immutable scalar geometry and settings: no tile/world, stack or snapshot-NBT references. */
    public static final class Head {
        public final Address address;public final BlockPos carrier;
        /** Present only while this head has an installed native cell in the carried structure. */
        public final BlockPos nativeCell,legacyCell;
        public final EnumFacing legacyFacing;public final double legacyHeight,localX,localY,localZ;
        public final boolean articulated,requested,locked,released;
        public final com.vandorlabs.coupler.CouplerAssociation association;
        public final double x,y,z,nx,ny,nz;
        private Head(Address address,BlockPos carrier,IBlockState state,boolean articulated,boolean requested,RigidTransform transform){this(address,carrier,address.cell,state,articulated,requested,transform,address.arm==null?address.cell:null,address.cell,state);}
        private Head(Address address,BlockPos carrier,BlockPos geometryCell,IBlockState state,boolean articulated,boolean requested,RigidTransform transform,BlockPos nativeCell,BlockPos legacyCell,IBlockState legacyState){this(address,carrier,geometryCell,state,articulated,requested,transform,nativeCell,legacyCell,legacyState,false);}
        private Head(Address address,BlockPos carrier,BlockPos geometryCell,IBlockState state,boolean articulated,boolean requested,RigidTransform transform,BlockPos nativeCell,BlockPos legacyCell,IBlockState legacyState,boolean released){
            this(address,carrier,geometryCell,state,articulated,requested,transform,nativeCell,legacyCell,legacyState,released,null);
        }
        private Head(Address address,BlockPos carrier,BlockPos geometryCell,IBlockState state,boolean articulated,boolean requested,RigidTransform transform,BlockPos nativeCell,BlockPos legacyCell,IBlockState legacyState,boolean released,com.vandorlabs.coupler.CouplerAssociation association){
            this.association=association;this.released=released;
            this.address=address;this.carrier=carrier.toImmutable();this.nativeCell=nativeCell==null?null:nativeCell.toImmutable();this.legacyCell=legacyCell.toImmutable();this.legacyFacing=legacyState.getValue(BlockCoupler.FACING);this.legacyHeight=BlockCoupler.couplingHeight(legacyState);this.articulated=articulated;this.requested=requested;locked=state.getValue(BlockCoupler.LOCKED);EnumFacing face=state.getValue(BlockCoupler.FACING);BlockPos cell=geometryCell;
            double px=cell.getX()+.5+face.getFrontOffsetX()*.5,py=face.getAxis()==EnumFacing.Axis.Y?cell.getY()+.5+face.getFrontOffsetY()*.5:cell.getY()+BlockCoupler.couplingHeight(state),pz=cell.getZ()+.5+face.getFrontOffsetZ()*.5;
            localX=px;localY=py;localZ=pz;
            double[] m=transform.r;x=transform.x+m[0]*px+m[1]*py+m[2]*pz;y=transform.y+m[3]*px+m[4]*py+m[5]*pz;z=transform.z+m[6]*px+m[7]*py+m[8]*pz;int dx=face.getFrontOffsetX(),dy=face.getFrontOffsetY(),dz=face.getFrontOffsetZ();nx=m[0]*dx+m[1]*dy+m[2]*dz;ny=m[3]*dx+m[4]*dy+m[5]*dz;nz=m[6]*dx+m[7]*dy+m[8]*dz;
            if(!finite(x)||!finite(y)||!finite(z)||!Double.isFinite(nx)||!Double.isFinite(ny)||!Double.isFinite(nz)||Math.abs(nx*nx+ny*ny+nz*nz-1)>1e-7)throw new IllegalArgumentException("Invalid carried Coupler head transform");
        }
    }
    public static final class Snapshot {
        public final List<Head> heads;public final int capturedArms;public final long retainedBytes;
        public final java.util.UUID vehicle,generation;
        private Snapshot(List<Head> heads,int capturedArms){this(heads,capturedArms,null,null);}
        private Snapshot(List<Head> heads,int capturedArms,java.util.UUID vehicle,java.util.UUID generation){this.heads=Collections.unmodifiableList(new ArrayList<>(heads));this.capturedArms=capturedArms;this.vehicle=vehicle;this.generation=generation;retainedBytes=128L+heads.size()*416L;}
        /** Explicit frozen owner receipt, supplied outside the head proofs. Never mints or publishes. */
        public Snapshot ownership(java.util.UUID vehicle,java.util.UUID generation){if(vehicle==null||generation==null)throw new IllegalArgumentException("External Coupler ownership receipt required");return new Snapshot(heads,capturedArms,vehicle,generation);}
        public boolean permitsUnpaired(Head head){return head.association!=null&&head.association.permitsUnpaired(head.association.head,vehicle,generation);}

    }
    /** Scalar observations at an authenticated physical ownership boundary; no vehicle scope is inferred. */
    static Snapshot nativeReceiptSnapshot(List<Head> heads,int arms){if(heads.size()>MAX_HEADS)throw new IllegalArgumentException("Native Coupler receipt exceeds head budget");return new Snapshot(heads,arms);}
    static Head nativeReceiptHead(Address address,BlockPos carrier,BlockPos localCell,IBlockState state,TileCoupler tile){
        if(tile.quarantined())throw new IllegalArgumentException("Native Coupler association is retained but unavailable");validate(state);
        return new Head(address,carrier,localCell,state,tile.articulation(),tile.requested(),RigidTransform.IDENTITY,localCell,localCell,state,tile.releasedArmHead(),tile.association());
    }
    static Head capturedReceiptHead(Address address,BlockPos carrier,VehicleStructure.Cell cell,RigidTransform frame,BlockPos legacyCell,IBlockState legacyState){
        net.minecraft.util.ResourceLocation id=TileEntity.getKey(TileCoupler.class);if(id==null||!cell.hasTileKey("id",8)||!id.toString().equals(cell.tileString("id")))throw new IllegalArgumentException("Captured Coupler own data is unavailable");validate(cell.state);
        boolean requested=cell.hasTileKey("CouplerRequested",1)?cell.tileBoolean("CouplerRequested"):cell.tileBoolean("CouplerReceived")!=cell.tileBoolean("CouplerInverted");
        return new Head(address,carrier,cell.pos,cell.state,cell.tileBoolean("CouplerArticulation"),requested,frame,null,legacyCell,legacyState,cell.tileBoolean("VehicleArmCouplerReleased"),association(cell));
    }
    /** Main server thread; every observation comes from installed local views, never chunk discovery. */
    public static Snapshot observe(EntityGroundVehicle craft){
        if(craft==null||!(craft.world instanceof WorldServer)||!((WorldServer)craft.world).getMinecraftServer().isCallingFromMinecraftThread())throw new IllegalStateException("Arm Coupler observations require the server thread");
        if(craft.isDead||craft.structure==null||craft.view==null||craft.view.craft!=craft||craft.view.structure!=craft.structure)throw new IllegalArgumentException("Vehicle attachment owner is unavailable");
        Snapshot observed=observe(craft.structure,craft.view);java.util.UUID generation=craft.couplerAssociationGeneration();return generation==null?observed:observed.ownership(craft.getUniqueID(),generation);
    }
    private static final class MaterializedHead {
        final Address address;final BlockPos carrier;
        MaterializedHead(Address address,BlockPos carrier){this.address=address;this.carrier=carrier;}
    }
    static Snapshot observe(VehicleStructure structure,VehicleWorld view){return observe(structure,view,Collections.emptyMap());}
    /** Parking may refresh materialized source state once, without replacing the live owner. */
    static Snapshot observe(VehicleStructure structure,VehicleWorld view,Map<BlockPos,ArmPayload> prepared){
        List<Head> heads=new ArrayList<>();Set<Address> addresses=new HashSet<>();
        Map<BlockPos,MaterializedHead> endpointHeads=new HashMap<>();Set<BlockPos> endpointRoots=new HashSet<>();
        for(VehicleStructure.Cell cell:structure.cells){
            if(!(cell.state.getBlock() instanceof GearBlock))continue;TileEntity raw=view.tiles.get(cell.pos);if(!(raw instanceof GearTileEntity))throw new IllegalArgumentException("Arm data is unavailable at "+cell.pos);GearTileEntity gear=((GearTileEntity)raw).loadedController();if(gear==null)throw new IllegalArgumentException("Arm membership is unavailable at "+cell.pos);if(!endpointRoots.add(gear.getPos()))continue;
            ArmPayload payload=prepared.getOrDefault(gear.getPos(),gear.payload());if(payload==null||!payload.materialized())continue;
            if(payload.hasOwnershipJournal()||!gear.isOperational())throw new IllegalArgumentException("Arm snapshot requires recovery before coupling");
            for(int i=0;i<payload.blocks.cells.size();i++){
                VehicleStructure.Cell original=payload.blocks.cells.get(i);if(!(original.state.getBlock() instanceof BlockCoupler))continue;
                BlockPos at=payload.placedCellPosition(gear,i);IBlockState expected=payload.placedCellState(i);VehicleStructure.Cell installed=structure.at(at);
                if(installed==null||installed.state.getBlock()!=expected.getBlock()||installed.state.withProperty(BlockCoupler.LOCKED,expected.getValue(BlockCoupler.LOCKED))!=expected)throw new IllegalArgumentException("Materialized Coupler correspondence is unavailable at "+at);
                if(endpointHeads.size()>=MAX_HEADS||endpointHeads.put(at,new MaterializedHead(Address.payloadCell(gear.getPos(),original.pos),gear.getPos()))!=null)throw new IllegalArgumentException("Multiple arms claim the same Coupler at "+at);
            }
        }
        for(VehicleStructure.Cell cell:structure.couplers){TileEntity raw=view.tiles.get(cell.pos);if(!(raw instanceof TileCoupler))throw new IllegalArgumentException("Coupler data is unavailable at "+cell.pos);TileCoupler tile=(TileCoupler)raw;if(tile.quarantined())throw new IllegalArgumentException("Coupler snapshot retained; association unsupported");MaterializedHead owner=endpointHeads.remove(cell.pos);
            if(owner==null)add(heads,addresses,head(Address.nativeCell(cell.pos),cell.pos,cell.state,tile,RigidTransform.IDENTITY));
            else{validate(cell.state);add(heads,addresses,new Head(owner.address,owner.carrier,cell.pos,cell.state,tile.articulation(),tile.requested(),RigidTransform.IDENTITY,cell.pos,cell.pos,cell.state,tile.releasedArmHead(),tile.association()));}
        }
        if(!endpointHeads.isEmpty())throw new IllegalArgumentException("Materialized Coupler cells are unavailable");
        Set<BlockPos> roots=new HashSet<>();int arms=0;
        for(VehicleStructure.Cell cell:structure.cells){if(!(cell.state.getBlock() instanceof GearBlock))continue;TileEntity raw=view.tiles.get(cell.pos);if(!(raw instanceof GearTileEntity))throw new IllegalArgumentException("Arm data is unavailable at "+cell.pos);GearTileEntity gear=((GearTileEntity)raw).loadedController();if(gear==null)throw new IllegalArgumentException("Arm membership is unavailable at "+cell.pos);if(!roots.add(gear.getPos()))continue;
            ArmPayload payload=prepared.getOrDefault(gear.getPos(),gear.payload());if(payload==null){if(gear.hasFailedArmSnapshot()||gear.hasTaxiData()&&!gear.isOperational())throw new IllegalArgumentException("Arm snapshot requires recovery before coupling");continue;}if(payload.materialized())continue;
            if(payload.hasOwnershipJournal()||!gear.isOperational())throw new IllegalArgumentException("Arm snapshot requires recovery before coupling");
            // Nested moving mechanisms need an explicit frame hierarchy; do not quietly fold them rigid.
            for(VehicleStructure.Cell nested:payload.blocks.cells)if(nested.state.getBlock() instanceof GearBlock)throw new IllegalArgumentException("Nested arm Coupler frames are not supported yet");
            if(payload.blocks.couplers.isEmpty())continue;arms++;RigidTransform frame=payloadFrame(payload,gear,gear.progress());
            for(int cellIndex=0;cellIndex<payload.blocks.cells.size();cellIndex++){VehicleStructure.Cell nested=payload.blocks.cells.get(cellIndex);if(!(nested.state.getBlock() instanceof BlockCoupler))continue;net.minecraft.util.ResourceLocation expected=TileEntity.getKey(TileCoupler.class);if(expected==null||!nested.hasTileKey("id",8)||!expected.toString().equals(nested.tileString("id")))throw new IllegalArgumentException("Captured Coupler data is unavailable at "+nested.pos);boolean requested=nested.hasTileKey("CouplerRequested",1)?nested.tileBoolean("CouplerRequested"):nested.tileBoolean("CouplerReceived")!=nested.tileBoolean("CouplerInverted");validate(nested.state);add(heads,addresses,new Head(Address.payloadCell(gear.getPos(),nested.pos),gear.getPos(),nested.pos,nested.state,nested.tileBoolean("CouplerArticulation"),requested,frame,null,payload.placedCellPosition(gear,cellIndex),payload.placedCellState(cellIndex),nested.tileBoolean("VehicleArmCouplerReleased"),association(nested)));}
        }
        VehicleWorld.CouplerScope scope=view.couplerScope();return scope==null?new Snapshot(heads,arms):new Snapshot(heads,arms,scope.owner,scope.generation);
    }
    private static Head head(Address address,BlockPos carrier,IBlockState state,TileCoupler tile,RigidTransform frame){if(tile.quarantined())throw new IllegalArgumentException("Coupler snapshot retained; association unsupported");validate(state);return new Head(address,carrier,address.cell,state,tile.articulation(),tile.requested(),frame,address.arm==null?address.cell:null,address.cell,state,tile.releasedArmHead(),tile.association());}
    private static com.vandorlabs.coupler.CouplerAssociation association(VehicleStructure.Cell cell){if(!cell.hasTileKey(com.vandorlabs.coupler.CouplerAssociationData.KEY))return null;net.minecraft.nbt.NBTTagCompound data=cell.tileFields(new String[]{com.vandorlabs.coupler.CouplerAssociationData.KEY,"CouplerControlIdentityMost","CouplerControlIdentityLeast"});com.vandorlabs.coupler.CouplerAssociation proof=com.vandorlabs.coupler.CouplerAssociationData.read(data);if(proof!=null&&(!data.hasKey("CouplerControlIdentityMost",4)||!data.hasKey("CouplerControlIdentityLeast",4)||!proof.head.equals(data.getUniqueId("CouplerControlIdentity"))))throw new IllegalArgumentException("Captured Coupler association identity changed");return proof;}
    private static void validate(IBlockState state){if(!(state.getBlock() instanceof BlockCoupler)||state.getBlock().getStateFromMeta(state.getBlock().getMetaFromState(state))!=state)throw new IllegalArgumentException("Coupler orientation cannot be saved exactly");}
    private static void add(List<Head> heads,Set<Address> addresses,Head head){if(heads.size()>=MAX_HEADS||!addresses.add(head.address))throw new IllegalArgumentException("Arm Coupler head budget or duplicate address");heads.add(head);}
    static RigidTransform payloadFrame(ArmPayload payload,GearTileEntity arm,double progress){
        if(!Double.isFinite(progress)||progress<0||progress>1)throw new IllegalArgumentException("Invalid carried arm progress");Vec3d zero=payload.transform(arm,progress,0,0,0),x=payload.transform(arm,progress,1,0,0).subtract(zero),y=payload.transform(arm,progress,0,1,0).subtract(zero),z=payload.transform(arm,progress,0,0,1).subtract(zero);double[] m={x.x,y.x,z.x,x.y,y.y,z.y,x.z,y.z,z.z};for(double value:m)if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite arm basis");for(int a=0;a<3;a++)for(int b=a;b<3;b++)if(Math.abs(m[a]*m[b]+m[3+a]*m[3+b]+m[6+a]*m[6+b]-(a==b?1:0))>1e-7)throw new IllegalArgumentException("Nonrigid arm basis");double determinant=m[0]*(m[4]*m[8]-m[5]*m[7])-m[1]*(m[3]*m[8]-m[5]*m[6])+m[2]*(m[3]*m[7]-m[4]*m[6]);if(Math.abs(determinant-1)>1e-7)throw new IllegalArgumentException("Reflected arm basis");return new RigidTransform(m,zero.x+arm.getPos().getX(),zero.y+arm.getPos().getY(),zero.z+arm.getPos().getZ());
    }
    private static boolean finite(double value){return Double.isFinite(value)&&Math.abs(value)<=30000001;}
    /** Private Source proof switch; never a public gameplay configuration. */
    public static boolean runtime(){return Boolean.getBoolean("vandorlabs.armCouplerRuntime")&&System.getProperty("vandorlabs.reprolab")!=null;}
    static boolean hasPayloadHeads(VehicleWorld view){if(!runtime()||view==null)return false;for(TileEntity tile:view.tiles.values())if(tile instanceof GearTileEntity){ArmPayload payload=((GearTileEntity)tile).payload();if(payload!=null&&!payload.blocks.couplers.isEmpty())return true;}return false;}
    static long signature(VehicleWorld view){long hash=1469598103934665603L;for(TileEntity tile:view.tiles.values())if(tile instanceof GearTileEntity){GearTileEntity gear=(GearTileEntity)tile;ArmPayload payload=gear.payload();if(payload==null||payload.blocks.couplers.isEmpty())continue;hash=(hash^gear.getPos().toLong())*1099511628211L;hash=(hash^(payload.materialized()?1:0))*1099511628211L;for(VehicleStructure.Cell head:payload.blocks.couplers){hash=(hash^head.pos.toLong())*1099511628211L;hash=(hash^net.minecraft.block.Block.getStateId(head.state))*1099511628211L;hash=(hash^(head.tileBoolean("CouplerArticulation")?1:0))*1099511628211L;}}return hash;}
    public static boolean locked(EntityGroundVehicle craft,Address address){VehicleStructure.Cell cell;if(address.arm==null)cell=craft.structure.at(address.cell);else{TileEntity raw=craft.view.getTileEntity(address.arm);ArmPayload payload=raw instanceof GearTileEntity?((GearTileEntity)raw).payload():null;cell=payload==null?null:payload.blocks.at(address.cell);}return cell!=null&&cell.state.getBlock() instanceof BlockCoupler&&cell.state.getValue(BlockCoupler.LOCKED);}
    static void point(Head head,VehicleWorld view,Map<BlockPos,? extends Number> desired,double[] out){
        if(head.address.arm==null){out[0]=head.x;out[1]=head.y;out[2]=head.z;return;}
        TileEntity raw=view.getTileEntity(head.address.arm);if(!(raw instanceof GearTileEntity))throw new IllegalArgumentException("Arm Coupler carrier disappeared");GearTileEntity arm=(GearTileEntity)raw;ArmPayload payload=arm.payload();if(payload==null)throw new IllegalArgumentException("Arm Coupler snapshot disappeared");if(payload.materialized()){out[0]=head.x;out[1]=head.y;out[2]=head.z;return;}
        Number requested=desired==null?null:desired.get(arm.getPos());Vec3d p=payload.transform(arm,requested==null?arm.progress():requested.doubleValue(),head.localX,head.localY,head.localZ);out[0]=p.x+arm.getPos().getX();out[1]=p.y+arm.getPos().getY();out[2]=p.z+arm.getPos().getZ();
    }
    private VehicleArmCouplers(){}
}

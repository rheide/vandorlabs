package com.vandorlabs.vehicle;

import net.minecraft.util.math.*;

/** Immutable separation geometry from actual closed heads, never fabricated native adjacency. */
public final class VehicleArmCouplerReleaseContact {
 public final java.util.UUID owner;public final VehicleArmCouplers.Address first,second;public final BlockPos firstCarrier,secondCarrier,firstAlias,secondAlias;public final int firstBody,secondBody;public final double nx,ny,nz,secondNx,secondNy,secondNz;
 private VehicleArmCouplerReleaseContact(EntityGroundVehicle craft,VehicleArmCouplers.Head a,VehicleArmCouplers.Head b){
  owner=craft.getUniqueID();first=a.address;second=b.address;firstCarrier=a.carrier;secondCarrier=b.carrier;firstAlias=a.legacyCell;secondAlias=b.legacyCell;firstBody=craft.articulation.bodyAt(firstCarrier);secondBody=craft.articulation.bodyAt(secondCarrier);VehicleCouplingPlan.require(firstBody!=secondBody,"Released heads must belong to distinct carried bodies");
  Vec3d pa=craft.toWorld(new Vec3d(a.x,a.y,a.z),a.carrier),pb=craft.toWorld(new Vec3d(b.x,b.y,b.z),b.carrier),normal=craft.toWorld(new Vec3d(a.x+a.nx,a.y+a.ny,a.z+a.nz),a.carrier).subtract(pa),opposite=craft.toWorld(new Vec3d(b.x+b.nx,b.y+b.ny,b.z+b.nz),b.carrier).subtract(pb);nx=normal.x;ny=normal.y;nz=normal.z;secondNx=opposite.x;secondNy=opposite.y;secondNz=opposite.z;
  VehicleCouplingPlan.require(a.locked&&b.locked&&pa.squareDistanceTo(pb)<=1e-12&&Math.abs(normal.lengthSquared()-1)<1e-6&&Math.abs(opposite.lengthSquared()-1)<1e-6,"Captured release heads no longer share their actual contact");
 }
 public static VehicleArmCouplerReleaseContact capture(EntityGroundVehicle craft,VehicleArmCouplerPairs.Pair pair){VehicleArmCouplers.Snapshot current=VehicleArmCouplers.observe(craft);if(pair==null||pair.first.address.equals(pair.second.address))throw new IllegalArgumentException("Missing release pair");VehicleArmCouplers.Head first=null,second=null;for(VehicleArmCouplers.Head head:current.heads){if(head.address.equals(pair.first.address)){VehicleCouplingPlan.require(first==null,"Ambiguous first release address");first=head;}if(head.address.equals(pair.second.address)){VehicleCouplingPlan.require(second==null,"Ambiguous second release address");second=head;}}VehicleCouplingPlan.require(first!=null&&second!=null,"Release address disappeared");return new VehicleArmCouplerReleaseContact(craft,first,second);}
}

package com.vandorlabs.vehicle;

import java.util.*;

/** Initial loaded local-frame pair discovery. Persistent movement keeps addresses, never rediscovers per pose. */
public final class VehicleArmCouplerPairs {
    private static final double EPS=1e-6;
    private static final int MAX_BUCKET=64;
    public static final class Pair {
        public final VehicleArmCouplers.Head first,second;
        private Pair(VehicleArmCouplers.Head first,VehicleArmCouplers.Head second){this.first=first;this.second=second;}
    }
    public static final class Snapshot {
        public final List<Pair> pairs;public final long retainedBytes;
        private Snapshot(List<Pair> pairs,int heads){this.pairs=Collections.unmodifiableList(new ArrayList<>(pairs));retainedBytes=64L+heads*320L+pairs.size()*48L;}
    }
    private static final class Key {
        long x,y,z;
        Key(long x,long y,long z){this.x=x;this.y=y;this.z=z;}
        @Override public int hashCode(){long h=x*73856093L^y*19349663L^z*83492791L;return(int)(h^(h>>>32));}
        @Override public boolean equals(Object o){return o instanceof Key&&x==((Key)o).x&&y==((Key)o).y&&z==((Key)o).z;}
    }
    public static Snapshot discover(VehicleArmCouplers.Snapshot observations){return discover(observations,VehicleArmCouplers.runtime());}
    /** Read-only saved-address constraints for a proposed parking layout, independent of live activation. */
    public static Snapshot discoverForParking(VehicleArmCouplers.Snapshot observations){return discover(observations,true);}
    private static Snapshot discover(VehicleArmCouplers.Snapshot observations,boolean savedAliases){
        if(observations==null||observations.heads.size()>VehicleArmCouplers.MAX_HEADS)throw new IllegalArgumentException("Invalid Coupler observations");
        Map<Key,List<VehicleArmCouplers.Head>> buckets=new HashMap<>();
        for(VehicleArmCouplers.Head head:observations.heads){if(!head.locked||head.released)continue;Key key=key(head);List<VehicleArmCouplers.Head> list=buckets.computeIfAbsent(key,k->new ArrayList<>());if(list.size()>=MAX_BUCKET)throw new IllegalArgumentException("Ambiguous coincident Coupler head budget");list.add(head);}
        Map<net.minecraft.util.math.BlockPos,List<VehicleArmCouplers.Head>> aliases=new HashMap<>();if(savedAliases)for(VehicleArmCouplers.Head head:observations.heads)if(head.locked&&!head.released){List<VehicleArmCouplers.Head> list=aliases.computeIfAbsent(head.legacyCell,k->new ArrayList<>());if(list.size()>=MAX_BUCKET)throw new IllegalArgumentException("Ambiguous saved Coupler alias budget");list.add(head);}
        Map<VehicleArmCouplers.Address,VehicleArmCouplers.Head> partners=new HashMap<>();Key probe=new Key(0,0,0);
        for(VehicleArmCouplers.Head head:observations.heads){if(!head.locked||head.released)continue;Key base=key(head);VehicleArmCouplers.Head partner=null;
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
                probe.x=base.x+dx;probe.y=base.y+dy;probe.z=base.z+dz;List<VehicleArmCouplers.Head> candidates=buckets.get(probe);if(candidates==null)continue;
                for(VehicleArmCouplers.Head other:candidates){if(head==other||!matching(head,other))continue;if(partner!=null&&partner!=other)throw new IllegalArgumentException("Several Coupler heads occupy the same pair");partner=other;}
            }
            if(partner==null&&savedAliases){List<VehicleArmCouplers.Head> candidates=aliases.get(head.legacyCell.offset(head.legacyFacing));if(candidates!=null)for(VehicleArmCouplers.Head other:candidates){if(other==head||head.address.arm==null&&other.address.arm==null)continue;if(other.legacyFacing==head.legacyFacing.getOpposite()&&Math.abs(head.legacyHeight-other.legacyHeight)<EPS){if(partner!=null&&partner!=other)throw new IllegalArgumentException("Ambiguous saved arm Coupler pair");partner=other;}}}
            if(partner==null){if(head.address.arm!=null)throw new IllegalArgumentException("Captured locked Coupler has no matching head: "+head.address);continue;}partners.put(head.address,partner);
        }
        List<Pair> pairs=new ArrayList<>();Set<VehicleArmCouplers.Address> used=new HashSet<>();
        for(VehicleArmCouplers.Head head:observations.heads){VehicleArmCouplers.Head partner=partners.get(head.address);if(partner==null||used.contains(head.address))continue;if(partners.get(partner.address)!=head)throw new IllegalArgumentException("Nonreciprocal Coupler pair");used.add(head.address);used.add(partner.address);pairs.add(new Pair(head,partner));}
        return new Snapshot(pairs,observations.heads.size());
    }
    private static Key key(VehicleArmCouplers.Head head){return new Key((long)Math.floor(head.x/EPS),(long)Math.floor(head.y/EPS),(long)Math.floor(head.z/EPS));}
    private static boolean matching(VehicleArmCouplers.Head a,VehicleArmCouplers.Head b){double x=a.x-b.x,y=a.y-b.y,z=a.z-b.z;return x*x+y*y+z*z<=EPS*EPS&&Math.abs(a.nx+b.nx)<=EPS&&Math.abs(a.ny+b.ny)<=EPS&&Math.abs(a.nz+b.nz)<=EPS;}
    private VehicleArmCouplerPairs(){}
}

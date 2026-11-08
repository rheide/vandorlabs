package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.BlockCoupler;
import java.util.*;
import net.minecraft.util.math.BlockPos;

/** Immutable physical section graph; inventories and mechanism timelines do not belong in its edges. */
public final class VehicleCouplerTopology {
    public static final class Link {
        public final BlockPos a,b;
        public final int first,second;
        public final boolean articulation;
        /** Prepared arm links use these stable scalar descriptors; legacy links leave them null. */
        public final VehicleArmCouplers.Head headA,headB;
        Link(BlockPos a,BlockPos b,int first,int second,boolean articulation){this.a=a;this.b=b;this.first=first;this.second=second;this.articulation=articulation;headA=headB=null;}
        private Link(VehicleArmCouplerPairs.Pair pair,int first,int second){headA=pair.first;headB=pair.second;a=headA.nativeCell;b=headB.nativeCell;this.first=first;this.second=second;articulation=headA.articulated||headB.articulated;}
    }
    public final List<Set<BlockPos>> sections;
    public final Map<BlockPos,Integer> section;
    public final List<Link> links;
    public final int root;
    private Boolean liftReady;
    private VehicleCouplerTopology(VehicleStructure structure,VehicleWorld view){
        sections=Collections.unmodifiableList(VehicleScan.couplerSections(view));Map<BlockPos,Integer> ids=new HashMap<>();for(int i=0;i<sections.size();i++)for(BlockPos p:sections.get(i))ids.put(p,i);section=Collections.unmodifiableMap(ids);
        BlockPos rootPos=structure.controller()==null?structure.seat:structure.controller().pos;root=ids.getOrDefault(rootPos,0);
        List<Link> paired=new ArrayList<>();
        for(VehicleStructure.Cell a:structure.couplers){
            if(!a.state.getValue(BlockCoupler.LOCKED))continue;BlockPos p=a.pos.offset(a.state.getValue(BlockCoupler.FACING));VehicleStructure.Cell b=structure.at(p);
            if(b==null||a.pos.compareTo(p)>=0||!VehicleCouplings.matching(a.state,b.state)||!b.state.getValue(BlockCoupler.LOCKED)||!structure.isActive(a.pos)||!structure.isActive(b.pos))continue;
            int first=ids.get(a.pos),second=ids.get(p);if(first==second)continue;
            net.minecraft.tileentity.TileEntity firstTile=view.getTileEntity(a.pos),secondTile=view.getTileEntity(p);boolean articulatedA=firstTile instanceof com.vandorlabs.coupler.TileCoupler?((com.vandorlabs.coupler.TileCoupler)firstTile).articulation():a.tileBoolean("CouplerArticulation"),articulatedB=secondTile instanceof com.vandorlabs.coupler.TileCoupler?((com.vandorlabs.coupler.TileCoupler)secondTile).articulation():b.tileBoolean("CouplerArticulation");paired.add(new Link(a.pos,p,first,second,articulatedA||articulatedB));
        }
        links=Collections.unmodifiableList(paired);
    }
    private VehicleCouplerTopology(VehicleStructure structure,List<Set<BlockPos>> groups,List<Link> links){
        sections=Collections.unmodifiableList(groups);Map<BlockPos,Integer> ids=new HashMap<>();for(int i=0;i<groups.size();i++)for(BlockPos pos:groups.get(i))ids.put(pos,i);section=Collections.unmodifiableMap(ids);BlockPos at=structure.controller()==null?structure.seat:structure.controller().pos;root=ids.getOrDefault(at,0);this.links=Collections.unmodifiableList(links);
    }
    /** Detached first-slice graph; does not replace the current graph or move a body. */
    public static VehicleCouplerTopology withArmPairs(VehicleStructure structure,VehicleWorld view,VehicleArmCouplerPairs.Snapshot pairs){return withArmPairs(structure,view,pairs,true);}
    /** Canonical sections and every constraint, including parallel links; no runtime graph installation. */
    public static VehicleCouplerTopology forParking(VehicleStructure structure,VehicleWorld view,VehicleArmCouplerPairs.Snapshot pairs){return withArmPairs(structure,view,pairs,false);}
    private static VehicleCouplerTopology withArmPairs(VehicleStructure structure,VehicleWorld view,VehicleArmCouplerPairs.Snapshot pairs,boolean requireBridges){
        if(structure==null||view==null||view.structure!=structure||pairs==null)throw new IllegalArgumentException("Coupler snapshot owner changed");
        List<Set<BlockPos>> original=VehicleScan.couplerSections(view);Map<BlockPos,Integer> sourceIds=new HashMap<>();int[] union=new int[original.size()];for(int i=0;i<original.size();i++){union[i]=i;for(BlockPos pos:original.get(i))sourceIds.put(pos,i);}
        for(VehicleArmCouplerPairs.Pair pair:pairs.pairs)for(VehicleArmCouplers.Head head:new VehicleArmCouplers.Head[]{pair.first,pair.second})if(head.address.arm!=null&&head.nativeCell!=null){Integer carrier=sourceIds.get(head.carrier),cell=sourceIds.get(head.nativeCell);if(carrier==null||cell==null)throw new IllegalArgumentException("Materialized Coupler carrier is unavailable");union[find(union,cell)]=find(union,carrier);}
        Map<Integer,Set<BlockPos>> merged=new LinkedHashMap<>();for(int i=0;i<original.size();i++)merged.computeIfAbsent(find(union,i),k->new LinkedHashSet<>()).addAll(original.get(i));List<Set<BlockPos>> groups=new ArrayList<>();Map<BlockPos,Integer> ids=new HashMap<>();for(Set<BlockPos> group:merged.values()){for(BlockPos pos:group)ids.put(pos,groups.size());groups.add(Collections.unmodifiableSet(group));}
        List<Link> links=new ArrayList<>();List<com.vandorlabs.physics.HingeGraph.Edge> edges=new ArrayList<>();Set<Integer> armEdges=new HashSet<>();
        for(VehicleArmCouplerPairs.Pair pair:pairs.pairs){Integer a=ids.get(pair.first.carrier),b=ids.get(pair.second.carrier);if(a==null||b==null)throw new IllegalArgumentException("Coupler carrier section is unavailable");boolean arm=pair.first.address.arm!=null||pair.second.address.arm!=null;if(requireBridges&&(!structure.isActive(pair.first.carrier)||!structure.isActive(pair.second.carrier))){if(arm)throw new IllegalArgumentException("Arm Coupler carrier is inactive");continue;}if(a.equals(b)&&requireBridges)continue;Link link=new Link(pair,a,b);if(arm&&!link.articulation)throw new IllegalArgumentException("Rigid arm-mounted Coupler constraints are not ready");if(arm)armEdges.add(links.size());links.add(link);edges.add(new com.vandorlabs.physics.HingeGraph.Edge(a,b,link.articulation));}
        VehicleCouplerTopology result=new VehicleCouplerTopology(structure,groups,links);if(!requireBridges)return result;com.vandorlabs.physics.HingeGraph graph=new com.vandorlabs.physics.HingeGraph(groups.size(),result.root,edges);Set<Integer> bridges=new HashSet<>();for(com.vandorlabs.physics.HingeGraph.Joint joint:graph.joints)bridges.add(joint.edge);for(int edge:armEdges)if(!bridges.contains(edge))throw new IllegalArgumentException("Multi-arm loop/parallel constraints are not ready");return result;
    }
    private static int find(int[] union,int index){int root=index;while(union[root]!=root)root=union[root];return root;}
    public static VehicleCouplerTopology of(VehicleStructure structure){
        if(structure.couplerTopology!=null)return structure.couplerTopology;
        VehicleWorld view=structure.componentView();if(view==null)return null;
        VehicleWorld current=view.structure==structure?view:new VehicleWorld(view.parent,structure,structure.componentScope());
        if(VehicleArmCouplers.hasPayloadHeads(current))return structure.couplerTopology=withArmPairs(structure,current,VehicleArmCouplerPairs.discover(VehicleArmCouplers.observe(structure,current)));
        return structure.couplerTopology=new VehicleCouplerTopology(structure,current);
    }
    public boolean sufficientLift(VehicleStructure structure){
        if(liftReady!=null)return liftReady;
        int total=0;int[] count=new int[sections.size()];for(VehicleStructure.Cell cell:structure.liftCells)if(structure.isActive(cell.pos)){count[section.get(cell.pos)]++;total++;}
        if(total<3)return liftReady=false;
        for(Link link:links)if(count[link.first]<3||count[link.second]<3)return liftReady=false;return liftReady=true;
    }
}

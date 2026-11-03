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
        Link(BlockPos a,BlockPos b,int first,int second,boolean articulation){this.a=a;this.b=b;this.first=first;this.second=second;this.articulation=articulation;}
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
            net.minecraft.nbt.NBTTagCompound at=a.tileData(),bt=b.tileData();paired.add(new Link(a.pos,p,first,second,at!=null&&at.getBoolean("CouplerArticulation")||bt!=null&&bt.getBoolean("CouplerArticulation")));
        }
        links=Collections.unmodifiableList(paired);
    }
    public static VehicleCouplerTopology of(VehicleStructure structure){
        if(structure.couplerTopology!=null)return structure.couplerTopology;
        VehicleWorld view=structure.componentView();if(view==null)return null;
        return structure.couplerTopology=new VehicleCouplerTopology(structure,view.structure==structure?view:new VehicleWorld(view.parent,structure));
    }
    public boolean sufficientLift(VehicleStructure structure){
        if(liftReady!=null)return liftReady;
        int total=0;int[] count=new int[sections.size()];for(VehicleStructure.Cell cell:structure.liftCells)if(structure.isActive(cell.pos)){count[section.get(cell.pos)]++;total++;}
        if(total<3)return liftReady=false;
        for(Link link:links)if(count[link.first]<3||count[link.second]<3)return liftReady=false;return liftReady=true;
    }
}

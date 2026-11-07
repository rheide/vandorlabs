package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.BlockCoupler;
import com.vandorlabs.gear.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Detached native-grid contact geometry. Never uses a moving head's current fractional pivot
 * as the pivot of its eventual native block, or clones tile data while constructing options. */
final class VehicleArmParkingGeometry {
    static final int SCALE=1024,MAX_ARMS=64,MAX_BODIES=128,MAX_PORTS=512,MAX_POINTS=65536;
    static final class Point {
        final long x,y,z;final int facing;
        Point(BlockPos cell,IBlockState state){
            if(!(state.getBlock() instanceof BlockCoupler))throw new IllegalArgumentException("Parking contact is not a Coupler");
            EnumFacing face=state.getValue(BlockCoupler.FACING);facing=face.getIndex();
            x=(long)cell.getX()*SCALE+SCALE/2+face.getFrontOffsetX()*SCALE/2;
            z=(long)cell.getZ()*SCALE+SCALE/2+face.getFrontOffsetZ()*SCALE/2;
            y=(long)cell.getY()*SCALE+(face.getAxis()==EnumFacing.Axis.Y?SCALE/2+face.getFrontOffsetY()*SCALE/2:Math.round(BlockCoupler.couplingHeight(state)*SCALE));
        }
    }
    static final class Arm {
        final GearTileEntity owner;final ArmPayload payload;final ArmParkingCandidates.Result options;final int body;
        Arm(GearTileEntity owner,ArmParkingCandidates.Result options,int body){this.owner=owner;payload=owner.payload();this.options=options;this.body=body;}
    }
    static final class Port {
        final VehicleArmCouplers.Address address;final int body,variable;final Point[] choices;
        Port(VehicleArmCouplers.Address address,int body,int variable,Point[] choices){this.address=address;this.body=body;this.variable=variable;this.choices=choices;}
    }
    final VehicleCouplerTopology topology;final List<Arm> arms;final List<Port> ports;final int[][] links;
    private VehicleArmParkingGeometry(VehicleCouplerTopology topology,List<Arm> arms,List<Port> ports,int[][] links){this.topology=topology;this.arms=Collections.unmodifiableList(new ArrayList<>(arms));this.ports=Collections.unmodifiableList(new ArrayList<>(ports));this.links=links;}
    /** Loaded ownership was admitted by the phase enumerator before this scalar projection. */
    static VehicleArmParkingGeometry project(VehicleStructure source,VehicleWorld view,VehicleCouplerTopology topology,Map<BlockPos,ArmParkingCandidates.Result> candidates){
        if(view==null||view.structure!=source||topology==null||topology.sections.size()>MAX_BODIES||candidates.size()>MAX_ARMS)throw new IllegalArgumentException("Arm parking layout exceeds its search bounds");
        List<Arm> arms=new ArrayList<>();Map<BlockPos,Integer> variables=new LinkedHashMap<>();
        for(Map.Entry<BlockPos,ArmParkingCandidates.Result> entry:candidates.entrySet()){
            TileEntity raw=view.tiles.get(entry.getKey());Integer body=topology.section.get(entry.getKey());ArmParkingCandidates.Result options=entry.getValue();
            if(!(raw instanceof GearTileEntity)||((GearTileEntity)raw).payload()==null||body==null||options==null||!options.completeForDistinctGridMappings()||options.candidates.isEmpty())throw new IllegalArgumentException("No complete native arm parking choices");
            variables.put(entry.getKey(),arms.size());arms.add(new Arm((GearTileEntity)raw,options,body));
        }
        Map<VehicleArmCouplers.Address,Integer> indexes=new LinkedHashMap<>();List<Port> ports=new ArrayList<>();int[][] links=new int[topology.links.size()][2];int points=0,index=0;
        for(VehicleCouplerTopology.Link link:topology.links){
            if(link.headA==null||link.headB==null)throw new IllegalArgumentException("Parking topology lacks stable Coupler addresses");
            VehicleArmCouplers.Head[] heads={link.headA,link.headB};
            for(int side=0;side<2;side++){
                VehicleArmCouplers.Head head=heads[side];Integer known=indexes.get(head.address);
                if(known==null){
                    if(ports.size()>=MAX_PORTS)throw new IllegalArgumentException("Too many arm parking contacts");
                    Integer body=topology.section.get(head.carrier);if(body==null)throw new IllegalArgumentException("Parking contact carrier disappeared");
                    int variable=-1;Point[] choices;
                    if(head.address.arm==null){VehicleStructure.Cell cell=source.at(head.address.cell);if(cell==null)throw new IllegalArgumentException("Native parking contact disappeared");choices=new Point[]{new Point(cell.pos,cell.state)};}
                    else{
                        Integer found=variables.get(head.address.arm);if(found==null)throw new IllegalArgumentException("Arm parking contact has no phase domain");variable=found;Arm arm=arms.get(variable);VehicleStructure.Cell original=arm.payload.blocks.at(head.address.cell);if(original==null)throw new IllegalArgumentException("Original arm parking contact disappeared");
                        if((long)points+arm.options.candidates.size()>MAX_POINTS)throw new IllegalArgumentException("Too many arm parking contact choices");
                        choices=new Point[arm.options.candidates.size()];
                        for(int option=0;option<choices.length;option++){
                            ArmGridMapping mapping=arm.options.candidates.get(option).mapping();
                            IBlockState state=mapping.state(original);if(state==null)throw new IllegalArgumentException("Arm parking contact cannot become a native block");choices[option]=new Point(mapping.cell(original.pos),state);
                        }
                    }
                    if((long)points+choices.length>MAX_POINTS)throw new IllegalArgumentException("Too many arm parking contact choices");points+=choices.length;known=ports.size();indexes.put(head.address,known);ports.add(new Port(head.address,body,variable,choices));
                }
                links[index][side]=known;
            }
            index++;
        }
        return new VehicleArmParkingGeometry(topology,arms,ports,links);
    }
}

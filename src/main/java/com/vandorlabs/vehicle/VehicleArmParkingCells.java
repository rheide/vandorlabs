package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import com.vandorlabs.physics.GridCouplerParking;
import java.util.*;
import net.minecraft.util.math.BlockPos;

/** Allocation-free whole-layout ownership filter. It admits scalar cells only; the selected
 * candidate still needs typed materialization, terrain/permission checks and the vehicle journal. */
final class VehicleArmParkingCells {
    interface Displacement {boolean allowed(GearTileEntity arm,ArmParkingCandidates.Candidate selected,VehicleStructure.Cell cell);}
    static final int MAX_ENTRIES=VehicleStructure.MAX_BLOCKS+64*512;
    private final VehicleArmParkingGeometry geometry;
    private final int[] fixedX,fixedY,fixedZ,fixedBody,fixedOwner,zeroOption;
    private final boolean[] fixedStored;
    private final int[][] payloadX,payloadY,payloadZ;
    private final long[] xs,ys,zs;private final int[] stamps,owners;private final boolean[] stored;
    private final int mask;private int generation;
    private long minX,minY,minZ,maxX,maxY,maxZ;private int count;
    private static final class Fixed {
        final BlockPos pos;final int body,owner;final boolean stored;
        Fixed(BlockPos pos,int body,int owner,boolean stored){this.pos=pos;this.body=body;this.owner=owner;this.stored=stored;}
    }
    VehicleArmParkingCells(VehicleStructure source,VehicleArmParkingGeometry geometry,Displacement displacement){
        this.geometry=geometry;int n=geometry.arms.size();zeroOption=new int[n];Arrays.fill(zeroOption,-1);
        Map<BlockPos,Integer> nativePayloads=new HashMap<>();Map<BlockPos,Integer> sectionOwners=new HashMap<>();
        int payloadCount=0;payloadX=new int[n][];payloadY=new int[n][];payloadZ=new int[n][];
        for(int armIndex=0;armIndex<n;armIndex++){
            VehicleArmParkingGeometry.Arm arm=geometry.arms.get(armIndex);int size=arm.payload.blocks.cells.size();payloadCount=Math.addExact(payloadCount,size);
            if(payloadCount>MAX_ENTRIES)throw new IllegalArgumentException("Arm parking payload budget exceeded");
            payloadX[armIndex]=new int[size];payloadY[armIndex]=new int[size];payloadZ[armIndex]=new int[size];
            for(int i=0;i<size;i++){VehicleStructure.Cell cell=arm.payload.blocks.cells.get(i);payloadX[armIndex][i]=cell.pos.getX();payloadY[armIndex][i]=cell.pos.getY();payloadZ[armIndex][i]=cell.pos.getZ();
                if(arm.payload.materialized()&&nativePayloads.put(arm.payload.placedCellPosition(arm.owner,i),armIndex)!=null)throw new IllegalArgumentException("Multiple arms own one native payload cell");}
            for(int i=0;i<arm.options.candidates.size();i++)if(arm.options.candidates.get(i).phase==0)zeroOption[armIndex]=i;
            if(zeroOption[armIndex]>=0)for(BlockPos member:arm.owner.members()){
                VehicleStructure.Cell cell=source.at(member);
                if(cell!=null&&displacement.allowed(arm.owner,arm.options.candidates.get(zeroOption[armIndex]),cell)&&sectionOwners.put(member,armIndex)!=null)throw new IllegalArgumentException("Multiple arm section owners");
            }
        }
        List<Fixed> fixed=new ArrayList<>();
        for(VehicleStructure.Cell cell:source.cells){
            if(nativePayloads.containsKey(cell.pos))continue;Integer body=geometry.topology.section.get(cell.pos);if(body==null)throw new IllegalArgumentException("Native arm parking section unavailable");
            fixed.add(new Fixed(cell.pos,body,sectionOwners.getOrDefault(cell.pos,-1),false));
        }
        for(int armIndex=0;armIndex<n;armIndex++){
            VehicleArmParkingGeometry.Arm arm=geometry.arms.get(armIndex);
            for(VehicleStructure.Cell cell:arm.owner.storedSectionCells())fixed.add(new Fixed(arm.owner.getPos().add(cell.pos),arm.body,armIndex,true));
        }
        if((long)fixed.size()+payloadCount>MAX_ENTRIES)throw new IllegalArgumentException("Arm parking native cell budget exceeded");
        fixedX=new int[fixed.size()];fixedY=new int[fixed.size()];fixedZ=new int[fixed.size()];fixedBody=new int[fixed.size()];fixedOwner=new int[fixed.size()];fixedStored=new boolean[fixed.size()];
        for(int i=0;i<fixed.size();i++){Fixed cell=fixed.get(i);fixedX[i]=cell.pos.getX();fixedY[i]=cell.pos.getY();fixedZ[i]=cell.pos.getZ();fixedBody[i]=cell.body;fixedOwner[i]=cell.owner;fixedStored[i]=cell.stored;}
        int capacity=2;while(capacity<2*(fixed.size()+payloadCount))capacity<<=1;mask=capacity-1;xs=new long[capacity];ys=new long[capacity];zs=new long[capacity];stamps=new int[capacity];owners=new int[capacity];stored=new boolean[capacity];
    }
    boolean fits(GridCouplerParking.View pose){
        for(int body=0;body<geometry.topology.sections.size();body++)for(int axis=0;axis<3;axis++){long shift=pose.translation(body,axis);if(shift< -999_000_000L||shift>999_000_000L)return false;}
        if(++generation==0){Arrays.fill(stamps,0);generation=1;}count=0;minX=minY=minZ=Long.MAX_VALUE;maxX=maxY=maxZ=Long.MIN_VALUE;
        for(int i=0;i<fixedX.length;i++)if(!put(fixedX[i]+pose.translation(fixedBody[i],0),fixedY[i]+pose.translation(fixedBody[i],1),fixedZ[i]+pose.translation(fixedBody[i],2),fixedOwner[i],fixedStored[i],-1,pose))return false;
        for(int armIndex=0;armIndex<geometry.arms.size();armIndex++){
            VehicleArmParkingGeometry.Arm arm=geometry.arms.get(armIndex);int option=pose.option(armIndex);if(option<0||option>=arm.options.candidates.size())return false;
            ArmParkingCandidates.Candidate candidate=arm.options.candidates.get(option);long tx=pose.translation(arm.body,0),ty=pose.translation(arm.body,1),tz=pose.translation(arm.body,2);
            for(int i=0;i<payloadX[armIndex].length;i++){
                int x=payloadX[armIndex][i],y=payloadY[armIndex][i],z=payloadZ[armIndex][i];
                long px=tx+candidate.x+(long)candidate.basis(0,0)*x+(long)candidate.basis(0,1)*y+(long)candidate.basis(0,2)*z;
                long py=ty+candidate.y+(long)candidate.basis(1,0)*x+(long)candidate.basis(1,1)*y+(long)candidate.basis(1,2)*z;
                long pz=tz+candidate.z+(long)candidate.basis(2,0)*x+(long)candidate.basis(2,1)*y+(long)candidate.basis(2,2)*z;
                if(!put(px,py,pz,-1,false,armIndex,pose))return false;
            }
        }
        return count>0&&count<=VehicleStructure.MAX_BLOCKS;
    }
    private boolean put(long x,long y,long z,int owner,boolean oldStored,int payload,GridCouplerParking.View pose){
        // The input solver bounds coordinates; retain a local guard before arithmetic/normalization.
        if(x< -1_000_000_000L||x>1_000_000_000L||y< -1_000_000_000L||y>1_000_000_000L||z< -1_000_000_000L||z>1_000_000_000L)return false;
        int slot=hash(x,y,z)&mask;
        while(stamps[slot]==generation){
            if(xs[slot]==x&&ys[slot]==y&&zs[slot]==z){
                if(payload<0||owners[slot]!=payload||!stored[slot]&&pose.option(payload)!=zeroOption[payload])return false;
                owners[slot]=-1;stored[slot]=false;return true;
            }
            slot=(slot+1)&mask;
        }
        minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);
        if(maxX-minX>=VehicleStructure.MAX_AXIS||maxY-minY>=VehicleStructure.MAX_AXIS||maxZ-minZ>=VehicleStructure.MAX_AXIS)return false;
        stamps[slot]=generation;xs[slot]=x;ys[slot]=y;zs[slot]=z;owners[slot]=owner;stored[slot]=oldStored;count++;return true;
    }
    private static int hash(long x,long y,long z){long value=x*73856093L^y*19349663L^z*83492791L;return(int)(value^(value>>>32));}
}

package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.compat.RampStairs;
import com.vandorlabs.ramp.RampGeometry;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.lang.reflect.*;
import java.util.*;

/** Exact ordered parity with the previous object-based deduplication/grouping pipeline. */
final class VehicleCollisionMergeRuntimeChecks {
    private static Constructor<VehicleCollision> reference;
    private static Method deduplicate,merge,oldMerge;
    private static int checks;private static long compared;
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("collision primitive parity: "+message);}
    @SuppressWarnings("unchecked") private static List<AxisAlignedBB> invoke(Method method,List<AxisAlignedBB> boxes,Object... rest)throws Exception{Object[] args=new Object[rest.length+1];args[0]=boxes;System.arraycopy(rest,0,args,1,rest.length);return (List<AxisAlignedBB>)method.invoke(null,args);}
    private static void equal(List<AxisAlignedBB> actual,List<AxisAlignedBB> expected,String context){int mismatch=0;while(mismatch<Math.min(actual.size(),expected.size())&&actual.get(mismatch).equals(expected.get(mismatch)))mismatch++;require(mismatch==actual.size()&&mismatch==expected.size(),context+" first-difference="+mismatch+" sizes="+actual.size()+"/"+expected.size());checks++;compared+=actual.size();}
    private static void raw(List<AxisAlignedBB> input,String context)throws Exception{
        List<AxisAlignedBB> expected=new ArrayList<>(new LinkedHashSet<>(input)),actual=invoke(deduplicate,input);equal(actual,expected,context+" dedup");
        for(int axis=0;axis<3;axis++){actual=invoke(merge,actual,axis);expected=invoke(oldMerge,expected,axis);equal(actual,expected,context+" axis="+axis);}
    }
    static void run(EntityGroundVehicle saved)throws Exception{
        reference=VehicleCollision.class.getDeclaredConstructor(VehicleWorld.class,boolean.class);reference.setAccessible(true);
        deduplicate=VehicleCollision.class.getDeclaredMethod("deduplicate",List.class);deduplicate.setAccessible(true);
        merge=VehicleCollision.class.getDeclaredMethod("merge",List.class,int.class);merge.setAccessible(true);
        oldMerge=VehicleCollision.class.getDeclaredMethod("legacyMerge",List.class,int.class);oldMerge.setAccessible(true);
        VehicleWorld initial=new VehicleWorld(saved.world,saved.structure);
        for(int turn=0;turn<4;turn++){
            VehicleStructure rotated=VehicleRotation.rotate(saved.structure,initial,turn);VehicleWorld view=new VehicleWorld(saved.world,rotated);
            for(int pose=0;pose<=16;pose++){
                for(TileEntity tile:view.tiles.values()){
                    if(tile instanceof TileEntityCanopy){TileEntityCanopy canopy=(TileEntityCanopy)tile;if(canopy.draws()&&canopy.opening())canopy.progress=pose/16F;}
                    if(tile instanceof TileEntityLandingGear)((TileEntityLandingGear)tile).progress=(pose%5)/2F;
                }
                VehicleCollision actual=new VehicleCollision(view),expected=reference.newInstance(view,true);
                equal(actual.boxes,expected.boxes,"saved craft quarterturn="+turn+" canopy-pose="+pose+" gear-pose="+(pose%5));require(actual.bounds.equals(expected.bounds),"full hull bounds differ");
                for(float yaw:new float[]{0,13,45,90,179.5F}){require(actual.bounds(yaw).equals(expected.bounds(yaw)),"rotated bounds differ");List<VehicleCollision.OrientedBox> a=actual.pose(yaw),b=expected.pose(yaw);require(a.size()==b.size(),"oriented box count differs");for(int i=0;i<a.size();i++)require(a.get(i).broad.equals(b.get(i).broad),"oriented broad box differs");}
            }
        }
        List<AxisAlignedBB> floor=new ArrayList<>();for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)if(Math.abs(x)>2||Math.abs(z)>2){AxisAlignedBB b=new AxisAlignedBB(x,-2,z,x+1,-1,z+1);floor.add(b);floor.add(b);}
        floor.add(new AxisAlignedBB(-0D,-0D,-0D,1,1,1));floor.add(new AxisAlignedBB(0D,0D,0D,1,1,1));floor.add(new AxisAlignedBB(1+5e-9,0,0,2,1,1));floor.add(new AxisAlignedBB(2+2e-8,0,0,3,1,1));Collections.shuffle(floor,new Random(921));raw(floor,"negative floor cabin-hole signed-zero tiny-gap");
        List<AxisAlignedBB> unusual=new ArrayList<>();unusual.add(new AxisAlignedBB(Double.NaN,0,0,1,1,1));unusual.add(new AxisAlignedBB(Double.longBitsToDouble(0x7ff8000000000001L),0,0,1,1,1));unusual.add(new AxisAlignedBB(0,0,0,1,1,1){});unusual.add(new AxisAlignedBB(0,0,0,1,1,1));raw(unusual,"canonical-NaN foreign-subclass-fallback");
        for(int count=1;count<=12;count++){
            List<AxisAlignedBB> tiny=new ArrayList<>();for(int i=count-1;i>=0;i--)tiny.add(new AxisAlignedBB(i/4D,0,0,i/4D+.5,1,1));
            tiny.add(new AxisAlignedBB(-0D,0,0,.125,1,1));tiny.add(new AxisAlignedBB(0D,0,0,.25,1,1));raw(tiny,"stable small-group boundary count="+count);
        }
        List<AxisAlignedBB> subclassUnion=new ArrayList<>();subclassUnion.add(new AxisAlignedBB(0,0,0,4,1,1){@Override public AxisAlignedBB union(AxisAlignedBB box){return super.union(box).offset(.125,0,0);}});subclassUnion.add(new AxisAlignedBB(1,0,0,2,1,1));raw(subclassUnion,"foreign subclass virtual union retained");
        int stairCases=0;
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values())for(BlockStairs.EnumShape shape:BlockStairs.EnumShape.values()){
            IBlockState state=Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half).withProperty(BlockStairs.SHAPE,shape);
            for(int pixels:new int[]{2,8,16})for(double pose:new double[]{0,.375,1}){
                List<AxisAlignedBB> boxes=new ArrayList<>();
                for(RampGeometry.Box box:RampStairs.treads(saved.world,state,RampGeometry.Direction.valueOf(facing.name()),new BlockPos(-15,-3,27),2,5,-1,3,pixels,pose,false,0,true,false,1)){
                    AxisAlignedBB b=new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);boxes.add(b);boxes.add(b);
                }
                raw(boxes,"native stairs shaped ramp facing="+facing+" half="+half+" shape="+shape+" pixels="+pixels+" pose="+pose);stairCases++;
            }
        }
        System.out.println("[vandorlabs][reprolab] vehicle-collision-primitive-parity PASS checks="+checks+" ordered-boxes="+compared+" saved-craft-poses=68 four-cardinals 17-canopy-poses changed-gear-state native-stair-ramp-cases="+stairCases+" negative-floor-hole signed-zero tiny-gap canonical-NaN subclass-fallback exact-bounds-and-oriented-boxes");
    }
}

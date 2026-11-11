package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableStairs;
import com.vandorlabs.blocks.HousingBlockState;
import com.vandorlabs.blocks.ProgrammableHousingState;
import com.vandorlabs.blocks.ProgrammableStairGeometry;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Exact native dispatch parity and allocation for rejecting translated stair cells. */
public final class StairCollisionQueryChecks {
    private static volatile Object consumed;
    private static void need(boolean condition,String message){if(!condition)throw new AssertionError(message);}

    /** Frozen dispatch from e8cc78a1. Both versions use the same geometry adapter,
     * so this comparison isolates the collision loop from the separate shape cache. */
    private static class Before extends BlockStairs {
        Before(){super(Blocks.IRON_BLOCK.getDefaultState());}
        @Override protected BlockStateContainer createBlockState(){
            return new HousingBlockState(this,new IProperty[]{FACING,HALF,SHAPE},
                    new IUnlistedProperty[]{ProgrammableHousingState.FINISH,ProgrammableHousingState.SIDE_FINISH,ProgrammableHousingState.FACES,
                            ProgrammableHousingState.TILE_SIDES,ProgrammableHousingState.STAIR_GEOMETRY});
        }
        @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){return ProgrammableStairGeometry.settings(world,pos)>>5!=0?state.withProperty(SHAPE,EnumShape.STRAIGHT):super.getActualState(state,world,pos);}
        @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> out,Entity entity,boolean actual){
            int settings=ProgrammableStairGeometry.settings(world,pos);
            if(settings==8){super.addCollisionBoxToList(state,world,pos,query,out,entity,actual);return;}
            for(AxisAlignedBB box:ProgrammableStairGeometry.boxes(getActualState(state,world,pos),settings))if(box.offset(pos).intersects(query))out.add(box.offset(pos));
        }
    }
    private static final class SingleOffset extends Before {
        @Override public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB query,List<AxisAlignedBB> out,Entity entity,boolean actual){
            int settings=ProgrammableStairGeometry.settings(world,pos);
            if(settings==8){super.addCollisionBoxToList(state,world,pos,query,out,entity,actual);return;}
            for(AxisAlignedBB box:ProgrammableStairGeometry.boxes(getActualState(state,world,pos),settings)){
                AxisAlignedBB shifted=box.offset(pos);if(shifted.intersects(query))out.add(shifted);
            }
        }
    }
    private static IBlockState state(BlockStairs block,EnumFacing facing,BlockStairs.EnumHalf half,BlockStairs.EnumShape shape){return block.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half).withProperty(BlockStairs.SHAPE,shape);}
    private static void prepare(NonRenderingChecks.MemoryWorld world,BlockPos pos,IBlockState state,int tread,int roll){
        world.clear();world.states.put(pos,state);
        TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setStairGeometry(tread,roll);tile.setWorld(world);tile.setPos(pos);world.tiles.put(pos,tile);
        BlockStairs.EnumShape corner=state.getValue(BlockStairs.SHAPE);
        if(corner!=BlockStairs.EnumShape.STRAIGHT){
            EnumFacing facing=state.getValue(BlockStairs.FACING);
            boolean outer=corner==BlockStairs.EnumShape.OUTER_LEFT||corner==BlockStairs.EnumShape.OUTER_RIGHT;
            boolean left=corner==BlockStairs.EnumShape.OUTER_LEFT||corner==BlockStairs.EnumShape.INNER_LEFT;
            IBlockState neighbor=Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.HALF,state.getValue(BlockStairs.HALF))
                    .withProperty(BlockStairs.FACING,left?facing.rotateYCCW():facing.rotateY());
            world.states.put(pos.offset(outer?facing:facing.getOpposite()),neighbor);
        }
    }
    private static final class CountingQuery extends AxisAlignedBB {
        int calls;
        CountingQuery(BlockPos pos){super(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY()+1,pos.getZ()+1);}
        @Override public boolean intersects(AxisAlignedBB other){calls++;return super.intersects(other);}
        @Override public boolean intersects(double a,double b,double c,double d,double e,double f){calls++;return super.intersects(a,b,c,d,e,f);}
    }
    private static AxisAlignedBB[] queries(BlockPos pos){
        AxisAlignedBB[] local={
            new AxisAlignedBB(-.1,-.1,-.1,1.1,1.1,1.1),
            new AxisAlignedBB(2,2,2,3,3,3),
            new AxisAlignedBB(.99,0,.99,1.01,1,1.01),
            new AxisAlignedBB(.45,.45,.45,.55,.55,.55),
            new AxisAlignedBB(0,0,0,1,.02,1),
            new AxisAlignedBB(1,0,0,2,1,1),new AxisAlignedBB(-1,0,0,0,1,1),
            new AxisAlignedBB(0,1,0,1,2,1),new AxisAlignedBB(0,-1,0,1,0,1),
            new AxisAlignedBB(0,0,1,1,1,2),new AxisAlignedBB(0,0,-1,1,1,0),
            new AxisAlignedBB(.5,0,0,.5,1,1),
            new AxisAlignedBB(Double.NaN,0,0,1,1,1),
            new AxisAlignedBB(Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY),
            new AxisAlignedBB(Math.nextAfter(1D,Double.NEGATIVE_INFINITY),0,0,Math.nextAfter(1D,Double.POSITIVE_INFINITY),1,1)
        };
        AxisAlignedBB[] result=new AxisAlignedBB[local.length+1];for(int i=0;i<local.length;i++)result[i]=local[i].offset(pos);
        result[local.length]=new CountingQuery(pos);
        return result;
    }
    private static void same(List<AxisAlignedBB> before,List<AxisAlignedBB> after){
        need(before.size()==after.size(),"collision count changed");
        for(int i=0;i<before.size();i++){
            AxisAlignedBB a=before.get(i),b=after.get(i);
            need(bits(a.minX,b.minX)&&bits(a.minY,b.minY)&&bits(a.minZ,b.minZ)&&bits(a.maxX,b.maxX)&&bits(a.maxY,b.maxY)&&bits(a.maxZ,b.maxZ),"ordered collision coordinates changed");
        }
    }
    private static boolean bits(double a,double b){return Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b);}
    private static void matrix(){
        Before before=new Before();BlockProgrammableStairs after=new BlockProgrammableStairs();
        NonRenderingChecks.MemoryWorld oldWorld=new NonRenderingChecks.MemoryWorld(false),newWorld=new NonRenderingChecks.MemoryWorld(false);
        List<AxisAlignedBB> expected=new ArrayList<>(),actual=new ArrayList<>();int cases=0,poses=0;
        for(BlockPos pos:new BlockPos[]{new BlockPos(0,64,0),new BlockPos(29999980,64,-29999980),new BlockPos(-29999980,64,29999980)}){
            AxisAlignedBB[] queries=queries(pos);
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values())for(BlockStairs.EnumShape corner:BlockStairs.EnumShape.values())for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++){
                IBlockState oldState=state(before,facing,half,corner),newState=state(after,facing,half,corner);
                prepare(oldWorld,pos,oldState,tread,roll);prepare(newWorld,pos,newState,tread,roll);
                need(after.getActualState(newState,newWorld,pos).getValue(BlockStairs.SHAPE)==(roll==0?corner:BlockStairs.EnumShape.STRAIGHT),"fixture did not form requested corner");
                for(boolean suppliedActual:new boolean[]{false,true})for(AxisAlignedBB query:queries){
                    expected.clear();actual.clear();
                    CountingQuery counted=query instanceof CountingQuery?(CountingQuery)query:null;
                    if(counted!=null)counted.calls=0;
                    before.addCollisionBoxToList(oldState,oldWorld,pos,query,expected,null,suppliedActual);
                    int beforeCalls=counted==null?0:counted.calls;if(counted!=null)counted.calls=0;
                    after.addCollisionBoxToList(newState,newWorld,pos,query,actual,null,suppliedActual);
                    if(counted!=null){
                        need(counted.calls==beforeCalls,"query subclass dispatch changed");
                        need((tread==8&&roll==0)?counted.calls>0:counted.calls==0,"vanilla/custom query receivers changed");
                    }
                    same(expected,actual);cases++;
                }
                poses++;
            }
        }
        System.out.println("Stair collision query PASS poses="+poses+" cases="+cases+" all600 settings, actual/default dispatch, corners, partial/full/disjoint/exact touches, far coordinates and query subclass");
    }
    private static void benchmark(){
        java.lang.management.ThreadMXBean base=ManagementFactory.getThreadMXBean();if(!(base instanceof com.sun.management.ThreadMXBean))return;
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)base;if(!meter.isThreadAllocatedMemorySupported()||!meter.isCurrentThreadCpuTimeSupported())return;
        meter.setThreadAllocatedMemoryEnabled(true);meter.setThreadCpuTimeEnabled(true);long thread=Thread.currentThread().getId();
        BlockStairs[] blocks={new Before(),new SingleOffset(),new BlockProgrammableStairs()};
        NonRenderingChecks.MemoryWorld[] worlds=new NonRenderingChecks.MemoryWorld[blocks.length];IBlockState[] states=new IBlockState[blocks.length];
        BlockPos pos=new BlockPos(8,64,8);List<AxisAlignedBB> out=new ArrayList<>(256);
        for(int i=0;i<blocks.length;i++){worlds[i]=new NonRenderingChecks.MemoryWorld(false);states[i]=blocks[i].getDefaultState();prepare(worlds[i],pos,states[i],1,0);}
        AxisAlignedBB[] queries={new AxisAlignedBB(2,2,2,3,3,3).offset(pos),new AxisAlignedBB(.99,0,.99,1.01,1,1.01).offset(pos)};
        int calls=200;
        for(int profile=0;profile<queries.length;profile++){
            long[][] cpu=new long[3][21],allocated=new long[3][21];int accepted=-1;
            for(int sample=-20;sample<21;sample++)for(int order=0;order<3;order++){
                int algorithm=Math.floorMod(sample+order,3);long bytes=meter.getThreadAllocatedBytes(thread),start=meter.getCurrentThreadCpuTime();
                for(int i=0;i<calls;i++){out.clear();blocks[algorithm].addCollisionBoxToList(states[algorithm],worlds[algorithm],pos,queries[profile],out,null,false);consumed=out;}
                long elapsed=meter.getCurrentThreadCpuTime()-start,used=meter.getThreadAllocatedBytes(thread)-bytes;
                if(accepted<0)accepted=out.size();else need(out.size()==accepted,"benchmark collision counts differ");
                if(sample>=0){cpu[algorithm][sample]=elapsed;allocated[algorithm][sample]=used;}
            }
            for(int algorithm=0;algorithm<3;algorithm++){
                Arrays.sort(cpu[algorithm]);Arrays.sort(allocated[algorithm]);
                System.out.printf(Locale.ROOT,"stair-query profile=%s algorithm=%s calls=%d accepted-per-query=%d cpu-median-ns=%d allocated-median-bytes=%d%n",profile==0?"disjoint":"one-cell",algorithm==0?"original":algorithm==1?"single-offset":"scalar-rejection",calls,accepted,cpu[algorithm][10],allocated[algorithm][10]);
            }
        }
    }
    public static void main(String[] ignored){net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();matrix();benchmark();}
}

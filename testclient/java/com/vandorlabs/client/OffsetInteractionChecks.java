package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Current world contents must be sampled even when candidate coordinates are cached. */
final class OffsetInteractionChecks {
    static void run() {
        int cases=0;Random random=new Random(93182);
        for(int shape=0;shape<4;shape++)for(boolean open:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.HORIZONTALS) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
            BlockPos pos=new BlockPos(15,100,8);
            BlockProgrammableTrapdoor block=(BlockProgrammableTrapdoor)(shape==0?ModBlocks.PROGRAMMABLE_TRAPDOOR:ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR);
            IBlockState state=block.getDefaultState().withProperty(BlockProgrammableTrapdoor.FACING,facing).withProperty(BlockProgrammableTrapdoor.OPEN,open);
            for(int x=0;x<2;x++) {
                BlockPos at=pos.east(x);world.setBlockState(at,state,2);
                TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)world.getTileEntity(at);
                leaf.configure(0,Math.max(0,shape-1),open,0,0);
                if(shape==0)leaf.setCover(true);
                world.states.put(at,state);
            }
            AxisAlignedBB bounds=OffsetTrapdoorInteractions.bounds((TileEntityProgrammableTrapdoor)world.getTileEntity(pos));
            Vec3d center=bounds.getCenter();
            for(int ray=0;ray<24;ray++) {
                Vec3d offset=new Vec3d(random.nextDouble()*6-3,random.nextDouble()*6-3,random.nextDouble()*6-3);
                Vec3d start=center.add(offset),end=center.subtract(offset);
                compare(world,start,end,bounds.grow(.1));cases++;
                world.chunkLimit=true;compare(world,start,end,bounds.grow(.1));cases++;world.chunkLimit=false;
            }
            Vec3d start=center.addVector(0,3,0),end=center.addVector(0,-3,0);
            compare(world,start,end,bounds.grow(.1));world.clear();
            compare(world,start,end,bounds.grow(.1));cases+=2;
        }
        System.out.println("PASS: "+cases+" offset ray/collision comparisons retain current world changes, loaded boundaries and exact hit precedence");
    }
    private static void compare(NonRenderingChecks.MemoryWorld world,Vec3d start,Vec3d end,AxisAlignedBB query) {
        RayTraceResult expected=ReferenceOffsetTrapdoorInteractions.trace(world,start,end),actual=OffsetTrapdoorInteractions.trace(world,start,end);
        if(expected==null?actual!=null:actual==null || !expected.getBlockPos().equals(actual.getBlockPos())
                || expected.sideHit!=actual.sideHit || !expected.hitVec.equals(actual.hitVec))throw new AssertionError("offset ray hit changed");
        List<AxisAlignedBB> before=new ArrayList<>(),after=new ArrayList<>();
        ReferenceOffsetTrapdoorInteractions.addCollisions(world,query,before);OffsetTrapdoorInteractions.addCollisions(world,query,after);
        if(!before.equals(after))throw new AssertionError("offset collision lists differ");
    }
}

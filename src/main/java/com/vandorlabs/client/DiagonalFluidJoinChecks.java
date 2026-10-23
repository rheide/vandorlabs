package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;
import java.util.*;

/** A wet flush glass/porthole join must not retain a water sheet over its solid footprint. */
final class DiagonalFluidJoinChecks {
    static void run(Minecraft mc,java.io.File output)throws Exception{
        World world=mc.world;BlockPos pos=new BlockPos(330,48,146);int cases=0,vertices=0;boolean flushStrip=false;
        Block glass=Block.getBlockFromName("vandorlabs:programmable_diagonal_glass"),porthole=Block.getBlockFromName("vandorlabs:programmable_porthole_wall");
        try{for(int mode=0;mode<2;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(boolean upper:new boolean[]{false,true})for(int depth=0;depth<3;depth++){
            BlockPos join=upper?pos.up():pos.down();
            IBlockState state=glass.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
            world.setBlockState(pos,state,2);((TileEntityAnimatedScreenSelector)world.getTileEntity(pos)).setDiagonalGeometry(mode,0);
            world.setBlockState(join,porthole.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.DEPTH,depth),2);
            if(Double.isNaN(BlockProgrammableWall.flatEnd(state,world,pos,upper))){world.setBlockToAir(join);world.setBlockToAir(pos);continue;}
            for(int side=0;side<2;side++){
                ContainedFluids data=WaterloggedParts.data(world.getTileEntity(pos));data.set(0,side==0?new FluidStack(FluidRegistry.WATER,1000):null);data.set(1,side==1?new FluidStack(FluidRegistry.WATER,1000):null);
                EnumFacing face=side==0?facing:facing.getOpposite();BlockPos nativePos=pos.offset(face),nativeJoin=join.offset(face);
                world.setBlockState(nativePos,Blocks.WATER.getDefaultState(),2);world.setBlockState(nativeJoin,Blocks.WATER.getDefaultState(),2);
                List<double[][]> quads=NativeFluidContactChecks.render(mc,world,nativePos,world.getBlockState(nativePos));
                for(double[][] quad:quads)for(double[] p:quad){p[0]+=face.getFrontOffsetX();p[1]+=face.getFrontOffsetY();p[2]+=face.getFrontOffsetZ();}
                int axis=face.getAxis()==EnumFacing.Axis.X?0:2;double edge=face.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE?1:0;
                vertices+=NativeFluidContactChecks.assertStill(quads,axis,edge,new FluidStack(FluidRegistry.WATER,1000));
                if(mode==0&&facing==EnumFacing.NORTH&&inverted&&upper&&depth==1&&side==0){
                    for(double[][] quad:quads){boolean strip=true;double high=0;for(double[] p:quad){strip&=Math.abs(p[axis]-edge)<.003;high=Math.max(high,p[1]);}flushStrip|=strip&&high>.95;}
                    ContactRenderCapture.save(mc,output,"half-width-glass-porthole-water-top",pos,join,nativePos,nativeJoin);
                }
                world.setBlockToAir(nativePos);world.setBlockToAir(nativeJoin);cases++;
            }
            world.setBlockToAir(join);world.setBlockToAir(pos);
        }}finally{for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)world.setBlockToAir(pos.add(x,y,z));}
        require(cases==64&&!flushStrip,"water sheet remains over the half-width top/near glass-porthole join");
        System.out.println("[vandorlabs][reprolab] diagonal-fluid-joins PASS cases="+cases+" stillVertices="+vertices+" half/full-width four facings inversions upper/lower portholes both wet sides no top/near pane-footprint strip");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}

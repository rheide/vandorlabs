package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.List;

/** Check routing against emitted geometry, independently of the containment formula. */
final class DiagonalBoundaryChecks {
    static void mesh(BlockProgrammableWall block,IBlockState state,int mode,List<BakedQuad> quads) {
        double[] low={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
        double[] high={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        float[] point=new float[4];
        for(BakedQuad quad:quads)for(int v=0;v<4;v++) {
            LightUtil.unpack(quad.getVertexData(),point,quad.getFormat(),v,0);
            for(int axis=0;axis<3;axis++){low[axis]=Math.min(low[axis],point[axis]);high[axis]=Math.max(high[axis],point[axis]);}
        }
        for(int x:new int[]{0,1,14,15})for(int y:new int[]{0,1,14,15})for(int z:new int[]{0,1,14,15}) {
            BlockPos pos=new BlockPos(x,y,z);
            if(!DiagonalWallState.baked(block,pos,block.getMetaFromState(state),mode==2,mode==1))continue;
            int[] local={x,y,z};
            for(int axis=0;axis<3;axis++)require(local[axis]+low[axis]>=-1E-6 && local[axis]+high[axis]<=16+1E-6,
                    "baked mesh crosses section: "+state+" mode="+mode+" pos="+pos+" axis="+axis+" bounds="+low[axis]+","+high[axis]);
        }
    }
    static void routing(BlockProgrammableWall block) {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++) {
            IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
            int count=0;
            for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++) {
                boolean baked=DiagonalWallState.baked(block,new BlockPos(x,y,z),block.getMetaFromState(state),mode==2,mode==1);
                require(baked==DiagonalWallState.baked(block,new BlockPos(x-32,y+64,z-16),block.getMetaFromState(state),mode==2,mode==1),"negative coordinate routing differs");
                if(x>0 && x<15 && y>0 && y<15 && z>0 && z<15)require(baked,"interior wall lost chunk rendering");
                if(baked)count++;
            }
            require(count==(mode==0?3360:mode==1?3136:3840),"unexpected conservative coverage: "+count);
            for(BlockPos pos:new BlockPos[]{new BlockPos(0,80,0),new BlockPos(8,80,8),new BlockPos(15,95,15),new BlockPos(-1,95,-1),new BlockPos(8,95,8)}) {
                world.clear();world.setBlockState(pos,state,2);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
                tile.setDiagonalGeometry(mode,0);
                agree(world,block,state,pos,tile);
                net.minecraft.nbt.NBTTagCompound update=tile.getUpdateTag();
                update.setBoolean("DiagonalHalfHeight",!tile.isDiagonalHalfHeight());
                tile.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,update));
                agree(world,block,state,pos,tile);
                require(pos.add(-1,-1,-1).equals(world.renderMin) && pos.add(1,1,1).equals(world.renderMax),"boundary packet did not invalidate geometry");
            }
        }
        System.out.println("PASS: diagonal boundary containment, negative coordinates, model/tile agreement and packet routing changes");
    }
    private static void agree(NonRenderingChecks.MemoryWorld world,BlockProgrammableWall block,IBlockState state,BlockPos pos,TileEntityAnimatedScreenSelector tile) {
        boolean baked=((IExtendedBlockState)block.getExtendedState(state,world,pos)).getValue(DiagonalWallState.PROPERTY)!=null;
        require(baked!=tile.shouldRenderInPass(0),"chunk and tile paths disagree at "+pos);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

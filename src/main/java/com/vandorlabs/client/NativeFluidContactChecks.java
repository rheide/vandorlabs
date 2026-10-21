package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Verify actual vanilla liquid vertices against independently sampled collision-free wet/dry space. */
final class NativeFluidContactChecks {
    static void run(Minecraft mc,java.io.File output)throws Exception{
        World world=mc.world;BlockPos pos=new BlockPos(310,36,142);int cases=0,wetSamples=0,drySamples=0,stillVertices=0;
        try{
            for(String id:new String[]{"programmable_wall","programmable_porthole_wall","programmable_glass","programmable_diagonal_wall","programmable_diagonal_porthole","programmable_diagonal_glass","programmable_door","programmable_trapdoor","programmable_diagonal_trapdoor"})
            for(int mode=0;mode<3;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true}){
                Block block=Block.getBlockFromName("vandorlabs:"+id);IBlockState state=block.getDefaultState();
                if(block instanceof BlockProgrammableWall)state=state.withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted).withProperty(BlockProgrammableWall.DEPTH,mode);
                else if(block instanceof BlockProgrammableGlass)state=state.withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,mode);
                else if(block instanceof BlockConfigurableSpaceDoor)state=state.withProperty(BlockVandorDoor.FACING,facing);
                else state=state.withProperty(BlockProgrammableTrapdoor.FACING,facing);
                world.setBlockState(pos,state,2);TileEntity tile=world.getTileEntity(pos);
                if(tile instanceof TileEntityAnimatedScreenSelector&&((BlockProgrammableWall)block).isDiagonalShape())((TileEntityAnimatedScreenSelector)tile).setDiagonalGeometry(mode,0);
                if(tile instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass)((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile).setPanelFacing(facing);
                ContainedFluids data=WaterloggedParts.data(tile);
                for(Block liquid:new Block[]{Blocks.WATER,Blocks.FLOWING_WATER,Blocks.LAVA,Blocks.FLOWING_LAVA})for(int fill=0;fill<3;fill++){
                    Fluid fluid=liquid==Blocks.WATER||liquid==Blocks.FLOWING_WATER?FluidRegistry.WATER:FluidRegistry.LAVA;
                    data.set(0,fill==1?null:new FluidStack(fluid,1000));data.set(1,fill==0?null:new FluidStack(fluid,1000));
                    for(EnumFacing face:EnumFacing.values()){
                        BlockPos nativePos=pos.offset(face);IBlockState nativeState=liquid.getDefaultState();if(liquid==Blocks.FLOWING_WATER||liquid==Blocks.FLOWING_LAVA)nativeState=nativeState.withProperty(net.minecraft.block.BlockLiquid.LEVEL,4);
                        world.setBlockState(nativePos,nativeState,2);
                        List<double[][]> quads=render(mc,world,nativePos,nativeState);require(!quads.isEmpty(),"ordinary liquid still renders");
                        for(double[][] quad:quads)for(double[] vertex:quad){vertex[0]+=face.getFrontOffsetX();vertex[1]+=face.getFrontOffsetY();vertex[2]+=face.getFrontOffsetZ();}
                        int axis=face.getAxis()==EnumFacing.Axis.X?0:face.getAxis()==EnumFacing.Axis.Y?1:2;double edge=face.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE?1:0;
                        stillVertices+=assertStill(quads,axis,edge,new FluidStack(fluid,1000));
                        // Horizontal boundaries include vanilla's deliberately inset surface height.
                        // Side probes avoid the wall itself and one collision-slice width near slopes.
                        if(axis!=1)for(int a=1;a<8;a++)for(int b=1;b<8;b++){
                            double[] p=new double[3];p[axis]=edge;p[(axis+1)%3]=(a+.17)/8;p[(axis+2)%3]=(b+.29)/8;
                            Vec3d inside=new Vec3d(p[0],p[1],p[2]).add(new Vec3d(face.getDirectionVec()).scale(-.02));
                            boolean gap=false;for(WaterloggedParts.Cell cell:WaterloggedParts.volume(world,pos).cells)if(cell.box.shrink(.006).contains(inside)){gap=true;break;}if(!gap)continue;
                            int side=WaterloggedParts.side(world,pos,inside);boolean wet=WaterloggedParts.fluid(world,pos,side)!=null;
                            boolean drawn=false;for(double[][] quad:quads){boolean boundary=true;for(double[] vertex:quad)boundary&=Math.abs(vertex[axis]-edge)<.003;if(boundary&&contains(quad,p,axis)){drawn=true;break;}}
                            if(wet){require(!drawn,"native fluid border in wet gap "+id+" mode="+mode+" facing="+facing+" inverted="+inverted+" liquid="+liquid+" face="+face+" fill="+fill+" p="+Arrays.toString(p));wetSamples++;}
                            else if(fill<2&&(liquid==Blocks.WATER||liquid==Blocks.LAVA)&&p[1]<.5){ // Below the vanilla free surface; flowing liquid can legitimately be lower.
                                require(drawn,"native fluid face lost across dry gap "+id+" mode="+mode+" liquid="+liquid+" fill="+fill+" face="+face+" p="+Arrays.toString(p));drySamples++;
                            }
                        }
                        if(fill==2)for(WaterloggedSurfaceMesh.Surface surface:WaterloggedSurfaceMesh.get(tile))require(surface.boundary!=face,"contained border against matching ordinary liquid "+id+" face="+face);
                        if(mode==0&&facing==EnumFacing.NORTH&&!inverted&&liquid==Blocks.WATER&&fill==2&&face==EnumFacing.EAST)ContactRenderCapture.save(mc,output,"ordinary-water-contact-"+id,pos,nativePos);
                        world.setBlockToAir(nativePos);cases++;
                    }
                }
                world.setBlockToAir(pos);
            }
        }finally{world.setBlockToAir(pos);for(EnumFacing face:EnumFacing.values())world.setBlockToAir(pos.offset(face));}
        require(wetSamples>1000&&drySamples>1000,"native contact coverage");
        System.out.println("[vandorlabs][reprolab] native-fluid-contacts PASS cases="+cases+" wetSamples="+wetSamples+" drySamples="+drySamples+" stillVertices="+stillVertices+" source/flow water/lava shapes facings inversions six boundaries both renderers");
    }
    static List<double[][]> render(Minecraft mc,World world,BlockPos pos,IBlockState state){
        BufferBuilder buffer=new BufferBuilder(4096);buffer.begin(7,DefaultVertexFormats.BLOCK);mc.getBlockRendererDispatcher().renderBlock(state,pos,world,buffer);buffer.finishDrawing();
        ByteBuffer bytes=buffer.getByteBuffer();int stride=DefaultVertexFormats.BLOCK.getNextOffset();List<double[][]> quads=new ArrayList<>();
        for(int v=0;v<buffer.getVertexCount();v+=4){double[][] quad=new double[4][5];for(int k=0;k<4;k++){int at=(v+k)*stride;quad[k]=new double[]{bytes.getFloat(at)-pos.getX(),bytes.getFloat(at+4)-pos.getY(),bytes.getFloat(at+8)-pos.getZ(),bytes.getFloat(at+16),bytes.getFloat(at+20)};}quads.add(quad);}return quads;
    }
    static int assertStill(List<double[][]> quads,int axis,double edge,FluidStack fluid){
        net.minecraft.client.renderer.texture.TextureAtlasSprite still=WaterloggedRenderer.sprite(fluid);int vertices=0;
        for(double[][] quad:quads){boolean boundary=true;for(double[] p:quad)boundary&=Math.abs(p[axis]-edge)<.003;if(!boundary)continue;
            for(double[] p:quad){require(p[3]>=still.getMinU()-1e-6&&p[3]<=still.getMaxU()+1e-6&&p[4]>=still.getMinV()-1e-6&&p[4]<=still.getMaxV()+1e-6,"connected fluid interface must use still artwork, including the solid pane footprint");vertices++;}
        }return vertices;
    }
    private static boolean contains(double[][] q,double[] p,int axis){int a=(axis+1)%3,b=(axis+2)%3;for(int i=1;i<3;i++){double[] u=q[0],v=q[i],w=q[i+1];double d=(v[b]-w[b])*(u[a]-w[a])+(w[a]-v[a])*(u[b]-w[b]);if(Math.abs(d)<1e-10)continue;double x=((v[b]-w[b])*(p[a]-w[a])+(w[a]-v[a])*(p[b]-w[b]))/d,y=((w[b]-u[b])*(p[a]-w[a])+(u[a]-w[a])*(p[b]-w[b]))/d;if(x>1e-6&&y>1e-6&&x+y<1-1e-6)return true;}return false;}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}

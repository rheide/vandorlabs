package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;

/** Native cached meshes: exposed thickness must survive solid contacts and flat wall joins. */
final class DiagonalContactChecks {
    static void run(World world,java.io.File output)throws Exception{
        BlockPos pos=new BlockPos(54,12,48);int cases=0;
        java.lang.reflect.Field payload=StaticSurfaceMesh.class.getDeclaredField("vertices");payload.setAccessible(true);
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/space_doors/medium/double_frame_metal");
        try{
            for(String id:new String[]{"programmable_diagonal_wall","programmable_diagonal_porthole","programmable_diagonal_glass"})
            for(int mode=0;mode<3;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(boolean wet:new boolean[]{false,true})for(int fill=0;fill<4;fill++)for(boolean framed:new boolean[]{false,true}){
                Block block=Block.getBlockFromName("vandorlabs:"+id);IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
                world.setBlockState(pos,state,2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);tile.setDiagonalGeometry(mode,fill);tile.setGlassAppearance(1,framed);
                if(wet)for(int side=0;side<2;side++)tile.fluids.set(side,new FluidStack(FluidRegistry.WATER,1000));
                for(int axis=0;axis<3;axis++)for(int end=0;end<2;end++){
                    EnumFacing positive=axis==0?facing.rotateY():axis==1?EnumFacing.UP:facing.getOpposite();BlockPos next=pos.offset(end==0?positive.getOpposite():positive);
                    StaticSurfaceMesh reference=DiagonalWallMeshCache.get(tile,state,sprite,sprite);double before=area((float[])payload.get(reference),axis,end*16);
                    StaticSurfaceMesh referenceFrame=block instanceof BlockProgrammableDiagonalGlass?DiagonalWallMeshCache.frame(tile,state,sprite):null;
                    if(before<1e-5)continue;
                    world.setBlockState(next,Blocks.STONE.getDefaultState(),2);
                    StaticSurfaceMesh touching=DiagonalWallMeshCache.get(tile,state,sprite,sprite);double after=area((float[])payload.get(touching),axis,end*16);
                    require(after>=before-1e-4,"contact cap lost "+id+" mode="+mode+" facing="+facing+" inverted="+inverted+" wet="+wet+" axis="+axis+" end="+end+" before="+before+" after="+after);
                    if(block instanceof BlockProgrammableDiagonalGlass){StaticSurfaceMesh frame=DiagonalWallMeshCache.frame(tile,state,sprite);require(framed?frame!=null&&(fill==3||referenceFrame.vertexCount()==0||frame.vertexCount()>0):frame==null,"glass contact frame setting mode="+mode+" facing="+facing+" inverted="+inverted+" fill="+fill+" framed="+framed+" axis="+axis+" end="+end);}
                    if(block instanceof BlockProgrammableDiagonalGlass&&framed&&fill==0&&axis==0)require(DiagonalWallMeshCache.frame(tile,state,sprite).hasPlaneFace(0,end==0?1.01:14.99,end==0?1:-1),"inner side wall vanished against solid neighbour mode="+mode+" facing="+facing+" end="+end);
                    if(block instanceof BlockProgrammableDiagonalGlass&&framed&&fill==0&&!wet&&!inverted&&facing==EnumFacing.NORTH&&axis==0&&end==1)ContactRenderCapture.save(Minecraft.getMinecraft(),output,"diagonal-glass-solid-interior-"+mode,pos,next);
                    world.setBlockToAir(next);require(Math.abs(area((float[])payload.get(DiagonalWallMeshCache.get(tile,state,sprite,sprite)),axis,end*16)-before)<1e-4,"contact removal failed to restore cap");cases++;
                }
                if(block instanceof BlockProgrammableDiagonalGlass)for(int end=0;end<2;end++){
                    BlockPos next=end==0?pos.down():pos.up();BlockProgrammableWall flat=(BlockProgrammableWall)Block.getBlockFromName("vandorlabs:programmable_porthole_wall");
                    double expected=16*com.vandorlabs.render.DiagonalWallGeometry.near(mode,inverted,end);int depth=0;for(int candidate=1;candidate<3;candidate++)if(Math.abs(PanelDepth.start(candidate)-expected)<Math.abs(PanelDepth.start(depth)-expected))depth=candidate;
                    world.setBlockState(next,flat.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.DEPTH,depth),2);
                    StaticSurfaceMesh mesh=DiagonalWallMeshCache.get(tile,state,sprite,sprite);
                    if(mode<2){require(area((float[])payload.get(mesh),1,end*16)>1e-5,"glass thickness missing at flat porthole join mode="+mode+" end="+end);if(framed&&fill!=3)require(DiagonalWallMeshCache.frame(tile,state,sprite).vertexCount()>0,"flat porthole frame missing");}
                    if(mode<2&&fill==0){require(!Double.isNaN(BlockProgrammableWall.flatEnd(state,world,pos,end==1)),"flat porthole failed to align with diagonal endpoint");if(framed)require(area((float[])payload.get(DiagonalWallMeshCache.frame(tile,state,sprite)),1,end*16+(end==0?-.01:.01))>1e-5,"opaque rim does not reach porthole bottom/top plane");}
                    if(framed&&!wet&&fill==0&&facing==EnumFacing.NORTH&&!inverted&&end==1)ContactRenderCapture.save(Minecraft.getMinecraft(),output,"diagonal-glass-porthole-contact-"+mode,pos,next);
                    world.setBlockToAir(next);cases++;
                }
                world.setBlockToAir(pos);
            }
        }finally{world.setBlockToAir(pos);for(EnumFacing side:EnumFacing.values())world.setBlockToAir(pos.offset(side));}
        System.out.println("[vandorlabs][reprolab] diagonal-contact-matrix PASS cases="+cases+" three shapes wall/porthole/glass four facings inversions dry/wet solid contacts flat portholes removal cache restoration");
    }
    private static double area(float[] data,int axis,double edge){double area=0;for(int at=0;at<data.length;at+=32){boolean on=true;for(int v=0;v<4;v++)on&=Math.abs(data[at+8*v+axis]-edge)<1e-6;if(!on)continue;for(int v=1;v<3;v++){double[] a=new double[3],b=new double[3];for(int k=0;k<3;k++){a[k]=data[at+8*v+k]-data[at+k];b[k]=data[at+8*(v+1)+k]-data[at+k];}double x=a[1]*b[2]-a[2]*b[1],y=a[2]*b[0]-a[0]*b[2],z=a[0]*b[1]-a[1]*b[0];area+=Math.sqrt(x*x+y*y+z*z)/2;}}return area;}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}

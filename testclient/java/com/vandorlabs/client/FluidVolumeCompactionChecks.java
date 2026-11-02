package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Compare the compact cells with the previous Cartesian partition, without
 * allocating the previous implementation's millions of rendering vertices. */
final class FluidVolumeCompactionChecks {
    static void run(){
        long oldCells=0,newCells=0;int cases=0;
        for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean open:new boolean[]{false,true})for(int depth=0;depth<3;depth++){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            BlockPos pos=new BlockPos(8,100,8);
            IBlockState state=ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR.getDefaultState().withProperty(BlockProgrammableTrapdoor.FACING,face).withProperty(BlockProgrammableTrapdoor.OPEN,open);
            world.setBlockState(pos,state,2);
            ((TileEntityProgrammableTrapdoor)world.getTileEntity(pos)).configure(ScreenHousingTextures.DEFAULT_TRAPDOOR,depth,false,com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED,0);
            world.setBlockState(pos,state,2);
            long[] counts=compare(world,pos);oldCells+=counts[0];newCells+=counts[1];cases++;
        }
        for(IBlockState state:new IBlockState[]{ModBlocks.PROGRAMMABLE_SLAB.getDefaultState(),ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState()}){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(8,100,8);world.setBlockState(pos,state,2);compare(world,pos);cases++;
        }
        if(newCells>=oldCells)throw new AssertionError("fluid cells did not compact");
        System.out.println("PASS: fluid-volume-compaction cases="+cases+" exact previous-grid ownership, deferred face counts, fluid cells="+oldCells+"->"+newCells);
    }
    private static long[] compare(NonRenderingChecks.MemoryWorld world,BlockPos pos){
        IBlockState state=world.getBlockState(pos).getActualState(world,pos);List<AxisAlignedBB> global=new ArrayList<>(),solid=new ArrayList<>();
        state.addCollisionBoxToList(world,pos,new AxisAlignedBB(pos),global,null,false);
        for(AxisAlignedBB box:global)if(box.intersects(new AxisAlignedBB(pos)))solid.add(box.offset(-pos.getX(),-pos.getY(),-pos.getZ()).intersect(new AxisAlignedBB(0,0,0,1,1,1)));
        double[] aperture=OpeningFluids.supports(state)?OpeningFluids.plane(world,pos):null;double[][] cuts=new double[3][];
        for(int axis=0;axis<3;axis++){
            TreeSet<Double> values=new TreeSet<>();values.add(0D);values.add(1D);
            if(aperture!=null){values.add(.5);if(Math.abs(aperture[axis])>.999)for(int end=3;end<5;end++){double at=aperture[end]/aperture[axis];if(at>0&&at<1)values.add(at);}}
            for(AxisAlignedBB box:solid){values.add(axis==0?box.minX:axis==1?box.minY:box.minZ);values.add(axis==0?box.maxX:axis==1?box.maxY:box.maxZ);}
            cuts[axis]=values.stream().mapToDouble(Double::doubleValue).toArray();
        }
        int nx=cuts[0].length-1,ny=cuts[1].length-1,nz=cuts[2].length-1;byte[] compact=new byte[Math.multiplyExact(Math.multiplyExact(nx,ny),nz)];Arrays.fill(compact,(byte)-1);
        WaterloggedParts.Volume volume=WaterloggedParts.uncachedVolume(world,pos);
        for(WaterloggedParts.Cell cell:volume.cells){
            int x0=Arrays.binarySearch(cuts[0],cell.box.minX),x1=Arrays.binarySearch(cuts[0],cell.box.maxX),y0=Arrays.binarySearch(cuts[1],cell.box.minY),y1=Arrays.binarySearch(cuts[1],cell.box.maxY),z0=Arrays.binarySearch(cuts[2],cell.box.minZ),z1=Arrays.binarySearch(cuts[2],cell.box.maxZ);
            if(x0<0||x1<=x0||y0<0||y1<=y0||z0<0||z1<=z0)throw new AssertionError("fluid run leaves previous partition");
            for(int x=x0;x<x1;x++)for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++){int i=(x*ny+y)*nz+z;if(compact[i]!=-1)throw new AssertionError("overlapping fluid cells");compact[i]=(byte)cell.side;}
        }
        long previous=0;int expectedFaces=0;
        for(int x=0;x<nx;x++)for(int y=0;y<ny;y++)for(int z=0;z<nz;z++){
            double cx=(cuts[0][x]+cuts[0][x+1])*.5,cy=(cuts[1][y]+cuts[1][y+1])*.5,cz=(cuts[2][z]+cuts[2][z+1])*.5;
            int expected=aperture==null?WaterloggedParts.side(world,pos,new Vec3d(cx,cy,cz)):cx*aperture[0]+cy*aperture[1]+cz*aperture[2]<(aperture[3]+aperture[4])/2?0:1;
            for(AxisAlignedBB box:solid)if(box.contains(new Vec3d(cx,cy,cz))){expected=-1;break;}
            if(compact[(x*ny+y)*nz+z]!=expected)throw new AssertionError("previous fluid ownership changed "+x+","+y+","+z);
            if(expected<0)continue;previous++;
            if(compact.length<=4096)for(EnumFacing face:EnumFacing.values()){int xx=x+face.getFrontOffsetX(),yy=y+face.getFrontOffsetY(),zz=z+face.getFrontOffsetZ();if(xx<0||xx>=nx||yy<0||yy>=ny||zz<0||zz>=nz||compact[(xx*ny+yy)*nz+zz]!=expected)expectedFaces++;}
        }
        if(compact.length<=4096&&volume.faces.size()!=expectedFaces)throw new AssertionError("deferred fluid face mask changed");
        return new long[]{previous,volume.cells.size()};
    }
}

package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.*;
import net.minecraftforge.fluids.*;
final class FluidHeightRegressionChecks {
    static void run(Minecraft mc,java.io.File output)throws Exception{
        BlockPos pos=new BlockPos(350,48,146);
        try{
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)mc.world.setBlockState(pos.add(x,0,z),Blocks.WATER.getDefaultState(),2);
            mc.world.setBlockState(pos.east(),Blocks.STONE.getDefaultState(),2);double vanilla=top(mc,pos);
            mc.world.setBlockState(pos.east(),net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_glass").getDefaultState(),2);
            net.minecraft.tileentity.TileEntity tile=mc.world.getTileEntity(pos.east());for(int side=0;side<2;side++)WaterloggedParts.data(tile).set(side,new FluidStack(FluidRegistry.WATER,1000));
            double joined=top(mc,pos);if(Math.abs(vanilla-joined)>.002||joined>.9)throw new IllegalStateException("Glass raises native free surface: "+vanilla+" -> "+joined);
            for(WaterloggedSurfaceMesh.Surface face:WaterloggedSurfaceMesh.get(tile))if(face.boundary==net.minecraft.util.EnumFacing.UP)for(Vec3d point:face.vertices)if(Math.abs(point.x)<1e-8&&Math.abs(point.y-joined)>.002)throw new IllegalStateException("Contained/native waterline mismatch");
            // A wet column beside falling native water must raise BOTH shared corners,
            // including the wet diagonal-above samples used by vanilla's four-column average.
            mc.world.setBlockState(pos.east().up(),mc.world.getBlockState(pos.east()),2);
            net.minecraft.tileentity.TileEntity upper=mc.world.getTileEntity(pos.east().up());
            for(int side=0;side<2;side++)WaterloggedParts.data(upper).set(side,new FluidStack(FluidRegistry.WATER,1000));
            mc.world.setBlockState(pos,Blocks.FLOWING_WATER.getDefaultState().withProperty(net.minecraft.block.BlockLiquid.LEVEL,8),2);
            boolean sharedTop=false;
            for(double[][] quad:NativeFluidContactChecks.render(mc,mc.world,pos,mc.world.getBlockState(pos))) {
                boolean top=true;for(double[] v:quad)top&=v[1]>.5;
                if(top)for(double[] v:quad)if(v[0]>.999){sharedTop=true;if(v[1]<.998)throw new IllegalStateException("Falling-water gap below wet glass column: "+v[1]);}
            }
            if(!sharedTop)throw new IllegalStateException("Missing flowing-water top");
            ContactRenderCapture.save(mc,output,"glass-stacked-flow",0,pos,pos.east(),pos.east().up());
            mc.world.setBlockToAir(pos.east().up());mc.world.setBlockState(pos,Blocks.WATER.getDefaultState(),2);
            ContactRenderCapture.save(mc,output,"glass-waterline-above",5,pos,pos.east());ContactRenderCapture.save(mc,output,"glass-waterline-below",-5,pos,pos.east());
        }finally{mc.world.setBlockToAir(pos.east().up());for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)mc.world.setBlockToAir(pos.add(x,0,z));}
        System.out.println("[vandorlabs][reprolab] fluid-free-height PASS native stone/glass bank height parity contained surface continuity stacked flowing-water corners grazing-angle captures");
    }
    private static double top(Minecraft mc,BlockPos pos){double top=0;for(double[][] quad:NativeFluidContactChecks.render(mc,mc.world,pos,mc.world.getBlockState(pos)))for(double[] point:quad)top=Math.max(top,point[1]);return top;}
}

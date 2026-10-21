package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;
import java.util.*;

/** All cells sharing a pool corner must agree on its height, including diagonal contacts. */
final class DoorFluidSurfaceChecks {
    static void run(Minecraft mc,java.io.File output)throws Exception{
        World world=mc.world;BlockPos pos=new BlockPos(332,40,142);int cases=0;
        try{for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean open:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(int levels=1;levels<=2;levels++){
            pos=new BlockPos(332+(open?4:0),40,142);
            IBlockState state=net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_door").getDefaultState().withProperty(BlockVandorDoor.FACING,facing).withProperty(BlockVandorDoor.OPEN,open);
            world.setBlockState(pos,state,2);world.setBlockState(pos.up(),state.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);
            ((com.vandorlabs.tiles.TileEntitySpaceDoor)world.getTileEntity(pos)).setPlacementDepth(depth);
            for(int y=0;y<levels;y++)for(int side=0;side<2;side++)WaterloggedParts.data(world.getTileEntity(pos.up(y))).set(side,new FluidStack(FluidRegistry.WATER,1000));
            List<BlockPos> pool=new ArrayList<>();for(int y=0;y<levels;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0){BlockPos at=pos.add(x,y,z);world.setBlockState(at,Blocks.WATER.getDefaultState(),2);pool.add(at);}
            if(levels==1){require(!WaterloggedParts.wet(world,pos.up()),"lower-only water never renders in upper door");require(WaterloggedSurfaceMesh.get(world.getTileEntity(pos.up())).isEmpty(),"upper door emits no water mesh");}
            BlockPos surfacePos=pos.up(levels-1);
            for(WaterloggedSurfaceMesh.Surface surface:WaterloggedSurfaceMesh.get(world.getTileEntity(surfacePos)))if(surface.boundary==EnumFacing.UP)for(net.minecraft.util.math.Vec3d vertex:surface.vertices)require(vertex.y<.9995&&vertex.y>.9985,"exposed water stays below coplanar door frame");
            if(levels==2){for(WaterloggedSurfaceMesh.Surface surface:WaterloggedSurfaceMesh.get(world.getTileEntity(pos)))require(surface.boundary!=EnumFacing.UP,"no internal water cap between door blocks");for(WaterloggedSurfaceMesh.Surface surface:WaterloggedSurfaceMesh.get(world.getTileEntity(pos.up())))require(surface.boundary!=EnumFacing.DOWN,"no internal bottom cap between door blocks");}
            Map<String,Double> heights=new HashMap<>();int shared=0;
            for(BlockPos at:pool)if(at.getY()==surfacePos.getY())for(double[][] quad:NativeFluidContactChecks.render(mc,world,at,world.getBlockState(at))){
                boolean top=true;for(double[] vertex:quad)top&=vertex[1]>.5;if(!top)continue;
                for(double[] vertex:quad){double x=at.getX()-pos.getX()+vertex[0],z=at.getZ()-pos.getZ()+vertex[2];String key=Math.round(x*1000)+":"+Math.round(z*1000);Double previous=heights.put(key,vertex[1]);if(previous!=null){require(Math.abs(previous-vertex[1])<.002,"pool corner height discontinuity "+key+" "+previous+" vs "+vertex[1]+" facing="+facing+" depth="+depth);shared++;}}
            }
            require(shared>8,"pool shares surface corners");
            for(WaterloggedSurfaceMesh.Surface surface:WaterloggedSurfaceMesh.get(world.getTileEntity(surfacePos)))if(surface.boundary==EnumFacing.UP)for(net.minecraft.util.math.Vec3d vertex:surface.vertices){if((Math.abs(vertex.x)<1e-8||Math.abs(vertex.x-1)<1e-8)&&(Math.abs(vertex.z)<1e-8||Math.abs(vertex.z-1)<1e-8)){Double nativeHeight=heights.get(Math.round(vertex.x*1000)+":"+Math.round(vertex.z*1000));require(nativeHeight!=null&&Math.abs(nativeHeight-vertex.y)<.002,"contained and native surface meet");}}
            if(facing==EnumFacing.NORTH&&depth==0)ContactRenderCapture.save(mc,output,"door-"+levels+"-block-pool-"+(open?"open":"closed"),45,pos,pos.up(),pool.toArray(new BlockPos[0]));
            for(BlockPos at:pool)world.setBlockToAir(at);world.setBlockToAir(pos.up());world.setBlockToAir(pos);cases++;
        }}finally{for(int x=-1;x<=1;x++)for(int y=0;y<2;y++)for(int z=-1;z<=1;z++)world.setBlockToAir(pos.add(x,y,z));}
        System.out.println("[vandorlabs][reprolab] door-fluid-surface PASS cases="+cases+" one/two-block pools noncoplanar top surfaces seamless vertical joins all shared corner heights lower-only meshes four facings three depths open/closed");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}

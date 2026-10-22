package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraftforge.fluids.*;
import java.util.*;

/** Interface invariants independent of the fluid renderer's drawing and reference pipeline. */
final class FluidBoundaryMatrixChecks {
    static void run(Minecraft mc){
        BlockPos pos=new BlockPos(350,52,146);int cases=0;List<String> failures=new ArrayList<>();
        String[] ids={"programmable_wall","programmable_glass","programmable_porthole_wall","programmable_diagonal_wall","programmable_diagonal_glass","programmable_diagonal_porthole","programmable_door","programmable_trapdoor","programmable_diagonal_trapdoor","programmable_slab","programmable_stairs"};
        try{for(String id:ids)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int fill=0;fill<3;fill++){
            Block block=Block.getBlockFromName("vandorlabs:"+id);IBlockState state=block.getDefaultState();
            if(state.getPropertyKeys().contains(BlockVandorDoor.FACING))state=state.withProperty(BlockVandorDoor.FACING,facing);
            else if(block instanceof BlockProgrammableWall)state=state.withProperty(BlockProgrammableWall.FACING,facing);
            else if(block instanceof BlockProgrammableGlass)state=state.withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X);
            mc.world.setBlockState(pos,state,2);
            if(block instanceof BlockConfigurableSpaceDoor)mc.world.setBlockState(pos.up(),state.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);
            TileEntity tile=mc.world.getTileEntity(pos);ContainedFluids data=WaterloggedParts.data(tile);
            data.set(0,fill==1?null:new FluidStack(FluidRegistry.WATER,1000));data.set(1,fill==0?null:new FluidStack(FluidRegistry.WATER,1000));
            for(EnumFacing contact:EnumFacing.values()){
                if(block instanceof BlockConfigurableSpaceDoor&&contact==EnumFacing.UP)continue;
                BlockPos neighbor=pos.offset(contact);mc.world.setBlockState(neighbor,Blocks.STONE.getDefaultState(),2);
                for(WaterloggedSurfaceMesh.Surface face:WaterloggedSurfaceMesh.get(tile))if(face.boundary==contact){failures.add(id+" "+facing+" fill="+fill+" water face coplanar with stone "+contact);break;}
                mc.world.setBlockToAir(neighbor);cases++;
            }
            if(block instanceof BlockConfigurableSpaceDoor){
                double[] leaf=OpeningFluids.plane(mc.world,pos);
                for(WaterloggedSurfaceMesh.Surface face:WaterloggedSurfaceMesh.get(tile))if(face.boundary==null)for(Vec3d v:face.vertices){double d=leaf[0]*v.x+leaf[1]*v.y+leaf[2]*v.z;if(Math.min(Math.abs(d-leaf[3]),Math.abs(d-leaf[4]))<.0005){failures.add("door fluid coplanar with leaf "+facing+" side="+face.side);break;}}
            }
            mc.world.setBlockToAir(pos.up());mc.world.setBlockToAir(pos);
        }}finally{for(BlockPos p:BlockPos.getAllInBox(pos.add(-1,-1,-1),pos.add(1,2,1)))mc.world.setBlockToAir(p);}
        if(!failures.isEmpty())throw new IllegalStateException("Fluid boundary matrix failed "+failures.size()+" / "+cases+": "+failures.subList(0,Math.min(12,failures.size())));
        System.out.println("[vandorlabs][reprolab] fluid-boundary-matrix PASS cases="+cases+" eleven families four facings one/two wet sides six solid contacts and closed door leaf separation");
    }
}

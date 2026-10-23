package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraftforge.fluids.*;
import org.lwjgl.util.glu.GLU;
import java.util.*;

/** Visible water must not be culled using the smaller solid pane/slab bounding box. */
final class FluidRenderBoundsChecks {
    static void run(Minecraft mc){
        BlockPos pos=new BlockPos(350,52,146);int cases=0,views=0;List<String> failures=new ArrayList<>();
        String[] ids={"programmable_wall","programmable_glass","programmable_porthole_wall","programmable_diagonal_wall","programmable_diagonal_glass","programmable_diagonal_porthole","programmable_door","programmable_trapdoor","programmable_diagonal_trapdoor","programmable_slab","programmable_stairs"};
        try{
            for(String id:ids)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int fill=0;fill<3;fill++){
                Block block=Block.getBlockFromName("vandorlabs:"+id);IBlockState state=block.getDefaultState();
                if(state.getPropertyKeys().contains(BlockVandorDoor.FACING))state=state.withProperty(BlockVandorDoor.FACING,facing);
                else if(block instanceof BlockProgrammableGlass)state=state.withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X);
                mc.world.setBlockState(pos,state,2);
                if(block instanceof BlockConfigurableSpaceDoor)mc.world.setBlockState(pos.up(),state.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);
                TileEntity tile=mc.world.getTileEntity(pos);ContainedFluids data=WaterloggedParts.data(tile);
                data.set(0,fill==1?null:new FluidStack(FluidRegistry.WATER,1000));data.set(1,fill==0?null:new FluidStack(FluidRegistry.WATER,1000));
                AxisAlignedBB bounds=tile.getRenderBoundingBox().grow(1e-7);boolean outside=false;
                for(WaterloggedSurfaceMesh.Surface face:WaterloggedSurfaceMesh.get(tile))for(Vec3d v:face.vertices)outside|=!bounds.contains(v.addVector(pos.getX(),pos.getY(),pos.getZ()));
                if(outside)failures.add(id+" "+facing+" fill="+fill+" water outside render bounds");
                mc.world.setBlockToAir(pos.up());mc.world.setBlockToAir(pos);cases++;
            }
            GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GLU.gluPerspective(12,1,.01F,10);
            GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
            try{for(boolean rotated:new boolean[]{false,true}){
                IBlockState state=Block.getBlockFromName("vandorlabs:programmable_glass").getDefaultState().withProperty(BlockGlassWall.DEPTH,0).withProperty(BlockGlassWall.ROTATED,rotated);
                mc.world.setBlockState(pos,state,2);TileEntity tile=mc.world.getTileEntity(pos);
                for(int side=0;side<2;side++)WaterloggedParts.data(tile).set(side,new FluidStack(FluidRegistry.WATER,1000));
                for(int turn=-2;turn<=2;turn++){
                    GlStateManager.loadIdentity();float tx=rotated?.05F:.5F+turn*.025F,tz=rotated?.5F+turn*.025F:.95F;
                    GLU.gluLookAt(.5F,1.2F,.5F,tx,.88F,tz,0,1,0);
                    Frustum frustum=new Frustum();frustum.setPosition(pos.getX(),pos.getY(),pos.getZ());
                    AxisAlignedBB visibleWater=new AxisAlignedBB(tx-.02,.86,tz-.02,tx+.02,.89,tz+.02).offset(pos);
                    if(!frustum.isBoundingBoxInFrustum(visibleWater))throw new IllegalStateException("Water frustum fixture misses the visible free surface");
                    if(!frustum.isBoundingBoxInFrustum(tile.getRenderBoundingBox()))failures.add("glass water culled at viewport edge rotated="+rotated+" turn="+turn);
                    views++;
                }
                mc.world.setBlockToAir(pos);
            }}finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);}
        }finally{mc.world.setBlockToAir(pos.up());mc.world.setBlockToAir(pos);}
        if(!failures.isEmpty())throw new IllegalStateException("Water render bounds failures="+failures.size()+" "+failures);
        System.out.println("[vandorlabs][reprolab] fluid-render-bounds PASS cases="+cases+" cameraViews="+views+" eleven families all emitted water vertices enclosed; visible near-edge water survives actual frustum culling");
    }
}

package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockIndustrialLever;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Validate supplied binary artwork against the selectable mounts after baking. */
final class TwinPowerLeverChecks {
    static void checkModels(Minecraft mc){
        int poses=0;
        for(String id:new String[]{"small_power_lever","large_power_lever"}){
            BlockIndustrialLever block=(BlockIndustrialLever)Block.getBlockFromName("vandorlabs:"+id);
            for(EnumFacing face:EnumFacing.Plane.HORIZONTAL)for(boolean floor:new boolean[]{false,true}){
                java.util.Set<String> signatures=new java.util.HashSet<>();
                for(boolean on:new boolean[]{false,true}){
                    IBlockState state=block.getDefaultState().withProperty(BlockIndustrialLever.FACING,face).withProperty(BlockIndustrialLever.FLOOR,floor).withProperty(BlockIndustrialLever.POWERED,on);
                    IBakedModel model=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
                    require(model!=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(),"missing model "+id);
                    AxisAlignedBB bounds=block.getBoundingBox(state,mc.world,BlockPos.ORIGIN).grow(.00002);
                    StringBuilder signature=new StringBuilder();int count=0;
                    for(EnumFacing side:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad quad:model.getQuads(state,side,0)){
                        count++;signature.append(java.util.Arrays.hashCode(quad.getVertexData()));
                        int[] data=quad.getVertexData();int stride=data.length/4;
                        for(int i=0;i<4;i++)require(bounds.contains(new Vec3d(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2]))),"selection excludes artwork "+id+" "+face+" floor="+floor);
                    }
                    require(count>0,"empty lever model "+id);signatures.add(signature.toString());poses++;
                }
                require(signatures.size()==2,"on/off artwork repeated "+id);
            }
        }
        System.out.println("[vandorlabs][reprolab] twin-power-lever-models PASS poses="+poses+" (both sizes, both states, every wall and floor mounting)");
    }
    private static void require(boolean pass,String message){if(!pass)throw new IllegalStateException(message);}
}

package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraftforge.common.property.IExtendedBlockState;

/** Baked geometry must fit the server shape and every configured inventory icon. */
final class MountedBinaryControlChecks {
    static void checkModels(Minecraft mc){
        int poses=0,icons=0;
        for(String id:new String[]{"small_power_lever","industrial_power_lever","toggle_switch"}){
            Block block=Block.getBlockFromName("vandorlabs:"+id);boolean lever=block instanceof BlockIndustrialLever;
            for(EnumFacing face:EnumFacing.values())for(int rotation=0;rotation<(face.getAxis()==EnumFacing.Axis.Y?4:1);rotation++)for(int size=0;size<(lever?2:1);size++)for(boolean on:new boolean[]{false,true}){
                if(lever && face==EnumFacing.DOWN)continue;
                IBlockState state=block.getDefaultState();
                if(lever)state=state.withProperty(BlockIndustrialLever.FACING,face==EnumFacing.UP?EnumFacing.getHorizontal((rotation+2)%4):face).withProperty(BlockIndustrialLever.FLOOR,face==EnumFacing.UP).withProperty(BlockIndustrialLever.POWERED,on);
                else state=state.withProperty(BlockVandorSwitch.FACING,face).withProperty(BlockVandorSwitch.ROTATION,rotation).withProperty(BlockVandorSwitch.ON,on);
                IBakedModel model=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
                require(model!=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(),"missing binary mounted model");
                for(int mount=0;mount<64;mount++){
                    IBlockState extended=((IExtendedBlockState)state).withProperty(MountedControlGeometry.MOUNT,mount);if(lever)extended=((IExtendedBlockState)extended).withProperty(BlockIndustrialLever.SIZE,size);
                    java.util.List<AxisAlignedBB> pieces=MountedControlGeometry.boxes(state,size,mount);AxisAlignedBB bounds=pieces.get(0);for(AxisAlignedBB b:pieces)bounds=bounds.union(b);bounds=bounds.grow(.00003);int count=0;
                    for(EnumFacing side:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad q:model.getQuads(extended,side,0)){
                        count++;int[] data=q.getVertexData();int stride=data.length/4;for(int i=0;i<4;i++)require(bounds.contains(new Vec3d(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2]))),"binary render/shape mismatch "+id+" "+face+" rot="+rotation+" size="+size+" mount="+mount);
                    }
                    require(count>0,"empty mounted binary model");
                    if(mount%16>0){SignalControlMount transform=MountedControlGeometry.transform(state,size,mount);for(AxisAlignedBB support:MountedControlGeometry.nativePose(state,size).supports){Vec3d top=transform.transform(support.getCenter()),middle=top.add(transform.project(top)).scale(.5);require(pieces.stream().anyMatch(b->b.grow(.00001).contains(middle)),"binary pedestal lacks collision");}}
                    poses++;
                }
            }
            for(int size=0;size<(lever?2:1);size++)for(int mount=0;mount<64;mount++){
                ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();tag.setInteger("ControlMountVersion",2);tag.setInteger("BaseHeight",mount%4);tag.setInteger("BaseTilt",mount/4%4);tag.setInteger("TiltDirection",mount/16);tag.setInteger("PowerLeverSize",size);stack.setTagInfo("RedstoneChannelSettings",tag);
                IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);ItemTransformVec3f gui=model.getItemCameraTransforms().gui;
                for(BakedQuad q:model.getQuads(null,null,0)){int[] data=q.getVertexData();int stride=data.length/4;for(int i=0;i<4;i++){
                    Vec3d p=ControlItemPadding.project(new Vec3d(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2])),gui).addVector(.5+gui.translation.x,.5+gui.translation.y,0);
                    require(p.x>.035 && p.x<.965 && p.y>.035 && p.y<.965,"binary configured icon padding "+id+" size="+size+" mount="+mount+" "+p);
                }}icons++;
            }
        }
        System.out.println("[vandorlabs][reprolab] binary-control-mounts PASS poses="+poses+" icons="+icons+" (native cuboids, upright filled bases, size variants and GUI transforms)");
    }
    private static void require(boolean pass,String message){if(!pass)throw new IllegalStateException(message);}
}

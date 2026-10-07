package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.common.property.IExtendedBlockState;

/** Compare every configured baked pose with the shared server selection and support plane. */
final class SignalControlMountChecks {
    static void checkModels(Minecraft mc){
        io.netty.buffer.ByteBuf buffer=io.netty.buffer.Unpooled.buffer();
        com.vandorlabs.network.MessageRedstoneChannel packet=new com.vandorlabs.network.MessageRedstoneChannel(BlockPos.ORIGIN,14861).withControlLimits(true,6,8).withControlMount(true,2,3,1).withChannels(com.vandorlabs.redstone.ChannelList.of(14861,14862,14863));
        packet.toBytes(buffer);com.vandorlabs.network.MessageRedstoneChannel decoded=new com.vandorlabs.network.MessageRedstoneChannel();decoded.fromBytes(buffer);
        require(packet.getRedstoneChannels().equals(decoded.getRedstoneChannels()) && !buffer.isReadable(),"mount extension breaks channel list codec");buffer.release();
        buffer=io.netty.buffer.Unpooled.buffer();new com.vandorlabs.network.MessageRedstoneChannel(BlockPos.ORIGIN,14861).withControlMount(true,3,0,0).toBytes(buffer);decoded=new com.vandorlabs.network.MessageRedstoneChannel();decoded.fromBytes(buffer);require(decoded.getRedstoneChannels()==null,"invalid mount packet accepted");buffer.release();
        int poses=0,icons=0;
        for(String id:new String[]{"thruster_lever","airliner_throttle","fighter_throttle"}){
            BlockSignalControl block=(BlockSignalControl)Block.getBlockFromName("vandorlabs:"+id);
            for(EnumFacing facing:EnumFacing.values())for(int rotation=0;rotation<(facing.getAxis()==EnumFacing.Axis.Y?4:1);rotation++)for(int level=0;level<4;level++){
                IBlockState pose=block.getDefaultState().withProperty(BlockVandorSwitch.FACING,facing).withProperty(BlockVandorSwitch.ROTATION,rotation).withProperty(BlockSignalControl.LEVEL,level);
                IBakedModel model=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(pose);
                for(int h=0;h<3;h++)for(int tilt=0;tilt<4;tilt++)for(int direction=0;direction<4;direction++){
                    IBlockState extended=((IExtendedBlockState)pose).withProperty(BlockSignalControl.MOUNT,h+3*(tilt+4*direction));
                    AxisAlignedBB expected=new SignalControlMount(facing,rotation,h,tilt,direction,block.supportBounds(pose)).bounds(block.originalBounds(pose));
                    int count=0;
                    for(EnumFacing side:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad q:model.getQuads(extended,side,0)){
                        int[] data=q.getVertexData();int stride=data.length/4;count++;
                        for(int i=0;i<4;i++){Vec3d p=new Vec3d(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2]));require(expected.grow(.00001).contains(p),"selection excludes model "+id+" "+facing+" "+h+"/"+tilt+"/"+direction);double depth=p.subtract(new Vec3d(.5,.5,.5).subtract(new Vec3d(facing.getDirectionVec()).scale(.5))).dotProduct(new Vec3d(facing.getDirectionVec()));require(depth>=-.00001,"model intersects support "+id);}
                    }
                    require(count>0,"empty configured model");poses++;
                }
            }
            for(int h=0;h<3;h++)for(int tilt=0;tilt<4;tilt++)for(int direction=0;direction<4;direction++){
                ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();tag.setInteger("BaseHeight",h);tag.setInteger("BaseTilt",tilt);tag.setInteger("TiltDirection",direction);stack.setTagInfo("RedstoneChannelSettings",tag);
                IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);require(!model.getQuads(null,null,0).isEmpty(),"empty configured item");icons++;
            }
        }
        System.out.println("[vandorlabs][reprolab] signal-control-mounts PASS poses="+poses+" icons="+icons+" (all heights, tilts, directions, detents and mounts; selection encloses artwork and no support penetration)");
    }
    private static void require(boolean pass,String message){if(!pass)throw new IllegalStateException(message);}
}

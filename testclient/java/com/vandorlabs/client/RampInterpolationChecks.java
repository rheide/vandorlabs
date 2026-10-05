package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.network.MessageRampController;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import io.netty.buffer.*;

final class RampInterpolationChecks {
    static void run() {
        try {
            java.lang.reflect.Field field=MessageRampController.class.getDeclaredField("interpolation");field.setAccessible(true);
            for(int style=0;style<3;style++) {
                MessageRampController packet=new MessageRampController(new BlockPos(8,100,8),4,2,false,true,false,false,EnumFacing.SOUTH)
                        .withInterpolation(style).withChannels(ChannelList.of(41,42));
                ByteBuf bytes=Unpooled.buffer();packet.toBytes(bytes);
                MessageRampController copy=new MessageRampController();copy.fromBytes(bytes.copy());
                require(field.getInt(copy)==style && copy.getRedstoneChannels().equals(ChannelList.of(41,42)),"packet profile/channel round trip");
                // Remove only the new tagged field, retaining an older channel-list payload.
                int tag=-1;
                for(int i=0;i<=bytes.writerIndex()-8;i++)if(bytes.getInt(i)==0x52414D50){tag=i;break;}
                require(tag>=0,"missing tagged field");
                ByteBuf legacy=Unpooled.buffer();legacy.writeBytes(bytes,0,tag);legacy.writeBytes(bytes,tag+8,bytes.writerIndex()-tag-8);
                copy=new MessageRampController();copy.fromBytes(legacy);
                require(field.getInt(copy)==0 && copy.getRedstoneChannels().equals(ChannelList.of(41,42)),"legacy packet changed");
                bytes.release();legacy.release();
                TileEntityRampController controller=new TileEntityRampController();controller.setPos(new BlockPos(8,100,8));controller.interpolation=style;
                NBTTagCompound saved=controller.writeToNBT(new NBTTagCompound());
                TileEntityRampController restored=new TileEntityRampController();restored.readFromNBT(saved);
                require(restored.interpolation==style,"controller NBT profile");saved.removeTag("RampInterpolation");restored.readFromNBT(saved);
                require(restored.interpolation==0,"legacy controller default");
                TileEntityControlledRamp part=new TileEntityControlledRamp();part.setPos(new BlockPos(8,100,8));part.source=net.minecraft.init.Blocks.STONE.getDefaultState();part.interpolation=style;
                saved=part.writeToNBT(new NBTTagCompound());TileEntityControlledRamp restoredPart=new TileEntityControlledRamp();restoredPart.readFromNBT(saved);
                require(restoredPart.interpolation==style,"part NBT profile");
            }
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        System.out.println("PASS: all ramp profiles persist in controllers/parts and tagged packets; legacy defaults and multi-channel payloads preserved");
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("ramp interpolation: "+message);}
}

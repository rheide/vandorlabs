package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Live Forge checks for receiver polarity, persistence and packet encoding. */
final class RedstoneInversionRuntimeChecks {
    static void run(World world) {
        BlockPos pos=new BlockPos(40,100,40);
        int displays=0,propulsion=0;
        for(Block block:Block.REGISTRY) {
            if(!(block instanceof BlockAnimatedScreenSelector) && !(block instanceof BlockPropulsionLight))continue;
            world.setBlockState(pos,block.getDefaultState(),2);
            net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);
            if(raw instanceof TileEntityAnimatedScreenSelector && !(raw instanceof TileEntityProgrammableLight)
                    && !(raw instanceof TileEntityProgrammableTrigger) && !(raw instanceof TileEntityProgrammableStorage)) {
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)raw;
                tile.setDisplayMode(2);tile.setRedstoneEnabled(true);
                for(boolean inverted:new boolean[]{false,true}) {
                    tile.setRedstoneInverted(inverted);
                    for(int level:new int[]{0,15}) {
                        tile.setChannelLevel(level);
                        require(tile.getEffectiveMode()==((level>0)!=inverted?2:0),"display polarity "+block.getRegistryName());
                    }
                }
                NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());
                TileEntityAnimatedScreenSelector restored=new TileEntityAnimatedScreenSelector();restored.readFromNBT(tag);
                require(restored.isRedstoneInverted(),"display NBT");
                tag.removeTag("RedstoneInverted");restored.readFromNBT(tag);require(!restored.isRedstoneInverted(),"display legacy NBT");
                displays++;
            } else if(raw instanceof TileEntityRedstoneLight) {
                TileEntityRedstoneLight tile=(TileEntityRedstoneLight)raw;
                tile.setRedstoneChannel(17329);
                for(boolean inverted:new boolean[]{false,true}) {
                    tile.setRedstoneInverted(inverted);
                    for(int level:new int[]{0,1,7,14,15}) {
                        tile.setChannelLevel(level);tile.configureSignalBrightness(false,8);
                        require(world.getBlockState(pos).getValue(BlockPropulsionLight.POWERED)==((level>0)!=inverted),"propulsion on/off polarity");
                        tile.configureSignalBrightness(true,8);tile.setParticleLevel(1);
                        require(tile.getBrightness()==(inverted?15-level:level),"propulsion brightness "+block.getRegistryName());
                        require(tile.getActivationLevel()==(inverted?15-level:level),"propulsion level");
                        require(tile.isParticleStreamSelected()==((inverted?15-level:level)>=8),"propulsion particle threshold");
                    }
                }
                NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());
                TileEntityRedstoneLight restored=new TileEntityRedstoneLight();restored.readFromNBT(tag);
                require(restored.isRedstoneInverted(),"propulsion NBT");
                tag.removeTag("RedstoneInverted");restored.readFromNBT(tag);require(!restored.isRedstoneInverted(),"propulsion legacy NBT");
                propulsion++;
            }
            world.setBlockToAir(pos);
        }
        MessageSyncScreenSelector packet=new MessageSyncScreenSelector(pos,"screen_off",true,2,false,1,"screen_off").withRedstoneInverted(true);
        io.netty.buffer.ByteBuf buf=Unpooled.buffer();packet.toBytes(buf);
        MessageSyncScreenSelector decoded=new MessageSyncScreenSelector();decoded.fromBytes(buf);
        require(decoded.isRedstoneInverted(),"display packet");buf.release();
        require(displays>0 && propulsion>0,"missing registered receivers");
        System.out.println("[vandorlabs] redstone-inversion-live PASS displays="+displays+" propulsion="+propulsion);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}

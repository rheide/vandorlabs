package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Configuration, legacy defaults and inventory round trips for the merged lever. */
final class PowerLeverRuntimeChecks {
    static void run(World world,EntityPlayer player){
        BlockPos pos=new BlockPos(41,88,41);world.setBlockState(pos.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);
        BlockTwinPowerLever canonical=(BlockTwinPowerLever)Block.getBlockFromName("vandorlabs:small_power_lever");
        for(String id:new String[]{"small_power_lever","large_power_lever"}){
            BlockTwinPowerLever block=(BlockTwinPowerLever)Block.getBlockFromName("vandorlabs:"+id);
            world.setBlockState(pos,block.getDefaultState().withProperty(BlockIndustrialLever.FLOOR,true),3);
            TileEntityRedstoneChannel tile=(TileEntityRedstoneChannel)world.getTileEntity(pos);
            require(tile.getPowerLeverSize()==(id.startsWith("large")?1:0),"legacy default lost");
            for(int size=0;size<2;size++){
                tile.setPowerLeverSize(size);tile.setRedstoneChannels(ChannelList.of(17101,17102));
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntityRedstoneChannel loaded=new TileEntityRedstoneChannel();loaded.readFromNBT(saved);
                require(loaded.getPowerLeverSize()==size && loaded.getRedstoneChannels().equals(tile.getRedstoneChannels()),"NBT round trip");
                ItemStack picked=block.getPickBlock(world.getBlockState(pos),null,world,pos,player);
                require(picked.getItem()==net.minecraft.item.Item.getItemFromBlock(canonical) && picked.getSubCompound("RedstoneChannelSettings").getInteger("PowerLeverSize")==size,"canonical pick size");
                NonNullList<ItemStack> drops=NonNullList.create();block.getDrops(drops,world,pos,world.getBlockState(pos),0);
                require(drops.size()==1 && ItemStack.areItemStackTagsEqual(picked,drops.get(0)),"configured drop");
                tile.setPowerLeverSize(1-size);block.onBlockPlacedBy(world,pos,world.getBlockState(pos),player,picked);require(tile.getPowerLeverSize()==size,"placement lost size");
            }
            world.setBlockToAir(pos);
        }
        world.setBlockToAir(pos.down());System.out.println("[vandorlabs][reprolab] power-lever-size PASS legacy defaults, NBT, channels, pick/drop/placement");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}

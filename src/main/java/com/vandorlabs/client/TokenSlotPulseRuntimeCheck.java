package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockTokenSlot;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Uses real scheduled world ticks to catch removal/reinsertion before a pulse expires. */
final class TokenSlotPulseRuntimeCheck {
    private final WorldServer world;
    private final BlockPos pos=new BlockPos(440,185,440);
    private final long started;
    private final TileEntityTokenSlot tile;
    private boolean replaced;
    private TokenSlotPulseRuntimeCheck(WorldServer world){
        this.world=world;world.getChunkFromBlockCoords(pos);world.setBlockState(pos.south(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);world.setBlockState(pos,net.minecraft.block.Block.getBlockFromName("vandorlabs:data_card_slot").getDefaultState(),2);
        tile=(TileEntityTokenSlot)world.getTileEntity(pos);tile.configure(false,1,1);tile.setRedstoneChannels(com.vandorlabs.redstone.ChannelList.of(64212));tile.setInventorySlotContents(0,new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.DATA_CARD));started=world.getTotalWorldTime();
    }
    static void start(WorldServer world){FMLCommonHandler.instance().bus().register(new TokenSlotPulseRuntimeCheck(world));}
    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event){
        if(event.world!=world||event.phase!=TickEvent.Phase.END)return;long elapsed=world.getTotalWorldTime()-started;
        if(elapsed>=5&&!replaced){tile.clear();tile.setInventorySlotContents(0,new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.DATA_CARD));replaced=true;}
        if(elapsed==22&&com.vandorlabs.redstone.RedstoneChannels.level(world,64212)!=15)throw new IllegalStateException("Replacement token pulse ended at the previous token's deadline");
        if(elapsed<27)return;
        if(tile.getOutputLevel()!=0||com.vandorlabs.redstone.RedstoneChannels.level(world,64212)!=0)throw new IllegalStateException("Replacement token pulse left a channel powered: output="+tile.getOutputLevel()+" channel="+com.vandorlabs.redstone.RedstoneChannels.level(world,64212)+" elapsed="+elapsed+" until="+tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()).getLong("TokenPulseUntil")+" now="+world.getTotalWorldTime());
        tile.clear();world.setBlockToAir(pos);world.setBlockToAir(pos.south());FMLCommonHandler.instance().bus().unregister(this);System.out.println("[vandorlabs][reprolab] token-mount-pulse-rearm PASS real world ticks early deadline replacement insertion and channel release");
    }
}

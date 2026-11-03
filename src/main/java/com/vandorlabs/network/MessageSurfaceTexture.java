package com.vandorlabs.network;

import com.vandorlabs.tiles.*;
import com.vandorlabs.container.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** A static face choice; animation settings remain independently available. */
public final class MessageSurfaceTexture implements IMessage {
    private BlockPos pos;private int slot,choice;
    public MessageSurfaceTexture() { }
    public MessageSurfaceTexture(BlockPos pos,int slot,int choice){this.pos=pos;this.slot=slot;this.choice=choice;}
    @Override public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());slot=b.readInt();choice=b.readInt();}
    @Override public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeInt(slot);b.writeInt(choice);}
    public static final class Handler implements IMessageHandler<MessageSurfaceTexture,IMessage> {
        @Override public IMessage onMessage(MessageSurfaceTexture m,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(m.pos==null || m.slot<0 || m.slot>1 || m.choice< TileEntityAnimatedScreenSelector.CHIP_SURFACE || (m.choice>=0 && !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(m.choice))
                        || !player.world.isBlockLoaded(m.pos) || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player))return;
                TileEntity tile=player.world.getTileEntity(m.pos);
                if(player.openContainer instanceof ContainerAnimatedScreenSelector && tile instanceof TileEntityAnimatedScreenSelector) {
                    ContainerAnimatedScreenSelector c=(ContainerAnimatedScreenSelector)player.openContainer;
                    if(c.getTileEntity()!=tile || !c.canInteractWith(player))return;
                    if(!com.vandorlabs.blocks.RedstoneScreenInteractions.supportsSlot(tile.getBlockType(),m.slot))return;
                    if(m.choice==TileEntityAnimatedScreenSelector.CHIP_SURFACE&&!com.vandorlabs.blocks.ScreenChipSupport.available((TileEntityAnimatedScreenSelector)tile,m.slot))return;
                    TileEntityAnimatedScreenSelector screen=(TileEntityAnimatedScreenSelector)tile;
                    java.util.List<TileEntityAnimatedScreenSelector> targets=(tile.getBlockType().getClass()==com.vandorlabs.blocks.BlockAnimatedScreenSelector.class&&(m.choice==TileEntityAnimatedScreenSelector.CHIP_SURFACE||screen.hasChipSurface(m.slot)))?DisplayEditGroups.members(screen):java.util.Collections.singletonList(screen);
                    for(TileEntityAnimatedScreenSelector target:targets)if(!com.vandorlabs.blocks.ScreenChipGroups.canEdit(player,target))return;
                    for(TileEntityAnimatedScreenSelector target:targets)target.setSurfaceTexture(m.slot,m.choice);

                }
            });return null;
        }
    }
}

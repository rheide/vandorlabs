package com.vandorlabs.network;

import com.vandorlabs.tiles.*;
import com.vandorlabs.container.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Independent slab and porthole edge material. */
public final class MessageSideTexture implements IMessage {
    private BlockPos pos;private int choice;
    public MessageSideTexture() { }
    public MessageSideTexture(BlockPos pos,int choice){this.pos=pos;this.choice=choice;}
    @Override public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());choice=b.readInt();}
    @Override public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeInt(choice);}
    public static final class Handler implements IMessageHandler<MessageSideTexture,IMessage> {
        @Override public IMessage onMessage(MessageSideTexture m,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(m.pos==null || m.choice< -1 || (m.choice!=-1 && !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(m.choice))
                        || !player.world.isBlockLoaded(m.pos) || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player))return;
                TileEntity tile=player.world.getTileEntity(m.pos);
                if(player.openContainer instanceof ContainerAnimatedScreenSelector && tile instanceof TileEntityAnimatedScreenSelector) {
                    ContainerAnimatedScreenSelector c=(ContainerAnimatedScreenSelector)player.openContainer;
                    if(c.getTileEntity()!=tile || !c.canInteractWith(player))return;
                    if(TileEntityAnimatedScreenSelector.supportsSideTexture(tile.getBlockType()))
                        ((TileEntityAnimatedScreenSelector)tile).setSideTexture(m.choice);
                }
            });return null;
        }
    }
}

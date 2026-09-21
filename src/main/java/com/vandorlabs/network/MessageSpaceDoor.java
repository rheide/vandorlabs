package com.vandorlabs.network;

import com.vandorlabs.container.ContainerSpaceDoor;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public class MessageSpaceDoor implements IMessage {
    private BlockPos pos;
    private int design,detail,channel,slideDirection;
    private boolean framed;
    private boolean middle;
    public MessageSpaceDoor() {}
    public MessageSpaceDoor(BlockPos pos,int design,int detail,boolean framed,int channel,int slideDirection,boolean middle) {
        this.pos=pos; this.design=design; this.detail=detail; this.framed=framed; this.channel=channel;
        this.slideDirection=slideDirection;
        this.middle=middle;
    }
    @Override public void fromBytes(ByteBuf b) {
        pos=BlockPos.fromLong(b.readLong()); design=b.readInt(); detail=b.readInt(); framed=b.readBoolean(); channel=b.readInt();
        slideDirection=b.readInt();
        middle=b.readBoolean();
    }
    @Override public void toBytes(ByteBuf b) {
        b.writeLong(pos.toLong()); b.writeInt(design); b.writeInt(detail); b.writeBoolean(framed); b.writeInt(channel);
        b.writeInt(slideDirection);
        b.writeBoolean(middle);
    }
    public static class Handler implements IMessageHandler<MessageSpaceDoor,IMessage> {
        @Override public IMessage onMessage(MessageSpaceDoor m,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if (!TileEntitySpaceDoor.valid(m.design,m.detail) || !TileEntitySpaceDoor.validSlideDirection(m.slideDirection)
                        || m.channel<0 || m.pos==null
                        || !player.world.isBlockLoaded(m.pos) || !(player.openContainer instanceof ContainerSpaceDoor)) return;
                TileEntity raw=player.world.getTileEntity(m.pos);
                ContainerSpaceDoor container=(ContainerSpaceDoor)player.openContainer;
                if (!(raw instanceof TileEntitySpaceDoor) || container.member!=raw || !container.canInteractWith(player)) return;
                TileEntitySpaceDoor tile=(TileEntitySpaceDoor)raw;
                BlockPos mate=tile.mate();
                TileEntitySpaceDoor other=null;
                if (mate!=null) {
                    if (!player.world.isBlockLoaded(mate) || !(player.world.getTileEntity(mate) instanceof TileEntitySpaceDoor)) return;
                    other=(TileEntitySpaceDoor)player.world.getTileEntity(mate);
                    if (!other.usable(player)) return;
                }
                tile.configure(m.design,m.detail,m.framed,m.slideDirection,m.middle); tile.setRedstoneChannel(m.channel);
                if (other!=null) { other.configure(m.design,m.detail,m.framed,m.slideDirection,m.middle); other.setRedstoneChannel(m.channel); }
            });
            return null;
        }
    }
}

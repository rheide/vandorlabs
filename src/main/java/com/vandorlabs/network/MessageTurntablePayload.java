package com.vandorlabs.network;

import com.vandorlabs.VandorLabs;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Fragmented, server-authoritative snapshot of a Turntable's turning volume. */
public class MessageTurntablePayload implements IMessage {
    public BlockPos pos = BlockPos.ORIGIN;
    public int total;
    public int offset;
    public byte[] data = new byte[0];

    public MessageTurntablePayload() { }

    public MessageTurntablePayload(BlockPos pos, int total, int offset, byte[] data) {
        this.pos = pos;
        this.total = total;
        this.offset = offset;
        this.data = data;
    }

    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        total = buf.readInt();
        offset = buf.readInt();
        int length = buf.readInt();
        data = new byte[Math.max(0, Math.min(length, buf.readableBytes()))];
        buf.readBytes(data);
    }

    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(total);
        buf.writeInt(offset);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    public static class Handler implements IMessageHandler<MessageTurntablePayload, IMessage> {
        @Override public IMessage onMessage(MessageTurntablePayload message, MessageContext context) {
            VandorLabs.proxy.turntablePayload(message);
            return null;
        }
    }
}

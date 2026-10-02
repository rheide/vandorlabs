package com.vandorlabs.network;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Applies the artwork and brightness chosen in the light menu. */
public final class MessageProgrammableLight implements IMessage {
    private BlockPos pos;
    private int texture;
    private int level;
    private boolean join;
    private int channel;
    private int housing;
    private int trigger;
    private boolean small,tileSides;

    public MessageProgrammableLight() { }
    public MessageProgrammableLight(BlockPos pos, int texture, int level,
            boolean join, int channel) {
        this(pos, texture, level, join, channel, 0);
    }

    public MessageProgrammableLight(BlockPos pos, int texture, int level,
            boolean join, int channel, int housing) {
        this(pos, texture, level, join, channel, housing,
                com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED);
    }

    public MessageProgrammableLight(BlockPos pos, int texture, int level,
            boolean join, int channel, int housing, int trigger) {
        this(pos,texture,level,join,channel,housing,trigger,false,true);
    }
    public MessageProgrammableLight(BlockPos pos,int texture,int level,boolean join,int channel,int housing,int trigger,boolean small,boolean tileSides) {
        this.small=small;this.tileSides=tileSides;
        this.pos = pos;
        this.texture = texture;
        this.level = level;
        this.join = join;
        this.channel = channel;
        this.housing = housing;
        this.trigger = trigger;
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        texture = buf.readInt();
        level = buf.readInt();
        join = buf.readBoolean();
        channel = buf.readInt();
        housing = buf.readInt();
        trigger = buf.readInt();
        small=buf.readBoolean();tileSides=buf.readBoolean();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(texture);
        buf.writeInt(level);
        buf.writeBoolean(join);
        buf.writeInt(channel);
        buf.writeInt(housing);
        buf.writeInt(trigger);
        buf.writeBoolean(small);buf.writeBoolean(tileSides);
    }

    public static final class Handler implements IMessageHandler<MessageProgrammableLight, IMessage> {
        @Override public IMessage onMessage(MessageProgrammableLight msg, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || !com.vandorlabs.persistence.SpaceDoorData.validTrigger(msg.trigger)
                        || msg.texture < 0
                        || !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(msg.texture)
                        || msg.housing < 0
                        || !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(msg.housing)
                        || msg.level < 0 || msg.level > 15 || msg.channel < 0
                        || !player.world.isBlockLoaded(msg.pos)
                        || !(player.openContainer instanceof ContainerAnimatedScreenSelector)) return;
                ContainerAnimatedScreenSelector container =
                        (ContainerAnimatedScreenSelector) player.openContainer;
                TileEntity tile = player.world.getTileEntity(msg.pos);
                if (!(tile instanceof TileEntityProgrammableLight)
                        || tile != container.getTileEntity()
                        || !container.canInteractWith(player)
                        || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)
                        || !(player.world.getBlockState(msg.pos).getBlock()
                        instanceof com.vandorlabs.blocks.BlockProgrammableLight)) return;
                ((TileEntityProgrammableLight)tile).setSmallInput(msg.small);
                ((TileEntityProgrammableLight)tile).setSlabTileSides(msg.tileSides);
                ((TileEntityProgrammableLight)tile).setFaceTexture(msg.texture);
                ((TileEntityProgrammableLight) tile).configure(
                        ((TileEntityProgrammableLight)tile).getTexture(), msg.level, msg.join, msg.channel, msg.housing, msg.trigger);
            });
            return null;
        }
    }
}

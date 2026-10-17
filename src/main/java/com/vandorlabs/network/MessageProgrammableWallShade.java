package com.vandorlabs.network;

import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Applies a porthole glass shade while its configuration dialog is open. */
public final class MessageProgrammableWallShade implements IMessage {
    private BlockPos pos;
    private int shade;
    private boolean join;
    private int shape;
    private int tint=1;
    private int material=-1;
    private boolean stretch,textureJoin=true;
    private int layout=1;
    public MessageProgrammableWallShade withLayout(int mode,boolean join){layout=mode;stretch=mode!=1;textureJoin=join;return this;}
    public MessageProgrammableWallShade withLayout(boolean stretch,boolean join){layout=stretch?0:1;this.stretch=stretch;textureJoin=join;return this;}
    public MessageProgrammableWallShade withMaterial(int material){this.material=material;return this;}
    private boolean framed;
    private boolean appearance;

    public MessageProgrammableWallShade() {}
    public MessageProgrammableWallShade(BlockPos pos, int shade, boolean join, int shape) {
        this.pos = pos;
        this.shade = shade;
        this.join = join;
        this.shape = shape;
    }
    public MessageProgrammableWallShade withAppearance(int tint,boolean framed){this.tint=tint;this.framed=framed;appearance=true;return this;}
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        shade = buf.readInt();
        join = buf.readBoolean();
        shape = buf.readInt();
        appearance=buf.readableBytes()>=2;
        if(appearance){tint=buf.readUnsignedByte();framed=buf.readBoolean();}
        material=buf.readableBytes()>=4?buf.readInt():-1;
        stretch=buf.isReadable() && buf.readBoolean();textureJoin=!buf.isReadable() || buf.readBoolean();
        layout=buf.isReadable()?buf.readUnsignedByte():stretch?0:1;
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(shade);
        buf.writeBoolean(join);
        buf.writeInt(shape);
        if(appearance){buf.writeByte(tint);buf.writeBoolean(framed);}
        if(appearance){buf.writeInt(material);buf.writeBoolean(stretch);buf.writeBoolean(textureJoin);buf.writeByte(layout<0?(stretch?0:1):layout);}
    }

    public static final class Handler implements IMessageHandler<MessageProgrammableWallShade, IMessage> {
        @Override public IMessage onMessage(MessageProgrammableWallShade msg, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!com.vandorlabs.render.TextureLayoutMode.valid(msg.layout) || !com.vandorlabs.tiles.GlassMaterials.valid(msg.material) || !com.vandorlabs.tiles.GlassTextures.validTint(msg.tint) || msg.pos == null || !com.vandorlabs.tiles.GlassTextures.valid(msg.shade)
                        || msg.shape < 0 || msg.shape > 3
                        || !player.world.isBlockLoaded(msg.pos)
                        || !(player.openContainer instanceof ContainerAnimatedScreenSelector)) return;
                ContainerAnimatedScreenSelector container =
                        (ContainerAnimatedScreenSelector) player.openContainer;
                TileEntity raw = player.world.getTileEntity(msg.pos);
                if (!(raw instanceof TileEntityAnimatedScreenSelector)
                        || raw != container.getTileEntity()
                        || !container.canInteractWith(player)
                        || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)
                        || !(player.world.getBlockState(msg.pos).getBlock()
                        instanceof BlockProgrammableWall)
                        || (!((BlockProgrammableWall) player.world.getBlockState(msg.pos)
                        .getBlock()).isPortholeShape() && !(player.world.getBlockState(msg.pos).getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalGlass))) return;
                ((TileEntityAnimatedScreenSelector) raw).setGlassShade(msg.shade);
                if(msg.appearance){TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)raw;tile.setGlassMaterial(msg.material);tile.setGlassAppearance(msg.tint,msg.framed);tile.setGlassLayout(msg.stretch,msg.textureJoin);tile.setGlassTextureLayout(msg.layout);}
                ((TileEntityAnimatedScreenSelector) raw).setJoinPortholes(msg.join);
                ((TileEntityAnimatedScreenSelector) raw).setPortholeShape(msg.shape);
            });
            return null;
        }
    }
}

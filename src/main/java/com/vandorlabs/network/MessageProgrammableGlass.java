package com.vandorlabs.network;

import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.container.ContainerProgrammableGlass;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class MessageProgrammableGlass implements IMessage {
    private BlockPos pos;
    private int size, shade;
    private boolean join;
    private int tint=1;
    private int material=-1;
    private boolean stretch;
    private int layout=1;
    public MessageProgrammableGlass withTextureLayout(int layout){this.layout=layout;this.stretch=layout!=1;return this;}
    public MessageProgrammableGlass withStretch(boolean stretch){this.layout=stretch?0:1;this.stretch=stretch;return this;}
    public MessageProgrammableGlass withMaterial(int material){this.material=material;return this;}
    private boolean framed=true;
    public MessageProgrammableGlass() {}
    public MessageProgrammableGlass(BlockPos pos, int size, int shade, boolean join) {
        this.pos = pos; this.size = size; this.shade = shade; this.join = join;
    }
    public MessageProgrammableGlass withAppearance(int tint,boolean framed){this.tint=tint;this.framed=framed;return this;}
    @Override public void fromBytes(ByteBuf b) {
        pos = BlockPos.fromLong(b.readLong()); size = b.readInt(); shade = b.readInt();
        join = b.readBoolean();
        tint=b.isReadable()?b.readUnsignedByte():1;framed=!b.isReadable()||b.readBoolean();
        material=b.readableBytes()>=4?b.readInt():-1;
        stretch=b.isReadable() && b.readBoolean();
        layout=b.isReadable()?b.readUnsignedByte():stretch?0:1;
    }
    @Override public void toBytes(ByteBuf b) {
        b.writeLong(pos.toLong()); b.writeInt(size); b.writeInt(shade); b.writeBoolean(join);b.writeByte(tint);b.writeBoolean(framed);b.writeInt(material);b.writeBoolean(stretch);b.writeByte(layout<0?(stretch?0:1):layout);
    }
    public static final class Handler implements IMessageHandler<MessageProgrammableGlass, IMessage> {
        @Override public IMessage onMessage(MessageProgrammableGlass msg, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!com.vandorlabs.render.TextureLayoutMode.valid(msg.layout) || !com.vandorlabs.tiles.GlassMaterials.valid(msg.material) || !com.vandorlabs.tiles.GlassTextures.validTint(msg.tint) || msg.pos == null || msg.size < 0 || msg.size > 2
                        || !com.vandorlabs.tiles.GlassTextures.valid(msg.shade)
                        || !player.world.isBlockLoaded(msg.pos)
                        || !(player.openContainer instanceof ContainerProgrammableGlass)) return;
                TileEntity te = player.world.getTileEntity(msg.pos);
                ContainerProgrammableGlass container = (ContainerProgrammableGlass) player.openContainer;
                if (!(te instanceof TileEntityProgrammableGlass) || container.tile != te
                        || !container.canInteractWith(player)
                        || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)) return;
                IBlockState state = player.world.getBlockState(msg.pos);
                if (!(state.getBlock() instanceof BlockProgrammableGlass)) return;
                if (state.getValue(BlockProgrammableGlass.DEPTH) == 0) {
                    IBlockState changed = state.withProperty(BlockProgrammableGlass.SIZE, msg.size);
                    if (changed != state) player.world.setBlockState(msg.pos, changed, 3);
                }
                TileEntity updated = player.world.getTileEntity(msg.pos);
                if (updated instanceof TileEntityProgrammableGlass) {
                    ((TileEntityProgrammableGlass) updated).setSize(msg.size);
                    ((TileEntityProgrammableGlass) updated).setShade(msg.shade);
                    ((TileEntityProgrammableGlass) updated).setMaterial(msg.material);
                    ((TileEntityProgrammableGlass) updated).setTextureLayout(msg.layout);
                    ((TileEntityProgrammableGlass) updated).setJoin(msg.join);
                    ((TileEntityProgrammableGlass) updated).setAppearance(msg.tint,msg.framed);
                }
            });
            return null;
        }
    }
}

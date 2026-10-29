package com.vandorlabs.network;

import com.vandorlabs.tiles.*;
import com.vandorlabs.container.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Fixed-size, container-authorized frame finish update. */
public final class MessageFrameAppearance implements IMessage {
    private BlockPos pos;private int front,interior,exterior=-1,top=-1,bottom=-1;private boolean tile,displayFit,split,bare;
    public MessageFrameAppearance(){}
    public MessageFrameAppearance(TileEntity owner,FrameAppearance appearance){pos=owner.getPos();front=appearance.front;interior=appearance.interior;exterior=appearance.exteriorChoice();tile=appearance.tile;displayFit=appearance.displayFit;split=appearance.split;top=appearance.splitTop;bottom=appearance.splitBottom;bare=appearance.bare;}
    public void fromBytes(ByteBuf b){int length=b.readableBytes();if(length!=17&&length!=18&&length!=27&&length!=28&&length!=32)throw new io.netty.handler.codec.DecoderException("Invalid frame appearance length");pos=BlockPos.fromLong(b.readLong());front=b.readInt();interior=b.readInt();tile=b.readBoolean();displayFit=b.isReadable()&&b.readBoolean();if(b.readableBytes()>=9){split=b.readBoolean();top=b.readInt();bottom=b.readInt();}bare=b.isReadable()&&b.readBoolean();exterior=b.readableBytes()>=4?b.readInt():interior;}
    public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeInt(front);b.writeInt(interior);b.writeBoolean(tile);b.writeBoolean(displayFit);b.writeBoolean(split);b.writeInt(top);b.writeInt(bottom);b.writeBoolean(bare);b.writeInt(exterior);}
    public static final class Handler implements IMessageHandler<MessageFrameAppearance,IMessage>{
        public IMessage onMessage(MessageFrameAppearance msg,MessageContext context){
            net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(msg.pos==null||!FrameAppearance.valid(msg.front)||!FrameAppearance.validSurface(msg.interior)||!FrameAppearance.validSurface(msg.exterior)||(msg.top!=-1&&!ScreenHousingTextures.validChoice(msg.top))||(msg.bottom!=-1&&!ScreenHousingTextures.validChoice(msg.bottom))||!player.world.isBlockLoaded(msg.pos)||!com.vandorlabs.items.ConfigurationAccess.canConfigure(player))return;
                TileEntity owner=player.world.getTileEntity(msg.pos);net.minecraft.inventory.Container c=player.openContainer;
                boolean matches=c instanceof ContainerProgrammableGlass&&((ContainerProgrammableGlass)c).tile==owner
                    ||c instanceof ContainerAnimatedScreenSelector&&((ContainerAnimatedScreenSelector)c).getTileEntity()==owner
                    ||c instanceof ContainerRedstoneChannel&&((ContainerRedstoneChannel)c).member.channelTile()==owner;
                FrameAppearance a=FrameAppearance.of(owner);if(!matches||a==null||!c.canInteractWith(player))return;
                if(com.vandorlabs.blocks.BlockAnimatedScreenSelector.usesDeviceHousing(owner.getBlockType()))a.artworkVersion=1;
                a.bare=msg.bare;a.front=msg.front;a.interior=msg.interior;a.exterior=msg.exterior;a.tile=msg.tile;a.displayFit=msg.displayFit;a.split=msg.split;a.splitTop=msg.top;a.splitBottom=msg.bottom;com.vandorlabs.tiles.GlassMaterials.changed();owner.markDirty();
                net.minecraft.block.state.IBlockState state=player.world.getBlockState(msg.pos);player.world.notifyBlockUpdate(msg.pos,state,state,3);player.world.checkLight(msg.pos);
            });return null;
        }
    }
}

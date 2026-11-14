package com.vandorlabs.network;

import com.vandorlabs.surface.TextureJoinOwner;
import com.vandorlabs.surface.TextureJoinSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Append-only configuration message; all world access is deferred to the server. */
public final class MessageTextureJoin implements IMessage {
    private BlockPos pos;private int direction,parity;private boolean valid;
    public MessageTextureJoin(){}
    public MessageTextureJoin(BlockPos pos,TextureJoinSettings settings){this.pos=pos;direction=settings.direction;parity=settings.parity;valid=true;}
    public void fromBytes(ByteBuf data){valid=false;if(data.readableBytes()!=10)return;pos=BlockPos.fromLong(data.readLong());direction=data.readUnsignedByte();parity=data.readUnsignedByte();valid=TextureJoinSettings.valid(direction,parity);}
    public void toBytes(ByteBuf data){data.writeLong(pos.toLong());data.writeByte(direction);data.writeByte(parity);}
    public static final class Handler implements IMessageHandler<MessageTextureJoin,IMessage>{
        public IMessage onMessage(MessageTextureJoin message,MessageContext context){
            net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(!message.valid||message.pos==null||!player.world.isBlockLoaded(message.pos)
                        ||!com.vandorlabs.items.ConfigurationAccess.canConfigure(player))return;
                TileEntity tile=player.world.getTileEntity(message.pos);boolean owns=false;
                if(player.openContainer instanceof com.vandorlabs.container.ContainerAnimatedScreenSelector)
                    owns=((com.vandorlabs.container.ContainerAnimatedScreenSelector)player.openContainer).getTileEntity()==tile;
                if(player.openContainer instanceof com.vandorlabs.container.ContainerProgrammableGlass)
                    owns=((com.vandorlabs.container.ContainerProgrammableGlass)player.openContainer).tile==tile;
                if(owns&&tile instanceof TextureJoinOwner&&player.openContainer.canInteractWith(player)
                        &&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,player.world,message.pos)){
                    ((TextureJoinOwner)tile).setTextureJoin(new TextureJoinSettings(message.direction,message.parity));
                    boolean enabled=message.direction!=TextureJoinSettings.OFF;
                    if(tile instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass)((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile).setJoin(enabled);
                    if(tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector){
                        com.vandorlabs.tiles.TileEntityAnimatedScreenSelector host=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile;
                        host.setJoinPortholes(enabled);host.setGlassLayout(host.isGlassStretch(),enabled);
                    }
                }
            });return null;
        }
    }
}

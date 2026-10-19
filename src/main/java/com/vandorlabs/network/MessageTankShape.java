package com.vandorlabs.network;

import com.vandorlabs.container.ContainerStorageStatus;
import com.vandorlabs.tiles.TileEntityHexTank;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Shape changes are confined to the storage block whose dialog the player has open. */
public final class MessageTankShape implements IMessage {
    private int window;
    private boolean block;
    private int casing=-1,layout=-1;
    public MessageTankShape(){}
    public MessageTankShape(int window,boolean block){this.window=window;this.block=block;}
    public MessageTankShape withCasing(int casing){this.casing=casing;return this;}
    public MessageTankShape withLayout(int layout){this.layout=layout;return this;}
    @Override public void fromBytes(ByteBuf buffer){window=buffer.readInt();block=buffer.readBoolean();casing=buffer.readInt();layout=buffer.readInt();}
    @Override public void toBytes(ByteBuf buffer){buffer.writeInt(window);buffer.writeBoolean(block);buffer.writeInt(casing);buffer.writeInt(layout);}
    public static final class Handler implements IMessageHandler<MessageTankShape,IMessage>{
        @Override public IMessage onMessage(MessageTankShape message,MessageContext context){
            net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(!(player.openContainer instanceof ContainerStorageStatus)||player.openContainer.windowId!=message.window)return;
                ContainerStorageStatus container=(ContainerStorageStatus)player.openContainer;
                if(container.canInteractWith(player)&&container.tile instanceof com.vandorlabs.tiles.TileEntityBattery
                        &&message.casing==-1&&message.layout==-1
                        &&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,player.world,container.tile.getPos())){
                    ((com.vandorlabs.tiles.TileEntityBattery)container.tile).setBlockShape(message.block);return;
                }
                if(container.canInteractWith(player)&&container.tile instanceof TileEntityHexTank
                        &&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,player.world,container.tile.getPos())){
                    TileEntityHexTank tank=(TileEntityHexTank)container.tile;
                    if(message.casing!=-1&&!com.vandorlabs.tiles.ScreenHousingTextures.validChoice(message.casing))return;
                    if(message.layout!=-1&&message.layout!=1&&message.layout!=2)return;
                    tank.setBlockShape(message.block);if(message.layout!=-1)tank.setMainTextureLayout(message.layout);if(message.casing!=-1)tank.setHousingTexture(message.casing);
                }
            });
            return null;
        }
    }
}

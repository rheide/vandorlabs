package com.vandorlabs.network;
import com.vandorlabs.container.ContainerLargeCanopy;
import com.vandorlabs.canopy.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
public final class MessageLargeCanopy implements IMessage {
    private BlockPos pos;private LargeCanopySettings settings;private boolean valid;
    public MessageLargeCanopy(){}
    public MessageLargeCanopy(BlockPos pos,int d,int s,int c,int t){this.pos=pos;settings=new LargeCanopySettings();settings.depth=d;settings.scale=s;settings.shade=c;settings.tint=t;valid=true;}
    public MessageLargeCanopy(BlockPos pos,LargeCanopySettings settings){this.pos=pos;NBTTagCompound tag=new NBTTagCompound();settings.write(tag);this.settings=new LargeCanopySettings();this.settings.read(tag);valid=true;}
    public void toBytes(ByteBuf b){b.writeLong(pos.toLong());NBTTagCompound tag=new NBTTagCompound();settings.write(tag);ByteBufUtils.writeTag(b,tag);}
    public void fromBytes(ByteBuf b){try{pos=BlockPos.fromLong(b.readLong());NBTTagCompound tag=ByteBufUtils.readTag(b);settings=new LargeCanopySettings();settings.read(tag);valid=tag!=null && (!tag.hasKey("CanopyTextureLayout",3) || com.vandorlabs.render.TextureLayoutMode.valid(tag.getInteger("CanopyTextureLayout"))) && LargeCanopySettings.valid(tag.getInteger("CanopyDepth"),tag.getInteger("CanopyScale"),tag.getInteger("CanopyShade"),tag.getInteger("CanopyTint")) && (settings.material==-1 || com.vandorlabs.tiles.ScreenHousingTextures.validChoice(settings.material));}catch(Exception failure){valid=false;}}
    public static final class Handler implements IMessageHandler<MessageLargeCanopy,IMessage>{
        public IMessage onMessage(MessageLargeCanopy message,MessageContext context){
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(!message.valid || !(player.openContainer instanceof ContainerLargeCanopy) || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player))return;
                ContainerLargeCanopy container=(ContainerLargeCanopy)player.openContainer;
                if(!container.canInteractWith(player) || !container.tile.getPos().equals(message.pos))return;
                boolean applied=LargeCanopyOwnership.apply(container.tile,player,message.settings);
                PacketHandler.INSTANCE.sendTo(new MessageCanopySettingsResult(container.tile,message.settings,applied),player);
                if(!applied)container.tile.sync();
            });return null;
        }
    }
}

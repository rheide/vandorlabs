package com.vandorlabs.network;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.canopy.LargeCanopySettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.common.network.ByteBufUtils;
/** Exact edit acknowledgement with authoritative values on rejection. */
public final class MessageCanopySettingsResult implements IMessage {
    public BlockPos pos;public int depth,scale,shade,tint;public String requested;public boolean applied;
    public LargeCanopySettings settings;
    public MessageCanopySettingsResult(){}
    public MessageCanopySettingsResult(TileEntityCanopy tile,LargeCanopySettings next,boolean applied){pos=tile.getPos();requested=next.key();NBTTagCompound tag=new NBTTagCompound();tile.large.write(tag);settings=new LargeCanopySettings();settings.read(tag);this.applied=applied;depth=settings.depth;scale=settings.scale;shade=settings.shade;tint=settings.tint;}
    public MessageCanopySettingsResult(TileEntityCanopy tile,int d,int s,int c,int t,boolean applied){this(tile,legacy(d,s,c,t),applied);}
    private static LargeCanopySettings legacy(int d,int s,int c,int t){LargeCanopySettings settings=new LargeCanopySettings();settings.depth=d;settings.scale=s;settings.shade=c;settings.tint=t;return settings;}
    public void toBytes(ByteBuf b){b.writeLong(pos.toLong());ByteBufUtils.writeUTF8String(b,requested);NBTTagCompound tag=new NBTTagCompound();settings.write(tag);ByteBufUtils.writeTag(b,tag);b.writeBoolean(applied);}
    public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());requested=ByteBufUtils.readUTF8String(b);settings=new LargeCanopySettings();settings.read(ByteBufUtils.readTag(b));depth=settings.depth;scale=settings.scale;shade=settings.shade;tint=settings.tint;applied=b.readBoolean();}
    public static final class Handler implements IMessageHandler<MessageCanopySettingsResult,IMessage>{public IMessage onMessage(MessageCanopySettingsResult message,MessageContext context){net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();mc.addScheduledTask(()->{if(mc.currentScreen instanceof com.vandorlabs.client.GuiLargeCanopy)((com.vandorlabs.client.GuiLargeCanopy)mc.currentScreen).acknowledge(message);});return null;}}
}

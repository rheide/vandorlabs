package com.vandorlabs.network;
import com.vandorlabs.tiles.*;
import com.vandorlabs.container.ContainerSpaceDoor;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.*;
/** Fixed-size glazing update, appended after legacy packet registrations. */
public final class MessageDoorGlass implements IMessage {
 private BlockPos pos;private int material,shade,tint,layout;
 public MessageDoorGlass(){}
 public MessageDoorGlass(BlockPos pos,DoorGlassAppearance a){this.pos=pos;material=a.material;shade=a.shade;tint=a.tint;layout=a.layout;}
 public void fromBytes(ByteBuf b){if(b.readableBytes()!=15)throw new io.netty.handler.codec.DecoderException("Invalid door glass packet length");pos=BlockPos.fromLong(b.readLong());material=b.readInt();shade=b.readUnsignedByte();tint=b.readUnsignedByte();layout=b.readUnsignedByte();}
 public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeInt(material);b.writeByte(shade);b.writeByte(tint);b.writeByte(layout);}
 public static final class Handler implements IMessageHandler<MessageDoorGlass,IMessage>{
  public IMessage onMessage(MessageDoorGlass m,MessageContext ctx){net.minecraft.entity.player.EntityPlayerMP player=ctx.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{
   if(m.pos==null||!DoorGlassAppearance.valid(m.material,m.shade,m.tint,m.layout)||!player.world.isBlockLoaded(m.pos)||!com.vandorlabs.items.ConfigurationAccess.canConfigure(player)||!(player.openContainer instanceof ContainerSpaceDoor))return;
   TileEntity raw=player.world.getTileEntity(m.pos);ContainerSpaceDoor container=(ContainerSpaceDoor)player.openContainer;
   if(!(raw instanceof TileEntitySpaceDoor)||container.member!=raw||!container.canInteractWith(player))return;TileEntitySpaceDoor door=(TileEntitySpaceDoor)raw;if(!door.supportsGlassAppearance())return;
   TileEntitySpaceDoor other=null;BlockPos mate=door.mate();if(mate!=null){if(!player.world.isBlockLoaded(mate))return;TileEntity t=player.world.getTileEntity(mate);if(!(t instanceof TileEntitySpaceDoor)||!((TileEntitySpaceDoor)t).usable(player))return;other=(TileEntitySpaceDoor)t;}
   DoorGlassAppearance a=new DoorGlassAppearance(m.material,m.shade,m.tint,m.layout);door.setGlassAppearance(a);if(other!=null)other.setGlassAppearance(a);
  });return null;}
 }
}

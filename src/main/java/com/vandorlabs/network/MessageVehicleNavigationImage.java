package com.vandorlabs.network;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.util.UUID;
/** Existing bounded geographic raster, plus a carried address and local display state. */
public final class MessageVehicleNavigationImage implements IMessage {
    public int entity;public UUID token;public boolean follow,missing;public MessageNavigationImage image;
    public MessageVehicleNavigationImage(){}
    public MessageVehicleNavigationImage(EntityGroundVehicle craft,MessageNavigationImage image,boolean follow,boolean missing){entity=craft.getEntityId();token=craft.getUniqueID();this.image=image;this.follow=follow;this.missing=missing;}
    public void toBytes(ByteBuf b){b.writeInt(entity);b.writeLong(token.getMostSignificantBits());b.writeLong(token.getLeastSignificantBits());b.writeByte((follow?1:0)|(missing?2:0));image.toBytes(b);}
    public void fromBytes(ByteBuf b){if(b.readableBytes()<21+MessageNavigationImage.HEADER||b.readableBytes()>21+MessageNavigationImage.HEADER+512*512*4)throw new IllegalArgumentException("Invalid carried navigation image size");entity=b.readInt();token=new UUID(b.readLong(),b.readLong());int flags=b.readUnsignedByte();if(entity<0||flags>3)throw new IllegalArgumentException("Invalid carried navigation image address");follow=(flags&1)!=0;missing=(flags&2)!=0;image=new MessageNavigationImage();image.fromBytes(b);if(image.pos.getX()<0||image.pos.getY()<0||image.pos.getZ()<0||image.pos.getX()>=64||image.pos.getY()>=64||image.pos.getZ()>=64)throw new IllegalArgumentException("Invalid carried navigation image cell");}
    public static final class Handler implements IMessageHandler<MessageVehicleNavigationImage,IMessage>{public IMessage onMessage(MessageVehicleNavigationImage message,MessageContext context){com.vandorlabs.VandorLabs.proxy.vehicleNavigationImage(message);return null;}}
}

package com.vandorlabs.network;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.util.UUID;
/** A carried display address is scoped to both the loaded entity and its immutable UUID. */
public final class MessageVehicleNavigationRequest implements IMessage {
    public int entity,slot,serial,aspect,known;public UUID token;public BlockPos pos;
    public MessageVehicleNavigationRequest(){}
    public MessageVehicleNavigationRequest(EntityGroundVehicle craft,BlockPos pos,int slot,int serial,int aspect,int known){entity=craft.getEntityId();token=craft.getUniqueID();this.pos=pos;this.slot=slot;this.serial=serial;this.aspect=aspect;this.known=known;}
    public void toBytes(ByteBuf b){b.writeInt(entity);b.writeLong(token.getMostSignificantBits());b.writeLong(token.getLeastSignificantBits());b.writeLong(pos.toLong());b.writeByte(slot);b.writeInt(serial);b.writeByte(aspect);b.writeInt(known);}
    public void fromBytes(ByteBuf b){if(b.readableBytes()!=38)throw new IllegalArgumentException("Invalid carried navigation request size");entity=b.readInt();token=new UUID(b.readLong(),b.readLong());pos=BlockPos.fromLong(b.readLong());slot=b.readUnsignedByte();serial=b.readInt();aspect=b.readUnsignedByte();known=b.readInt();if(entity<0||slot>1||aspect<8||aspect>64||known<0||pos.getX()<0||pos.getY()<0||pos.getZ()<0||pos.getX()>=64||pos.getY()>=64||pos.getZ()>=64)throw new IllegalArgumentException("Invalid carried navigation address");}
    public static final class Handler implements IMessageHandler<MessageVehicleNavigationRequest,IMessage>{public IMessage onMessage(MessageVehicleNavigationRequest m,MessageContext c){net.minecraft.entity.player.EntityPlayerMP p=c.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{net.minecraft.entity.Entity raw=p.world.getEntityByID(m.entity);if(raw instanceof EntityGroundVehicle&&raw.getUniqueID().equals(m.token))com.vandorlabs.navigation.NavigationService.requestCarried(p,(EntityGroundVehicle)raw,m.pos,m.slot,m.serial,m.aspect,m.known);});return null;}}
}

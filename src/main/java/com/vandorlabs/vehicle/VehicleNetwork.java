package com.vandorlabs.vehicle;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.network.PacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.util.UUID;

/** Bounded fragments are sent only for previews and newly tracked vehicles. */
public final class VehicleNetwork {
    public static final int REQUEST=0, INPUT=1, CONFIRM=2, SNAPSHOT=3, PREVIEW=4, ERROR=5, STATE=6, DOORS=7, INTERACT=8, TAXI=9, SETTINGS=10, HEIGHT=11, INSPECTION=12, CANOPIES=13, TILE=14, FLIGHT=15;
    public static final int FRAGMENT=28000;
    public static final class Packet implements IMessage {
        public int type,entity,sequence,total,offset;
        public UUID token=new UUID(0,0);
        public byte forward,strafe;
        public boolean flag,valid=true;
        public BlockPos origin=BlockPos.ORIGIN;
        public byte[] data=new byte[0];
        public String text="";
        public long serverTick;
        public double x,y,z,dy,speed;public float yaw,steering;
        public Packet(){}
        public Packet(int type){this.type=type;}
        public void toBytes(ByteBuf b){b.writeByte(type);b.writeInt(entity);b.writeLong(token.getMostSignificantBits());b.writeLong(token.getLeastSignificantBits());b.writeInt(sequence);b.writeInt(total);b.writeInt(offset);b.writeByte(forward);b.writeByte(strafe);b.writeBoolean(flag);b.writeLong(origin.toLong());b.writeInt(data.length);b.writeBytes(data);ByteBufUtils.writeUTF8String(b,text);if(type==STATE){b.writeLong(serverTick);b.writeDouble(x);b.writeDouble(y);b.writeDouble(z);b.writeDouble(dy);b.writeDouble(speed);b.writeFloat(yaw);b.writeFloat(steering);}}
        public void fromBytes(ByteBuf b){try{type=b.readUnsignedByte();entity=b.readInt();token=new UUID(b.readLong(),b.readLong());sequence=b.readInt();total=b.readInt();offset=b.readInt();forward=b.readByte();strafe=b.readByte();flag=b.readBoolean();origin=BlockPos.fromLong(b.readLong());int n=b.readInt();if(n<0||n>FRAGMENT||n>b.readableBytes())throw new IllegalArgumentException();data=new byte[n];b.readBytes(data);text=ByteBufUtils.readUTF8String(b);valid=text.length()<=com.vandorlabs.redstone.ChannelList.MAX_TEXT_LENGTH && type<=FLIGHT;if(type==STATE){serverTick=b.readLong();x=b.readDouble();y=b.readDouble();z=b.readDouble();dy=b.readDouble();speed=b.readDouble();yaw=b.readFloat();steering=b.readFloat();valid=valid && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(dy) && Double.isFinite(speed) && Float.isFinite(yaw) && Float.isFinite(steering);}}catch(Exception e){valid=false;}}
    }
    public static final class Server implements IMessageHandler<Packet,IMessage> {
        public IMessage onMessage(Packet message,MessageContext ctx) {
            if(!message.valid || message.data.length!=0 || message.type>CONFIRM && message.type!=INTERACT && message.type!=TAXI && message.type!=SETTINGS && message.type!=HEIGHT && message.type!=FLIGHT)return null;
            EntityPlayerMP player=ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(message.type==HEIGHT){VehicleService.configureHeight(player,message);return;}
                if(message.type==SETTINGS){VehicleService.configureChannels(player,message);return;}
                if(message.type==TAXI){VehicleService.configureTaxi(player,message);return;}
                if(message.type==INTERACT){VehicleInteraction.receive(player,message);return;}
                if(message.type==CONFIRM){VehicleService.confirm(player,message.token);return;}
                Entity raw=player.world.getEntityByID(message.entity);
                if(!(raw instanceof EntityGroundVehicle) || !raw.getUniqueID().equals(message.token))return;
                EntityGroundVehicle vehicle=(EntityGroundVehicle)raw;
                if(message.type==FLIGHT){if(message.sequence==1){vehicle.exitFlight(player);return;}vehicle.flightInput(player,message.forward,message.flag);return;}
                if(message.type==INPUT){if(!VehicleNetworkLab.input(player,message,vehicle))vehicle.input(player,message.sequence,message.forward,message.strafe,message.flag);}
                else if(player.getDistanceSq(vehicle)<256*256 && vehicle.structure!=null && VehicleService.allowSnapshot(player,vehicle))
                    send(player,vehicle.structure,vehicle.getEntityId(),vehicle.getUniqueID(),new BlockPos(vehicle),false,false);
            });return null;
        }
    }
    public static final class Client implements IMessageHandler<Packet,IMessage> {
        public IMessage onMessage(Packet packet,MessageContext ctx){if(packet.valid)VandorLabs.proxy.vehiclePacket(packet);return null;}
    }
    public static void state(EntityGroundVehicle e) {
        Packet p=new Packet(STATE);p.entity=e.getEntityId();p.token=e.getUniqueID();p.sequence=e.acknowledgedInput();
        p.flag=e.hover;p.serverTick=e.world.getTotalWorldTime();p.x=e.posX;p.y=e.posY;p.z=e.posZ;p.dy=e.motionY;p.speed=e.driveSpeed;p.yaw=e.rotationYaw;p.steering=e.steering;
        if(VehicleNetworkLab.state(p,e))return;
        PacketHandler.INSTANCE.sendToAllTracking(p,e);
        if(e.getControllingPassenger() instanceof EntityPlayerMP)PacketHandler.INSTANCE.sendTo(p,(EntityPlayerMP)e.getControllingPassenger());
    }
    public static void request(EntityGroundVehicle entity){Packet p=new Packet(REQUEST);p.entity=entity.getEntityId();p.token=entity.getUniqueID();PacketHandler.INSTANCE.sendToServer(p);}
    public static void send(EntityPlayerMP player,VehicleStructure snapshot,int entity,UUID token,BlockPos origin,boolean preview,boolean parking) {
        send(player,snapshot,entity,token,origin,preview,parking,"");
    }
    public static void send(EntityPlayerMP player,VehicleStructure snapshot,int entity,UUID token,BlockPos origin,boolean preview,boolean parking,String status) {
        send(player,snapshot,entity,token,origin,preview,parking,status,false);
    }
    public static void send(EntityPlayerMP player,VehicleStructure snapshot,int entity,UUID token,BlockPos origin,boolean preview,boolean parking,String status,boolean controller) {
        try {
            Entity raw=entity<0?null:player.world.getEntityByID(entity);
            byte[] bytes=!preview && raw instanceof EntityGroundVehicle && ((EntityGroundVehicle)raw).structure==snapshot
                    ?((EntityGroundVehicle)raw).snapshotBytes():snapshot.encode();
            for(int offset=0;offset<bytes.length;offset+=FRAGMENT) {
                Packet p=new Packet(preview?PREVIEW:SNAPSHOT);p.entity=entity;p.token=token;p.origin=origin;p.flag=parking;p.total=bytes.length;p.offset=offset;
                p.text=status; p.sequence=status.isEmpty()?0:-1;p.forward=(byte)(controller?1:0);
                p.data=java.util.Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+FRAGMENT));PacketHandler.INSTANCE.sendTo(p,player);
            }
        }catch(Exception e){error(player,"Cannot send craft: "+e.getMessage());}
    }
    public static void inspection(EntityPlayerMP player,BlockPos seat,String text) {
        Packet packet=new Packet(INSPECTION);packet.origin=seat;packet.text=text==null?"Inspection failed.":text.substring(0,Math.min(500,text.length()));PacketHandler.INSTANCE.sendTo(packet,player);
    }
    public static void controllerInspection(EntityPlayerMP player,BlockPos pos,String text){Packet packet=new Packet(INSPECTION);packet.entity=-1;packet.forward=1;packet.sequence=-1;packet.origin=pos;packet.text=text==null?"Inspection unavailable.":text.substring(0,Math.min(500,text.length()));PacketHandler.INSTANCE.sendTo(packet,player);}
    public static void controllerInspection(EntityPlayerMP player,BlockPos pos,String text,java.util.UUID token,net.minecraft.nbt.NBTTagCompound control){
        try{Packet packet=new Packet(INSPECTION);packet.entity=-1;packet.forward=1;packet.sequence=-1;packet.origin=pos;packet.token=token;packet.text=text==null?"Inspection unavailable.":text.substring(0,Math.min(500,text.length()));
            net.minecraft.nbt.NBTTagCompound data=new net.minecraft.nbt.NBTTagCompound();data.setUniqueId("PreviewToken",token);data.setTag("Seat",control.copy());java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();net.minecraft.nbt.CompressedStreamTools.writeCompressed(data,bytes);packet.data=bytes.toByteArray();PacketHandler.INSTANCE.sendTo(packet,player);
        }catch(java.io.IOException failure){error(player,"Cannot send Vehicle Controller settings.");}
    }
    public static void error(EntityPlayerMP player,String text){Packet p=new Packet(ERROR);p.text=text.length()>500?text.substring(0,500):text;PacketHandler.INSTANCE.sendTo(p,player);}
}

package com.vandorlabs.network;

import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import io.netty.buffer.ByteBuf;
import java.util.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Pose corrections carry no topology, model data or compressed tile NBT. */
public final class MessageGearMotion implements IMessage {
    public int dimension,entity=-1;public UUID craft=new UUID(0,0);public long tick;
    public final List<Pose> poses=new ArrayList<>();
    public static final class Pose {
        public BlockPos pos;public int revision;public double progress;public boolean target,blocked;
        public int taxiPhase=-2,taxiAmount,taxiHeight;public double taxiLevel;
        Pose(){}
        Pose(GearTileEntity gear){pos=gear.getPos();revision=gear.motionRevision();progress=gear.progress();target=gear.target();blocked=gear.motionBlocked();int phase=gear.taxiPhase();if(phase==0||phase==2){taxiPhase=phase;taxiAmount=gear.taxiAmount();taxiHeight=gear.taxiHeight();taxiLevel=gear.taxiLevel();}else if(gear.consumeTaxiClear())taxiPhase=-1;}
    }
    public MessageGearMotion(){}
    public static void send(GearTileEntity gear){
        if(gear.getWorld()==null||gear.getWorld().isRemote)return;
        MessageGearMotion packet=new MessageGearMotion();packet.dimension=gear.getWorld().provider.getDimension();packet.tick=gear.getWorld().getTotalWorldTime();packet.poses.add(new Pose(gear));
        BlockPos p=gear.getPos();PacketHandler.INSTANCE.sendToAllAround(packet,new NetworkRegistry.TargetPoint(packet.dimension,p.getX()+.5,p.getY()+.5,p.getZ()+.5,256));
    }
    public static void send(EntityGroundVehicle craft,Collection<BlockPos> positions){
        if(craft.world.isRemote||positions.isEmpty())return;
        MessageGearMotion packet=new MessageGearMotion();packet.dimension=craft.dimension;packet.entity=craft.getEntityId();packet.craft=craft.getUniqueID();packet.tick=craft.world.getTotalWorldTime();
        for(BlockPos pos:positions){TileEntity tile=craft.view.getTileEntity(pos);if(tile instanceof GearTileEntity)packet.poses.add(new Pose((GearTileEntity)tile));
            if(packet.poses.size()==64){publish(craft,packet);packet=new MessageGearMotion();packet.dimension=craft.dimension;packet.entity=craft.getEntityId();packet.craft=craft.getUniqueID();packet.tick=craft.world.getTotalWorldTime();}}
        if(!packet.poses.isEmpty())publish(craft,packet);
    }
    private static void publish(EntityGroundVehicle craft,MessageGearMotion packet){
        PacketHandler.INSTANCE.sendToAllTracking(packet,craft);
        if(craft.getControllingPassenger() instanceof net.minecraft.entity.player.EntityPlayerMP)PacketHandler.INSTANCE.sendTo(packet,(net.minecraft.entity.player.EntityPlayerMP)craft.getControllingPassenger());
    }
    private static final int TAXI=0x54415849;
    @Override public void toBytes(ByteBuf b){
        b.writeInt(dimension);b.writeInt(entity);b.writeLong(craft.getMostSignificantBits());b.writeLong(craft.getLeastSignificantBits());b.writeLong(tick);b.writeByte(poses.size());
        int taxi=0;
        for(Pose p:poses){b.writeLong(p.pos.toLong());b.writeInt(p.revision);b.writeDouble(p.progress);b.writeBoolean(p.target);b.writeBoolean(p.blocked);if(p.taxiPhase!=-2)taxi++;}
        if(taxi==0)return;
        b.writeInt(TAXI);b.writeByte(taxi);
        for(int i=0;i<poses.size();i++){Pose p=poses.get(i);if(p.taxiPhase==-2)continue;b.writeByte(i);b.writeByte(p.taxiPhase);if(p.taxiPhase>=0){b.writeByte(p.taxiAmount);b.writeByte(p.taxiHeight);b.writeDouble(p.taxiLevel);}}
    }
    @Override public void fromBytes(ByteBuf b){
        if(b.readableBytes()<33)throw new IllegalArgumentException("Truncated gear motion packet");
        dimension=b.readInt();entity=b.readInt();craft=new UUID(b.readLong(),b.readLong());tick=b.readLong();int count=b.readUnsignedByte();
        if(count<1||count>64||b.readableBytes()<count*22||b.readableBytes()>count*34+5)throw new IllegalArgumentException("Invalid gear motion packet");
        for(int i=0;i<count;i++){Pose p=new Pose();p.pos=BlockPos.fromLong(b.readLong());p.revision=b.readInt();p.progress=b.readDouble();p.target=b.readBoolean();p.blocked=b.readBoolean();if(!Double.isFinite(p.progress)||p.progress<0||p.progress>1)throw new IllegalArgumentException("Invalid gear progress");poses.add(p);}
        if(!b.isReadable())return;
        if(b.readableBytes()<5||b.readInt()!=TAXI)throw new IllegalArgumentException("Invalid Taxi motion trailer");
        int entries=b.readUnsignedByte();if(entries<1||entries>count||b.readableBytes()<entries*2||b.readableBytes()>entries*12)throw new IllegalArgumentException("Invalid Taxi motion count");
        boolean[] seen=new boolean[count];
        for(int i=0;i<entries;i++){
            if(b.readableBytes()<2)throw new IllegalArgumentException("Truncated Taxi motion entry");int index=b.readUnsignedByte(),phase=b.readByte();
            if(index>=count||seen[index]||phase!=-1&&phase!=0&&phase!=2)throw new IllegalArgumentException("Invalid Taxi motion index or phase");seen[index]=true;Pose p=poses.get(index);p.taxiPhase=phase;
            if(phase<0)continue;
            if(b.readableBytes()<10)throw new IllegalArgumentException("Truncated Taxi motion state");p.taxiAmount=b.readUnsignedByte();p.taxiHeight=b.readUnsignedByte();p.taxiLevel=b.readDouble();
            if(p.taxiHeight<2||p.taxiHeight>8||p.taxiAmount<1||p.taxiAmount>=p.taxiHeight||!Double.isFinite(p.taxiLevel)||p.taxiLevel<0||p.taxiLevel>p.taxiAmount)throw new IllegalArgumentException("Invalid Taxi motion limits");
        }
        if(b.isReadable())throw new IllegalArgumentException("Trailing Taxi motion data");
    }
    public static final class Handler implements IMessageHandler<MessageGearMotion,IMessage>{public IMessage onMessage(MessageGearMotion packet,MessageContext context){com.vandorlabs.VandorLabs.proxy.gearMotion(packet);return null;}}
}

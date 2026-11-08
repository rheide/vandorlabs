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
    public int dimension,entity=-1;public UUID craft=new UUID(0,0);public long tick;public UUID associationGeneration;
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
    public static void send(EntityGroundVehicle craft,Collection<BlockPos> positions){send(craft,positions,false);}
    /** Cosmetic pause only; authoritative blocked state is unchanged. Known carried scopes add the optional trailer. */
    public static void sendHeld(EntityGroundVehicle craft,Collection<BlockPos> positions){send(craft,positions,true);}
    private static void send(EntityGroundVehicle craft,Collection<BlockPos> positions,boolean held){
        if(craft.world.isRemote||positions.isEmpty()||!craft.couplerScopeReady())return;
        MessageGearMotion packet=new MessageGearMotion();packet.dimension=craft.dimension;packet.entity=craft.getEntityId();packet.craft=craft.getUniqueID();packet.tick=craft.world.getTotalWorldTime();
        for(BlockPos pos:positions){TileEntity tile=craft.view.getTileEntity(pos);if(tile instanceof GearTileEntity){Pose pose=new Pose((GearTileEntity)tile);if(held)pose.blocked=true;packet.poses.add(pose);}
            if(packet.poses.size()==64){publish(craft,packet);packet=new MessageGearMotion();packet.dimension=craft.dimension;packet.entity=craft.getEntityId();packet.craft=craft.getUniqueID();packet.tick=craft.world.getTotalWorldTime();}}
        if(!packet.poses.isEmpty())publish(craft,packet);
    }
    private static void publish(EntityGroundVehicle craft,MessageGearMotion packet){
        packet.associationGeneration=craft.couplerAssociationGeneration();
        PacketHandler.INSTANCE.sendToAllTracking(packet,craft);
        if(craft.getControllingPassenger() instanceof net.minecraft.entity.player.EntityPlayerMP)PacketHandler.INSTANCE.sendTo(packet,(net.minecraft.entity.player.EntityPlayerMP)craft.getControllingPassenger());
    }
    private static final int TAXI=0x54415849,SCOPE=0x474d5343;
    private static final int SCOPE_BYTES=21;
    @Override public void toBytes(ByteBuf b){
        if(associationGeneration!=null&&(entity<0||zero(associationGeneration)))throw new IllegalArgumentException("Carried nonzero gear scope required");
        b.writeInt(dimension);b.writeInt(entity);b.writeLong(craft.getMostSignificantBits());b.writeLong(craft.getLeastSignificantBits());b.writeLong(tick);b.writeByte(poses.size());
        int taxi=0;
        for(Pose p:poses){b.writeLong(p.pos.toLong());b.writeInt(p.revision);b.writeDouble(p.progress);b.writeBoolean(p.target);b.writeBoolean(p.blocked);if(p.taxiPhase!=-2)taxi++;}
        if(taxi!=0){b.writeInt(TAXI);b.writeByte(taxi);
            for(int i=0;i<poses.size();i++){Pose p=poses.get(i);if(p.taxiPhase==-2)continue;b.writeByte(i);b.writeByte(p.taxiPhase);if(p.taxiPhase>=0){b.writeByte(p.taxiAmount);b.writeByte(p.taxiHeight);b.writeDouble(p.taxiLevel);}}}
        if(associationGeneration!=null){b.writeInt(SCOPE);b.writeByte(1);b.writeLong(associationGeneration.getMostSignificantBits());b.writeLong(associationGeneration.getLeastSignificantBits());}
    }
    /** Deferred client work owns primitive records; decoder reuse cannot rewrite queued observations. */
    public MessageGearMotion copyForClientQueue(){
        MessageGearMotion copy=new MessageGearMotion();copy.dimension=dimension;copy.entity=entity;copy.craft=craft;copy.tick=tick;copy.associationGeneration=associationGeneration;
        for(Pose source:poses){Pose pose=new Pose();pose.pos=source.pos.toImmutable();pose.revision=source.revision;pose.progress=source.progress;pose.target=source.target;pose.blocked=source.blocked;pose.taxiPhase=source.taxiPhase;pose.taxiAmount=source.taxiAmount;pose.taxiHeight=source.taxiHeight;pose.taxiLevel=source.taxiLevel;copy.poses.add(pose);}return copy;
    }
    private static boolean zero(UUID value){return value.getMostSignificantBits()==0&&value.getLeastSignificantBits()==0;}
    private void reset(){dimension=0;entity=-1;craft=new UUID(0,0);tick=0;associationGeneration=null;poses.clear();}
    @Override public void fromBytes(ByteBuf b){
        reset();try{read(b);}catch(RuntimeException malformed){reset();if(malformed instanceof IllegalArgumentException)throw malformed;throw new IllegalArgumentException("Malformed gear motion packet",malformed);}
    }
    private void read(ByteBuf b){
        if(b.readableBytes()<33)throw new IllegalArgumentException("Truncated gear motion packet");
        dimension=b.readInt();entity=b.readInt();craft=new UUID(b.readLong(),b.readLong());tick=b.readLong();int count=b.readUnsignedByte();
        if(count<1||count>64||b.readableBytes()<count*22||b.readableBytes()>count*34+5+SCOPE_BYTES)throw new IllegalArgumentException("Invalid gear motion packet");
        for(int i=0;i<count;i++){Pose p=new Pose();p.pos=BlockPos.fromLong(b.readLong());p.revision=b.readInt();p.progress=b.readDouble();p.target=b.readBoolean();p.blocked=b.readBoolean();if(!Double.isFinite(p.progress)||p.progress<0||p.progress>1)throw new IllegalArgumentException("Invalid gear progress");poses.add(p);}
        if(b.readableBytes()>=4&&b.getInt(b.readerIndex())==TAXI){
            b.readInt();if(!b.isReadable())throw new IllegalArgumentException("Truncated Taxi motion count");int entries=b.readUnsignedByte();
            if(entries<1||entries>count||b.readableBytes()<entries*2||b.readableBytes()>entries*12+SCOPE_BYTES)throw new IllegalArgumentException("Invalid Taxi motion count");
            boolean[] seen=new boolean[count];
            for(int i=0;i<entries;i++){
                if(b.readableBytes()<2)throw new IllegalArgumentException("Truncated Taxi motion entry");int index=b.readUnsignedByte(),phase=b.readByte();
                if(index>=count||seen[index]||phase!=-1&&phase!=0&&phase!=2)throw new IllegalArgumentException("Invalid Taxi motion index or phase");seen[index]=true;Pose p=poses.get(index);p.taxiPhase=phase;
                if(phase<0)continue;
                if(b.readableBytes()<10)throw new IllegalArgumentException("Truncated Taxi motion state");p.taxiAmount=b.readUnsignedByte();p.taxiHeight=b.readUnsignedByte();p.taxiLevel=b.readDouble();
                if(p.taxiHeight<2||p.taxiHeight>8||p.taxiAmount<1||p.taxiAmount>=p.taxiHeight||!Double.isFinite(p.taxiLevel)||p.taxiLevel<0||p.taxiLevel>p.taxiAmount)throw new IllegalArgumentException("Invalid Taxi motion limits");
            }
        }
        if(!b.isReadable())return;
        if(entity<0||b.readableBytes()!=SCOPE_BYTES||b.readInt()!=SCOPE||b.readUnsignedByte()!=1)throw new IllegalArgumentException("Invalid gear ownership scope trailer");
        associationGeneration=new UUID(b.readLong(),b.readLong());if(zero(associationGeneration))throw new IllegalArgumentException("Empty gear ownership scope");
    }
    public static final class Handler implements IMessageHandler<MessageGearMotion,IMessage>{public IMessage onMessage(MessageGearMotion packet,MessageContext context){com.vandorlabs.VandorLabs.proxy.gearMotion(packet);return null;}}
}

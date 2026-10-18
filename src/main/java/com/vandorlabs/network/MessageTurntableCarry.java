package com.vandorlabs.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Add platform travel to normal player movement without starting a teleport acknowledgement. */
public final class MessageTurntableCarry implements IMessage {
    private int dimension,entity;
    private double x,y,z;
    private float yaw;
    public MessageTurntableCarry(){}
    public MessageTurntableCarry(int dimension,int entity,double x,double y,double z,float yaw){this.dimension=dimension;this.entity=entity;this.x=x;this.y=y;this.z=z;this.yaw=yaw;}
    public void toBytes(ByteBuf b){b.writeInt(dimension);b.writeInt(entity);b.writeDouble(x);b.writeDouble(y);b.writeDouble(z);b.writeFloat(yaw);}
    public void fromBytes(ByteBuf b){dimension=b.readInt();entity=b.readInt();x=b.readDouble();y=b.readDouble();z=b.readDouble();yaw=b.readFloat();}
    public static final class Handler implements IMessageHandler<MessageTurntableCarry,IMessage>{public IMessage onMessage(MessageTurntableCarry m,MessageContext context){net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();mc.addScheduledTask(()->{
        if(mc.player==null||mc.world==null||mc.world.provider.getDimension()!=m.dimension||!(mc.world.getEntityByID(m.entity) instanceof com.vandorlabs.turntable.EntityTurntable)||mc.player.isRiding()||mc.player.capabilities.isFlying||!Double.isFinite(m.x+m.y+m.z)||Math.abs(m.x)+Math.abs(m.y)+Math.abs(m.z)>8||!Float.isFinite(m.yaw)||Math.abs(m.yaw)>10)return;
        mc.player.setPosition(mc.player.posX+m.x,mc.player.posY+m.y,mc.player.posZ+m.z);mc.player.rotationYaw+=m.yaw;mc.player.rotationYawHead+=m.yaw;mc.player.renderYawOffset+=m.yaw;
    });return null;}}
}

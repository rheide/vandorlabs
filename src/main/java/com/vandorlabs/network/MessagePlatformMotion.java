package com.vandorlabs.network;

import com.vandorlabs.VandorLabs;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server-authoritative platform support and optional horizontal belt displacement. */
public class MessagePlatformMotion implements IMessage {
    private int dimension;
    private long sequence;
    private static final java.util.Map<EntityPlayer,Long> SENT=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>()),RECEIVED=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private double x,z,before,after,dx,dz;
    private net.minecraft.util.math.BlockPos support;private int surface=-1;private double supportProgress=Double.NaN;
    public MessagePlatformMotion withArm(net.minecraft.util.math.BlockPos support,int surface){if(support!=null&&surface>=0){this.support=support.toImmutable();this.surface=surface;}return this;}
    public MessagePlatformMotion withArm(net.minecraft.util.math.BlockPos support,int surface,double progress){withArm(support,surface);if(support!=null){if(!Double.isFinite(progress)||progress<0||progress>1)throw new IllegalArgumentException("Invalid support progress");supportProgress=progress;}return this;}
    public double supportProgress(){return supportProgress;}
    public net.minecraft.util.math.BlockPos supportPosition(){return support;}
    public int supportSurface(){return surface;}
    public MessagePlatformMotion withHorizontal(double dx,double dz){this.dx=dx;this.dz=dz;return this;}
    public MessagePlatformMotion() { }
    public MessagePlatformMotion(EntityPlayer player,double before,double after) {
        sequence=SENT.merge(player,1L,(a,b)->a+b);dimension=player.dimension; x=player.posX; z=player.posZ; this.before=before; this.after=after;
    }
    @Override public void fromBytes(ByteBuf buf) {
        int length=buf.readableBytes();if(length!=36&&length!=52&&length!=60&&length!=72&&length!=80)throw new IllegalArgumentException("Invalid platform motion length");
        support=null;surface=-1;supportProgress=Double.NaN;sequence=0;dx=dz=0;
        dimension=buf.readInt(); x=buf.readDouble(); z=buf.readDouble(); before=buf.readDouble(); after=buf.readDouble();
        if(buf.readableBytes()>=16){dx=buf.readDouble();dz=buf.readDouble();}if(buf.readableBytes()>=8)sequence=buf.readLong();
        if(buf.readableBytes()>=12){support=net.minecraft.util.math.BlockPos.fromLong(buf.readLong());surface=buf.readInt();if(surface<0)throw new IllegalArgumentException("Invalid support surface");}
        if(buf.readableBytes()==8){supportProgress=buf.readDouble();if(!Double.isFinite(supportProgress)||supportProgress<0||supportProgress>1)throw new IllegalArgumentException("Invalid support progress");}
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension); buf.writeDouble(x); buf.writeDouble(z); buf.writeDouble(before); buf.writeDouble(after);buf.writeDouble(dx);buf.writeDouble(dz);buf.writeLong(sequence);if(support!=null){buf.writeLong(support.toLong());buf.writeInt(surface);if(!Double.isNaN(supportProgress))buf.writeDouble(supportProgress);}
    }
    /** Authoritative transport, excluding correction of the client's falling position. */
    public double verticalDisplacement(){return after-before;}
    public boolean apply(EntityPlayer player) {
        if(player==null||player.dimension!=dimension||!Double.isFinite(x)||!Double.isFinite(z)||!Double.isFinite(before)||!Double.isFinite(after-before)||sequence<0)return false;
        if(sequence>0){Long last=RECEIVED.get(player);if(last!=null&&sequence<=last)return false;RECEIVED.put(player,sequence);}
        if (player==null || player.dimension!=dimension || player.isDead || player.isRiding()
                || player.noClip || player.capabilities.isFlying || player.motionY>0.1) return false;
        // Discard late corrections after walking/jumping off, teleporting, or changing dimensions.
        double feet=player.getEntityBoundingBox().minY;
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || Math.abs(dx)>4 || Math.abs(dz)>4 || !Double.isFinite(after) || Math.abs(player.posX-x)>.75 || Math.abs(player.posZ-z)>.75
                || Math.min(Math.abs(feet-before),Math.abs(feet-after))>.5) return false;
        player.setPosition(player.posX+dx,player.posY+after-feet,player.posZ+dz);
        player.onGround=true; player.fallDistance=0; player.motionY=0;com.vandorlabs.motion.PlatformRiders.supported(player);
        // X/Z momentum, camera direction, and keyboard movement are deliberately untouched.
        return true;
    }
    public static class Handler implements IMessageHandler<MessagePlatformMotion,IMessage> {
        @Override public IMessage onMessage(MessagePlatformMotion message,MessageContext context) {
            VandorLabs.proxy.platformMotion(message);
            return null;
        }
    }
}

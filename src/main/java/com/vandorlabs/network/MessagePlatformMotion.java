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
    private double x,z,before,after,dx,dz;
    public MessagePlatformMotion withHorizontal(double dx,double dz){this.dx=dx;this.dz=dz;return this;}
    public MessagePlatformMotion() { }
    public MessagePlatformMotion(EntityPlayer player,double before,double after) {
        dimension=player.dimension; x=player.posX; z=player.posZ; this.before=before; this.after=after;
    }
    @Override public void fromBytes(ByteBuf buf) {
        dimension=buf.readInt(); x=buf.readDouble(); z=buf.readDouble(); before=buf.readDouble(); after=buf.readDouble();
        if(buf.readableBytes()>=16){dx=buf.readDouble();dz=buf.readDouble();}
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension); buf.writeDouble(x); buf.writeDouble(z); buf.writeDouble(before); buf.writeDouble(after);buf.writeDouble(dx);buf.writeDouble(dz);
    }
    public void apply(EntityPlayer player) {
        if (player==null || player.dimension!=dimension || player.isDead || player.isRiding()
                || player.noClip || player.capabilities.isFlying || player.motionY>0.1) return;
        // Discard late corrections after walking/jumping off, teleporting, or changing dimensions.
        double feet=player.getEntityBoundingBox().minY;
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || Math.abs(dx)>.1 || Math.abs(dz)>.1 || !Double.isFinite(after) || Math.abs(player.posX-x)>.75 || Math.abs(player.posZ-z)>.75
                || Math.min(Math.abs(feet-before),Math.abs(feet-after))>.5) return;
        player.setPosition(player.posX+dx,player.posY+after-feet,player.posZ+dz);
        player.onGround=true; player.fallDistance=0; player.motionY=0;
        // X/Z momentum, camera direction, and keyboard movement are deliberately untouched.
    }
    public static class Handler implements IMessageHandler<MessagePlatformMotion,IMessage> {
        @Override public IMessage onMessage(MessagePlatformMotion message,MessageContext context) {
            VandorLabs.proxy.platformMotion(message);
            return null;
        }
    }
}

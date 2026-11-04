package com.vandorlabs.network;

import com.vandorlabs.garage.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.util.UUID;

/** Cosmetic poses may lead a slow server; validate the exact panel and current motion identity. */
public final class MessageGarageDoorUse implements IMessage {
    private BlockPos pos;private Vec3d point;private UUID identity;private long sequence;private double pose;private boolean valid;
    public MessageGarageDoorUse(){}
    public MessageGarageDoorUse(GarageDoorTile tile,Vec3d point){GarageDoorTile root=tile.root();pos=tile.getPos();this.point=point;identity=root.identity;sequence=root.sequence;pose=com.vandorlabs.VandorLabs.proxy.garageSelectionPose(root);valid=true;}
    public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeDouble(point.x);b.writeDouble(point.y);b.writeDouble(point.z);b.writeLong(identity.getMostSignificantBits());b.writeLong(identity.getLeastSignificantBits());b.writeLong(sequence);b.writeDouble(pose);}
    public void fromBytes(ByteBuf b){if(b.readableBytes()!=64)return;pos=BlockPos.fromLong(b.readLong());point=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());identity=new UUID(b.readLong(),b.readLong());sequence=b.readLong();pose=b.readDouble();valid=Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z)&&Double.isFinite(pose)&&pose>=0&&pose<=1&&sequence>=0;}
    public boolean interact(EntityPlayerMP player){
        if(!valid)return false;
        if(player.isSpectator()||!player.world.isBlockLoaded(pos)||!player.world.isBlockModifiable(player,pos))return false;
        GarageDoorTile tile=GarageDoorTile.at(player.world,pos);if(tile==null)return false;GarageDoorTile root=tile.root();
        if(!identity.equals(root.identity)||sequence!=root.sequence)return false;
        // At rest require the authoritative endpoint. During a current/recent
        // stroke the client can legitimately be ahead or behind server time.
        if(Math.abs(pose-root.pose(0))>.01&&(sequence==0||player.world.getTotalWorldTime()-root.started>root.duration+20))return false;
        Vec3d eye=player.getPositionEyes(1);double reach=player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue();if(!Double.isFinite(reach)||reach<=0||eye.squareDistanceTo(point)>(reach+.25)*(reach+.25))return false;
        Vec3d end=eye.add(point.subtract(eye).normalize().scale(reach+.25));RayTraceResult hit=GarageDoorInteractions.hit(tile,eye,end,pose);if(hit==null||hit.hitVec.squareDistanceTo(point)>.05*.05)return false;
        RayTraceResult terrain=player.world.rayTraceBlocks(eye,end,false,true,false);
        if(terrain!=null&&eye.squareDistanceTo(terrain.hitVec)+1e-6<eye.squareDistanceTo(hit.hitVec)){
            GarageDoorTile other=GarageDoorTile.at(player.world,terrain.getBlockPos());if(other==null)return false;
            boolean linked=false;try{for(GarageDoorTile column:tile.columns())if(column==other.root()){linked=true;break;}}catch(IllegalArgumentException incomplete){return false;}if(!linked)return false;
        }
        if(!player.canPlayerEdit(pos,hit.sideHit,player.getHeldItemMainhand()))return false;
        boolean before=root.targetOpen;net.minecraft.inventory.Container container=player.openContainer;
        EnumActionResult result=player.interactionManager.processRightClickBlock(player,player.world,player.getHeldItemMainhand(),EnumHand.MAIN_HAND,pos,hit.sideHit,(float)(point.x-pos.getX()),(float)(point.y-pos.getY()),(float)(point.z-pos.getZ()));player.inventoryContainer.detectAndSendChanges();return result==EnumActionResult.SUCCESS||before!=root.targetOpen||container!=player.openContainer;
    }
    public static final class Handler implements IMessageHandler<MessageGarageDoorUse,IMessage>{public IMessage onMessage(MessageGarageDoorUse message,MessageContext context){EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{if(message.valid)message.interact(player);});return null;}}
}

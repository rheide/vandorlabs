package com.vandorlabs.network;

import com.vandorlabs.canopy.CanopyInteractions;
import com.vandorlabs.tiles.TileEntityCanopy;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Validate reach against the visible shell when its stationary anchor is farther away. */
public final class MessageCanopyUse implements IMessage {
    private BlockPos pos;private EnumHand hand;private Vec3d point;private boolean valid;
    public MessageCanopyUse(){}
    public MessageCanopyUse(BlockPos pos,EnumHand hand,Vec3d point){this.pos=pos;this.hand=hand;this.point=point;valid=true;}
    public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeByte(hand.ordinal());b.writeDouble(point.x);b.writeDouble(point.y);b.writeDouble(point.z);}
    public void fromBytes(ByteBuf b){try{pos=BlockPos.fromLong(b.readLong());int value=b.readUnsignedByte();valid=value<EnumHand.values().length;if(valid)hand=EnumHand.values()[value];point=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());valid &= finite(point);}catch(RuntimeException failure){valid=false;}}
    private static boolean finite(Vec3d point){return point!=null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);}
    public static boolean interact(EntityPlayerMP player,BlockPos pos,EnumHand hand){
        Vec3d eye=player.getPositionEyes(1);double reach=player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue();
        RayTraceResult hit=CanopyInteractions.trace(player.world,eye,eye.add(player.getLookVec().scale(reach)));
        return hit!=null && interact(player,pos,hand,hit.hitVec);
    }
    public static boolean interact(EntityPlayerMP player,BlockPos pos,EnumHand hand,Vec3d point){
        if(!finite(point))return false;
        if(player.isSpectator() || !player.world.isBlockLoaded(pos) || !player.world.isBlockModifiable(player,pos))return false;
        TileEntityCanopy tile=TileEntityCanopy.at(player.world,pos);if(tile==null)return false;tile=tile.owner();
        if(!tile.draws() || !tile.getPos().equals(pos))return false;
        double reach=player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue();
        Vec3d eye=player.getPositionEyes(1);if(!Double.isFinite(reach) || reach<=0 || eye.squareDistanceTo(point)>(reach+.25)*(reach+.25))return false;
        Vec3d end=eye.add(point.subtract(eye).normalize().scale(reach+.25));
        RayTraceResult hit=CanopyInteractions.trace(player.world,eye,end);
        TileEntityCanopy selected=hit==null?null:TileEntityCanopy.at(player.world,hit.getBlockPos());
        if(selected==null || selected.owner()!=tile)return false;
        RayTraceResult terrain=player.world.rayTraceBlocks(eye,end,false,true,false);
        if(terrain!=null && eye.squareDistanceTo(terrain.hitVec)+1e-6<eye.squareDistanceTo(hit.hitVec))return false;
        ItemStack stack=player.getHeldItem(hand);
        if(!stack.isEmpty() && !(stack.getItem() instanceof ItemBlock) && stack.getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER && stack.getItem()!=com.vandorlabs.items.ModItems.DUPLIFIER)return false;
        if(stack.getItem() instanceof com.vandorlabs.blocks.BlockCanopy.CanopyItem)return false;
        // Block placement follows normal visible surfaces; never redirect it through glazing.
        if(stack.getItem() instanceof ItemBlock)return false;
        Vec3d local=hit.hitVec.subtract(new Vec3d(pos));
        boolean before=tile.targetOpen;net.minecraft.inventory.Container previous=player.openContainer;
        EnumActionResult result=player.interactionManager.processRightClickBlock(player,player.world,stack,hand,pos,hit.sideHit,(float)local.x,(float)local.y,(float)local.z);
        player.inventoryContainer.detectAndSendChanges();
        // Forge 1.12 returns PASS for an empty hand even after successful block activation.
        return result==EnumActionResult.SUCCESS || tile.targetOpen!=before || player.openContainer!=previous;
    }
    public static final class Handler implements IMessageHandler<MessageCanopyUse,IMessage>{
        public IMessage onMessage(MessageCanopyUse message,MessageContext context){EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{if(message.valid)interact(player,message.pos,message.hand,message.point);});return null;}
    }
}

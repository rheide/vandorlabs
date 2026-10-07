package com.vandorlabs.network;

import com.vandorlabs.items.ItemProgrammableArmor;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** The client resolves resource-pack artwork; the server owns the held stack and interaction bounds. */
public final class MessageSampleArmorTexture implements IMessage {
    private BlockPos pos;
    private EnumHand hand;
    private ItemStack expected;
    private String sprite;
    public MessageSampleArmorTexture() { }
    public MessageSampleArmorTexture(BlockPos pos,EnumHand hand,ItemStack expected,String sprite) {
        this.pos=pos;this.hand=hand;this.expected=expected.copy();this.sprite=sprite;
    }
    @Override public void toBytes(ByteBuf out) {
        out.writeLong(pos.toLong());out.writeBoolean(hand==EnumHand.OFF_HAND);
        ByteBufUtils.writeItemStack(out,expected);ByteBufUtils.writeUTF8String(out,sprite);
    }
    @Override public void fromBytes(ByteBuf in) {
        pos=BlockPos.fromLong(in.readLong());hand=in.readBoolean()?EnumHand.OFF_HAND:EnumHand.MAIN_HAND;
        expected=ByteBufUtils.readItemStack(in);sprite=ByteBufUtils.readUTF8String(in);
    }
    public static boolean apply(EntityPlayerMP player,BlockPos pos,EnumHand hand,ItemStack expected,String sprite) {
        ItemStack stack=player.getHeldItem(hand);
        double reach=player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue();
        if(!player.isEntityAlive() || player.isSpectator() || !player.isSneaking()
                || !(stack.getItem() instanceof ItemProgrammableArmor)
                || !ItemStack.areItemStacksEqual(stack,expected) || !ItemProgrammableArmor.validSample(sprite)
                || !player.world.isBlockLoaded(pos) || player.world.isAirBlock(pos)
                || player.getPositionEyes(1).squareDistanceTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>(reach+1)*(reach+1))return false;
        ItemProgrammableArmor.setSample(stack,sprite);
        player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();
        player.connection.sendPacket(new net.minecraft.network.play.server.SPacketSetSlot(-2,
                hand==EnumHand.MAIN_HAND?player.inventory.currentItem:40,stack));
        player.sendStatusMessage(new TextComponentTranslation("item.vandorlabs.programmable_armor.sampled",sprite),true);
        return true;
    }
    public static final class Handler implements IMessageHandler<MessageSampleArmorTexture,IMessage> {
        @Override public IMessage onMessage(MessageSampleArmorTexture message,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->apply(player,message.pos,message.hand,message.expected,message.sprite));
            return null;
        }
    }
}

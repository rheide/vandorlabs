package com.vandorlabs.items;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;

/** Copy appearance alone; resolve native uniforms to the destination equipment slot. */
public final class DuplifierArmorTextures {
    public static final String KEY="armorAppearance";
    private DuplifierArmorTextures() { }

    public static String copyFrom(ItemStack armor,ItemStack tool) {
        if(!(armor.getItem() instanceof ItemProgrammableArmor) || tool.getItem()!=ModItems.DUPLIFIER)return null;
        NBTTagCompound appearance=new NBTTagCompound();
        String sample=ItemProgrammableArmor.sample(armor);
        int choice=ItemProgrammableArmor.texture(armor);
        if(sample!=null)appearance.setString("sample",sample);
        else if(ItemProgrammableArmor.validTexture(armor,choice))appearance.setInteger("choice",choice);
        else return null;
        NBTTagCompound settings=new NBTTagCompound();settings.setTag(KEY,appearance);
        tool.setTagInfo(ItemDuplifier.SETTINGS_TAG,settings);
        tool.setTagInfo(ItemDuplifier.SOURCE_TAG,new net.minecraft.nbt.NBTTagString(armor.getDisplayName()));
        return armor.getDisplayName();
    }
    public static boolean apply(ItemStack armor,NBTTagCompound settings) {
        if(!(armor.getItem() instanceof ItemProgrammableArmor) || settings==null || !settings.hasKey(KEY,10))return false;
        NBTTagCompound appearance=settings.getCompoundTag(KEY);
        if(appearance.hasKey("sample",8)) {
            String sample=appearance.getString("sample");if(!ItemProgrammableArmor.validSample(sample))return false;
            ItemProgrammableArmor.setSample(armor,sample);return true;
        }
        if(!appearance.hasKey("choice",3))return false;
        int choice=appearance.getInteger("choice");
        ArmorTextures.Entry role=ArmorTextures.entry(choice);
        if(role!=null) {
            role=ArmorTextures.forSlot(choice,((ItemProgrammableArmor)armor.getItem()).armorType);
            if(role==null)return false;
            choice=role.choice;
        }
        if(!ItemProgrammableArmor.validTexture(armor,choice))return false;
        ItemProgrammableArmor.setTexture(armor,choice);return true;
    }
    public static boolean applyTo(ItemStack armor,ItemStack tool) {
        return tool.getItem()==ModItems.DUPLIFIER && apply(armor,DuplifierApplyOptions.selected(
                tool.getSubCompound(ItemDuplifier.SETTINGS_TAG),DuplifierApplyOptions.mask(tool)));
    }
    public static boolean interact(EntityPlayer player,ItemStack tool,ItemStack armor) {
        boolean copied=player.isSneaking();
        String name=copied?copyFrom(armor,tool):null;
        boolean success=copied?name!=null:applyTo(armor,tool);
        player.sendStatusMessage(new TextComponentString(success
                ?copied?"Duplifier copied "+name:"Duplifier applied armor texture"
                :"No copied armor texture applies here"),true);
        if(success){
            player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();
            if(!copied && armor==player.getHeldItemOffhand() && player instanceof net.minecraft.entity.player.EntityPlayerMP)
                ((net.minecraft.entity.player.EntityPlayerMP)player).connection.sendPacket(new net.minecraft.network.play.server.SPacketSetSlot(-2,40,armor));
        }
        return success;
    }
}

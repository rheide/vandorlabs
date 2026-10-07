package com.vandorlabs.items;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Diamond armor behavior with an independent, persistent programmable material. */
public final class ItemProgrammableArmor extends ItemArmor {
    public static final String TEXTURE_TAG = "ArmorTexture";
    public static final String SAMPLE_TAG = "ArmorSampleTexture";
    public static final int SAMPLE_CHOICE = 0x7FFFFFFE;
    public static boolean validSample(String sprite) {
        return sprite!=null && sprite.length()<=256 && sprite.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                && !sprite.contains("..") && !sprite.endsWith(":missingno");
    }
    public static String sample(ItemStack stack) {
        NBTTagCompound tag=stack.getTagCompound();
        String value=tag==null?null:tag.getString(SAMPLE_TAG);
        return validSample(value)?value:null;
    }
    public static void setSample(ItemStack stack,String sprite) {
        if(!validSample(sprite))throw new IllegalArgumentException("Invalid armor sprite");
        NBTTagCompound tag=stack.getTagCompound();
        if(tag==null) {tag=new NBTTagCompound();stack.setTagCompound(tag);}
        tag.setString(SAMPLE_TAG,sprite);
    }

    public ItemProgrammableArmor(String name, EntityEquipmentSlot slot) {
        super(ArmorMaterial.DIAMOND, 0, slot);
        setRegistryName(VandorLabs.MODID, name);
        setUnlocalizedName("vandorlabs." + name);
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
    }

    /** Match the ordinary block picker, excluding its optional custom sample action. */
    public static boolean validTexture(int choice) {
        int index = ScreenHousingTextures.localIndex(choice);
        if (ScreenHousingTextures.choiceAt(index) != choice || !ScreenHousingTextures.visible(index)) return false;
        String category = ScreenHousingTextures.category(index);
        return !category.equals("Screens")
                && !category.equals("Doors") && !category.equals("Double Doors");
    }

    /** Enforce native armor slot filtering on both the client and the server. */
    public static boolean validTexture(ItemStack stack, int choice) {
        return stack.getItem() instanceof ItemProgrammableArmor
                && (validTexture(choice) || ArmorTextures.fits(choice, ((ItemProgrammableArmor)stack.getItem()).armorType));
    }

    public static int texture(ItemStack stack) {
        if(sample(stack)!=null)return SAMPLE_CHOICE;
        NBTTagCompound tag = stack.getTagCompound();
        if(tag != null && tag.hasKey(TEXTURE_TAG, 3))return tag.getInteger(TEXTURE_TAG);
        return stack.getItem() instanceof ItemProgrammableArmor
                ? ArmorTextures.defaultChoice(((ItemProgrammableArmor)stack.getItem()).armorType) : 0;
    }

    public static void setTexture(ItemStack stack, int choice) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) { tag = new NBTTagCompound(); stack.setTagCompound(tag); }
        tag.removeTag(SAMPLE_TAG);
        tag.setInteger(TEXTURE_TAG, choice);
    }

    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (player.isSneaking() && ConfigurationAccess.canConfigure(player)) {
            if (!world.isRemote) player.openGui(VandorLabs.instance, GuiHandler.GUI_PROGRAMMABLE_ARMOR,
                    world, hand.ordinal(), 0, 0);
            return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
        }
        return super.onItemRightClick(world, player, hand);
    }

    @Override @SideOnly(Side.CLIENT)
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        String sampled=sample(stack);
        if(sampled!=null)return com.vandorlabs.client.ProgrammableArmorTextures.texture(sampled,slot==EntityEquipmentSlot.LEGS);
        int choice=texture(stack);
        ArmorTextures.Entry armor=ArmorTextures.entry(choice);
        if(armor!=null && armor.slot==armorType)return armor.worn;
        return com.vandorlabs.client.ProgrammableArmorTextures.texture(validTexture(choice)?choice:0,
                slot == EntityEquipmentSlot.LEGS);
    }

    @Override @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, java.util.List<String> lines,
            net.minecraft.client.util.ITooltipFlag flag) {
        super.addInformation(stack, world, lines, flag);
        lines.add(net.minecraft.client.resources.I18n.format("item.vandorlabs.programmable_armor.hint"));
    }
}

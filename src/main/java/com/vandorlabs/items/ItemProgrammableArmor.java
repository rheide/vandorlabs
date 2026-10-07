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
        return !category.equals("Screens") && !category.equals("Lights")
                && !category.equals("Doors") && !category.equals("Double Doors");
    }

    public static int texture(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TEXTURE_TAG, 3) ? tag.getInteger(TEXTURE_TAG) : 0;
    }

    public static void setTexture(ItemStack stack, int choice) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) { tag = new NBTTagCompound(); stack.setTagCompound(tag); }
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
        return com.vandorlabs.client.ProgrammableArmorTextures.texture(texture(stack));
    }

    @Override @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, java.util.List<String> lines,
            net.minecraft.client.util.ITooltipFlag flag) {
        super.addInformation(stack, world, lines, flag);
        lines.add(net.minecraft.client.resources.I18n.format("item.vandorlabs.programmable_armor.hint"));
    }
}

package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class NativeArmorItem extends ArmorItem {
    private static final String TEXTURE = "ArmorTexture";
    private static final String SAMPLE = "ArmorSampleTexture";

    NativeArmorItem(Type type) {
        super(ArmorMaterials.DIAMOND, type, new Properties().durability(type.getDurability(33)));
    }

    public static NativeArmorCatalog.Design design(ItemStack stack) {
        NativeArmorItem item = (NativeArmorItem) stack.getItem();
        var design = NativeArmorCatalog.design(choice(stack), item.getEquipmentSlot());
        return design == null ? item.defaultDesign : design;
    }

    private final NativeArmorCatalog.Design defaultDesign = NativeArmorCatalog.defaultDesign(getEquipmentSlot());

    public static int choice(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe();
        if (tag.contains(TEXTURE, Tag.TAG_INT)) return tag.getInt(TEXTURE);
        return ((NativeArmorItem)stack.getItem()).defaultDesign.choice();
    }

    static boolean listedMaterial(int choice) {
        if (choice < 0 || choice >= ModernFinishCatalog.ENTRIES.size()) return false;
        var material = ModernFinishCatalog.option(choice);
        return material.visible() && material.general();
    }

    static boolean configure(ItemStack stack, int choice) {
        if (!(stack.getItem() instanceof NativeArmorItem item)
            || NativeArmorCatalog.design(choice, item.getEquipmentSlot()) == null && !listedMaterial(choice)) return false;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(TEXTURE, choice);
            tag.remove(SAMPLE);
        });
        return true;
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot,
                                            ArmorMaterial.Layer layer, boolean innerModel) {
        int choice = choice(stack);
        if (listedMaterial(choice)) {
            return NativeArmorMaterials.worn(choice, layer.texture(innerModel), design(stack).worn());
        }
        return design(stack).worn();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && NativeConfigurationAccess.canConfigure(player) && !player.isSpectator()) {
            if (player instanceof ServerPlayer server) {
                server.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new NativeArmorMenu(id, inventory, hand),
                    stack.getHoverName()), buffer -> buffer.writeEnum(hand));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return player.getItemBySlot(getEquipmentSlot()).isEmpty()
            ? super.use(level, player, hand) : InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("item.vandorlabs.programmable_armor.hint"));
    }
}

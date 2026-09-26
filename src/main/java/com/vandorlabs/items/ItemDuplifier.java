package com.vandorlabs.items;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockPropulsionLight;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Shift-click stores semantic settings; click applies supported settings. */
@Mod.EventBusSubscriber(modid = VandorLabs.MODID)
public final class ItemDuplifier extends Item {
    public static final String SETTINGS_TAG = "DuplifierSettings";
    public static final String SOURCE_TAG = "DuplifierSource";

    public ItemDuplifier() {
        setRegistryName(VandorLabs.MODID, "duplifier");
        setUnlocalizedName("vandorlabs.duplifier");
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setMaxStackSize(1);
    }

    @Override public String getItemStackDisplayName(ItemStack stack) {
        String name = super.getItemStackDisplayName(stack);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(SOURCE_TAG, 8)) return name;
        String source = tag.getString(SOURCE_TAG);
        return source.isEmpty() ? name : name + " - " + source;
    }

    public static boolean hasCopy(ItemStack tool) {
        NBTTagCompound settings = tool.getSubCompound(SETTINGS_TAG);
        return settings != null && !settings.hasNoTags();
    }

    public static void clearCopyState(ItemStack tool) {
        NBTTagCompound root = tool.getTagCompound();
        if (root == null) return;
        root.removeTag(SETTINGS_TAG);
        root.removeTag(SOURCE_TAG);
        tool.setTagCompound(root.hasNoTags() ? null : root);
    }

    @Override public ActionResult<ItemStack> onItemRightClick(World world,
            EntityPlayer player, EnumHand hand) {
        ItemStack tool = player.getHeldItem(hand);
        if (!player.isSneaking())
            return new ActionResult<>(EnumActionResult.PASS, tool);
        if (!world.isRemote) {
            clearCopyState(tool);
            player.inventory.markDirty();
            player.sendStatusMessage(new TextComponentString("Duplifier cleared"), true);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, tool);
    }

    public static String copyFrom(World world, BlockPos pos, ItemStack tool) {
        NBTTagCompound settings = ProgrammableSettings.capture(world, pos);
        if (settings == null) return null;
        Block block = world.getBlockState(pos).getBlock();
        if (block instanceof BlockPropulsionLight) {
            String family = ((BlockPropulsionLight) block).familyId();
            if (!family.isEmpty())
                block = Block.REGISTRY.getObject(new ResourceLocation(VandorLabs.MODID, family));
        }
        ItemStack sourceItem = new ItemStack(block);
        String source = sourceItem.isEmpty() ? block.getLocalizedName()
                : sourceItem.getDisplayName();
        NBTTagCompound root = tool.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        // Replace the prior snapshot so absent source settings never linger.
        root.setTag(SETTINGS_TAG, settings);
        root.setString(SOURCE_TAG, source);
        tool.setTagCompound(root);
        return source;
    }

    public static boolean applyTo(World world, BlockPos pos, ItemStack tool,
            EntityPlayer player) {
        NBTTagCompound settings = tool.getSubCompound(SETTINGS_TAG);
        return ProgrammableSettings.apply(world, pos, settings, player);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != EnumHand.MAIN_HAND
                || event.getItemStack().getItem() != ModItems.DUPLIFIER) return;
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        World world = event.getWorld();
        if (world.isRemote) return;
        EntityPlayer player = event.getEntityPlayer();
        BlockPos pos = ProgrammableTarget.settingsPos(world, event.getPos());
        if (!world.isBlockLoaded(pos) || !world.isBlockModifiable(player, pos)
                || !player.canPlayerEdit(pos, event.getFace(), event.getItemStack())) return;
        ItemStack tool = event.getItemStack();
        if (player.isSneaking()) {
            String source = copyFrom(world, pos, tool);
            if (source == null) {
                player.sendStatusMessage(new TextComponentString("No configurable settings here"), true);
                return;
            }
            player.inventory.markDirty();
            player.sendStatusMessage(new TextComponentString("Duplifier copied " + source), true);
            return;
        }
        NBTTagCompound stored = tool.getSubCompound(SETTINGS_TAG);
        if (stored == null || stored.hasNoTags()) {
            player.sendStatusMessage(new TextComponentString("Duplifier has no copied settings"), true);
            return;
        }
        if (applyTo(world, pos, tool, player))
            player.sendStatusMessage(new TextComponentString("Duplifier applied settings"), true);
        else
            player.sendStatusMessage(new TextComponentString("No copied settings apply here"), true);
    }
}

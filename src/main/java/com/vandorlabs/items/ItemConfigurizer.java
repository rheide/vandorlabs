package com.vandorlabs.items;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockRampController;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableChair;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opens the existing configuration menus without the creative-mode gate. */
@Mod.EventBusSubscriber(modid = VandorLabs.MODID)
public final class ItemConfigurizer extends Item {
    public ItemConfigurizer() {
        setRegistryName(VandorLabs.MODID, "configurizer");
        setUnlocalizedName("vandorlabs.configurizer");
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setMaxStackSize(1);
    }

    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGH)
    public static void onArmorStand(PlayerInteractEvent.EntityInteractSpecific event) {
        if(event.getHand()!=EnumHand.MAIN_HAND || event.getItemStack().getItem()!=ModItems.CONFIGURIZER
                || !(event.getTarget() instanceof net.minecraft.entity.item.EntityArmorStand))return;
        net.minecraft.entity.item.EntityArmorStand stand=(net.minecraft.entity.item.EntityArmorStand)event.getTarget();
        if(stand.hasMarker() || event.getEntityPlayer().isSpectator())return;
        event.setCanceled(true);event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
        net.minecraft.inventory.EntityEquipmentSlot slot=armorSlot(stand,event.getLocalPos().y);
        if(slot==null || !(stand.getItemStackFromSlot(slot).getItem() instanceof ItemProgrammableArmor))return;
        if(!event.getWorld().isRemote)event.getEntityPlayer().openGui(VandorLabs.instance,
                GuiHandler.GUI_PROGRAMMABLE_ARMOR,event.getWorld(),stand.getEntityId(),slot.getIndex(),1);
    }
    /** Match vanilla armor-stand click regions, including its small stand scale. */
    public static net.minecraft.inventory.EntityEquipmentSlot armorSlot(net.minecraft.entity.item.EntityArmorStand stand,double y) {
        boolean small=stand.isSmall();double height=small?y*2:y;
        net.minecraft.inventory.EntityEquipmentSlot slot;
        if(height>=.1 && height<.1+(small?.8:.45) && stand.hasItemInSlot(net.minecraft.inventory.EntityEquipmentSlot.FEET))slot=net.minecraft.inventory.EntityEquipmentSlot.FEET;
        else if(height>=.9+(small?.3:0) && height<.9+(small?1:.7) && stand.hasItemInSlot(net.minecraft.inventory.EntityEquipmentSlot.CHEST))slot=net.minecraft.inventory.EntityEquipmentSlot.CHEST;
        else if(height>=.4 && height<.4+(small?1:.8) && stand.hasItemInSlot(net.minecraft.inventory.EntityEquipmentSlot.LEGS))slot=net.minecraft.inventory.EntityEquipmentSlot.LEGS;
        else if(height>=1.6 && stand.hasItemInSlot(net.minecraft.inventory.EntityEquipmentSlot.HEAD))slot=net.minecraft.inventory.EntityEquipmentSlot.HEAD;
        else return null;
        return slot;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != EnumHand.MAIN_HAND
                || event.getItemStack().getItem() != ModItems.CONFIGURIZER) return;
        World world = event.getWorld();
        BlockPos pos = ProgrammableTarget.settingsPos(world, event.getPos());
        IBlockState state = world.getBlockState(pos);
        TileEntity tile = world.getTileEntity(pos);
        int gui;
        if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableTrapdoor) gui = GuiHandler.GUI_PROGRAMMABLE_TRAPDOOR;
        else if (tile instanceof TileEntitySpaceDoor) gui = GuiHandler.GUI_SPACE_DOOR;
        else if (tile instanceof TileEntityProgrammableGlass) gui = GuiHandler.GUI_PROGRAMMABLE_GLASS;
        else if (tile instanceof TileEntityProgrammableLight) gui = GuiHandler.GUI_PROGRAMMABLE_LIGHT;
        else if (tile instanceof com.vandorlabs.tiles.TileEntityLandingGear) gui = GuiHandler.GUI_LANDING_GEAR;
        else if (tile instanceof TileEntityProgrammableChair) gui = GuiHandler.GUI_PROGRAMMABLE_CHAIR;
        else if (state.getBlock() instanceof BlockRampController) gui = GuiHandler.GUI_RAMP_CONTROLLER;
        else if (tile instanceof TileEntityAnimatedScreenSelector)
            gui = GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR;
        else if (tile instanceof RedstoneChannelMember) gui = GuiHandler.GUI_REDSTONE_CHANNEL;
        else return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
        if (!world.isRemote) {
            EntityPlayer player = event.getEntityPlayer();
            player.openGui(VandorLabs.instance, gui, world,
                    pos.getX(), pos.getY(), pos.getZ());
        }
    }
}

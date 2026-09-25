package com.vandorlabs.items;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockRampController;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableChair;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.BlockDoor;
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

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != EnumHand.MAIN_HAND
                || event.getItemStack().getItem() != ModItems.CONFIGURIZER) return;
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockVandorDoor
                && state.getValue(BlockVandorDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER)
            pos = pos.down();
        else if (state.getBlock() instanceof BlockDoor
                && state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER)
            pos = pos.down();
        TileEntity tile = world.getTileEntity(pos);
        int gui;
        if (tile instanceof TileEntitySpaceDoor) gui = GuiHandler.GUI_SPACE_DOOR;
        else if (tile instanceof TileEntityProgrammableGlass) gui = GuiHandler.GUI_PROGRAMMABLE_GLASS;
        else if (tile instanceof TileEntityProgrammableLight) gui = GuiHandler.GUI_PROGRAMMABLE_LIGHT;
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

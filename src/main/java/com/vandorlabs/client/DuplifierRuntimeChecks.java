package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.items.ItemDuplifier;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Exercises semantic transfers between different placed programmable blocks. */
final class DuplifierRuntimeChecks {
    private DuplifierRuntimeChecks() { }

    static void run(EntityPlayer player) {
        World world = player.world;
        BlockPos source = new BlockPos(54, 245, 54);
        BlockPos target = source.east(3);
        try {
            Block rocket = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", "rocket_thruster"));
            world.setBlockState(source, rocket.getDefaultState(), 3);
            TileEntityRedstoneLight engine = (TileEntityRedstoneLight) world.getTileEntity(source);
            engine.setSideTexture(20);
            engine.setJoin(false);
            engine.setManualMode(2, true);
            engine.setRedstoneChannel(19);
            ItemStack tool = new ItemStack(ModItems.DUPLIFIER);
            require(ItemDuplifier.copyFrom(world, source, tool) != null
                            && tool.getDisplayName().contains("Rocket Thruster"),
                    "duplifier did not label the captured source");
            NBTTagCompound copied = tool.getSubCompound(ItemDuplifier.SETTINGS_TAG);
            require(copied != null && copied.getInteger(ProgrammableSettings.WALL_TEXTURE) == 20
                            && !copied.getBoolean(ProgrammableSettings.JOIN)
                            && copied.getInteger(ProgrammableSettings.CHANNEL) == 19,
                    "thruster settings were not captured under shared keys");

            world.setBlockState(target, ModBlocks.PROGRAMMABLE_PORTHOLE_WALL.getDefaultState(), 3);
            require(ItemDuplifier.applyTo(world, target, tool, player),
                    "thruster settings did not apply to porthole");
            TileEntityAnimatedScreenSelector porthole =
                    (TileEntityAnimatedScreenSelector) world.getTileEntity(target);
            require(porthole.getHousingTexture() == 20 && !porthole.isJoinPortholes()
                            && porthole.getRedstoneChannel() == 19,
                    "thruster to porthole wall texture, Join or channel transfer failed");

            NBTTagCompound fromPorthole = ProgrammableSettings.capture(world, target);
            Block ion = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", "ion_drive"));
            world.setBlockState(source, ion.getDefaultState(), 3);
            require(ProgrammableSettings.apply(world, source, fromPorthole),
                    "porthole settings did not apply to ion drive");
            TileEntityRedstoneLight ionTile = (TileEntityRedstoneLight) world.getTileEntity(source);
            require(ionTile.getSideTexture() == 20 && !ionTile.isJoin()
                            && ionTile.getRedstoneChannel() == 19,
                    "porthole to thruster shared settings transfer failed");

            world.setBlockState(source, ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(), 3);
            require(ProgrammableSettings.apply(world, source, fromPorthole),
                    "porthole finish did not apply to programmable block");
            require(((TileEntityAnimatedScreenSelector) world.getTileEntity(source))
                            .getHousingTexture() == 20,
                    "programmable block did not use the shared wall finish");

            world.setBlockState(source, ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(), 3);
            TileEntityAnimatedScreenSelector screen =
                    (TileEntityAnimatedScreenSelector) world.getTileEntity(source);
            screen.setSelectedScreen("engineering_screen");
            screen.setDisplayMode(TileEntityAnimatedScreenSelector.MODE_STATIC);
            screen.setAnimationSpeedIndex(2);
            screen.setRedstoneEnabled(true);
            screen.setHousingTexture(14);
            require(ItemDuplifier.copyFrom(world, source, tool) != null,
                    "duplifier could not replace the captured source");
            NBTTagCompound screenSettings = tool.getSubCompound(ItemDuplifier.SETTINGS_TAG);
            require(!screenSettings.hasKey(ProgrammableSettings.JOIN),
                    "new snapshot retained Join from the previous block");
            world.setBlockState(target, ModBlocks.PROGRAMMABLE_CONSOLE.getDefaultState(), 3);
            require(ItemDuplifier.applyTo(world, target, tool, player),
                    "screen settings did not apply to programmable console");
            TileEntityAnimatedScreenSelector console =
                    (TileEntityAnimatedScreenSelector) world.getTileEntity(target);
            require("engineering_screen".equals(console.getSelectedScreen())
                            && console.getDisplayMode() == TileEntityAnimatedScreenSelector.MODE_STATIC
                            && console.getAnimationSpeedIndex() == 2
                            && console.isRedstoneEnabled() && console.getHousingTexture() == 14,
                    "screen primary, display, redstone or wall settings transfer failed");
            require(!ProgrammableSettings.apply(world, source, new NBTTagCompound()),
                    "empty snapshot should not change target");
        } finally {
            world.setBlockToAir(source);
            world.setBlockToAir(target);
        }
        System.out.println("[vandorlabs][reprolab] duplifier-runtime PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

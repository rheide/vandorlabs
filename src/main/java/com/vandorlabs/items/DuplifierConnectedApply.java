package com.vandorlabs.items;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Discover the original matching region before applying any copied settings. */
public final class DuplifierConnectedApply {
    public static final int MAX_CELLS = 4096;

    private DuplifierConnectedApply() { }

    private static boolean editable(World world, BlockPos pos, EntityPlayer player,
            EnumFacing face, ItemStack tool) {
        return world.isBlockLoaded(pos) && world.isBlockModifiable(player, pos)
                && player.canPlayerEdit(pos, face, tool);
    }

    private static NBTTagCompound configuration(World world, BlockPos pos) {
        NBTTagCompound settings = ProgrammableSettings.capture(world, pos);
        // Disabled face overrides still hold saved choices. Those must match
        // too, even though the normal copy snapshot omits them while disabled.
        TileEntity tile = world.getTileEntity(pos);
        if (settings != null && tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector) {
            com.vandorlabs.tiles.TileEntityAnimatedScreenSelector screen =
                    (com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile;
            settings.setBoolean("placement_ceiling", screen.isCeilingMounted());
            settings.setInteger("placement_ceiling_position", screen.getCeilingPosition(-1));
            settings.setInteger("placement_wall_position", screen.getWallPosition(-1));
        }
        if (settings != null && settings.hasKey(ProgrammableSettings.FACE_TEXTURES)
                && tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)
            settings.getCompoundTag(ProgrammableSettings.FACE_TEXTURES).setIntArray("choices",
                    ((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile)
                            .getFaceTextures().choices());
        if (settings != null) {
            settings.removeTag(ProgrammableSettings.SWITCH_ROTATION);
            settings.removeTag(ProgrammableSettings.RAMP_DIRECTION);
        }
        return settings;
    }

    private static boolean matchingState(IBlockState original, IBlockState candidate) {
        if (original.getBlock() != candidate.getBlock()) return false;
        for (net.minecraft.block.properties.IProperty<?> property : original.getPropertyKeys()) {
            String name = property.getName();
            if (name.equals("facing") || name.equals("rotation") || name.equals("rotated")
                    || name.equals("hinge") || name.equals("inverted")) continue;
            if (!original.getValue(property).equals(candidate.getValue(property))) return false;
        }
        return true;
    }

    /** Returns applied logical blocks, or -1 when the region exceeds the limit. */
    public static int apply(World world, BlockPos clicked, ItemStack tool,
            EntityPlayer player, EnumFacing face) {
        if (world.isRemote || !editable(world, clicked, player, face, tool)) return 0;
        BlockPos origin = ProgrammableTarget.settingsPos(world, clicked);
        if (!editable(world, origin, player, face, tool)) return 0;
        IBlockState originalState = world.getBlockState(origin);
        NBTTagCompound originalSettings = configuration(world, origin);
        if (originalSettings == null) return 0;
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> targets = new LinkedHashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.add(clicked.toImmutable());
        pending.add(origin.toImmutable());
        int cells = 0;
        while (!pending.isEmpty()) {
            BlockPos cell = pending.removeFirst();
            if (!visited.add(cell) || !editable(world, cell, player, face, tool)) continue;
            BlockPos root = ProgrammableTarget.settingsPos(world, cell);
            if (!editable(world, root, player, face, tool)
                    || !matchingState(originalState, world.getBlockState(root))
                    || !originalSettings.equals(configuration(world, root))) continue;
            if (++cells > MAX_CELLS) return -1;
            targets.add(root.toImmutable());
            // Walk physical cells so the upper half of a door or seat can
            // connect to another matching object. Apply to each root once.
            for (EnumFacing direction : EnumFacing.values()) pending.add(cell.offset(direction));
        }
        int applied = 0;
        for (BlockPos target : targets) {
            if (editable(world, target, player, face, tool)
                    && world.getBlockState(target).getBlock() == originalState.getBlock()
                    && ItemDuplifier.applyTo(world, target, tool, player)) applied++;
        }
        return applied;
    }
}

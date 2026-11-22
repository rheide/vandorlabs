package com.vandorlabs.neoforge.builders;

import com.vandorlabs.drives.MaxwellEngine;
import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.neoforge.transfer.NativeConstructorFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

public final class NativeBuilderFormation {
    public record Layout(Direction facing, int width, int x, int z) {}

    public static int width(NativeBuilderDesigns.Design design, CompoundTag tag) {
        if (!tag.contains(MaxwellEngine.WIDTH_TAG)) return design.width();
        if (!design.id().equals(MaxwellEngine.DESIGN) || !tag.contains(MaxwellEngine.WIDTH_TAG, Tag.TAG_INT))
            throw new IllegalArgumentException("Unexpected Maxwell frame width");
        int width = tag.getInt(MaxwellEngine.WIDTH_TAG);
        MaxwellEngine.depth(width);
        return width;
    }

    static void writeWidth(CompoundTag tag, int width) {
        tag.remove(MaxwellEngine.WIDTH_TAG);
        if (width != MaxwellEngine.BASE_WIDTH) tag.putInt(MaxwellEngine.WIDTH_TAG, width);
    }

    public static CoreLayout layout(NativeBuilderDesigns.Design design, int width, int facing, int x, int z) {
        return design.id().equals(MaxwellEngine.DESIGN)
                ? new CoreLayout(width, width, MaxwellEngine.depth(width), facing, x, z)
                : new CoreLayout(design.width(), design.height(), design.depth(), facing, x, z);
    }

    public static CoreLayout savedLayout(NativeBuilderDesigns.Design design, Direction facing, CompoundTag tag) {
        var layout = layout(design, width(design, tag), facing.get3DDataValue(),
                tag.getInt("ConstructorCoreX"), tag.getInt("ConstructorCoreZ"));
        if (!layout.canonicalCorner()) throw new IllegalArgumentException("Unknown builder frame corner");
        return layout;
    }

    static boolean contains(CoreLayout layout, BlockPos offset) {
        int x = offset.getX() + layout.pivotX(), z = offset.getZ() + layout.pivotZ();
        return x >= 0 && x < layout.extentX() && z >= 0 && z < layout.extentZ()
                && offset.getY() >= 0 && offset.getY() < layout.height;
    }

    static boolean frameAt(CoreLayout layout, BlockPos offset) {
        int x = offset.getX() + layout.pivotX(), z = offset.getZ() + layout.pivotZ(), y = offset.getY();
        return switch (layout.turns) {
            case 1 -> layout.frame(z, y, layout.depth - 1 - x);
            case 2 -> layout.frame(layout.width - 1 - x, y, layout.depth - 1 - z);
            case 3 -> layout.frame(layout.width - 1 - z, y, x);
            default -> layout.frame(x, y, z);
        };
    }

    public static boolean loaded(Level level, BlockPos pos) {
        return level != null && !level.isOutsideBuildHeight(pos)
                && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    public static net.minecraft.world.level.block.Block matter() { return NativeConstructorFrame.block(); }

    public static Layout discover(NativeBuilderEntity owner) {
        var design = owner.design();
        int[] widths = owner.maxwell() ? MaxwellEngine.widths() : new int[]{design.width()};
        for (int width : widths) {
            int depth = owner.maxwell() ? MaxwellEngine.depth(width) : design.depth();
            for (int turn = 0; turn < 4; turn++) {
                var face = Direction.from2DDataValue((owner.facing().get2DDataValue() + turn) % 4);
                for (int x : new int[]{0, width - 1}) for (int z : new int[]{0, depth - 1}) {
                    var shape = layout(design, width, face.get3DDataValue(), x, z);
                    if (matches(owner, shape)) return new Layout(face, width, x, z);
                }
            }
        }
        throw new IllegalArgumentException("Complete the Programmable Block frame and leave its interior empty");
    }

    private static boolean matches(NativeBuilderEntity owner, CoreLayout shape) {
        var level = owner.getLevel();
        var root = owner.getBlockPos();
        for (int x = 0; x < shape.width; x++) for (int y = 0; y < shape.height; y++) for (int z = 0; z < shape.depth; z++) {
            var pos = root.offset(shape.x(x, z), y, shape.z(x, z));
            if (!pos.equals(root) && (!loaded(level, pos) || !NativeConstructorFrame.fits(level, pos, shape.frame(x, y, z))))
                return false;
        }
        return true;
    }

    private NativeBuilderFormation() {}
}

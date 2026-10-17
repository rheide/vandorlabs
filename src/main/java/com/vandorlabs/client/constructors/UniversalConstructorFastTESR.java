package com.vandorlabs.client.constructors;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.model.animation.FastTESR;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Optional Forge 1.12.2 renderer adapter; supply your own controller TileEntity.
 * Requires the accompanying UniversalConstructorModelCache.
 * This default draws complete.obj once, in its resting pose, from the controller.
 * Override drawMachine to draw static.obj + glass.obj + moving parts instead.
 */
@SideOnly(Side.CLIENT)
public abstract class UniversalConstructorFastTESR<T extends TileEntity> extends FastTESR<T> {
    protected abstract boolean isController(T te);
    protected abstract String machineId(T te);

    protected EnumFacing facing(T te) { return EnumFacing.NORTH; }

    /** Unrotated dimensions. Override if the design dimensions are changed. */
    protected double width(T te) {
        switch (machineId(te)) {
            case "assembly_press": return 4;
            case "synthesis_tower": return 3;
            case "enclosed_gantry": return 5;
            default: throw new IllegalArgumentException("Unknown constructor design " + machineId(te));
        }
    }

    protected double depth(T te) { return 3; }

    /**
     * Controller-relative location of the minimum X/Y/Z corner of the rotated
     * machine footprint. Override if the controller is elsewhere in the machine.
     * The default places that minimum corner at the controller block position.
     */
    protected BlockPos originOffset(T te) { return BlockPos.ORIGIN; }

    /**
     * Simple whole-machine light sample. Override for your lighting policy.
     * Cyan pixels do not automatically emit world light or ignore the lightmap.
     */
    protected int packedLight(T te) {
        return te.getWorld().getCombinedLight(te.getPos().up(), 0);
    }

    @Override
    public final void renderTileEntityFast(T te, double x, double y, double z,
                                          float partialTicks, int destroyStage,
                                          float partial, BufferBuilder buffer) {
        if (!isController(te) || te.getWorld() == null || !UniversalConstructorModelCache.isReady()) return;
        BlockPos origin = originOffset(te);
        // FastTESR shares a BufferBuilder with other tile renderers.
        // Use camera-relative positions directly and leave translation at zero.
        buffer.setTranslation(0, 0, 0);
        try {
            drawMachine(te, x + origin.getX(), y + origin.getY(), z + origin.getZ(), partialTicks, buffer);
        } finally {
            buffer.setTranslation(0, 0, 0);
        }
    }

    protected void drawMachine(T te, double x, double y, double z,
                               float partialTicks, BufferBuilder buffer) {
        drawPart(te, "complete.obj", buffer, x, y, z, 0, 0, 0);
    }

    /** Translations are in model-local block units before whole-machine facing. */
    protected final void drawPart(T te, String relativeObjPath, BufferBuilder buffer,
                                  double x, double y, double z,
                                  double offsetX, double offsetY, double offsetZ) {
        UniversalConstructorModelCache.get(machineId(te), relativeObjPath)
            .emit(buffer, x, y, z, facing(te), width(te), depth(te),
                  offsetX, offsetY, offsetZ, packedLight(te));
    }
}

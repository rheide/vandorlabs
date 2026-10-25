package com.vandorlabs.programmer.client;

import com.vandorlabs.programmer.*;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Submits to Forge's existing FastTESR batch; never starts or draws one. */
@SideOnly(Side.CLIENT)
public final class ProgrammerRenderHelper {
    private ProgrammerRenderHelper() { }

    public static ProgrammerTransform.Facing facing(EnumFacing facing) {
        switch (facing) {
            case NORTH: return ProgrammerTransform.Facing.NORTH;
            case EAST: return ProgrammerTransform.Facing.EAST;
            case SOUTH: return ProgrammerTransform.Facing.SOUTH;
            case WEST: return ProgrammerTransform.Facing.WEST;
            default: throw new IllegalArgumentException("Programmer requires horizontal facing");
        }
    }

    public static AxisAlignedBB bounds(ProgrammerHost host) {
        ProgrammerModelCache.Definition def = ProgrammerModelCache.INSTANCE.definition();
        ProgrammerTransform.Affine a = ProgrammerTransform.of(facing(host.getProgrammerFacing()), def.width, def.depth);
        BlockPos p = host.getProgrammerOrigin();
        return new AxisAlignedBB(p.getX(), p.getY(), p.getZ(),
                p.getX()+a.worldWidth, p.getY()+def.height, p.getZ()+a.worldDepth).grow(.02);
    }

    public static void append(BufferBuilder buffer, ProgrammerModelCache.Definition def,
                              ProgrammerStatus status, EnumFacing facing, int exampleCardMask,
                              double originX, double originY, double originZ,
                              int packedLight, float alpha) {
        ProgrammerTransform.Affine a = ProgrammerTransform.of(facing(facing), def.width, def.depth);
        buffer.setTranslation(0, 0, 0);
        // BufferBuilder copies the data, so one scratch array handles the whole assembly.
        int stride=ProgrammerModelCache.INSTANCE.format.getIntegerSize();
        int color=ProgrammerModelCache.INSTANCE.format.getColorOffset()/4, light=ProgrammerModelCache.INSTANCE.format.getUvOffsetById(1)/4;
        int[] data = new int[stride*4];
        for (ProgrammerModelCache.Part part : def.parts) {
            if (part.exampleCardBit != 0 && (exampleCardMask & part.exampleCardBit) == 0) continue;
            boolean fullbright = def.screenFullbrightWhenActive && "screen".equals(part.name) && status.screenLit;
            int partLight = fullbright ? 0x00F000F0 : packedLight;
            for (int[] original : ProgrammerModelCache.INSTANCE.mesh(status, part.name).rawQuads()) {
                System.arraycopy(original, 0, data, 0, data.length);
                for (int vertex = 0; vertex < 4; ++vertex) {
                    int i = vertex * stride;
                    double vx = Float.intBitsToFloat(original[i]);
                    double vy = Float.intBitsToFloat(original[i + 1]);
                    double vz = Float.intBitsToFloat(original[i + 2]);
                    double px = a.xx * vx + a.xz * vz + originX + a.offsetX;
                    double py = vy + originY;
                    double pz = a.zx * vx + a.zz * vz + originZ + a.offsetZ;
                    data[i] = Float.floatToRawIntBits((float) px);
                    data[i+1] = Float.floatToRawIntBits((float) py);
                    data[i+2] = Float.floatToRawIntBits((float) pz);
                    int opacity = (int) Math.round((original[i+color] >>> 24) * ProgrammerTransform.clamp01(alpha));
                    int rgb = fullbright ? 0x00FFFFFF : (original[i+color] & 0x00FFFFFF);
                    data[i+color] = rgb | (opacity << 24);
                    data[i+light] = partLight;
                }
                buffer.addVertexData(data);
            }
        }
        buffer.setTranslation(0, 0, 0);
    }
}

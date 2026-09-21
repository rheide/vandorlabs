package com.vandorlabs.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

/** Small full-bright colored mote emitted along a propulsion block's facing. */
public class ParticleThrusterStream extends Particle {
    private final int style;
    private float phase;

    public ParticleThrusterStream(World world, BlockPos pos, EnumFacing facing, int color,
            int style, float speed) {
        this(world, pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D,
                facing, color, style, speed, 1.0F);
    }

    /** Creates a plume around an exact assembly center rather than one block center. */
    public ParticleThrusterStream(World world, double x, double y, double z,
            EnumFacing facing, int color, int style, float speed, float spreadScale) {
        super(world, x, y, z);
        this.style = style;
        this.phase = rand.nextFloat() * 6.2831855F;
        Vec3i direction = facing.getDirectionVec();
        double baseSpread = style == 1 ? .18D : style == 3 ? .56D
                : style == 2 ? .42D : .32D;
        double spread = baseSpread * Math.max(.25D, spreadScale);
        double spreadX = direction.getX() == 0 ? (rand.nextDouble() - .5D) * spread : 0;
        double spreadY = direction.getY() == 0 ? (rand.nextDouble() - .5D) * spread : 0;
        double spreadZ = direction.getZ() == 0 ? (rand.nextDouble() - .5D) * spread : 0;
        setPosition(posX + direction.getX() * .54D + spreadX,
                posY + direction.getY() * .54D + spreadY,
                posZ + direction.getZ() * .54D + spreadZ);
        double actualSpeed = speed * (.78D + rand.nextDouble() * .44D);
        double jitter = style == 2 ? .025D : style == 3 ? .018D : .009D;
        motionX = direction.getX() * actualSpeed + (rand.nextDouble() - .5D) * jitter;
        motionY = direction.getY() * actualSpeed + (rand.nextDouble() - .5D) * jitter;
        motionZ = direction.getZ() * actualSpeed + (rand.nextDouble() - .5D) * jitter;
        particleRed = ((color >> 16) & 255) / 255.0F;
        particleGreen = ((color >> 8) & 255) / 255.0F;
        particleBlue = (color & 255) / 255.0F;
        particleScale = (style == 2 ? .34F : style == 3 ? .25F : style == 0 ? .20F : .16F)
                + rand.nextFloat() * .12F;
        particleMaxAge = style == 0 ? 8 + rand.nextInt(6)
                : style == 1 ? 16 + rand.nextInt(8)
                : style == 2 ? 18 + rand.nextInt(10)
                : style == 3 ? 22 + rand.nextInt(10) : 12 + rand.nextInt(8);
        particleGravity = 0;
        setParticleTextureIndex(style == 0 ? 48 : style == 1 ? 65
                : style == 2 ? 128 : style == 3 ? 7 : 65);
    }

    @Override public int getBrightnessForRender(float partialTick) { return 0x00F000F0; }

    @Override public void onUpdate() {
        super.onUpdate();
        double drag = style == 0 ? .985D : style == 1 ? .995D
                : style == 2 ? .965D : style == 3 ? .945D : .97D;
        motionX *= drag;
        motionY *= drag;
        motionZ *= drag;
        if (style == 2) {
            phase += .45F;
            motionX += Math.sin(phase) * .0025D;
            motionY += Math.cos(phase * .8F) * .0025D;
        }
        particleScale *= style == 1 ? .99F : .97F;
    }
}

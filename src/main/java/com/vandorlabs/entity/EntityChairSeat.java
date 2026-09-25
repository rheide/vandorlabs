package com.vandorlabs.entity;

import com.vandorlabs.blocks.BlockBridgeChair;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Invisible, non-colliding mount used to give bridge chairs vanilla riding
 * posture without turning the decorative chair itself into an entity. */
public class EntityChairSeat extends Entity {

    /** In the riding pose the rendered pelvis is about 0.725 blocks above
     * EntityPlayer's origin. The manifest marker describes the cushion where
     * the pelvis belongs, not where the player's feet belong. */
    public static final double RIDER_PELVIS_OFFSET = 0.725D;

    private BlockPos chairPos;

    public EntityChairSeat(World world) {
        super(world);
        setSize(0.01F, 0.01F);
        noClip = true;
        setInvisible(true);
    }

    public EntityChairSeat(World world, BlockPos chairPos, double seatY) {
        this(world);
        this.chairPos = chairPos;
        // EntityPlayer contributes a -0.35 riding Y offset in 1.12.2; cancel
        // that first, then lower the origin so the visible pelvis—not the
        // feet—lands on the authored cushion marker.
        setPosition(chairPos.getX() + 0.5D,
                chairPos.getY() + seatY + 0.35D - RIDER_PELVIS_OFFSET,
                chairPos.getZ() + 0.5D);
    }

    public BlockPos getChairPos() { return chairPos; }

    public void setSeatY(double seatY) {
        if (chairPos == null) return;
        setPosition(chairPos.getX() + 0.5D,
                chairPos.getY() + seatY + 0.35D - RIDER_PELVIS_OFFSET,
                chairPos.getZ() + 0.5D);
        for (Entity passenger : getPassengers()) updatePassenger(passenger);
    }

    @Override protected void entityInit() { }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!world.isRemote) {
            IBlockState state = chairPos == null ? null
                    : world.getBlockState(chairPos);
            if (state == null || !(state.getBlock() instanceof BlockBridgeChair)
                    || state.getValue(BlockBridgeChair.UPPER)
                    || (ticksExisted > 5 && getPassengers().isEmpty())) {
                setDead();
            }
        }
    }

    @Override public double getMountedYOffset() { return 0.0D; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean canBePushed() { return false; }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        chairPos = BlockPos.fromLong(compound.getLong("ChairPos"));
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        if (chairPos != null) compound.setLong("ChairPos", chairPos.toLong());
    }
}

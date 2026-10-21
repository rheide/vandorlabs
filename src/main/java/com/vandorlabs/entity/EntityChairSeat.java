package com.vandorlabs.entity;

import com.vandorlabs.blocks.BlockBridgeChair;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Invisible, non-colliding mount used to give bridge chairs vanilla riding
 * posture without turning the decorative chair itself into an entity. */
public class EntityChairSeat extends Entity implements net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData {

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
        cockpitOffset();
    }

    public BlockPos getChairPos() { return chairPos; }

    public void setSeatY(double seatY) {
        if (chairPos == null) return;
        setPosition(chairPos.getX() + 0.5D,
                chairPos.getY() + seatY + 0.35D - RIDER_PELVIS_OFFSET,
                chairPos.getZ() + 0.5D);
        cockpitOffset();
        for (Entity passenger : getPassengers()) updatePassenger(passenger);
    }

    @Override protected void entityInit() { }
    @Override public void writeSpawnData(io.netty.buffer.ByteBuf out){out.writeBoolean(chairPos!=null);if(chairPos!=null)out.writeLong(chairPos.toLong());}
    @Override public void readSpawnData(io.netty.buffer.ByteBuf in){chairPos=in.readBoolean()?BlockPos.fromLong(in.readLong()):null;}
    public com.vandorlabs.tiles.TileEntityCockpit cockpit(){
        if(chairPos==null||world==null)return null;
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(chairPos);
        return tile instanceof com.vandorlabs.tiles.TileEntityCockpit?(com.vandorlabs.tiles.TileEntityCockpit)tile:null;
    }
    /** The rider, camera and seat renderer share the tile's persisted timeline. */
    public net.minecraft.util.math.Vec3d cockpitRiderPosition(Entity passenger,float partial){
        com.vandorlabs.tiles.TileEntityCockpit tile=cockpit();if(tile==null||tile.data().seat==null)return null;
        net.minecraft.util.math.Vec3d at=com.vandorlabs.cockpit.CockpitData.rotate(tile.seatLocation(partial),tile.facing());
        return new net.minecraft.util.math.Vec3d(chairPos.getX()+at.x,chairPos.getY()+tile.seatHeight()+.35-RIDER_PELVIS_OFFSET+passenger.getYOffset(),chairPos.getZ()+at.z);
    }
    @Override public void updatePassenger(Entity passenger){
        net.minecraft.util.math.Vec3d at=cockpitRiderPosition(passenger,0);
        if(!isPassenger(passenger)||at==null){super.updatePassenger(passenger);return;}
        passenger.setPosition(at.x,at.y,at.z);
        if(world.isRemote){net.minecraft.util.math.Vec3d before=cockpitRiderPosition(passenger,-1);passenger.prevPosX=passenger.lastTickPosX=before.x;passenger.prevPosY=passenger.lastTickPosY=before.y;passenger.prevPosZ=passenger.lastTickPosZ=before.z;}
    }
    @Override public void setPositionAndRotationDirect(double x,double y,double z,float yaw,float pitch,int increments,boolean teleport){
        if(cockpit()==null)super.setPositionAndRotationDirect(x,y,z,yaw,pitch,increments,teleport);
    }
    private void cockpitOffset(){
        if(chairPos==null || !(world.getTileEntity(chairPos) instanceof com.vandorlabs.tiles.TileEntityCockpit))return;
        com.vandorlabs.tiles.TileEntityCockpit cockpit=(com.vandorlabs.tiles.TileEntityCockpit)world.getTileEntity(chairPos);if(cockpit.data().seat==null)return;
        net.minecraft.util.math.Vec3d at=com.vandorlabs.cockpit.CockpitData.rotate(cockpit.seatLocation(),cockpit.facing());setPosition(chairPos.getX()+at.x,posY,chairPos.getZ()+at.z);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        com.vandorlabs.tiles.TileEntityCockpit cockpit=cockpit();if(cockpit!=null){setSeatY(cockpit.seatHeight());}
        if (!world.isRemote) {
            IBlockState state = chairPos == null ? null
                    : world.getBlockState(chairPos);
            boolean pilot = state != null && state.getBlock() instanceof com.vandorlabs.blocks.BlockPilotSeat;
            boolean tallChair = state != null && (state.getBlock() instanceof BlockBridgeChair
                    || state.getBlock() instanceof com.vandorlabs.blocks.BlockConnectedSeat);
            if (!(pilot || tallChair) || (tallChair && state.getValue(BlockBridgeChair.UPPER))
                    || (ticksExisted > 5 && getPassengers().isEmpty())) {
                setDead();
            }
        }
    }

    @Override public double getMountedYOffset() { return 0.0D; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean canBePushed() { return false; }

    @Override protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if(!world.isRemote && chairPos!=null && !com.vandorlabs.vehicle.VehicleTransfer.isMutating(world)) {
            net.minecraft.util.math.AxisAlignedBB shape=world.getBlockState(chairPos).getBoundingBox(world,chairPos);
            net.minecraft.util.math.AxisAlignedBB bounds=shape==null?new net.minecraft.util.math.AxisAlignedBB(chairPos):shape.offset(chairPos);
            SafeDismount.move(passenger,SafeDismount.find(world,passenger,bounds),bounds);
        }
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        chairPos = BlockPos.fromLong(compound.getLong("ChairPos"));
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        if (chairPos != null) compound.setLong("ChairPos", chairPos.toLong());
    }
}

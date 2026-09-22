package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockVandorSwitch extends BlockVandor {

    public static final PropertyEnum<EnumFacing> FACING = PropertyEnum.create("facing", EnumFacing.class, java.util.Arrays.asList(EnumFacing.values()));
    public static final PropertyBool ON = PropertyBool.create("on");

    /** Rocker switches latch open/closed on click; buttons (push buttons)
     * turn ON for a short moment, then release like a vanilla button. */
    protected final boolean momentary;
    public boolean isMomentary() { return momentary; }

    public void applyLinkedState(World world,BlockPos pos,IBlockState state,boolean on) {
        if (momentary || state.getValue(ON)==on) return;
        world.setBlockState(pos,state.withProperty(ON,on),3);
        world.checkLight(pos);
        world.notifyNeighborsOfStateChange(pos.offset(state.getValue(FACING).getOpposite()),this,false);
    }

    // 6px plates (50% smaller), hugging the support side: horizontal boxes sit
    // against the clicked block; UP (floor mount) sits on the floor, DOWN
    // (ceiling mount) hangs from the ceiling -- matching vanilla lever/buttons.
    private static final AxisAlignedBB DOWN_AABB = new AxisAlignedBB(0.3125D, 0.9375D, 0.3125D, 0.6875D, 1.0D, 0.6875D);
    private static final AxisAlignedBB UP_AABB = new AxisAlignedBB(0.3125D, 0.0D, 0.3125D, 0.6875D, 0.0625D, 0.6875D);
    private static final AxisAlignedBB NORTH_AABB = new AxisAlignedBB(0.3125D, 0.3125D, 0.9375D, 0.6875D, 0.6875D, 1.0D);
    private static final AxisAlignedBB SOUTH_AABB = new AxisAlignedBB(0.3125D, 0.3125D, 0.0D, 0.6875D, 0.6875D, 0.0625D);
    private static final AxisAlignedBB WEST_AABB = new AxisAlignedBB(0.9375D, 0.3125D, 0.3125D, 1.0D, 0.6875D, 0.6875D);
    private static final AxisAlignedBB EAST_AABB = new AxisAlignedBB(0.0D, 0.3125D, 0.3125D, 0.0625D, 0.6875D, 0.6875D);

    public BlockVandorSwitch(String name) {
        this(name, false);
    }

    public BlockVandorSwitch(String name, boolean momentary) {
        super(name);
        this.momentary = momentary;
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(ON, false));
        setLightOpacity(0);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, ON);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        switch (state.getValue(FACING)) {
            case UP: return UP_AABB;
            case DOWN: return DOWN_AABB;
            case SOUTH: return SOUTH_AABB;
            case WEST: return WEST_AABB;
            case EAST: return EAST_AABB;
            case NORTH:
            default: return NORTH_AABB;
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getStateForPlacement(worldIn, pos, facing, hitX, hitY, hitZ, meta, placer, EnumHand.MAIN_HAND);
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, facing).withProperty(ON, false);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getFront(meta & 7)).withProperty(ON, (meta & 8) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex() | (state.getValue(ON) ? 8 : 0);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityRedstoneChannel();
    }

    private TileEntityRedstoneChannel channelTile(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileEntityRedstoneChannel ? (TileEntityRedstoneChannel) tile : null;
    }

    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult target,
            World world,BlockPos pos,EntityPlayer player) {
        ItemStack stack=new ItemStack(this);
        TileEntityRedstoneChannel tile=channelTile(world,pos);
        if (tile!=null && tile.getRedstoneChannel()!=0) {
            NBTTagCompound settings=new NBTTagCompound();
            settings.setInteger("Channel",tile.getRedstoneChannel());
            stack.setTagInfo("RedstoneChannelSettings",settings);
        }
        return stack;
    }

    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,
            EntityLivingBase placer,ItemStack stack) {
        super.onBlockPlacedBy(world,pos,state,placer,stack);
        NBTTagCompound settings=stack.getSubCompound("RedstoneChannelSettings");
        TileEntityRedstoneChannel tile=channelTile(world,pos);
        if (!world.isRemote && settings!=null && tile!=null)
            tile.setRedstoneChannel(Math.max(0,settings.getInteger("Channel")));
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        // Like a vanilla lever: needs a solid face to mount on.
        return world.isSideSolid(pos.offset(side.getOpposite()), side);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        // Support gone: pop off and drop, like a vanilla lever. Removal routes
        // through breakBlock, which re-notifies past the old support.
        EnumFacing facing = state.getValue(FACING);
        if (!world.isSideSolid(pos.offset(facing.getOpposite()), facing)) {
            this.dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        // A removed ON switch stops powering: the support block must
        // re-broadcast so anything past it (lamp above the wall) re-checks
        // power instead of staying lit on a stale signal.
        if (!world.isRemote && state.getValue(ON)) {
            world.notifyNeighborsOfStateChange(pos.offset(state.getValue(FACING).getOpposite()), this, false);
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public int tickRate(World world) {
        return momentary ? 20 : 0;
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, java.util.Random rand) {
        if (world.isRemote || !momentary || !state.getValue(ON)) {
            return;
        }
        // Button released: drop ON and re-ring the support so anything past
        // the wall picks up the edge, same as the press path below.
        TileEntityRedstoneChannel tile = channelTile(world, pos);
        if (tile != null) tile.setLocalOn(false);
        world.setBlockState(pos, state.withProperty(ON, false), 3);
        world.checkLight(pos);
        world.notifyNeighborsOfStateChange(pos.offset(state.getValue(FACING).getOpposite()), this, false);
        world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.6F, 0.5F);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            if (!world.isRemote && channelTile(world, pos) != null)
                player.openGui(VandorLabs.instance, GuiHandler.GUI_REDSTONE_CHANNEL,
                        world, pos.getX(), pos.getY(), pos.getZ());
            return true;
        }
        if (!world.isRemote) {
            TileEntityRedstoneChannel tile = channelTile(world, pos);
            if (momentary) {
                boolean before = state.getValue(ON);
                if (!before) {
                    world.setBlockState(pos, state.withProperty(ON, true), 3);
                    if (tile != null) tile.setLocalOn(true);
                    world.checkLight(pos);
                    world.notifyNeighborsOfStateChange(pos.offset(state.getValue(FACING).getOpposite()), this, false);
                }
                // (Re)arm the release timer so mashing never sticks it on.
                world.scheduleUpdate(pos, this, this.tickRate(world));
                world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.6F, 0.7F);
                return true;
            }
            boolean on = !state.getValue(ON);
            world.setBlockState(pos, state.withProperty(ON, on), 3);
            if (tile != null) tile.setLocalOn(on);
            world.checkLight(pos);
            // Re-notify the support block too: setBlockState only reaches the
            // switch's direct neighbors, so without this a lamp sitting on the
            // far side of the support (e.g. above the wall) never re-checks
            // power and stays dark.
            world.notifyNeighborsOfStateChange(pos.offset(state.getValue(FACING).getOpposite()), this, false);
            world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.6F, on ? 0.7F : 0.5F);
        }
        return true;
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return state.getValue(ON) ? 15 : 0;
    }

    @Override
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        // Strong on every side while ON (like a vanilla lever): the support
        // block is guaranteed strongly powered, so it conducts up/through to
        // lamps and dust no matter which face the switch sits on.
        return state.getValue(ON) ? 15 : 0;
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(ON) ? 5 : 0;
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirrorIn) {
        return state.withRotation(mirrorIn.toRotation(state.getValue(FACING)));
    }
}

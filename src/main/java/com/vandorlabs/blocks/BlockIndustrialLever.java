package com.vandorlabs.blocks;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.GuiHandler;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.init.SoundEvents;
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

/** Heavy armored industrial lever (custom 3D model, hand-maintained assets).
 * Four horizontal wall mountings, two states. Adapted from the supplied
 * example: Vandor Labs creative tab, and strong redstone power on all sides while
 * on so walls conduct through to lamps like the rest of the mod's switches.
 */
public class BlockIndustrialLever extends BlockHorizontal {
    public static final PropertyBool POWERED = PropertyBool.create("powered");
    public static final PropertyBool FLOOR = PropertyBool.create("floor");

    public BlockIndustrialLever() {
        this("industrial_lever");
    }

    protected BlockIndustrialLever(String name) {
        super(Material.CIRCUITS);
        setRegistryName(name);
        setUnlocalizedName(VandorLabs.MODID + "." + name);
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setHardness(0.5F);
        setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH)
                .withProperty(POWERED, false).withProperty(FLOOR,false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, POWERED, FLOOR);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(POWERED, (meta & 4) != 0)
                .withProperty(FLOOR,(meta & 8)!=0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex()
                |(state.getValue(POWERED)?4:0)|(state.getValue(FLOOR)?8:0);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityRedstoneChannel();
    }

    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult target,
            World world,BlockPos pos,EntityPlayer player) {
        ItemStack stack=new ItemStack(this);
        TileEntity raw=world.getTileEntity(pos);
        if (raw instanceof TileEntityRedstoneChannel
                && ((TileEntityRedstoneChannel)raw).getRedstoneChannel()!=0) {
            NBTTagCompound settings=new NBTTagCompound();
            settings.setInteger("Channel",((TileEntityRedstoneChannel)raw).getRedstoneChannel());
            stack.setTagInfo("RedstoneChannelSettings",settings);
        }
        return stack;
    }

    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,
            EntityLivingBase placer,ItemStack stack) {
        super.onBlockPlacedBy(world,pos,state,placer,stack);
        NBTTagCompound settings=stack.getSubCompound("RedstoneChannelSettings");
        TileEntity raw=world.getTileEntity(pos);
        if (!world.isRemote && settings!=null && raw instanceof TileEntityRedstoneChannel)
            ((TileEntityRedstoneChannel)raw).setRedstoneChannel(Math.max(0,settings.getInteger("Channel")));
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return withRotation(state, mirror.toRotation(state.getValue(FACING)));
    }

    private boolean hasSupport(World world, BlockPos pos, EnumFacing outward) {
        BlockPos support = pos.offset(outward.getOpposite());
        return world.getBlockState(support).isSideSolid(world, support, outward);
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return (side==EnumFacing.UP||side.getAxis().isHorizontal())&&hasSupport(world,pos,side);
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        if (hasSupport(world,pos,EnumFacing.UP)) return true;
        for (EnumFacing face : EnumFacing.Plane.HORIZONTAL) {
            if (hasSupport(world, pos, face)) return true;
        }
        return false;
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        boolean floor=facing==EnumFacing.UP;
        EnumFacing outward=floor?placer.getHorizontalFacing().getOpposite()
                :facing.getAxis().isHorizontal()?facing:placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING,outward).withProperty(POWERED,false)
                .withProperty(FLOOR,floor);
    }

    private void notifyPower(World world, BlockPos pos, IBlockState state) {
        world.notifyNeighborsOfStateChange(pos, this, false);
        world.notifyNeighborsOfStateChange(pos.offset(supportDirection(state)), this, false);
    }

    public void applyLinkedState(World world,BlockPos pos,IBlockState state,boolean on) {
        if (state.getValue(POWERED)==on) return;
        IBlockState next=state.withProperty(POWERED,on);
        world.setBlockState(pos,next,3);
        world.checkLight(pos);
        notifyPower(world,pos,next);
    }

    private EnumFacing supportDirection(IBlockState state) {
        return state.getValue(FLOOR)?EnumFacing.DOWN:state.getValue(FACING).getOpposite();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
            EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof TileEntityRedstoneChannel)
                player.openGui(VandorLabs.instance, GuiHandler.GUI_REDSTONE_CHANNEL,
                        world, pos.getX(), pos.getY(), pos.getZ());
            return true;
        }
        if (!world.isRemote) {
            IBlockState next = state.cycleProperty(POWERED);
            world.setBlockState(pos, next, 3);
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityRedstoneChannel)
                ((TileEntityRedstoneChannel) tile).setLocalOn(next.getValue(POWERED));
            world.checkLight(pos);
            world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS,
                    0.3F, next.getValue(POWERED) ? 0.6F : 0.5F);
            notifyPower(world, pos, next);
        }
        return true;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        EnumFacing outward=state.getValue(FLOOR)?EnumFacing.UP:state.getValue(FACING);
        if (!world.isRemote && !hasSupport(world,pos,outward)) {
            dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (state.getValue(POWERED)) notifyPower(world, pos, state);
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean canProvidePower(IBlockState state) { return true; }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        // Mod convention (matches BlockVandorSwitch): strong on every side
        // while on, so the support block conducts through to lamps and dust.
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing side) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        // Conservative union of both handle positions, including rear wall feet.
        double a = 1.0 / 16.0, b = 15.0 / 16.0, front = 7.0 / 16.0;
        if (state.getValue(FLOOR)) return new AxisAlignedBB(a,0,a,b,9.0/16,b);
        switch (state.getValue(FACING)) {
            case SOUTH: return new AxisAlignedBB(a, 2.0/16, 0, b, 14.0/16, 1-front);
            case EAST:  return new AxisAlignedBB(0, 2.0/16, a, 1-front, 14.0/16, b);
            case WEST:  return new AxisAlignedBB(front, 2.0/16, a, 1, 14.0/16, b);
            default:    return new AxisAlignedBB(a, 2.0/16, front, b, 14.0/16, 1);
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB; // Like a vanilla lever: selectable, no movement collision.
    }
}

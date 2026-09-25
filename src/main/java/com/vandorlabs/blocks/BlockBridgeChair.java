package com.vandorlabs.blocks;

import com.vandorlabs.entity.EntityChairSeat;
import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityProgrammableChair;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Two-cell decorative chair. The invisible upper cell reserves the space
 * occupied by the backrest so blocks cannot be placed through the model. */
public class BlockBridgeChair extends BlockVandorDirectional {

    public static final PropertyBool UPPER = PropertyBool.create("upper");
    public enum Style implements IStringSerializable {
        COMMAND("command", 24, 12), COMPANION("companion", 21, 11),
        OPERATOR("operator", 23, 12), CONFERENCE("conference", 23.5, 11),
        MESS_HALL("mess_hall", 18.5, 10);
        public final String id;
        public final double height, seatY;
        Style(String id, double height, double seatY) {
            this.id = id; this.height = height / 16.0; this.seatY = seatY / 16.0;
        }
        @Override public String getName() { return id; }
        public static Style byIndex(int index) {
            return values()[Math.max(0, Math.min(values().length - 1, index))];
        }
    }
    public static final PropertyEnum<Style> STYLE = PropertyEnum.create("style", Style.class);

    public BlockBridgeChair() {
        super("programmable_chair");
        setDefaultState(this.blockState.getBaseState()
                .withProperty(FACING, EnumFacing.SOUTH)
                .withProperty(UPPER, false).withProperty(STYLE, Style.COMMAND));
        setLightOpacity(0);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, UPPER, STYLE);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityProgrammableChair();
    }

    private TileEntityProgrammableChair settings(IBlockAccess world, BlockPos pos,
            IBlockState state) {
        TileEntity raw = world.getTileEntity(state.getValue(UPPER) ? pos.down() : pos);
        return raw instanceof TileEntityProgrammableChair
                ? (TileEntityProgrammableChair) raw : null;
    }

    @Override public IBlockState getActualState(IBlockState state, IBlockAccess world,
            BlockPos pos) {
        TileEntityProgrammableChair tile = settings(world, pos, state);
        return state.withProperty(STYLE, Style.byIndex(tile == null ? 0 : tile.getStyle()));
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos,
            EnumFacing side, float hitX, float hitY, float hitZ, int meta,
            EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING,
                placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos)
                && world.getBlockState(pos.up()).getBlock()
                        .isReplaceable(world, pos.up());
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
            EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote) {
            world.setBlockState(pos.up(), state.withProperty(UPPER, true), 3);
            TileEntityProgrammableChair tile = settings(world, pos, state);
            NBTTagCompound tag = stack.getSubCompound("BlockEntityTag");
            if (tile != null && tag != null) tile.setStyle(tag.getInteger("ChairStyle"));
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        BlockPos lower = state.getValue(UPPER) ? pos.down() : pos;
        if (!world.isRemote) {
            for (EntityChairSeat seat : world.getEntitiesWithinAABB(
                    EntityChairSeat.class,
                    new AxisAlignedBB(lower).grow(0.25D, 1.0D, 0.25D))) {
                seat.removePassengers();
                seat.setDead();
            }
        }
        BlockPos other = state.getValue(UPPER) ? pos.down() : pos.up();
        IBlockState otherState = world.getBlockState(other);
        if (otherState.getBlock() == this) {
            world.setBlockToAir(other);
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos,
            IBlockState state, EntityPlayer player, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return true;
        BlockPos lower = state.getValue(UPPER) ? pos.down() : pos;
        if (player.isSneaking()) {
            if (player.capabilities.isCreativeMode && !world.isRemote)
                player.openGui(VandorLabs.instance, GuiHandler.GUI_PROGRAMMABLE_CHAIR,
                        world, lower.getX(), lower.getY(), lower.getZ());
            return player.capabilities.isCreativeMode;
        }
        if (world.isRemote) return true;
        AxisAlignedBB search = new AxisAlignedBB(lower).grow(0.25D, 1.0D, 0.25D);
        for (EntityChairSeat seat : world.getEntitiesWithinAABB(
                EntityChairSeat.class, search)) {
            if (!seat.isDead && seat.getPassengers().isEmpty()) {
                player.startRiding(seat, true);
                return true;
            }
            if (!seat.isDead) return true; // occupied
        }
        TileEntityProgrammableChair tile = settings(world, pos, state);
        EntityChairSeat seat = new EntityChairSeat(world, lower,
                Style.byIndex(tile == null ? 0 : tile.getStyle()).seatY);
        IBlockState lowerState = world.getBlockState(lower);
        seat.rotationYaw = lowerState.getValue(FACING).getHorizontalAngle();
        if (world.spawnEntity(seat)) player.startRiding(seat, true);
        return true;
    }

    @Override
    public int damageDropped(IBlockState state) { return 0; }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
                .withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(UPPER, (meta & 4) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex()
                | (state.getValue(UPPER) ? 4 : 0);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world,
            BlockPos pos) {
        TileEntityProgrammableChair tile = settings(world, pos, state);
        double height = Style.byIndex(tile == null ? 0 : tile.getStyle()).height;
        return state.getValue(UPPER)
                ? new AxisAlignedBB(0, 0, 0, 1, Math.max(0.05D, height - 1.0D), 1)
                : FULL_BLOCK_AABB;
    }

    @Override public ItemStack getPickBlock(IBlockState state, RayTraceResult target,
            World world, BlockPos pos, EntityPlayer player) {
        ItemStack stack = new ItemStack(Item.getItemFromBlock(this));
        TileEntityProgrammableChair tile = settings(world, pos, state);
        if (tile != null) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("ChairStyle", tile.getStyle());
            stack.setTagInfo("BlockEntityTag", tag);
        }
        return stack;
    }

    @Override public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world,
            BlockPos pos, IBlockState state, int fortune) {
        ItemStack stack = new ItemStack(Item.getItemFromBlock(this));
        TileEntityProgrammableChair tile = settings(world, pos, state);
        if (tile != null) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("ChairStyle", tile.getStyle());
            stack.setTagInfo("BlockEntityTag", tag);
        }
        drops.add(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.CUTOUT; }
}

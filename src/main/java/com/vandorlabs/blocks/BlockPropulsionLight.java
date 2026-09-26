package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Rotation;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Six-direction engine/hover fixture with a channel-aware illuminated state. */
public class BlockPropulsionLight extends BlockVandor {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final PropertyBool POWERED = PropertyBool.create("powered");
    /** Derived client render state; persisted by the tile entity, not metadata. */
    public static final PropertyBool PARTICLES = PropertyBool.create("particles");
    public static final IUnlistedProperty<Integer> SIDE_TEXTURE = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "side_texture"; }
        @Override public boolean isValid(Integer value) { return value != null; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };

    private final float depth;

    public BlockPropulsionLight(String name, boolean pointsUp, float depth) {
        super(name);
        this.depth = Math.max(0.0F, Math.min(1.0F, depth));
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, pointsUp ? EnumFacing.UP : EnumFacing.NORTH)
                .withProperty(POWERED, true)
                .withProperty(PARTICLES, false));
        setLightLevel(1.0F);
    }

    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this,
                new IProperty<?>[]{FACING, POWERED, PARTICLES},
                new IUnlistedProperty<?>[]{SIDE_TEXTURE});
    }

    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess source,
            BlockPos pos) {
        TileEntity tile = source.getTileEntity(pos);
        int choice = tile instanceof TileEntityRedstoneLight
                ? ((TileEntityRedstoneLight) tile).getSideTexture()
                : com.vandorlabs.tiles.ScreenHousingTextures.INDUSTRIAL_BLOCK;
        return ((IExtendedBlockState) state).withProperty(SIDE_TEXTURE, choice);
    }

    @Override public IBlockState getActualState(IBlockState state, IBlockAccess source,
            BlockPos pos) {
        return withParticleState(state, source, pos);
    }

    protected IBlockState withParticleState(IBlockState state, IBlockAccess source,
            BlockPos pos) {
        TileEntity tile = source.getTileEntity(pos);
        boolean particles = state.getValue(POWERED)
                && tile instanceof TileEntityRedstoneLight
                && ((TileEntityRedstoneLight) tile).isParticleStreamSelected();
        return state.withProperty(PARTICLES, particles);
    }

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos,
            EnumFacing facing, float hitX, float hitY, float hitZ, int meta,
            EntityLivingBase placer, EnumHand hand) {
        if (this instanceof BlockConnectedPropulsionLight && placer instanceof EntityPlayer) {
            ItemStack held = ((EntityPlayer) placer).getHeldItem(hand);
            NBTTagCompound tag = held.getSubCompound("BlockEntityTag");
            int shape = tag == null ? 0 : tag.getInteger("PropulsionShape");
            Block selected = shapeBlock(familyId(), shape, facing, hitX, hitY, hitZ);
            if (selected != null && selected != this)
                return selected.getDefaultState().withProperty(FACING, facing)
                        .withProperty(POWERED, true);
        }
        return getDefaultState().withProperty(FACING, facing).withProperty(POWERED, true);
    }

    public String familyId() {
        ResourceLocation name = getRegistryName();
        String id = name == null ? "" : name.getResourcePath();
        for (String family : new String[] {"rocket_thruster", "ion_drive",
                "plasma_vent", "impulse_engine"})
            if (id.equals(family) || id.startsWith(family + "_")) return family;
        return "";
    }

    public int shape() {
        ResourceLocation name = getRegistryName();
        String id = name == null ? "" : name.getResourcePath();
        if (id.contains("_wedge")) return 2;
        return id.endsWith("_hexagonal") ? 1 : 0;
    }

    public boolean hasJoinMode() {
        return this instanceof BlockConnectedPropulsionLight;
    }

    private static Block shapeBlock(String family, int shape, EnumFacing facing,
            float hitX, float hitY, float hitZ) {
        if (family.isEmpty() || shape < 0 || shape > 2) return null;
        String id = shape == 0 ? family : shape == 1 ? family + "_hexagonal"
                : BlockTrianglePropulsionLight.variantIdForHit(family + "_wedge",
                        facing, hitX, hitY, hitZ);
        Block block = Block.REGISTRY.getObject(new ResourceLocation(VandorLabs.MODID, id));
        return block instanceof BlockPropulsionLight ? block : null;
    }

    public static void configureShape(World world, BlockPos pos, int shape) {
        if (shape < 0 || shape > 2 || !world.isBlockLoaded(pos)) return;
        IBlockState before = world.getBlockState(pos);
        if (!(before.getBlock() instanceof BlockPropulsionLight)) return;
        BlockPropulsionLight current = (BlockPropulsionLight) before.getBlock();
        if (current.familyId().isEmpty() || current.shape() == shape) return;
        Block target = shapeBlock(current.familyId(), shape, before.getValue(FACING),
                0.25F, 0.25F, 0.25F);
        if (target == null) return;
        TileEntity tile = world.getTileEntity(pos);
        NBTTagCompound saved = tile instanceof TileEntityRedstoneLight
                ? tile.writeToNBT(new NBTTagCompound()) : null;
        IBlockState after = target.getDefaultState()
                .withProperty(FACING, before.getValue(FACING))
                .withProperty(POWERED, before.getValue(POWERED));
        if (!world.setBlockState(pos, after, 3)) return;
        TileEntity replacement = world.getTileEntity(pos);
        if (saved != null && replacement != tile && replacement instanceof TileEntityRedstoneLight) {
            replacement.readFromNBT(saved);
            replacement.markDirty();
        }
        if (current instanceof BlockConnectedPropulsionLight)
            ((BlockConnectedPropulsionLight) current).refreshConnectedModels(world, pos,
                    before.getValue(FACING));
        if (target instanceof BlockConnectedPropulsionLight)
            ((BlockConnectedPropulsionLight) target).refreshConnectedModels(world, pos,
                    before.getValue(FACING));
        world.checkLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, pos);
        world.notifyBlockUpdate(pos, after, after, 3);
    }

    @Override public Item getItemDropped(IBlockState state, java.util.Random random,
            int fortune) {
        Block base = shapeBlock(familyId(), 0, EnumFacing.NORTH, 0, 0, 0);
        return Item.getItemFromBlock(base == null ? this : base);
    }

    @Override public ItemStack getPickBlock(IBlockState state, RayTraceResult target,
            World world, BlockPos pos, EntityPlayer player) {
        return configuredStack();
    }

    @Override public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world,
            BlockPos pos, IBlockState state, int fortune) {
        if (familyId().isEmpty()) super.getDrops(drops, world, pos, state, fortune);
        else drops.add(configuredStack());
    }

    private ItemStack configuredStack() {
        Block base = shapeBlock(familyId(), 0, EnumFacing.NORTH, 0, 0, 0);
        ItemStack stack = new ItemStack(base == null ? this : base);
        if (shape() > 0) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("PropulsionShape", shape());
            stack.setTagInfo("BlockEntityTag", tag);
        }
        return stack;
    }

    @Override public IBlockState getStateFromMeta(int meta) {
        int facingIndex = meta & 7;
        EnumFacing facing = facingIndex < EnumFacing.values().length
                ? EnumFacing.getFront(facingIndex) : getDefaultState().getValue(FACING);
        return getDefaultState().withProperty(FACING, facing)
                .withProperty(POWERED, (meta & 8) != 0);
    }

    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex() | (state.getValue(POWERED) ? 8 : 0);
    }

    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityRedstoneLight();
    }

    @Override public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source,
            BlockPos pos) {
        if (depth >= 1.0F) return FULL_BLOCK_AABB;
        double d = depth;
        switch (state.getValue(FACING)) {
            case DOWN: return new AxisAlignedBB(0, 1 - d, 0, 1, 1, 1);
            case NORTH: return new AxisAlignedBB(0, 0, 1 - d, 1, 1, 1);
            case SOUTH: return new AxisAlignedBB(0, 0, 0, 1, 1, d);
            case WEST: return new AxisAlignedBB(1 - d, 0, 0, 1, 1, 1);
            case EAST: return new AxisAlignedBB(0, 0, 0, d, 1, 1);
            case UP:
            default: return new AxisAlignedBB(0, 0, 0, 1, d, 1);
        }
    }

    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
            EntityPlayer player, EnumHand hand, EnumFacing facing,
            float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            if (!world.isRemote && player.capabilities.isCreativeMode
                    && world.getTileEntity(pos) instanceof TileEntityRedstoneLight)
                player.openGui(VandorLabs.instance, GuiHandler.GUI_REDSTONE_CHANNEL,
                        world, pos.getX(), pos.getY(), pos.getZ());
            return true;
        }
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityRedstoneLight)
                ((TileEntityRedstoneLight) tile).toggleManualState();
        }
        return true;
    }

    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos,
            Block block, BlockPos fromPos) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityRedstoneLight)
                ((TileEntityRedstoneLight) tile).refreshLocalInput();
        }
    }
}

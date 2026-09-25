package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;

/**
 * A full-cube housing block whose front face is painted by
 * {@link com.vandorlabs.client.TEAnimatedScreenSelector} from the configured
 * animated screen. Placement follows the observation-glass convention: an
 * isolated display faces the player, while extending an existing display
 * inherits its direction. This lets a wall display be placed from the floor
 * or a neighboring block instead of requiring a support directly behind it.
 * Right-click opens the selector GUI (stored in
 * {@link TileEntityAnimatedScreenSelector}); redstone behavior follows the
 * tile configuration.
 */
public class BlockAnimatedScreenSelector extends BlockContainer {

    public static final String NAME = "programmable_viewscreen";
    public static final PropertyEnum<EnumFacing> FACING = PropertyEnum.create(
            "facing", EnumFacing.class, java.util.Arrays.asList(EnumFacing.values()));

    public BlockAnimatedScreenSelector() {
        this(NAME);
    }

    protected BlockAnimatedScreenSelector(String name) {
        super(Material.IRON);
        setRegistryName(name);
        setUnlocalizedName(VandorLabs.MODID + "." + name);
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setHardness(5.0F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 1);
        setDefaultState(this.blockState.getBaseState()
                .withProperty(facingProperty(), EnumFacing.NORTH));
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityAnimatedScreenSelector();
    }

    /**
     * BlockContainer defaults to INVISIBLE because most legacy container
     * blocks are drawn entirely by a tile renderer.  This block is hybrid:
     * the baked model owns the opaque housing and the TESR owns only the
     * changing screen quad.
     */
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, facingProperty());
    }

    /** Subclasses with stair-style placement can narrow this to horizontal. */
    protected IProperty<EnumFacing> facingProperty() {
        return FACING;
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(facingProperty(),
                placementFacing(worldIn, pos, facing, placer));
    }

    /** Shared player-facing placement policy for the programmable family. */
    protected EnumFacing placementFacing(World world, BlockPos pos,
            EnumFacing clickedFace, EntityLivingBase placer) {
        IBlockState clicked = world.getBlockState(
                pos.offset(clickedFace.getOpposite()));
        if (clicked.getBlock() == this) {
            return clicked.getValue(facingProperty());
        }
        return placer.getHorizontalFacing().getOpposite();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(facingProperty(), EnumFacing.getFront(meta & 7));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(facingProperty()).getIndex();
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(facingProperty(),
                rot.rotate(state.getValue(facingProperty())));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirrorIn) {
        return state.withRotation(mirrorIn.toRotation(state.getValue(facingProperty())));
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand == EnumHand.MAIN_HAND && player.isSneaking()
                && player.capabilities.isCreativeMode && !world.isRemote
                && world.getTileEntity(pos) instanceof TileEntityAnimatedScreenSelector) {
            player.openGui(VandorLabs.instance, GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR,
                    world, pos.getX(), pos.getY(), pos.getZ());
        }
        return hand == EnumHand.MAIN_HAND && player.isSneaking()
                && player.capabilities.isCreativeMode;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
            net.minecraft.block.Block blockIn, BlockPos fromPos) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityAnimatedScreenSelector)
                ((TileEntityAnimatedScreenSelector) tile).localInputChanged();
            // Light level follows the tile's redstone state; refresh it.
            world.checkLightFor(EnumSkyBlock.BLOCK, pos);
        }
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world != null && pos != null ? world.getTileEntity(pos) : null;
        if (!(te instanceof TileEntityAnimatedScreenSelector)) {
            return 11;
        }
        // Brighter than the stock screens (7): keeps the wall-textured
        // housing readable in dark rooms next to the fullbright face.
        return ((TileEntityAnimatedScreenSelector) te).getEffectiveMode()
                == TileEntityAnimatedScreenSelector.MODE_OFF ? 0 : 11;
    }

    /** Build the inventory form used by mining, explosions and pick-block. */
    public ItemStack createConfiguredDrop(@Nullable TileEntity tile) {
        ItemStack stack = new ItemStack(Item.getItemFromBlock(this));
        if (tile instanceof TileEntityAnimatedScreenSelector) {
            NBTTagCompound configuration = tile.writeToNBT(new NBTTagCompound());
            // ItemBlock merges BlockEntityTag into the newly-created tile and
            // supplies its own identity and coordinates at the destination.
            configuration.removeTag("id");
            configuration.removeTag("x");
            configuration.removeTag("y");
            configuration.removeTag("z");
            stack.setTagInfo("BlockEntityTag", configuration);
        }
        return stack;
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world,
            BlockPos pos, IBlockState state, int fortune) {
        drops.add(createConfiguredDrop(world.getTileEntity(pos)));
    }

    /**
     * PlayerController removes a block before calling harvestBlock, so its
     * tile is no longer reachable through World. Use the captured tile
     * argument here; otherwise survival mining silently loses the settings.
     */
    @Override
    public void harvestBlock(World world, EntityPlayer player, BlockPos pos,
            IBlockState state, @Nullable TileEntity tile, ItemStack tool) {
        player.addStat(StatList.getBlockStats(this));
        player.addExhaustion(0.005F);
        NonNullList<ItemStack> drops = NonNullList.create();
        drops.add(createConfiguredDrop(tile));
        int fortune = EnchantmentHelper.getEnchantmentLevel(
                Enchantments.FORTUNE, tool);
        boolean silkTouch = EnchantmentHelper.getEnchantmentLevel(
                Enchantments.SILK_TOUCH, tool) > 0;
        float chance = ForgeEventFactory.fireBlockHarvesting(drops, world, pos,
                state, fortune, 1.0F, silkTouch, player);
        for (ItemStack drop : drops) {
            if (world.rand.nextFloat() <= chance) {
                spawnAsEntity(world, pos, drop);
            }
        }
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target,
            World world, BlockPos pos, EntityPlayer player) {
        return createConfiguredDrop(world.getTileEntity(pos));
    }
}

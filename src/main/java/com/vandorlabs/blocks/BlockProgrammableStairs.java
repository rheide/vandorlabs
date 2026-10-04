package com.vandorlabs.blocks;

import com.vandorlabs.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.*;
import net.minecraft.block.state.*;
import net.minecraft.block.properties.IProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.property.*;
import net.minecraftforge.event.ForgeEventFactory;
import javax.annotation.Nullable;

/** Vanilla stair placement, joining and collision with programmable finishes. */
public final class BlockProgrammableStairs extends BlockStairs {
    public BlockProgrammableStairs() {
        super(net.minecraft.init.Blocks.IRON_BLOCK.getDefaultState());
        setRegistryName("programmable_stairs");
        setUnlocalizedName(VandorLabs.MODID+".programmable_stairs");
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setHardness(5);setResistance(10);setHarvestLevel("pickaxe",1);
    }
    @Override protected BlockStateContainer createBlockState() {
        return new HousingBlockState(this,new IProperty[]{FACING,HALF,SHAPE},
                new IUnlistedProperty[]{ProgrammableHousingState.FINISH,ProgrammableHousingState.SIDE_FINISH,ProgrammableHousingState.FACES,
                        ProgrammableHousingState.TILE_SIDES});
    }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world,IBlockState state) { return new TileEntityAnimatedScreenSelector(); }
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos) {
        TileEntity raw=world.getTileEntity(pos);
        TileEntityAnimatedScreenSelector tile=raw instanceof TileEntityAnimatedScreenSelector?(TileEntityAnimatedScreenSelector)raw:null;
        return HousingBlockState.sample((IExtendedBlockState)state,tile==null?0:tile.getHousingTexture(),
                tile==null?-1:tile.getSideTexture(),tile==null?com.vandorlabs.tiles.FaceTextures.DEFAULT:tile.getFaceTextures(),
                tile!=null&&tile.isSlabTileSides()?1:0,63,0);
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

    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,net.minecraft.entity.EntityLivingBase placer,ItemStack stack) {
        super.onBlockPlacedBy(world,pos,state,placer,stack);
        BlockAnimatedScreenSelector.predictItemSettings(world,pos,stack);
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

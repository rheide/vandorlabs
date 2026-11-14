package com.vandorlabs.blocks;

import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.rack.ModuleRackGeometry;
import com.vandorlabs.tiles.TileEntityModuleRack;
import com.vandorlabs.vehicle.VehicleTransfer;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Static chunk-rendered housing with four independently clickable module slots. */
public final class BlockModuleRack extends BlockVandor {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final IUnlistedProperty<Integer> OCCUPIED_MASK = ProgrammableHousingState.integer("rack_occupied");
    public static final IUnlistedProperty<Integer> MODULE_MASK = ProgrammableHousingState.integer("rack_modules");

    public BlockModuleRack() {
        super("module_rack");
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.SOUTH));
    }

    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[] { FACING },
                new IUnlistedProperty<?>[] { OCCUPIED_MASK, MODULE_MASK });
    }

    @Override public IBlockState getStateFromMeta(int meta) {
        int facing = meta & 7;
        return getDefaultState().withProperty(FACING, facing < 6 ? EnumFacing.getFront(facing) : EnumFacing.SOUTH);
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }

    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face,
            float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, face);
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileEntityModuleRack(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face) {
        return ModuleRackGeometry.hasClosedBoundary(state.getValue(FACING).getIndex(), face.getIndex());
    }
    @Override public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face) {
        return ModuleRackGeometry.hasClosedBoundary(state.getValue(FACING).getIndex(), face.getIndex());
    }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state,
            BlockPos pos, EnumFacing face) {
        return isSideSolid(state, world, pos, face) ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }

    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity raw = world.getTileEntity(pos);
        int occupied = 0, modules = 0;
        if (raw instanceof TileEntityModuleRack) {
            TileEntityModuleRack rack = (TileEntityModuleRack)raw;
            occupied = rack.getOccupiedMask();
            modules = world instanceof VehicleWorld ? occupied : rack.getRenderMask();
        }
        return ((IExtendedBlockState)state).withProperty(OCCUPIED_MASK, occupied).withProperty(MODULE_MASK, modules);
    }

    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
            EntityPlayer player, EnumHand hand, EnumFacing face, float x, float y, float z) {
        int slot = ModuleRackGeometry.hitSlot(state.getValue(FACING).getIndex(), face.getIndex(), x, y, z);
        if (slot < 0 || world instanceof VehicleWorld || !ConfigurationAccess.canEditDialog(player, world, pos)) return false;
        TileEntity raw = world.getTileEntity(pos);
        if (!(raw instanceof TileEntityModuleRack)) return false;
        TileEntityModuleRack rack = (TileEntityModuleRack)raw;
        ItemStack held = player.getHeldItem(hand);
        boolean occupied = (rack.getOccupiedMask() & (1 << slot)) != 0;
        if (held.isEmpty()) {
            // An empty main hand must not swallow an off-hand insertion.
            if (!occupied && hand == EnumHand.MAIN_HAND && TileEntityModuleRack.accepts(player.getHeldItemOffhand())) return false;
            if (!world.isRemote && occupied) remove(world, pos, rack, slot, player);
            return true;
        }
        if (!TileEntityModuleRack.accepts(held)) return false;
        if (!world.isRemote && !occupied && rack.insertModule(slot, held, player.capabilities.isCreativeMode)) {
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, .25F, 1.3F);
        }
        return true;
    }

    private static void remove(World world, BlockPos pos, TileEntityModuleRack rack, int slot, EntityPlayer player) {
        ItemStack preview = rack.getStackInSlot(slot);
        if (!hasRoom(player, preview)) {
            player.sendStatusMessage(new TextComponentTranslation("message.vandorlabs.module_rack.inventory_full"), true);
            return;
        }
        ItemStack removed = rack.removeModule(slot);
        if (removed.isEmpty()) return;
        player.inventory.addItemStackToInventory(removed);
        // Another mod can change inventory in a block-update callback. Keep an
        // unaccepted item in its original slot rather than losing it on extraction.
        if (!removed.isEmpty()) {
            if (!rack.insertModule(slot, removed, false) && !removed.isEmpty()) player.dropItem(removed, false);
            player.sendStatusMessage(new TextComponentTranslation("message.vandorlabs.module_rack.inventory_full"), true);
            return;
        }
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, .25F, 1.3F);
    }

    private static boolean hasRoom(EntityPlayer player, ItemStack module) {
        if (module.isEmpty()) return true;
        for (ItemStack stack : player.inventory.mainInventory)
            if (stack.isEmpty() || mergeRoom(stack, module, player.inventory.getInventoryStackLimit())) return true;
        for (ItemStack stack : player.inventory.offHandInventory)
            if (!stack.isEmpty() && mergeRoom(stack, module, player.inventory.getInventoryStackLimit())) return true;
        return false;
    }

    private static boolean mergeRoom(ItemStack stack, ItemStack module, int inventoryLimit) {
        return ItemStack.areItemsEqual(stack, module) && ItemStack.areItemStackTagsEqual(stack, module)
                && stack.getCount() < Math.min(stack.getMaxStackSize(), inventoryLimit);
    }

    @Override public ItemStack getPickBlock(IBlockState state, RayTraceResult hit, World world,
            BlockPos pos, EntityPlayer player) { return new ItemStack(this); }

    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity raw = world.getTileEntity(pos);
        if (!world.isRemote && !VehicleTransfer.isMutating(world) && raw instanceof TileEntityModuleRack) {
            for (ItemStack module : ((TileEntityModuleRack)raw).drainModules())
                InventoryHelper.spawnItemStack(world, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, module);
        }
        super.breakBlock(world, pos, state);
    }
}

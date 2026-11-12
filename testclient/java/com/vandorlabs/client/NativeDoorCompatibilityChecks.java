package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockConfigurableSpaceDoor;
import com.vandorlabs.blocks.BlockDetailedDoor;
import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import com.vandorlabs.blocks.BlockProgrammableSlab;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.tiles.TileEntityLargeProgrammableDoor;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/** Checks the vanilla superclass assumptions that must not replace Vandor behavior. */
public final class NativeDoorCompatibilityChecks {
    private static final BlockPos POS = new BlockPos(8, 80, 8);

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        com.vandorlabs.VandorLabs.proxy = new com.vandorlabs.CommonProxy();
        com.vandorlabs.VandorLabs.logger = org.apache.logging.log4j.LogManager.getLogger("NativeDoorChecks");
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(
                TileEntitySpaceDoor.class, new ResourceLocation("vandorlabs", "native_door_fixture"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(
                TileEntityLargeProgrammableDoor.class, new ResourceLocation("vandorlabs", "native_large_door_fixture"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(
                com.vandorlabs.tiles.TileEntityTokenSlot.class, new ResourceLocation("vandorlabs", "native_reader_fixture"));
        BlockVandorDoor plain = new BlockVandorDoor("native_plain_fixture", BlockVandorDoor.DoorMotion.HINGED, false);
        BlockDetailedDoor paired = new BlockDetailedDoor("native_pair_fixture", BlockVandorDoor.DoorMotion.SLIDING,
                true, true, false, 1, 15, com.vandorlabs.blocks.BlockSpaceDoor.PIN_Z, 90, -90, -15, 15);
        BlockConfigurableSpaceDoor regular = new BlockConfigurableSpaceDoor("native_regular_fixture", true, paired);
        BlockLargeProgrammableDoor large = new BlockLargeProgrammableDoor("native_large_fixture", paired);
        java.lang.reflect.Field mapping = Item.class.getDeclaredField("BLOCK_TO_ITEM");
        mapping.setAccessible(true);
        Map<Block, Item> items = (Map<Block, Item>) mapping.get(null);
        for (BlockVandorDoor door : new BlockVandorDoor[]{plain, regular, large}) {
            ItemBlock item = new ItemBlock(door);
            items.put(door, item);
            require(door instanceof BlockDoor, "native door class: " + door.getRegistryName());
            for (int meta = 0; meta < 16; meta++) {
                IBlockState state = door.getStateFromMeta(meta);
                require(door.getMetaFromState(state) == meta, "legacy metadata round trip " + meta);
                require(state.getValue(BlockDoor.OPEN) == ((meta & 4) != 0), "native OPEN property " + meta);
                if (door == large || (meta & 8) == 0)
                    require(state.getValue(BlockDoor.FACING) == EnumFacing.getHorizontal(meta & 3), "legacy facing " + meta);
                else
                    require(state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER
                            && state.getValue(BlockDoor.HINGE) == ((meta & 1) == 0 ? BlockDoor.EnumHingePosition.LEFT : BlockDoor.EnumHingePosition.RIGHT)
                            && state.getValue(BlockDoor.POWERED) == ((meta & 2) != 0), "upper metadata " + meta);
            }
            NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
            IBlockState state = door.getDefaultState();
            world.setBlockState(POS, state, 2);
            require(door.getItem(world, POS, state).getItem() == item, "native item lookup must not return an oak door");
            require(door.getLocalizedName().equals(net.minecraft.util.text.translation.I18n.translateToLocal(
                    door.getUnlocalizedName() + ".name")), "tile translation key retained");
            require(door.getMobilityFlag(state) == EnumPushReaction.NORMAL, "do not inherit piston destruction");
            require(door.getBlockHardness(state, world, POS) == 5 && door.getHarvestLevel(state) == 1
                    && "pickaxe".equals(door.getHarvestTool(state)), "material defaults retained");
            for (EnumFacing side : EnumFacing.values())
                require(door.getBlockFaceShape(world, state, POS, side) == BlockFaceShape.SOLID, "existing attachment faces retained");
            world.setBlockState(POS.up(), state.withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.UPPER), 2);
            door.onBlockHarvested(world, POS, state, null);
            require(world.getBlockState(POS).getBlock() == door && world.getBlockState(POS.up()).getBlock() == door,
                    "Vandor breakBlock retains ownership of multipart removal");
        }
        require(plain.getBlockLayer() == BlockRenderLayer.SOLID && regular.getBlockLayer() == BlockRenderLayer.CUTOUT,
                "opaque and cutout render layers retained");
        NonRenderingChecks.MemoryWorld support = new NonRenderingChecks.MemoryWorld(false);
        support.setBlockState(POS.down(), new BlockProgrammableSlab().getDefaultState(), 2);
        require(regular.canPlaceBlockAt(support, POS), "bottom programmable slab remains a supported mount");
        support.setBlockState(POS.up(), Blocks.STONE.getDefaultState(), 2);
        require(!regular.canPlaceBlockAt(support, POS), "occupied upper cell is refused");

        int toggles = 0;
        for (BlockConfigurableSpaceDoor door : new BlockConfigurableSpaceDoor[]{regular, large})
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
                int columns = door == large ? 3 : 1, rows = door == large ? 3 : 2;
                EnumFacing width = facing.rotateYCCW();
                for (int x = 0; x < columns; x++) for (int y = 0; y < rows; y++) {
                    BlockPos at = POS.offset(width, x).up(y);
                    IBlockState state = door.getDefaultState().withProperty(BlockDoor.FACING, facing);
                    if (door != large) state = state.withProperty(BlockDoor.HALF,
                            y == 0 ? BlockDoor.EnumDoorHalf.LOWER : BlockDoor.EnumDoorHalf.UPPER);
                    world.setBlockState(at, state, 2);
                    if (door == large) ((TileEntityLargeProgrammableDoor) world.getTileEntity(at)).assign(POS);
                }
                for (int x = 0; x < columns; x++) for (int y = 0; y < rows; y++) {
                    BlockPos clicked = POS.offset(width, x).up(y);
                    for (boolean open : new boolean[]{true, false}) {
                        ((BlockDoor) door).toggleDoor(world, clicked, open);
                        for (int cx = 0; cx < columns; cx++) for (int cy = 0; cy < rows; cy++) {
                            BlockPos member = POS.offset(width, cx).up(cy);
                            require(world.getBlockState(member).getValue(BlockDoor.OPEN) == open, "native toggle updates every member");
                            require(door.isPassable(world, member) == open, "passability follows the resolved door, including large parts");
                        }
                        toggles++;
                    }
                }
                ((TileEntitySpaceDoor) world.getTileEntity(POS)).configureSecurity(1, 127);
                ((BlockDoor) door).toggleDoor(world, POS.offset(width, columns - 1).up(rows - 1), true);
                require(!world.getBlockState(POS).getValue(BlockDoor.OPEN), "native toggle cannot bypass root security");
            }
        preservedCaptureRules(regular);
        System.out.println("Native door compatibility PASS metadata=48 native-toggles=" + toggles
                + " item/material/attachment/harvest/render/support/security/capture contracts retained");
    }

    private static void preservedCaptureRules(BlockConfigurableSpaceDoor regular) {
        for (Block door : new Block[]{regular, Blocks.IRON_DOOR}) {
            NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
            java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells = new java.util.ArrayList<>();
            for (int y = 0; y < 2; y++) {
                BlockPos at = POS.up(y);
                IBlockState state = door.getDefaultState().withProperty(BlockDoor.HALF,
                        y == 0 ? BlockDoor.EnumDoorHalf.LOWER : BlockDoor.EnumDoorHalf.UPPER);
                world.setBlockState(at, state, 2);
                net.minecraft.tileentity.TileEntity tile = world.getTileEntity(at);
                cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(at, state,
                        tile == null ? null : tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));
            }
            com.vandorlabs.vehicle.VehicleStructure snapshot = com.vandorlabs.vehicle.VehicleStructure.display(cells);
            java.util.List<com.vandorlabs.copier.CopyMaterials.Need> materials = com.vandorlabs.copier.CopyMaterials.forSnapshot(
                    snapshot, new com.vandorlabs.vehicle.VehicleWorld(world, snapshot), false);
            require(materials.size() == 1 && materials.get(0).count == (door == regular ? 2 : 1),
                    "native type does not change existing copy material accounting");
            boolean refused = false;
            try {
                new com.vandorlabs.turntable.TurntableBoundaries(world, POS, POS).check(
                        POS, world.getBlockState(POS), world.getTileEntity(POS));
            } catch (IllegalArgumentException outside) {
                refused = true;
            }
            require(refused == (door == Blocks.IRON_DOOR), "native type does not change existing capture boundaries");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

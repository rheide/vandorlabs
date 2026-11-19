package com.vandorlabs.neoforge.compat;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.door.NativeDoorRegistrations;
import com.vandorlabs.neoforge.door.NativeProgrammableDoorBlock;
import com.vandorlabs.neoforge.propulsion.NativePropulsionBlock;
import com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock;
import com.vandorlabs.neoforge.shape.NativePanelBlock;
import com.vandorlabs.propulsion.PropulsionDefinition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeWorldEditRotationChecks {
    private static final String PREFIX = "[vandorlabs][worldedit-rotation]";
    private static final BlockPos SOURCE = new BlockPos(16, 100, 16);
    private record Cell(BlockPos offset, BlockState state, String marker) {}
    private static boolean done;

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        String mode = System.getProperty("vandorlabs.worldEditCheck", "");
        if (mode.isEmpty() || done) return;
        done = true;
        var world = event.getServer().overworld();
        try {
            if (mode.equals("absent")) {
                require(!ModList.get().isLoaded("worldedit"), "optional dependency genuinely absent");
                System.out.println(PREFIX + " absent PASS dedicatedStartup");
            } else {
                require(ModList.get().isLoaded("worldedit"), "installed WorldEdit must be loaded");
                run(world);
                System.out.println(PREFIX + " installed PASS actualClipboardCopyAndPaste/threeQuarterTurns/NBT");
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.out.println(PREFIX + " FAIL " + failure);
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void run(ServerLevel world) throws Exception {
        var cells = new ArrayList<Cell>();
        var door = NativeDoorRegistrations.ORDINARY.get().defaultBlockState()
            .setValue(NativeProgrammableDoorBlock.FACING, Direction.WEST)
            .setValue(NativeProgrammableDoorBlock.HINGE, DoorHingeSide.RIGHT);
        cells.add(new Cell(BlockPos.ZERO, door, null));
        cells.add(new Cell(BlockPos.ZERO.above(), door.setValue(NativeProgrammableDoorBlock.HALF, DoubleBlockHalf.UPPER), null));
        var diagonal = VandorLabsBootstrap.PROGRAMMABLE_DIAGONAL_WALL.get().defaultBlockState()
            .setValue(NativePanelBlock.FACING, Direction.EAST)
            .setValue(NativeDiagonalWallBlock.INVERTED, true);
        cells.add(new Cell(new BlockPos(0, 0, 2), diagonal, null));
        var vanilla = Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.SOUTH);
        cells.add(new Cell(new BlockPos(0, 0, 4), vanilla, null));
        for (var definition : PropulsionDefinition.values()) {
            if (definition.shape != 2) continue;
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("vandorlabs", definition.id));
            require(block instanceof NativePropulsionBlock, "registered propulsion wedge " + definition.id);
            for (var facing : List.of(Direction.UP, Direction.DOWN, Direction.EAST)) {
                var state = block.defaultBlockState().setValue(NativePropulsionBlock.FACING, facing);
                cells.add(new Cell(new BlockPos(cells.size() % 12 * 2 + 2, 0, cells.size() / 12 * 2),
                    state, definition.id + "/" + facing));
            }
        }
        for (var cell : cells) {
            var pos = SOURCE.offset(cell.offset);
            if (cell.offset.getY() == 0) world.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            world.setBlock(pos, cell.state, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);
            if (cell.marker != null) {
                var tile = world.getBlockEntity(pos);
                var tag = tile.saveWithFullMetadata(world.registryAccess());
                tag.putString("WorldEditFixtureMarker", cell.marker);
                tile.loadWithComponents(tag, world.registryAccess());
            }
        }

        var clipboard = copy(world, SOURCE, SOURCE.offset(24, 1, 8));
        var turns = List.of(Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90);
        var failures = new ArrayList<String>();
        for (int turn = 0; turn < turns.size(); turn++) {
            var rotation = turns.get(turn);
            var target = SOURCE.offset(64, 0, turn * 32);
            for (var cell : cells) {
                if (cell.offset.getY() == 0) world.setBlockAndUpdate(target.offset(cell.offset.rotate(rotation)).below(), Blocks.STONE.defaultBlockState());
            }
            paste(world, clipboard, target, (turn + 1) * -90);
            for (var cell : cells) {
                var pos = target.offset(cell.offset.rotate(rotation));
                var expected = cell.state.rotate(rotation);
                if (cell.state.getBlock() instanceof NativeProgrammableDoorBlock) {
                    expected = cell.state.setValue(NativeProgrammableDoorBlock.FACING,
                        rotation.rotate(cell.state.getValue(NativeProgrammableDoorBlock.FACING)));
                }
                var actual = world.getBlockState(pos);
                if (actual != expected) failures.add(rotation + " " + cell.offset + " expected " + expected + " got " + actual);
                if (cell.marker != null) {
                    var tile = world.getBlockEntity(pos);
                    require(tile != null && cell.marker.equals(tile.saveWithFullMetadata(world.registryAccess()).getString("WorldEditFixtureMarker")),
                        "WorldEdit retains the persisted tile NBT " + cell.marker);
                }
                require(world.getBlockState(SOURCE.offset(cell.offset)) == cell.state, "source block stays unchanged");
            }
        }
        require(failures.isEmpty(), failures.size() + " rotation mismatches, first " + failures.stream().limit(4).toList());
    }

    private static Class<?> api(String name) throws ClassNotFoundException {
        return Class.forName("com.sk89q.worldedit." + name);
    }

    private static Object vector(BlockPos pos) throws ReflectiveOperationException {
        return api("neoforge.NeoForgeAdapter").getMethod("adapt", BlockPos.class).invoke(null, pos);
    }

    private static Object session(ServerLevel level) throws ReflectiveOperationException {
        var worldEdit = api("WorldEdit");
        var world = api("neoforge.NeoForgeAdapter").getMethod("adapt", ServerLevel.class).invoke(null, level);
        return worldEdit.getMethod("newEditSession", api("world.World")).invoke(worldEdit.getMethod("getInstance").invoke(null), world);
    }

    private static void complete(Object operation) throws ReflectiveOperationException {
        api("function.operation.Operations").getMethod("complete", api("function.operation.Operation")).invoke(null, operation);
    }

    private static Object copy(ServerLevel world, BlockPos first, BlockPos last) throws Exception {
        var blockVector = api("math.BlockVector3");
        var regionType = api("regions.Region");
        var extent = api("extent.Extent");
        var origin = vector(first);
        var region = api("regions.CuboidRegion").getConstructor(blockVector, blockVector).newInstance(origin, vector(last));
        var clipboardType = api("extent.clipboard.BlockArrayClipboard");
        var clipboard = clipboardType.getConstructor(regionType).newInstance(region);
        clipboardType.getMethod("setOrigin", blockVector).invoke(clipboard, origin);
        try (var edit = (AutoCloseable) session(world)) {
            var operation = api("function.operation.ForwardExtentCopy").getConstructor(extent, regionType, extent, blockVector)
                .newInstance(edit, region, clipboard, origin);
            complete(operation);
        }
        return clipboard;
    }

    private static void paste(ServerLevel world, Object clipboard, BlockPos target, double degrees) throws Exception {
        var holderType = api("session.ClipboardHolder");
        var holder = holderType.getConstructor(api("extent.clipboard.Clipboard")).newInstance(clipboard);
        var affine = api("math.transform.AffineTransform");
        var transform = affine.getMethod("rotateY", double.class).invoke(affine.getConstructor().newInstance(), degrees);
        holderType.getMethod("setTransform", api("math.transform.Transform")).invoke(holder, transform);
        try (var edit = (AutoCloseable) session(world)) {
            var paste = holderType.getMethod("createPaste", api("extent.Extent")).invoke(holder, edit);
            var builder = api("session.PasteBuilder");
            builder.getMethod("to", api("math.BlockVector3")).invoke(paste, vector(target));
            builder.getMethod("ignoreAirBlocks", boolean.class).invoke(paste, true);
            complete(builder.getMethod("build").invoke(paste));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(PREFIX + " " + message);
    }

    private NativeWorldEditRotationChecks() {}
}

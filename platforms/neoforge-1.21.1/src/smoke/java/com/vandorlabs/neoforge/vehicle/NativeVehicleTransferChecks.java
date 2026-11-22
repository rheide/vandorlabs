package com.vandorlabs.neoforge.vehicle;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.drives.MaxwellEngine;
import com.vandorlabs.neoforge.builders.*;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.credentials.NativeCredentialReaderEntity;
import com.vandorlabs.neoforge.credentials.NativeReaderRegistrations;
import com.vandorlabs.neoforge.mixin.NativeRampChunkStorageAccess;
import com.vandorlabs.neoforge.seating.NativeSeatBindings;
import com.vandorlabs.neoforge.seating.NativeSeatBlock;
import com.vandorlabs.neoforge.seating.NativeSeatKind;
import com.vandorlabs.neoforge.transfer.NativeCellTransactions;
import com.vandorlabs.neoforge.transfer.NativeTransferHolds;
import com.vandorlabs.neoforge.transfer.NativeBuilderJournalFault;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Durable entity and terrain receipts through the production family, including abrupt restart. */
@EventBusSubscriber(modid = "vandorlabs")
public final class NativeVehicleTransferChecks {
    public static final BlockPos ARM_GALLERY_DONE = new BlockPos(15, 94, 7);
    private static final BlockPos ROOT = new BlockPos(15, 91, 8);
    private static final BlockPos PILOT = ROOT.east();
    private static final BlockPos BARREL = ROOT.south();
    private static final BlockPos PARK = ROOT.east(8);
    private static final BlockPos ENGINE = ROOT.east(2).south().above(), CARD = ROOT.west();
    private static final int FUEL_MB = 614;
    private static final UUID OWNER = new UUID(0, 0x7381);
    private static final AABB VOLUME = new AABB(ROOT).minmax(new AABB(PARK)).inflate(8);
    private static ServerLevel world;
    private static FakePlayer player;
    private static String feedback = "";
    private static final class Actor extends FakePlayer {
        Actor(ServerLevel level) { super(level, new GameProfile(OWNER, "VehicleTransferFixture")); }
        @Override public void displayClientMessage(Component message, boolean overlay) { feedback = message.getString(); }
    }
    private static NativeGroundVehicle craft, stale;
    private static NativeVehicleResourceFixture transit;
    private static NativeVehicleArmFixture arms;
    private static CompoundTag originalActor;
    private static int ticks, stage;
    private static boolean blockedBreak, blockedPlace, faulted, formed;
    private static Fluid fuel;

    @EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD)
    public static final class Fuels {
        @SubscribeEvent public static void register(RegisterEvent event) {
            if (!phase().equals("roundtrip")) return;
            event.register(Registries.FLUID, helper -> {
                fuel = new WaterFluid.Source();
                helper.register(ResourceLocation.parse("vandorlabs:fuelium"), fuel);
            });
        }
    }

    public static boolean armGallery() { return Boolean.getBoolean("vandorlabs.vehicleArmGallery"); }

    private static String phase() { return System.getProperty("vandorlabs.vehicleTransferPhase", ""); }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError("Native Vehicle " + phase() + " stage " + stage + " tick " + ticks + ": " + message);
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (phase().isEmpty()) return;
        try {
            world = event.getServer().overworld();
            for (int x = 0; x <= 1; x++) {
                world.setChunkForced(x, 0, true);
                world.getChunk(x, 0);
            }
            if (phase().startsWith("read-")) return;
            for (var cursor : BlockPos.betweenClosed(ROOT.offset(-3, -2, -4), PARK.offset(4, 5, 4))) {
                world.setBlock(cursor.immutable(), cursor.getY() == ROOT.getY() - 2
                    ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE);
            }
            for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) {
                world.setBlock(ROOT.offset(x, -1, z), Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), Block.UPDATE_ALL);
            }
            world.setBlock(ROOT, NativeVehicleBindings.CONTROLLER.defaultBlockState(), Block.UPDATE_ALL);
            ((NativeVehicleControllerEntity)world.getBlockEntity(ROOT)).placedBy(OWNER);
            var seat = NativeSeatBindings.BLOCKS.get(NativeSeatKind.PILOT);
            var lower = seat.defaultBlockState().setValue(NativeSeatBlock.FACING, Direction.EAST);
            world.setBlock(PILOT, lower, Block.UPDATE_ALL);
            world.setBlock(PILOT.above(), lower.setValue(NativeSeatBlock.UPPER, true), Block.UPDATE_ALL);
            ((com.vandorlabs.neoforge.seating.NativeChairEntity)world.getBlockEntity(PILOT)).placed();
            world.setBlock(BARREL, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH), Block.UPDATE_ALL);
            var inventory = (BarrelBlockEntity) world.getBlockEntity(BARREL);
            inventory.setItem(0, gold());
            inventory.setItem(1, new ItemStack(Items.DIAMOND, 2));
            inventory.setChanged();
            player = new Actor(world);
            player.setGameMode(GameType.SURVIVAL);
            movePlayer(ROOT);
            if (phase().equals("roundtrip")) {
                stageMaxwell();
                stageSecurity();
            }
            if (phase().equals("arms")) arms = new NativeVehicleArmFixture(world, ROOT);
            event.getServer().saveEverything(true, true, true);
            NativeBuilderJournalFault.set(NativeVehicleTransferChecks::fault);
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (world == null) return;
        try {
            if (armGallery() && ticks == 0) {
                var viewer = world.players().stream().findFirst().orElse(null);
                if (viewer == null) return;
                viewer.setGameMode(GameType.SPECTATOR);
                viewer.connection.teleport(ROOT.getX() - 3.5, ROOT.getY() + 3.5, ROOT.getZ() - 5.5, -45F, 19F);
            }
            require(++ticks < 450, "bounded asynchronous transfer; source=" + world.getBlockState(ROOT)
                + " parked=" + world.getBlockState(PARK) + " busySource=" + NativeCellTransactions.busy(world, ROOT, OWNER)
                + " busyTarget=" + NativeCellTransactions.busy(world, PARK, OWNER)
                + " actors=" + world.getEntitiesOfClass(NativeGroundVehicle.class, VOLUME).size() + " feedback=" + feedback);
            if (phase().equals("read-assemble")) {
                require(world.getBlockState(ROOT).is(NativeVehicleBindings.CONTROLLER), "cold assembly rollback recovers the controller");
                require(actors() == 0 && !NativeTransferHolds.active(world), "nothing carried after cold assembly rollback");
                requireInventory(BARREL);
                pass("partialTerrainFlush/asyncRollback/exactBarrelComponents/onePhysicalOwner");
                return;
            }
            if (phase().equals("read-park")) {
                if (actors() == 0 && ticks < 60) return;
                require(actors() == 1 && world.getBlockState(PARK).isAir() && !NativeTransferHolds.active(world),
                    "cold parking rollback restored one vehicle after terrain and entity writes");
                var live = world.getEntitiesOfClass(NativeGroundVehicle.class, VOLUME).getFirst();
                requireCarried(live);
                require(!NativeVehicleTransfer.escrowed(live), "cold recovery retired the journal and released the entity");
                pass("terrainAndEntityFlushed/rollbackRecreatesActor/exactBarrelComponents/oneCarriedOwner");
                return;
            }
            if (faulted || ticks < 12) return;
            if (stage == 0) {
                if (transit != null && (!formMaxwell() || !transit.formed(player))) return;
                if (arms != null && !arms.formed(player)) return;
                require(!new ChunkPos(ROOT).equals(new ChunkPos(PILOT)), "fixture hull crosses chunk boundaries");
                if (phase().equals("roundtrip")) {
                    exerciseSecurity();
                    world.noSave = true;
                    NativeVehicleTransfer.assemble(player, ROOT);
                    world.noSave = false;
                    require(!NativeCellTransactions.busy(world, ROOT, OWNER), "disabled saving refuses assembly");
                    blockedBreak = true;
                    NativeVehicleTransfer.assemble(player, ROOT);
                    blockedBreak = false;
                    require(!NativeCellTransactions.busy(world, ROOT, OWNER), "break protection refuses exactly the requested move");
                    requireInventory(BARREL);
                }
                NativeVehicleTransfer.assemble(player, ROOT);
                require(NativeCellTransactions.busy(world, ROOT, OWNER), "admitted assembly via production entry point: " + feedback);
                stage = 1;
                return;
            }
            if (stage == 1) {
                if (NativeCellTransactions.busy(world, ROOT, OWNER)) return;
                require(world.getBlockState(ROOT).isAir() && world.getBlockState(BARREL).isAir() && actors() == 1,
                    "durable assembly leaves only the carried owner: " + feedback);
                require(world.getBlockState(ROOT.below(2)).is(Blocks.SMOOTH_STONE), "top-slab deck leaves the terrain in place");
                craft = world.getEntitiesOfClass(NativeGroundVehicle.class, VOLUME).getFirst();
                requireCarried(craft);
                if (transit != null || arms != null) {
                    stage = 5;
                    return;
                }
                moveToParking();
                return;
            }
            if (stage == 5) {
                if (transit != null && !transit.transferred(craft, player, FUEL_MB)
                    || arms != null && !arms.transferred(craft, player)) return;
                moveToParking();
                return;
            }
            if (stage == 2) {
                if (!craft.canPark() || NativeVehicleTransfer.escrowed(craft)) return;
                if (phase().equals("roundtrip")) {
                    var blocked = parked(BARREL);
                    world.setBlock(blocked, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE);
                    NativeVehicleTransfer.park(player, craft);
                    require(!NativeCellTransactions.busy(world, PARK, OWNER) && !craft.isRemoved()
                        && world.getBlockState(blocked).is(Blocks.IRON_BLOCK), "parking obstruction preserves both owners");
                    world.setBlock(blocked, Blocks.AIR.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE);
                    blockedPlace = true;
                    NativeVehicleTransfer.park(player, craft);
                    blockedPlace = false;
                    require(!NativeCellTransactions.busy(world, PARK, OWNER), "place protection refuses the target transaction");
                } else {
                    event.getServer().saveEverything(true, true, true);
                }
                NativeVehicleTransfer.park(player, craft);
                require(NativeCellTransactions.busy(world, PARK, OWNER) && NativeVehicleTransfer.escrowed(craft),
                    "admitted parking freezes the source actor immediately: " + feedback);
                stage = 3;
                return;
            }
            if (stage == 4) {
                if (!stale.isRemoved()) return;
                require(actors() == 0, "the durable retirement removes a stale actor from its previous chunk");
                requireInventory(parked(BARREL));
                pass("permissionProbes/disabledSaving/obstruction/assembly/rotatedParking/exactBarrelComponents/maxwellFuelAndOwnedParts/RFFluidItemRoutes/gatedBatteryAndTankAnalog/surveyScanAndExactDebits/securedCardOwnership/staleChunkActor");
                return;
            }
            if (NativeCellTransactions.busy(world, PARK, OWNER)) return;
            require(actors() == 0 && craft.isRemoved() && !NativeTransferHolds.active(world), "parking retires its carried owner");
            require(world.getBlockState(PARK).is(NativeVehicleBindings.CONTROLLER), "restores controller at rotated anchor");
            require(world.getBlockState(parked(PILOT)).getValue(NativeSeatBlock.FACING) == Direction.SOUTH,
                "restores rotated Pilot Seat lower and backrest");
            var upper = world.getBlockState(parked(PILOT.above()));
            require(upper.getValue(NativeSeatBlock.UPPER) && upper.getValue(NativeSeatBlock.FACING) == Direction.SOUTH,
                "parked Pilot Seat assembly is structurally complete");
            require(world.getBlockState(parked(BARREL)).getValue(BlockStateProperties.FACING) == Direction.WEST,
                "native barrel facing rotates");
            requireInventory(parked(BARREL));
            if (arms != null) {
                arms.parked(PARK);
                pass("realArmPlacement/settledRF/movingAndFoldedGate/reverseFluid/componentItems/rotatedParking");
                return;
            }
            requireMaxwellParked();
            transit.parked(PARK);
            requireParkedSecurity();
            require(!new ChunkPos(ROOT).equals(new ChunkPos(PARK)), "parking crossed to a different entity storage chunk");
            stale = NativeVehicleBindings.VEHICLE.create(world);
            require(stale != null, "native actor factory is available for the stale-record regression");
            stale.load(originalActor.copy());
            require(world.addFreshEntity(stale) && NativeVehicleTransfer.escrowed(stale),
                "an older snapshot of the retired UUID cannot become usable while its record is checked");
            stage = 4;
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent public static void protectBreak(BlockEvent.BreakEvent event) {
        if (blockedBreak && event.getLevel() == world && event.getPos().equals(BARREL)) event.setCanceled(true);
    }

    @SubscribeEvent public static void protectPlace(BlockEvent.EntityPlaceEvent event) {
        if (blockedPlace && event.getLevel() == world && event.getPos().equals(parked(BARREL))
            && event.getPlacedBlock().is(Blocks.BARREL) && event.getBlockSnapshot().getState().isAir()) event.setCanceled(true);
    }

    private static void fault(String point, NativeCellTransactions.Header header, int ignored) {
        if (!header.family().getPath().equals("vehicle_transfer")) return;
        if (phase().equals("crash-assemble") && point.equals("after-install")) {
            require(world.getBlockState(BARREL).isAir() && actors() == 1, "assembly removed the native barrel and installed an escrow actor");
            require(NativeVehicleTransfer.escrowed(world.getEntitiesOfClass(NativeGroundVehicle.class, VOLUME).getFirst()),
                "uncommitted actor is frozen");
            requireNoDrops();
            faulted = true;
            world.noSave = true;
            var source = new ChunkPos(BARREL);
            var chunk = world.getChunkSource().getChunkNow(source.x, source.z);
            world.getChunkSource().chunkMap.write(source, ChunkSerializer.write(world, chunk))
                .thenCompose(unused -> ((NativeRampChunkStorageAccess)(Object)world.getChunkSource().chunkMap)
                    .vandorlabs$rampWorker().synchronize(true))
                .whenComplete((unused, failure) -> {
                    if (failure != null) {
                        failure.printStackTrace();
                        Runtime.getRuntime().halt(2);
                    }
                    abrupt("realArmed/partialSourceChunkOnly/noGracefulShutdown");
                });
            throw new IllegalStateException("Deliberate abrupt partial terrain save");
        }
        if (phase().equals("crash-park") && point.equals("checkpoint-durable") && header.root().equals(PARK)) {
            require(actors() == 0, "park publishes terrain and writes entity removal before commitment");
            requireInventory(parked(BARREL));
            abrupt("realArmed/terrainAndEntityCheckpoint/noGracefulShutdown");
        }
    }

    private static void stageSecurity() {
        var owner = (NativeVehicleControllerEntity)world.getBlockEntity(ROOT);
        var settings = owner.saveWithFullMetadata(world.registryAccess());
        settings.putBoolean("VehicleSecurity", true);
        owner.loadWithComponents(settings, world.registryAccess());
        owner.setChanged();
        var state = NativeReaderRegistrations.ACCESS_CARD_SLOT.get().defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.WEST);
        world.setBlock(CARD, state, Block.UPDATE_ALL);
    }

    private static void exerciseSecurity() {
        var controller = (NativeVehicleControllerEntity)world.getBlockEntity(ROOT);
        var reader = (NativeCredentialReaderEntity)world.getBlockEntity(CARD);
        require(reader.configure(player, false, 1 << 2, 0, reader.mountSettings()), "configured native Access Card filter");
        require(!controller.mayOperate(player), "secured Controller refuses an empty reader");
        NativeVehicleTransfer.assemble(player, ROOT);
        require(!NativeCellTransactions.busy(world, ROOT, OWNER), "secured Controller cannot journal without its accepted card");
        var wrong = NativeCredentialItems.accessCard(1);
        player.setItemInHand(InteractionHand.MAIN_HAND, wrong);
        reader.interact(player, InteractionHand.MAIN_HAND);
        require(ItemStack.matches(player.getMainHandItem(), wrong) && !controller.mayOperate(player), "wrong Access Card is neither consumed nor authorized");
        player.setItemInHand(InteractionHand.MAIN_HAND, keyCard());
        require(reader.interact(player, InteractionHand.MAIN_HAND) && player.getMainHandItem().isEmpty()
            && controller.mayOperate(player), "the accepted native Access Card authorizes the Controller");
    }

    private static void requireParkedSecurity() {
        var at = parked(CARD);
        var reader = (NativeCredentialReaderEntity)world.getBlockEntity(at);
        var owner = (NativeVehicleControllerEntity)world.getBlockEntity(PARK);
        require(reader.getBlockState().getValue(BlockStateProperties.FACING) == Direction.NORTH
            && ItemStack.matches(reader.getItem(0), keyCard()) && owner.mayOperate(player), "rotated attached card remains uniquely owned and authorized");
        require(reader.interact(player, InteractionHand.MAIN_HAND) && ItemStack.matches(player.getMainHandItem(), keyCard())
            && reader.getItem(0).isEmpty() && !owner.mayOperate(player), "taking the exact native card revokes Controller authorization");
    }

    private static ItemStack keyCard() {
        var key = NativeCredentialItems.accessCard(2);
        key.set(DataComponents.CUSTOM_NAME, Component.literal("Vehicle transfer access key"));
        return key;
    }

    private static void stageMaxwell() {
        var core = NativeBuilderBindings.CORES.get(MaxwellEngine.DESIGN);
        world.setBlock(ENGINE.below(), Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(ENGINE, core.defaultBlockState(), Block.UPDATE_ALL);
        var shape = NativeBuilderFormation.layout(core.design, 3, Direction.NORTH.get3DDataValue(), 0, 0);
        for (int x = 0; x < shape.width; x++) for (int y = 0; y < shape.height; y++) for (int z = 0; z < shape.depth; z++) {
            var pos = ENGINE.offset(shape.x(x, z), y, shape.z(x, z));
            if (!pos.equals(ENGINE)) world.setBlock(pos, shape.frame(x, y, z)
                ? NativeBuilderFormation.matter().defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        transit = new NativeVehicleResourceFixture(world, ROOT, ENGINE, fuel);
    }

    private static boolean formMaxwell() {
        var core = (NativeBuilderEntity)world.getBlockEntity(ENGINE);
        if (!formed) {
            require(NativeBuilderTransactions.request(core, player, true), "native Maxwell formation accepted");
            formed = true;
            return false;
        }
        if (!core.formed() || !core.available()) return false;
        if (core.maxwellFuel() == 0) {
            var pos = core.maxwellPort(Direction.NORTH);
            var inlet = world.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.NORTH);
            require(inlet != null && inlet.fill(new FluidStack(fuel, FUEL_MB), FluidAction.EXECUTE) == FUEL_MB,
                "registered fuel fills the real native Maxwell inlet");
        }
        return true;
    }

    private static void requireMaxwellParked() {
        var at = parked(ENGINE);
        var core = (NativeBuilderEntity)world.getBlockEntity(at);
        require(core.formed() && core.facing() == Direction.EAST && core.maxwellFuel() == FUEL_MB + NativeVehicleResourceFixture.FUEL_REFILL,
            "parked native Maxwell preserves its formed state, rotated facing and exact fuel amount");
        var layout = core.layout();
        for (int x = 0; x < layout.width; x++) for (int y = 0; y < layout.height; y++) for (int z = 0; z < layout.depth; z++) {
            require(NativeBuilderBindings.ownerAt(world, core.cell(x, y, z)) == core,
                "rotated Maxwell frame member keeps its unique native owner");
        }
        var inlet = world.getCapability(Capabilities.FluidHandler.BLOCK, core.maxwellPort(Direction.EAST), Direction.EAST);
        require(inlet != null && inlet.getFluidInTank(0).getFluid() == fuel && inlet.getFluidInTank(0).getAmount() == FUEL_MB + NativeVehicleResourceFixture.FUEL_REFILL,
            "rotated front inlet exposes the original fuel identity and no duplicate owner");
    }

    private static void moveToParking() {
        originalActor = craft.saveWithoutId(new CompoundTag());
        craft.setYRot(90);
        craft.setPos(PARK.getX() + .5, PARK.getY(), PARK.getZ() + .5);
        movePlayer(PARK);
        stage = 2;
    }

    private static void movePlayer(BlockPos anchor) {
        player.setPos(anchor.getX() + .5, anchor.getY(), anchor.getZ() - 2.5);
    }

    private static int actors() {
        return world.getEntitiesOfClass(NativeGroundVehicle.class, VOLUME).size();
    }

    private static BlockPos parked(BlockPos original) {
        return PARK.offset(original.subtract(ROOT).rotate(Rotation.CLOCKWISE_90));
    }

    private static ItemStack gold() {
        var result = new ItemStack(Items.GOLD_INGOT, 31);
        result.set(DataComponents.CUSTOM_NAME, Component.literal("Vehicle ownership receipt"));
        var data = new CompoundTag();
        data.putString("Fixture", "single-owner");
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return result;
    }

    private static void requireCarried(NativeGroundVehicle vehicle) {
        require(vehicle.admitted() && OWNER.equals(vehicle.owner()) && vehicle.pilotSeat().equals(PILOT.subtract(ROOT))
            && vehicle.facing() == Direction.EAST, "admitted snapshot contains original owner and Pilot Seat");
        var barrel = vehicle.snapshot().cells().stream().filter(cell -> cell.pos.equals(BARREL.subtract(ROOT))).findFirst().orElseThrow();
        var raw = barrel.tile();
        var inventory = raw.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        require(inventory.size() == 2, "carried barrel keeps exactly two occupied slots");
        require(ItemStack.matches(ItemStack.parseOptional(world.registryAccess(), inventory.getCompound(0)), gold())
            && ItemStack.matches(ItemStack.parseOptional(world.registryAccess(), inventory.getCompound(1)), new ItemStack(Items.DIAMOND, 2)),
            "carried inventory preserves item identity, count, name and component payload");
        if (phase().equals("roundtrip")) {
            var core = vehicle.snapshot().cells().stream().filter(cell -> cell.pos.equals(ENGINE.subtract(ROOT))).findFirst().orElseThrow();
            var tank = NativeMaxwellTank.read(core.tile(), world.registryAccess());
            require(tank.amount() == FUEL_MB && tank.contents().getFluid() == fuel,
                "carried Maxwell snapshot retains exact native fuel and its identity");
            var reader = vehicle.snapshot().cells().stream().filter(cell -> cell.pos.equals(CARD.subtract(ROOT))).findFirst().orElseThrow();
            require(ItemStack.matches(ItemStack.parseOptional(world.registryAccess(), reader.tile().getCompound("InsertedToken")), keyCard()),
                "carried Access Card remains one exact owned item");
        }
        requireNoDrops();
    }

    private static void requireInventory(BlockPos at) {
        require(world.getBlockEntity(at) instanceof BarrelBlockEntity, "native barrel is installed at " + at.toShortString());
        var inventory = (BarrelBlockEntity)world.getBlockEntity(at);
        require(ItemStack.matches(inventory.getItem(0), gold()) && ItemStack.matches(inventory.getItem(1), new ItemStack(Items.DIAMOND, 2)),
            "native inventory preserves exact item contents and components");
        for (int slot = 2; slot < inventory.getContainerSize(); slot++) require(inventory.getItem(slot).isEmpty(), "no extra inventory slots");
        requireNoDrops();
    }

    private static void requireNoDrops() {
        require(world.getEntitiesOfClass(ItemEntity.class, VOLUME).isEmpty(), "ownership handoff cannot spawn a loose resource copy");
    }

    private static void pass(String evidence) {
        System.out.println("[vandorlabs][vehicle-transfer] " + phase() + " PASS " + evidence);
        NativeBuilderJournalFault.set(null);
        var server = world.getServer();
        if (armGallery()) {
            world.setBlock(ARM_GALLERY_DONE, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            System.out.println("[vandorlabs][vehicle-arm-player] server PASS real mounted and parked owners");
        }
        world = null;
        if (!armGallery()) server.halt(false);
    }

    private static void abrupt(String evidence) {
        System.out.println("[vandorlabs][vehicle-transfer] " + phase() + " PASS " + evidence);
        System.out.flush();
        Runtime.getRuntime().halt(0);
    }

    private static void fail(Throwable failure) {
        failure.printStackTrace();
        System.out.println("[vandorlabs][vehicle-transfer] " + phase() + " FAIL " + failure);
        NativeBuilderJournalFault.set(null);
        if (world != null) {
            var server = world.getServer();
            world.noSave = false;
            world = null;
            server.halt(false);
        }
    }

    private NativeVehicleTransferChecks() {}
}

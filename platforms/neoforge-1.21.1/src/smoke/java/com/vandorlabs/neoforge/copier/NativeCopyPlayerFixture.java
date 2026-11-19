package com.vandorlabs.neoforge.copier;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.surface.NativeSurfaceBindings;
import com.vandorlabs.neoforge.navigation.NativeNavigationChip;
import com.vandorlabs.neoforge.navigation.NativeNavigationTableAssemblies;
import com.vandorlabs.neoforge.navigation.NativeNavigationTableBindings;
import com.vandorlabs.neoforge.navigation.NativeNavigationTableEntity;
import com.vandorlabs.neoforge.credentials.NativeChipTypes;
import com.vandorlabs.neoforge.transfer.NativeTransferHolds;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeCopyPlayerFixture {
    static final BlockPos ROOT = new BlockPos(8, 96, 8);
    static final BlockPos INPUT = ROOT.east(), OUTPUT = ROOT.west();
    private static final BlockPos SUPPLY = ROOT.south();
    private static ServerPlayer player;
    private static int stage, tablePublications;
    private static ItemStack chip, wrongChip;
    static boolean navigation() { return Boolean.getBoolean("vandorlabs.copyPlayerNavigationCheck"); }
    private static final BlockPos TABLE_NEIGHBOR = OUTPUT.above().north();

    static boolean enabled() {
        return Boolean.getBoolean("vandorlabs.copyPlayerCheck");
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer actor)) return;
        player = actor;
        var world = actor.serverLevel();
        require(world.getRecipeManager().byKey(ResourceLocation.parse("vandorlabs:copy_controller")).isPresent(),
            "public Copy Controller crafting recipe");
        for (var pos : BlockPos.betweenClosed(ROOT.offset(-4, -1, -4), ROOT.offset(4, 4, 3))) {
            world.setBlockAndUpdate(pos, pos.getY() < ROOT.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        world.setBlockAndUpdate(ROOT, NativeCopyBindings.BLOCK.defaultBlockState());
        world.setBlockAndUpdate(INPUT, NativeSurfaceBindings.BLOCK.defaultBlockState());
        world.setBlockAndUpdate(OUTPUT, NativeSurfaceBindings.BLOCK.defaultBlockState());
        if (navigation()) {
            world.setBlockAndUpdate(INPUT.above(), NativeNavigationTableBindings.FLAT.defaultBlockState());
            world.setBlockAndUpdate(TABLE_NEIGHBOR, NativeNavigationTableBindings.FLAT.defaultBlockState());
            var source = (NativeNavigationTableEntity) world.getBlockEntity(INPUT.above());
            NativeNavigationTableAssemblies.rebuild(source, new java.util.HashSet<>());
            chip = NativeChipTypes.variant("navigation");
            NativeNavigationChip.write(chip, world, 900000, 800000);
            chip.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Surveyed beacon"));
            wrongChip = chip.copy();
            NativeNavigationChip.write(wrongChip, world, 900000, 800001);
            require(source.setChip(chip), "physical source Navigation Chip installed");
            source.setRxChannels(ChannelList.of(50));
            NativeCopyService.fault = (header, cell) -> {
                if (!cell.pos.equals(OUTPUT.above())) return;
                require(NativeTransferHolds.reserved(world, TABLE_NEIGHBOR)
                    && !world.setBlockAndUpdate(TABLE_NEIGHBOR, NativeNavigationTableBindings.FLAT.defaultBlockState()),
                    "adjacent Table publication is held until durable retirement");
                require(world.getBlockEntity(OUTPUT.above()) instanceof NativeNavigationTableEntity table
                    && ItemStack.matches(table.storedChip(), chip), "physical output chip staged before injected failure");
                if (++tablePublications == 1) throw new IllegalStateException("Expected installed Navigation Table rollback");
            };
        } else world.setBlockAndUpdate(INPUT.above(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(SUPPLY, Blocks.CHEST.defaultBlockState());
        var supply = (ChestBlockEntity) world.getBlockEntity(SUPPLY);
        if (navigation()) {
            supply.setItem(0, new ItemStack(NativeNavigationTableBindings.FLAT, 2));
            supply.setItem(1, chip.copy());
            supply.setItem(2, wrongChip.copy());
        } else supply.setItem(0, new ItemStack(Items.STONE, 2));
        actor.setGameMode(GameType.SURVIVAL);
        actor.getInventory().clearContent();
        setHeld(ItemStack.EMPTY);
        actor.connection.teleport(8.5, 96, 6.5, 0, 0);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (player == null) return;
        var world = player.serverLevel();
        var tile = (NativeCopyControllerEntity) world.getBlockEntity(ROOT);
        if (stage == 0 && player.containerMenu instanceof NativeCopyMenu menu) {
            require(player.getMainHandItem().isEmpty() && menu.tile == tile && menu.stillValid(player),
                "ordinary Survival click opens the correct authorized menu");
            stage = 1;
        } else if (stage == 1 && player.containerMenu == player.inventoryMenu) {
            require(tile.settings().activation == 2 && tile.settings().height == 1,
                "actual right cycle and height changes reached the server");
            setHeld(new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
            stage = 2;
        } else if (stage == 2 && player.containerMenu instanceof NativeCopyMenu menu) {
            require(player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()) && menu.tile == tile && menu.stillValid(player),
                "Configurizer opens the same authorized menu");
            stage = 3;
        } else if (navigation() && stage == 3 && tile.error().contains("clear of adjacent tables")) {
            require(tablePublications == 0 && world.getBlockState(OUTPUT.above()).isAir(), "adjacent table refusal publishes nothing");
            assertNavigationOwnership(false);
            world.removeBlock(TABLE_NEIGHBOR, false);
            stage = 5;
        } else if (navigation() && stage == 5 && !tile.busy() && tile.status().equals("Copy rolled back")) {
            require(tablePublications == 1 && world.getBlockState(OUTPUT.above()).isAir(), "rollback removes staged table");
            assertNavigationOwnership(false);
            stage = 6;
        } else if ((navigation() ? stage == 6 : stage == 3) && !tile.busy() && tile.status().equals("Copy complete")) {
            require(tile.input() != null && tile.output() != null, "both pads remain claimed");
            if (navigation()) {
                assertNavigationOwnership(true);
                require(tablePublications == 2 && world.getBlockEntity(OUTPUT.above()) instanceof NativeNavigationTableEntity table
                    && table.formed() && table.ownsReader() && table.rxChannels().equals(ChannelList.of(50))
                    && ItemStack.matches(table.chipStack(), chip), "retry creates one usable isolated output chip reader");
                System.out.println("[vandorlabs][copy-player] Navigation Table PASS adjacentRefusal/actualRollback/exactPhysicalChip");
                NativeCopyService.fault = null;
            } else {
                require(world.getBlockState(INPUT.above()).is(Blocks.STONE) && world.getBlockState(OUTPUT.above()).is(Blocks.STONE),
                    "input remains and physical output was installed");
                require(ItemStack.matches(((ChestBlockEntity) world.getBlockEntity(SUPPLY)).getItem(0), new ItemStack(Items.STONE)),
                    "Survival materials withdrawn exactly once");
            }
            System.out.println("[vandorlabs][copy-player] server PASS ordinary/Configurizer/settings/pairedPads/materialOwnership");
            stage = 4;
        }
    }

    private static void assertNavigationOwnership(boolean paid) {
        var world = player.serverLevel();
        var supply = (ChestBlockEntity) world.getBlockEntity(SUPPLY);
        require(supply.getItem(0).is(NativeNavigationTableBindings.FLAT.asItem()) && supply.getItem(0).getCount() == (paid ? 1 : 2)
            && ItemStack.matches(supply.getItem(1), paid ? ItemStack.EMPTY : chip)
            && ItemStack.matches(supply.getItem(2), wrongChip), "table and exact matching chip costs with no affected decoy");
        require(world.getBlockEntity(INPUT.above()) instanceof NativeNavigationTableEntity table
            && ItemStack.matches(table.storedChip(), chip), "original chip stays in the original table");
        require(world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(ROOT).inflate(8)).isEmpty(), "no staged or refunded Navigation Chip drops");
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != player) return;
        require(stage == 4, "player completed the Copy journey");
        player = null;
    }

    private static void setHeld(ItemStack stack) {
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.connection.send(new ClientboundSetCarriedItemPacket(0));
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, 0, stack.copy()));
        player.inventoryMenu.broadcastChanges();
    }

    static void require(boolean valid, String detail) {
        if (!valid) throw new AssertionError("Copy player: " + detail);
    }

    private NativeCopyPlayerFixture() {}
}

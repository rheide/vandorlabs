package com.vandorlabs.neoforge.copier;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.surface.NativeSurfaceBindings;
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
    private static int stage;

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
        world.setBlockAndUpdate(INPUT.above(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(SUPPLY, Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) world.getBlockEntity(SUPPLY)).setItem(0, new ItemStack(Items.STONE, 2));
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
        } else if (stage == 3 && !tile.busy() && tile.status().equals("Copy complete")) {
            require(tile.input() != null && tile.output() != null, "both pads remain claimed");
            require(world.getBlockState(INPUT.above()).is(Blocks.STONE) && world.getBlockState(OUTPUT.above()).is(Blocks.STONE),
                "input remains and physical output was installed");
            require(ItemStack.matches(((ChestBlockEntity) world.getBlockEntity(SUPPLY)).getItem(0), new ItemStack(Items.STONE)),
                "Survival materials withdrawn exactly once");
            System.out.println("[vandorlabs][copy-player] server PASS ordinary/Configurizer/settings/pairedPads/materialOwnership");
            stage = 4;
        }
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

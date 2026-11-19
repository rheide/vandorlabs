package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.surface.NativeSurfaceBindings;
import com.vandorlabs.neoforge.surface.NativeSurfaceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeTurntablePlayerFixture {
    static final BlockPos ROOT = new BlockPos(14, 130, 6);
    static final BlockPos SOURCE = ROOT.east(2).above();
    static final BlockPos TARGET = SOURCE.south();
    static final Vec3 ACTOR = new Vec3(14.5, 130, 3.5);
    private static final boolean ENABLED = Boolean.getBoolean("vandorlabs.turntablePlayerChecks");
    private static ServerPlayer player;
    private static boolean captured;
    private static boolean reported;

    static boolean enabled() { return ENABLED; }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!enabled()) return;
        var world = event.getServer().overworld();
        world.getChunkAt(ROOT);
        world.getChunkAt(SOURCE);
        world.setBlockAndUpdate(BlockPos.containing(ACTOR).below(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(ROOT, NativeTurntableBindings.block.defaultBlockState()
                .setValue(NativeTurntableBlock.FACING, Direction.EAST));
        var pad = NativeSurfaceBindings.BLOCK.defaultBlockState().setValue(NativeSurfaceBlock.FACING, Direction.UP);
        for (int x = 1; x <= 2; x++) for (int z = 0; z < 2; z++) world.setBlock(ROOT.offset(x, 0, z), pad, Block.UPDATE_ALL);
        world.setBlockAndUpdate(SOURCE, Blocks.OAK_SIGN.defaultBlockState());
        ((SignBlockEntity) world.getBlockEntity(SOURCE)).updateText(
                line -> line.setMessage(0, Component.literal("Turntable player sign")), true);
    }

    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer actor)) return;
        player = actor;
        actor.setGameMode(GameType.SURVIVAL);
        actor.getInventory().selected = 0;
        actor.getInventory().setItem(0, ItemStack.EMPTY);
        actor.getInventory().setItem(1, new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
        actor.connection.teleport(ACTOR.x, ACTOR.y, ACTOR.z, 0, 0);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!enabled() || reported || player == null || player.isRemoved()) return;
        var world = player.serverLevel();
        if (!(world.getBlockEntity(ROOT) instanceof NativeTurntableEntity controller)) return;
        if (controller.hasCapture() && world.getBlockState(SOURCE).isAir()) captured = true;
        if (controller.busy() || !captured || controller.hasCapture() || controller.settings().height != 2
                || !(player.containerMenu instanceof NativeTurntableMenu)
                || !player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())
                || !(world.getBlockEntity(TARGET) instanceof SignBlockEntity moved)
                || !moved.getFrontText().getMessage(0, false).getString().equals("Turntable player sign")) return;
        reported = true;
        System.out.println("[vandorlabs][turntable-player] server PASS exact owner and Configurizer menu");
    }

    @SubscribeEvent public static void left(PlayerEvent.PlayerLoggedOutEvent event) {
        if (enabled() && reported && event.getEntity() == player) player.getServer().halt(false);
    }
}

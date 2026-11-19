package com.vandorlabs.neoforge.legacylanding;

import com.vandorlabs.neoforge.transfer.NativeCellTransactions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** An explicit operator maintenance request for a stationary, loaded legacy wheel. */
@EventBusSubscriber(modid = "vandorlabs")
public final class NativeLegacyMigrationCommand {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (!NativeLegacyLandingBindings.enabled()) return;
        event.getDispatcher().register(Commands.literal("vandorlabs")
            .then(Commands.literal("convert-landing-gear")
                .requires(source -> source.hasPermission(2) && source.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("position", BlockPosArgument.blockPos())
                    .executes(context -> convert(context.getSource(), BlockPosArgument.getBlockPos(context, "position"))))));
    }

    private static int convert(CommandSourceStack source, BlockPos pos) {
        var world = source.getLevel();
        if (!world.getWorldBorder().isWithinBounds(pos) || world.isOutsideBuildHeight(pos)) {
            return failure(source, "Select a position inside the world.");
        }
        var root = NativeLegacyLandingBlock.owner(world, pos);
        if (root == null || root.kind() != NativeLegacyLandingKind.WHEEL) {
            return failure(source, "Select a loaded stationary telescopic wheel or one of its verified helpers.");
        }
        try {
            var existing = NativeLegacyMigration.pending(root);
            if (existing != null) {
                if (existing.status() == NativeCellTransactions.Status.FAILED) {
                    return failure(source, "Landing gear conversion is retained for recovery. " + existing.error());
                }
                source.sendSuccess(() -> Component.literal("Landing gear conversion is already pending."), false);
                return 1;
            }
            var operation = NativeLegacyMigration.request(root);
            if (operation == null) return failure(source, "Conversion could not be queued. Check world saving and try again.");
            NativeLegacyMigration.notifyOnCompletion(root, (ServerPlayer) source.getEntity());
            source.sendSuccess(() -> Component.literal("Stationary landing gear conversion queued."), false);
            return 1;
        } catch (IllegalArgumentException refusal) {
            return failure(source, refusal.getMessage());
        }
    }

    private static int failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private NativeLegacyMigrationCommand() {}
}

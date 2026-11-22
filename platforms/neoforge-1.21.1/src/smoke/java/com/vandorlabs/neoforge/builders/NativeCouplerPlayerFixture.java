package com.vandorlabs.neoforge.builders;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.coupler.NativeCouplerBindings;
import com.vandorlabs.neoforge.coupler.NativeCouplerEntity;
import com.vandorlabs.neoforge.coupler.NativeCouplerMenu;
import com.vandorlabs.redstone.ChannelList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;

final class NativeCouplerPlayerFixture {
    static final BlockPos SQUARE = new BlockPos(0, 96, 0), SLAB = SQUARE.east(2);
    static final ChannelList BANK = ChannelList.of(182, 204);
    private static UUID actor;
    private static int next;

    static boolean enabled() { return Boolean.getBoolean("vandorlabs.couplerClient"); }
    static BlockPos ack(int stage) { return SQUARE.offset(-2, 0, stage); }
    static Vec3 position(BlockPos pos) { return new Vec3(pos.getX() + .5, 96, -2.5); }
    static Vec3 target(BlockPos pos, boolean placing) {
        return new Vec3(pos.getX() + .5, 96 + (pos.equals(SLAB) ? .76 : .5), placing ? 1 : .25);
    }

    static void login(ServerPlayer player) {
        actor = player.getUUID();
        var world = player.serverLevel();
        for (int x = -4; x <= 5; x++) for (int z = -5; z <= 4; z++) {
            world.setBlock(new BlockPos(x, 95, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            for (int y = 96; y < 102; y++) world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        world.setBlock(SQUARE.south(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        world.setBlock(SLAB.south(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        equip(player, new ItemStack(NativeCouplerBindings.SQUARE), SQUARE, true);
        System.out.println("[vandorlabs][builder-client] coupler-setup PASS");
    }

    static void apply(ServerPlayer player, int stage) {
        need(player.getUUID().equals(actor) && stage == next, "ordered fixture actor");
        switch (stage) {
            case 0 -> {
                need(player.getMainHandItem().isEmpty() && owner(player, SQUARE).getBlockState()
                        .is(NativeCouplerBindings.SQUARE), "ordinary square placement consumed its single item");
                equip(player, new ItemStack(NativeCouplerBindings.SLAB), SLAB, true);
            }
            case 1 -> {
                var state = owner(player, SLAB).getBlockState();
                need(player.getMainHandItem().isEmpty() && state.is(NativeCouplerBindings.SLAB)
                        && state.getValue(BlockStateProperties.HALF) == Half.TOP,
                        "ordinary slab placement consumed its single item and selected the upper half");
                equip(player, ItemStack.EMPTY, SQUARE, false);
            }
            case 2 -> {
                var square = owner(player, SQUARE);
                need(player.containerMenu == player.inventoryMenu && square.engaged()
                        && square.getRedstoneChannels().equals(BANK) && !square.inverted(),
                        "ordinary dialog committed channels, two polarity cycles and real Engage button");
                equip(player, new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()), SQUARE, false);
            }
            case 3 -> {
                need(player.containerMenu instanceof NativeCouplerMenu menu && menu.owner == owner(player, SQUARE),
                        "Configurizer opened the same dedicated dialog and placed owner");
                System.out.println("[vandorlabs][builder-client] coupler-controls PASS");
            }
            default -> throw new IllegalArgumentException("Coupler fixture stage");
        }
        player.serverLevel().setBlock(ack(stage), Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
        next++;
    }

    static void logout(Player player) {
        if (!player.getUUID().equals(actor)) return;
        need(next == 4, "completed authentic Coupler journey");
        System.out.println("[vandorlabs][builder-client] logout PASS");
    }

    private static NativeCouplerEntity owner(ServerPlayer player, BlockPos pos) {
        if (player.level().getBlockEntity(pos) instanceof NativeCouplerEntity coupler) return coupler;
        throw new AssertionError("No Coupler at " + pos);
    }

    private static void equip(ServerPlayer player, ItemStack stack, BlockPos pos, boolean placing) {
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.connection.send(new ClientboundSetCarriedItemPacket(0));
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, 0, stack.copy()));
        player.inventoryMenu.broadcastChanges();
        var origin = position(pos);
        var look = target(pos, placing);
        float pitch = (float)Math.toDegrees(Math.atan2(origin.y + player.getEyeHeight() - look.y, look.z - origin.z));
        player.connection.teleport(origin.x, origin.y, origin.z, 0, pitch);
    }

    private static void need(boolean ok, String why) {
        if (!ok) throw new AssertionError("Coupler player server stage " + next + ": " + why);
    }

    private NativeCouplerPlayerFixture() {}
}

package com.vandorlabs.neoforge.builders;

import com.vandorlabs.machines.BuilderOutputBounds;
import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.machines.SynthesisPresentation;
import com.vandorlabs.neoforge.rack.NativeBatteryModule;
import com.vandorlabs.neoforge.rack.NativeRackBindings;
import com.vandorlabs.rack.ModuleRackGeometry;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class NativeBuilderPartsClientFixture {
    static final String[] MACHINES = {"assembly_press", "enclosed_gantry"};
    private static final int[] RECIPES = {1, 5};
    private static UUID actor;
    private static int step;

    static BlockPos root(int machine) { return NativeBuilderClientFixture.ROOT.east(machine * 8); }
    static BlockPos ack(int operation) { return new BlockPos(-2, 95, operation - 2); }
    static ItemStack product(int machine) { return NativeBuilderProducts.partResult(MACHINES[machine], RECIPES[machine]); }

    static Vec3 aim(int machine, int action) {
        var root = root(machine);
        var spec = SynthesisPresentation.forMachine(MACHINES[machine]);
        var box = BuilderOutputBounds.forMachine(MACHINES[machine]);
        return switch (action) {
            case 0 -> new Vec3(root.getX() + .5, root.getY() + .5, 0);
            case 1 -> new Vec3(root.getX() + spec.rackX + 1
                    - (ModuleRackGeometry.BUTTON_MIN_X + ModuleRackGeometry.BUTTON_MAX_X) / 32,
                    root.getY() + spec.rackY + ModuleRackGeometry.slotCenterY(0) / 16, spec.rackFront());
            case 2 -> new Vec3(root.getX() + 1.2, root.getY() + 2.6, 1.5);
            default -> new Vec3(root.getX() + box.centerX(), root.getY() + box.centerY(), box.centerZ());
        };
    }

    static void login(ServerPlayer player) {
        actor = player.getUUID();
        var world = player.serverLevel();
        for (int x = -3; x <= 13; x++) for (int z = -5; z <= 5; z++) {
            world.setBlock(new BlockPos(x, 95, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 96; y <= 101; y++) world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int machine = 0; machine < MACHINES.length; machine++) {
            var root = root(machine);
            var core = NativeBuilderBindings.CORES.get(MACHINES[machine]);
            var design = core.design;
            var layout = new CoreLayout(design.width(), design.height(), design.depth(), Direction.NORTH.get3DDataValue(), 0, 0);
            world.setBlock(root, core.defaultBlockState(), 3);
            for (int x = 0; x < design.width(); x++) for (int y = 0; y < design.height(); y++) for (int z = 0; z < design.depth(); z++) {
                var at = root.offset(layout.x(x, z), y, layout.z(x, z));
                if (!at.equals(root) && layout.frame(x, y, z)) world.setBlock(at, NativeBuilderFormation.matter().defaultBlockState(), 3);
            }
        }
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        held(player, ItemStack.EMPTY, aim(0, 0));
        System.out.println("[vandorlabs][builder-client] part-setup PASS synchronized Survival/both authored frames");
    }

    static void apply(ServerPlayer player, int operation) {
        need(player.getUUID().equals(actor) && step == operation && operation < 8, "ordered focused actor");
        int machine = operation / 4;
        var tile = (NativeBuilderEntity) player.serverLevel().getBlockEntity(root(machine));
        need(tile != null && tile.formed() && tile.available() && tile.synthesisReady(), "ordinary click durably formed machine");
        switch (operation % 4) {
            case 0 -> {
                tile.selectDesign(RECIPES[machine]);
                var battery = new ItemStack(NativeRackBindings.BATTERY);
                NativeBatteryModule.transfer(battery, 10000, false, true);
                held(player, battery, aim(machine, 1));
            }
            case 1 -> {
                need(tile.batteryEnergy(0) == 10000 && player.getMainHandItem().isEmpty(), "rack hit at authored position");
                var input = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "vandorlabs", com.vandorlabs.machines.BuilderRecipeCatalog.input(MACHINES[machine])));
                tile.setItem(0, new ItemStack(input));
                held(player, ItemStack.EMPTY, aim(machine, 2));
            }
            case 2 -> {
                need(player.containerMenu == player.inventoryMenu && ItemStack.matches(tile.getItem(1), product(machine)),
                        "former Tower-only hit leaves product on bed");
                held(player, ItemStack.EMPTY, aim(machine, 3));
            }
            case 3 -> {
                need(player.containerMenu == player.inventoryMenu && tile.getItem(1).isEmpty() && tile.batteryEnergy(0) == 2000
                        && player.getInventory().items.stream().filter(s -> s.is(product(machine).getItem())).mapToInt(ItemStack::getCount).sum() == 1,
                        "authored bed click transfers the finished product exactly once after 8000 RF");
                if (machine == 0) held(player, ItemStack.EMPTY, aim(1, 0));
                else System.out.println("[vandorlabs][builder-client] part-ownership PASS two physical mounts/hit negatives/one finished item each");
            }
            default -> throw new IllegalArgumentException("Fixture action");
        }
        player.serverLevel().setBlock(ack(operation), Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
        step++;
    }

    static void logout(Player player) {
        if (!player.getUUID().equals(actor)) return;
        need(step == 8, "completed both machine journeys");
        System.out.println("[vandorlabs][builder-client] logout PASS");
    }

    private static void held(ServerPlayer player, ItemStack stack, Vec3 target) {
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.connection.send(new ClientboundSetCarriedItemPacket(0));
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, 0, stack.copy()));
        player.inventoryMenu.broadcastChanges();
        double y = 96, z = -2.5;
        float pitch = (float) Math.toDegrees(Math.atan2(y + player.getEyeHeight() - target.y, target.z - z));
        player.connection.teleport(target.x, y, z, 0, pitch);
    }

    private static void need(boolean ok, String why) {
        if (!ok) throw new AssertionError("Physical builder server step " + step + ": " + why);
    }

    private NativeBuilderPartsClientFixture() {}
}

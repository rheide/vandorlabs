package com.vandorlabs.neoforge.legacylanding;

import com.vandorlabs.neoforge.gear.NativeGearBindings;
import com.vandorlabs.neoforge.gear.NativeGearDefinition;
import com.vandorlabs.neoforge.gear.NativeGearItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeLegacyWheelInventoryFixture {
    static final boolean ENABLED = Boolean.getBoolean("vandorlabs.legacyWheelInventoryChecks");
    private static final BlockPos GROUND = new BlockPos(8, 110, 8);
    private static ServerPlayer actor;
    private static ItemEntity drop;
    private static int ticks;
    private static boolean passed;

    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer player)) return;
        actor = player;
        actor.setGameMode(GameType.SURVIVAL);
        actor.serverLevel().getChunkAt(GROUND);
        actor.serverLevel().setBlockAndUpdate(GROUND, Blocks.STONE.defaultBlockState());
        actor.connection.teleport(8.5, 111, 8.5, 0, 0);
        actor.getInventory().selected = 0;
        actor.getInventory().setItem(0, source());
        var opaque = source();
        opaque.set(DataComponents.CUSTOM_NAME, Component.literal("Opaque legacy owner"));
        actor.getInventory().setItem(1, opaque);
        for (int i = 2; i < actor.getInventory().items.size(); i++) actor.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        actor.serverLevel().setBlockAndUpdate(GROUND.east(3), Blocks.STONE.defaultBlockState());
        drop = new ItemEntity(actor.serverLevel(), 11.5, 111.25, 8.5, source());
        drop.setNeverPickUp();
        actor.serverLevel().addFreshEntity(drop);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || actor == null || passed || actor.isRemoved()) return;
        if (++ticks > 300) throw new AssertionError("Automatic legacy inventory conversion timed out");
        var inv = actor.getInventory();
        if (ticks == 60) {
            if (!ItemStack.matches(inv.getItem(0), source()) || !opaque(inv)) {
                throw new AssertionError("A full inventory partially converted a legacy stack");
            }
            inv.setItem(4, ItemStack.EMPTY);
            actor.inventoryMenu.broadcastChanges();
        }
        if (ticks <= 60 || !converted(inv)) return;
        if (drop.isRemoved() || !ItemStack.matches(drop.getItem(), source())) throw new AssertionError("Dropped owner changed");
        passed = true;
        System.out.println("[vandorlabs][legacy-wheel-inventory-player] server PASS capacity refusal, exact products and untouched opaque owners");
    }

    @SubscribeEvent public static void left(PlayerEvent.PlayerLoggedOutEvent event) {
        if (ENABLED && passed && event.getEntity() == actor) actor.getServer().halt(false);
    }

    static boolean converted(Inventory inv) {
        var arm = inv.getItem(0);
        var wheel = inv.getItem(4);
        if (!arm.is(NativeGearBindings.ITEMS.get(NativeGearDefinition.LARGE_EXTENDER.id)) || arm.getCount() != 6
                || !wheel.is(NativeGearBindings.ITEMS.get(NativeGearDefinition.LARGE_WHEEL.id)) || wheel.getCount() != 2
                || !opaque(inv)) return false;
        var tag = arm.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe().getCompound(NativeGearItem.SETTINGS);
        return tag.getInt("Trigger") == 2 && tag.getBoolean("Deployed") && tag.getBoolean("LegacyAnyChannels")
                && tag.getString("Channels").equals("51319, 51321");
    }

    static boolean opaque(Inventory inv) {
        var item = inv.getItem(1);
        return item.is(NativeLegacyLandingBindings.ITEMS.get(NativeLegacyLandingKind.WHEEL)) && item.getCount() == 2
                && item.getHoverName().getString().equals("Opaque legacy owner");
    }

    static ItemStack source() {
        var stack = new ItemStack(NativeLegacyLandingBindings.ITEMS.get(NativeLegacyLandingKind.WHEEL), 2);
        var choices = new CompoundTag();
        choices.putInt("ExtensionPixels", 40);
        choices.putInt("GearSize", 4);
        choices.putInt("RedstoneMode", 2);
        choices.putIntArray("RedstoneChannels", new int[]{51319, 51321});
        var root = new CompoundTag();
        root.put("BlockEntityTag", choices);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return stack;
    }
}

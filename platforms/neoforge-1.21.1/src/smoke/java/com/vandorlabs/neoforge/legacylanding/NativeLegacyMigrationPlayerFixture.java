package com.vandorlabs.neoforge.legacylanding;

import com.vandorlabs.neoforge.gear.NativeGearDefinition;
import com.vandorlabs.neoforge.gear.NativeGearEntity;
import com.vandorlabs.neoforge.transfer.NativeTransferHolds;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeLegacyMigrationPlayerFixture {
    static final BlockPos ROOT = new BlockPos(8, 96, 8);
    static final String COMMAND = "vandorlabs convert-landing-gear 8 96 8";
    static final String MARKER = "[vandorlabs][legacy-migration-player]";
    private static ServerPlayer player;
    private static NativeLegacyLandingEntity legacy;
    private static ItemStack retiredItem;
    private static ItemEntity dropped;
    private static int attempts, stage;

    static boolean enabled() {
        return Boolean.getBoolean("vandorlabs.legacyMigrationPlayerCheck");
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer actor)) return;
        player = actor;
        var world = actor.serverLevel();
        for (var pos : BlockPos.betweenClosed(ROOT.offset(-4, -1, -4), ROOT.offset(4, 3, 4))) {
            world.setBlockAndUpdate(pos, pos.getY() < ROOT.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        for (var pos : BlockPos.betweenClosed(ROOT.offset(-2, -1, -2), ROOT.offset(2, -1, 2))) {
            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        world.setBlockAndUpdate(ROOT.below(), Blocks.OBSIDIAN.defaultBlockState());
        actor.setGameMode(GameType.SURVIVAL);
        actor.getServer().getPlayerList().op(actor.getGameProfile());
        actor.connection.teleport(8.5, 96, 5.5, 0, 0);
        actor.getInventory().clearContent();
        actor.getInventory().selected = 0;
        actor.getInventory().setItem(8, new ItemStack(Items.DIAMOND, 5));
        actor.inventoryMenu.broadcastChanges();
        world.setBlockAndUpdate(ROOT.above(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(ROOT, NativeLegacyLandingBindings.BLOCKS.get(NativeLegacyLandingKind.WHEEL).defaultBlockState());
        legacy = NativeLegacyLandingBlock.owner(world, ROOT);
        require(legacy != null && legacy.configure(actor, new NativeLegacyLandingData.Configuration(0, ChannelList.EMPTY, 16, 0)),
            "regular wheel owner with complete mounting support");
        retiredItem = new ItemStack(NativeLegacyLandingBindings.ITEMS.get(NativeLegacyLandingKind.WHEEL));
        legacy.writeItem(retiredItem);
        actor.getInventory().setItem(7, retiredItem.copy());
        actor.inventoryMenu.broadcastChanges();
        dropped = new ItemEntity(world, 11.5, 98.5, 8.5, retiredItem.copy());
        dropped.setNeverPickUp();
        dropped.setNoGravity(true);
        dropped.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        require(world.addFreshEntity(dropped), "existing legacy item drop");
        world.noSave = true;
    }

    @SubscribeEvent
    public static void command(CommandEvent event) {
        if (player == null || !event.getParseResults().getReader().getString().equals(COMMAND)) return;
        require(event.getParseResults().getContext().getSource().getEntity() == player, "incoming packet belongs to the connected operator");
        attempts++;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (player == null) return;
        var world = player.serverLevel();
        if (stage == 0 && attempts == 1) {
            require(world.getBlockState(ROOT.below()).is(Blocks.OBSIDIAN)
                && NativeLegacyLandingBlock.owner(world, ROOT) == legacy && NativeLegacyMigration.pending(legacy) == null,
                "obstructed packet preserves the obstruction, original owner and absence of a pending operation");
            world.removeBlock(ROOT.below(), false);
            stage = 1;
        } else if (stage == 1 && attempts == 2) {
            require(world.noSave && NativeLegacyLandingBlock.owner(world, ROOT) == legacy && NativeLegacyMigration.pending(legacy) == null,
                "saving-disabled packet preserves the legacy owner and absence of a pending operation");
            world.noSave = false;
            stage = 2;
        } else if (stage == 2 && attempts == 3 && !NativeTransferHolds.active(world)
            && world.getBlockEntity(ROOT) instanceof NativeGearEntity gear && gear.assembly() != null) {
            require(gear.identity().equals(legacy.identity()) && gear.definition() == NativeGearDefinition.EXTENDER,
                "successful retry retains the physical owner's identity in the ordinary modular group");
            require(ItemStack.matches(player.getInventory().getItem(8), new ItemStack(Items.DIAMOND, 5))
                && ItemStack.matches(player.getInventory().getItem(7), retiredItem),
                "stationary conversion keeps player inventory and configured legacy gear intact");
            var drops = world.getEntitiesOfClass(ItemEntity.class, new AABB(ROOT).inflate(5));
            require(drops.size() == 1 && drops.getFirst() == dropped && ItemStack.matches(dropped.getItem(), retiredItem),
                "stationary conversion leaves the exact prior legacy drop and creates no new drops");
            stage = 3;
            System.out.println(MARKER + " server PASS packets/obstructionRefusal/savingRefusal/retry/exactInventoryAndDrop");
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != player) return;
        require(stage == 3, "connected conversion completed");
        player = null;
    }

    static void require(boolean valid, String detail) {
        if (!valid) throw new AssertionError(MARKER + " " + detail);
    }

    private NativeLegacyMigrationPlayerFixture() {}
}

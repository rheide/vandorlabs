package com.vandorlabs.neoforge.coupler;

import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.neoforge.vehicle.NativeVehicleCouplerChecks;
import com.vandorlabs.redstone.ChannelList;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class NativeCouplerChecks {
    public static void exercise(ServerLevel world, ServerPlayer actor, BlockPos head, ItemStack output,
                                BiConsumer<Boolean, String> check) {
        var oldPos = actor.position();
        var oldHeld = actor.getMainHandItem();
        var oldMenu = actor.containerMenu;
        var mate = head.east();
        var west = head.west();
        var east = mate.east();
        var power = head.north();
        boolean slab = output.is(NativeCouplerBindings.SLAB.asItem());
        var copy = output.copy();
        check.accept(output.is((slab ? NativeCouplerBindings.SLAB : NativeCouplerBindings.SQUARE).asItem()),
                "real Gantry output is a native Coupler block item");
        try {
            world.setBlock(west, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(east, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            actor.setPos(head.getX() + .5, head.getY(), head.getZ() - 2.5);
            place(actor, west, Direction.EAST, slab ? .75 : .5, output, check);
            place(actor, east, Direction.WEST, slab ? .75 : .5, copy, check);
            check.accept(world.getBlockEntity(head) instanceof NativeCouplerEntity
                            && world.getBlockEntity(mate) instanceof NativeCouplerEntity,
                    "both normal item placements build mounted Coupler owners");
            var first = (NativeCouplerEntity)world.getBlockEntity(head);
            var second = (NativeCouplerEntity)world.getBlockEntity(mate);
            // The fabricated items are placed during this server tick. Queue the same registration
            // as their deferred onLoad, which otherwise runs at the next block-entity tick.
            NativeRedstoneChannels.queueRegistration(first);
            NativeRedstoneChannels.queueRegistration(second);
            NativeRedstoneChannels.flush(world);
            check.accept(NativeCouplerBlock.facing(first.getBlockState()) == Direction.EAST
                            && NativeCouplerBlock.facing(second.getBlockState()) == Direction.WEST
                            && !first.engaged() && !second.engaged() && !first.paired(),
                    "Coupler heads face each other and initially release");

            double released = first.getBlockState().getCollisionShape(world, head).bounds().maxX;
            var firstMenu = new NativeCouplerMenu(NativeCouplerBindings.MENU, 76, first);
            var secondMenu = new NativeCouplerMenu(NativeCouplerBindings.MENU, 77, second);
            var bank = ChannelList.of(182, 204);
            actor.containerMenu = firstMenu;
            check.accept(!NativeCouplerBindings.apply(actor, new NativeCouplerBindings.Edit(head, 77, bank, true))
                            && !secondMenu.clickMenuButton(actor, 0),
                    "stale menu IDs cannot configure or engage a Coupler");
            check.accept(NativeCouplerBindings.apply(actor, new NativeCouplerBindings.Edit(head, 76, bank, false)),
                    "open authorized menu applies Coupler settings");
            check.accept(firstMenu.clickMenuButton(actor, 0) && first.engaged() && !first.paired(),
                    "authorized Engage latches exactly one Coupler head");
            check.accept(first.getBlockState().getCollisionShape(world, head).bounds().maxX > released,
                    "engaged Coupler collision extends the authored nose");
            actor.containerMenu = secondMenu;
            check.accept(secondMenu.clickMenuButton(actor, 0) && first.paired() && second.paired(),
                    "opposing engaged heads make a matching pair");
            NativeVehicleCouplerChecks.checkScan(world, head, true, check);
            if (slab) {
                var state = first.getBlockState();
                var bounds = state.getCollisionShape(world, head).bounds();
                check.accept(state.getValue(BlockStateProperties.HALF) == Half.TOP && bounds.minY >= .5
                                && !NativeCouplerBlock.pair(state, second.getBlockState()
                                .setValue(BlockStateProperties.HALF, Half.BOTTOM), Direction.EAST),
                        "upper slab preserves half-height geometry and refuses a lower mate");
            }

            actor.containerMenu = firstMenu;
            check.accept(firstMenu.clickMenuButton(actor, 0) && !first.engaged(), "manual Release opens both endpoints");
            NativeVehicleCouplerChecks.checkScan(world, head, false, check);
            world.setBlock(power, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            check.accept(first.engaged() && first.paired(), "real physical redstone edge expires the manual release");
            check.accept(firstMenu.clickMenuButton(actor, 0) && !first.engaged(),
                    "manual release persists while the input stays high");
            first.requestSample();
            check.accept(!first.engaged(), "sampling a steady signal does not expire the override");

            var saved = first.saveWithoutMetadata(world.registryAccess());
            var restored = new NativeCouplerEntity(head, first.getBlockState());
            restored.loadWithComponents(saved, world.registryAccess());
            var restoredTag = restored.saveWithoutMetadata(world.registryAccess());
            check.accept(!restored.inert() && restored.getRedstoneChannels().equals(bank)
                            && restoredTag.getBoolean("CouplerManualOverride")
                            && !restoredTag.getBoolean("CouplerManualRequested"),
                    "Coupler channels and manual release survive a save/read");
            var associated = saved.copy();
            associated.put("VehicleArmCouplerAssociation", new CompoundTag());
            restored.loadWithComponents(associated, world.registryAccess());
            check.accept(restored.inert() && restored.saveWithoutMetadata(world.registryAccess())
                            .contains("VehicleArmCouplerAssociation"),
                    "unsupported moving associations stay intact and inert");
        } finally {
            actor.containerMenu = oldMenu;
            actor.setItemInHand(InteractionHand.MAIN_HAND, oldHeld);
            actor.setPos(oldPos.x, oldPos.y, oldPos.z);
            for (var pos : new BlockPos[]{power, head, mate, west, east})
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void place(ServerPlayer actor, BlockPos backing, Direction face, double height, ItemStack stack,
                              BiConsumer<Boolean, String> check) {
        actor.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(new Vec3(backing.getX() + (face == Direction.EAST ? 1 : 0),
                backing.getY() + height, backing.getZ() + .5), face, backing, false);
        var used = stack.useOn(new UseOnContext(actor, InteractionHand.MAIN_HAND, hit));
        check.accept(used.consumesAction() && actor.getMainHandItem().isEmpty(), "Survival item use places one Coupler");
    }

    private NativeCouplerChecks() {}
}

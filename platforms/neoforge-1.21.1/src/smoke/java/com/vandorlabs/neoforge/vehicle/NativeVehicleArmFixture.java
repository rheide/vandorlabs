package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.gear.NativeGearBindings;
import com.vandorlabs.neoforge.gear.NativeGearDefinition;
import com.vandorlabs.neoforge.gear.NativeGearEntity;
import com.vandorlabs.neoforge.gear.NativeGearItem;
import com.vandorlabs.neoforge.gear.NativeGearSettings;
import com.vandorlabs.neoforge.ports.NativePortBindings;
import com.vandorlabs.neoforge.ports.NativePortBlock;
import com.vandorlabs.neoforge.ports.NativePortData;
import com.vandorlabs.neoforge.ports.NativePortKind;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.neoforge.redstone.NativeTransmitterItem;
import com.vandorlabs.ports.ResourcePortSettings;
import com.vandorlabs.redstone.ChannelList;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

/** A real moving Rotating Arm and two native fixed Mounts with independently owned endpoints. */
final class NativeVehicleArmFixture {
    private static final int ENERGY = 77, WATER = 131;
    private static final ChannelList ACTIVATE = ChannelList.of(721), MOTION = ChannelList.of(722);
    private final ItemStack activate = transmitter(ACTIVATE), motion = transmitter(MOTION);
    private final ServerLevel world;
    private final BlockPos root, rf, fluid, item;
    private int step, moving, galleryWait;
    private boolean placed;

    NativeVehicleArmFixture(ServerLevel world, BlockPos root) {
        this.world = world;
        this.root = root;
        rf = root.north(2);
        fluid = rf.east(2);
        item = rf.west(2);
        for (int x = -2; x <= 2; x++) world.setBlock(root.north().east(x), Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        port(rf, NativePortKind.POWER, Direction.UP, true, 9, ACTIVATE, nbt -> nbt.putInt("PortEnergy", ENERGY));
        port(fluid, NativePortKind.FLUID, Direction.UP, false, 17, ChannelList.EMPTY, nbt -> {});
        port(item, NativePortKind.MATTER, Direction.UP, true, 2, ACTIVATE, nbt -> {
            var slot = new CompoundTag();
            slot.putInt("Slot", 0);
            slot.put("Item", parcel().save(world.registryAccess()));
            var contents = new ListTag();
            contents.add(slot);
            nbt.put("PortItems", contents);
        });
    }

    boolean formed(ServerPlayer actor) {
        if (!placed) {
            place(actor, rf, NativeGearDefinition.ROTATING, ArmResource.POWER, false, MOTION);
            place(actor, fluid, NativeGearDefinition.MOUNT, ArmResource.FLUID, true, ChannelList.EMPTY);
            place(actor, item, NativeGearDefinition.MOUNT, ArmResource.ITEM, false, ChannelList.EMPTY);
            port(rf.above(2), NativePortKind.POWER, Direction.DOWN, false, 9, ChannelList.EMPTY, nbt -> {});
            port(fluid.above(2), NativePortKind.FLUID, Direction.DOWN, true, 17, ACTIVATE, nbt ->
                nbt.put("PortFluid", new FluidStack(Fluids.WATER, WATER).save(world.registryAccess())));
            port(item.above(2), NativePortKind.MATTER, Direction.DOWN, false, 2, ChannelList.EMPTY, nbt -> {});
            placed = true;
            return false;
        }
        for (var holder : new BlockPos[]{rf, fluid, item}) {
            if (!(world.getBlockEntity(holder.above()) instanceof NativeGearEntity gear) || !gear.canCarryWithVehicle()) return false;
        }
        return true;
    }

    boolean transferred(NativeGroundVehicle craft, ServerPlayer actor) {
        var arm = carried(craft, rf.above());
        int source = carried(craft, rf).getInt("PortEnergy"), sink = carried(craft, rf.above(2)).getInt("PortEnergy");
        int waterSource = water(carried(craft, fluid.above(2))), waterSink = water(carried(craft, fluid));
        int itemsSource = items(carried(craft, item)), itemsSink = items(carried(craft, item.above(2)));
        need(source + sink == ENERGY && waterSource + waterSink == WATER && itemsSource + itemsSink == parcel().getCount(),
            "conserved all owners at motion stage " + step);
        need(!arm.contains("NativeGearPayload") && !arm.contains("NativeGearStoredSections") && !arm.getBoolean("GearBlocked"),
            "moving empty arm obstructed: " + arm.getString("GearError") + " at " + arm.getDouble("GearProgress"));
        double progress = arm.getDouble("GearProgress");
        if (step == 0) {
            need(progress == 1 && sink == 0 && waterSink == 0 && itemsSink == 0, "gated source ports");
            if (NativeVehicleTransferChecks.armGallery() && ++galleryWait < 65) return false;
            galleryWait = 0;
            toggle(actor, motion);
            toggle(actor, activate);
            step++;
        } else if (step == 1) {
            need(sink == 0, "RF bypassed a swinging or folded arm: phase=" + progress + " received=" + sink);
            if (progress > 0 && progress < 1) moving++;
            if (progress == 0) {
                need(moving >= 20 && waterSink == WATER && itemsSink == parcel().getCount(),
                    "continued transfer at independent Mounts during real Arm motion");
                need(!craft.resources().armsReadyToPark(), "folded arm cannot write a partial parked native column");
                if (NativeVehicleTransferChecks.armGallery() && ++galleryWait < 40) return false;
                galleryWait = 0;
                toggle(actor, motion);
                step++;
            }
        } else {
            if (progress < 1) need(sink == 0, "RF bypassed redeploying Arm");
            if (progress == 1 && sink == ENERGY) {
                need(source == 0 && waterSource == 0 && itemsSource == 0 && craft.resources().armsReadyToPark(),
                    "settled cardinal contact delivered exact amounts");
                return true;
            }
        }
        return false;
    }

    void parked(BlockPos target) {
        var rfBase = rotate(rf, target);
        var arm = (NativeGearEntity)world.getBlockEntity(rfBase.above());
        need(arm != null && arm.controller() && arm.mount() == Direction.UP && arm.progress() == 1 && arm.target(),
            "rotated native Arm restored with exact settled owner");
        need(raw(rfBase).getInt("PortEnergy") == 0 && raw(rfBase.above(2)).getInt("PortEnergy") == ENERGY,
            "parked RF preserved");
        var fluidBase = rotate(fluid, target);
        need(water(raw(fluidBase)) == WATER && water(raw(fluidBase.above(2))) == 0, "parked reverse fluid preserved");
        var itemBase = rotate(item, target);
        need(items(raw(itemBase)) == 0 && items(raw(itemBase.above(2))) == parcel().getCount(), "parked item ownership and components preserved");
        for (var source : new BlockPos[]{rf, fluid, item}) {
            need(world.getBlockState(source).isAir() && world.getBlockState(source.above()).isAir()
                && world.getBlockState(source.above(2)).isAir(), "no abandoned physical owner");
        }
    }

    private BlockPos rotate(BlockPos at, BlockPos target) {
        return target.offset(at.subtract(root).rotate(Rotation.CLOCKWISE_90));
    }

    private static ItemStack transmitter(ChannelList bank) {
        var stack = NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(), 3);
        NativeTransmitterItem.configure(stack, bank);
        return stack;
    }

    private static void toggle(ServerPlayer actor, ItemStack stack) {
        need(NativeRedstoneChannels.manualToggle(actor, stack), "real handheld native transmitter registration");
    }

    private void place(ServerPlayer actor, BlockPos support, NativeGearDefinition definition, ArmResource mode,
                       boolean reverse, ChannelList channels) {
        var settings = NativeGearSettings.defaults().editableChoices();
        settings.putInt(ArmResource.CHOICE, mode.ordinal());
        settings.putBoolean(ArmResource.OUT, reverse);
        if (!channels.isEmpty()) {
            settings.putInt("Trigger", 1);
            settings.putString("Channels", channels.toString());
        }
        var tag = new CompoundTag();
        tag.put(NativeGearItem.SETTINGS, settings);
        var stack = new ItemStack(NativeGearBindings.ITEMS.get(definition.id));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        actor.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = Vec3.atCenterOf(support).add(0, .5, 0);
        need(stack.useOn(new net.minecraft.world.item.context.UseOnContext(actor, InteractionHand.MAIN_HAND,
            new BlockHitResult(hit, Direction.UP, support, false))).consumesAction(), "actual Arm item placement at " + support);
        actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private void port(BlockPos at, NativePortKind kind, Direction face, boolean output, int rate,
                      ChannelList gate, Consumer<CompoundTag> populate) {
        var state = NativePortBindings.BLOCKS.get(kind).defaultBlockState().setValue(NativePortBlock.FACING, face);
        world.setBlock(at, state, Block.UPDATE_ALL);
        var owner = world.getBlockEntity(at);
        var tag = owner.saveWithFullMetadata(world.registryAccess());
        var setup = ResourcePortSettings.placed(face.getOpposite().get3DDataValue())
            .withFace(face.getOpposite().get3DDataValue(), ResourcePortSettings.Face.DISABLED)
            .withFace(face.get3DDataValue(), output ? ResourcePortSettings.Face.OUTPUT : ResourcePortSettings.Face.INPUT)
            .withTransfer(rate, !output, ResourcePortSettings.OnTexture.TRANSFERRING);
        NativePortData.write(tag, new NativePortData.Configuration(setup, gate.isEmpty() ? 0 : 1, gate));
        populate.accept(tag);
        owner.loadWithComponents(tag, world.registryAccess());
        owner.setChanged();
    }

    private CompoundTag carried(NativeGroundVehicle craft, BlockPos at) {
        var local = at.subtract(root);
        return craft.snapshot().cells().stream().filter(cell -> cell.pos.equals(local)).findFirst().orElseThrow().tile();
    }

    private CompoundTag raw(BlockPos at) {
        return world.getBlockEntity(at).saveWithFullMetadata(world.registryAccess());
    }

    private int water(CompoundTag raw) {
        if (!raw.contains("PortFluid")) return 0;
        var water = FluidStack.parse(world.registryAccess(), raw.get("PortFluid")).orElseThrow();
        need(water.getFluid() == Fluids.WATER, "fluid identity preserved");
        return water.getAmount();
    }

    private int items(CompoundTag raw) {
        int count = 0;
        for (var row : raw.getList("PortItems", Tag.TAG_COMPOUND)) {
            var slot = ItemStack.parseOptional(world.registryAccess(), ((CompoundTag)row).getCompound("Item"));
            need(ItemStack.isSameItemSameComponents(slot, parcel()), "no inventory component change");
            count += slot.getCount();
        }
        return count;
    }

    private static ItemStack parcel() {
        var parcel = new ItemStack(Items.DIAMOND, 7);
        parcel.set(DataComponents.CUSTOM_NAME, Component.literal("Arm parcel"));
        return parcel;
    }

    private static void need(boolean value, String evidence) {
        if (!value) throw new AssertionError(evidence);
    }
}

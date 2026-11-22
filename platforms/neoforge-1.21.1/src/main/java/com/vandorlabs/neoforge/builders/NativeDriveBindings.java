package com.vandorlabs.neoforge.builders;

import net.minecraft.network.chat.Component;
import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD)
public final class NativeDriveBindings {
    private static final ResourceLocation ID = ResourceLocation.parse("vandorlabs:drive");
    public static MenuType<NativeDriveMenu> MENU;

    @SubscribeEvent public static void register(RegisterEvent event) {
        if (!NativeBuilderBindings.enabled()) return;
        event.register(Registries.MENU, helper -> {
            MENU = IMenuTypeExtension.create((window, inventory, data) -> {
                if (!data.readBoolean()) return new NativeDriveMenu(MENU, window, inventory, data.readBlockPos(), data.readBlockPos());
                var entity = inventory.player.level().getEntity(data.readVarInt());
                var target = data.readBlockPos();
                var initial = Edit.CODEC.decode(data);
                if (!(entity instanceof NativeGroundVehicle craft))
                    throw new IllegalArgumentException("Missing carried Maxwell Drive");
                return new NativeDriveMenu(MENU, window, craft, target, initial);
            });
            helper.register(ID, MENU);
        });
    }

    static boolean canUse(Player player, NativeBuilderEntity owner, BlockPos target) {
        return owner.drive() != null && owner.formed() && owner.canUse(player)
                && player.distanceToSqr(target.getCenter()) <= 64
                && NativeBuilderBindings.ownerAt(player.level(), target) == owner
                && player.level().mayInteract(player, target)
                && player.mayUseItemAt(target, Direction.UP, player.getMainHandItem());
    }

    static boolean open(Player player, NativeBuilderEntity owner, BlockPos target) {
        if (MENU == null || !canUse(player, owner, target)) return false;
        if (player instanceof ServerPlayer server) {
            server.connection.send(owner.getUpdatePacket());
            server.openMenu(new SimpleMenuProvider((window, inventory, actor) ->
                    new NativeDriveMenu(MENU, window, owner, target), owner.getBlockState().getBlock().getName()), data -> {
                data.writeBoolean(false);
                data.writeBlockPos(owner.getBlockPos());
                data.writeBlockPos(target);
            });
        }
        return true;
    }

    public static void openCarried(ServerPlayer server, NativeGroundVehicle craft,
                                   BlockPos target, Edit initial) {
        if (MENU == null || !craft.canUseEngine(server, initial.root(), target)) return;
        server.openMenu(new SimpleMenuProvider((window, inventory, actor) ->
            new NativeDriveMenu(MENU, window, craft, target, initial), Component.literal("Maxwell Drive")), data -> {
                data.writeBoolean(true);
                data.writeVarInt(craft.getId());
                data.writeBlockPos(target);
                Edit.CODEC.encode(data, initial);
            });
    }

    public record Edit(int window, BlockPos root, NativeDriveSettings config, ChannelList channels) implements CustomPacketPayload {
        private static final int HEADER_BYTES = 25;
        private static final Type<Edit> TYPE = new Type<>(ID);
        static final StreamCodec<RegistryFriendlyByteBuf, Edit> CODEC = new StreamCodec<>() {
            @Override public Edit decode(RegistryFriendlyByteBuf input) {
                int length = input.readableBytes();
                if (length < HEADER_BYTES || length > HEADER_BYTES + ChannelList.MAX_CHANNELS * 4) throw invalid();
                int window = input.readInt();
                var root = input.readBlockPos();
                int flags = input.readUnsignedByte(), particles = input.readUnsignedByte();
                int threshold = input.readUnsignedByte(), speed = input.readUnsignedByte();
                int display = input.readInt(), keyboard = input.readInt(), count = input.readUnsignedByte();
                if (flags > 15 || count > ChannelList.MAX_CHANNELS || input.readableBytes() != count * 4) throw invalid();
                var config = new NativeDriveSettings((flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0,
                        particles, threshold, display, keyboard, (flags & 8) != 0, speed);
                int[] channels = new int[count];
                for (int i = 0; i < count; i++) channels[i] = input.readInt();
                return new Edit(window, root, config, ChannelList.of(channels));
            }

            @Override public void encode(RegistryFriendlyByteBuf output, Edit edit) {
                var config = edit.config();
                int flags = (config.redstone() ? 1 : 0) | (config.inverted() ? 2 : 0)
                        | (config.levels() ? 4 : 0) | (config.animated() ? 8 : 0);
                output.writeInt(edit.window());
                output.writeBlockPos(edit.root());
                output.writeByte(flags).writeByte(config.particles()).writeByte(config.threshold()).writeByte(config.speed());
                output.writeInt(config.display()).writeInt(config.keyboard());
                output.writeByte(edit.channels().size());
                for (int i = 0; i < edit.channels().size(); i++) output.writeInt(edit.channels().get(i));
            }
        };

        public Edit {
            if (window < 0 || root == null || config == null || channels == null) throw invalid();
            root = root.immutable();
        }

        private static IllegalArgumentException invalid() { return new IllegalArgumentException("Drive settings packet"); }
        @Override public Type<Edit> type() { return TYPE; }
    }

    static boolean apply(ServerPlayer player, Edit edit) {
        if (!player.getServer().isSameThread() || !(player.containerMenu instanceof NativeDriveMenu menu)
                || menu.containerId != edit.window() || !menu.root().equals(edit.root()) || !menu.stillValid(player)) return false;
        return menu.craft == null ? menu.tile.configureDrive(edit.config(), edit.channels())
            : menu.craft.configureEngine(player, menu.target, edit);
    }

    @SubscribeEvent public static void payload(RegisterPayloadHandlersEvent event) {
        if (NativeBuilderBindings.enabled()) event.registrar("1").playToServer(Edit.TYPE, Edit.CODEC,
                (edit, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) apply(player, edit);
                }));
    }

    private NativeDriveBindings() {}
}

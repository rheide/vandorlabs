package com.vandorlabs.neoforge.coupler;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = "vandorlabs", bus = EventBusSubscriber.Bus.MOD)
public final class NativeCouplerBindings {
    private static final ResourceLocation SQUARE_ID = id("square_coupler");
    private static final ResourceLocation SLAB_ID = id("vehicle_coupler_slab");
    private static final ResourceLocation MENU_ID = id("vehicle_coupler");
    public static NativeCouplerBlock SQUARE, SLAB;
    public static BlockEntityType<NativeCouplerEntity> ENTITY;
    public static MenuType<NativeCouplerMenu> MENU;

    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("vandorlabs", name); }

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5, 6)
                .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion();
    }

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            SQUARE = new NativeCouplerBlock(properties());
            SLAB = new NativeCouplerBlock.Slab(properties());
            helper.register(SQUARE_ID, SQUARE);
            helper.register(SLAB_ID, SLAB);
        });
        event.register(Registries.ITEM, helper -> {
            helper.register(SQUARE_ID, new BlockItem(SQUARE, new Item.Properties()));
            helper.register(SLAB_ID, new BlockItem(SLAB, new Item.Properties()));
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            ENTITY = BlockEntityType.Builder.of(NativeCouplerEntity::new, SQUARE, SLAB).build(null);
            helper.register(MENU_ID, ENTITY);
        });
        event.register(Registries.MENU, helper -> {
            MENU = IMenuTypeExtension.create((window, inventory, buffer) ->
                    new NativeCouplerMenu(MENU, window, inventory.player, buffer.readBlockPos()));
            helper.register(MENU_ID, MENU);
        });
    }

    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(id("vandorlabs"))) {
            event.accept(SQUARE);
            event.accept(SLAB);
        }
    }

    public static boolean open(Player actor, Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof NativeCouplerEntity owner)
                || owner.inert() || !NativeConfigurationAccess.canEditDialog(actor, owner)) return false;
        if (actor instanceof ServerPlayer server) {
            server.connection.send(owner.getUpdatePacket());
            server.openMenu(new SimpleMenuProvider((window, inventory, player) -> new NativeCouplerMenu(MENU, window, owner),
                    owner.getBlockState().getBlock().getName()), buffer -> buffer.writeBlockPos(pos));
        }
        return true;
    }

    public record Edit(BlockPos pos, int window, ChannelList bank, boolean inverted) implements CustomPacketPayload {
        private static final Type<Edit> TYPE = new Type<>(id("vehicle_coupler_settings"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Edit> CODEC = new StreamCodec<>() {
            @Override public Edit decode(RegistryFriendlyByteBuf buffer) {
                if (buffer.readableBytes() < 14 || buffer.readableBytes() > 14 + 4 * ChannelList.MAX_CHANNELS)
                    throw new IllegalArgumentException("Coupler edit size");
                var pos = buffer.readBlockPos();
                int window = buffer.readInt(), flags = buffer.readUnsignedByte(), count = buffer.readUnsignedByte();
                if (flags > 1 || count > ChannelList.MAX_CHANNELS || buffer.readableBytes() != 4 * count)
                    throw new IllegalArgumentException("Coupler edit shape");
                int[] channels = new int[count];
                for (int i = 0; i < count; i++) channels[i] = buffer.readInt();
                return new Edit(pos, window, ChannelList.of(channels), flags == 1);
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Edit edit) {
                buffer.writeBlockPos(edit.pos);
                buffer.writeInt(edit.window);
                buffer.writeBoolean(edit.inverted);
                buffer.writeByte(edit.bank.size());
                for (int channel : edit.bank.toArray()) buffer.writeInt(channel);
            }
        };
        @Override public Type<Edit> type() { return TYPE; }
    }

    static boolean apply(ServerPlayer player, Edit edit) {
        return player.getServer().isSameThread() && player.containerMenu instanceof NativeCouplerMenu menu
                && menu.containerId == edit.window && menu.owner.getBlockPos().equals(edit.pos) && menu.stillValid(player)
                && menu.owner.configure(edit.bank, edit.inverted);
    }

    @SubscribeEvent public static void network(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Edit.TYPE, Edit.CODEC, (edit, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) apply(player, edit);
                }));
    }

    private NativeCouplerBindings() {}
}

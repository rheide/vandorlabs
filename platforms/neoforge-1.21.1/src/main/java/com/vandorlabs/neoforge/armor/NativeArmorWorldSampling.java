package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.NativeTriggerBlockEntity;
import com.vandorlabs.neoforge.door.NativeDoorLifecycle;
import com.vandorlabs.neoforge.door.NativeLargeDoorBlock;
import com.vandorlabs.neoforge.door.NativeLargeDoorLifecycle;
import com.vandorlabs.neoforge.door.NativeProgrammableDoorBlock;
import com.vandorlabs.neoforge.door.NativeProgrammableDoorEntity;
import com.vandorlabs.neoforge.ProgrammableBlock;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.light.NativeLightArtwork;
import com.vandorlabs.neoforge.light.NativeLightBlock;
import com.vandorlabs.neoforge.light.NativeLightEntity;
import com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock;
import com.vandorlabs.neoforge.shape.NativeFlatWallBlock;
import com.vandorlabs.neoforge.shape.NativeWallEntity;
import com.vandorlabs.neoforge.trapdoor.NativeTrapdoorBlock;
import com.vandorlabs.neoforge.trapdoor.NativeTrapdoorEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** A real shift-click starts a one-shot server session; the client only supplies baked artwork. */
@EventBusSubscriber(modid = "vandorlabs")
public final class NativeArmorWorldSampling {
    static final int MAX_SPRITE_LENGTH = 256;
    private static final int TICKET_TICKS = 60;
    private static final Map<UUID, Ticket> PENDING = new HashMap<>();
    private static long nextTicket;
    static volatile Consumer<Request> receive = request -> {};

    private record Ticket(long id, ServerLevel level, BlockHitResult hit, BlockState state, BlockEntity tile,
                          InteractionHand hand, ItemStack stack, ItemStack original, ItemStack tool,
                          int selected, String expectedSprite, long expires) {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        var actor = event.getEntity();
        var hand = armorHand(actor, event.getHand());
        if (hand == null || !actor.isShiftKeyDown() || actor.isSpectator()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));

        if (actor instanceof ServerPlayer player) {
            var request = begin(player, event.getHand(), event.getHitVec());
            if (request != null) PacketDistributor.sendToPlayer(player, request);
        }
    }

    static Request begin(ServerPlayer player, InteractionHand clicked, BlockHitResult hit) {
        var world = player.serverLevel();
        var identifier = player.getUUID();
        PENDING.remove(identifier);
        var hand = armorHand(player, clicked);
        var pos = hit.getBlockPos();
        if (hand == null || !player.isShiftKeyDown() || !canSample(player, world, pos)) return null;
        var state = world.getBlockState(pos);
        if (!supported(state)) {
            unavailable(player);
            return null;
        }

        var stack = player.getItemInHand(hand);
        var tool = hand != clicked ? player.getMainHandItem() : ItemStack.EMPTY;
        var tile = sampleOwner(world, state, pos);
        String material = verifiedMaterial(state, tile, hit.getDirection());
        if (usesRendererArtwork(state) && material == null) {
            unavailable(player);
            return null;
        }
        var ticket = new Ticket(++nextTicket, world, hit, state, tile, hand, stack, stack.copy(),
            tool, player.getInventory().selected, material, world.getGameTime() + TICKET_TICKS);
        PENDING.put(identifier, ticket);
        return new Request(ticket.id(), hit);
    }

    private static InteractionHand armorHand(Player player, InteractionHand clicked) {
        if (clicked == InteractionHand.MAIN_HAND && player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())
            && player.getOffhandItem().getItem() instanceof NativeArmorItem) return InteractionHand.OFF_HAND;
        return player.getItemInHand(clicked).getItem() instanceof NativeArmorItem ? clicked : null;
    }

    private static boolean canSample(ServerPlayer player, ServerLevel world, BlockPos pos) {
        return player.isAlive() && !player.isSpectator() && player.containerMenu == player.inventoryMenu
            && player.serverLevel() == world && world.hasChunkAt(pos) && world.mayInteract(player, pos)
            && player.canInteractWithBlock(pos, 1.0);
    }

    static boolean supported(BlockState state) {
        return usesRendererArtwork(state) || state.getRenderShape() == RenderShape.MODEL && !state.isAir() && !state.liquid()
            && (state.is(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get())
                || state.is(VandorLabsBootstrap.TRIGGER_BLOCK.get())
                || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft"));
    }

    static boolean complete(ServerPlayer player, Result result) {
        var pending = PENDING.get(player.getUUID());
        if (pending == null || pending.id() != result.id()) return false;
        PENDING.remove(player.getUUID());

        var world = pending.level();
        var pos = pending.hit().getBlockPos();
        if (world.getGameTime() > pending.expires() || !pos.equals(result.pos())
            || pending.hit().getDirection() != result.face() || !canSample(player, world, pos)
            || world.getBlockState(pos) != pending.state() || sampleOwner(world, pending.state(), pos) != pending.tile()
            || player.getInventory().selected != pending.selected()
            || player.getItemInHand(pending.hand()) != pending.stack()
            || !ItemStack.matches(pending.stack(), pending.original())
            || !pending.tool().isEmpty() && player.getMainHandItem() != pending.tool()) return false;

        if (pending.expectedSprite() != null && (!pending.expectedSprite().equals(result.sprite())
            || !Objects.equals(pending.expectedSprite(), verifiedMaterial(pending.state(), pending.tile(), result.face())))) {
            unavailable(player);
            return false;
        }
        if (sprite(result.sprite()) == null) {
            unavailable(player);
            return false;
        }
        NativeArmorItem.applyWorldSample(pending.stack(), result.sprite());
        NativeArmorItem.synchronizeHeld(player, pending.hand(), pending.stack());
        player.displayClientMessage(Component.translatable("item.vandorlabs.programmable_armor.sampled", result.sprite()), true);
        return true;
    }

    static boolean usesRendererArtwork(BlockState state) {
        var block = state.getBlock();
        return block instanceof NativeLightBlock || block instanceof NativeProgrammableDoorBlock
                || block instanceof NativeLargeDoorBlock || block instanceof NativeTrapdoorBlock
                || block instanceof NativeFlatWallBlock || block instanceof NativeDiagonalWallBlock diagonal && !diagonal.isGlass();
    }

    static BlockEntity sampleOwner(Level world, BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof NativeProgrammableDoorBlock) return NativeDoorLifecycle.root(world, pos);
        if (state.getBlock() instanceof NativeLargeDoorBlock) {
            var owner = NativeLargeDoorLifecycle.root(world, pos);
            return owner != null && NativeLargeDoorLifecycle.complete(world, owner) ? owner : null;
        }
        return world.getBlockEntity(pos);
    }

    static String verifiedMaterial(BlockState state, BlockEntity tile, Direction face) {
        int choice;
        if (state.getBlock() instanceof NativeLightBlock && tile instanceof NativeLightEntity light && light.available()) {
            var visual = light.visual();
            choice = visual.facing() == face
                    ? NativeLightArtwork.face(visual.settings().light(), visual.on() && visual.level() > 0)
                    : visual.housing().mainTexture;
        } else if (tile instanceof NativeProgrammableDoorEntity door && !door.placementRecovery()) {
            var settings = door.settings();
            choice = settings.faceTexture() < 0 ? NativeArmorInventorySources.doorArtwork(settings.door()) : settings.faceTexture();
        } else if (state.getBlock() instanceof NativeTrapdoorBlock
                && tile instanceof NativeTrapdoorEntity trapdoor && !trapdoor.inert()) {
            choice = trapdoor.settings().choices().texture;
        } else if (tile instanceof NativeWallEntity wall && !wall.isGlass()) {
            choice = wall.settings().mainTexture;
        } else if ((state.is(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get()) || state.is(VandorLabsBootstrap.TRIGGER_BLOCK.get()))
                && tile instanceof ProgrammableBlockEntity cube) {
            var settings = cube instanceof NativeTriggerBlockEntity trigger ? trigger.modelSettings() : cube.settings();
            int local = ProgrammableBlock.localTextureFace(face, state.getValue(ProgrammableBlock.FACING));
            choice = settings.facesEnabled && settings.face(local) >= 0 ? settings.face(local) : settings.mainTexture;
        } else {
            return null;
        }
        if (choice < 0) return null;
        int stored = ModernFinishCatalog.storedChoice(choice);
        return stored < ModernFinishCatalog.ENTRIES.size() ? ModernFinishCatalog.option(stored).texture() : null;
    }

    static ResourceLocation sprite(String value) {
        if (value == null || value.length() > MAX_SPRITE_LENGTH || value.indexOf(':') <= 0 || value.contains("..")) return null;
        var id = ResourceLocation.tryParse(value);
        return id == null || id.getPath().isEmpty() || id.getPath().equals("missingno") ? null : id;
    }

    private static void unavailable(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("item.vandorlabs.programmable_armor.no_sample"), true);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PENDING.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void expire(ServerTickEvent.Post event) {
        if (!PENDING.isEmpty()) PENDING.values().removeIf(ticket -> ticket.expires() < ticket.level().getGameTime());
    }

    record Request(long id, BlockHitResult hit) implements CustomPacketPayload {
        static final Type<Request> TYPE = new Type<>(idFor("programmable_armor_sample_face"));
        static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
            (buffer, request) -> {
                buffer.writeVarLong(request.id());
                buffer.writeBlockHitResult(request.hit());
            }, buffer -> new Request(buffer.readVarLong(), buffer.readBlockHitResult()));

        @Override public Type<Request> type() { return TYPE; }
    }

    record Result(long id, BlockPos pos, Direction face, String sprite) implements CustomPacketPayload {
        static final Type<Result> TYPE = new Type<>(idFor("programmable_armor_sample_result"));
        static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Result::id, BlockPos.STREAM_CODEC, Result::pos, Direction.STREAM_CODEC, Result::face,
            ByteBufCodecs.stringUtf8(MAX_SPRITE_LENGTH), Result::sprite, Result::new);

        @Override public Type<Result> type() { return TYPE; }
    }

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Request.TYPE, Request.CODEC, (request, context) ->
            context.enqueueWork(() -> receive.accept(request)));
        registrar.playToServer(Result.TYPE, Result.CODEC, (result, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) complete(player, result);
        }));
    }

    private static ResourceLocation idFor(String path) {
        return ResourceLocation.fromNamespaceAndPath("vandorlabs", path);
    }

    private NativeArmorWorldSampling() {}
}

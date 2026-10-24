package com.vandorlabs.neoforge;

import com.vandorlabs.ui.ProgrammableBlockDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Fixed-size settings message: position + main + layout + enabled + six choices. */
public record FinishPayload(BlockPos pos, int main, int layout, boolean enabled, int[] faces) implements CustomPacketPayload {
    public static final Type<FinishPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs", "finish"));
    public FinishPayload { faces=faces.clone(); if(faces.length!=6)throw new IllegalArgumentException("Six face choices required"); }
    @Override public int[] faces() { return faces.clone(); }
    public static final StreamCodec<RegistryFriendlyByteBuf, FinishPayload> CODEC = new StreamCodec<>() {
        @Override public FinishPayload decode(RegistryFriendlyByteBuf buffer) {
            BlockPos pos=buffer.readBlockPos();int main=buffer.readInt(),layout=buffer.readUnsignedByte();boolean enabled=buffer.readBoolean();
            int[] faces=new int[6];for(int i=0;i<6;i++)faces[i]=buffer.readInt();
            return new FinishPayload(pos,main,layout,enabled,faces);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, FinishPayload value) {
            buffer.writeBlockPos(value.pos);buffer.writeInt(value.main);buffer.writeByte(value.layout);buffer.writeBoolean(value.enabled);
            for(int choice:value.faces)buffer.writeInt(choice);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) { event.registrar("1").playToServer(TYPE,CODEC,FinishPayload::handle); }
    public boolean valid() {
        if(!ModernFinishCatalog.validChoice(main)||layout<0||layout>2)return false;
        for(int face:faces)if(face!=-1&&!ModernFinishCatalog.validChoice(face))return false;
        return true;
    }
    static boolean apply(ServerPlayer player, FinishPayload packet) {
        if (!packet.valid() || !(player.containerMenu instanceof ProgrammableMenu menu) || !menu.stillValid(player) || !menu.tile.getBlockPos().equals(packet.pos)) return false;
        if (player.distanceToSqr(packet.pos.getCenter()) > 64 || !player.serverLevel().hasChunkAt(packet.pos)
                || !player.serverLevel().mayInteract(player,packet.pos)) return false;
        if (!player.serverLevel().getBlockState(packet.pos).is(VandorLabsModern.PROGRAMMABLE_BLOCK.get())
                || !(player.serverLevel().getBlockEntity(packet.pos) instanceof ProgrammableBlockEntity tile) || tile != menu.tile) return false;
        tile.apply(new ProgrammableBlockDialog.State(packet.main,packet.layout,packet.enabled,packet.faces,-1));
        return true;
    }
    private static void handle(FinishPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> { if(context.player() instanceof ServerPlayer player)apply(player,packet); });
    }
}

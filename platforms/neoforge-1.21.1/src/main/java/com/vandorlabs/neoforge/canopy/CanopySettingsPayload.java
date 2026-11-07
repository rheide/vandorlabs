package com.vandorlabs.neoforge.canopy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Opt-in dedicated Canopy edit and request-correlated authoritative acknowledgement. */
public record CanopySettingsPayload(NativeCanopyEdit edit) implements CustomPacketPayload {
    public CanopySettingsPayload {java.util.Objects.requireNonNull(edit);}
    public static final Type<CanopySettingsPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:canopy_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CanopySettingsPayload> CODEC=new StreamCodec<>(){
        public CanopySettingsPayload decode(RegistryFriendlyByteBuf b){return new CanopySettingsPayload(NativeCanopyEdit.decode(b));}
        public void encode(RegistryFriendlyByteBuf b,CanopySettingsPayload value){value.edit.encode(b);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public record Result(NativeCanopyEdit authoritative,boolean applied) implements CustomPacketPayload {
        public Result {java.util.Objects.requireNonNull(authoritative);}
        public static final Type<Result> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:canopy_settings_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Result> CODEC=new StreamCodec<>(){
            public Result decode(RegistryFriendlyByteBuf b){int flag=b.readUnsignedByte();if(flag>1)throw new IllegalArgumentException("Canopy result flag");return new Result(NativeCanopyEdit.decode(b),flag!=0);}
            public void encode(RegistryFriendlyByteBuf b,Result value){b.writeBoolean(value.applied);value.authoritative.encode(b);}
        };
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private static java.util.function.Consumer<Result> RESULTS=value->{};
    /** Client binding supplies only the currently open screen dispatcher. */
    public static void bindResults(java.util.function.Consumer<Result> receiver){RESULTS=java.util.Objects.requireNonNull(receiver);}
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("1");
        registrar.playToServer(TYPE,CODEC,(packet,context)->context.enqueueWork(()->{
            if(!(context.player() instanceof ServerPlayer player)||!(player.containerMenu instanceof NativeCanopyMenu menu))return;
            var edit=packet.edit;
            if(menu.containerId!=edit.window()||!menu.tile.getBlockPos().equals(edit.pos())||!menu.stillValid(player)
                ||!com.vandorlabs.neoforge.NativeConfigurationAccess.canConfigure(player))return;
            boolean applied=menu.apply(player,edit.window(),edit.pos(),edit.settings());
            PacketDistributor.sendToPlayer(player,new Result(new NativeCanopyEdit(edit.pos(),edit.window(),edit.request(),menu.tile.settings()),applied));
        }));
        registrar.playToClient(Result.TYPE,Result.CODEC,(packet,context)->context.enqueueWork(()->RESULTS.accept(packet)));
    }
}

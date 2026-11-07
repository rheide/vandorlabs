package com.vandorlabs.neoforge.ramp;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Opt-in bounded editor transport; admission/ownership remains the injected real command service. */
public record RampSettingsPayload(NativeRampEdit edit) implements CustomPacketPayload {
 public RampSettingsPayload{java.util.Objects.requireNonNull(edit);}
 public static final Type<RampSettingsPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:ramp_settings"));
 public static final StreamCodec<RegistryFriendlyByteBuf,RampSettingsPayload> CODEC=new StreamCodec<>(){public RampSettingsPayload decode(RegistryFriendlyByteBuf b){return new RampSettingsPayload(NativeRampEdit.decode(b));}public void encode(RegistryFriendlyByteBuf b,RampSettingsPayload value){value.edit.encode(b);}};
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public record Result(NativeRampEdit authoritative,NativeRampMenu.Status status,boolean admitted,boolean choicesCommitted,boolean actionPending,boolean activationRefused) implements CustomPacketPayload {
  public Result{java.util.Objects.requireNonNull(authoritative);java.util.Objects.requireNonNull(status);}
  public static final Type<Result> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:ramp_settings_result"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Result> CODEC=new StreamCodec<>(){
   public Result decode(RegistryFriendlyByteBuf b){int flags=b.readUnsignedByte();if(flags>127)throw new IllegalArgumentException("Ramp result flags");long revision=b.readLong();String text=b.readUtf(256);var status=new NativeRampMenu.Status(text,(flags&16)!=0,(flags&32)!=0,(flags&64)!=0,revision);return new Result(NativeRampEdit.decode(b),status,(flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0);}
   public void encode(RegistryFriendlyByteBuf b,Result value){var s=value.status;b.writeByte((value.admitted?1:0)|(value.choicesCommitted?2:0)|(value.actionPending?4:0)|(value.activationRefused?8:0)|(s.error()?16:0)|(s.pending()?32:0)|(s.moving()?64:0));b.writeLong(s.revision());b.writeUtf(s.text(),256);value.authoritative.encode(b);}
  };
  @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 private static java.util.function.Consumer<Result> RESULTS=result->{};
 public static void bindResults(java.util.function.Consumer<Result> receiver){RESULTS=java.util.Objects.requireNonNull(receiver);}
 public static Result apply(ServerPlayer player,NativeRampEdit edit){if(!(player.containerMenu instanceof NativeRampMenu menu))return null;var outcome=menu.apply(player,edit);if(outcome==null)return null;var display=new NativeRampDraft(outcome.choices(),menu.scanFacing,menu.horizontalFacing);return new Result(new NativeRampEdit(edit.pos(),edit.window(),edit.request(),display.settings()),outcome.status(),outcome.admitted(),outcome.choicesCommitted(),outcome.actionPending(),outcome.activationRefused());}
 public static void register(RegisterPayloadHandlersEvent event){var registrar=event.registrar("1");registrar.playToServer(TYPE,CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player){var result=apply(player,packet.edit);if(result!=null)PacketDistributor.sendToPlayer(player,result);}}));registrar.playToClient(Result.TYPE,Result.CODEC,(packet,context)->context.enqueueWork(()->RESULTS.accept(packet)));}
}

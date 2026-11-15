package com.vandorlabs.neoforge.gear;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Bounded choice-only C2S request. owner is the session member, not its mutable controller. All world reads and authorization execute on the server thread. */
public record NativeGearSettingsPayload(int window,BlockPos owner,NativeGearSettings settings,boolean deployed,int request) implements CustomPacketPayload {
    public static final Type<NativeGearSettingsPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:gear_settings"));
    public NativeGearSettingsPayload(int window,BlockPos owner,NativeGearSettings settings,boolean deployed){this(window,owner,settings,deployed,0);}
    public NativeGearSettingsPayload {owner=owner.immutable();java.util.Objects.requireNonNull(settings);}
    public static final StreamCodec<RegistryFriendlyByteBuf,NativeGearSettingsPayload> CODEC=new StreamCodec<>(){
        public NativeGearSettingsPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()<48||b.readableBytes()>48+ChannelList.MAX_TEXT_LENGTH*3+5)throw new IllegalArgumentException("Invalid arm settings length");int window=b.readInt();var owner=b.readBlockPos();int trigger=b.readUnsignedByte(),direction=b.readUnsignedByte(),foot=b.readUnsignedByte(),flags=b.readUnsignedShort(),range=b.readUnsignedByte(),height=b.readUnsignedByte();if((flags&~2047)!=0||(flags&128)!=0&&(flags&8)==0||(flags&768)!=0&&(flags&1024)==0)throw new IllegalArgumentException("Invalid arm flags");double start=b.readDouble(),end=b.readDouble(),extension=b.readDouble();var channels=ChannelList.parse(b.readUtf(ChannelList.MAX_TEXT_LENGTH));int request=b.readInt();if(b.isReadable())throw new IllegalArgumentException("Trailing arm settings bytes");return new NativeGearSettingsPayload(window,owner,new NativeGearSettings(trigger,direction,channels,(flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)==0?com.vandorlabs.gear.core.ArmHinge.Mode.SINGLE:(flags&128)!=0?com.vandorlabs.gear.core.ArmHinge.Mode.DOUBLE:com.vandorlabs.gear.core.ArmHinge.Mode.LEGACY_CENTER,(flags&16)!=0,range,(flags&32)!=0,height,start,end,extension,foot,(flags&1024)!=0?(flags>>8)&3:-1),(flags&64)!=0,request);}
        public void encode(RegistryFriendlyByteBuf b,NativeGearSettingsPayload p){var s=p.settings;b.writeInt(p.window);b.writeBlockPos(p.owner);b.writeByte(s.trigger());b.writeByte(s.direction());b.writeByte(s.footFacing());b.writeShort((s.legacyAnyChannels()?1:0)|(s.pushBlocks()?2:0)|(s.rotatePayload()?4:0)|(s.middleHinge()?8:0)|(s.join()?16:0)|(s.carryLoad()?32:0)|(p.deployed?64:0)|(s.hingeMode()==com.vandorlabs.gear.core.ArmHinge.Mode.DOUBLE?128:0)|(s.kinkRaw()<0?0:1024|(s.kinkRaw()<<8)));b.writeByte(s.joinRange());b.writeByte(s.loadHeight());b.writeDouble(s.startAngle());b.writeDouble(s.activeAngle());b.writeDouble(s.activeExtension());b.writeUtf(s.channels().toString(),ChannelList.MAX_TEXT_LENGTH);b.writeInt(p.request);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static boolean apply(ServerPlayer actor,NativeGearSettingsPayload packet){return actor.getServer().isSameThread()&&actor.containerMenu instanceof NativeGearMenu menu&&menu.apply(actor,packet.window,packet.owner,packet.settings,packet.deployed);}
    public record Result(NativeGearSettingsPayload authoritative,boolean applied,BlockPos controller,java.util.UUID controllerIdentity) implements CustomPacketPayload {
        public static final Type<Result> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:gear_settings_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Result> CODEC=new StreamCodec<>(){public Result decode(RegistryFriendlyByteBuf b){boolean applied=b.readBoolean();var owner=b.readBlockPos();var identity=b.readUUID();return new Result(NativeGearSettingsPayload.CODEC.decode(b),applied,owner,identity);}public void encode(RegistryFriendlyByteBuf b,Result p){b.writeBoolean(p.applied);b.writeBlockPos(p.controller);b.writeUUID(p.controllerIdentity);NativeGearSettingsPayload.CODEC.encode(b,p.authoritative);}};
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private static java.util.function.Consumer<Result> results=ignored->{};
    public static void bindResults(java.util.function.Consumer<Result> listener){results=java.util.Objects.requireNonNull(listener);}
    public static void register(RegisterPayloadHandlersEvent event){if(NativeGearBindings.enabled()){var registrar=event.registrar("1");registrar.playToServer(TYPE,CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor){boolean applied=apply(actor,packet);if(actor.containerMenu instanceof NativeGearMenu menu&&menu.containerId==packet.window&&menu.selectedPosition().equals(packet.owner)&&menu.stillValid(actor)){var tile=menu.tile;net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(actor,new Result(new NativeGearSettingsPayload(menu.containerId,menu.selectedPosition(),tile.settings(),tile.target(),packet.request),applied,tile.getBlockPos(),tile.identity()));}}}));registrar.playToClient(Result.TYPE,Result.CODEC,(result,context)->context.enqueueWork(()->results.accept(result)));}}
}

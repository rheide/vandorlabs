package com.vandorlabs.neoforge.seating;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
/** Fixed16-byte native chair request; all authorizations are rechecked against the physical owner. */
public record NativeSeatPayload(BlockPos pos,int window,int style,int height,int angle,boolean join) implements CustomPacketPayload {
    public NativeSeatPayload{if(pos==null||style<0||style>4||height<0||height>3||angle<0||angle>7)throw new IllegalArgumentException("Invalid seat choices");}
    public static final Type<NativeSeatPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","chair_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NativeSeatPayload> CODEC=new StreamCodec<>(){
        public NativeSeatPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=16)throw new IllegalArgumentException("Invalid seat request length");var pos=b.readBlockPos();int window=b.readInt(),style=b.readUnsignedByte(),height=b.readUnsignedByte(),angle=b.readUnsignedByte(),join=b.readUnsignedByte();if(join>1)throw new IllegalArgumentException("Invalid seat join");return new NativeSeatPayload(pos,window,style,height,angle,join==1);}
        public void encode(RegistryFriendlyByteBuf b,NativeSeatPayload p){b.writeBlockPos(p.pos);b.writeInt(p.window);b.writeByte(p.style);b.writeByte(p.height);b.writeByte(p.angle);b.writeBoolean(p.join);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static boolean apply(ServerPlayer actor,NativeSeatPayload p){var menu=NativeSeatMenu.editing(actor,p.pos,p.window);return menu!=null&&menu.tile.kind()!=NativeSeatKind.COCKPIT&&menu.tile.configure(actor,p.style,p.height,p.angle,p.join);}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer actor)apply(actor,p);}));}
}

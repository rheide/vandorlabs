package com.vandorlabs.neoforge.programmer;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Original bounded editor body. Vehicle operation indices remain reserved and unavailable. */
public record ProgrammerSettingsPayload(int window,int operation,NativeProgrammerDraft draft) implements CustomPacketPayload {
    public static final int MAX_BYTES=24+40*4+8*512*4;
    public ProgrammerSettingsPayload{Objects.requireNonNull(draft);if(operation<0||operation>2||draft.vehicle())throw new IllegalArgumentException("Unavailable Programmer operation");}
    public static ProgrammerSettingsPayload copy(int window){return new ProgrammerSettingsPayload(window,2,NativeProgrammerDraft.empty());}
    public static final Type<ProgrammerSettingsPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","programmer_settings"));
    private static String read(RegistryFriendlyByteBuf b,int limit){if(b.readableBytes()<2)throw new IllegalArgumentException("Truncated Programmer text length");int bytes=b.readUnsignedShort();if(bytes>limit*4||bytes>b.readableBytes())throw new IllegalArgumentException("Invalid Programmer text length");String text=b.toString(b.readerIndex(),bytes,StandardCharsets.UTF_8);b.skipBytes(bytes);if(text.length()>limit)throw new IllegalArgumentException("Programmer text too long");return text;}
    private static void write(RegistryFriendlyByteBuf b,String text){byte[] bytes=text.getBytes(StandardCharsets.UTF_8);b.writeShort(bytes.length);b.writeBytes(bytes);}
    public static final StreamCodec<RegistryFriendlyByteBuf,ProgrammerSettingsPayload> CODEC=new StreamCodec<>(){
        public ProgrammerSettingsPayload decode(RegistryFriendlyByteBuf b){
            if(b.readableBytes()<24||b.readableBytes()>MAX_BYTES)throw new IllegalArgumentException("Programmer body bounds");int window=b.readInt(),operation=b.readUnsignedByte();if(operation>2)throw new IllegalArgumentException("Unavailable Programmer operation");boolean journal=b.readBoolean();String title=read(b,40);var pages=new ArrayList<String>(8);for(int page=0;page<8;page++)pages.add(read(b,512));if(b.isReadable())throw new IllegalArgumentException("Trailing Programmer data");return new ProgrammerSettingsPayload(window,operation,new NativeProgrammerDraft(false,journal,title,pages));
        }
        public void encode(RegistryFriendlyByteBuf b,ProgrammerSettingsPayload edit){b.writeInt(edit.window);b.writeByte(edit.operation);b.writeBoolean(edit.draft.journal());write(b,edit.draft.title());for(String page:edit.draft.pages())write(b,page);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(edit,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)NativeProgrammerMenu.apply(player,edit);}));}
}

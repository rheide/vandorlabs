package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.motion.PlatformMotionPolicy;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Optional S2C correction. Registration/client smoothing belongs to the owning runtime adapter. */
public record NativeRampMotionPayload(ResourceLocation dimension,long sequence,double x,double z,double before,double after,double dx,double dz) implements CustomPacketPayload {
    public NativeRampMotionPayload{Objects.requireNonNull(dimension);}
    public static final Type<NativeRampMotionPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:ramp_support"));
    private static final int MAX_BYTES=3*32767+5+56;
    public static final StreamCodec<RegistryFriendlyByteBuf,NativeRampMotionPayload> CODEC=new StreamCodec<>(){
        public NativeRampMotionPayload decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()<57||buffer.readableBytes()>MAX_BYTES)throw new IllegalArgumentException("Ramp support length");var dimension=ResourceLocation.parse(buffer.readUtf(32767));if(buffer.readableBytes()!=56)throw new IllegalArgumentException("Ramp support fields");return new NativeRampMotionPayload(dimension,buffer.readLong(),buffer.readDouble(),buffer.readDouble(),buffer.readDouble(),buffer.readDouble(),buffer.readDouble(),buffer.readDouble());}
        public void encode(RegistryFriendlyByteBuf buffer,NativeRampMotionPayload payload){buffer.writeUtf(payload.dimension.toString(),32767);buffer.writeLong(payload.sequence);buffer.writeDouble(payload.x);buffer.writeDouble(payload.z);buffer.writeDouble(payload.before);buffer.writeDouble(payload.after);buffer.writeDouble(payload.dx);buffer.writeDouble(payload.dz);}
    };
    private static final class Sequence {long value;Sequence(long value){this.value=value;}}
    private static final NativeEntityMemo<Sequence> RECEIVED=new NativeEntityMemo<>(8192);
    public double verticalDisplacement(){return after-before;}
    /** Main client thread only; a newer refused physical correction still consumes its sequence. */
    public boolean apply(Player player){
        if(player==null||!player.level().isClientSide)return false;NativeRampRiders.requireThread(player.level());
        if(!PlatformMotionPolicy.header(player.level().dimension().location().equals(dimension),sequence,x,z,before,after))return false;
        var previous=RECEIVED.get(player);if(!PlatformMotionPolicy.ordered(sequence,previous!=null,previous==null?0:previous.value))return false;
        if(sequence>0){if(previous==null)RECEIVED.put(player,new Sequence(sequence));else previous.value=sequence;}
        double feet=player.getBoundingBox().minY;if(!PlatformMotionPolicy.physical(NativeRampRiders.available(player),player.getDeltaMovement().y,player.getX(),player.getZ(),feet,x,z,before,after,dx,dz))return false;
        player.setPos(player.getX()+dx,player.getY()+after-feet,player.getZ()+dz);player.setOnGround(true);player.fallDistance=0;var velocity=player.getDeltaMovement();player.setDeltaMovement(velocity.x,0,velocity.z);NativeRampRiders.supported(player);return true;
    }
    static void unload(net.minecraft.world.level.Level level){RECEIVED.clearWorld(level);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}

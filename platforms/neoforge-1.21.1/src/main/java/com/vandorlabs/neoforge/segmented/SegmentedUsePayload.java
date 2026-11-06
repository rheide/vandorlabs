package com.vandorlabs.neoforge.segmented;

import com.vandorlabs.persistence.LegacyBlockPosition;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

/** Released 64-byte moving-panel intent; pose claims require the current identity and a recent stroke. */
public record SegmentedUsePayload(BlockPos pos,Vec3 point,UUID identity,long sequence,double pose) implements CustomPacketPayload {
    public static final Type<SegmentedUsePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","segmented_use"));
    public SegmentedUsePayload {
        if(pos==null||identity==null||!finite(point)||sequence<0||!Double.isFinite(pose)||pose<0||pose>1)
            throw new IllegalArgumentException("Invalid Segmented Door interaction");
        pos=pos.immutable();
    }
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    public static final StreamCodec<RegistryFriendlyByteBuf,SegmentedUsePayload> CODEC=new StreamCodec<>(){
        public SegmentedUsePayload decode(RegistryFriendlyByteBuf b){
            if(b.readableBytes()!=64)throw new IllegalArgumentException("Invalid Segmented Door interaction length");
            long packed=b.readLong();return new SegmentedUsePayload(new BlockPos(LegacyBlockPosition.x(packed),LegacyBlockPosition.y(packed),LegacyBlockPosition.z(packed)),
                    new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readUUID(),b.readLong(),b.readDouble());
        }
        public void encode(RegistryFriendlyByteBuf b,SegmentedUsePayload p){b.writeLong(LegacyBlockPosition.pack(p.pos.getX(),p.pos.getY(),p.pos.getZ()));b.writeDouble(p.point.x);b.writeDouble(p.point.y);b.writeDouble(p.point.z);b.writeUUID(p.identity);b.writeLong(p.sequence);b.writeDouble(p.pose);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static boolean poseAllowed(double claimed,double actual,long sequence,long now,long started,int duration){
        return Math.abs(claimed-actual)<=.01||sequence>0&&now-started<=duration+20;
    }
    public static boolean apply(ServerPlayer player,SegmentedUsePayload intent){
        if(!player.getServer().isSameThread()||intent==null||!player.isAlive()||player.isSpectator()||player.getMainHandItem().getItem() instanceof BlockItem)return false;
        ServerLevel level=player.serverLevel();var tile=NativeSegmentedTargets.loaded(level,intent.pos);if(tile==null||!level.mayInteract(player,intent.pos))return false;
        var root=tile.root();var publication=root.publication();if(publication==null||!publication.motion().identity().equals(intent.identity)||root.sequence()!=intent.sequence
                ||!poseAllowed(intent.pose,root.pose(0),intent.sequence,level.getGameTime(),root.started(),root.duration()))return false;
        double reach=player.blockInteractionRange();var eye=player.getEyePosition();if(!Double.isFinite(reach)||reach<=0||reach>64||eye.distanceToSqr(intent.point)>(reach+.25)*(reach+.25))return false;
        var end=eye.add(intent.point.subtract(eye).normalize().scale(reach+.25));var geometry=NativeSegmentedTargets.geometry(tile,intent.pose);if(geometry==null)return false;
        var hit=geometry.trace(eye.x,eye.y,eye.z,end.x,end.y,end.z);if(hit==null||new Vec3(hit.x,hit.y,hit.z).distanceToSqr(intent.point)>.05*.05)return false;
        var visibleEnd=new Vec3(hit.x,hit.y,hit.z);
        boolean unloaded=BlockGetter.traverseBlocks(eye,visibleEnd,level,(world,pos)->world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null?Boolean.TRUE:null,world->Boolean.FALSE);
        if(unloaded)return false;
        var terrain=level.clip(new ClipContext(eye,visibleEnd,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player){
            @Override public VoxelShape getBlockShape(net.minecraft.world.level.block.state.BlockState state,BlockGetter world,BlockPos pos){
                if(state.getBlock() instanceof NativeSegmentedDoorBlock){var other=NativeSegmentedTargets.loaded(level,pos);if(other!=null&&other.root().pose(0)>0)return Shapes.empty();}
                return super.getBlockShape(state,world,pos);
            }
        });
        var moved=NativeSegmentedTargets.trace(level,eye,visibleEnd);
        if(moved!=null&&(terrain.getType()==HitResult.Type.MISS||eye.distanceToSqr(moved.hit().getLocation())<eye.distanceToSqr(terrain.getLocation())))terrain=moved.hit();
        if(terrain.getType()!=HitResult.Type.MISS&&eye.distanceToSqr(terrain.getLocation())+1e-6<eye.distanceToSqr(visibleEnd)){
            var other=NativeSegmentedTargets.loaded(level,terrain.getBlockPos());if(other==null)return false;
            try{if(!tile.roots().contains(other.root()))return false;}catch(IllegalArgumentException incomplete){return false;}
        }
        var face=Direction.from3DDataValue(hit.face);if(!player.mayUseItemAt(intent.pos,face,player.getMainHandItem())||NativeSegmentedTargets.loaded(level,intent.pos)!=tile||tile.root()!=root||root.sequence()!=intent.sequence)return false;
        boolean before=root.targetOpen();var menu=player.containerMenu;
        var result=com.vandorlabs.neoforge.world.NativeBlockUse.apply(player,InteractionHand.MAIN_HAND,new BlockHitResult(visibleEnd,face,intent.pos,false));
        return result.consumesAction()||before!=root.targetOpen()||menu!=player.containerMenu;
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,payload);}));}
}

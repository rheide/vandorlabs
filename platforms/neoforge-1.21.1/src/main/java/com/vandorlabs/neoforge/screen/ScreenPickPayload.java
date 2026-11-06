package com.vandorlabs.neoforge.screen;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
/** Server-owned configured picking. Public block-entity projections never carry inventory snapshots. */
public record ScreenPickPayload(BlockPos pos,int selected,int inventoryState) implements CustomPacketPayload {
 public static final int BYTES=13,MAX_ITEM_BYTES=1024*1024;
 public static final Type<ScreenPickPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","screen_pick"));
 public ScreenPickPayload{if(pos==null||selected<0||selected>=9||inventoryState<0||inventoryState>32767)throw new IllegalArgumentException("Screen pick context");pos=pos.immutable();}
 public static final StreamCodec<RegistryFriendlyByteBuf,ScreenPickPayload> CODEC=new StreamCodec<>(){public ScreenPickPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=BYTES)throw new IllegalArgumentException("Screen pick length");return new ScreenPickPayload(b.readBlockPos(),b.readUnsignedByte(),b.readInt());}public void encode(RegistryFriendlyByteBuf b,ScreenPickPayload p){b.writeBlockPos(p.pos);b.writeByte(p.selected);b.writeInt(p.inventoryState);}};
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public boolean apply(ServerPlayer player){
  var level=player.serverLevel();var inventory=player.getInventory();
  if(!player.getServer().isSameThread()||!player.isAlive()||!player.isCreative()||player.isSpectator()||player.containerMenu!=player.inventoryMenu||!player.inventoryMenu.getCarried().isEmpty()||inventory.selected!=selected||player.inventoryMenu.getStateId()!=inventoryState||!NativeRedstoneChannels.open(level)||level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)||level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,Direction.UP,player.getMainHandItem()))return false;
  if(!(level.getBlockEntity(pos) instanceof NativeScreenEntity owner)||owner.isRemoved()||owner.getLevel()!=level||!(owner.getBlockState().getBlock() instanceof NativeScreenBlock block)||block.family()!=owner.family())return false;
  var expectedState=owner.getBlockState();long geometryRevision=owner.visual().geometryRevision();
  double blocks=player.blockInteractionRange(),entities=player.entityInteractionRange(),reach=Math.max(blocks,entities);if(!Double.isFinite(blocks)||!Double.isFinite(entities)||blocks<=0||entities<=0||reach>64)return false;
  var position=player.position();float yaw=player.getYRot(),pitch=player.getXRot();var start=player.getEyePosition();var direction=Vec3.directionFromRotation(pitch,yaw);var end=start.add(direction.scale(reach));
  var target=owner.getBlockState().getShape(level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(player)).clip(start,end,pos);if(target==null||!target.getLocation().closerThan(start,blocks))return false;
  var visibleEnd=target.getLocation();boolean unavailable=BlockGetter.traverseBlocks(start,visibleEnd,level,(world,at)->world.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4)==null?Boolean.TRUE:null,world->Boolean.FALSE);if(unavailable)return false;
  var barrier=level.clip(new ClipContext(start,visibleEnd,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));if(barrier.getType()==HitResult.Type.BLOCK&&!barrier.getBlockPos().equals(pos))return false;var hit=target;
  double distance=start.distanceToSqr(hit.getLocation());var targetEnd=hit.getLocation();
  var entity=ProjectileUtil.getEntityHitResult(player,start,targetEnd,player.getBoundingBox().expandTowards(targetEnd.subtract(start)).inflate(1),candidate->!candidate.isSpectator()&&candidate.isPickable(),distance);if(entity!=null&&start.distanceToSqr(entity.getLocation())<distance)return false;
  var mechanism=com.vandorlabs.neoforge.door.NativeDoorTargets.pick(level,player,start,visibleEnd);if(mechanism!=null&&start.distanceToSqr(mechanism.hit().getLocation())<distance)return false;
  final ItemStack result;try{result=owner.getBlockState().getCloneItemStack(hit,level,pos,player);if(result.isEmpty()||!result.isItemEnabled(level.enabledFeatures())||level.getBlockEntity(pos)!=owner||player.containerMenu!=player.inventoryMenu||inventory.selected!=selected||player.inventoryMenu.getStateId()!=inventoryState)return false;var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(256,MAX_ITEM_BYTES),level.registryAccess());try{ItemStack.STREAM_CODEC.encode(buffer,result);}finally{buffer.release();}}catch(RuntimeException unsupported){return false;}
  // The vanilla selector handles existing hotbar matches, inventory swaps, empty slots and replacement.
  if(!player.isCreative()||!player.isAlive()||level.getBlockEntity(pos)!=owner||owner.getBlockState()!=expectedState||owner.visual().geometryRevision()!=geometryRevision||player.containerMenu!=player.inventoryMenu||!player.inventoryMenu.getCarried().isEmpty()||inventory.selected!=selected||player.inventoryMenu.getStateId()!=inventoryState||!player.position().equals(position)||player.getYRot()!=yaw||player.getXRot()!=pitch)return false;
  inventory.setPickedItem(result);inventory.setChanged();player.inventoryMenu.broadcastChanges();player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(inventory.selected));return true;
 }
 public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)payload.apply(player);}));}
}

package com.vandorlabs.neoforge.navigation.observer;
import net.minecraft.core.BlockPos;import net.minecraft.server.level.ServerLevel;import net.minecraft.world.level.Level;import net.minecraft.world.level.block.state.BlockState;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;
/** Loaded-only refusal for non-player replacement. Only the exact durable family write may cross it. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeObserverProtection {
 private static final class Admission{final ServerLevel level;final BlockPos pos;final BlockState state;boolean used;Admission(ServerLevel l,BlockPos p,BlockState s){level=l;pos=p.immutable();state=s;}}
 private static final ThreadLocal<Admission> WRITE=new ThreadLocal<>();
 static boolean set(ServerLevel l,BlockPos p,BlockState state,int flags){if(WRITE.get()!=null||!com.vandorlabs.neoforge.transfer.NativeTransferHolds.reserved(l,p))throw new IllegalStateException("Builder write requires exact durable cell lease");WRITE.set(new Admission(l,p,state));try{return l.setBlock(p,state,flags);}finally{WRITE.remove();}}
 public static boolean retained(Level l,BlockPos p){if(!(l instanceof ServerLevel)||NativeObserverBindings.PART==null)return false;var chunk=l.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4);if(chunk==null)return false;var state=chunk.getBlockState(p);if(state.getBlock() instanceof NativeObserverPartBlock)return true;if(!(state.getBlock() instanceof NativeObserverCoreBlock))return false;if(state.getValue(NativeObserverCoreBlock.FORMED))return true;return chunk.getBlockEntities().get(p) instanceof NativeObserverEntity owner&&owner.ownsRecovery();}
 public static boolean replacementBlocked(Level l,BlockPos p,BlockState next){if(com.vandorlabs.neoforge.gear.NativeGearHolds.publicationAdmitted(l,p))return false;var admission=WRITE.get();if(admission!=null&&!admission.used&&admission.level==l&&admission.pos.equals(p)&&admission.state==next){admission.used=true;return false;}return retained(l,p);}
 @SubscribeEvent public static void explosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate e){e.getAffectedBlocks().removeIf(p->retained(e.getLevel(),p));}
 private NativeObserverProtection(){}
}

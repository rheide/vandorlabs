package com.vandorlabs.neoforge.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One stationary cell: install choices before consumption/observers, or restore its former owner. */
public final class NativeStationaryPlacement {
 private NativeStationaryPlacement(){}
 private record Scope(Level level,BlockPos pos){}
 private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
 public static boolean mutating(Level level,BlockPos pos){var scope=ACTIVE.get();return scope!=null&&scope.level==level&&scope.pos.equals(pos);}
 public interface Hooks<T extends BlockEntity> {
  boolean stage();boolean restore();T owner();void install(T owner);T retainRecovery();String label();
 }
 private static void transfer(Player player,ItemStack stack){if(!player.getAbilities().instabuild)stack.shrink(1);}
 public static <T extends BlockEntity> boolean commit(Level level,BlockPos pos,BlockState old,BlockState state,Player player,ItemStack stack,Hooks<T> hooks){
  Scope prior=ACTIVE.get();ACTIVE.set(new Scope(level,pos));T owner=null;boolean committed=false,owned=false;Throwable original=null;
  try{if(!hooks.stage()||level.getBlockState(pos)!=state||(owner=hooks.owner())==null)return false;hooks.install(owner);owner.setChanged();transfer(player,stack);owned=committed=true;
  }catch(RuntimeException|Error failure){original=failure;throw failure;}finally{
   Throwable rollback=null;try{if(!committed&&level.getBlockState(pos)==state&&(owner==null||level.getBlockEntity(pos)==owner)){
    try{hooks.restore();if(level.getBlockState(pos)!=old)throw new IllegalStateException("Refused "+hooks.label()+" placement rollback");}catch(RuntimeException|Error failed){rollback=failed;}
    if(level.getBlockState(pos)==state)try{T retained=hooks.retainRecovery();if(retained!=null){if(!owned){transfer(player,stack);owned=true;}level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);}}catch(RuntimeException|Error failed){if(rollback==null)rollback=failed;else rollback.addSuppressed(failed);}
   }}finally{if(prior==null)ACTIVE.remove();else ACTIVE.set(prior);}
   if(rollback!=null){if(original!=null)original.addSuppressed(rollback);else throw new IllegalStateException(hooks.label()+" placement rollback failed; native owner retained",rollback);}
  }
  return committed;
 }
}

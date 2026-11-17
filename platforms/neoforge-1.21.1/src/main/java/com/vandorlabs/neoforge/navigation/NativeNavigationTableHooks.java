package com.vandorlabs.neoforge.navigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
/** Common-side integration seam. No client classes or chunk-loading operations. */
public final class NativeNavigationTableHooks {
 public interface Table {
  default boolean action(Player player,NativeNavigationTableEntity tile,int action){return false;}
  default void changed(NativeNavigationTableEntity tile){}
  default void reparent(NativeNavigationTableEntity previous,NativeNavigationTableEntity current){}
  default void recordSources(NativeNavigationTableEntity tile){}
  default boolean moduleItem(ItemStack stack){return false;}
  default boolean chipItem(ItemStack stack){return false;}
  default ItemStack moduleSummary(ItemStack stack){return stack.copy();}
 }
 public static java.util.function.BiConsumer<NativeNavigationTableEntity,Integer> CLIENT_ACTION=(t,a)->{};
 public static Table TABLE=new Table(){};
 private NativeNavigationTableHooks(){}
}

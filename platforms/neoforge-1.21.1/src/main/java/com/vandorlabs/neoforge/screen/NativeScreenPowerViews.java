package com.vandorlabs.neoforge.screen;
import java.util.function.Consumer;
/** Local-only client cache invalidation, including newly loaded members without an existing cache. */
public final class NativeScreenPowerViews {
 private NativeScreenPowerViews(){}
 private static volatile Consumer<NativeScreenEntity> invalidator=tile->{var view=tile.preparedPowerView();if(view!=null)view.invalidate();};
 private static volatile Consumer<NativeScreenEntity> renderInvalidator=tile->{};
 public interface Preparation {NativeScreenEntity.Visual prepare(NativeScreenEntity tile,int mask);}
 private static volatile Preparation clientPreparation=(tile,mask)->tile.visual();
 public static void bindPreparation(Preparation value){clientPreparation=java.util.Objects.requireNonNull(value);}
 /** Interaction entry point: no boxed masks, client linkage or world access from mesh workers. */
 public static NativeScreenEntity.Visual prepare(NativeScreenEntity tile,int mask){
  if(mask<0||(mask&~com.vandorlabs.screen.ScreenPowerQueries.ALL)!=0)throw new IllegalArgumentException("Screen getter mask");
  if(tile.getLevel()!=null&&tile.getLevel().isClientSide)return clientPreparation.prepare(tile,mask);
  if(tile.getLevel() instanceof net.minecraft.server.level.ServerLevel level&&!level.getServer().isSameThread())throw new IllegalStateException("Screen interaction preparation requires server thread");
  NativeScreenPowerService.queryGetters(tile,mask);return tile.visual();
 }
 public static synchronized Consumer<NativeScreenEntity> bindRender(Consumer<NativeScreenEntity> value){var previous=renderInvalidator;renderInvalidator=java.util.Objects.requireNonNull(value);return previous;}
 public static void renderChanged(NativeScreenEntity tile){if(tile.getLevel()!=null&&tile.getLevel().isClientSide)renderInvalidator.accept(tile);}
 public static void bindClient(Consumer<NativeScreenEntity> value){invalidator=java.util.Objects.requireNonNull(value);}
 static void changed(NativeScreenEntity tile){if(tile.getLevel()!=null&&tile.getLevel().isClientSide){invalidator.accept(tile);renderInvalidator.accept(tile);}}
}

package com.vandorlabs.neoforge.screen;
import java.util.function.Consumer;
/** Local-only client cache invalidation, including newly loaded members without an existing cache. */
public final class NativeScreenPowerViews {
 private NativeScreenPowerViews(){}
 private static volatile Consumer<NativeScreenEntity> invalidator=tile->{var view=tile.preparedPowerView();if(view!=null)view.invalidate();};
 private static volatile Consumer<NativeScreenEntity> renderInvalidator=tile->{};
 public static synchronized Consumer<NativeScreenEntity> bindRender(Consumer<NativeScreenEntity> value){var previous=renderInvalidator;renderInvalidator=java.util.Objects.requireNonNull(value);return previous;}
 public static void renderChanged(NativeScreenEntity tile){if(tile.getLevel()!=null&&tile.getLevel().isClientSide)renderInvalidator.accept(tile);}
 public static void bindClient(Consumer<NativeScreenEntity> value){invalidator=java.util.Objects.requireNonNull(value);}
 static void changed(NativeScreenEntity tile){if(tile.getLevel()!=null&&tile.getLevel().isClientSide){invalidator.accept(tile);renderInvalidator.accept(tile);}}
}

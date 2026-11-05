package com.vandorlabs.neoforge.fluid;
import net.neoforged.bus.api.Event;
/** Client main-thread publication boundary; listeners capture immutable geometry/materials. */
public final class NativeFluidEvents {
 private NativeFluidEvents(){}
 public static final class Updated extends Event {public final ContainedFluidHost host;public final boolean modelChanged;
  public Updated(ContainedFluidHost host){this(host,false);}
  public Updated(ContainedFluidHost host,boolean modelChanged){this.host=host;this.modelChanged=modelChanged;}}
 public static final class Removed extends Event {public final ContainedFluidHost host;public Removed(ContainedFluidHost host){this.host=host;}}
}

package com.vandorlabs.neoforge.screen;
import com.vandorlabs.neoforge.redstone.NativeTransmissionMigration;
import net.minecraft.nbt.CompoundTag;
/** Compatibility facade for the shared Source development-module migration. */
public final class NativeScreenTransmissionMigration {
 private NativeScreenTransmissionMigration(){}
 public static final class Result {
  private final NativeTransmissionMigration.Result value;
  private Result(NativeTransmissionMigration.Result value){this.value=value;}
  public boolean installable(){return value.installable();}
  public CompoundTag install(){return value.install();}
  public CompoundTag unchanged(){return value.unchanged();}
  CompoundTag referenceProjection(){return value.referenceProjection();}
 }
 public static Result read(CompoundTag source){return new Result(NativeTransmissionMigration.read(source));}
}

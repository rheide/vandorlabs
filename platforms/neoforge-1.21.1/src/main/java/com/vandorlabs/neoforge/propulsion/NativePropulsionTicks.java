package com.vandorlabs.neoforge.propulsion;
import java.util.Objects;
import java.util.function.Consumer;
/** Common ticker seam; dedicated servers never resolve a client particle or renderer class. */
public final class NativePropulsionTicks {
 private static volatile Consumer<NativePropulsionEntity> client=owner->{};
 private NativePropulsionTicks(){}
 public static void installClient(Consumer<NativePropulsionEntity> ticker){client=Objects.requireNonNull(ticker);}
 static void client(NativePropulsionEntity owner){client.accept(owner);}
}

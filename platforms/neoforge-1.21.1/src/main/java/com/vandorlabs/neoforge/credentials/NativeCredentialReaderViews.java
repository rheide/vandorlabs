package com.vandorlabs.neoforge.credentials;

import java.util.Objects;
import java.util.function.Consumer;

/** Stateless application fanout for adjacent-reader caches; never publishes physical ownership. */
public final class NativeCredentialReaderViews {
    private static volatile Consumer<NativeCredentialReaderEntity> changed=ignored->{};
    private NativeCredentialReaderViews(){}
    /** Install a stateless dispatcher. Callbacks must only invalidate local epochs/queue positions,
     * never resolve neighbors or register gameplay sources (onLoad also invokes this hook). */
    public static void bindChanged(Consumer<NativeCredentialReaderEntity> listener){changed=Objects.requireNonNull(listener);}
    static void changed(NativeCredentialReaderEntity owner){changed.accept(owner);}
}

package com.vandorlabs.neoforge.ramp;

/** Concrete runtime result shared by item and editor adapters; pending work is not a refusal. */
public record NativeRampReceipt(boolean admitted,boolean choicesCommitted,boolean actionPending,
                                boolean activationRefused,NativeRampMenu.Status status) {
    public NativeRampReceipt {java.util.Objects.requireNonNull(status);}
    public NativeRampMenu.Outcome outcome(NativeRampSettings choices){return new NativeRampMenu.Outcome(choices,status,admitted,choicesCommitted,actionPending,activationRefused);}
}

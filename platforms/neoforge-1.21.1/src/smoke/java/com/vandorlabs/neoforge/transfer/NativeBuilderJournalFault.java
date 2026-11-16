package com.vandorlabs.neoforge.transfer;
/** Test-only bridge into the real async engine's existing deliberate fault boundary. */
public final class NativeBuilderJournalFault {
 public interface Probe{void check(String stage,NativeCellTransactions.Header header,int cell);}
 public static void set(Probe probe){NativeCellTransactions.fault=probe==null?null:probe::check;}
 private NativeBuilderJournalFault(){}
}

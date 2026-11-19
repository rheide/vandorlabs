package com.vandorlabs.neoforge.transfer;

public final class CellTransferFaultFixture {
    @FunctionalInterface public interface AfterCell { void run(NativeCellTransactions.Header header, int index); }

    public static void afterCell(AfterCell action) {
        NativeCellTransactions.fault = action == null ? null : (point, header, index) -> {
            if (point.equals("after-cell")) action.run(header, index);
        };
    }

    private CellTransferFaultFixture() {}
}

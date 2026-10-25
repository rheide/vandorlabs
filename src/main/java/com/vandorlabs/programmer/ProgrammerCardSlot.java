package com.vandorlabs.programmer;

/** Optional generic sample-card meshes. Real item rendering can replace them. */
public enum ProgrammerCardSlot {
    MASTER("sample_master", 1),
    INPUT("sample_input", 2),
    OUTPUT("sample_output", 4);

    public final String partName;
    public final int mask;

    ProgrammerCardSlot(String partName, int mask) {
        this.partName = partName;
        this.mask = mask;
    }
}

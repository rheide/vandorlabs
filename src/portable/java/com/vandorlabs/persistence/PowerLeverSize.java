package com.vandorlabs.persistence;

/** Current lever size semantics: saved tiles, live edits and item previews deliberately differ. */
public final class PowerLeverSize {
    public static final String KEY="PowerLeverSize";
    public static final int UNSPECIFIED=-1;
    private PowerLeverSize(){}
    public static boolean hasSavedValue(int stored){return stored>=0;}
    public static boolean valid(int size){return size>=0&&size<=1;}
    /** Only an exact integer tag selects a saved size; malformed integer values clamp on world load. */
    public static int readSaved(boolean typedInt,int raw){return typedInt?Math.max(0,Math.min(1,raw)):UNSPECIFIED;}
    /** Missing saved fields retain the original registry ID's default, without writing a new field. */
    public static int effective(int stored,int registryDefault){return hasSavedValue(stored)?stored:registryDefault;}
    /** Do not clamp item choices: an invalid typed value must leave the original baked model selected. */
    public static int itemChoice(boolean typedInt,int raw,int registryDefault){return typedInt?raw:registryDefault;}
}

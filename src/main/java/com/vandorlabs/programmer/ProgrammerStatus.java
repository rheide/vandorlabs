package com.vandorlabs.programmer;

/** Texture status; the host owns actual programming progress and completion. */
public enum ProgrammerStatus {
    READY("ready", true),
    WRITING("writing", true),
    COMPLETE("complete", true),
    BLANK("blank", false);

    public final String resourceName;
    public final boolean screenLit;

    ProgrammerStatus(String resourceName, boolean screenLit) {
        this.resourceName = resourceName;
        this.screenLit = screenLit;
    }
}

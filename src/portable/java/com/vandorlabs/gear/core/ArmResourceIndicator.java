package com.vandorlabs.gear.core;

/** Selects the cropped Port bar for a stationary arm base. Activity is transient. */
public final class ArmResourceIndicator {
    public static final int BLOCK_EVENT=0;
    private static final long REFRESH_TICKS=5, LIT_TICKS=10;
    private static final ArmResource[] MODES=ArmResource.values();
    private static final String[][] MODELS={
            {null,null},
            {"resource_bar_power_off","resource_bar_power_on"},
            {"resource_bar_fluid_off","resource_bar_fluid_on"},
            {"resource_bar_matter_off","resource_bar_matter_on"}
    };
    private ArmResource published=ArmResource.DISABLED, received=ArmResource.DISABLED;
    private long lastPublished,lastReceived;

    public static ArmResource eventMode(int value) {
        return value>0&&value<MODES.length?MODES[value]:ArmResource.DISABLED;
    }

    public static String model(ArmResource mode,boolean active) {
        return MODELS[mode.ordinal()][active?1:0];
    }

    public static String faceModel(ArmResource mode,boolean output,boolean active,boolean tip){
        if(mode==ArmResource.DISABLED)return null;
        String family=mode==ArmResource.POWER?"power":mode==ArmResource.FLUID?"fluid":"matter";
        return (tip?"resource_tip_":"resource_mount_")+family+(output?"_out_":"_in_")+(active?"on":"off");
    }

    public boolean publish(ArmResource mode,long tick) {
        if(published==mode&&tick>=lastPublished&&tick-lastPublished<REFRESH_TICKS)return false;
        published=mode;
        lastPublished=tick;
        return true;
    }

    public void receive(ArmResource mode,long tick) {
        received=mode;
        lastReceived=tick;
    }

    public boolean active(ArmResource mode,long tick) {
        return mode!=ArmResource.DISABLED&&received==mode&&tick>=lastReceived&&tick-lastReceived<LIT_TICKS;
    }
}

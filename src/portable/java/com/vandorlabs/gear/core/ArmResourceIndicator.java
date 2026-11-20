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
    private static final String[][][][] FACES=new String[4][2][2][2];
    static {
        String[] families={null,"power","fluid","matter"};
        for(int mode=1;mode<4;mode++)for(int out=0;out<2;out++)for(int lit=0;lit<2;lit++)for(int tip=0;tip<2;tip++)
            FACES[mode][out][lit][tip]=(tip==1?"resource_tip_":"resource_mount_")+families[mode]+(out==1?"_out_":"_in_")+(lit==1?"on":"off");
    }
    private ArmResource published=ArmResource.DISABLED, received=ArmResource.DISABLED;
    private long lastPublished,lastReceived;

    public static ArmResource eventMode(int value) {
        return value>0&&value<MODES.length?MODES[value]:ArmResource.DISABLED;
    }

    public static String model(ArmResource mode,boolean active) {
        return MODELS[mode.ordinal()][active?1:0];
    }

    public static String faceModel(ArmResource mode,boolean output,boolean active,boolean tip){
        return FACES[mode.ordinal()][output?1:0][active?1:0][tip?1:0];
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

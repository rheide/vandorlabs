package com.vandorlabs.light;

import com.vandorlabs.persistence.SpaceDoorData;

/** Source Light evaluation; callers supply separately sampled direct and joined inputs. */
public final class ProgrammableLightPolicy {
    private ProgrammableLightPolicy(){}
    public static int level(boolean numeric,int configured,boolean joined,int directLevel,int joinedLevel,int offset){
        return numeric?Math.max(0,Math.min(15,(joined?joinedLevel:directLevel)+offset)):configured;
    }
    public static boolean on(boolean numeric,int level,int trigger,boolean manualOn,boolean joined,boolean directPower,boolean joinedPower){
        return numeric?level>0:trigger==SpaceDoorData.TRIGGER_DISABLED?manualOn:SpaceDoorData.openForSignal(trigger,joined?joinedPower:directPower);
    }
    public static int emitted(boolean on,int level){return on?level:0;}
    public static boolean litArtwork(boolean on,int level){return on&&level>0;}
    /** Numeric brightness does not disable the source's manual-state write. */
    public static boolean manualToggleAllowed(int trigger){return trigger==SpaceDoorData.TRIGGER_DISABLED;}
    public static boolean consumesInteraction(boolean mainHand,boolean sneaking,boolean creative){return !mainHand||!sneaking||creative;}
    public static boolean opensConfiguration(boolean authoritative,boolean mainHand,boolean sneaking,boolean creative){return authoritative&&mainHand&&sneaking&&creative;}
    public static boolean togglesInteraction(boolean authoritative,boolean mainHand,boolean sneaking,int trigger){return authoritative&&mainHand&&!sneaking&&manualToggleAllowed(trigger);}
}

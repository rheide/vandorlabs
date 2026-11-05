package com.vandorlabs.persistence;

/** Frozen pre-extraction arithmetic oracle, including public methods' out-of-domain behavior. */
public final class SignalControlDataTest {
    private static void require(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    private static int oldLevel(int value,int low,int high){return value==0?0:value==1?low:value==2?(low+high+1)/2:high;}
    private static int oldStep(int level,int low,int high){int nearest=0,distance=16;for(int i=level>0?1:0;i<4;i++){int d=Math.abs(level-oldLevel(i,low,high));if(d<distance){nearest=i;distance=d;}}return nearest;}
    public static void main(String[] ignored){int cases=0;int[] extremes={Integer.MIN_VALUE,-32,-1,0,1,2,3,4,15,16,31,Integer.MAX_VALUE};
        require(SignalControlData.LOW.equals("LowLimit")&&SignalControlData.HIGH.equals("HighLimit")&&SignalControlData.STEP.equals("ControlStep"),"stable save keys");
        for(int low=-2;low<=18;low++)for(int high=-2;high<=18;high++)require(SignalControlData.validLimits(low,high)==(low>=1&&high<=15&&high-low>=2),"limit boundary");
        for(int low=1;low<=13;low++)for(int high=low+2;high<=15;high++){
            for(int step:extremes){require(SignalControlData.levelForStep(step,low,high)==oldLevel(step,low,high),"four levels and public fallback");cases++;}
            for(int level=-32;level<=47;level++){require(SignalControlData.stepForLevel(level,low,high)==oldStep(level,low,high),"all ordinary levels/ties");cases++;}
            for(int level:extremes){require(SignalControlData.stepForLevel(level,low,high)==oldStep(level,low,high),"extreme integer arithmetic");cases++;}
        }
        for(int low:extremes)for(int high:extremes)for(int value:extremes){require(SignalControlData.levelForStep(value,low,high)==oldLevel(value,low,high)&&SignalControlData.stepForLevel(value,low,high)==oldStep(value,low,high),"unvalidated public inputs preserve overflow behavior");cases++;}
        for(int value:extremes){require(SignalControlData.savedLow(false,value)==5&&SignalControlData.savedHigh(false,value)==15,"absent defaults");require(SignalControlData.savedLow(true,value)==value&&SignalControlData.savedHigh(true,value)==value,"present wrong/numeric host conversion retained");require(SignalControlData.savedStep(value)==(value<0?0:value>3?3:value),"saved step clamp");}
        require(SignalControlData.stepForLevel(1,5,15)==1,"positive signal must not display off");require(SignalControlData.stepForLevel(7,5,13)==1,"equal-distance tie selects lower detent");
        System.out.println("SignalControlData PASS cases="+cases+" exhaustive valid limits/levels, lower ties, positive floor, extremes and scalar save defaults");
    }
}

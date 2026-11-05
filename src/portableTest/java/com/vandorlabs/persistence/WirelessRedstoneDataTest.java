package com.vandorlabs.persistence;

/** Independent endpoint truth tables and saved-tag bounds, shared unchanged by both JDKs. */
public final class WirelessRedstoneDataTest {
    private static void require(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    public static void main(String[] ignored){
        int[] levels={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
        int[] digital={0,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15};
        for(int input=0;input<16;input++){require(WirelessRedstoneData.sourceLevel(true,input)==levels[input],"analog source truth table");require(WirelessRedstoneData.sourceLevel(false,input)==digital[input],"digital source truth table");}
        require(WirelessRedstoneData.outputLevel(true,0,8,3)==3&&WirelessRedstoneData.outputLevel(true,1,8,3)==1&&WirelessRedstoneData.outputLevel(true,15,8,3)==15,"receiver analog zero/positive distinction");
        require(WirelessRedstoneData.outputLevel(false,0,8,3)==3&&WirelessRedstoneData.outputLevel(false,1,8,3)==8&&WirelessRedstoneData.outputLevel(false,15,8,3)==8,"receiver digital truth table");
        for(boolean analog:new boolean[]{false,true})for(int on=0;on<16;on++)for(int off=0;off<16;off++){
            require(WirelessRedstoneData.outputLevel(analog,0,on,off)==off,"all Off choices, including nonzero Off");
            for(int received=1;received<16;received++)require(WirelessRedstoneData.outputLevel(analog,received,on,off)==(analog?levels[received]:on),"all configured On and received levels");
        }
        int[] raw={Integer.MIN_VALUE,-1,0,1,14,15,16,Integer.MAX_VALUE},clamped={0,0,0,1,14,15,15,15};
        for(int i=0;i<raw.length;i++){require(WirelessRedstoneData.level(raw[i])==clamped[i],"level bounds");require(WirelessRedstoneData.savedOn(true,raw[i])==clamped[i],"actual int On bounds");require(WirelessRedstoneData.savedOn(false,raw[i])==15,"wrong-type or absent On defaults15");}
        require(WirelessRedstoneData.savedAnalog(false,false)&&WirelessRedstoneData.savedAnalog(false,true)&&!WirelessRedstoneData.savedAnalog(true,false)&&WirelessRedstoneData.savedAnalog(true,true),"presence-sensitive Analog default");
        require(WirelessRedstoneData.sourceLevel(true,Integer.MIN_VALUE)==Integer.MIN_VALUE&&WirelessRedstoneData.sourceLevel(false,Integer.MIN_VALUE)==0,"source formula does not replace host sample normalization");
        require(WirelessRedstoneData.ANALOG.equals("WirelessAnalog")&&WirelessRedstoneData.ON.equals("WirelessOn")&&WirelessRedstoneData.OFF.equals("WirelessOff")&&WirelessRedstoneData.INPUT.equals("WirelessInput")&&WirelessRedstoneData.RECEIVED.equals("WirelessReceived"),"retained saved keys");
        System.out.println("WirelessRedstoneData PASS analog/digital truth tables all16levels and On/Off pairs typed defaults and integer bounds");
    }
}

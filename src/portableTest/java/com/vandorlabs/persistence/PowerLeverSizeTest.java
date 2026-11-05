package com.vandorlabs.persistence;

/** Literal old-data and boundary cases, including the different malformed item and tile contracts. */
public final class PowerLeverSizeTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] ignored){
        int[] values={Integer.MIN_VALUE,-2,-1,0,1,2,3,Integer.MAX_VALUE};int[] loaded={0,0,0,0,1,1,1,1};int cases=0;
        require(PowerLeverSize.KEY.equals("PowerLeverSize")&&PowerLeverSize.UNSPECIFIED==-1,"stable field and sentinel");
        for(int i=0;i<values.length;i++)for(int fallback:new int[]{0,1}){
            int raw=values[i];require(PowerLeverSize.hasSavedValue(raw)==(raw>=0),"saved sentinel/presence");require(PowerLeverSize.valid(raw)==(raw==0||raw==1),"live invalid size rejected");
            require(PowerLeverSize.readSaved(true,raw)==loaded[i],"loaded exact int clamp");require(PowerLeverSize.readSaved(false,raw)==-1,"wrong/missing tag retains absent sentinel");
            require(PowerLeverSize.effective(-1,fallback)==fallback,"registry default for absent saved size");require(PowerLeverSize.effective(loaded[i],fallback)==loaded[i],"explicit saved size overrides registry default");
            require(PowerLeverSize.itemChoice(false,raw,fallback)==fallback,"item requires exact int tag");require(PowerLeverSize.itemChoice(true,raw,fallback)==raw,"item invalid int must remain invalid rather than clamp");cases++;
        }
        require(PowerLeverSize.effective(2,0)==2,"effective getter must not add validation to caller-owned stored state");
        System.out.println("PowerLeverSize PASS cases="+cases+" saved clamp, strict live/item choices and unchanged registry defaults");
    }
}

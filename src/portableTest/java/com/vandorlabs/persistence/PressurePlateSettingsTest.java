package com.vandorlabs.persistence;

/** Fixed Source sensing/output contracts, including weighted float-ceiling boundaries. */
public final class PressurePlateSettingsTest {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        for(int sensitivity=0;sensitivity<3;sensitivity++)for(int count=0;count<=175;count++){
            int maximum=sensitivity==1?15:150;
            int expected=sensitivity==0?(count==0?0:15):count==0?0:(int)Math.ceil(((float)Math.min(count,maximum)/(float)maximum)*15F);
            need(PressurePlateSettings.measure(sensitivity,count,count>0)==expected,"Source float ceil count="+count+" kind="+sensitivity);
            need(PressurePlateSettings.measure(sensitivity,count,false)==(sensitivity==0?0:expected),"Wood excludes trigger-exempt; weighted counts it");
            for(int mode=0;mode<3;mode++)need(PressurePlateSettings.output(mode,expected)==(mode==1?expected:mode==2&&expected==0?15:0),"independent channel output");
        }
        need(PressurePlateSettings.releaseTicks(0)==20&&PressurePlateSettings.releaseTicks(1)==10&&PressurePlateSettings.releaseTicks(2)==10,"Source native release cadence");
        need(PressurePlateSettings.savedMode(false,0)==1&&PressurePlateSettings.savedMode(true,-1)==0&&PressurePlateSettings.savedMode(true,5)==2,"previous saved defaults/clamps");
        System.out.println("PressurePlateSettings PASS checks="+checks+" threeSensingModes/floatCeil/entityCounts/independentOutput/release/savedDefaults");
    }
    private PressurePlateSettingsTest(){}
}

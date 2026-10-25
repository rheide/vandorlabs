package com.vandorlabs.persistence;

public final class ControlSettingsTest {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("Control settings "+checks);checks++;}
    public static void main(String[] args){
        MemoryPrimitiveData tag=new MemoryPrimitiveData();ControlSettings.Mount fallback=new ControlSettings.Mount(3,2,1);
        check(ControlSettings.readSize(tag)==-1);check(ControlSettings.size(-1,1)==1);check(ControlSettings.size(0,1)==0);
        for(int h=-1;h<=4;h++)for(int t=-1;t<=4;t++)for(int d=-1;d<=4;d++){
            tag.putInt("ControlMountVersion",2);tag.putInt("BaseHeight",h);tag.putInt("BaseTilt",t);tag.putInt("TiltDirection",d);ControlSettings.Mount m=ControlSettings.Mount.read(tag,fallback);
            check((h>=0&&h<=3&&t>=0&&t<=3&&d>=0&&d<=3)?m.height==h&&m.tilt==t&&m.direction==d:m==fallback);
        }
        tag=new MemoryPrimitiveData();tag.putInt("BaseTilt",3);check(ControlSettings.Mount.read(tag,fallback).tilt==1);tag.putInt("ControlMountVersion",0);check(ControlSettings.Mount.read(tag,fallback).tilt==3);
        for(int size=-5;size<=5;size++){tag.putInt("PowerLeverSize",size);check(ControlSettings.readSize(tag)==Math.max(0,Math.min(1,size)));}
        for(int low=-1;low<=16;low++)for(int high=-1;high<=16;high++)for(int step=-1;step<=4;step++){
            tag.putInt("LowLimit",low);tag.putInt("HighLimit",high);tag.putInt("ControlStep",step);ControlSettings.Detents state=ControlSettings.Detents.read(tag,3,12);boolean valid=low>=1&&high<=15&&high>=low+2;
            check(state.low==(valid?low:3));check(state.high==(valid?high:12));check(state.step==Math.max(0,Math.min(3,step)));
        }
        tag=new MemoryPrimitiveData();ControlSettings.Detents defaults=ControlSettings.Detents.read(tag,3,12);check(defaults.low==5&&defaults.high==15&&defaults.step==0);
        tag.putString("PowerLeverSize","1");check(ControlSettings.readSize(tag)==-1);
        tag.putString("LowLimit","5");defaults=ControlSettings.Detents.read(tag,3,12);check(defaults.low==3&&defaults.high==12);
        tag.putInt("BaseHeight",99);ControlSettings.Mount zero=new ControlSettings.Mount(0,0,0);check(ControlSettings.Mount.read(tag,zero)==zero);check(ControlSettings.Mount.read(tag,fallback)==fallback);
        System.out.println("ControlSettingsTest PASS "+checks+" assertions");
    }
}

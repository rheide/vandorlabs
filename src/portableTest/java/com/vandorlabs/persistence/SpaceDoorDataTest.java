package com.vandorlabs.persistence;

public final class SpaceDoorDataTest {
    private static void check(boolean value,String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int design=0;design<15;design++) for (int detail=0;detail<3;detail++)
            for (boolean framed:new boolean[]{false,true}) for (int direction=0;direction<3;direction++)
                for (boolean middle:new boolean[]{false,true}) for (boolean sliding:new boolean[]{false,true}) {
                MemoryPrimitiveData tag=new MemoryPrimitiveData();
                new SpaceDoorData(design,detail,framed,direction,middle,sliding).write(tag);
                SpaceDoorData copy=SpaceDoorData.read(tag);
                check(copy.design==design && copy.detail==detail && copy.framed==framed
                        && copy.direction==direction && copy.middle==middle && copy.sliding==sliding,"settings round trip");
                double travel=SpaceDoorData.verticalTravel(framed,direction);
                check(direction==0?travel==0:direction==1?travel>0:travel<0,"slide direction sign");
                double low=framed?1.0/16:0, high=framed?31.0/16:2;
                check(low+travel>=-2 && high+travel<=4,"vertical renderer bounds");
                if (direction==1) check(low+travel==(framed?2:31.0/16),"upper framed clearance / bare reveal");
                if (direction==2) check(high+travel==(framed?0:1.0/16),"lower framed clearance / bare reveal");
            }
        SpaceDoorData empty=SpaceDoorData.read(new MemoryPrimitiveData());
        check(empty.design==2 && empty.detail==1 && empty.framed && empty.direction==0
                && !empty.middle && !empty.sliding,"legacy defaults");
        SpaceDoorData bad=new SpaceDoorData(-1,9,false,90);
        check(bad.design==2 && bad.detail==1 && bad.direction==0,"invalid settings fallback");
        check(new SpaceDoorData(15,1,true,0).design==2,"unknown design fallback");
        check(SpaceDoorData.positionOffset(false,false)==0,"rotating edge native");
        check(SpaceDoorData.positionOffset(false,true)<0,"rotating middle inset");
        check(SpaceDoorData.positionOffset(true,true)==0,"sliding middle native");
        check(SpaceDoorData.positionOffset(true,false)>0,"sliding edge outset");
        com.vandorlabs.render.SpaceDoorMotion motion=com.vandorlabs.render.SpaceDoorMotion.ROTATING;
        for (int i=0;i<4;i++) {
            check(motion.sliding==(i>0) && motion.direction==Math.max(0,i-1),"four motion choices");
            check(com.vandorlabs.render.SpaceDoorMotion.fromSettings(motion.sliding,motion.direction)==motion,
                    "dialog motion matches saved settings");
            motion=motion.next();
        }
        check(motion==com.vandorlabs.render.SpaceDoorMotion.ROTATING,"motion selector wraps");
        System.out.println("Space door settings PASS: 1080 configurations, persistence, motion, vertical travel and bounds");
    }
}

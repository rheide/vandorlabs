package com.vandorlabs.persistence;

public final class SpaceDoorDataTest {
    private static void check(boolean value,String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int design=0;design<30;design++) for (int detail=0;detail<3;detail++)
            for (boolean framed:new boolean[]{false,true}) for (int direction=0;direction<3;direction++)
                for (boolean middle:new boolean[]{false,true}) for (boolean sliding:new boolean[]{false,true})
                    for (boolean hinges:new boolean[]{false,true}) {
                MemoryPrimitiveData tag=new MemoryPrimitiveData();
                new SpaceDoorData(design,detail,framed,direction,middle,sliding,hinges).write(tag);
                SpaceDoorData copy=SpaceDoorData.read(tag);
                check(copy.design==design && copy.detail==Math.min(detail,1) && copy.framed==framed
                        && copy.direction==direction && copy.middle==middle && copy.sliding==sliding && copy.hinges==hinges,"settings round trip");
                double travel=SpaceDoorData.verticalTravel(framed,direction);
                check(direction==0?travel==0:direction==1?travel>0:travel<0,"slide direction sign");
                double low=framed?1.0/16:0, high=framed?31.0/16:2;
                check(low+travel>=-2 && high+travel<=4,"vertical renderer bounds");
                if (direction==1) check(low+travel==(framed?2:31.0/16),"upper framed clearance / bare reveal");
                if (direction==2) check(high+travel==(framed?0:1.0/16),"lower framed clearance / bare reveal");
            }
        SpaceDoorData empty=SpaceDoorData.read(new MemoryPrimitiveData());
        check(empty.design==2 && empty.detail==1 && empty.framed && empty.direction==0
                && !empty.middle && !empty.sliding && empty.hinges && empty.panel
                && empty.trigger==SpaceDoorData.TRIGGER_DISABLED,"legacy defaults");
        MemoryPrimitiveData panelOff=new MemoryPrimitiveData();
        new SpaceDoorData(2,1,true,0,false,false,true,0,false).write(panelOff);
        check(!SpaceDoorData.read(panelOff).panel,"disabled panel survives save and copy");
        for (int trigger=0;trigger<=2;trigger++) {
            MemoryPrimitiveData tag=new MemoryPrimitiveData();
            new SpaceDoorData(2,1,true,0,false,false,true,trigger).write(tag);
            check(SpaceDoorData.read(tag).trigger==trigger,"trigger survives save and copy");
        }
        check(SpaceDoorData.openForSignal(SpaceDoorData.TRIGGER_REDSTONE_ON,true)
                && !SpaceDoorData.openForSignal(SpaceDoorData.TRIGGER_REDSTONE_ON,false),"open on signal");
        check(!SpaceDoorData.openForSignal(SpaceDoorData.TRIGGER_REDSTONE_OFF,true)
                && SpaceDoorData.openForSignal(SpaceDoorData.TRIGGER_REDSTONE_OFF,false),"open without signal");
        check(new SpaceDoorData(2,1,true,0,false,false,true,99).trigger
                ==SpaceDoorData.TRIGGER_DISABLED,"invalid trigger fallback");
        check(new SpaceDoorData(15,1,true,0).design==15,"white glass design survives codec");
        check(new SpaceDoorData(16,1,true,0).design==16,"dark glass design survives codec");
        check(new SpaceDoorData(29,1,true,0).design==29,"slim glass design survives codec");
        SpaceDoorData bad=new SpaceDoorData(-1,9,false,90);
        check(bad.design==2 && bad.detail==1 && bad.direction==0,"invalid settings fallback");
        check(new SpaceDoorData(30,1,true,0).design==2,"unknown design fallback");
        for (boolean sliding:new boolean[]{false,true}) for (boolean framed:new boolean[]{false,true})
                for (boolean hinges:new boolean[]{false,true}) {
            double low=sliding?(framed?6:7):(hinges?10.49:framed?11.24:12.24);
            double high=sliding?(framed?10:9):(framed?15.24:14.24);
            check(Math.abs(high+16*SpaceDoorData.positionOffset(sliding,framed,hinges,1)-(framed?16:15))<1E-9,
                    "near-edge door clearance is wrong");
            check(Math.abs(low+16*SpaceDoorData.positionOffset(sliding,framed,hinges,2)-(framed?0:1))<1E-9,
                    "far-edge door clearance is wrong");
            check(SpaceDoorData.positionOffset(sliding,framed,hinges,0)==(sliding?0:-5.24/16.0),
                    "middle door model moved");
        }
        com.vandorlabs.render.SpaceDoorMotion motion=com.vandorlabs.render.SpaceDoorMotion.ROTATING;
        for (int i=0;i<6;i++) {
            check(motion.sliding==(i>0) && motion.direction==Math.max(0,i-1),"motion choices append without changing old IDs");
            check(com.vandorlabs.render.SpaceDoorMotion.fromSettings(motion.sliding,motion.direction)==motion,
                    "dialog motion matches saved settings");
            motion=motion.next();
        }
        MemoryPrimitiveData legacySplit=new MemoryPrimitiveData();
        legacySplit.putInt("SpaceSlideDirection",4);
        legacySplit.putInt("SpaceDoorSchema",2);
        check(SpaceDoorData.read(legacySplit).direction==0,"invalid pre-split direction retains old fallback");
        new SpaceDoorData(23,1,true,4,false,true).write(legacySplit);
        check(legacySplit.getInt("SpaceDoorSchema")==3&&SpaceDoorData.read(legacySplit).direction==4,"vertical split uses explicit save schema");
        check(!SpaceDoorData.validDirection(4,false)&&SpaceDoorData.validDirection(4,true),"vertical split only on Large Doors");
        check(new SpaceDoorData(23,1,true,3,false,true).direction==3,"X motion persistence");
        check(motion==com.vandorlabs.render.SpaceDoorMotion.ROTATING,"motion selector wraps");
        splitGeometry();
        System.out.println("Space door settings PASS: 4176 configurations, persistence, hinges, motion, vertical travel and bounds");
    }
    private static void splitGeometry(){
        for(boolean frame:new boolean[]{false,true})for(int facing=0;facing<4;facing++){
            checkSplitEndpoint(frame,facing);
        }
        com.vandorlabs.render.DoorPanelSplit split=com.vandorlabs.render.DoorPanelSplit.VERTICAL;
        java.util.List<float[]> quad=java.util.Arrays.asList(new float[]{0,0,9F/16,0,0,0,0,1},
                new float[]{1,0,9F/16,1,0,0,0,1},new float[]{1,2,9F/16,1,1,0,0,1},new float[]{0,2,9F/16,0,1,0,0,1});
        for(int panel=0;panel<2;panel++){
            java.util.List<float[]> clipped=split.clip(quad,0,panel);
            check(clipped.size()==4&&clipped.stream().filter(v->v[1]==1&&v[4]==.5F).count()==2,"central cut interpolates UV coordinates");
            int caps=0;
            for(int i=0;i<clipped.size();i++){
                float[][] cap=split.cap(0,panel,0,clipped.get(i),clipped.get((i+1)%clipped.size()));
                if(cap==null)continue;
                check(cap[1][2]==7F/16&&cap[0][2]==9F/16&&Math.abs(cap[0][6])==1,"seam caps keep slab depth and face normal");
                caps++;
            }
            check(caps==1,"one cap closes each newly exposed seam");
        }
    }
    private static void checkSplitEndpoint(boolean frame,int facing){
        java.util.List<com.vandorlabs.render.LargeDoorGeometry.Box> closed=com.vandorlabs.render.LargeDoorGeometry.boxes(frame,true,SpaceDoorData.VERTICAL_SPLIT,false,0,facing);
        java.util.List<com.vandorlabs.render.LargeDoorGeometry.Box> open=com.vandorlabs.render.LargeDoorGeometry.boxes(frame,true,SpaceDoorData.VERTICAL_SPLIT,true,0,facing);
        int start=frame?4:0;
        check(closed.get(start).y1==1.5&&closed.get(start+1).y0==1.5,"closed leaves meet at mid-height");
        double reveal=frame?0:1/16.0;
        check(open.get(start).y1==reveal&&open.get(start+1).y0==3-reveal,"open trim retains frame convention");
        for(int leaf=start;leaf<start+2;leaf++){
            com.vandorlabs.render.LargeDoorGeometry.Box box=open.get(leaf).horizontal(facing);
            check(box.y0==7/16.0&&box.y1==9/16.0,"horizontal split runs along its rotated opening plane");
        }
    }
}

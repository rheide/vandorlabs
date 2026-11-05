package com.vandorlabs.persistence;

/** Literal old-save contracts and full scalar range boundaries, independent of Minecraft classes. */
public final class ControlMountSettingsTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void fields(ControlMountSettings value,int height,int tilt,int direction,String message){require(value.height==height&&value.tilt==tilt&&value.direction==direction,message);}
    public static void main(String[] ignored){
        MemoryPrimitiveData data=new MemoryPrimitiveData();fields(ControlMountSettings.read(data,2,3,1),0,0,0,"missing fields reset to flat rather than preserving previous mount");
        data.putInt("BaseHeight",3);data.putInt("BaseTilt",3);data.putInt("TiltDirection",2);fields(ControlMountSettings.read(data,2,3,1),3,1,2,"unversioned legacy tilt preset");
        data.putString("ControlMountVersion","not an integer");fields(ControlMountSettings.read(data,2,3,1),3,3,2,"any present version suppresses legacy migration");
        data.putInt("BaseHeight",4);fields(ControlMountSettings.read(data,2,3,1),2,3,1,"invalid triple retains all previous fields");
        data.putString("BaseHeight","3");data.putString("BaseTilt","3");data.putString("TiltDirection","2");fields(ControlMountSettings.read(data,2,3,1),0,0,0,"wrong scalar fields retain legacy zero-read behavior");
        int boundaries=0;
        for(int h=-2;h<6;h++)for(int t=-2;t<6;t++)for(int d=-2;d<6;d++){
            boolean expected=h>=0&&h<4&&t>=0&&t<4&&d>=0&&d<4;require(ControlMountSettings.valid(h,t,d)==expected,"mount boundary "+h+","+t+","+d);boundaries++;
        }
        for(int tilt=-2;tilt<=5;tilt++){
            require(ControlMountSettings.migratedTilt(tilt,true)==tilt,"versioned tilt must not be clamped");int[] old={-2,-1,0,1,1,1,4,5};require(ControlMountSettings.migratedTilt(tilt,false)==old[tilt+2],"literal legacy tilt mapping");
        }
        for(int h=0;h<4;h++)for(int t=0;t<4;t++)for(int d=0;d<4;d++){
            MemoryPrimitiveData tag=new MemoryPrimitiveData();ControlMountSettings.write(tag,h,t,d);require(tag.getInt("ControlMountVersion")==2,"stable writer version");fields(ControlMountSettings.read(tag,0,0,0),h,t,d,"version2 mount roundtrip");
        }
        MemoryPrimitiveData raw=new MemoryPrimitiveData();ControlMountSettings.write(raw,7,-1,8);require(raw.getInt("BaseHeight")==7&&raw.getInt("BaseTilt")==-1&&raw.getInt("TiltDirection")==8,"writer must not silently normalize caller-owned values");
        System.out.println("Control mount settings PASS boundaries="+boundaries+" old/versioned/malformed fields, whole previous-state fallback and scalar writer contract");
    }
}

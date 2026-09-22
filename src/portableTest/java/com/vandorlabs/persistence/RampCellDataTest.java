package com.vandorlabs.persistence;

public final class RampCellDataTest {
    private static void check(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        MemoryPrimitiveData legacy=new MemoryPrimitiveData();
        RampCellData defaults=RampCellData.read(legacy);
        check(defaults.top,"legacy cell defaults to top-travel");
        check(defaults.length==1&&defaults.drop==1&&defaults.segments==2
                &&defaults.duration==1,"legacy numeric defaults are safe");

        RampCellData source=new RampCellData(-20,200,500,50,8,0,.25,.75,
                false,true,true,true,Double.NaN,123456789L);
        MemoryPrimitiveData encoded=new MemoryPrimitiveData();
        source.write(encoded);
        RampCellData decoded=RampCellData.read(encoded);
        check(decoded.sourceY==-20&&decoded.length==128&&decoded.row==127,
                "ramp indices clamp");
        check(decoded.drop==16&&decoded.segments==8&&decoded.duration==1,
                "ramp motion values clamp");
        check(decoded.startPose==0&&decoded.startTick==123456789L,
                "ramp animation state round trip");
        check(!decoded.top&&decoded.elevator&&decoded.open&&decoded.moving,
                "ramp flags round trip");
        for (boolean top:new boolean[]{true,false}) {
            MemoryPrimitiveData old=new MemoryPrimitiveData();
            old.putInt(SaveSchema.Ramp.CONTROLLER_VERSION_KEY,5);
            old.putInt(SaveSchema.Ramp.DROP,3); old.putBoolean(SaveSchema.Ramp.TOP,top);
            RampCellData migrated=RampCellData.read(old);
            check(migrated.startOffset==0 && migrated.endOffset==(top?-3:3),"old top and bottom saves preserve motion");
        }
        for (int start=-8;start<=8;start++) for (int end=-8;end<=8;end++) {
            MemoryPrimitiveData signed=new MemoryPrimitiveData();
            signed.putInt(SaveSchema.Ramp.START_OFFSET,start);
            signed.putInt(SaveSchema.Ramp.END_OFFSET,end);
            RampCellData value=RampCellData.read(signed);
            MemoryPrimitiveData roundTrip=new MemoryPrimitiveData(); value.write(roundTrip);
            RampCellData saved=RampCellData.read(roundTrip);
            check(saved.startOffset==start && saved.endOffset==end,"signed endpoints including zero round trip");
        }
        for (int pixels=1;pixels<=16;pixels++) {
            MemoryPrimitiveData data=new MemoryPrimitiveData();
            data.putInt(SaveSchema.Ramp.TREAD_PIXELS,pixels);
            RampCellData value=RampCellData.read(data);
            MemoryPrimitiveData copy=new MemoryPrimitiveData(); value.write(copy);
            check(RampCellData.read(copy).treadPixels==pixels,"every pixel tread size round trips");
        }
        System.out.println("Ramp cell save codec PASS");
    }
}

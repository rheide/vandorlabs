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
        System.out.println("Ramp cell save codec PASS");
    }
}

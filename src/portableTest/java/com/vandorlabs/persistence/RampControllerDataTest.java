package com.vandorlabs.persistence;

public final class RampControllerDataTest {
    private static void check(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        RampControllerData legacy=RampControllerData.read(new MemoryPrimitiveData());
        check(legacy.savedVersion==0&&legacy.top&&legacy.activateOnPower,
                "legacy controller defaults preserved");
        check(legacy.drop==1&&legacy.segments==2&&legacy.duration==1&&legacy.length==1,
                "legacy controller numeric defaults are safe");

        RampControllerData source=new RampControllerData(3,30,8,"Moving",false,false,
                true,true,true,true,true,Double.NaN,45L,46L,0,0,-3,7,true,2,
                .25,.75,true,true,true,-9,true);
        MemoryPrimitiveData encoded=new MemoryPrimitiveData();
        source.write(encoded);
        RampControllerData decoded=RampControllerData.read(encoded);
        check(decoded.savedVersion==SaveSchema.Ramp.CONTROLLER_VERSION,
                "current controller version written");
        check(decoded.drop==16&&decoded.segments==8&&decoded.duration==1
                &&decoded.length==1&&decoded.redstoneChannel==0,"controller values clamp");
        check(decoded.hasDirection&&decoded.direction==2&&decoded.facing==7,
                "controller directions round trip");
        check(decoded.startPose==0&&decoded.startTick==45L&&decoded.lastStepTick==46L,
                "controller animation state round trip");
        check(!decoded.top&&!decoded.activateOnPower&&decoded.slow&&decoded.elevator
                &&decoded.error&&decoded.open&&decoded.moving,"controller flags round trip");
        System.out.println("Ramp controller save codec PASS");
    }
}

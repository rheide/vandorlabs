package com.vandorlabs.physics;

public final class AircraftAttitudeTest {
    public static void main(String[] args) {
        require(AircraftAttitude.pitch(44,0)==44&&AircraftAttitude.pitch(45,0)==45,"hold normal climb angles");
        float pitched=45;
        for(int tick=0;tick<30;tick++)pitched=AircraftAttitude.pitch(pitched,1.5F);
        require(pitched>52&&pitched<AircraftAttitude.MAX_PITCH,"pilot can intentionally exceed 45 degrees");
        require(AircraftAttitude.propulsion(45)==1&&AircraftAttitude.propulsion(-45)==1,"full acceleration through the normal envelope");
        require(AircraftAttitude.propulsion(55)>AircraftAttitude.propulsion(65)&&AircraftAttitude.propulsion(75)>.34,"progressive steep climb acceleration penalty");
        float recovered=AircraftAttitude.pitch(pitched,0);
        require(recovered<pitched&&recovered>45,"steep climb eases toward 45 degrees upon release");
        for(int tick=0;tick<150;tick++)recovered=AircraftAttitude.pitch(recovered,0);
        require(Math.abs(recovered-45)<.001,"trim settles at 45 degrees");

        float bank=AircraftAttitude.bank(0,1);
        require(bank>0&&bank<AircraftAttitude.MAX_BANK&&AircraftAttitude.bank(0,-1)==-bank,"A and D bank smoothly in opposing directions");
        for(int tick=0;tick<15;tick++)bank=AircraftAttitude.bank(bank,1);
        require(bank==AircraftAttitude.MAX_BANK,"bank reaches its bound");
        for(int tick=0;tick<15;tick++)bank=AircraftAttitude.bank(bank,0);
        require(bank==0,"neutral steering eases back to level");
        require(!AircraftAttitude.validBank(Float.NaN)&&!AircraftAttitude.validBank(33)&&AircraftAttitude.validBank(-32),"valid wire/save bank");
        require(!AircraftAttitude.validPitch(Float.POSITIVE_INFINITY)&&AircraftAttitude.validPitch(74)&&!AircraftAttitude.validPitch(-46),"valid wire/save pitch");
        System.out.println("Aircraft attitude PASS proportional steep trim acceleration signed self-centering bank");
    }

    private static void require(boolean result,String message) {
        if(!result)throw new AssertionError(message);
    }

    private AircraftAttitudeTest() {}
}

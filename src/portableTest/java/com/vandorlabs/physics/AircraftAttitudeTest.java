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

        float rate=0,bank=0,travel=0;
        boolean inverted=false;
        for(int tick=0;tick<55;tick++){
            rate=AircraftAttitude.rollRate(rate,1,2);
            float next=AircraftAttitude.bank(bank,rate);
            float step=AircraftAttitude.wrap(next-bank);
            require(step>0&&step<9,"right roll remains continuous through the signed boundary");
            travel+=step;
            bank=next;
            inverted|=Math.abs(bank)>175;
        }
        require(inverted&&travel>360,"continuous bank completes an actual rotation");
        require(AircraftAttitude.rollRate(0,-1,2)==-AircraftAttitude.rollRate(0,1,2),"left and right use symmetric rates");
        for(int tick=0;tick<10;tick++){
            rate=AircraftAttitude.rollRate(rate,0,2);
            bank=AircraftAttitude.bank(bank,rate);
        }
        require(rate==0&&Math.abs(bank)>20&&AircraftAttitude.bank(bank,rate)==bank,"released controls hold the selected bank");
        require(AircraftAttitude.levelBank(-179)==-175&&AircraftAttitude.levelBank(3)==0,"recovery levels the bank on the shorter arc");
        require(AircraftAttitude.coordinatedTurn(Math.PI/4,2)>0&&AircraftAttitude.coordinatedTurn(-Math.PI/4,2)<0,
                "turn follows the lowered wing");
        require(AircraftAttitude.bankSink(Math.PI,2)>AircraftAttitude.bankSink(Math.PI/2,2)&&AircraftAttitude.bankSink(0,2)==0,
                "inversion loses vertical lift");
        float nose=28;
        for(int tick=0;tick<20;tick++)nose=AircraftAttitude.recoveryPitch(nose,nose+1,30,false);
        require(nose<0&&AircraftAttitude.recoveryPitch(-10,-20,30,false)==-20,"powered ceiling trim lowers the nose gradually and respects a dive");
        require(!AircraftAttitude.validBank(Float.NaN)&&!AircraftAttitude.validBank(180)&&AircraftAttitude.validBank(-180),"valid full-circle wire and saved bank");
        require(!AircraftAttitude.validPitch(Float.POSITIVE_INFINITY)&&AircraftAttitude.validPitch(74)
                &&AircraftAttitude.validPitch(-85)&&!AircraftAttitude.validPitch(-86),"valid wire and saved pitch");
        System.out.println("Aircraft attitude PASS steep trim, proportional acceleration, signed full roll, held bank, loss of lift and ceiling recovery");
    }

    private static void require(boolean result,String message) {
        if(!result)throw new AssertionError(message);
    }

    private AircraftAttitudeTest() {}
}

package com.vandorlabs.physics;

/** Thresholds and continuous terrain contact used by both normal and cruise Flight. */
public final class FlightEnvelopeTest {
    private static void require(boolean valid,String message) {
        if(!valid)throw new AssertionError(message);
    }

    public static void main(String[] args) {
        require(FlightEnvelope.weightEquation(230,200).equals("Weight: 230 actual - 30 hover plates = 200"),"displayed lift reduction");
        double limit=FlightEnvelope.takeoffMass(1);
        // Masses below were chosen against the original 512 takeoff limit; keep them proportional.
        double k=limit/512;
        require(FlightEnvelope.canFly(limit,1)&&!FlightEnvelope.canFly(Math.nextUp(limit),1),"maximum weight inclusive");
        require(!FlightEnvelope.canFly(1,0)&&FlightEnvelope.maximumAltitude(1,0)==0,"no fueled drive");
        require(FlightEnvelope.effectiveMass(100,0,0)==100,"unassisted mass");
        double assisted=FlightEnvelope.effectiveMass(100,2,3);
        require(assisted<FlightEnvelope.effectiveMass(100,1,0)&&assisted>25,"lift reduction has diminishing returns");
        require(FlightEnvelope.acceleration(assisted,1)>FlightEnvelope.acceleration(470*k,1),"light craft accelerate faster");
        require(FlightEnvelope.maximumAltitude(limit,1)==FlightEnvelope.WORLD_CEILING,"zero altitude reserve at takeoff limit");
        require(FlightEnvelope.maximumAltitude(470*k,1)>FlightEnvelope.WORLD_CEILING&&FlightEnvelope.maximumAltitude(470*k,1)<FlightEnvelope.MAX_ALTITUDE,"underpowered altitude envelope");
        require(Math.abs(FlightEnvelope.maximumAltitude(limit,2)-4245.76)<.01,"twice takeoff thrust reaches Y=4246");
        require(FlightEnvelope.maximumAltitude(limit,3.59)<FlightEnvelope.MAX_ALTITUDE&&
                FlightEnvelope.maximumAltitude(limit,3.60)==FlightEnvelope.MAX_ALTITUDE,"Y=10000 needs about 3.60 times takeoff thrust");
        require(FlightEnvelope.cruiseSpeed(256)==1.5&&FlightEnvelope.cruiseSpeed(300)<1.51,"continuous handoff at build height");
        require(FlightEnvelope.cruiseSpeed(10000)==15&&FlightEnvelope.cruiseSpeed(20000)==15,"speed cap");

        require(FlightEnvelope.cruiseSpeed(256,200*k,1)==1.5,"light drive handoff is continuous");
        require(FlightEnvelope.cruiseSpeed(257,200*k,1)<1.62,"first cruise block has no speed jump");
        require(FlightEnvelope.cruiseSpeed(320,200*k,1)<1.8,"low approach speed at terrain handoff");
        require(FlightEnvelope.cruiseSpeed(600,200*k,1)*20>=100,"cruise performance returns above the transition");
        for(int y=256;y<=512;y++)
            require(FlightEnvelope.cruiseSpeed(y+1,200*k,1)-FlightEnvelope.cruiseSpeed(y,200*k,1)<.016,
                    "climb speed changes gradually throughout the approach");
        require(FlightEnvelope.cruiseSpeed(800,400*k,1)<FlightEnvelope.cruiseSpeed(800,200*k,1),"payload reduces cruise speed");
        require(FlightEnvelope.cruiseSpeed(10000,120*k,1)==15,"light craft retain the absolute speed cap");
        require(FlightEnvelope.cruiseSpeed(10000,limit,1)==1.5,"takeoff-limited load cannot accelerate into cruise");

        require(FlightEnvelope.seedOpacity(255,512)==0&&FlightEnvelope.seedOpacity(288)==.5&&FlightEnvelope.seedOpacity(513,512)==1,
                "seed transition endpoints and midpoint");
        require(FlightEnvelope.seedOpacity(288,320)==.5&&FlightEnvelope.seedOpacity(320,320)==1
                &&FlightEnvelope.seedOpacity(256,256)==0&&FlightEnvelope.seedOpacity(257,256)==1,"short or direct handoff");
        require(FlightEnvelope.sustainableClimb(3,500)==3&&FlightEnvelope.sustainableClimb(3,48)==.75
                &&FlightEnvelope.sustainableClimb(3,0)==0&&FlightEnvelope.sustainableClimb(-2,0)==-2,
                "climb rate tapers to the service ceiling and descent remains available");
        double speed=FlightEnvelope.MAX_CRUISE_SPEED,y=FlightEnvelope.DESCENT_BRAKING_ALTITUDE;
        while(y>250){
            double previous=speed;
            double drop=speed; // Steepest possible approach, one tick at a time.
            speed=FlightEnvelope.descentSpeed(speed,y,drop);
            require(speed<=previous && previous-speed<=.150001,"bounded descent braking");
            y-=drop;
            if(y<=320)require(speed<1.55,"shed excess speed before default handoff");
        }
        require(speed==FlightEnvelope.LOW_SPEED&&FlightEnvelope.descentSpeed(1,290,60)==1,"low-flight limit without speeding up a slow craft");
        require(FlightEnvelope.descentSpeed(15,320,12)>=14.85,"late legacy overspeed begins braking smoothly");
        require(FlightEnvelope.descentSpeed(15,400,0)==15,"level flight retains speed");

        // 3.1 Forge rules.
        require(Math.abs(FlightEnvelope.runwayLength(0,1)-FlightTuning.RUNWAY_MIN)<1e-9
                &&Math.abs(FlightEnvelope.runwayLength(limit,1)-FlightTuning.RUNWAY_MAX)<1e-9
                &&FlightEnvelope.runwayLength(limit/2,1)>FlightTuning.RUNWAY_MIN&&FlightEnvelope.runwayLength(limit/2,1)<FlightTuning.RUNWAY_MAX,"runway grows with load");
        for(double mass:new double[]{1,limit/4,limit*300/512,limit}){
            double a=FlightEnvelope.takeoffAcceleration(mass,1),v=0,run=0;
            while(v<FlightTuning.RUNWAY_TAKEOFF_SPEED){v=Math.min(FlightTuning.RUNWAY_TAKEOFF_SPEED,v+a);run+=v;}
            require(Math.abs(run-FlightEnvelope.runwayLength(mass,1))<1,"discrete roll covers the planned runway, mass="+mass+" run="+run);
        }
        require(FlightEnvelope.lowAltitudeSpeed(limit,1)==FlightTuning.LOW_SPEED_MIN&&FlightEnvelope.lowAltitudeSpeed(limit/2,1)==FlightTuning.LOW_SPEED_MAX
                &&FlightEnvelope.lowAltitudeSpeed(limit/4,1)==FlightTuning.LOW_SPEED_MAX,"low-altitude speed follows thrust reserve up to twice the need");
        require(FlightEnvelope.reserveCruiseSpeed(200,limit/2,1)==FlightTuning.LOW_SPEED_MAX
                &&FlightEnvelope.reserveCruiseSpeed(256,limit,1)==FlightTuning.LOW_SPEED_MIN,"reserve speed holds below the cruise band");
        require(FlightEnvelope.reserveCruiseSpeed(10000,120*k,1)==15&&FlightEnvelope.reserveCruiseSpeed(1,1,0)==0,"reserve speed keeps the cap and needs thrust");
        for(int h=256;h<=600;h++)require(FlightEnvelope.reserveCruiseSpeed(h+1,300*k,1)>=FlightEnvelope.reserveCruiseSpeed(h,300*k,1),"reserve speed rises with altitude");
        require(FlightEnvelope.cruiseSpeed(256,200*k,1)==1.5,"3.0 cruise speed is unchanged for NeoForge");
        require(Math.abs(FlightEnvelope.burnLoadFactor(limit,1)-1)<1e-12&&Math.abs(FlightEnvelope.burnLoadFactor(1,0)-1)<1e-12
                &&Math.abs(FlightEnvelope.burnLoadFactor(0,1)-(1-FlightTuning.MAXWELL_LOAD_BURN_SHARE))<1e-12
                &&FlightEnvelope.burnLoadFactor(100*k,1)<FlightEnvelope.burnLoadFactor(400*k,1),"lighter craft burn less");

        // 3.1 landing approach.
        require(FlightEnvelope.approachSpeed(2,40,false)==2&&FlightEnvelope.approachSpeed(2,10,true)==2,"approach rule leaves high and climbing flight alone");
        require(Math.abs(FlightEnvelope.approachSpeed(2,FlightTuning.APPROACH_FLOOR,false)-FlightTuning.APPROACH_SPEED)<1e-9
                &&FlightEnvelope.approachSpeed(2,1,false)==FlightTuning.APPROACH_SPEED,"near the ground the target is the approach speed");
        double previousTarget=0;
        for(int h=0;h<=30;h++){double v=FlightEnvelope.approachSpeed(2,h,false);require(v>=previousTarget&&v<=2,"approach target rises with clearance");previousTarget=v;}
        require(FlightEnvelope.approachSpeed(.5,1,false)==.5,"a slower target is never raised");
        double average=FlightEnvelope.smoothClearance(Double.NaN,80);
        average=FlightEnvelope.smoothClearance(average,5);
        require(average>30,"a one-tick dip over a tall feature does not trigger the approach: "+average);
        for(int tick=0;tick<16;tick++)average=FlightEnvelope.smoothClearance(average,5);
        require(average<8,"sustained low flight converges on the clearance: "+average);
        require(FlightEnvelope.smoothClearance(10,Double.POSITIVE_INFINITY)>10,"unknown clearance reads as high");

        OrientedBounds hull=new OrientedBounds(0,0,0,.04,.5,.5,RigidTransform.IDENTITY);
        OrientedBounds pane=new OrientedBounds(.4,0,0,.42,.5,.5,RigidTransform.IDENTITY);
        require(!hull.intersects(pane)&&hull.sweepsInto(pane,1.5,0,0),"thin terrain crossed between positional samples");
        require(!hull.sweepsInto(pane,-1.5,0,0)&&!hull.sweepsInto(pane,.32,0,0),"moving away and stopping short stay clear");
        OrientedBounds side=new OrientedBounds(.4,.5,0,.42,1,.5,RigidTransform.IDENTITY);
        require(!hull.sweepsInto(side,1.5,0,0),"approach along a touching face stays clear");
        RigidTransform rotated=RigidTransform.rotation(0,1,0,-45);
        OrientedBounds tilted=new OrientedBounds(0,0,0,.04,.5,.5,rotated);
        require(tilted.sweepsInto(pane,1.5,0,-.25),"oriented hull sweeps on all separating axes");
        System.out.println("Flight envelope PASS takeoff-limit lift acceleration diminishing-altitude-ceiling cruise-cap thin-terrain-sweep");
    }

    private FlightEnvelopeTest() {}
}

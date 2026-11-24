package com.vandorlabs.physics;

/** Thresholds and continuous terrain contact used by both normal and cruise Flight. */
public final class FlightEnvelopeTest {
    private static void require(boolean valid,String message) {
        if(!valid)throw new AssertionError(message);
    }

    public static void main(String[] args) {
        require(FlightEnvelope.weightEquation(230,200).equals("Weight: 230 actual - 30 hover plates = 200"),"displayed lift reduction");
        double limit=FlightEnvelope.takeoffMass(1);
        require(FlightEnvelope.canFly(limit,1)&&!FlightEnvelope.canFly(Math.nextUp(limit),1),"maximum weight inclusive");
        require(!FlightEnvelope.canFly(1,0)&&FlightEnvelope.maximumAltitude(1,0)==0,"no fueled drive");
        require(FlightEnvelope.effectiveMass(100,0,0)==100,"unassisted mass");
        double assisted=FlightEnvelope.effectiveMass(100,2,3);
        require(assisted<FlightEnvelope.effectiveMass(100,1,0)&&assisted>25,"lift reduction has diminishing returns");
        require(FlightEnvelope.acceleration(assisted,1)>FlightEnvelope.acceleration(470,1),"light craft accelerate faster");
        require(FlightEnvelope.maximumAltitude(limit,1)==FlightEnvelope.WORLD_CEILING,"zero altitude reserve at takeoff limit");
        require(FlightEnvelope.maximumAltitude(470,1)>FlightEnvelope.WORLD_CEILING&&FlightEnvelope.maximumAltitude(470,1)<FlightEnvelope.MAX_ALTITUDE,"underpowered altitude envelope");
        require(Math.abs(FlightEnvelope.maximumAltitude(limit,2)-4245.76)<.01,"twice takeoff thrust reaches Y=4246");
        require(FlightEnvelope.maximumAltitude(limit,3.59)<FlightEnvelope.MAX_ALTITUDE&&
                FlightEnvelope.maximumAltitude(limit,3.60)==FlightEnvelope.MAX_ALTITUDE,"Y=10000 needs about 3.60 times takeoff thrust");
        require(FlightEnvelope.cruiseSpeed(256)==1.5&&FlightEnvelope.cruiseSpeed(300)<1.51,"continuous handoff at build height");
        require(FlightEnvelope.cruiseSpeed(10000)==15&&FlightEnvelope.cruiseSpeed(20000)==15,"speed cap");

        require(FlightEnvelope.cruiseSpeed(256,200,1)==1.5,"light drive handoff is continuous");
        require(FlightEnvelope.cruiseSpeed(257,200,1)<1.62,"first cruise block has no speed jump");
        require(FlightEnvelope.cruiseSpeed(320,200,1)*20>=100,"one Maxwell with modest cargo can reach 100 m/s soon above the build ceiling");
        require(FlightEnvelope.cruiseSpeed(800,400,1)<FlightEnvelope.cruiseSpeed(800,200,1),"payload reduces cruise speed");
        require(FlightEnvelope.cruiseSpeed(10000,120,1)==15,"light craft retain the absolute speed cap");
        require(FlightEnvelope.cruiseSpeed(10000,512,1)==1.5,"takeoff-limited load cannot accelerate into cruise");

        require(FlightEnvelope.seedOpacity(255,512)==0&&FlightEnvelope.seedOpacity(288)==.5&&FlightEnvelope.seedOpacity(513,512)==1,
                "seed transition endpoints and midpoint");
        require(FlightEnvelope.seedOpacity(288,320)==.5&&FlightEnvelope.seedOpacity(320,320)==1
                &&FlightEnvelope.seedOpacity(256,256)==0&&FlightEnvelope.seedOpacity(257,256)==1,"short or direct handoff");
        require(FlightEnvelope.sustainableClimb(3,500)==3&&FlightEnvelope.sustainableClimb(3,48)==.75
                &&FlightEnvelope.sustainableClimb(3,0)==0&&FlightEnvelope.sustainableClimb(-2,0)==-2,
                "climb rate tapers to the service ceiling and descent remains available");
        double speed=75,y=520;
        while(y>250){
            double previous=speed;
            speed=FlightEnvelope.descentSpeed(speed,y,5);
            require(speed<=previous && previous-speed<1.5,"progressive descent braking");
            y-=5;
        }
        require(speed==FlightEnvelope.LOW_SPEED&&FlightEnvelope.descentSpeed(1,290,60)==1,"low-flight limit without speeding up a slow craft");
        require(FlightEnvelope.descentSpeed(55,400,0)==55,"level flight retains speed");

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

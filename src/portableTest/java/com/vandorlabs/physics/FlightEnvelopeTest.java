package com.vandorlabs.physics;

/** Thresholds and continuous terrain contact used by both normal and cruise Flight. */
public final class FlightEnvelopeTest {
    private static void require(boolean valid,String message) {
        if(!valid)throw new AssertionError(message);
    }

    public static void main(String[] args) {
        double limit=FlightEnvelope.takeoffMass(1);
        require(FlightEnvelope.canFly(limit,1)&&!FlightEnvelope.canFly(Math.nextUp(limit),1),"maximum weight inclusive");
        require(!FlightEnvelope.canFly(1,0)&&FlightEnvelope.maximumAltitude(1,0)==0,"no fueled drive");
        require(FlightEnvelope.effectiveMass(100,0,0)==100,"unassisted mass");
        double assisted=FlightEnvelope.effectiveMass(100,2,3);
        require(assisted<FlightEnvelope.effectiveMass(100,1,0)&&assisted>25,"lift reduction has diminishing returns");
        require(FlightEnvelope.acceleration(assisted,1)>FlightEnvelope.acceleration(470,1),"light craft accelerate faster");
        require(FlightEnvelope.maximumAltitude(limit,1)==FlightEnvelope.WORLD_CEILING,"zero altitude reserve at takeoff limit");
        require(FlightEnvelope.maximumAltitude(470,1)>FlightEnvelope.WORLD_CEILING&&FlightEnvelope.maximumAltitude(470,1)<FlightEnvelope.MAX_ALTITUDE,"underpowered altitude envelope");
        require(FlightEnvelope.maximumAltitude(470,2)==FlightEnvelope.MAX_ALTITUDE,"second drive reaches hard ceiling");
        require(FlightEnvelope.cruiseSpeed(256)==1.5&&FlightEnvelope.cruiseSpeed(300)<1.51,"continuous handoff at build height");
        require(FlightEnvelope.cruiseSpeed(10000)==75&&FlightEnvelope.cruiseSpeed(20000)==75,"speed cap");

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

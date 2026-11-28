package com.vandorlabs.vehicle;

import java.util.ArrayDeque;

/** One accepted command represents one controller step, not merely one received packet. */
public final class VehicleControls {
    public static final int LIMIT=64;
    /** Shared by authoritative movement and prediction; reverse follows actual travel. */
    public static float yawChange(float steering,double speed,boolean hover,boolean autopilot){
        if(hover)return autopilot?0:steering*1.7F*(speed<0?-1:1);
        return (float)(steering*speed*11*(1-.45*Math.abs(speed)/.28));
    }
    /** Radius is averaged before converting to angular travel; one bounded step still uses normal collision sweeps. */
    public static float yawChange(float steering,double speed,boolean hover,boolean autopilot,VehicleSteering.Profile supports,boolean grounded){
        if(hover)return yawChange(steering,speed,true,autopilot);
        double travel=Math.abs(speed);
        double turn=Math.abs(yawChange(1,speed,false,autopilot)/supports.radiusFactor);
        if(supports.radiusFactor<1){
            // Let tight-radius running gear build yaw gradually through its crawl range.
            double blend=Math.min(1,travel/.24);blend=blend*blend*(3-2*blend);
            turn=Math.min(turn*(.35+.65*blend),.6+2.6*blend);
        }
        // One continuous curve replaces the old 2.4-degree pivot / rolling threshold.
        if(supports.tracksOnly()&&grounded)turn=Math.max(.6,turn);
        return (float)(steering*(speed<0?-1:1)*Math.min(8,turn));
    }
    public static float autopilotYawLimit(VehicleSteering.Profile supports,boolean hover){return hover?3:(float)Math.min(8,3/supports.radiusFactor);}
    public static final class Command {
        public final int sequence;
        public final byte forward,strafe;
        public final boolean brake;
        public Command(int sequence,byte forward,byte strafe,boolean brake){this.sequence=sequence;this.forward=forward;this.strafe=strafe;this.brake=brake;}
    }
    private final ArrayDeque<Command> queue=new ArrayDeque<>();
    private int received=-1,applied=-1;
    public boolean offer(int sequence,byte forward,byte strafe,boolean brake) {
        if(sequence<=received || forward< -1 || forward>1 || strafe< -1 || strafe>1 || queue.size()>=LIMIT)return false;
        received=sequence;queue.addLast(new Command(sequence,forward,strafe,brake));return true;
    }
    /** Hover has no client prediction replay; an input burst is the latest held state. */
    public boolean offerLatest(int sequence,byte forward,byte strafe,boolean brake){return offerLatest(sequence,forward,strafe,brake,false);}
    /** @param keepTaps Flight: an unapplied new press survives the release that follows it within one server tick */
    public boolean offerLatest(int sequence,byte forward,byte strafe,boolean brake,boolean keepTaps){
        if(sequence<=received || forward< -1 || forward>1 || strafe< -1 || strafe>1)return false;
        // Keep an unapplied steering or pitch press ahead of the release that ends it: on a slow server
        // both can arrive within one tick, and dropping the press would turn a brief tap into nothing.
        Command pending=queue.peekLast();
        boolean tap=keepTaps&&pending!=null&&(pending.forward!=0||pending.strafe!=0)&&(pending.forward!=forward||pending.strafe!=strafe)
                &&(pending.forward!=appliedForward||pending.strafe!=appliedStrafe);
        received=sequence;queue.clear();if(tap)queue.addLast(pending);queue.addLast(new Command(sequence,forward,strafe,brake));return true;
    }
    private byte appliedForward,appliedStrafe;
    public Command poll(){Command command=queue.pollFirst();if(command!=null){applied=command.sequence;appliedForward=command.forward;appliedStrafe=command.strafe;}return command;}
    public int acknowledged(){return applied;}
    public int size(){return queue.size();}
    public void clear(){queue.clear();received=applied=-1;appliedForward=appliedStrafe=0;}
}

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
            // Tight-radius running gear should ease into a turn while creeping.
            // Keep the existing cruising radius once travel reaches .12 blocks/tick.
            double blend=Math.min(1,travel/.12);blend=blend*blend*(3-2*blend);
            turn=Math.min(turn*(.35+.65*blend),.6+7.4*blend);
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
    public boolean offerLatest(int sequence,byte forward,byte strafe,boolean brake){
        if(sequence<=received || forward< -1 || forward>1 || strafe< -1 || strafe>1)return false;
        received=sequence;queue.clear();queue.addLast(new Command(sequence,forward,strafe,brake));return true;
    }
    public Command poll(){Command command=queue.pollFirst();if(command!=null)applied=command.sequence;return command;}
    public int acknowledged(){return applied;}
    public int size(){return queue.size();}
    public void clear(){queue.clear();received=applied=-1;}
}

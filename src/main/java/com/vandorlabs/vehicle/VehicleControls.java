package com.vandorlabs.vehicle;

import java.util.ArrayDeque;

/** One accepted command represents one controller step, not merely one received packet. */
public final class VehicleControls {
    public static final int LIMIT=64;
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

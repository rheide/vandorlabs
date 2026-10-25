package com.vandorlabs.render;

/** Vanilla 1.12 stair joining, including its same-half side blockers and outer-first priority. */
public final class StairJoinPolicy {
    public static final class State {
        public final boolean stair,upper;public final int facing;
        public State(boolean stair,int facing,boolean upper){this.stair=stair;this.facing=facing;this.upper=upper;}
    }
    public interface Neighbors {State at(int facing);}
    private StairJoinPolicy(){}
    public static int opposite(int facing){return facing^1;}
    public static int counterClockwise(int facing){switch(facing){case 2:return 4;case 4:return 3;case 3:return 5;case 5:return 2;default:throw new IllegalArgumentException("Horizontal stair facing required");}}
    private static boolean sameHalf(State other,boolean upper){return other!=null&&other.stair&&other.upper==upper;}
    private static boolean different(int facing,boolean upper,State other){return !sameHalf(other,upper)||other.facing!=facing;}
    public static StairGeometry.Shape shape(int facing,boolean upper,Neighbors neighbors){
        State front=neighbors.at(facing);
        if(sameHalf(front,upper)&&(front.facing>=4)!=(facing>=4)&&different(facing,upper,neighbors.at(opposite(front.facing))))return front.facing==counterClockwise(facing)?StairGeometry.Shape.OUTER_LEFT:StairGeometry.Shape.OUTER_RIGHT;
        State back=neighbors.at(opposite(facing));
        if(sameHalf(back,upper)&&(back.facing>=4)!=(facing>=4)&&different(facing,upper,neighbors.at(back.facing)))return back.facing==counterClockwise(facing)?StairGeometry.Shape.INNER_LEFT:StairGeometry.Shape.INNER_RIGHT;
        return StairGeometry.Shape.STRAIGHT;
    }
}

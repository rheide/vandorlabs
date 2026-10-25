package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.List;

public final class StairJoinPolicyTest {
    private static int checks;
    private static void check(boolean b){checks++;if(!b)throw new AssertionError("Stair join "+checks);}
    private static StairJoinPolicy.State option(int i){return i==0?null:new StairJoinPolicy.State(true,new int[]{2,3,4,5}[(i-1)%4],i>4);}
    private static boolean same(StairJoinPolicy.State n,boolean upper){return n!=null&&n.stair&&n.upper==upper;}
    private static boolean block(StairJoinPolicy.State n,boolean upper){return same(n,upper)&&n.facing==2;}
    /** Independent north-facing truth table: left=W, right=E; outer wins when both exist. */
    private static StairGeometry.Shape north(StairJoinPolicy.State[] n,boolean upper){
        if(same(n[2],upper)&&n[2].facing==4&&!block(n[5],upper))return StairGeometry.Shape.OUTER_LEFT;
        if(same(n[2],upper)&&n[2].facing==5&&!block(n[4],upper))return StairGeometry.Shape.OUTER_RIGHT;
        if(same(n[3],upper)&&n[3].facing==4&&!block(n[4],upper))return StairGeometry.Shape.INNER_LEFT;
        if(same(n[3],upper)&&n[3].facing==5&&!block(n[5],upper))return StairGeometry.Shape.INNER_RIGHT;
        return StairGeometry.Shape.STRAIGHT;
    }
    private static int rotate(int facing,int count){for(int i=0;i<count;i++)facing=StairJoinPolicy.counterClockwise(facing);return facing;}
    public static void main(String[] args){int matrices=0;
        for(int a=0;a<9;a++)for(int b=0;b<9;b++)for(int c=0;c<9;c++)for(int d=0;d<9;d++){
            StairJoinPolicy.State[] original={null,null,option(a),option(b),option(c),option(d)};
            for(boolean upper:new boolean[]{false,true})for(int turn=0;turn<4;turn++){
                StairJoinPolicy.State[] rotated=new StairJoinPolicy.State[6];for(int face=2;face<6;face++){StairJoinPolicy.State s=original[face];rotated[rotate(face,turn)]=s==null?null:new StairJoinPolicy.State(s.stair,rotate(s.facing,turn),s.upper);}
                check(StairJoinPolicy.shape(rotate(2,turn),upper,f->rotated[f])==north(original,upper));matrices++;
            }
        }
        List<Integer> queried=new ArrayList<>();StairGeometry.Shape outer=StairJoinPolicy.shape(2,false,face->{queried.add(face);return face==2?new StairJoinPolicy.State(true,4,false):face==3?new StairJoinPolicy.State(true,5,false):null;});check(outer==StairGeometry.Shape.OUTER_LEFT);check(queried.size()==2&&queried.get(0)==2&&queried.get(1)==5);
        check(StairJoinPolicy.shape(2,false,f->new StairJoinPolicy.State(false,4,false))==StairGeometry.Shape.STRAIGHT);
        System.out.println("StairJoinPolicyTest PASS "+matrices+" neighborhood/half/rotation matrices "+checks+" checks");
    }
}

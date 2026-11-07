package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.ramp.*;
import java.util.*;
import net.minecraft.world.phys.*;

/** Released horizontal belt selection/anchoring/landing policy, without entity mutation. */
public final class NativeRampBeltTargets {
    public record Candidate(int key,Vec3 position,AABB body,boolean player,boolean item,boolean removed,boolean noPhysics,boolean riding,boolean flying,boolean spectator,Vec3 velocity,double stepHeight,Vec3 history){}
    public record Move(Candidate actor,Vec3 anchor,Vec3 delta) {public Vec3 target(){return (anchor==null?actor.position():anchor).add(delta);}}
    public record Result(double nextDistance,List<Move> moves){public Result{moves=List.copyOf(moves);}}
    @FunctionalInterface public interface Obstacles {List<AABB> query(Candidate actor,AABB bounds);}
    private static final long WORK=16777216;
    static void profile(NativeRampFootprint.Spec spec){if(spec.moat()||spec.elevator()||spec.travel()!=0||spec.extend()||spec.start()!=0||spec.length()*16<=spec.treadPixels())throw new IllegalArgumentException("Unsupported deployed belt profile");}
    public static double delta(NativeRampFootprint.Spec spec,int mode,int speed){if(mode<1||mode>2||speed<0||speed>2)throw new IllegalArgumentException("Belt control profile");return EscalatorGeometry.rate(speed)*VerticalRampGeometry.beltSign(spec.verticalLayout()&&!spec.moat(),spec.end(),mode);}
    public static AABB bounds(NativeRampFootprint.Spec spec){
        var first=spec.origins().getFirst().position();double x=first.getX(),z=first.getZ(),X=x+1,Z=z+1;
        for(var source:spec.origins()){var p=source.position();x=Math.min(x,p.getX());z=Math.min(z,p.getZ());X=Math.max(X,p.getX()+1);Z=Math.max(Z,p.getZ()+1);}
        return new AABB(x,first.getY()+spec.low()+Math.min(0,spec.end()),z,X,first.getY()+spec.high()+Math.max(0,spec.end())+.6,Z);
    }
    public static boolean eligible(Candidate actor){return !actor.removed()&&(!actor.noPhysics()||actor.item())&&!actor.riding()&&(!(actor.velocity().y>.1)||actor.item()&&actor.history()!=null)&&(!actor.player()||!actor.flying()&&!actor.spectator());}
    public static Result select(NativeRampFootprint.Spec spec,boolean shaped,double distance,int mode,int speed,List<Candidate> candidates,Obstacles obstacles){
        profile(spec);if(spec.verticalLayout())throw new IllegalArgumentException("Separate vertical belt adapter");if(!Double.isFinite(distance)||Math.abs(distance)>1e12||candidates.size()>8192)throw new IllegalArgumentException("Belt selection bound");
        double delta=delta(spec,mode,speed),next=EscalatorGeometry.phase(distance+delta,spec.length()*16);var platform=bounds(spec);
        var riders=new ArrayList<Candidate>();for(var actor:candidates)if(eligible(actor)&&actor.body().intersects(platform))riders.add(actor);
        if(riders.isEmpty())return new Result(next,List.of());
        var before=NativeRampBeltGeometry.surfaces(spec,shaped,distance);var after=NativeRampBeltGeometry.surfaces(spec,shaped,distance+delta);
        if((long)(before.size()+after.size())*riders.size()>WORK)throw new IllegalArgumentException("Belt support work budget");var moves=new ArrayList<Move>();
        for(var actor:riders){
            var body=actor.body();Vec3 anchor=null;
            if(actor.item()&&actor.history()!=null&&actor.position().distanceToSqr(actor.history())<.5625){
                var old=actor.history();var anchored=body.move(old.x-actor.position().x,old.y-actor.position().y,old.z-actor.position().z);boolean clear=true;
                for(var obstacle:obstacles.query(actor,anchored))if(obstacle.intersects(anchored)){clear=false;break;}
                if(clear&&Math.abs(anchored.minY-support(before,anchored))<1e-6){body=anchored;anchor=old;}
            }
            if(Math.abs(body.minY-support(before,body))>(actor.player()?.5:.15))continue;
            double dx=spec.facing().getStepX()*delta,dz=spec.facing().getStepZ()*delta,nextTop=support(after,body.move(dx,0,dz));double dy=Double.isInfinite(nextTop)?0:nextTop-body.minY;
            double landing=landing(spec,actor,body,platform,delta,obstacles);if(landing>0)dy=Math.max(dy,landing);
            var clipped=NativeRampRiders.clipped(body,new Vec3(dx,dy,dz),obstacles.query(actor,body.expandTowards(dx,dy,dz).inflate(1e-7)));
            if(dy<0&&clipped.y>dy)dy=clipped.y;
            if(Math.abs(clipped.y-dy)>1e-6)return null;
            moves.add(new Move(actor,anchor,clipped));
        }
        return new Result(next,moves);
    }
    static double support(List<AABB> surfaces,AABB body){double top=Double.NEGATIVE_INFINITY;for(var b:surfaces)if(body.maxX>b.minX+1e-7&&body.minX<b.maxX-1e-7&&body.maxZ>b.minZ+1e-7&&body.minZ<b.maxZ-1e-7)top=Math.max(top,b.maxY);return top;}
    private static double landing(NativeRampFootprint.Spec s,Candidate actor,AABB body,AABB platform,double delta,Obstacles obstacles){
        if(!actor.player()||Math.abs(s.end())<1e-7)return 0;var previous=actor.history();double walkX=previous==null?actor.velocity().x:actor.position().x-previous.x,walkZ=previous==null?actor.velocity().z:actor.position().z-previous.z;
        if((walkX*s.facing().getStepX()+walkZ*s.facing().getStepZ())*delta>=-1e-6)return 0;
        int sign=s.end()>=0?-1:1;double x=s.facing().getStepX()*sign,z=s.facing().getStepZ()*sign,centerX=(body.minX+body.maxX)*.5,centerZ=(body.minZ+body.maxZ)*.5;
        double edge=x<0?centerX-platform.minX:x>0?platform.maxX-centerX:z<0?centerZ-platform.minZ:platform.maxZ-centerZ;if(edge>.65)return 0;
        var toward=body.move(x*.2,0,z*.2);double top=body.minY;
        for(var b:obstacles.query(actor,body.expandTowards(x*.4,actor.stepHeight(),z*.4)))if(toward.maxX>b.minX&&toward.minX<b.maxX&&toward.maxZ>b.minZ&&toward.minZ<b.maxZ&&b.maxY>top&&b.maxY-body.minY<=actor.stepHeight()+1e-7)top=b.maxY;
        double rise=top-body.minY;if(rise<=0)return 0;var raised=body.move(0,rise,0);for(var b:obstacles.query(actor,raised))if(b.intersects(raised))return 0;return rise;
    }
    private NativeRampBeltTargets(){}
}

package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.ramp.*;
import java.util.*;
import net.minecraft.world.phys.*;

/** World-free released ordinary/stair/sideways rider selection. No ownership publication. */
public final class NativeRampRiderTargets {
    public record Candidate(int key,AABB body,boolean player,boolean available,double motionY,boolean collidable){}
    public static Map<Candidate,Vec3> select(NativeRampFootprint.Spec spec,boolean shaped,double previous,double current,boolean fromOriginal,boolean toOriginal,List<Candidate> candidates){
        return select(spec,shaped,previous,current,fromOriginal,toOriginal,candidates,null);
    }
    static Map<Candidate,Vec3> select(NativeRampFootprint.Spec spec,boolean shaped,double previous,double current,boolean fromOriginal,boolean toOriginal,List<Candidate> candidates,MoatSurfaces preparedMoat){

        if(!Double.isFinite(previous)||!Double.isFinite(current)||previous<0||previous>1||current<0||current>1||candidates.size()>8192)throw new IllegalArgumentException("Ramp rider selection bounds");
        var result=new LinkedHashMap<Candidate,Vec3>();if(spec.extend()&&!spec.moat())return result;
        long work=0;for(var source:spec.origins())work+=(long)(shaped?source.shapes().size():1)*(spec.elevator()?1:ControllerPlatform.treadCount(spec.treadPixels()))*candidates.size();if(work>16777216)throw new IllegalArgumentException("Ramp rider selection work budget");
        if(spec.moat())return moat(preparedMoat==null?moatSurfaces(spec,previous,current,fromOriginal,toOriginal):preparedMoat,candidates);
        if(spec.verticalLayout())return vertical(spec,previous,current,fromOriginal,toOriginal,false,0,0,candidates);
        var direction=RampGeometry.Direction.valueOf(spec.facing().name());int count=spec.elevator()?1:ControllerPlatform.treadCount(spec.treadPixels());
        for(var origin:spec.origins()){
            var source=origin.position();int row=source.getX()*spec.facing().getStepX()+source.getZ()*spec.facing().getStepZ()-spec.minimumAlong();
            if(shaped){
                var before=treads(spec,origin,row,previous,fromOriginal);var after=treads(spec,origin,row,current,toOriginal);
                for(int i=0;i<before.size();i++){var a=before.get(i);var b=after.get(i);
                    for(var actor:candidates){var body=actor.body();if(!actor.available()||actor.motionY()>.1||!body.intersects(a.inflate(0,.2,0))||body.maxX<=a.minX+1e-7||body.minX>=a.maxX-1e-7||body.maxZ<=a.minZ+1e-7||body.minZ>=a.maxZ-1e-7||Math.abs(body.minY-a.maxY)>.15)continue;
                        var delta=new Vec3(b.minX-a.minX,b.maxY-body.minY,b.minZ-a.minZ);var existing=result.get(actor);if(existing==null||delta.y>existing.y)result.put(actor,delta);
                    }
                }continue;
            }
            for(int step=0;step<count;step++){
                double before=ControllerPlatform.offsetPixels(row,step,spec.length(),spec.treadPixels(),spec.start(),spec.end(),previous,spec.elevator(),spec.fast(),spec.interpolation());
                double after=ControllerPlatform.offsetPixels(row,step,spec.length(),spec.treadPixels(),spec.start(),spec.end(),current,spec.elevator(),spec.fast(),spec.interpolation());
                if(spec.travel()!=RampGeometry.VERTICAL){
                    var moved=RampGeometry.movingTread(direction,source.getX(),source.getY(),source.getZ(),spec.low(),spec.high(),row,spec.length(),spec.start(),spec.end(),spec.treadPixels(),step,previous,previous,spec.elevator(),spec.travel(),false,spec.fast(),spec.interpolation());var tread=box(moved);
                    var side=spec.travel()==RampGeometry.LEFT?spec.facing().getCounterClockWise():spec.facing().getClockWise();double delta=after-before;
                    for(var actor:candidates){var body=actor.body();if(!actor.available()||!body.intersects(tread.inflate(0,.2,0))||Math.abs(body.minY-tread.maxY)>.25||!body.intersects(tread.inflate(0,.25,0)))continue;if(Math.abs(delta)>1e-9)result.put(actor,new Vec3(side.getStepX()*delta,0,side.getStepZ()*delta));}
                }else{
                    double minY=source.getY()+spec.high()+Math.min(0,Math.min(spec.start(),spec.end()))-.2,maxY=source.getY()+spec.high()+Math.max(0,Math.max(spec.start(),spec.end()))+.2;
                    var query=new AABB(source.getX(),minY,source.getZ(),source.getX()+1,maxY,source.getZ()+1);var local=RampGeometry.footprintPixels(direction,step,spec.elevator()?16:spec.treadPixels(),minY,maxY);var tread=new AABB(source.getX()+local.minX,local.minY,source.getZ()+local.minZ,source.getX()+local.maxX,local.maxY,source.getZ()+local.maxZ);
                    double oldTop=source.getY()+spec.high()+(fromOriginal?0:before),newTop=source.getY()+spec.high()+(toOriginal?0:after);
                    for(var actor:candidates){var body=actor.body();if(!actor.available()||actor.motionY()>.1||!body.intersects(query)||!body.deflate(1e-7).intersects(tread))continue;double target=RampGeometry.riderTarget(oldTop,newTop,body.minY,actor.player()?.5:.15);if(!Double.isNaN(target)){var existing=result.get(actor);double delta=target-body.minY;if(existing==null||delta>existing.y)result.put(actor,new Vec3(0,delta,0));}}
                }
            }
        }
        return result;
    }
    record MoatSurfaces(List<AABB> before,List<AABB> after,List<AABB> sweep,AABB bounds){}
    static MoatSurfaces moatSurfaces(NativeRampFootprint.Spec s,double previous,double current,boolean fromOriginal,boolean toOriginal){
        var before=new ArrayList<AABB>();var after=new ArrayList<AABB>();var swept=new ArrayList<AABB>();var direction=RampGeometry.Direction.valueOf(s.facing().name());
        for(var origin:s.origins()){var p=origin.position();for(var b:origin.shapes()){
            var shape=new RampGeometry.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);var whole=MoatGeometry.world(shape,p.getX(),p.getY(),p.getZ());
            for(var piece:MoatGeometry.pieces(direction,shape,s.treadPixels())){
                int step=MoatGeometry.step(direction,piece,s.treadPixels());var nativeBox=MoatGeometry.world(piece,p.getX(),p.getY(),p.getZ());var stored=MoatGeometry.storedSection(direction,nativeBox,whole);
                var a=fromOriginal?stored:MoatGeometry.transform(direction,s.sourceY(),s.length(),s.treadPixels(),s.start(),s.end(),previous,s.fast(),s.interpolation(),p.getY(),step,nativeBox,whole);
                var last=toOriginal?stored:MoatGeometry.transform(direction,s.sourceY(),s.length(),s.treadPixels(),s.start(),s.end(),current,s.fast(),s.interpolation(),p.getY(),step,nativeBox,whole);
                var first=box(a);var end=box(last);before.add(first);after.add(end);var sweep=MoatGeometry.sweep(direction,s.sourceY(),s.length(),s.treadPixels(),s.start(),s.end(),previous,current,s.fast(),s.interpolation(),p.getY(),step,nativeBox,whole);swept.add(fromOriginal||toOriginal?first.minmax(end):box(sweep));
            }
        }}if(before.isEmpty())throw new IllegalArgumentException("Empty Ramp Moat support");var bounds=before.getFirst();for(var sweep:swept)bounds=bounds.minmax(sweep);return new MoatSurfaces(List.copyOf(before),List.copyOf(after),List.copyOf(swept),bounds);
    }
    /** Null denotes a collidable bystander in the sweep or an invalid destination. */
    private static Map<Candidate,Vec3> moat(MoatSurfaces surfaces,List<Candidate> candidates){
        var result=new LinkedHashMap<Candidate,Vec3>();for(var actor:candidates){var body=actor.body();if(!actor.available()||!actor.collidable()||!body.intersects(surfaces.bounds.inflate(0,.6,0)))continue;
            int support=-1;double distance=Double.POSITIVE_INFINITY;for(int i=0;i<surfaces.before.size();i++){var box=surfaces.before.get(i);double dy=Math.abs(body.minY-box.maxY);if(body.maxX>box.minX+1e-7&&body.minX<box.maxX-1e-7&&body.maxZ>box.minZ+1e-7&&body.minZ<box.maxZ-1e-7&&dy<=(actor.player()?.5:.15)&&dy<distance){support=i;distance=dy;}}
            if(support<0){for(var sweep:surfaces.sweep)if(sweep.deflate(1e-6).intersects(body))return null;continue;}
            var a=surfaces.before.get(support);var b=surfaces.after.get(support);var delta=new Vec3(b.minX-a.minX,b.maxY-a.maxY,b.minZ-a.minZ);var destination=body.move(delta).deflate(1e-5);for(var box:surfaces.after)if(box.intersects(destination))return null;result.put(actor,delta);
        }return result;
    }
    static List<VerticalRampGeometry.Piece> verticalPieces(NativeRampFootprint.Spec s,NativeRampGeometry.Origin origin,double pose,boolean nativeEndpoint,boolean belt,double distance){
        var pieces=new ArrayList<VerticalRampGeometry.Piece>();var face=RampGeometry.Direction.valueOf(s.facing().name());var p=origin.position();for(var b:origin.shapes())pieces.addAll(VerticalRampGeometry.pieces(face,p.getX(),p.getY(),p.getZ(),new RampGeometry.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ),s.sourceY(),s.length(),s.treadPixels(),nativeEndpoint?0:s.start(),nativeEndpoint?0:s.end(),pose,s.elevator(),s.travel(),s.extend(),s.fast(),s.interpolation(),belt&&!nativeEndpoint,distance));return pieces;
    }
    static Map<Candidate,Vec3> vertical(NativeRampFootprint.Spec spec,double previous,double current,boolean fromOriginal,boolean toOriginal,boolean belt,double beforeDistance,double afterDistance,List<Candidate> candidates){
        var result=new LinkedHashMap<Candidate,Vec3>();for(var origin:spec.origins()){
            var before=verticalPieces(spec,origin,previous,fromOriginal,belt,beforeDistance);var after=verticalPieces(spec,origin,current,toOriginal,belt,afterDistance);
            for(var old:before){VerticalRampGeometry.Piece next=null;double best=Double.POSITIVE_INFINITY;for(var candidate:after){if(candidate.step!=old.step)continue;double difference=Math.abs(candidate.box.minY-old.box.minY);if(difference<best){best=difference;next=candidate;}}if(next==null||belt&&best>.5)continue;
                var b=old.box;var n=next.box;var support=new AABB(b.minX,b.maxY-.15,b.minZ,b.maxX,b.maxY+.5,b.maxZ);var delta=new Vec3(next.dx-old.dx,n.maxY-b.maxY,next.dz-old.dz);
                for(var actor:candidates){var body=actor.body();if(!actor.available()||actor.motionY()>.1||!body.intersects(support)||Math.abs(body.minY-b.maxY)>.15||body.maxX<=b.minX||body.minX>=b.maxX||body.maxZ<=b.minZ||body.minZ>=b.maxZ)continue;result.putIfAbsent(actor,delta);}
            }
        }return result;
    }
    static List<AABB> treads(NativeRampFootprint.Spec s,NativeRampGeometry.Origin origin,int row,double pose,boolean nativeEndpoint){
        var result=new ArrayList<AABB>();var face=RampGeometry.Direction.valueOf(s.facing().name());var p=origin.position();
        for(var shape:origin.shapes())for(int step=0;step<(s.elevator()?1:ControllerPlatform.treadCount(s.treadPixels()));step++){
            var f=RampGeometry.footprintPixels(face,step,s.elevator()?16:s.treadPixels(),shape.minY,shape.maxY);double x=Math.max(shape.minX,f.minX),y=Math.max(shape.minY,f.minY),z=Math.max(shape.minZ,f.minZ),X=Math.min(shape.maxX,f.maxX),Y=Math.min(shape.maxY,f.maxY),Z=Math.min(shape.maxZ,f.maxZ);if(X-x<=1e-8||Y-y<=1e-8||Z-z<=1e-8)continue;
            var moved=RampGeometry.movingTread(face,p.getX(),p.getY(),p.getZ(),shape.minY,shape.maxY,row,s.length(),nativeEndpoint?0:s.start(),nativeEndpoint?0:s.end(),s.treadPixels(),step,pose,pose,s.elevator(),s.travel(),false,s.fast(),s.interpolation());
            result.add(new AABB(moved.minX+x-f.minX,moved.minY,moved.minZ+z-f.minZ,moved.maxX+X-f.maxX,moved.maxY,moved.maxZ+Z-f.maxZ));
        }return result;
    }
    private static AABB box(RampGeometry.Box b){return new AABB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private NativeRampRiderTargets(){}
}

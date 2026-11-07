package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.ramp.*;
import net.minecraft.core.*;

/** Source material ownership and UV offsets follow the released lookup order, not merely geometry provenance. */
public final class NativeRampTextureCoordinates {
    public record Frame(BlockPos origin,double uShift,double vShift,double bottom,double top){}
    private static RampGeometry.Box box(NativeRampGeometry.Piece piece){var b=piece.box();return new RampGeometry.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    public static Frame frame(NativeRampGeometry.Spec spec,NativeRampGeometry.Piece piece,double pose){
        var b=piece.box();BlockPos origin=null;
        if((spec.verticalLayout()||spec.moat()||spec.shaped())&&piece.origin()!=null)origin=piece.origin().position();
        if(origin==null){double x=spec.cell().getX()+(b.minX+b.maxX)*.5,z=spec.cell().getZ()+(b.minZ+b.maxZ)*.5;for(var candidate:spec.origins()){var p=candidate.position();if(x>=p.getX()&&x<p.getX()+1&&z>=p.getZ()&&z<p.getZ()+1){origin=p;break;}}}
        if(origin==null)origin=spec.origins().isEmpty()?spec.cell():spec.origins().getFirst().position();
        double u=0,v=0,bottom,top;var direction=RampGeometry.Direction.valueOf(spec.facing().name());
        if(spec.verticalLayout()||spec.moat()){
            u=spec.cell().getX()-origin.getX()-piece.dx();v=spec.cell().getZ()-origin.getZ()-piece.dz();
            bottom=ControllerPlatform.sideTextureV(spec.cell().getY(),b.minY,origin.getY(),piece.dy());top=ControllerPlatform.sideTextureV(spec.cell().getY(),b.maxY,origin.getY(),piece.dy());
        }else{
            if(spec.travel()!=RampGeometry.VERTICAL&&!spec.extend()){
                var side=spec.travel()==RampGeometry.LEFT?spec.facing().getCounterClockWise():spec.facing().getClockWise();
                boolean found=false;int hinge=spec.cell().getX()*spec.facing().getStepX()+spec.cell().getZ()*spec.facing().getStepZ()-spec.row();
                for(var source:spec.origins()){
                    var p=source.position();int row=p.getX()*spec.facing().getStepX()+p.getZ()*spec.facing().getStepZ()-hinge;
                    for(int step=0;step<(spec.elevator()?1:ControllerPlatform.treadCount(spec.treadPixels()));step++){
                        var whole=RampGeometry.movingTread(direction,p.getX(),p.getY(),p.getZ(),spec.low(),spec.high(),row,spec.length(),spec.start(),spec.end(),spec.treadPixels(),step,pose,pose,spec.elevator(),spec.travel(),false,spec.fast(),spec.interpolation());
                        var clipped=RampGeometry.clip(whole,spec.cell().getX(),spec.cell().getY(),spec.cell().getZ());
                        if(clipped==null||Math.abs(clipped.minX-b.minX)>1e-7||Math.abs(clipped.maxX-b.maxX)>1e-7||Math.abs(clipped.minY-b.minY)>1e-7||Math.abs(clipped.maxY-b.maxY)>1e-7||Math.abs(clipped.minZ-b.minZ)>1e-7||Math.abs(clipped.maxZ-b.maxZ)>1e-7)continue;
                        double offset=ControllerPlatform.offsetPixels(row,step,spec.length(),spec.treadPixels(),spec.start(),spec.end(),pose,spec.elevator(),spec.fast(),spec.interpolation());
                        u=spec.cell().getX()-p.getX()-side.getStepX()*offset;v=spec.cell().getZ()-p.getZ()-side.getStepZ()*offset;found=true;break;
                    }
                    if(found)break;
                }
            }
            if(spec.extend()){bottom=1-b.minY;top=1-b.maxY;}
            else{int step=RampGeometry.segmentAtPixels(direction,box(piece),spec.treadPixels(),spec.elevator());double offset=spec.travel()==RampGeometry.VERTICAL?ControllerPlatform.offsetPixels(spec.row(),step,spec.length(),spec.treadPixels(),spec.start(),spec.end(),pose,spec.elevator(),spec.fast(),spec.interpolation()):0;bottom=ControllerPlatform.sideTextureV(spec.cell().getY(),b.minY,spec.sourceY(),offset);top=ControllerPlatform.sideTextureV(spec.cell().getY(),b.maxY,spec.sourceY(),offset);}
        }
        return new Frame(origin,u,v,bottom,top);
    }
    private NativeRampTextureCoordinates(){}
}

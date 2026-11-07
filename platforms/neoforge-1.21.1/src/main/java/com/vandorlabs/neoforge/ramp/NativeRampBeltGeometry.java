package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.ramp.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;

/** Deployed belt shapes and source UV frames. Belt velocity is not the ordinary Piece translation. */
public final class NativeRampBeltGeometry {
    public record Snapshot(NativeRampGeometry.Spec spec,double distance,NativeRampGeometry.Snapshot geometry,List<NativeRampTextureCoordinates.Frame> frames){
        public Snapshot{frames=List.copyOf(frames);if(frames.size()!=geometry.pieces().size())throw new IllegalArgumentException("Belt frame count");}
        public long retainedBytes(){return 128L+geometry.retainedBytes()+96L*frames.size();}
    }
    private static RampGeometry.Box box(AABB b){return new RampGeometry.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static RampGeometry.Box intersection(RampGeometry.Box a,RampGeometry.Box b){double x=Math.max(a.minX,b.minX),y=Math.max(a.minY,b.minY),z=Math.max(a.minZ,b.minZ),X=Math.min(a.maxX,b.maxX),Y=Math.min(a.maxY,b.maxY),Z=Math.min(a.maxZ,b.maxZ);return X-x>1e-8&&Y-y>1e-8&&Z-z>1e-8?new RampGeometry.Box(x,y,z,X,Y,Z):null;}
    private static void require(int length,int pixels,boolean moat,boolean elevator,int travel,boolean extend,double start){if(moat||elevator||travel!=RampGeometry.VERTICAL||extend||start!=0||length*16<=pixels)throw new IllegalArgumentException("Unsupported deployed belt profile");}
    public static Snapshot snapshot(NativeRampGeometry.Spec spec,double distance){
        require(spec.length(),spec.treadPixels(),spec.moat(),spec.elevator(),spec.travel(),spec.extend(),spec.start());if(!Double.isFinite(distance))throw new IllegalArgumentException("Belt distance");
        long work=0;for(var origin:spec.origins())work+=(long)(spec.shaped()||spec.verticalLayout()?origin.shapes().size():1)*ControllerPlatform.treadCount(spec.treadPixels());if(work>131072)throw new IllegalArgumentException("Belt geometry work budget");
        var pieces=new ArrayList<NativeRampGeometry.Piece>();var frames=new ArrayList<NativeRampTextureCoordinates.Frame>();var direction=RampGeometry.Direction.valueOf(spec.facing().name());int hinge=spec.cell().getX()*spec.facing().getStepX()+spec.cell().getZ()*spec.facing().getStepZ()-spec.row();
        for(var origin:spec.origins()){
            var p=origin.position();
            if(spec.verticalLayout()){
                double at=EscalatorGeometry.phase(p.getY()-spec.sourceY()+distance,spec.length()*16),row=spec.cell().getY()-spec.sourceY();if(!(at<row+1&&at+1>row)&&!(at-spec.length()<row+1&&at+1-spec.length()>row))continue;
                for(var shape:origin.shapes())for(var moved:VerticalRampGeometry.pieces(direction,p.getX(),p.getY(),p.getZ(),box(shape),spec.sourceY(),spec.length(),spec.treadPixels(),spec.start(),spec.end(),1,false,0,false,spec.fast(),spec.interpolation(),true,distance))add(pieces,frames,spec,distance,origin,moved.box,moved.step,moved.dx,moved.dy,moved.dz);
                continue;
            }
            if(spec.facing().getAxis()==Direction.Axis.X?p.getZ()!=spec.cell().getZ():p.getX()!=spec.cell().getX())continue;
            int row=p.getX()*spec.facing().getStepX()+p.getZ()*spec.facing().getStepZ()-hinge;double at=EscalatorGeometry.phase(row+distance,spec.length()*16);if(!(at<spec.row()+1&&at+1>spec.row())&&!(at-spec.length()<spec.row()+1&&at+1-spec.length()>spec.row()))continue;
            if(!spec.shaped()){for(var whole:EscalatorGeometry.treads(direction,p.getX(),p.getY(),p.getZ(),spec.low(),spec.high(),row,spec.length(),spec.treadPixels(),spec.end(),distance,spec.interpolation()))add(pieces,frames,spec,distance,origin,whole,-1,0,0,0);continue;}
            double pitch=spec.treadPixels()/16D,shift=EscalatorGeometry.phase(distance,spec.length()*16);boolean axisX=spec.facing().getAxis()==Direction.Axis.X,reverse=spec.facing()==Direction.NORTH||spec.facing()==Direction.WEST;
            for(var nativeShape:origin.shapes())for(int step=0;step<ControllerPlatform.treadCount(spec.treadPixels());step++){
                var shape=box(nativeShape);var part=intersection(shape,RampGeometry.footprintPixels(direction,step,spec.treadPixels(),0,1));if(part==null)continue;double anchor=EscalatorGeometry.phase(row+step*pitch+shift,spec.length()*16);
                for(int copy=0;copy<2;copy++){double position=anchor-copy*spec.length(),delta=(position-row-step*pitch)*(reverse?-1:1),offset=EscalatorGeometry.height(position,spec.length(),spec.treadPixels(),spec.end(),spec.interpolation());double a=(axisX?part.minX:part.minZ)+delta,b=(axisX?part.maxX:part.maxZ)+delta,low=reverse?row+1-spec.length():-row,high=reverse?row+1:spec.length()-row;a=Math.max(a,low);b=Math.min(b,high);if(b-a<1e-8)continue;
                    add(pieces,frames,spec,distance,origin,new RampGeometry.Box(p.getX()+(axisX?a:part.minX),p.getY()+part.minY+offset,p.getZ()+(axisX?part.minZ:a),p.getX()+(axisX?b:part.maxX),p.getY()+part.maxY+offset,p.getZ()+(axisX?part.maxZ:b)),step,axisX?delta:0,offset,axisX?0:delta);
                }
            }
        }
        return new Snapshot(spec,distance,NativeRampGeometry.finish(pieces),frames);
    }
    private static void add(List<NativeRampGeometry.Piece> pieces,List<NativeRampTextureCoordinates.Frame> frames,NativeRampGeometry.Spec spec,double distance,NativeRampGeometry.Origin origin,RampGeometry.Box whole,int step,double dx,double dy,double dz){
        var b=RampGeometry.clip(whole,spec.cell().getX(),spec.cell().getY(),spec.cell().getZ());if(b==null)return;if(pieces.size()>=8192)throw new IllegalArgumentException("Belt piece budget");for(double value:new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ})if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite belt shape");var p=origin.position();double u,v,bottom,top;
        if(spec.verticalLayout()){u=spec.cell().getX()-p.getX()-dx;v=spec.cell().getZ()-p.getZ()-dz;bottom=ControllerPlatform.sideTextureV(spec.cell().getY(),b.minY,p.getY(),dy);top=ControllerPlatform.sideTextureV(spec.cell().getY(),b.maxY,p.getY(),dy);}
        else{double along=(spec.cell().getX()+(b.minX+b.maxX)*.5)*spec.facing().getStepX()+(spec.cell().getZ()+(b.minZ+b.maxZ)*.5)*spec.facing().getStepZ();int minimum=spec.cell().getX()*spec.facing().getStepX()+spec.cell().getZ()*spec.facing().getStepZ()-spec.row();double coordinate=along-minimum+(spec.facing()==Direction.NORTH||spec.facing()==Direction.WEST?1:0),source=EscalatorGeometry.phase(coordinate-distance,spec.length()*16),displacement=coordinate-source;u=spec.cell().getX()-p.getX()-spec.facing().getStepX()*displacement;v=spec.cell().getZ()-p.getZ()-spec.facing().getStepZ()*displacement;double originalTop=whole.maxY-spec.cell().getY();bottom=1-(b.minY-originalTop+spec.high());top=1-(b.maxY-originalTop+spec.high());}
        pieces.add(new NativeRampGeometry.Piece(new AABB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ),origin,step,dx,dy,dz));frames.add(new NativeRampTextureCoordinates.Frame(p,u,v,bottom,top));
    }
    /** The released reserveStep(1,1) envelope includes moving last-tread overhang, not just one phase. */
    public static Set<BlockPos> cells(NativeRampFootprint.Spec spec){
        require(spec.length(),spec.treadPixels(),spec.moat(),spec.elevator(),spec.travel(),spec.extend(),spec.start());var result=new HashSet<BlockPos>();var direction=RampGeometry.Direction.valueOf(spec.facing().name());double pitch=spec.treadPixels()/16D;long work=0;
        for(var origin:spec.origins()){var p=origin.position();int row=p.getX()*spec.facing().getStepX()+p.getZ()*spec.facing().getStepZ()-spec.minimumAlong();for(int step=0;step<ControllerPlatform.treadCount(spec.treadPixels());step++){
            if(++work>131072)throw new IllegalArgumentException("Belt footprint work budget");RampGeometry.Box whole;
            if(spec.verticalLayout())whole=VerticalRampGeometry.sweep(direction,p.getX(),p.getY(),p.getZ(),spec.low(),spec.high(),spec.sourceY(),spec.length(),spec.treadPixels(),step,0,spec.end(),1,1,false,0,false,spec.fast(),spec.interpolation(),true);
            else{var fp=RampGeometry.footprintPixels(direction,step,spec.treadPixels(),spec.low(),spec.high());double a=EscalatorGeometry.height(row+step*pitch,spec.length(),spec.treadPixels(),spec.end(),spec.interpolation()),b=EscalatorGeometry.height(row+(step+1)*pitch,spec.length(),spec.treadPixels(),spec.end(),spec.interpolation()),extra=row==spec.length()-1&&step==ControllerPlatform.treadCount(spec.treadPixels())-1?0:pitch;whole=new RampGeometry.Box(p.getX()+fp.minX+(spec.facing().getStepX()<0?-extra:0),p.getY()+spec.low()+Math.min(a,b),p.getZ()+fp.minZ+(spec.facing().getStepZ()<0?-extra:0),p.getX()+fp.maxX+(spec.facing().getStepX()>0?extra:0),p.getY()+spec.high()+Math.max(a,b),p.getZ()+fp.maxZ+(spec.facing().getStepZ()>0?extra:0));}
            NativeRampFootprint.add(result,spec.root(),whole);
        }}return Set.copyOf(result);
    }
    private NativeRampBeltGeometry(){}
}

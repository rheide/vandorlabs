package com.vandorlabs.neoforge.fluid;

import com.vandorlabs.fluid.FluidRuns;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Immutable local collision-free runs. Construction samples no level and faces remain lazy. */
public final class NativeFluidVolume {
    @FunctionalInterface public interface Side {int at(double x,double y,double z);}
    public record Cell(AABB box,int side) {}
    public record Face(List<Vec3> vertices,int side,int axis) {
        public Face {vertices=List.copyOf(vertices);}
    }
    private static final FluidRuns<AABB,Cell,Side,Void> RUNS=new FluidRuns<>() {
        protected double minimum(AABB b,int a){return a==0?b.minX:a==1?b.minY:b.minZ;}
        protected double maximum(AABB b,int a){return a==0?b.maxX:a==1?b.maxY:b.maxZ;}
        protected int side(Side side,Void ignored,double[] aperture,double x,double y,double z){return side.at(x,y,z);}
        protected Cell createCell(double x0,double y0,double z0,double x1,double y1,double z1,int side){return new Cell(new AABB(x0,y0,z0,x1,y1,z1),side);}
    };
    public static final Side SINGLE_SIDE=(x,y,z)->0;
    private final List<AABB> solid;
    private final List<Cell> cells;
    private final double[][] cuts;
    private volatile List<Face> faces;
    private volatile IllegalStateException faceFailure;
    private static final long MAX_FACE_WORK=32L*1024*1024,MAX_FACE_SCRATCH=4L*1024*1024,MAX_FACE_RETAINED=8L*1024*1024;
    private static final int FACE_BYTES=384; // Conservative for wide references and 16-byte object alignment.
    private static final Direction[] DIRECTIONS=Direction.values();
    private NativeFluidVolume(List<AABB> solid,List<Cell> cells,double[][] cuts){this.solid=solid;this.cells=List.copyOf(cells);this.cuts=cuts;}
    /** Boxes must already be clipped into local unit coordinates. Optional cuts split two-sided openings. */
    public static NativeFluidVolume build(List<AABB> localSolid,Side side,double[]... extraCuts){
        Objects.requireNonNull(side);List<AABB> solid=List.copyOf(localSolid);double[][] cuts=new double[3][];
        for(int a=0;a<3;a++){
            TreeSet<Double> points=new TreeSet<>();points.add(0D);points.add(1D);
            for(AABB b:solid){double low=a==0?b.minX:a==1?b.minY:b.minZ,high=a==0?b.maxX:a==1?b.maxY:b.maxZ;
                if(!Double.isFinite(low)||!Double.isFinite(high)||low<0||high>1)throw new IllegalArgumentException("Unclipped fluid solid");
                points.add(low);points.add(high);}
            if(extraCuts.length!=0){if(extraCuts.length!=3)throw new IllegalArgumentException("Three cut axes required");for(double cut:extraCuts[a]){if(!Double.isFinite(cut)||cut<0||cut>1)throw new IllegalArgumentException("Invalid fluid cut");points.add(cut);}}
            cuts[a]=points.stream().mapToDouble(Double::doubleValue).toArray();
        }
        List<Cell> cells=new ArrayList<>();RUNS.append(cells,solid,cuts,side,null,null);return new NativeFluidVolume(solid,cells,cuts);
    }
    public List<AABB> solids(){return solid;}
    public List<Cell> cells(){return cells;}
    public boolean hasSide(int side){for(Cell c:cells)if(c.side==side)return true;return false;}
    /** Half-open ownership matches current Forge partition boundaries. */
    public int sideAt(double x,double y,double z){for(Cell cell:cells){AABB b=cell.box;if(x>=b.minX&&x<b.maxX&&y>=b.minY&&y<b.maxY&&z>=b.minZ&&z<b.maxZ)return cell.side;}return -1;}
    public boolean facesMaterialized(){return faces!=null;}
    /** Complete immutable faces in legacy order. Excessive direct graphs fail explicitly and retain no partial list. */
    public List<Face> faces(){List<Face> value=faces;if(value==null)synchronized(this){value=faces;if(value==null){if(faceFailure!=null)throw faceFailure;try{faces=value=List.copyOf(volumeFaces(cells,cuts));}catch(IllegalStateException failure){faceFailure=failure;throw failure;}}}return value;}
    private static List<Face> volumeFaces(List<Cell> cells,double[][] cuts){
        if(cells.isEmpty())return List.of();
        int nx=cuts[0].length-1,ny=cuts[1].length-1,nz=cuts[2].length-1;
        long plane=(long)ny*nz,rangesBytes=(long)cells.size()*7*Integer.BYTES;
        if(rangesBytes>MAX_FACE_SCRATCH-256||plane>(MAX_FACE_SCRATCH-256-rangesBytes)/3)throw new IllegalStateException("Fluid face sweep exceeds checked work/scratch budget: plane="+plane+" runs="+cells.size());
        // Include cleared/filled planes, range visits, output traversal and six neighbor checks.
        if(nx>(MAX_FACE_WORK-2*plane)/(9*plane+cells.size()))throw new IllegalStateException("Fluid face sweep exceeds checked work/scratch budget: x="+nx+" plane="+plane+" runs="+cells.size());
        // Compact physical runs remain authoritative. Only three adjacent x planes are expanded.
        int[] ranges=new int[cells.size()*7];int index=0;
        for(Cell cell:cells){ranges[index++]=Arrays.binarySearch(cuts[0],cell.box.minX);ranges[index++]=Arrays.binarySearch(cuts[0],cell.box.maxX);ranges[index++]=Arrays.binarySearch(cuts[1],cell.box.minY);ranges[index++]=Arrays.binarySearch(cuts[1],cell.box.maxY);ranges[index++]=Arrays.binarySearch(cuts[2],cell.box.minZ);ranges[index++]=Arrays.binarySearch(cuts[2],cell.box.maxZ);ranges[index++]=cell.side;}
        byte[] previous=new byte[(int)plane],current=new byte[(int)plane],next=new byte[(int)plane];Arrays.fill(previous,(byte)-1);fillPlane(current,0,ranges,nz);fillPlane(next,1<nx?1:-1,ranges,nz);List<Face> faces=new ArrayList<>();
        for(int x=0;x<nx;x++){
          for(int y=0;y<ny;y++)for(int z=0;z<nz;z++)if(current[y*nz+z]>=0){
            int side=current[y*nz+z];double a=cuts[0][x],b=cuts[0][x+1],c=cuts[1][y],d=cuts[1][y+1],e=cuts[2][z],f=cuts[2][z+1];
            for(Direction face:DIRECTIONS){int yy=y+face.getStepY(),zz=z+face.getStepZ();if(yy>=0&&yy<ny&&zz>=0&&zz<nz){byte[] owners=face.getStepX()<0?previous:face.getStepX()>0?next:current;if(owners[yy*nz+zz]==side)continue;}
                if(256L+(long)(faces.size()+1)*FACE_BYTES>MAX_FACE_RETAINED)throw new IllegalStateException("Fluid face list exceeds checked retained budget: faces="+faces.size());
                Vec3[] vertices;switch(face){case DOWN:vertices=new Vec3[]{new Vec3(a,c,e),new Vec3(b,c,e),new Vec3(b,c,f),new Vec3(a,c,f)};break;case UP:vertices=new Vec3[]{new Vec3(a,d,e),new Vec3(a,d,f),new Vec3(b,d,f),new Vec3(b,d,e)};break;case WEST:vertices=new Vec3[]{new Vec3(a,d,e),new Vec3(a,c,e),new Vec3(a,c,f),new Vec3(a,d,f)};break;case EAST:vertices=new Vec3[]{new Vec3(b,d,e),new Vec3(b,d,f),new Vec3(b,c,f),new Vec3(b,c,e)};break;case NORTH:vertices=new Vec3[]{new Vec3(a,d,e),new Vec3(b,d,e),new Vec3(b,c,e),new Vec3(a,c,e)};break;default:vertices=new Vec3[]{new Vec3(a,d,f),new Vec3(a,c,f),new Vec3(b,c,f),new Vec3(b,d,f)};}
                faces.add(new Face(Arrays.asList(vertices),side,face.getAxis()==Direction.Axis.X?0:face.getAxis()==Direction.Axis.Y?1:2));
            }
          }
          byte[] recycled=previous;previous=current;current=next;next=recycled;fillPlane(next,x+2<nx?x+2:-1,ranges,nz);
        }
        return faces;
    }
    private static void fillPlane(byte[] owners,int x,int[] ranges,int nz){
        Arrays.fill(owners,(byte)-1);if(x<0)return;
        for(int index=0;index<ranges.length;index+=7)if(x>=ranges[index]&&x<ranges[index+1])for(int y=ranges[index+2];y<ranges[index+3];y++)Arrays.fill(owners,y*nz+ranges[index+4],y*nz+ranges[index+5],(byte)ranges[index+6]);
    }
}

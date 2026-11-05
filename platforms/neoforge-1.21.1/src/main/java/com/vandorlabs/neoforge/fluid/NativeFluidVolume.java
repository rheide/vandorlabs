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
    public boolean facesMaterialized(){return faces!=null;}
    public List<Face> faces(){List<Face> value=faces;if(value==null)synchronized(this){value=faces;if(value==null)faces=value=List.copyOf(volumeFaces(cells,cuts));}return value;}
    private static List<Face> volumeFaces(List<Cell> cells,double[][] cuts){
        int nx=cuts[0].length-1,ny=cuts[1].length-1,nz=cuts[2].length-1;List<Face> faces=new ArrayList<>();
        byte[][][] owners=new byte[nx][ny][nz];
        for(byte[][] plane:owners)for(byte[] row:plane)Arrays.fill(row,(byte)-1);
        for(Cell cell:cells){int x0=Arrays.binarySearch(cuts[0],cell.box.minX),x1=Arrays.binarySearch(cuts[0],cell.box.maxX),y0=Arrays.binarySearch(cuts[1],cell.box.minY),y1=Arrays.binarySearch(cuts[1],cell.box.maxY),z0=Arrays.binarySearch(cuts[2],cell.box.minZ),z1=Arrays.binarySearch(cuts[2],cell.box.maxZ);
            for(int x=x0;x<x1;x++)for(int y=y0;y<y1;y++)Arrays.fill(owners[x][y],z0,z1,(byte)cell.side);
        }
        for(int x=0;x<nx;x++)for(int y=0;y<ny;y++)for(int z=0;z<nz;z++)if(owners[x][y][z]>=0){
            int side=owners[x][y][z];double a=cuts[0][x],b=cuts[0][x+1],c=cuts[1][y],d=cuts[1][y+1],e=cuts[2][z],f=cuts[2][z+1];
            for(Direction face:Direction.values()){int xx=x+face.getStepX(),yy=y+face.getStepY(),zz=z+face.getStepZ();if(xx>=0&&xx<nx&&yy>=0&&yy<ny&&zz>=0&&zz<nz&&owners[xx][yy][zz]==side)continue;
                Vec3[] vertices;switch(face){case DOWN:vertices=new Vec3[]{new Vec3(a,c,e),new Vec3(b,c,e),new Vec3(b,c,f),new Vec3(a,c,f)};break;case UP:vertices=new Vec3[]{new Vec3(a,d,e),new Vec3(a,d,f),new Vec3(b,d,f),new Vec3(b,d,e)};break;case WEST:vertices=new Vec3[]{new Vec3(a,d,e),new Vec3(a,c,e),new Vec3(a,c,f),new Vec3(a,d,f)};break;case EAST:vertices=new Vec3[]{new Vec3(b,d,e),new Vec3(b,d,f),new Vec3(b,c,f),new Vec3(b,c,e)};break;case NORTH:vertices=new Vec3[]{new Vec3(a,d,e),new Vec3(b,d,e),new Vec3(b,c,e),new Vec3(a,c,e)};break;default:vertices=new Vec3[]{new Vec3(a,d,f),new Vec3(a,c,f),new Vec3(b,c,f),new Vec3(b,d,f)};}
                faces.add(new Face(Arrays.asList(vertices),side,face.getAxis()==Direction.Axis.X?0:face.getAxis()==Direction.Axis.Y?1:2));
            }
        }
        return faces;
    }
}

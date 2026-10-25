package com.vandorlabs.fluids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/** Collision-free bucket volumes and authored boundary winding in local block coordinates. */
public final class FluidVolume {
    public interface SideMap { int side(double x,double y,double z); }
    public static final class Box {
        public final double minX,minY,minZ,maxX,maxY,maxZ;
        public Box(double x0,double y0,double z0,double x1,double y1,double z1){minX=x0;minY=y0;minZ=z0;maxX=x1;maxY=y1;maxZ=z1;}
        boolean contains(double x,double y,double z){return x>minX&&x<maxX&&y>minY&&y<maxY&&z>minZ&&z<maxZ;}
        boolean owns(double x,double y,double z){return x>=minX&&x<maxX&&y>=minY&&y<maxY&&z>=minZ&&z<maxZ;}
    }
    public static final class Point {
        public final double x,y,z;
        Point(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    }
    public static final class Cell {
        public final Box box;public final int side;
        Cell(Box box,int side){this.box=box;this.side=side;}
    }
    public static final class Face {
        public final Point[] vertices;public final int side,axis,facing;
        Face(Point[] vertices,int side,int axis,int facing){this.vertices=vertices;this.side=side;this.axis=axis;this.facing=facing;}
    }
    public final List<Cell> cells;
    public final List<Face> faces;
    private FluidVolume(List<Cell> cells,List<Face> faces){this.cells=Collections.unmodifiableList(cells);this.faces=Collections.unmodifiableList(faces);}
    public boolean hasSide(int side){for(Cell cell:cells)if(cell.side==side)return true;return false;}
    public int sideAt(double x,double y,double z){for(Cell cell:cells)if(cell.box.owns(x,y,z))return cell.side;return -1;}
    public static FluidVolume build(List<Box> solid,double[][] extraCuts,SideMap mapping){
        double[][] cuts=new double[3][];
        for(int axis=0;axis<3;axis++){
            TreeSet<Double> points=new TreeSet<Double>();points.add(0D);points.add(1D);
            if(extraCuts!=null&&extraCuts[axis]!=null)for(double cut:extraCuts[axis])if(cut>0&&cut<1)points.add(cut);
            for(Box box:solid){points.add(axis==0?box.minX:axis==1?box.minY:box.minZ);points.add(axis==0?box.maxX:axis==1?box.maxY:box.maxZ);}
            cuts[axis]=new double[points.size()];int i=0;for(double value:points)cuts[axis][i++]=value;
        }
        int nx=cuts[0].length-1,ny=cuts[1].length-1,nz=cuts[2].length-1;
        int[][][] owners=new int[nx][ny][nz];List<Cell> cells=new ArrayList<Cell>();List<Face> faces=new ArrayList<Face>();
        for(int x=0;x<nx;x++)for(int y=0;y<ny;y++)for(int z=0;z<nz;z++){
            Box box=new Box(cuts[0][x],cuts[1][y],cuts[2][z],cuts[0][x+1],cuts[1][y+1],cuts[2][z+1]);
            double cx=(box.minX+box.maxX)*.5,cy=(box.minY+box.maxY)*.5,cz=(box.minZ+box.maxZ)*.5;
            int side=mapping.side(cx,cy,cz);for(Box occupied:solid)if(occupied.contains(cx,cy,cz)){side=-1;break;}
            owners[x][y][z]=side;if(side>=0)cells.add(new Cell(box,side));
        }
        int[][] offsets={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
        for(int x=0;x<nx;x++)for(int y=0;y<ny;y++)for(int z=0;z<nz;z++)if(owners[x][y][z]>=0){
            int side=owners[x][y][z];double a=cuts[0][x],b=cuts[0][x+1],c=cuts[1][y],d=cuts[1][y+1],e=cuts[2][z],f=cuts[2][z+1];
            for(int facing=0;facing<6;facing++){
                int xx=x+offsets[facing][0],yy=y+offsets[facing][1],zz=z+offsets[facing][2];
                if(xx>=0&&xx<nx&&yy>=0&&yy<ny&&zz>=0&&zz<nz&&owners[xx][yy][zz]==side)continue;
                Point[] vertices;
                switch(facing){
                    case 0:vertices=new Point[]{new Point(a,c,e),new Point(b,c,e),new Point(b,c,f),new Point(a,c,f)};break;
                    case 1:vertices=new Point[]{new Point(a,d,e),new Point(a,d,f),new Point(b,d,f),new Point(b,d,e)};break;
                    case 4:vertices=new Point[]{new Point(a,d,e),new Point(a,c,e),new Point(a,c,f),new Point(a,d,f)};break;
                    case 5:vertices=new Point[]{new Point(b,d,e),new Point(b,d,f),new Point(b,c,f),new Point(b,c,e)};break;
                    case 2:vertices=new Point[]{new Point(a,d,e),new Point(b,d,e),new Point(b,c,e),new Point(a,c,e)};break;
                    default:vertices=new Point[]{new Point(a,d,f),new Point(a,c,f),new Point(b,c,f),new Point(b,d,f)};
                }
                faces.add(new Face(vertices,side,facing<2?1:facing<4?2:0,facing));
            }
        }
        return new FluidVolume(cells,faces);
    }
    public static boolean canFill(int amount,boolean gaseous,boolean occupied,boolean hasVolume,boolean opening){return amount==1000&&!gaseous&&!occupied&&(opening||hasVolume);}
    public static boolean canFill(int amount,boolean gaseous,boolean occupied,java.util.function.BooleanSupplier available){return amount==1000&&!gaseous&&!occupied&&available.getAsBoolean();}
    public static boolean canDrain(boolean stored,int accepted){return stored&&accepted==1000;}
}

package com.vandorlabs.geometry;

import java.util.*;

/** Minecraft-pixel Core cage. Every solid cell and emitted edge lies on the authored 32-grid. */
public final class CoreMesh {
    public static final int GRID=32,CENTER_MIN=12,CENTER_MAX=20;
    public static final float ITEM_GUI_SCALE=.54F;
    public static final int EMPTY=0,FRAME=1,HIGHLIGHT=2,SHADOW=3,CORNER=4,LEGACY_CORE=5;
    public static final List<String> IDS=Collections.unmodifiableList(Arrays.asList("assembly_press","synthesis_tower","enclosed_gantry","longburn_drive","meridian_core","paladyne_navigation_computer","seyr_navigation_computer","vector_fold_array","oculus_drive","sentinel_array","standalone_antenna","standalone_dish","survey_dish"));
    // Cyan highlight matches the established GUI emblem; darker steps shade physical frame pixels.
    private static final int[] RGB={0,0x3d8b9d,0x77e6ef,0x235160,0x78e8f1,0xffffff};
    private static final byte[] VOXELS=new byte[GRID*GRID*GRID];
    public static final class Face {
        public final int rgb,material,fabricationRGB;
        public final boolean textured;
        public final double nx,ny,nz;
        private final double[] vertices;
        private Face(int material,int axis,int sign,double[] positions){this.material=material;textured=material==LEGACY_CORE;double shade=axis==1?(sign>0?1:.70):axis==0?(sign>0?.80:.86):(sign>0?.84:.94);rgb=textured?0xffffff:shade(RGB[material],shade);fabricationRGB=textured?shade(0xffffff,shade):rgb;vertices=positions;nx=axis==0?sign:0;ny=axis==1?sign:0;nz=axis==2?sign:0;}
        public double coordinate(int vertex,int axis){return vertices[vertex*3+axis];}
        public double u(int vertex){if(!textured)return .5;double x=coordinate(vertex,0)*GRID,y=coordinate(vertex,1)*GRID,z=coordinate(vertex,2)*GRID;return (nx<0?CENTER_MAX-z:nx>0?z-CENTER_MIN:nz>0?CENTER_MAX-x:x-CENTER_MIN)/(CENTER_MAX-CENTER_MIN);}
        public double v(int vertex){if(!textured)return .5;double y=coordinate(vertex,1)*GRID,z=coordinate(vertex,2)*GRID;return (ny>0?z-CENTER_MIN:ny<0?CENTER_MAX-z:CENTER_MAX-y)/(CENTER_MAX-CENTER_MIN);}
    }
    private static final List<Face> FULL;
    private static final int[] RANK=new int[GRID*GRID*GRID],LAYER_COUNTS=new int[GRID+1];
    private static final int[] ORDER;
    private static final LinkedHashMap<Integer,List<Face>> PREFIXES=new LinkedHashMap<Integer,List<Face>>(16,.75F,true);
    private static final long PREFIX_BUDGET=4L*1024*1024;
    private static long prefixBytes;
    static {
        // Eight actual cubes, not larger flat corner marks.
        for(int bits=0;bits<8;bits++){int x=(bits&1)==0?0:26,y=(bits&2)==0?0:26,z=(bits&4)==0?0:26;box(x,y,z,x+6,y+6,z+6,CORNER);}
        // Twelve three-pixel edges reproduce the outer band plus its inner highlight.
        for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int b=0;b<2;b++)for(int at=6;at<26;at++)for(int u=0;u<3;u++)for(int v=0;v<3;v++){
            int[] p=new int[3];p[axis]=at;p[(axis+1)%3]=a==0?2+u:29-u;p[(axis+2)%3]=b==0?2+v:29-v;put(p[0],p[1],p[2],u==2||v==2?HIGHLIGHT:FRAME);
        }
        // Each visible face has the stepped reference X, two pixels deep into its enclosure.
        for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++)for(int u=4;u<28;u++)for(int v=4;v<28;v++){
            int distance=Math.min(Math.abs(u-v),Math.abs(u-(31-v)));if(distance>1)continue;
            for(int depth=0;depth<2;depth++){int[] p=new int[3];p[axis]=side==0?2+depth:29-depth;p[(axis+1)%3]=u;p[(axis+2)%3]=v;put(p[0],p[1],p[2],distance==0?SHADOW:FRAME);}
        }
        // Four alternating space diagonals. Overlapping 2³ cells make Minecraft staircase supports.
        for(int bits:new int[]{0,3,5,6})for(int step=0;step<=8;step++){
            int x=(bits&1)==0?4+step:26-step,y=(bits&2)==0?4+step:26-step,z=(bits&4)==0?4+step:26-step;box(x,y,z,x+2,y+2,z+2,FRAME);
        }
        box(CENTER_MIN,CENTER_MIN,CENTER_MIN,CENTER_MAX,CENTER_MAX,CENTER_MAX,LEGACY_CORE);
        int occupied=0;for(byte material:VOXELS)if(material!=EMPTY)occupied++;ORDER=new int[occupied];int printed=0;
        for(int y=0;y<GRID;y++){for(int x=0;x<GRID;x++)for(int z=0;z<GRID;z++)if(material(x,y,z)!=EMPTY){int at=index(x,y,z);ORDER[printed]=at;RANK[at]=++printed;}LAYER_COUNTS[y+1]=printed;}
        FULL=Collections.unmodifiableList(surface(ORDER.length));
    }
    private static int shade(int rgb,double value){return (int)Math.round((rgb>>16&255)*value)<<16|(int)Math.round((rgb>>8&255)*value)<<8|(int)Math.round((rgb&255)*value);}
    private static int index(int x,int y,int z){return (y*GRID+z)*GRID+x;}
    public static int material(int x,int y,int z){return x<0||y<0||z<0||x>=GRID||y>=GRID||z>=GRID?EMPTY:VOXELS[index(x,y,z)];}
    private static void put(int x,int y,int z,int material){int at=index(x,y,z);if(VOXELS[at]!=CORNER)VOXELS[at]=(byte)material;}
    private static void box(int x0,int y0,int z0,int x1,int y1,int z1,int material){for(int x=x0;x<x1;x++)for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++)put(x,y,z,material);}
    public static int color(String id){require(id);return RGB[FRAME];}
    public static String legacyTexture(String id){require(id);String name=id.equals("meridian_core")?"meridian_drive_core":id.equals("paladyne_navigation_computer")?"paladyne_navigation_core":id.equals("seyr_navigation_computer")?"seyr_navigation_core":id+"_core";return "vandorlabs:blocks/matter/"+name;}
    private static void require(String id){if(!IDS.contains(id))throw new IllegalArgumentException("Unknown Core "+id);}
    public static List<Face> faces(String id){require(id);return FULL;}
    public static int layers(double progress){return Math.max(0,Math.min(GRID,(int)Math.floor(Math.max(0,Math.min(1,progress))*GRID)));}
    public static int voxelCount(){return ORDER.length;}
    public static int printed(double progress){return Math.max(0,Math.min(ORDER.length,(int)Math.floor(Math.max(0,Math.min(1,progress))*ORDER.length)));}
    public static double height(double progress){int count=printed(progress);return count==0?0:(ORDER[count-1]/(GRID*GRID)+1)/(double)GRID;}
    public static List<Face> faces(String id,int layers){require(id);if(layers<0||layers>GRID)throw new IllegalArgumentException("Core voxel layers");return prefix(LAYER_COUNTS[layers]);}
    /** A stable bottom-up voxel prefix, including partially printed rows; local tops preserve original UVs. */
    public static List<Face> faces(String id,double progress){require(id);return prefix(printed(progress));}
    private static synchronized List<Face> prefix(int count){if(count==0)return Collections.emptyList();if(count==ORDER.length)return FULL;List<Face> found=PREFIXES.get(count);if(found!=null)return found;List<Face> built=Collections.unmodifiableList(surface(count));long bytes=64L+built.size()*192L;while(prefixBytes+bytes>PREFIX_BUDGET&&!PREFIXES.isEmpty()){Iterator<Map.Entry<Integer,List<Face>>> iterator=PREFIXES.entrySet().iterator();Map.Entry<Integer,List<Face>> old=iterator.next();prefixBytes-=64L+old.getValue().size()*192L;iterator.remove();}if(bytes<=PREFIX_BUDGET){PREFIXES.put(count,built);prefixBytes+=bytes;}return built;}
    private static int printedMaterial(int x,int y,int z,int prefix){if(x<0||y<0||z<0||x>=GRID||y>=GRID||z>=GRID)return EMPTY;int at=index(x,y,z),rank=RANK[at];return rank>0&&rank<=prefix?VOXELS[at]:EMPTY;}
    private static List<Face> surface(int prefix){List<Face> result=new ArrayList<>();int[] mask=new int[GRID*GRID];
        for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1})for(int plane=0;plane<GRID;plane++){
            Arrays.fill(mask,0);int uAxis=(axis+1)%3,vAxis=(axis+2)%3;
            for(int u=0;u<GRID;u++)for(int v=0;v<GRID;v++){
                int x=axis==0?plane:uAxis==0?u:v,y=axis==1?plane:uAxis==1?u:v,z=axis==2?plane:uAxis==2?u:v;
                int material=printedMaterial(x,y,z,prefix),neighbor=printedMaterial(x+(axis==0?sign:0),y+(axis==1?sign:0),z+(axis==2?sign:0),prefix);
                if(material!=EMPTY&&neighbor==EMPTY)mask[v*GRID+u]=material;
            }
            for(int v=0;v<GRID;v++)for(int u=0;u<GRID;){int material=mask[v*GRID+u];if(material==0){u++;continue;}int width=1;while(u+width<GRID&&mask[v*GRID+u+width]==material)width++;int height=1;outer:while(v+height<GRID){for(int x=0;x<width;x++)if(mask[(v+height)*GRID+u+x]!=material)break outer;height++;}
                double[] positions=new double[12];int[][] corners=sign>0?new int[][]{{u,v},{u+width,v},{u+width,v+height},{u,v+height}}:new int[][]{{u,v+height},{u+width,v+height},{u+width,v},{u,v}};
                for(int vertex=0;vertex<4;vertex++){positions[vertex*3+axis]=(plane+(sign>0?1:0))/(double)GRID;positions[vertex*3+uAxis]=corners[vertex][0]/(double)GRID;positions[vertex*3+vAxis]=corners[vertex][1]/(double)GRID;}result.add(new Face(material,axis,sign,positions));for(int y=0;y<height;y++)Arrays.fill(mask,(v+y)*GRID+u,(v+y)*GRID+u+width,0);u+=width;
            }
        }return result;
    }
    private CoreMesh(){}
}

package com.vandorlabs.geometry;

import java.util.*;

/** Exact supplied cuboids, sampled at their authored quarter-model-unit (1/64 block) grid. */
final class ImportedCoreMesh {
    private static final int GRID=64;
    private static final Map<String,Model> MODELS=new HashMap<String,Model>();
    private static final LinkedHashMap<String,List<CoreMesh.Face>> PREFIXES=new LinkedHashMap<String,List<CoreMesh.Face>>(16,.75F,true);
    private static long bytes;
    private static final long BUDGET=8L*1024*1024;
    static boolean has(String id){return ImportedCoreData.model(id)!=null;}
    static synchronized Model model(String id){String key=ImportedCoreData.model(id);Model found=MODELS.get(key);if(found==null){found=new Model(key);MODELS.put(key,found);}return found;}
    static List<CoreMesh.Face> faces(String id,double progress){Model m=model(id);int count=m.printed(progress);if(count==m.order.length)return m.full;if(count==0)return Collections.emptyList();String key=m.name+":"+count;synchronized(PREFIXES){List<CoreMesh.Face> found=PREFIXES.get(key);if(found!=null)return found;List<CoreMesh.Face> built=Collections.unmodifiableList(m.surface(count));long size=64+built.size()*256L;while(bytes+size>BUDGET&&!PREFIXES.isEmpty()){Iterator<Map.Entry<String,List<CoreMesh.Face>>> it=PREFIXES.entrySet().iterator();Map.Entry<String,List<CoreMesh.Face>> old=it.next();bytes-=64+old.getValue().size()*256L;it.remove();}if(size<=BUDGET){PREFIXES.put(key,built);bytes+=size;}return built;}}
    static double height(String id,double progress){Model m=model(id);int count=m.printed(progress);return count==0?0:(m.order[count-1]/(GRID*GRID)+1)/(double)GRID;}
    private static int index(int x,int y,int z){return (y*GRID+z)*GRID+x;}
    static final class Model {
        final String name;final double[][] boxes;final short[] owner=new short[GRID*GRID*GRID];final int[] rank=new int[owner.length],order;final List<CoreMesh.Face> full;
        Model(String name){this.name=name;String[] input=ImportedCoreData.boxes(name);boxes=new double[input.length][];for(int b=0;b<input.length;b++){String[] numbers=input[b].split(",");double[] box=new double[numbers.length];for(int n=0;n<numbers.length;n++)box[n]=Double.parseDouble(numbers[n]);boxes[b]=box;for(int x=(int)box[0];x<(int)box[3];x++)for(int y=(int)box[1];y<(int)box[4];y++)for(int z=(int)box[2];z<(int)box[5];z++)owner[index(x,y,z)]=(short)(b+1);}
            int total=0;for(short value:owner)if(value!=0)total++;order=new int[total];int at=0;for(int y=0;y<GRID;y++)for(int x=0;x<GRID;x++)for(int z=0;z<GRID;z++){int cell=index(x,y,z);if(owner[cell]!=0){order[at]=cell;rank[cell]=++at;}}full=Collections.unmodifiableList(surface(total));}
        int printed(double progress){return Math.max(0,Math.min(order.length,(int)Math.floor(Math.max(0,Math.min(1,progress))*order.length)));}
        int material(int x,int y,int z,int count){if(x<0||y<0||z<0||x>=GRID||y>=GRID||z>=GRID)return 0;int at=index(x,y,z);return rank[at]>0&&rank[at]<=count?owner[at]:0;}
        List<CoreMesh.Face> surface(int count){List<CoreMesh.Face> faces=new ArrayList<CoreMesh.Face>();int[] mask=new int[GRID*GRID];for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1})for(int plane=0;plane<GRID;plane++){
            Arrays.fill(mask,0);int ua=(axis+1)%3,va=(axis+2)%3;for(int u=0;u<GRID;u++)for(int v=0;v<GRID;v++){int x=axis==0?plane:ua==0?u:v,y=axis==1?plane:ua==1?u:v,z=axis==2?plane:ua==2?u:v;int material=material(x,y,z,count);if(material!=0&&material(x+(axis==0?sign:0),y+(axis==1?sign:0),z+(axis==2?sign:0),count)==0)mask[v*GRID+u]=material;}
            for(int v=0;v<GRID;v++)for(int u=0;u<GRID;){int material=mask[v*GRID+u];if(material==0){u++;continue;}int w=1;while(u+w<GRID&&mask[v*GRID+u+w]==material)w++;int h=1;outer:while(v+h<GRID){for(int q=0;q<w;q++)if(mask[(v+h)*GRID+u+q]!=material)break outer;h++;}
                double[] positions=new double[12],uv=new double[8];int[][] corners=sign>0?new int[][]{{u,v},{u+w,v},{u+w,v+h},{u,v+h}}:new int[][]{{u,v+h},{u+w,v+h},{u+w,v},{u,v}};double[] box=boxes[material-1];int face=axis*2+(sign>0?1:0),offset=6+face*4;
                for(int vertex=0;vertex<4;vertex++){positions[vertex*3+axis]=(plane+(sign>0?1:0))/(double)GRID;positions[vertex*3+ua]=corners[vertex][0]/(double)GRID;positions[vertex*3+va]=corners[vertex][1]/(double)GRID;double x=positions[vertex*3]*GRID,y=positions[vertex*3+1]*GRID,z=positions[vertex*3+2]*GRID;
                    double tu=axis==0?(sign<0?(box[5]-z)/(box[5]-box[2]):(z-box[2])/(box[5]-box[2])):axis==2?(sign<0?(box[3]-x)/(box[3]-box[0]):(x-box[0])/(box[3]-box[0])):(x-box[0])/(box[3]-box[0]);double tv=axis==1?(sign<0?(box[5]-z)/(box[5]-box[2]):(z-box[2])/(box[5]-box[2])):(box[4]-y)/(box[4]-box[1]);uv[vertex*2]=(box[offset]+(box[offset+2]-box[offset])*tu)/16;uv[vertex*2+1]=(box[offset+1]+(box[offset+3]-box[offset+1])*tv)/16;}
                faces.add(new CoreMesh.Face(axis,sign,positions,uv));for(int row=0;row<h;row++)Arrays.fill(mask,(v+row)*GRID+u,(v+row)*GRID+u+w,0);u+=w;
            }
        }return faces;}
    }
    private ImportedCoreMesh(){}
}

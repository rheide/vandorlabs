package com.vandorlabs.neoforge;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Bake-time float vertex compatibility for the current authored control models.
 * Native quaternion rounding can move a45-degree normal across a directional-lighting axis tie.
 * Only position/order/direction are replaced; native UVs, quantized normals and material payload survive.
 */
final class NativeAuthoredControlBake {
    private static final int[][] VERTICES={{1,0,4,5},{2,3,7,6},{6,4,0,2},{3,1,5,7},{2,0,1,3},{7,5,4,6}};
    private record Face(float[][] points,Direction direction){}
    private static float[][] identity(){float[][] m=new float[4][4];for(int i=0;i<4;i++)m[i][i]=1;return m;}
    private static float[][] rotation(int axis,float angle){float[][] m=identity();float t=angle*.017453292F,c=(float)Math.cos(t),s=(float)Math.sin(t);int a=(axis+1)%3,b=(axis+2)%3;m[a][a]=c;m[a][b]=-s;m[b][a]=s;m[b][b]=c;/* Preserve the legacy float Rodrigues diagonal, including90-degree axis rounding. */m[axis][axis]=(1-c)+c;return m;}
    private static float[][] multiply(float[][] a,float[][] b){float[][] result=new float[4][4];for(int i=0;i<4;i++)for(int j=0;j<4;j++)result[i][j]=a[i][0]*b[0][j]+a[i][1]*b[1][j]+a[i][2]*b[2][j]+a[i][3]*b[3][j];return result;}
    private static float[] apply(float[][] m,float[] p){return new float[]{m[0][0]*p[0]+m[0][1]*p[1]+m[0][2]*p[2]+m[0][3],m[1][0]*p[0]+m[1][1]*p[1]+m[1][2]*p[2]+m[1][3],m[2][0]*p[0]+m[2][1]*p[1]+m[2][2]*p[2]+m[2][3]};}
    private static float[] vector(JsonArray a){return new float[]{a.get(0).getAsFloat()/16F,a.get(1).getAsFloat()/16F,a.get(2).getAsFloat()/16F};}
    private static List<Face> faces(NativeMountedControlGeometry.Authored authored){
        float[][] matrix=multiply(rotation(1,-authored.y()),rotation(0,-authored.x())),plus=identity(),minus=identity();for(int i=0;i<3;i++){plus[i][3]=.5F;minus[i][3]=-.5F;}matrix=multiply(multiply(plus,matrix),minus);boolean variant=authored.x()!=0||authored.y()!=0;List<Face> result=new ArrayList<>();
        for(JsonElement value:authored.elements()){
            JsonObject element=value.getAsJsonObject();float[] lo=vector(element.getAsJsonArray("from")),hi=vector(element.getAsJsonArray("to"));float[][] corners=new float[8][];JsonObject r=element.has("rotation")?element.getAsJsonObject("rotation"):null;float[][] part=r==null?null:rotation("xyz".indexOf(r.get("axis").getAsString()),r.get("angle").getAsFloat());float[] origin=r==null?null:vector(r.getAsJsonArray("origin"));
            for(int i=0;i<8;i++){float[] p={((i&4)==0?lo:hi)[0],((i&2)==0?lo:hi)[1],((i&1)==0?lo:hi)[2]};if(r!=null){for(int a=0;a<3;a++)p[a]-=origin[a];p=apply(part,p);if(r.has("rescale")&&r.get("rescale").getAsBoolean()){float degrees=Math.abs(r.get("angle").getAsFloat());float scale=degrees==22.5F?(float)(1/Math.cos(Math.PI/8)-1):(float)(1/Math.cos(Math.PI/4)-1);int axis="xyz".indexOf(r.get("axis").getAsString());for(int a=0;a<3;a++)p[a]*=a==axis?1:1+scale;}for(int a=0;a<3;a++)p[a]+=origin[a];}if(variant)p=apply(matrix,p);corners[i]=p;}
            for(String name:element.getAsJsonObject("faces").keySet()){
                Direction source=Direction.byName(name);/* Current Forge wraps variants in TRSRTransformation, whose vertex-index mapping is identity. */float[][] points=new float[4][];for(int i=0;i<4;i++)points[i]=corners[VERTICES[source.get3DDataValue()][i]];Direction direction=direction(points);
                if(r==null){float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY},max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};for(float[] p:points)for(int a=0;a<3;a++){min[a]=Math.min(min[a],p[a]);max[a]=Math.max(max[a],p[a]);}for(int i=0;i<4;i++){int corner=VERTICES[direction.get3DDataValue()][i];points[i]=new float[]{((corner&4)==0?min:max)[0],((corner&2)==0?min:max)[1],((corner&1)==0?min:max)[2]};}}
                result.add(new Face(points,direction));
            }
        }return result;
    }
    private static Direction direction(float[][] p){float ax=p[0][0]-p[1][0],ay=p[0][1]-p[1][1],az=p[0][2]-p[1][2],bx=p[2][0]-p[1][0],by=p[2][1]-p[1][1],bz=p[2][2]-p[1][2];float x=by*az-bz*ay,y=bz*ax-bx*az,z=bx*ay-by*ax,length=(float)Math.sqrt(x*x+y*y+z*z);x/=length;y/=length;z/=length;Direction selected=Direction.UP;float best=0;for(Direction d:Direction.values()){float dot=x*d.getStepX()+y*d.getStepY()+z*d.getStepZ();if(dot>=0&&dot>best){best=dot;selected=d;}}return selected;}
    private static BakedQuad correct(BakedQuad original,List<Face> faces){
        int[] raw=original.getVertices();int stride=raw.length/4;double best=Double.POSITIVE_INFINITY;Face chosen=null;int[] mapping=null;
        for(Face face:faces){int[] found=new int[4];boolean[] used=new boolean[4];double total=0;boolean valid=true;for(int i=0;i<4;i++){int nearest=-1;double distance=Double.POSITIVE_INFINITY;for(int v=0;v<4;v++)if(!used[v]){double d=0;for(int a=0;a<3;a++)d+=Math.abs(Float.intBitsToFloat(raw[v*stride+a])-face.points[i][a]);if(d<distance){distance=d;nearest=v;}}if(distance>1e-5){valid=false;break;}used[nearest]=true;found[i]=nearest;total+=distance;}
            if(valid){int a=found[0],b=found[1],c=found[2];float ux=Float.intBitsToFloat(raw[b*stride])-Float.intBitsToFloat(raw[a*stride]),uy=Float.intBitsToFloat(raw[b*stride+1])-Float.intBitsToFloat(raw[a*stride+1]),uz=Float.intBitsToFloat(raw[b*stride+2])-Float.intBitsToFloat(raw[a*stride+2]);float vx=Float.intBitsToFloat(raw[c*stride])-Float.intBitsToFloat(raw[a*stride]),vy=Float.intBitsToFloat(raw[c*stride+1])-Float.intBitsToFloat(raw[a*stride+1]),vz=Float.intBitsToFloat(raw[c*stride+2])-Float.intBitsToFloat(raw[a*stride+2]);float dot=(uy*vz-uz*vy)*original.getDirection().getStepX()+(uz*vx-ux*vz)*original.getDirection().getStepY()+(ux*vy-uy*vx)*original.getDirection().getStepZ();if(dot<=0)valid=false;}
            if(valid&&total<best){best=total;chosen=face;mapping=found;}
        }
        // User resource packs may replace authored geometry; preserve their native quad rather than guessing.
        if(chosen==null)return original;int[] out=raw.clone();for(int i=0;i<4;i++){System.arraycopy(raw,mapping[i]*stride,out,i*stride,stride);for(int a=0;a<3;a++)out[i*stride+a]=Float.floatToRawIntBits(chosen.points[i][a]);}return new BakedQuad(out,original.getTintIndex(),chosen.direction,original.getSprite(),original.isShade(),original.hasAmbientOcclusion());
    }
    static boolean isStatic(BakedModel model){return model.getClass()==net.minecraft.client.resources.model.SimpleBakedModel.class||model instanceof Corrected;}
    static BakedModel correct(BakedModel original,BlockState state,NativeMountedControlGeometry.Authored authored){
        if(original.getClass()!=net.minecraft.client.resources.model.SimpleBakedModel.class)return original;
        List<Face> faces=faces(authored);Map<Direction,List<BakedQuad>> sides=new HashMap<>();for(Direction side:new Direction[]{null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST}){List<BakedQuad> quads=new ArrayList<>();for(var q:original.getQuads(state,side,RandomSource.create(0),ModelData.EMPTY,null))quads.add(correct(q,faces));sides.put(side,List.copyOf(quads));}return new Corrected(original,sides);
    }
    private static final class Corrected extends BakedModelWrapper<BakedModel> {
        private final Map<Direction,List<BakedQuad>> sides;
        Corrected(BakedModel original,Map<Direction,List<BakedQuad>> sides){super(original);this.sides=sides;}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return sides.get(side);}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){return sides.get(side);}
    }
    private NativeAuthoredControlBake(){}
}

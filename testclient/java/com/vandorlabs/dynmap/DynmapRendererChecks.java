package com.vandorlabs.dynmap;

import java.lang.reflect.Proxy;
import java.util.*;
import org.dynmap.renderer.*;
import com.vandorlabs.tiles.ScreenHousingTextures;

/** Exercises adapter output against the published Dynmap API, without a client or world. */
public final class DynmapRendererChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class Patch implements RenderPatch {
        final int texture;final double[] coordinates,limits;
        Patch(int texture,double[] coordinates,double[] limits){this.texture=texture;this.coordinates=coordinates;this.limits=limits;}
        double[] point(double u,double v){double[] p=new double[3];for(int i=0;i<3;i++)p[i]=coordinates[i]+u*(coordinates[3+i]-coordinates[i])+v*(coordinates[6+i]-coordinates[i]);return p;}
        double[][] corners(){return new double[][]{point(limits[0],limits[2]),point(limits[0],limits[3]),point(limits[1],limits[4]),point(limits[1],limits[5])};}
        @Override public int getTextureIndex(){return texture;}
    }
    private static final RenderPatchFactory FACTORY=(RenderPatchFactory)Proxy.newProxyInstance(DynmapRendererChecks.class.getClassLoader(),new Class[]{RenderPatchFactory.class},(proxy,method,args)->{
        if(method.getName().equals("getPatch")){
            double[] points=new double[9];for(int i=0;i<9;i++)points[i]=(Double)args[i];
            double[] limits=args.length==17?new double[]{(Double)args[9],(Double)args[10],(Double)args[11],(Double)args[13],(Double)args[12],(Double)args[14]}:
                    args.length==15?new double[]{(Double)args[9],(Double)args[10],(Double)args[11],(Double)args[12],(Double)args[11],(Double)args[12]}:new double[]{0,1,0,1,0,1};
            return new Patch((Integer)args[args.length-1],points,limits);
        }
        throw new AssertionError("Unexpected factory call "+method);
    });
    private static final class Context implements MapDataContext {
        final DynmapBlockState state;final Map<String,Object> data=new HashMap<>();
        Context(String block,String properties){state=new DynmapBlockState(null,0,block,properties,"IRON");}
        public RenderPatchFactory getPatchFactory(){return FACTORY;}
        public DynmapBlockState getBlockType(){return state;}
        public Object getBlockTileEntityField(String name){return data.get(name);}
        public DynmapBlockState getBlockTypeAt(int x,int y,int z){return state;}
        public Object getBlockTileEntityFieldAt(String name,int x,int y,int z){return data.get(name);}
        public int getX(){return 0;}public int getY(){return 80;}public int getZ(){return 0;}
    }
    private static RenderPatch[] valid(CustomRenderer renderer,Context context){
        RenderPatch[] patches=renderer.getRenderPatchList(context);require(patches.length>0,"Nonempty mesh "+context.state);
        for(RenderPatch patch:patches){require(patch!=null,"Null patch");require(patch.getTextureIndex()>=0&&patch.getTextureIndex()<renderer.getMaximumTextureCount(FACTORY),"Texture exceeds declared map palette");for(double v:((Patch)patch).coordinates)require(Double.isFinite(v),"Finite geometry");}
        return patches;
    }
    private static void surfaces(){
        Context context=new Context("minecraft:stone","");List<RenderPatch> patches=new ArrayList<>();
        MapSurface.emit(context,patches,Arrays.asList(new MapSurface.Vertex(new net.minecraft.util.math.Vec3d(0,0,0),0,0),new MapSurface.Vertex(new net.minecraft.util.math.Vec3d(1,0,0),1,0),new MapSurface.Vertex(new net.minecraft.util.math.Vec3d(1,.5,0),1,.5),new MapSurface.Vertex(new net.minecraft.util.math.Vec3d(0,1,0),0,1)),0);
        require(!patches.isEmpty(),"Trapezoid emitted");
        for(RenderPatch raw:patches)for(double[] p:((Patch)raw).corners())require(p[1]<=1-.5*p[0]+1e-9,"Sloping side has no rectangular overfill");
        patches.clear();
        MapSurface.emit(context,patches,Arrays.asList(new MapSurface.Vertex(new net.minecraft.util.math.Vec3d(-.25,0,0),0,0),new MapSurface.Vertex(new net.minecraft.util.math.Vec3d(.75,0,0),1,0),new MapSurface.Vertex(new net.minecraft.util.math.Vec3d(.75,1,0),1,1)),0);
        double area=0;
        for(RenderPatch raw:patches){Patch patch=(Patch)raw;double[][] p=patch.corners();for(double[] corner:p)require(corner[0]>=-1e-9&&corner[1]<=corner[0]+.25+1e-9,"Clipped triangular face remains within authored triangle");area+=(p[2][0]-p[0][0])*((p[1][1]-p[0][1])+(p[3][1]-p[2][1]))/2;}
        require(Math.abs(area-.46875)<1e-9,"Clipping preserves exact triangular area");
        patches.clear();
        MapSurface.emit(context,patches,Arrays.asList(new MapSurface.Vertex(new net.minecraft.util.math.Vec3d(0,0,0),0,0),new MapSurface.Vertex(new net.minecraft.util.math.Vec3d(1,0,0),2,0),new MapSurface.Vertex(new net.minecraft.util.math.Vec3d(1,1,0),2,1),new MapSurface.Vertex(new net.minecraft.util.math.Vec3d(0,1,0),0,1)),0);
        require(patches.size()==2,"Authored repeated texture splits into two map tiles");
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();
        surfaces();
        ProgrammableSlabRenderer slab=new ProgrammableSlabRenderer();
        for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++)for(String half:new String[]{"bottom","top"}){
            Context c=new Context("vandorlabs:programmable_slab","facing=north,half="+half);c.data.put("housingTexture",choice);
            for(RenderPatch p:valid(slab,c))require(p.getTextureIndex()==ScreenHousingTextures.canonicalChoice(choice),"Slab uses selected material");
        }
        for(String facing:new String[]{"north","east","south","west"})for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int fill=0;fill<4;fill++){
            Context c=new Context("vandorlabs:programmable_diagonal_wall","facing="+facing+",inverted="+inverted);c.data.put("DiagonalFullWidth",mode==1);c.data.put("DiagonalHalfHeight",mode==2);c.data.put("DiagonalFill",fill);c.data.put("housingTexture",1);
            RenderPatch[] patches=valid(new DiagonalWallRenderer(),c);
            if(fill==0){
                int axis=mode==2?1:facing.equals("east")||facing.equals("west")?0:2;
                int rise=mode==2?(facing.equals("east")||facing.equals("west")?0:2):1;
                double sign=inverted?1:-1;
                if(mode!=2&&(facing.equals("east")||facing.equals("south")))sign=-sign;
                boolean found=false;
                for(RenderPatch patch:patches){double[] p=((Patch)patch).coordinates;
                    if(Math.abs(p[3+axis]-p[axis])>1e-9||Math.abs(p[6+rise]-p[rise])<1e-9)continue;
                    double expected=sign*(mode==1?1:.5);
                    require(Math.abs(p[6+axis]-p[axis]-expected)<1e-8,"Continuous whole-panel UVs follow facing and inversion");found=true;
                }
                require(found,"Slope surface survives map cell clipping");
            }
            if(mode==2&&fill==3){
                double low=1,high=0;
                for(RenderPatch raw:patches){Patch p=(Patch)raw;
                    for(double[] corner:p.corners()){double y=corner[1];require(y>=-1e-8&&y<=1+1e-8,"Half-height fill remains in map cell");low=Math.min(low,y);high=Math.max(high,y);}
                }
                require(Math.abs(low)<1e-8&&Math.abs(high-1)<1e-8,"Both fills cover the unused half for either inversion");
            }
        }
        for(String facing:new String[]{"north","east","south","west"})for(String shape:new String[]{"straight","inner_left","inner_right","outer_left","outer_right"})for(int roll=0;roll<3;roll++){
            Context c=new Context("minecraft:oak_stairs","facing="+facing+",half=bottom,shape="+shape);c.data.put("housingTexture",1);c.data.put("StairRoll",roll);c.data.put("StairTread",4);
            valid(new ProgrammableShapeRenderer(),c);
        }
        Context canopy=new Context("vandorlabs:regular_canopy_glass","facing=north");canopy.data.put("CanopyShade",18);canopy.data.put("CanopyTint",2);
        valid(new CanopyRenderer(),canopy);
        for(String facing:new String[]{"north","east","south","west"})for(int mount=0;mount<4;mount++){
            Context mounted=new Context("vandorlabs:regular_canopy_glass","facing="+facing);mounted.data.put("CanopyMount",mount);mounted.data.put("CanopyShade",18);mounted.data.put("CanopyTint",2);
            boolean glazing=false;for(RenderPatch patch:valid(new CanopyRenderer(),mounted))glazing|=patch.getTextureIndex()==60;
            require(glazing,"Mounted canopy retains its glazing artwork");
        }
        require(new CanopyRenderer().getMaximumTextureCount(FACTORY)==61,"All canopy materials declared");
        for(int design=0;design<30;design++)for(String facing:new String[]{"north","east","south","west"})for(boolean open:new boolean[]{false,true}){
            Context door=new Context("vandorlabs:programmable_door","facing="+facing+",half=lower,hinge=left,open="+open);door.data.put("SpaceDesign",design);
            RenderPatch[] mesh=valid(new ProgrammableDoorRenderer(),door);
            if(!open){
                int leaf=ProgrammableDoorRenderer.material(door,0,0,0);boolean found=false;
                for(RenderPatch patch:mesh)if(patch.getTextureIndex()==leaf){
                    Patch p=(Patch)patch;int axis=facing.equals("east")||facing.equals("west")?0:2;
                    double average=p.point((p.limits[0]+p.limits[1])/2,(p.limits[2]+p.limits[3])/2)[axis];
                    require(facing.equals("east")||facing.equals("south")?average>.5:average<.5,"Closed door sits on the authored facing side");found=true;break;
                }
                require(found,"Authored door leaf retains selected material");
            }
        }
        System.out.println("Dynmap renderer checks PASS ("+checks+" assertions)");
    }
}

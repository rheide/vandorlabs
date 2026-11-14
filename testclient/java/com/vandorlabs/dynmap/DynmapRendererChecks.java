package com.vandorlabs.dynmap;

import java.lang.reflect.Proxy;
import java.util.*;
import org.dynmap.renderer.*;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.GlassTextures;

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
        if(method.getName().equals("getRotatedPatch")){
            Patch source=(Patch)args[0];double[] points=source.coordinates.clone();double angle=Math.toRadians(((Number)args[2]).doubleValue()),sin=Math.sin(angle),cos=Math.cos(angle);
            for(int i=0;i<9;i+=3){double x=points[i]-.5,z=points[i+2]-.5;points[i]=.5+x*cos-z*sin;points[i+2]=.5+x*sin+z*cos;}
            return new Patch((Integer)args[4],points,source.limits);
        }
        throw new AssertionError("Unexpected factory call "+method);
    });
    private static class Context implements MapDataContext {
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
    private static void trapdoorFrames(){
        int variants=0;
        for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++)if(ScreenHousingTextures.visible(choice)&&"Frame".equals(ScreenHousingTextures.category(choice))&&ScreenHousingTextures.canonicalChoice(choice)==choice) {
            variants++;
            for(boolean diagonal:new boolean[]{false,true})for(String facing:new String[]{"north","east","south","west"})for(String half:new String[]{"top","bottom"})for(boolean open:new boolean[]{false,true})for(int position=0;position<3;position++) {
                CustomRenderer renderer=diagonal?new ProgrammableDiagonalTrapdoorRenderer():new ProgrammableTrapdoorRenderer();
                Context context=new Context("vandorlabs:"+(diagonal?"programmable_diagonal_trapdoor":"programmable_trapdoor"),"facing="+facing+",half="+half+",open="+open);
                context.data.put("housingTexture",ScreenHousingTextures.choiceAt(choice));context.data.put("TrapdoorPosition",position);
                RenderPatch[] patches=valid(renderer,context);int metal=0,art=0;
                for(RenderPatch patch:patches)if(patch.getTextureIndex()==ScreenHousingTextures.BUILTIN_COUNT*2+2)metal++;else if(patch.getTextureIndex()==choice)art++;
                require(patches.length==12&&metal==6&&art==6,"Frame trapdoor retains solid metal backing and selected artwork on every pose");
            }
        }
        require(variants>0,"Frame trapdoor palette exercised");
        System.out.println("Frame trapdoor Dynmap PASS variants="+variants+" regular/diagonal all-facings halves positions open/closed opaque-backing");
    }
    private static void segmented(){
        SegmentedDoorMapRenderer renderer=new SegmentedDoorMapRenderer();
        for(int height:new int[]{1,5,32})for(int row=0;row<height;row++)for(boolean open:new boolean[]{false,true})for(String facing:new String[]{"north","east","south","west"}){
            Context context=new Context("vandorlabs:segmented_door","facing="+facing);context.data.put("SegmentedTopOffset",row);context.data.put("SegmentedHeight",height);context.data.put("SegmentedOpen",(byte)(open?1:0));
            RenderPatch[] mesh=valid(renderer,context);require(mesh.length==(row==0?12:6),"Segmented segment and top header patch count");
            double min=Double.POSITIVE_INFINITY,max=-min;for(int i=0;i<6;i++)for(double[] p:((Patch)mesh[i]).corners()){min=Math.min(min,p[1]-row);max=Math.max(max,p[1]-row);}
            if(open)require(min>=1-com.vandorlabs.segmented.SegmentedDoorGeometry.HALF_THICKNESS-1e-8&&max<=1+com.vandorlabs.segmented.SegmentedDoorGeometry.HALF_THICKNESS+1e-8,"Segmented overhead panels retain column height");
            else require(min>=-row-1e-8&&max<=1-row+1e-8,"Segmented closed panel remains in native cell");
        }
        int variants=0;for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true})for(int shape=-1;shape<4;shape++)for(boolean open:new boolean[]{false,true})for(String facing:new String[]{"north","east","south","west"}){
            Context context=new Context("vandorlabs:segmented_door","facing="+facing);context.data.put("SegmentedTopOffset",1);context.data.put("SegmentedHeight",3);context.data.put("SegmentedColumn",1);context.data.put("SegmentedColumns",3);context.data.put("SegmentedOpen",(byte)(open?1:0));context.data.put("SegmentedSlide",direction);context.data.put("SegmentedStraight",(byte)(straight?1:0));context.data.put("SegmentedWindow",(byte)(shape>=0?1:0));context.data.put("SegmentedShape",Math.max(0,shape));context.data.put("SegmentedJoin",(byte)1);context.data.put("SegmentedGlassShade",7);context.data.put("SegmentedGlassTint",2);RenderPatch[] mesh=valid(renderer,context);require(mesh.length>0,"Directional/window Segmented Door map geometry");if(shape>=0){boolean glass=false;for(RenderPatch p:mesh)glass|=p.getTextureIndex()==ScreenHousingTextures.BUILTIN_COUNT*2+21+7*4+2;require(glass,"Native window shade/tint map material");}variants++;
        }
        for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<GlassTextures.TINT_COUNT;tint++)for(int tread:new int[]{8,16}){
            Context context=new Context("vandorlabs:segmented_door","facing=north");
            context.data.put("SegmentedSlide",0);context.data.put("SegmentedWindow",(byte)1);context.data.put("SegmentedTread",tread);
            context.data.put("SegmentedGlassShade",shade);context.data.put("SegmentedGlassTint",tint);
            boolean glass=false;for(RenderPatch patch:valid(renderer,context))glass|=patch.getTextureIndex()==ScreenHousingTextures.BUILTIN_COUNT*2+21+shade*GlassTextures.TINT_COUNT+tint;
            require(glass,"Every native glass finish survives direct and prepared Segmented map meshes");
        }
        for(int rotation=0;rotation<4;rotation++){
            final int rot=rotation,rx=rotation==0?1:rotation==2?-1:0,rz=rotation==1?-1:rotation==3?1:0;
            Context grouped=new Context("vandorlabs:segmented_door","facing="+new String[]{"north","west","south","east"}[rotation]){
                @Override public Object getBlockTileEntityFieldAt(String name,int x,int y,int z){int col=1+x*rx+z*rz,row=1-y;if(name.equals("SegmentedWindow")||name.equals("SegmentedJoin"))return (byte)(row==1&&col>=0&&col<3?1:0);if(name.equals("SegmentedShape"))return 2;return null;}
            };grouped.data.put("SegmentedJoin",(byte)1);grouped.data.put("SegmentedShape",2);
            require(Arrays.equals(SegmentedDoorMapRenderer.windowGroup(grouped,1,1,3,3,rot),new int[]{1,0,3,1}),"Map window fit excludes surrounding solid rows all facings");
            grouped.data.put("SegmentedShape",1);require(Arrays.equals(SegmentedDoorMapRenderer.windowGroup(grouped,1,1,3,3,rot),new int[]{0,0,1,1}),"Map window fit excludes different shapes");
        }
        System.out.println("Segmented Dynmap PASS heights=1,5,32 all-sections closed/open root-headers directional/window variants="+variants);
    }
    private static void segmentedGates(){
        SegmentedDoorMapRenderer renderer=new SegmentedDoorMapRenderer();int profiles=0,base=ScreenHousingTextures.BUILTIN_COUNT*2+21+GlassTextures.IDS.length*GlassTextures.TINT_COUNT;
        for(int kind=0;kind<3;kind++){
            int selected=-1;for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(com.vandorlabs.segmented.SegmentedArtworkPolicy.gateId(kind).equals(ScreenHousingTextures.IDS[i]))selected=i;
            if(selected<0)continue;final int material=selected;
            for(int rotation=0;rotation<4;rotation++)for(int direction=0;direction<4;direction++)for(int position=0;position<3;position++)for(boolean straight:new boolean[]{false,true})for(boolean open:new boolean[]{false,true})for(int mask=0;mask<16;mask++){
                final int rot=rotation,joins=mask,rx=rotation==0?1:rotation==2?-1:0,rz=rotation==1?-1:rotation==3?1:0;
                Context context=new Context("vandorlabs:segmented_door","facing="+new String[]{"north","west","south","east"}[rotation]){
                    @Override public Object getBlockTileEntityFieldAt(String name,int x,int y,int z){int column=1+x*rx+z*rz,row=1-y;if(name.equals("SegmentedColumn"))return column;if(name.equals("SegmentedTopOffset"))return row;if(name.equals("SegmentedTexture")){boolean horizontal=column==1||column==0&&(joins&1)!=0||column==2&&(joins&2)!=0,vertical=row==1||row==2&&(joins&4)!=0||row==0&&(joins&8)!=0;return horizontal&&vertical?material:-1;}return data.get(name);}
                };
                context.data.put("SegmentedTexture",selected);context.data.put("SegmentedTopOffset",1);context.data.put("SegmentedHeight",3);context.data.put("SegmentedColumn",1);context.data.put("SegmentedColumns",3);context.data.put("SegmentedOpen",(byte)(open?1:0));context.data.put("SegmentedSlide",direction);context.data.put("SegmentedPosition",position);context.data.put("SegmentedStraight",(byte)(straight?1:0));context.data.put("SegmentedWindow",(byte)1);context.data.put("SegmentedJoin",(byte)1);
                RenderPatch[] patches=valid(renderer,context);boolean authored=false;for(RenderPatch patch:patches){int texture=patch.getTextureIndex();authored|=texture>=base&&texture<base+7;require(texture>=base&&texture<base+7||texture==2*ScreenHousingTextures.BUILTIN_COUNT+2,"Gate map uses authored materials/opaque header, never picker preview or latent glass");}require(authored&&patches.length>20,"Gate map emits authored bars");profiles++;
            }
        }
        require(profiles==9216,"All supplied map gate profiles present");require(SegmentedGateMapMeshes.retainedBytes()<=4*1024*1024,"Gate map retained-byte bound");
        System.out.println("Segmented gate Dynmap PASS profiles="+profiles+" authoredMaterials/allMasks/facings/directions/positions/endpoints/latentWindow/bytes="+SegmentedGateMapMeshes.retainedBytes());
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();
        surfaces();trapdoorFrames();segmented();segmentedGates();
        FrameMachineMapChecks.run();
        UtilityMapChecks.run();SurfaceMapChecks.run();HexThrusterMapChecks.run();
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
        for(String facing:new String[]{"north","east","south","west"})for(int mount=0;mount<4;mount++)for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<GlassTextures.TINT_COUNT;tint++){
            Context mounted=new Context("vandorlabs:regular_canopy_glass","facing="+facing);mounted.data.put("CanopyMount",mount);mounted.data.put("CanopyShade",shade);mounted.data.put("CanopyTint",tint);
            boolean glazing=false;for(RenderPatch patch:valid(new CanopyRenderer(),mounted))glazing|=patch.getTextureIndex()==4+shade*GlassTextures.TINT_COUNT+tint;
            require(glazing,"Mounted canopy retains its glazing artwork");
        }
        require(new CanopyRenderer().getMaximumTextureCount(FACTORY)==4+GlassTextures.IDS.length*GlassTextures.TINT_COUNT,"All canopy materials declared");
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
        int utilities=0;for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++)if(ScreenHousingTextures.IDS[choice].startsWith("utility_door_")){
            utilities++;for(boolean large:new boolean[]{false,true}){Context door=new Context(large?"vandorlabs:large_programmable_door":"vandorlabs:programmable_door","facing=south,half=lower,hinge=right,open=false");door.data.put("SpaceDesign",2);door.data.put("DoorFaceTexture",choice);door.data.put("LargeDoorColumn",0);door.data.put("LargeDoorRow",0);boolean selected=false;for(RenderPatch patch:valid(large?new LargeProgrammableDoorRenderer():new ProgrammableDoorRenderer(),door))selected|=patch.getTextureIndex()==ScreenHousingTextures.BUILTIN_COUNT+choice;require(selected,"Utility full artwork map material ordinary/Large "+choice);}
        }require(utilities==16,"Utility Door map choices");
        System.out.println("Dynmap renderer checks PASS ("+checks+" assertions)");
    }
}

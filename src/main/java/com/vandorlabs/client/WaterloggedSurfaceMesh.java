package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.render.DiagonalMeshClip;
import com.vandorlabs.render.DiagonalWallGeometry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;

/** Visible fluid boundaries follow continuous panels, independently of conservative collision slices. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class WaterloggedSurfaceMesh {
    static final class Surface {
        final Vec3d[] vertices;final Vec3d normal;final int side,axis;final EnumFacing boundary;
        Surface(List<double[]> polygon,int side,int axis,EnumFacing boundary){this.vertices=new Vec3d[polygon.size()];for(int i=0;i<vertices.length;i++){double[] p=polygon.get(i);vertices[i]=new Vec3d(p[0],p[1],p[2]);}normal=WaterloggedSurfaceMesh.normal(polygon).normalize();this.side=side;this.axis=axis;this.boundary=boundary;}
    }
    private static final class Region {
        final List<double[]> planes=new ArrayList<>();final int side;
        Region(int side){this.side=side;}
        Region add(double x,double y,double z,double d){planes.add(new double[]{x,y,z,d});return this;}
    }
    private static final class Source {
        final WaterloggedParts.Volume volume;final FluidStack[] fluids;final List<Region> regions=new ArrayList<>();final List<Surface> surfaces=new ArrayList<>();boolean splitRegions;
        Source(World world,BlockPos pos){this(world,pos,false);}
        Source(World world,BlockPos pos,boolean detached){volume=detached?(WaterloggedParts.panel(world.getBlockState(pos))||OpeningFluids.supports(world.getBlockState(pos))?null:WaterloggedParts.uncachedVolume(world,pos)):WaterloggedParts.volume(world,pos);fluids=new FluidStack[]{WaterloggedParts.fluid(world,pos,0),WaterloggedParts.fluid(world,pos,1)};}
    }
    static final class Entry {
        final Object[] volumes;final FluidStack[] fluids;final List<Surface> mesh;
        final java.lang.ref.WeakReference<TileEntity> tile;final long fluidsRevision,panelsRevision;final int vehicleRevision;
        Entry(Object[] volumes,FluidStack[] fluids,List<Surface> mesh,TileEntity tile){this.volumes=volumes;this.fluids=fluids;this.mesh=mesh;this.tile=new java.lang.ref.WeakReference<>(tile);fluidsRevision=ContainedFluids.revision();panelsRevision=panelRevision();vehicleRevision=vehicleRevision(tile.getWorld());}
    }
    private static long panelRevision(){return com.vandorlabs.tiles.GlassMaterials.revision()+com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.getPortholeRevision();}
    private static int vehicleRevision(World world){return world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).tileRevision:0;}
    private static final class WorldCache extends com.vandorlabs.world.NeighbourhoodCache<Entry> {
        final Map<BlockPos,Entry> entries=super.entries;
        WorldCache(){super(2048);}
    }
    private static final Map<World,WorldCache> WORLDS=new WeakHashMap<>();
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unloaded(net.minecraftforge.event.world.WorldEvent.Unload event){if(!event.getWorld().isRemote)return;WorldCache cache=WORLDS.remove(event.getWorld());if(cache!=null)event.getWorld().removeEventListener(cache);}
    static List<Surface> get(TileEntity tile){return entry(tile).mesh;}
    // Fluid descriptions and surfaces share the same revision-checked cache lifetime.
    static Entry entry(TileEntity tile){
        World world=tile.getWorld();BlockPos pos=tile.getPos();WorldCache cache=WORLDS.get(world);if(cache==null){cache=new WorldCache();WORLDS.put(world,cache);world.addEventListener(cache);}Entry cached=cache.entries.get(pos);
        if(cached!=null&&cached.tile.get()==tile&&cached.fluidsRevision==ContainedFluids.revision()&&cached.panelsRevision==panelRevision()&&cached.vehicleRevision==vehicleRevision(world))return cached;
        Source[] sources=new Source[7];Object[] volumes=new Object[7];FluidStack[] fluids=new FluidStack[14];
        for(int index=0;index<7;index++){BlockPos at=index==0?pos:pos.offset(EnumFacing.values()[index-1]);if(!world.isBlockLoaded(at)||!WaterloggedParts.wet(world,at))continue;Source source=new Source(world,at);sources[index]=source;volumes[index]=source.volume;fluids[index*2]=source.fluids[0];fluids[index*2+1]=source.fluids[1];}
        // A changed diagonal wet column can alter corner heights without changing
        // these seven local volumes. Rebuild after revision invalidation as well.
        for(int i=0;i<sources.length;i++)if(sources[i]!=null)build(sources[i],world,i==0?pos:pos.offset(EnumFacing.values()[i-1]));
        List<Surface> mesh=new ArrayList<>();Source own=sources[0];if(own!=null)for(Surface surface:own.surfaces){
            FluidStack fluid=own.fluids[surface.side];if(fluid==null)continue;List<List<double[]>> pieces=new ArrayList<>();pieces.add(points(surface.vertices));
            if(surface.boundary!=null){BlockPos next=pos.offset(surface.boundary);if(world.isBlockLoaded(next)){IBlockState liquid=world.getBlockState(next);net.minecraftforge.fluids.Fluid nativeFluid=WaterloggedParts.nativeFluid(liquid);if(nativeFluid==fluid.getFluid()||liquid.doesSideBlockRendering(world,next,surface.boundary.getOpposite()))continue;}}
            Source neighbor=surface.boundary==null?null:sources[surface.boundary.getIndex()+1];
            if(neighbor!=null)for(Region region:neighbor.regions)if(equal(fluid,neighbor.fluids[region.side])){
                List<List<double[]>> remaining=new ArrayList<>();for(List<double[]> piece:pieces)subtract(piece,region,surface.boundary,remaining);pieces=remaining;
            }
            for(List<double[]> piece:pieces)if(area(piece)>1e-10){
                // Free surfaces follow native source/flowing-water corner heights. Covered
                // compartments retain their full volume and seamless vertical joins.
                if(fluid.getFluid()==net.minecraftforge.fluids.FluidRegistry.WATER||fluid.getFluid()==net.minecraftforge.fluids.FluidRegistry.LAVA){
                    for(double[] point:piece)if(point[1]>.998){
                        double height=ContainedFluidHeights.height(world,pos,fluid.getFluid(),point[0],point[2]);
                        if(height<.998)point[1]=height;
                    }
                }
                mesh.add(new Surface(piece,surface.side,surface.axis,surface.boundary));
            }
        }
        Entry entry=new Entry(volumes,fluids,mesh,tile);cache.entries.put(pos.toImmutable(),entry);return entry;
    }
    static final class Contact {
        final Source source;final FluidStack fluid;
        Contact(Source source,FluidStack fluid){this.source=source;this.fluid=fluid;}
        boolean touches(EnumFacing face){for(Surface surface:source.surfaces)if(surface.boundary==face&&equal(fluid,source.fluids[surface.side]))return true;return false;}
        java.util.List<java.util.List<double[]>> subtract(java.util.List<double[]> polygon,EnumFacing offset){
            // Retain only genuinely dry (or different-liquid) voids. Subtracting just
            // wet voids leaves the solid pane/leaf footprint as a false water sheet.
            java.util.List<java.util.List<double[]>> pieces=new ArrayList<>();
            for(Region region:source.regions)if(!equal(fluid,source.fluids[region.side])){
                List<double[]> piece=polygon;
                for(double[] plane:region.planes){double d=plane[3]+plane[0]*offset.getFrontOffsetX()+plane[1]*offset.getFrontOffsetY()+plane[2]*offset.getFrontOffsetZ();piece=DiagonalMeshClip.plane(piece,plane[0],plane[1],plane[2],d);if(area(piece)<1e-10)break;}
                if(area(piece)>1e-10)pieces.add(piece);
            }
            return pieces;
        }
    }
    static Contact contact(TileEntity tile,FluidStack fluid){
        if(tile==null||tile.getWorld()==null)return null;World world=tile.getWorld();BlockPos pos=tile.getPos();IBlockState state=world.getBlockState(pos);
        if(!WaterloggedParts.supports(state))return null;
        Source source=new Source(world,pos,true);if(!equal(fluid,source.fluids[0])&&!equal(fluid,source.fluids[1]))return null;
        FluidStack first=source.fluids[0],second=source.fluids[1];source.fluids[0]=fluid;source.fluids[1]=fluid;source.splitRegions=true;
        build(source,world,pos);source.fluids[0]=first;source.fluids[1]=second;return new Contact(source,fluid);
    }
    private static boolean equal(FluidStack a,FluidStack b){return a==null?b==null:b!=null&&a.isFluidEqual(b);}
    private static List<double[]> points(Vec3d[] vertices){List<double[]> out=new ArrayList<>();for(Vec3d v:vertices)out.add(new double[]{v.x,v.y,v.z});return out;}
    private static List<double[]> clip(List<double[]> polygon,double[] plane){return DiagonalMeshClip.plane(polygon,plane[0],plane[1],plane[2],plane[3]);}
    private static void subtract(List<double[]> polygon,Region region,EnumFacing offset,List<List<double[]>> out){
        List<double[]> inside=polygon;
        for(double[] plane:region.planes){double d=plane[3]+plane[0]*offset.getFrontOffsetX()+plane[1]*offset.getFrontOffsetY()+plane[2]*offset.getFrontOffsetZ();
            List<double[]> outside=DiagonalMeshClip.plane(inside,-plane[0],-plane[1],-plane[2],-d-1e-8);if(area(outside)>1e-10)out.add(outside);
            inside=DiagonalMeshClip.plane(inside,plane[0],plane[1],plane[2],d);if(area(inside)<1e-10)return;
        }
    }
    /** Exact half-spaces avoid collision-slice seams on diagonal leaves. */
    private static void opening(Source source,World world,BlockPos pos){
        double[] p=OpeningFluids.plane(world,pos);boolean open=OpeningFluids.open(world,pos),joined=!source.splitRegions&&open&&source.fluids[0]!=null&&equal(source.fluids[0],source.fluids[1]);
        for(int side=0;side<(joined?1:2);side++){
            if(source.fluids[side]==null)continue;
            // Keep an exposed door water surface off the coplanar top of its frame.
            // Internal boundaries between two wet door cells retain their full height.
            BlockPos above=pos.up();boolean covered=WaterloggedParts.nativeFluid(world.getBlockState(above))==source.fluids[side].getFluid();
            if(!covered&&WaterloggedParts.supports(world.getBlockState(above)))covered=equal(source.fluids[side],WaterloggedParts.fluid(world,above,0))||equal(source.fluids[side],WaterloggedParts.fluid(world,above,1));
            double height=world.getBlockState(pos).getBlock() instanceof BlockConfigurableSpaceDoor&&!covered?1-.001:1;
            Region region=new Region(side).add(-1,0,0,0).add(1,0,0,1).add(0,-1,0,0).add(0,1,0,height).add(0,0,-1,0).add(0,0,1,1);
            double sign=side==0?1:-1,limit=side==0?p[3]:p[4];if(open)limit=(p[3]+p[4])/2;
            else limit+=side==0?-.001:.001; // Fluid stays off the closed leaf's rendered plane.
            double[] divider={sign*p[0],sign*p[1],sign*p[2],sign*limit};if(!joined)region.planes.add(divider);source.regions.add(region);
            for(EnumFacing face:EnumFacing.values()){List<double[]> polygon=cube(face);for(double[] point:polygon)point[1]*=height;for(double[] plane:region.planes)polygon=clip(polygon,plane);if(area(polygon)>1e-10)source.surfaces.add(new Surface(polygon,side,axis(face),face));}
            if(!joined){int axis=Math.abs(p[0])>Math.abs(p[1])?(Math.abs(p[0])>Math.abs(p[2])?0:2):(Math.abs(p[1])>Math.abs(p[2])?1:2);int a=(axis+1)%3,b=(axis+2)%3;List<double[]> face=new ArrayList<>();for(double[] pair:new double[][]{{0,0},{1,0},{1,1},{0,1}}){double[] point=new double[3];point[a]=pair[0];point[b]=pair[1];point[axis]=(limit-p[a]*point[a]-p[b]*point[b])/p[axis];face.add(point);}for(int i=0;i<6;i++)face=clip(face,region.planes.get(i));if(area(face)>1e-10){Vec3d normal=normal(face);if(normal.x*divider[0]+normal.y*divider[1]+normal.z*divider[2]<0)Collections.reverse(face);source.surfaces.add(new Surface(face,side,axis,null));}}
        }
    }
    private static void build(Source source,World world,BlockPos pos){
        IBlockState state=world.getBlockState(pos);if(OpeningFluids.supports(state)){opening(source,world,pos);return;}if(!WaterloggedParts.panel(state)){
            for(WaterloggedParts.Cell cell:source.volume.cells){AxisAlignedBB b=cell.box;source.regions.add(new Region(cell.side).add(-1,0,0,-b.minX).add(1,0,0,b.maxX).add(0,-1,0,-b.minY).add(0,1,0,b.maxY).add(0,0,-1,-b.minZ).add(0,0,1,b.maxZ));}
            for(WaterloggedParts.Face face:source.volume.faces){List<double[]> polygon=points(face.vertices);source.surfaces.add(new Surface(polygon,face.side,face.axis,boundary(polygon)));}return;
        }
        EnumFacing facing=PanelJoins.flatFacing(state,world,pos);BlockProgrammableWall wall=state.getBlock() instanceof BlockProgrammableWall?(BlockProgrammableWall)state.getBlock():null;
        boolean diagonal=wall!=null&&wall.isDiagonalShape();int mode=diagonal?BlockProgrammableWall.geometry(world,pos):0;boolean inverted=diagonal&&state.getValue(BlockProgrammableWall.INVERTED);
        boolean shallow=diagonal&&mode==2;double lower=diagonal?BlockProgrammableWall.flatEnd(state,world,pos,false):Double.NaN,upper=diagonal?BlockProgrammableWall.flatEnd(state,world,pos,true):Double.NaN;
        double[] cuts=diagonal&&( !Double.isNaN(lower)||!Double.isNaN(upper))?new double[]{0,.25,.75,1}:new double[]{0,1};
        boolean inset=state.getBlock() instanceof BlockProgrammableGlass&&PanelJoins.flatCorner(state,world,pos)==null||wall!=null&&wall.isPortholeShape();
        int fill=diagonal?BlockProgrammableWall.fill(world,pos):0;
        for(int side=0;side<2;side++)if((fill&(1<<side))==0)for(int band=0;band+1<cuts.length;band++){
            double lo=cuts[band],hi=cuts[band+1];double a=diagonal?DiagonalWallGeometry.near(mode,inverted,lo,lower,upper):PanelJoins.flatNear(state,world,pos)/16D;
            double b=diagonal?DiagonalWallGeometry.near(mode,inverted,hi,lower,upper):a;
            double depth=(side==0?(inset?1D/16:0):(inset?3D/16:.25))+(side==0?-.001:.001);
            double slope=(b-a)/(hi-lo),intercept=a-slope*lo+depth+(shallow?DiagonalWallGeometry.band(mode,inverted):0);
            Region region=new Region(side);region.add(-1,0,0,0).add(1,0,0,1).add(0,-1,0,0).add(0,1,0,1).add(0,0,-1,0).add(0,0,1,1);
            region.planes.add(worldPlane(facing,shallow?new double[]{0,0,-1,-lo}:new double[]{0,-1,0,-lo}));region.planes.add(worldPlane(facing,shallow?new double[]{0,0,1,hi}:new double[]{0,1,0,hi}));
            double sign=side==0?1:-1;double[] plane=worldPlane(facing,shallow?new double[]{0,sign,-sign*slope,sign*intercept}:new double[]{0,-sign*slope,sign,sign*intercept});region.planes.add(plane);source.regions.add(region);
            for(EnumFacing face:EnumFacing.values()){List<double[]> polygon=cube(face);for(double[] limit:region.planes)polygon=clip(polygon,limit);if(area(polygon)>1e-10)source.surfaces.add(new Surface(polygon,side,axis(face),face));}
            List<double[]> pane=new ArrayList<>();for(double[] v:new double[][]{{0,lo},{1,lo},{1,hi},{0,hi}}){double depthAt=slope*v[1]+intercept;pane.add(worldPoint(facing,shallow?new double[]{v[0],depthAt,v[1]}:new double[]{v[0],v[1],depthAt}));}
            pane=DiagonalMeshClip.polygon(new double[]{0,0,0,1,1,1},pane.toArray(new double[0][]));
            if(area(pane)>1e-10){Vec3d normal=normal(pane);if(normal.dotProduct(new Vec3d(plane[0],plane[1],plane[2]))<0)Collections.reverse(pane);source.surfaces.add(new Surface(pane,side,shallow?1:axis(facing),null));}
        }
    }
    private static double[] worldPoint(EnumFacing facing,double[] p){switch(facing){case EAST:return new double[]{1-p[2],p[1],p[0]};case SOUTH:return new double[]{1-p[0],p[1],1-p[2]};case WEST:return new double[]{p[2],p[1],1-p[0]};default:return p.clone();}}
    private static double[] worldPlane(EnumFacing facing,double[] p){double[] origin=worldPoint(facing,new double[]{0,0,0}),tip=worldPoint(facing,new double[]{p[0],p[1],p[2]});double x=tip[0]-origin[0],y=tip[1]-origin[1],z=tip[2]-origin[2];return new double[]{x,y,z,p[3]+x*origin[0]+y*origin[1]+z*origin[2]};}
    private static int axis(EnumFacing face){return face.getAxis()==EnumFacing.Axis.X?0:face.getAxis()==EnumFacing.Axis.Y?1:2;}
    private static EnumFacing boundary(List<double[]> polygon){for(EnumFacing face:EnumFacing.values()){int axis=axis(face);double edge=face.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE?1:0;boolean on=true;for(double[] p:polygon)on&=Math.abs(p[axis]-edge)<1e-8;if(on)return face;}return null;}
    private static Vec3d normal(List<double[]> polygon){Vec3d result=Vec3d.ZERO;if(polygon.size()<3)return result;for(int i=0;i<polygon.size();i++){double[] a=polygon.get(i),b=polygon.get((i+1)%polygon.size());result=result.add(new Vec3d(a[0],a[1],a[2]).crossProduct(new Vec3d(b[0],b[1],b[2])));}return result;}
    private static double area(List<double[]> polygon){return normal(polygon).lengthVector()/2;}
    private static List<double[]> cube(EnumFacing face){double[][] p;switch(face){case DOWN:p=new double[][]{{0,0,0},{1,0,0},{1,0,1},{0,0,1}};break;case UP:p=new double[][]{{0,1,0},{0,1,1},{1,1,1},{1,1,0}};break;case WEST:p=new double[][]{{0,1,0},{0,0,0},{0,0,1},{0,1,1}};break;case EAST:p=new double[][]{{1,1,0},{1,1,1},{1,0,1},{1,0,0}};break;case NORTH:p=new double[][]{{0,1,0},{1,1,0},{1,0,0},{0,0,0}};break;default:p=new double[][]{{0,1,1},{0,0,1},{1,0,1},{1,1,1}};}return new ArrayList<>(Arrays.asList(p));}
}

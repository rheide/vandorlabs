package com.vandorlabs.client;

import java.util.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;

/** Collect visible glazing in pass zero; draw its translucent parts back-to-front in pass one. */
final class GlazingRenderOrder {
    private static final boolean LEGACY_BENCHMARK=Boolean.getBoolean("vandorlabs.benchmarkLegacyGlazing");
    private static final int LIMIT=16384;
    static String diagnostics="";static int lateDraws;static long sortNanos;
    static boolean batchPanes=true;
    private static final org.lwjgl.util.vector.Matrix4f inverse=new org.lwjgl.util.vector.Matrix4f();
    private static World world;private static boolean flushing,flushed;
    private static final Map<TileEntity,Draw> visible=new IdentityHashMap<>();
    private static final class Draw {
        TileEntity tile;double x,y,z;float partial,alpha;int stage,axis;double depth,plane;
        final GlassPanePreparation glass=new GlassPanePreparation();
        void set(TileEntity tile,double x,double y,double z,float partial,int stage,float alpha){this.tile=tile;this.x=x;this.y=y;this.z=z;this.partial=partial;this.stage=stage;this.alpha=alpha;}
    }
    private static final List<Draw> pool=new ArrayList<>(),ordered=new ArrayList<>();
    private static double marginX,marginY;
    private static final java.nio.FloatBuffer projection=org.lwjgl.BufferUtils.createFloatBuffer(16);
    private static final java.nio.IntBuffer viewport=org.lwjgl.BufferUtils.createIntBuffer(16);
    private static final java.nio.FloatBuffer matrix=org.lwjgl.BufferUtils.createFloatBuffer(16);
    private static void clear(){for(Draw draw:ordered){draw.tile=null;draw.glass.clear();}ordered.clear();visible.clear();}
    static void begin(World view){end();world=view;lateDraws=0;GlassPaneBatch.panes=GlassPaneBatch.draws=0;}
    static void end(){clear();world=null;flushing=false;flushed=false;}
    static boolean handle(TileEntity tile,double x,double y,double z,float partial,int stage,float alpha){
        if(flushing||world==null||tile.getWorld()!=world||stage>=0||VehicleRenderPass.shadow())return false;
        int pass=MinecraftForgeClient.getRenderPass();
        if(pass==0){
            // A second eye/render starts a fresh collection within the same render tick.
            if(flushed){clear();flushed=false;}
            if(!visible.containsKey(tile)&&visible.size()<LIMIT&&tile.shouldRenderInPass(1)){
                int index=ordered.size();if(index==pool.size())pool.add(new Draw());Draw draw=pool.get(index);draw.set(tile,x,y,z,partial,stage,alpha);ordered.add(draw);visible.put(tile,draw);
            }
            return false;
        }
        if(pass!=1)return false;
        if(!visible.containsKey(tile)){lateDraws++;return false;} // No matching opaque pass: preserve ordinary direct rendering.
        if(flushed)return true;
        TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;
        matrix.clear();org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX,matrix);
        projection.clear();org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX,projection);
        viewport.clear();org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_VIEWPORT,viewport);
        // Include rasterized boundary pixels when mathematical bounds only touch.
        marginX=4D/(Math.abs(projection.get(0))*Math.max(1,viewport.get(2)));
        marginY=4D/(Math.abs(projection.get(5))*Math.max(1,viewport.get(3)));

        // Dispatcher.entityY is the player's feet, not the rendered camera. Invert
        // the actual view matrix, including eye height, bobbing and third-person offsets.
        long started=System.nanoTime();
        matrix.position(0);inverse.load(matrix);org.lwjgl.util.vector.Matrix4f.invert(inverse,inverse);
        Draw first=ordered.get(0);
        double cx=first.tile.getPos().getX()-first.x+inverse.m30,
            cy=first.tile.getPos().getY()-first.y+inverse.m31,
            cz=first.tile.getPos().getZ()-first.z+inverse.m32;
        for(Draw draw:ordered)draw.depth=depth(draw,cx,cy,cz,matrix);
        ordered.sort((a,b)->{int distance=Double.compare(a.depth,b.depth);return distance!=0?distance:a.tile.getPos().compareTo(b.tile.getPos());});
        finitePlanes(cx,cy,cz);
        sortNanos=System.nanoTime()-started;
        flushing=true;
        try(TurntableRenderState saved=new TurntableRenderState();GlassPaneBatch batch=new GlassPaneBatch()){
            for(Draw draw:ordered)if(world.getTileEntity(draw.tile.getPos())==draw.tile&&draw.tile.shouldRenderInPass(1)){
                if(batchPanes&&batch.append(draw.tile,draw.x,draw.y,draw.z,draw.glass))continue;
                batch.flush();
                dispatcher.render(draw.tile,draw.x,draw.y,draw.z,draw.partial,draw.stage,draw.alpha);
            }
        }finally{flushing=false;flushed=true;}
        return true;
    }
    private static double depth(Draw draw,double cx,double cy,double cz,java.nio.FloatBuffer matrix){
        TileEntity tile=draw.tile;
        draw.glass.clear();long mutation=GlassSurfaceGroups.mutation();
        net.minecraft.block.state.IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        int axis=-1;double plane=0,offset=0;
        if(state.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableGlass&&tile instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass&&GlassSurfaceGroups.flatCorner((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile,state)==null){
            draw.glass.capture((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile,state,mutation);
            boolean rotated=state.getValue(com.vandorlabs.blocks.BlockGlassWall.ROTATED);axis=rotated?0:2;offset=com.vandorlabs.blocks.PanelDepth.offset(state.getValue(com.vandorlabs.blocks.BlockGlassWall.DEPTH))/16D;
            plane=rotated?tile.getPos().getX()+.5-offset:tile.getPos().getZ()+.5+offset;
        }else if(state.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableWall){
            com.vandorlabs.blocks.BlockProgrammableWall wall=(com.vandorlabs.blocks.BlockProgrammableWall)state.getBlock();
            if(wall.isPortholeShape()&&!wall.isDiagonalShape()&&com.vandorlabs.blocks.PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos())==null){
                net.minecraft.util.EnumFacing facing=state.getValue(com.vandorlabs.blocks.BlockProgrammableWall.FACING);axis=facing.getAxis()==net.minecraft.util.EnumFacing.Axis.X?0:2;
                offset=com.vandorlabs.blocks.PanelDepth.offset(state.getValue(com.vandorlabs.blocks.BlockProgrammableWall.DEPTH))/16D;
                plane=(axis==0?tile.getPos().getX():tile.getPos().getZ())+.5-(axis==0?facing.getFrontOffsetX():facing.getFrontOffsetZ())*offset;
            }
        }
        draw.axis=axis;draw.plane=plane;
        // Parallel planes have a camera-side order even when the centre viewing ray
        // misses them. Dividing by its near-zero normal flips priority at grazing angles.
        if(axis>=0)return -Math.abs(plane-(axis==0?cx:cz));
        return (tile.getPos().getX()+.5-cx)*matrix.get(2)+(tile.getPos().getY()+.5-cy)*matrix.get(6)+(tile.getPos().getZ()+.5-cz)*matrix.get(10);
    }
    private static final class Plane {
        final int axis;final double coordinate;final Draw draw;
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        double left,right,bottom,top;
        Plane(Draw d){draw=d;axis=d.axis;coordinate=d.plane;}
        void add(Draw d,double cx,double cy,double cz){
            net.minecraft.util.math.AxisAlignedBB b=new net.minecraft.util.math.AxisAlignedBB(d.tile.getPos());
            if(d.tile instanceof com.vandorlabs.tiles.TileEntityCanopy){java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes=((com.vandorlabs.tiles.TileEntityCanopy)d.tile).boxes();if(!boxes.isEmpty()){b=boxes.get(0);for(int i=1;i<boxes.size();i++)b=b.union(boxes.get(i));}}
            if(axis==0)b=new net.minecraft.util.math.AxisAlignedBB(coordinate-.0625,b.minY,b.minZ,coordinate+.0625,b.maxY,b.maxZ);
            if(axis==2)b=new net.minecraft.util.math.AxisAlignedBB(b.minX,b.minY,coordinate-.0625,b.maxX,b.maxY,coordinate+.0625);
            minX=b.minX;minZ=b.minZ;maxX=b.maxX;maxZ=b.maxZ;
            left=bottom=Double.POSITIVE_INFINITY;right=top=Double.NEGATIVE_INFINITY;
            int inFront=0;
            for(int v=0;v<8;v++){
                double x=((v&1)==0?b.minX:b.maxX)-cx,y=((v&2)==0?b.minY:b.maxY)-cy,z=((v&4)==0?b.minZ:b.maxZ)-cz;
                double depth=-(x*matrix.get(2)+y*matrix.get(6)+z*matrix.get(10));
                if(depth<=.001)continue;
                inFront++;
                double px=(x*matrix.get(0)+y*matrix.get(4)+z*matrix.get(8))/depth,py=(x*matrix.get(1)+y*matrix.get(5)+z*matrix.get(9))/depth;
                left=Math.min(left,px);right=Math.max(right,px);bottom=Math.min(bottom,py);top=Math.max(top,py);
            }
            if(inFront>0&&inFront<8){left=bottom=Double.NEGATIVE_INFINITY;right=top=Double.POSITIVE_INFINITY;}
            else if(inFront==8){left-=marginX;right+=marginX;bottom-=marginY;top+=marginY;}
        }
    }
    /** Compare individual finite surfaces only where their projections overlap.
     * Keep ambiguous cycles local, preserving all constraints between components. */
    private static void finitePlanes(double cx,double cy,double cz){
        // The common case of a wall or several parallel walls is already ordered by
        // plane depth. Avoid graph construction and pair scans for those scenes.
        int axis=ordered.get(0).axis;boolean parallel=axis>=0;
        for(Draw d:ordered)if(d.axis!=axis){parallel=false;break;}
        if(parallel){diagnostics="draws="+ordered.size()+" parallel=true";return;}
        List<Plane> planes=new ArrayList<>();
        for(Draw d:ordered){Plane p=new Plane(d);p.add(d,cx,cy,cz);planes.add(p);}
        diagnostics="draws="+ordered.size()+" planes="+planes.size();
        int n=planes.size();BitSet[] edge=new BitSet[n];for(int i=0;i<n;i++)edge[i]=new BitSet();
        Integer[] sweep=new Integer[n];for(int i=0;i<n;i++)sweep[i]=i;
        // Tall joined walls overlap heavily along X; choose the sweep that visits
        // fewer interval pairs. This changes only broad-phase work, never edges.
        boolean vertical=!LEGACY_BENCHMARK
                && projectedPairs(planes,true)<projectedPairs(planes,false);
        Arrays.sort(sweep,(a,b)->Double.compare(vertical?planes.get(a).bottom:planes.get(a).left,vertical?planes.get(b).bottom:planes.get(b).left));
        long tested=0;
        for(int left=0;left<n;left++)for(int right=left+1;right<n;right++){
            int i=sweep[left],j=sweep[right];Plane one=planes.get(i),two=planes.get(j);
            if(vertical?two.bottom>=one.top:two.left>=one.right)break;
            tested++;
            if(one.right<=two.left||two.right<=one.left||one.top<=two.bottom||two.top<=one.bottom)continue;
            int a=relation(one,two,cx,cz),b=-relation(two,one,cx,cz);
            if(a!=0&&b!=0&&a!=b)continue;int direction=a!=0?a:b;if(direction==0)continue;
            int from=direction<0?i:j,to=direction<0?j:i;edge[from].set(to);
        }
        com.vandorlabs.render.OcclusionOrder.Result result=com.vandorlabs.render.OcclusionOrder.sort(edge);
        ordered.clear();for(int selected:result.order)ordered.add(planes.get(selected).draw);
        int largest=0;int[] sizes=new int[n];for(int c:result.component)largest=Math.max(largest,++sizes[c]);
        diagnostics+=" sweep="+(vertical?"Y":"X")+" pairs="+tested+" cyclic-components="+result.cyclicComponents+" largest="+largest;

    }
    private static long projectedPairs(List<Plane> planes,boolean vertical){
        int n=planes.size(),used=0;double[] starts=new double[n],ends=new double[n];
        for(Plane p:planes){double lo=vertical?p.bottom:p.left,hi=vertical?p.top:p.right;if(lo<hi){starts[used]=lo;ends[used++]=hi;}}
        Arrays.sort(starts,0,used);Arrays.sort(ends,0,used);
        long pairs=0;int ended=0;
        for(int i=0;i<used;i++){while(ended<used&&ends[ended]<=starts[i])ended++;pairs+=i-ended;}
        return pairs;
    }
    /** A behind B's plane sorts first; only wholly separated finite bounds establish an edge. */
    private static int relation(Plane a,Plane b,double cx,double cz){
        if(b.axis<0)return 0;double camera=b.axis==0?cx:cz,lo=b.axis==0?a.minX:a.minZ,hi=b.axis==0?a.maxX:a.maxZ;
        if(camera>b.coordinate+1e-5){if(hi<=b.coordinate-1e-5)return -1;if(lo>=b.coordinate+1e-5)return 1;}
        else if(camera<b.coordinate-1e-5){if(lo>=b.coordinate+1e-5)return -1;if(hi<=b.coordinate-1e-5)return 1;}
        return 0;
    }
    private GlazingRenderOrder(){}
}

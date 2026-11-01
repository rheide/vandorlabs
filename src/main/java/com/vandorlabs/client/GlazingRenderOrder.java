package com.vandorlabs.client;

import java.util.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;

/** Collect visible glazing in pass zero; draw its translucent parts back-to-front in pass one. */
final class GlazingRenderOrder {
    private static final int LIMIT=16384;
    private static World world;private static boolean flushing,flushed;
    private static final Map<TileEntity,Draw> visible=new IdentityHashMap<>();
    private static final class Draw {
        TileEntity tile;double x,y,z;float partial,alpha;int stage,axis;double depth,plane;
        void set(TileEntity tile,double x,double y,double z,float partial,int stage,float alpha){this.tile=tile;this.x=x;this.y=y;this.z=z;this.partial=partial;this.stage=stage;this.alpha=alpha;}
    }
    private static final List<Draw> pool=new ArrayList<>(),ordered=new ArrayList<>();
    private static final java.nio.FloatBuffer matrix=org.lwjgl.BufferUtils.createFloatBuffer(16);
    private static void clear(){for(Draw draw:ordered)draw.tile=null;ordered.clear();visible.clear();}
    static void begin(World view){end();world=view;}
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
        if(!visible.containsKey(tile))return false; // No matching opaque pass: preserve ordinary direct rendering.
        if(flushed)return true;
        TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;
        matrix.clear();org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX,matrix);
        for(Draw draw:ordered)draw.depth=depth(draw,dispatcher.entityX,dispatcher.entityY,dispatcher.entityZ,matrix);
        ordered.sort((a,b)->{int distance=Double.compare(a.depth,b.depth);return distance!=0?distance:a.tile.getPos().compareTo(b.tile.getPos());});
        finitePlanes(dispatcher.entityX,dispatcher.entityZ);
        flushing=true;
        try(TurntableRenderState saved=new TurntableRenderState()){
            for(Draw draw:ordered)if(world.getTileEntity(draw.tile.getPos())==draw.tile&&draw.tile.shouldRenderInPass(1))
                dispatcher.render(draw.tile,draw.x,draw.y,draw.z,draw.partial,draw.stage,draw.alpha);
        }finally{flushing=false;flushed=true;}
        return true;
    }
    private static double depth(Draw draw,double cx,double cy,double cz,java.nio.FloatBuffer matrix){
        TileEntity tile=draw.tile;
        net.minecraft.block.state.IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        int axis=-1;double plane=0,offset=0;
        if(state.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableGlass&&com.vandorlabs.blocks.PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos())==null){
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
        // Sort joined parallel planes as a whole, rather than interleaving their tiles at oblique angles.
        if(axis>=0){double normal=matrix.get(axis==0?2:10);if(Math.abs(normal)>1e-6)return (plane-(axis==0?cx:cz))/normal;}
        return (tile.getPos().getX()+.5-cx)*matrix.get(2)+(tile.getPos().getY()+.5-cy)*matrix.get(6)+(tile.getPos().getZ()+.5-cz)*matrix.get(10);
    }
    private static final class Plane {
        final int axis;final double coordinate;final List<Draw> draws=new ArrayList<>();
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        Plane(Draw d){axis=d.axis;coordinate=d.plane;}
        void add(Draw d){draws.add(d);net.minecraft.util.math.AxisAlignedBB b=new net.minecraft.util.math.AxisAlignedBB(d.tile.getPos());if(d.tile instanceof com.vandorlabs.tiles.TileEntityCanopy){java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes=((com.vandorlabs.tiles.TileEntityCanopy)d.tile).boxes();if(!boxes.isEmpty()){b=boxes.get(0);for(int i=1;i<boxes.size();i++)b=b.union(boxes.get(i));}}minX=Math.min(minX,b.minX);minZ=Math.min(minZ,b.minZ);maxX=Math.max(maxX,b.maxX);maxZ=Math.max(maxZ,b.maxZ);}
    }
    /** Infinite-plane ray depths are ambiguous for disjoint perpendicular panes. Respect
     * their finite extents first, then retain view depth for intersecting/ambiguous sets. */
    private static void finitePlanes(double cx,double cz){
        List<Plane> planes=new ArrayList<>();Map<String,Plane> flat=new HashMap<>();
        Plane last=null;
        for(Draw d:ordered){Plane p=d.axis>=0&&last!=null&&last.axis==d.axis&&last.coordinate==d.plane?last:d.axis<0?null:flat.get(d.axis+":"+d.plane);if(p==null){p=new Plane(d);planes.add(p);if(planes.size()>128)return;if(d.axis>=0)flat.put(d.axis+":"+d.plane,p);}p.add(d);last=p;}
        int n=planes.size();boolean[][] edge=new boolean[n][n];int[] incoming=new int[n];boolean[] done=new boolean[n];
        for(int i=0;i<n;i++)for(int j=i+1;j<n;j++){
            int a=relation(planes.get(i),planes.get(j),cx,cz),b=-relation(planes.get(j),planes.get(i),cx,cz);
            if(a!=0&&b!=0&&a!=b)continue;int direction=a!=0?a:b;if(direction==0)continue;
            int from=direction<0?i:j,to=direction<0?j:i;edge[from][to]=true;incoming[to]++;
        }
        ordered.clear();
        for(int step=0;step<n;step++){
            int selected=-1;for(int i=0;i<n;i++)if(!done[i]&&incoming[i]==0){selected=i;break;}
            // Intersecting polygons can form cycles; choose the existing depth order deterministically.
            if(selected<0)for(int i=0;i<n;i++)if(!done[i]){selected=i;break;}
            done[selected]=true;ordered.addAll(planes.get(selected).draws);for(int j=0;j<n;j++)if(edge[selected][j])incoming[j]--;
        }
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

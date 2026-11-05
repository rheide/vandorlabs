package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.NativeVoxelShapes;
import com.vandorlabs.render.PanelCornerGeometry;
import com.vandorlabs.render.PanelDepthPolicy;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Immutable current four-pixel wall geometry. Neighbour discovery is owned by the tile publication path. */
public final class NativeFlatWallGeometry {
    public record Join(Double frontArm,Double backArm,Boolean frontRight,Boolean backRight,boolean straightLeft,boolean straightRight){
        public PanelCornerGeometry.FlatCorner corner(){return new PanelCornerGeometry.FlatCorner(frontArm,backArm,frontRight,backRight,straightLeft,straightRight);}
    }
    public record Geometry(List<AABB> boxes,AABB bounds,Direction facing,int depth,Join join,NativePanelLayout layout,boolean fullDepth) implements NativeWallGeometry {
        public Geometry(List<AABB> boxes,AABB bounds,Direction facing,int depth,Join join,NativePanelLayout layout){this(boxes,bounds,facing,depth,join,layout,false);}
        public Geometry(List<AABB> boxes,AABB bounds,Direction facing,int depth){this(boxes,bounds,facing,depth,null,NativePanelLayout.UNIT);}
        public Geometry(List<AABB> boxes,AABB bounds,Direction facing,int depth,Join join){this(boxes,bounds,facing,depth,join,NativePanelLayout.UNIT);}
        public Geometry {boxes=List.copyOf(boxes);java.util.Objects.requireNonNull(layout);}
        @Override public boolean sameFluidGeometry(NativeWallGeometry other){return other instanceof Geometry flat&&facing==flat.facing&&depth==flat.depth&&fullDepth==flat.fullDepth&&boxes.equals(flat.boxes);}
        public Geometry withLayout(NativePanelLayout next){return layout.equals(next)?this:new Geometry(boxes,bounds,facing,depth,join,next,fullDepth);}
        @Override public com.vandorlabs.neoforge.fluid.NativeFluidRegions fluidRegions(boolean porthole){return fullDepth?com.vandorlabs.neoforge.fluid.NativeFluidRegions.empty():com.vandorlabs.neoforge.fluid.NativeFluidRegions.flat(facing,depth,porthole);}
        public int side(double x,double y,double z){double normal=facing==Direction.EAST?1-x:facing==Direction.SOUTH?1-z:facing==Direction.WEST?x:z;return normal<(fullDepth?.5:PanelDepthPolicy.start(depth)/16D+.125)?0:1;}
    }
    private static final int BUDGET=8*1024*1024;
    private static final java.util.concurrent.ConcurrentMap<List<AABB>,VoxelShape> SHAPES=new java.util.concurrent.ConcurrentHashMap<>();
    private static final LinkedHashMap<List<AABB>,Integer> COSTS=new LinkedHashMap<>();
    private static int retainedBytes;
    private static final AABB UNIT=new AABB(0,0,0,1,1,1);
    private static final Geometry[] FULL=new Geometry[18];
    private static final Geometry[] STRAIGHT=new Geometry[18];
    static {for(Direction facing:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})for(int depth=0;depth<3;depth++){STRAIGHT[facing.get3DDataValue()*3+depth]=build(facing,depth,null);FULL[facing.get3DDataValue()*3+depth]=new Geometry(List.of(UNIT),UNIT,facing,depth,null,NativePanelLayout.UNIT,true);}}
    public static Geometry straight(Direction facing,int depth){
        if(facing.getAxis()==Direction.Axis.Y||depth<0||depth>2)throw new IllegalArgumentException("Invalid flat wall pose");
        return STRAIGHT[facing.get3DDataValue()*3+depth];
    }
    public static Geometry full(Direction facing,int depth){straight(facing,depth);return FULL[facing.get3DDataValue()*3+depth];}
    public static Geometry build(Direction facing,int depth,PanelCornerGeometry.FlatCorner corner){
        if(facing.getAxis()==Direction.Axis.Y||depth<0||depth>2)throw new IllegalArgumentException("Invalid flat wall pose");
        double near=PanelDepthPolicy.start(depth);List<AABB> boxes=new ArrayList<>(3);
        add(boxes,facing,corner==null?0:corner.left(),corner==null?16:corner.right(),near,near+4);
        if(corner!=null&&corner.frontArm!=null)add(boxes,facing,corner.frontArm,corner.frontArm+4,0,near);
        if(corner!=null&&corner.backArm!=null)add(boxes,facing,corner.backArm,corner.backArm+4,near+4,16);
        AABB bounds=boxes.getFirst();for(AABB box:boxes)bounds=bounds.minmax(box);
        return new Geometry(boxes,bounds,facing,depth,corner==null?null:new Join(corner.frontArm,corner.backArm,corner.frontRight,corner.backRight,corner.straightLeft,corner.straightRight));
    }
    private static void add(List<AABB> boxes,Direction facing,double x0,double x1,double z0,double z1){
        if(x1-x0<1e-7||z1-z0<1e-7)return;
        AABB b=new AABB(x0/16,0,z0/16,x1/16,1,z1/16);boxes.add(rotate(b,facing));
    }
    public static AABB rotate(AABB b,Direction facing){return switch(facing){
        case EAST->new AABB(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);
        case SOUTH->new AABB(1-b.maxX,b.minY,1-b.maxZ,1-b.minX,b.maxY,1-b.minZ);
        case WEST->new AABB(b.minZ,b.minY,1-b.maxX,b.maxZ,b.maxY,1-b.minX);
        default->b;
    };}
    public static VoxelShape collision(NativeWallGeometry geometry){
        VoxelShape found=SHAPES.get(geometry.boxes());if(found!=null)return found;
        synchronized(COSTS){
            found=SHAPES.get(geometry.boxes());if(found!=null)return found;
            VoxelShape built=NativeVoxelShapes.union(geometry.boxes(),BUDGET-256);
            int cost=NativeVoxelShapes.retainedCost(built,geometry.boxes().size())+256;
            while(retainedBytes+cost>BUDGET&&!COSTS.isEmpty()){
                var oldest=COSTS.entrySet().iterator().next();retainedBytes-=oldest.getValue();SHAPES.remove(oldest.getKey());COSTS.remove(oldest.getKey());
            }
            COSTS.put(geometry.boxes(),cost);SHAPES.put(geometry.boxes(),built);retainedBytes+=cost;return built;
        }
    }
    private NativeFlatWallGeometry(){}
}

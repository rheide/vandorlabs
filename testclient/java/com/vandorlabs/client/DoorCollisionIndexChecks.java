package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Exact old-loop comparisons; the index only rejects candidates, never changes a box. */
public final class DoorCollisionIndexChecks {
    private static volatile Object consumed;
    private static void need(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void original(List<AxisAlignedBB> boxes, AxisAlignedBB query, List<AxisAlignedBB> out) {
        for (AxisAlignedBB box : boxes) if (box.intersects(query) && !out.contains(box)) out.add(box);
    }
    private static RayTraceResult original(List<AxisAlignedBB> boxes, Vec3d start, Vec3d end) {
        RayTraceResult nearest = null; double distance = Double.MAX_VALUE;
        for (AxisAlignedBB box : boxes) {
            RayTraceResult hit = box.calculateIntercept(start, end);
            if (hit != null && start.squareDistanceTo(hit.hitVec) < distance) {
                distance = start.squareDistanceTo(hit.hitVec); nearest = hit;
            }
        }
        return nearest;
    }
    private static void same(RayTraceResult expected, RayTraceResult actual) {
        need((expected == null) == (actual == null), "ray hit presence changed");
        if (expected == null) return;
        need(expected.sideHit == actual.sideHit, "ray side / ordered tie changed");
        need(Double.doubleToRawLongBits(expected.hitVec.x) == Double.doubleToRawLongBits(actual.hitVec.x)
                && Double.doubleToRawLongBits(expected.hitVec.y) == Double.doubleToRawLongBits(actual.hitVec.y)
                && Double.doubleToRawLongBits(expected.hitVec.z) == Double.doubleToRawLongBits(actual.hitVec.z),
                "ray point changed");
    }
    private static void configure(TileEntityLargeProgrammableDoor tile, boolean sliding, int direction,
            boolean framed, boolean hinges, int depth, int panels, int security) {
        tile.configure(23, 1, framed, direction, false, sliding, hinges, 0, panels != 0);
        tile.setPlacementDepth(depth); tile.setPanelMode(panels); tile.configureSecurity(security, 127);
    }
    private static Vec3d point(BlockPos p, double x, double y, double z) {
        return new Vec3d(p.getX() + x, p.getY() + y, p.getZ() + z);
    }
    private static void matrix() throws Exception {
        TileEntityLargeProgrammableDoor tile = new TileEntityLargeProgrammableDoor();
        List<AxisAlignedBB> expected = new ArrayList<>(), actual = new ArrayList<>();
        Field tree = DoorCollisionIndex.class.getDeclaredField("bounds"); tree.setAccessible(true);
        Field ranges = DoorCollisionIndex.class.getDeclaredField("ranges"); ranges.setAccessible(true);
        int cases = 0, queries = 0, rays = 0, maximumBoxes = 0, maximumNodes = 0, maximumPayload = 0;
        for (BlockPos pos : new BlockPos[]{new BlockPos(17,80,-20), new BlockPos(-29999983,240,29999983)})
        for (EnumFacing facing : EnumFacing.HORIZONTALS) for (boolean sliding : new boolean[]{false,true})
        for (int direction = 0; direction < (sliding ? 4 : 1); direction++) for (boolean framed : new boolean[]{false,true})
        for (boolean hinges : new boolean[]{false,true}) for (int depth = 0; depth < 3; depth++)
        for (int panels = 0; panels < 3; panels++) for (int security = 0; security < 2; security++)
        for (boolean open : new boolean[]{false,true}) {
            tile.setPos(pos); configure(tile, sliding, direction, framed, hinges, depth, panels, security);
            List<AxisAlignedBB> boxes = tile.collisionGeometry(facing, open);
            DoorCollisionIndex index = tile.collisionIndex(facing, open);
            need(index == tile.collisionIndex(facing, open), "warm index not retained");
            maximumBoxes = Math.max(maximumBoxes, boxes.size());
            double[] bounds = (double[]) tree.get(index); int[] nodes = (int[]) ranges.get(index);
            if (bounds != null) {
                maximumNodes = Math.max(maximumNodes, nodes.length / 3);
                maximumPayload = Math.max(maximumPayload, bounds.length * 8 + nodes.length * 4);
            }
            AxisAlignedBB first = boxes.get(0);
            AxisAlignedBB[] tests = {
                new AxisAlignedBB(pos.getX()-8,pos.getY()-8,pos.getZ()-8,pos.getX()+9,pos.getY()+9,pos.getZ()+9),
                new AxisAlignedBB(pos.getX()+.2,pos.getY()+.25,pos.getZ()+.2,pos.getX()+.8,pos.getY()+2.05,pos.getZ()+.8),
                new AxisAlignedBB(pos.getX()+50,pos.getY()+50,pos.getZ()+50,pos.getX()+51,pos.getY()+51,pos.getZ()+51),
                first, new AxisAlignedBB(first.maxX,first.minY,first.minZ,first.maxX,first.maxY,first.maxZ),
                new AxisAlignedBB(Double.NaN,0,0,1,1,1),
                new AxisAlignedBB(Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,
                        Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY)
            };
            for (AxisAlignedBB query : tests) {
                expected.clear(); actual.clear(); expected.add(first); actual.add(first);
                original(boxes, query, expected); index.addIntersecting(query, actual);
                need(expected.equals(actual), "collision order/content changed in case " + cases);
                queries++;
            }
            for (int axis = 0; axis < 3; axis++) for (double a : new double[]{-.1,.5,1.5,3.1}) {
                Vec3d start = point(pos, axis == 0 ? -5 : a, axis == 1 ? -5 : .5, axis == 2 ? -5 : a);
                Vec3d end = point(pos, axis == 0 ? 7 : a, axis == 1 ? 7 : .5, axis == 2 ? 7 : a);
                same(original(boxes,start,end),index.trace(start,end));
                same(original(boxes,end,start),index.trace(end,start)); rays+=2;
            }
            Vec3d[] edges = {
                new Vec3d(first.minX,first.minY,first.minZ), new Vec3d(first.maxX,first.maxY,first.maxZ),
                new Vec3d(Math.nextDown(first.minX),Math.nextUp(first.maxY),first.minZ),
                new Vec3d(Math.nextUp(first.maxX),Math.nextDown(first.minY),first.maxZ),
                new Vec3d((first.minX+first.maxX)/2,(first.minY+first.maxY)/2,(first.minZ+first.maxZ)/2),
                new Vec3d(Double.NaN,0,0), new Vec3d(Double.POSITIVE_INFINITY,0,0)
            };
            for (int i = 0; i < edges.length; i++) {
                Vec3d start=edges[i],end=edges[(i+1)%edges.length];
                same(original(boxes,start,end),index.trace(start,end)); rays++;
            }
            cases++;
        }
        DoorCollisionIndex previous = tile.collisionIndex(EnumFacing.SOUTH,true);
        NBTTagCompound tag = tile.writeToNBT(new NBTTagCompound());
        tag.setBoolean("SpaceFramed",!tile.isFramed());tile.readFromNBT(tag);
        need(previous!=tile.collisionIndex(EnumFacing.SOUTH,true),"NBT geometry edit retained stale index");
        previous=tile.collisionIndex(EnumFacing.SOUTH,true);tile.setPos(tile.getPos().add(7,3,-9));
        need(previous!=tile.collisionIndex(EnumFacing.SOUTH,true),"relocation retained stale index");
        System.out.println("door-index exact PASS configurations="+cases+" collisionQueries="+queries+" rays="+rays
                +" maximumBoxes="+maximumBoxes+" maximumNodes="+maximumNodes+" maximumPrimitivePayloadBytes="+maximumPayload
                +" ordered ties, duplicate suppression, exact touches, far coordinates, nonfinite rays, NBT and relocation");
    }
    private static void extensions() throws Exception {
        final List<AxisAlignedBB> supplied = new ArrayList<>();
        BlockDetailedDoor model = new BlockDetailedDoor("door_index_model",BlockVandorDoor.DoorMotion.SLIDING,
                true,true,false,1,15,7,90,-90,-16,16);
        for (boolean blockOverride : new boolean[]{false,true}) {
            BlockLargeProgrammableDoor block = blockOverride ? new BlockLargeProgrammableDoor("door_index_block_extension",model) {
                @Override public List<AxisAlignedBB> geometry(net.minecraft.world.IBlockAccess world,BlockPos pos) {return supplied;}
            } : new BlockLargeProgrammableDoor("door_index_native",model);
            TileEntityLargeProgrammableDoor tile = blockOverride ? new TileEntityLargeProgrammableDoor() : new TileEntityLargeProgrammableDoor() {
                @Override public List<AxisAlignedBB> collisionGeometry(EnumFacing facing,boolean open) {return supplied;}
            };
            NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(true);
            BlockPos pos=new BlockPos(8,64,8); IBlockState state=block.getDefaultState().withProperty(BlockVandorDoor.FACING,EnumFacing.SOUTH);
            tile.setWorld(world);tile.setPos(pos);tile.assign(pos);world.states.put(pos,state);world.tiles.put(pos,tile);
            MovedDoorInteractions.INDEX.add(tile);
            try {
                for(int changed=0;changed<2;changed++) {
                    supplied.clear(); supplied.add(new AxisAlignedBB(8.1,64.1,8.1+changed*.1,8.9,65.9,8.4+changed*.1));
                    Vec3d start=new Vec3d(8.5,65,7),end=new Vec3d(8.5,65,10);
                    RayTraceResult expected=original(supplied,start,end);
                    same(expected,block.collisionRayTrace(state,world,pos,start,end));
                    same(expected,MovedDoorInteractions.trace(world,start,end));
                    List<AxisAlignedBB> actual=new ArrayList<>(); AxisAlignedBB query=new AxisAlignedBB(8,64,8,9,66,9);
                    MovedDoorInteractions.INSTANCE.collisions(new net.minecraftforge.event.world.GetCollisionBoxesEvent(world,null,query,actual));
                    need(actual.equals(supplied),"dynamic extension collision bypassed");
                }
            } finally {MovedDoorInteractions.INDEX.remove(tile);}
        }
        // A non-native AABB can override intersection; the broad phase must not hide it.
        List<AxisAlignedBB> custom=new ArrayList<>();for(int i=0;i<64;i++)custom.add(new AxisAlignedBB(i,0,0,i+1,1,1){
            @Override public boolean intersects(AxisAlignedBB other){return true;}
        });
        List<AxisAlignedBB> result=new ArrayList<>();new DoorCollisionIndex(custom).addIntersecting(new AxisAlignedBB(100,100,100,101,101,101),result);
        need(result.equals(custom),"AABB extension override was spatially rejected");
        List<AxisAlignedBB> malformed=new ArrayList<>();
        for(int i=0;i<40;i++)malformed.add(new AxisAlignedBB(i,0,0,i+1,1,1));
        malformed.add(new AxisAlignedBB(Double.NaN,0,0,1,1,1));
        List<AxisAlignedBB> expected=new ArrayList<>();result.clear();AxisAlignedBB all=new AxisAlignedBB(-1,-1,-1,100,2,2);
        original(malformed,all,expected);new DoorCollisionIndex(malformed).addIntersecting(all,result);
        need(expected.equals(result),"nonfinite source hid finite geometry");
        System.out.println("door-index extensions PASS dynamic block/tile/AABB behavior");
    }
    private static void benchmark() {
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!meter.isThreadAllocatedMemorySupported()||!meter.isCurrentThreadCpuTimeSupported())return;
        meter.setThreadAllocatedMemoryEnabled(true);meter.setThreadCpuTimeEnabled(true);long thread=Thread.currentThread().getId();
        TileEntityLargeProgrammableDoor tile=new TileEntityLargeProgrammableDoor();BlockPos pos=new BlockPos(8,64,8);tile.setPos(pos);
        int calls=2048;
        for(int motion:new int[]{0,3}) {
            configure(tile,true,motion,true,true,0,1,0);
            List<AxisAlignedBB> boxes=tile.collisionGeometry(EnumFacing.SOUTH,true);DoorCollisionIndex index=tile.collisionIndex(EnumFacing.SOUTH,true);
            Vec3d[] starts=new Vec3d[64],ends=new Vec3d[64];AxisAlignedBB[] queries=new AxisAlignedBB[64];
            for(int i=0;i<64;i++) {
                double x=-1.5+(i%8)*.75,y=-.25+(i/8)*.5;
                starts[i]=point(pos,x,y,-3);ends[i]=point(pos,x,y,4);
                queries[i]=new AxisAlignedBB(pos.getX()+x-.15,pos.getY()+y-.15,pos.getZ()+.1,pos.getX()+x+.15,pos.getY()+y+.15,pos.getZ()+.9);
            }
            for(boolean ray:new boolean[]{false,true}) {
                long[][] cpu=new long[2][15],allocated=new long[2][15];List<AxisAlignedBB> out=new ArrayList<>();
                for(int sample=-40;sample<15;sample++)for(int order=0;order<2;order++) {
                    int algorithm=(sample+order)&1;long bytes=meter.getThreadAllocatedBytes(thread),start=meter.getCurrentThreadCpuTime();
                    for(int i=0;i<calls;i++) {
                        int j=i&63;
                        if(ray)consumed=algorithm==0?original(boxes,starts[j],ends[j]):index.trace(starts[j],ends[j]);
                        else {out.clear();if(algorithm==0)original(boxes,queries[j],out);else index.addIntersecting(queries[j],out);consumed=out;}
                    }
                    long elapsed=meter.getCurrentThreadCpuTime()-start,used=meter.getThreadAllocatedBytes(thread)-bytes;
                    if(sample>=0){cpu[algorithm][sample]=elapsed;allocated[algorithm][sample]=used;}
                }
                for(int algorithm=0;algorithm<2;algorithm++) {
                    Arrays.sort(cpu[algorithm]);Arrays.sort(allocated[algorithm]);
                    System.out.println("door-index benchmark motion="+(motion==0?"sideways":"X")+" operation="+(ray?"ray":"collision")
                            +" boxes="+boxes.size()+" calls="+calls+" algorithm="+(algorithm==0?"linear":"index")
                            +" median-thread-ns="+cpu[algorithm][7]+" allocated-bytes="+allocated[algorithm][7]);
                }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityLargeProgrammableDoor.class,
                new net.minecraft.util.ResourceLocation("vandorlabs","door_collision_index_checks"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityTokenSlot.class,
                new net.minecraft.util.ResourceLocation("vandorlabs","door_collision_index_reader"));
        matrix();extensions();benchmark();
    }
}

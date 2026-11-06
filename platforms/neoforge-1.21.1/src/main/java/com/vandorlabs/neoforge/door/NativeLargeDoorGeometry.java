package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.NativeVoxelShapes;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.render.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Exact released Large collision boxes. Raw cache is world-free; world snapshots retain source rounding. */
public final class NativeLargeDoorGeometry {
    private static final int BUDGET=4*1024*1024,SNAPSHOT_BUDGET=4*1024*1024;
    private record Raw(List<LargeDoorGeometry.Box> boxes,int cost){}
    private static final LinkedHashMap<Integer,Raw> CACHE=new LinkedHashMap<>();private static int retained;
    private NativeLargeDoorGeometry(){}
    public record Key(boolean sliding,boolean framed,boolean hinges,int direction,int depth,boolean panel,int panelMode,boolean security,Direction face,boolean open){}
    public static Key key(NativeDoorSettings s,Direction face,boolean open){if(face.getAxis()==Direction.Axis.Y||s.depth()<0||s.depth()>2)throw new IllegalArgumentException("Large Door pose");var d=s.door();return new Key(d.sliding,d.framed,d.hinges,d.sliding?d.direction:0,s.depth(),d.panel,s.panelMode(),s.securityMode()>0,face,open);}
    private static Raw raw(Key k){int key=k.face().get2DDataValue()|(k.sliding()?4:0)|(k.framed()?8:0)|(k.hinges()?16:0)|(k.direction()<<5)|(k.depth()<<7)|(k.panel()?512:0)|(k.open()?1024:0);
        synchronized(CACHE){var found=CACHE.get(key);if(found!=null)return found;double offset=SpaceDoorData.positionOffset(k.sliding(),k.framed(),k.hinges(),k.depth());var boxes=List.copyOf(LargeDoorGeometry.boxes(k.framed(),k.sliding(),k.direction(),k.open(),offset,k.face().get2DDataValue(),k.panel(),k.depth()==2));int cost=512+boxes.size()*80;if(cost+128>BUDGET)throw new IllegalStateException("Large Door raw geometry exceeds byte budget");while(retained+cost+128>BUDGET){var it=CACHE.entrySet().iterator();var victim=it.next();retained-=victim.getValue().cost();it.remove();}var built=new Raw(boxes,cost);CACHE.put(key,built);retained+=cost;return built;}
    }
    public static final class Snapshot {
        private final Key key;private final BlockPos root;private final List<AABB> boxes;private final AABB bounds;private final AtomicReferenceArray<Part> parts=new AtomicReferenceArray<>(9);private int cost;
        private Snapshot(Key key,BlockPos root,List<AABB> boxes){this.key=key;this.root=root.immutable();this.boxes=List.copyOf(boxes);AABB broad=null;for(var b:boxes)broad=broad==null?b:broad.minmax(b);bounds=broad==null?new AABB(root):broad;cost=512+boxes.size()*80;}
        public Key key(){return key;}public BlockPos root(){return root;}public List<AABB> boxes(){return boxes;}public AABB bounds(){return bounds;}
        public Part part(int column,int row){if(column<0||column>2||row<0||row>2)throw new IllegalArgumentException("Large Door part");int index=column*3+row;var found=parts.get(index);if(found!=null)return found;synchronized(this){found=parts.get(index);if(found!=null)return found;var built=buildPart(this,column,row);if((long)cost+built.cost()>SNAPSHOT_BUDGET)throw new IllegalStateException("Large Door snapshot exceeds checked retained budget");cost+=built.cost();parts.set(index,built);return built;}}
        public synchronized int retainedBytes(){return cost;}
    }
    public record Part(BlockPos pos,List<AABB> collision,AABB outline,VoxelShape collisionShape,VoxelShape outlineShape,int cost){public Part{collision=List.copyOf(collision);}}
    public static Snapshot build(NativeDoorSettings settings,Direction face,boolean open,BlockPos root){var k=key(settings,face,open);var source=raw(k).boxes();List<AABB> boxes=new ArrayList<>(source.size()+1);for(var b:source)boxes.add(new AABB(b.x0,b.y0,b.z0,b.x1,b.y1,b.z1).move(root));
        if(k.panel()&&!boxes.isEmpty()){var pad=boxes.removeLast().move(-root.getX(),-root.getY(),-root.getZ());if(k.security()){pad=pad.inflate(0,1.05/16,0);pad=face.getAxis()==Direction.Axis.X?pad.inflate(.75/16,0,0):pad.inflate(0,0,.75/16);}boxes.add(pad.move(root));if(k.panelMode()==2)boxes.add(SpaceDoorControlPanel.mirror(AABB::new,pad.minX,pad.minY,pad.minZ,pad.maxX,pad.maxY,pad.maxZ,face.get3DDataValue()).move(root));}
        return new Snapshot(k,root,boxes);
    }
    private static Part buildPart(Snapshot s,int column,int row){Direction width=s.key.face().getCounterClockWise();BlockPos pos=s.root.relative(width,column).above(row);double low=column==0?Double.NEGATIVE_INFINITY:0,high=column==2?Double.POSITIVE_INFINITY:1,minX=Double.NEGATIVE_INFINITY,maxX=Double.POSITIVE_INFINITY,minZ=Double.NEGATIVE_INFINITY,maxZ=Double.POSITIVE_INFINITY;
        if(width.getAxis()==Direction.Axis.X){minX=pos.getX()+(width.getStepX()>0?low:1-high);maxX=pos.getX()+(width.getStepX()>0?high:1-low);}else{minZ=pos.getZ()+(width.getStepZ()>0?low:1-high);maxZ=pos.getZ()+(width.getStepZ()>0?high:1-low);}var partition=new AABB(minX,row==0?Double.NEGATIVE_INFINITY:pos.getY(),minZ,maxX,row==2?Double.POSITIVE_INFINITY:pos.getY()+1,maxZ);var unit=new AABB(pos);List<AABB> collisions=new ArrayList<>(),local=new ArrayList<>();AABB outline=null;
        for(var b:s.boxes){if(b.intersects(partition)){var clipped=b.intersect(partition);collisions.add(clipped);local.add(clipped.move(-pos.getX(),-pos.getY(),-pos.getZ()));}if(b.intersects(unit)){var clipped=b.intersect(unit);outline=outline==null?clipped:outline.minmax(clipped);}}
        outline=outline==null?new AABB(0,0,0,1,1,1):outline.move(-pos.getX(),-pos.getY(),-pos.getZ());var collision=NativeVoxelShapes.union(local,SNAPSHOT_BUDGET);var selection=NativeVoxelShapes.union(List.of(outline),SNAPSHOT_BUDGET);int cost=128+collisions.size()*80+NativeVoxelShapes.retainedCost(collision,local.size())+NativeVoxelShapes.retainedCost(selection,1);return new Part(pos,collisions,outline,collision,selection,cost);
    }
    /** Source Large rays compare every full box in original order; no ordinary-pad priority rule applies. */
    public static LegacyBoxTrace.Hit trace(Snapshot s,double sx,double sy,double sz,double ex,double ey,double ez){LegacyBoxTrace.Hit best=null;for(var b:s.boxes){var hit=LegacyBoxTrace.clip(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,sx,sy,sz,ex,ey,ez);if(hit!=null&&(best==null||hit.distance(sx,sy,sz)<best.distance(sx,sy,sz)))best=hit;}return best;}
    public static boolean panelContains(NativeDoorSettings settings,double normal,double y){return NativeDoorGeometry.panelContains(settings,normal,y,1.5);}
    static long retainedBytes(){synchronized(CACHE){return retained+128L;}}static int entries(){synchronized(CACHE){return CACHE.size();}}static void clear(){synchronized(CACHE){CACHE.clear();retained=0;}}
}

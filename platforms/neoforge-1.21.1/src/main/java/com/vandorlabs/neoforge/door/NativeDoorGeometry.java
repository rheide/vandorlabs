package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.NativeVoxelShapes;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.render.SpaceDoorBounds;
import com.vandorlabs.render.SpaceDoorControlPanel;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Released ordinary-door geometry only. World/half/pair resolution and moving-leaf targeting stay outside it. */
public final class NativeDoorGeometry {
    private static final SpaceDoorBounds.Factory<AABB> BOXES=AABB::new;
    private static final int KEYS=4096,MASK=KEYS-1,BUDGET=4*1024*1024,FIXED_BYTES=KEYS*12+128;
    private static final AtomicReferenceArray<Entry> CACHE=new AtomicReferenceArray<>(KEYS);
    private static final int[] FIFO=new int[KEYS];private static int head,size,retained;
    private record Entry(AABB bounds,VoxelShape shape,int cost){}
    private NativeDoorGeometry(){}
    private static int face(Direction face){int value=face.get3DDataValue()-2;if(value<0||value>3)throw new IllegalArgumentException("Ordinary door facing must be horizontal");return value;}
    private static void depth(int value){if(value<0||value>2)throw new IllegalArgumentException("Ordinary door depth");}
    private static int leafKey(boolean sliding,boolean framed,boolean hinges,int depth,Direction face,boolean open,boolean left,boolean paired,boolean orphan){
        depth(depth);if(sliding){open=false;left=false;framed=false;paired=false;hinges=false;}else{if(!open){left=false;paired=false;if(depth!=2||!hinges)framed=false;}else if(!framed)paired=false;if(depth!=2)hinges=false;}
        return face(face)|(sliding?4:0)|(open?8:0)|(left?16:0)|(framed?32:0)|(paired?64:0)|(hinges?128:0)|(depth<<8)|(orphan?1024:0);
    }
    private static Entry entry(int key){
        Entry found=CACHE.get(key);if(found!=null)return found;
        synchronized(CACHE){found=CACHE.get(key);if(found!=null)return found;Entry built=create(key);if(built.cost+FIXED_BYTES>BUDGET)throw new IllegalStateException("Door geometry exceeds retained budget");
            while(size>0&&retained+built.cost+FIXED_BYTES>BUDGET){int victim=FIFO[head];head=(head+1)&MASK;size--;Entry removed=CACHE.getAndSet(victim,null);if(removed!=null)retained-=removed.cost;}
            if(size==KEYS)throw new IllegalStateException("Door geometry queue overflow");FIFO[(head+size)&MASK]=key;size++;retained+=built.cost;CACHE.set(key,built);return built;
        }
    }
    private static Entry create(int key){
        if((key&2048)!=0)return panelEntry(key&2047);
        int face=(key&3)+2,d=(key>>>8)&3;boolean sliding=(key&4)!=0,open=(key&8)!=0,left=(key&16)!=0,framed=(key&32)!=0,paired=(key&64)!=0,hinges=(key&128)!=0,orphan=(key&1024)!=0;
        AABB local=!sliding&&open?SpaceDoorBounds.rotatingOpen(BOXES,face,left,framed,paired):SpaceDoorBounds.closed(BOXES,face,sliding);
        double offset=orphan?0:SpaceDoorData.positionOffset(sliding,framed,hinges,d);AABB bounds=SpaceDoorBounds.positioned(BOXES,local.minX,local.minY,local.minZ,local.maxX,local.maxY,local.maxZ,face,offset,!sliding&&open);
        VoxelShape shape=NativeVoxelShapes.union(List.of(bounds),BUDGET-FIXED_BYTES);return new Entry(bounds,shape,NativeVoxelShapes.retainedCost(shape,1)+128);
    }
    public static AABB outline(NativeDoorSettings settings,Direction face,boolean open,boolean hingeLeft,boolean paired){var d=settings.door();return entry(leafKey(d.sliding,d.framed,d.hinges,settings.depth(),face,open,hingeLeft,paired,false)).bounds;}
    public static VoxelShape shape(NativeDoorSettings settings,Direction face,boolean open,boolean hingeLeft,boolean paired,boolean collision){var d=settings.door();if(collision&&d.sliding&&open)return Shapes.empty();return entry(leafKey(d.sliding,d.framed,d.hinges,settings.depth(),face,open,hingeLeft,paired,false)).shape;}
    /** Missing-host fallback is the block prototype, not a newly configured near-edge door. */
    public static AABB orphanOutline(boolean slidingModel,Direction face,boolean open,boolean hingeLeft,boolean paired){return entry(leafKey(slidingModel,true,false,0,face,open,hingeLeft,paired,true)).bounds;}
    public static VoxelShape orphanShape(boolean slidingModel,Direction face,boolean open,boolean hingeLeft,boolean paired,boolean collision){if(collision&&slidingModel&&open)return Shapes.empty();return entry(leafKey(slidingModel,true,false,0,face,open,hingeLeft,paired,true)).shape;}
    private static Entry panelEntry(int key){
        int face=(key&3)+2,d=(key>>>5)&3;boolean sliding=(key&4)!=0,framed=(key&8)!=0,hinges=(key&16)!=0,right=(key&128)!=0,security=(key&256)!=0,upper=(key&512)!=0,mirror=(key&1024)!=0;
        double offset=SpaceDoorData.positionOffset(sliding,framed,hinges,d);AABB box=SpaceDoorControlPanel.bounds(BOXES,face,right?SpaceDoorControlPanel.Side.RIGHT:SpaceDoorControlPanel.Side.LEFT,offset,sliding,d==2);
        if(security)box=box.inflate(0,.7/16,0);if(mirror)box=SpaceDoorControlPanel.mirror(BOXES,box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,face);box=box.move(0,upper?-1:0,0);return new Entry(box,null,160);
    }
    public static AABB panel(NativeDoorSettings settings,Direction face,SpaceDoorControlPanel.Side side,boolean upper,boolean mirrored){
        if(side==SpaceDoorControlPanel.Side.NONE)return null;var d=settings.door();int dep=settings.depth();depth(dep);boolean framed=d.framed,hinges=d.hinges;
        if(d.sliding||dep!=2){framed=false;hinges=false;}else if(!hinges)framed=false;
        int key=2048|face(face)|(d.sliding?4:0)|(framed?8:0)|(hinges?16:0)|(dep<<5)|(side==SpaceDoorControlPanel.Side.RIGHT?128:0)|(settings.securityMode()>0?256:0)|(upper?512:0)|(mirrored?1024:0);return entry(key).bounds;
    }
    public static boolean panelContains(NativeDoorSettings settings,double normalZ,double lowerY,double scale){var d=settings.door();return SpaceDoorControlPanel.containsConfigured(d.sliding,settings.depth()==2,settings.panelMode(),settings.securityMode(),SpaceDoorData.positionOffset(d.sliding,d.framed,d.hinges,settings.depth()),normalZ,lowerY,scale);}
    static long retainedBytes(){synchronized(CACHE){return FIXED_BYTES+(long)retained;}}
    static int entries(){synchronized(CACHE){return size;}}
    static void clear(){synchronized(CACHE){for(int i=0;i<KEYS;i++)CACHE.set(i,null);head=size=retained=0;}}
}

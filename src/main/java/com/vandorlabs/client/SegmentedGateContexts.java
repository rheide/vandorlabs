package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;
import java.lang.ref.WeakReference;

/** Loaded main-thread preparation; values retain immutable row keys and weak cosmetic clock handles only. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class SegmentedGateContexts {
    private static final int BUDGET=4*1024*1024,LIMIT=1024;
    /** Exact world-free description for the ordinary template renderer. */
    public static final class Appearance {
        public final SegmentedDoorSettings settings;
        public final int texture,height,column,columns,face,rotation;
        public final boolean nativePanel,uniformArtwork;
        private final SegmentedDoorSettings[] panels;
        private final SegmentedDoorWindows.Layout[] windows;
        private final int[] artwork;
        private Appearance(SegmentedDoorTile owner,SegmentedDoorTile[] members){
            settings=owner.settings;texture=owner.texture;height=owner.height;column=owner.column;columns=owner.columns;
            EnumFacing facing=owner.facing();face=facing.getIndex();rotation=((int)(180-facing.getHorizontalAngle())/90)&3;
            panels=new SegmentedDoorSettings[height];windows=new SegmentedDoorWindows.Layout[height];artwork=new int[height];
            boolean nativePanel=settings.treadPixels==16&&settings.position==0&&settings.direction==0&&!settings.straight,uniform=true;
            int first=owner.effectiveTexture();
            for(int row=0;row<height;row++){
                SegmentedDoorTile member=members[row];int selected=member.effectiveTexture();
                panels[row]=member.settings;artwork[row]=selected;windows[row]=SegmentedDoorWindows.layout(member);
                nativePanel&=!SegmentedArtwork.complex(selected)&&member.settings.nativePanel()&&selected<com.vandorlabs.tiles.ScreenHousingTextures.BUILTIN_COUNT&&!member.frameTexture();
                uniform&=selected==first;
            }
            this.nativePanel=nativePanel;uniformArtwork=uniform;
        }
        public int artwork(int row){return artwork[row];}
        public SegmentedDoorSettings panel(int row){return panels[row];}
        public SegmentedDoorWindows.Layout window(int row){return windows[row];}
        int bytes(){return 256+height*192;}
    }
    public static final class Prepared {
        public final boolean complex;public final int flags,rotation,height,width,column;public final SegmentedDoorSettings settings;
        private Appearance appearance;
        public Appearance appearance(){return appearance;}
        public final SegmentedGateContext context;private final int[] kinds;private final SegmentedDoorPanels.Prepared[] rows;
        private final WeakReference<SegmentedDoorTile> left,right;
        private final boolean joinedWindows;private final long windowRevision;
        public final int[] lights;public long lightRevision=-1,dynamic=-1,origin;public int vehicleRevision;public WeakReference<Object> structure;
        private Prepared(Appearance appearance,int flags,boolean joinedWindows,long revision){this.appearance=appearance;this.flags=flags;complex=false;rotation=height=width=column=0;settings=null;context=null;kinds=null;rows=null;lights=null;left=new WeakReference<>(null);right=new WeakReference<>(null);this.joinedWindows=joinedWindows;windowRevision=revision;}
        private Prepared(SegmentedDoorTile owner,int flags,int rotation,SegmentedGateContext context,int[] kinds,SegmentedDoorPanels.Prepared[] rows,SegmentedDoorTile left,SegmentedDoorTile right,boolean joinedWindows,long revision){complex=true;appearance=null;this.flags=flags;this.rotation=rotation;height=owner.height;width=owner.columns;column=owner.column;settings=owner.settings;this.context=context;this.kinds=kinds;this.rows=rows;this.left=new WeakReference<>(left);this.right=new WeakReference<>(right);lights=new int[height];this.joinedWindows=joinedWindows;windowRevision=revision;}
        public int kind(int row){return kinds[row];}public void emitPanel(int row,int pass,net.minecraft.client.renderer.BufferBuilder buffer,double x,double y,double z,double[] pose,int light){rows[row].emit(pass,buffer,x,y,z,pose,rotation,light);}
        public double leftPose(float partial){return pose(left,partial);}public double rightPose(float partial){return pose(right,partial);}
        private static double pose(WeakReference<SegmentedDoorTile> reference,float partial){SegmentedDoorTile peer=reference.get();return peer==null||!peer.ready()||!Minecraft.getMinecraft().isCallingFromMinecraftThread()?Double.NaN:com.vandorlabs.client.segmented.SegmentedDoorVisuals.pose(peer,partial);}
        int bytes(){int bytes=416+(appearance==null?0:appearance.bytes())+(context==null?0:context.retainedBytes())+(kinds==null?0:32+kinds.length*8)+(rows==null?0:16+rows.length*8);if(rows!=null)for(SegmentedDoorPanels.Prepared row:rows)if(row!=null)bytes+=row.retainedBytes();return bytes;}
    }
    private static final class WorldCache {
        final SegmentedGatePreparedCache<SegmentedDoorTile,Prepared> entries=new SegmentedGatePreparedCache<>(BUDGET,LIMIT);
        long windowRevision,lastUse;int vehicleRevision=-1;WeakReference<Object> snapshot;
    }
    private static final WeakHashMap<World,WorldCache> WORLDS=new WeakHashMap<>();
    private static long useSequence;
    private static long builds,reads;
    public static long builds(){return builds;}public static long loadedReads(){return reads;}
    public static int retainedBytes(){synchronized(WORLDS){int bytes=512;for(WorldCache cache:WORLDS.values())bytes+=64+cache.entries.retainedBytes();return bytes;}}
    public static void clear(){synchronized(WORLDS){WORLDS.clear();}}
    public static void changed(SegmentedDoorTile tile){World world=tile.getWorld();if(world==null||!world.isRemote)return;SegmentedDoorWindows.invalidate(world);BlockPos pos=tile.getPos();synchronized(WORLDS){WorldCache cache=WORLDS.get(world);if(cache!=null){cache.entries.changed(pos.getX(),pos.getY(),pos.getZ());cache.windowRevision++;}}}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){synchronized(WORLDS){WORLDS.remove(event.getWorld());}}
    private static SegmentedDoorTile loaded(World world,BlockPos pos){reads++;SegmentedDoorTile tile=SegmentedDoorTile.at(world,pos);return tile!=null&&tile.ready()?tile:null;}
    private static boolean driver(SegmentedDoorTile a,SegmentedDoorTile b,int offset,EnumFacing facing){return b!=null&&b.isRoot()&&a.height==b.height&&a.columns==b.columns&&a.column+offset==b.column&&b.facing()==facing&&a.settings.direction==b.settings.direction&&a.settings.straight==b.settings.straight&&a.settings.position==b.settings.position&&a.ticksPerSection==b.ticksPerSection;}
    private static boolean movingDriver(SegmentedDoorTile a,SegmentedDoorTile b){return a.motionIdentity!=null&&a.motionIdentity.equals(b.motionIdentity)&&a.started==b.started&&a.duration==b.duration&&a.targetOpen==b.targetOpen&&Double.doubleToLongBits(a.from)==Double.doubleToLongBits(b.from);}
    private static int[] styles(SegmentedDoorTile root){int[] result=new int[root.height];for(int row=0;row<result.length;row++){SegmentedDoorTile cell=loaded(root.getWorld(),root.getPos().down(row));result[row]=cell!=null&&cell.topOffset==row&&cell.height==root.height?SegmentedArtwork.gate(cell.effectiveTexture()):-1;}return result;}
    private static void trim(World keep){
        // Several small carried views should coexist; evict by bytes rather than
        // alternating between the former two-world slots every render pass.
        while(WORLDS.size()>128||retainedBytes()>2*BUDGET){World oldest=null;long use=Long.MAX_VALUE;for(Map.Entry<World,WorldCache> entry:WORLDS.entrySet())if(entry.getKey()!=keep&&entry.getValue().lastUse<use){oldest=entry.getKey();use=entry.getValue().lastUse;}if(oldest==null)return;WORLDS.remove(oldest);}
    }
    public static Prepared prepare(SegmentedDoorTile owner){return prepare(owner,false);}
    /** Full ordinary appearance is needed only for a real draw, never for pass eligibility. */
    public static Prepared prepareRender(SegmentedDoorTile owner){return prepare(owner,true);}
    private static Prepared prepare(SegmentedDoorTile owner,boolean render){
        if(!Minecraft.getMinecraft().isCallingFromMinecraftThread()||!owner.isRoot()||!owner.ready()||!owner.getWorld().isRemote)return null;World world=owner.getWorld();BlockPos pos=owner.getPos();synchronized(WORLDS){
            WorldCache state=WORLDS.get(world);if(state==null){state=new WorldCache();WORLDS.put(world,state);}state.lastUse=++useSequence;
            Object revision=null;if(world instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld vehicle=(com.vandorlabs.vehicle.VehicleWorld)world;revision=vehicle.structure;if(state.vehicleRevision!=vehicle.tileRevision||(state.snapshot==null?null:state.snapshot.get())!=revision){state.entries.clear();SegmentedDoorWindows.invalidate(world);state.vehicleRevision=vehicle.tileRevision;state.snapshot=new WeakReference<>(revision);}}
            SegmentedGatePreparedCache<SegmentedDoorTile,Prepared> cache=state.entries;
            Prepared found=cache.get(owner,pos.getX(),pos.getY(),pos.getZ(),revision);if(found!=null&&(!found.joinedWindows||found.windowRevision==state.windowRevision)){
                if(render&&!found.complex&&found.appearance==null){
                    SegmentedDoorTile[] members=new SegmentedDoorTile[owner.height];for(int row=0;row<owner.height;row++){SegmentedDoorTile part=loaded(world,pos.down(row));members[row]=part!=null&&part.topOffset==row&&part.height==owner.height?part:owner;}
                    found.appearance=new Appearance(owner,members);cache.put(owner,pos.getX(),pos.getY(),pos.getZ(),revision,found,found.bytes());trim(world);
                }
                return found;
            }
            if(loaded(world,pos)!=owner)return null;builds++;int height=owner.height;SegmentedDoorTile[] members=new SegmentedDoorTile[height];int[] own=new int[height],shown=new int[height];boolean complex=false,hasGlass=false,hasGate=false,joinedWindows=false;
            for(int row=0;row<height;row++){SegmentedDoorTile cell=loaded(world,pos.down(row));if(cell==null||cell.topOffset!=row||cell.height!=height)cell=null;SegmentedDoorTile part=cell==null?owner:cell;members[row]=part;shown[row]=SegmentedArtwork.gate(part.effectiveTexture());own[row]=cell==null?-1:shown[row];hasGate|=shown[row]>=0;complex|=SegmentedArtwork.complex(part.effectiveTexture());hasGlass|=shown[row]<0&&(part.settings.window||part.frameTexture()||SegmentedArtwork.translucent(part.effectiveTexture()));joinedWindows|=shown[row]<0&&part.settings.window&&part.settings.join;}
            if(!complex){found=new Prepared(render?new Appearance(owner,members):null,hasGlass?2:0,joinedWindows,state.windowRevision);cache.put(owner,pos.getX(),pos.getY(),pos.getZ(),revision,found,found.bytes());trim(world);return found;}
            EnumFacing facing=owner.facing(),right=facing.rotateY();SegmentedDoorTile left=hasGate&&owner.column>0?loaded(world,pos.offset(right,-1)):null,next=hasGate&&owner.column+1<owner.columns?loaded(world,pos.offset(right)):null;boolean joinLeft=driver(owner,left,-1,facing),joinRight=driver(owner,next,1,facing);int moving=(joinLeft&&movingDriver(owner,left)?1:0)|(joinRight&&movingDriver(owner,next)?2:0);
            SegmentedGateContext context=new SegmentedGateContext(height,owner.columns,owner.column,owner.settings.direction,owner.settings.straight,owner.settings.depthOffset(),owner.motionIdentity!=null,(joinLeft?1:0)|(joinRight?2:0),moving,own,joinLeft?styles(left):null,joinRight?styles(next):null);SegmentedDoorPanels.Prepared[] rows=new SegmentedDoorPanels.Prepared[height];for(int row=0;row<height;row++)if(shown[row]<0){SegmentedDoorTile part=members[row];rows[row]=SegmentedDoorPanels.prepare(part.settings,part.effectiveTexture(),owner.column,row,owner.columns,height,SegmentedDoorWindows.layout(part));}
            found=new Prepared(owner,1|(hasGlass?2:0),((int)(180-facing.getHorizontalAngle())/90)&3,context,shown,rows,joinLeft?left:null,joinRight?next:null,joinedWindows,state.windowRevision);cache.put(owner,pos.getX(),pos.getY(),pos.getZ(),revision,found,found.bytes());trim(world);return found;
        }
    }
    private SegmentedGateContexts(){}
}

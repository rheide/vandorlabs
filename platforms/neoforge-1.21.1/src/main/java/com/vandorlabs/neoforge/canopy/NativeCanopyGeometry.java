package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.canopy.*;
import com.vandorlabs.persistence.CanopySettingsData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.*;

/** Prepared physical facts. Selection never constructs a union of the detailed triangle boxes. */
public final class NativeCanopyGeometry {
    private NativeCanopyGeometry(){}
    public static final class Snapshot {
        private final List<CanopyFace> faces;
        private final AABB[] collision;
        private final List<AABB> boxes;
        private final int facing,x,y,z;
        private final AABB bounds;
        private final VoxelShape outline;
        private final VoxelShape[] memberOutlines=new VoxelShape[64];
        private final int[] memberOrigins=new int[64*3];
        private int memberOutlineCount;
        private final long retainedBytes;
        private Snapshot(NativeCanopyRenderSnapshot visual,double progress,int x,int y,int z){
            this.x=x;this.y=y;this.z=z;facing=visual.facing();
            var data=visual.settings().data();boolean moving=visual.kind().equals("canopy")||visual.kind().equals("long_canopy")||CanopySettingsData.supports(visual.kind())&&data.openable;
            var base=NativeCanopyModels.builtin().geometry(visual.kind(),visual.width(),visual.mask(),visual.roof(),visual.centered(),data);
            faces=CanopyTransform.faces(base,visual.kind(),visual.width(),data,moving?progress:0);
            var scalar=CanopyPhysics.boxes(faces,facing,x,y,z,CanopySettingsData.supports(visual.kind()));
            collision=new AABB[scalar.size()];for(int i=0;i<collision.length;i++){var b=scalar.get(i);collision[i]=new AABB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
            boxes=Collections.unmodifiableList(Arrays.asList(collision));var b=CanopyPhysics.bounds(scalar,x,y,z);bounds=new AABB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);
            outline=Shapes.create(bounds.move(-x,-y,-z));
            long bytes=1024L+64*1024L+collision.length*80L;for(var face:faces)bytes+=256L+face.points()*128L;retainedBytes=bytes;
        }
        public AABB bounds(){return bounds;}
        /** One bounding cuboid, independent of the dense physical approximation. */
        public VoxelShape outline(){return outline;}
        /** Selected helpers/columns outline the same owner, without allocating or unioning faces per query. */
        public synchronized VoxelShape outlineAt(int px,int py,int pz){if(px==x&&py==y&&pz==z)return outline;for(int i=0;i<memberOutlineCount;i++){int at=i*3;if(memberOrigins[at]==px&&memberOrigins[at+1]==py&&memberOrigins[at+2]==pz)return memberOutlines[i];}var shape=Shapes.create(bounds.move(-px,-py,-pz));if(memberOutlineCount<memberOutlines.length){int at=memberOutlineCount*3;memberOrigins[at]=px;memberOrigins[at+1]=py;memberOrigins[at+2]=pz;memberOutlines[memberOutlineCount++]=shape;}return shape;}
        public List<AABB> boxes(){return boxes;}
        public long retainedBytes(){return retainedBytes;}
        /** Appends existing immutable boxes; callers may reuse their output buffer. */
        public void appendIntersecting(AABB query,List<AABB> output){for(AABB box:collision)if(box.intersects(query))output.add(box);}
        public CanopyPhysics.Hit trace(CanopyPoint start,CanopyPoint end){return CanopyPhysics.trace(faces,facing,x,y,z,start,end);}
    }
    /** Owning-thread cache. No world/tile references; retire on world unload or resource replacement. */
    public static final class Cache {
        private record Key(NativeCanopyRenderSnapshot visual,long progress,int x,int y,int z){}
        private final long budget;
        private final LinkedHashMap<Key,Snapshot> entries=new LinkedHashMap<>(16,.75f,true);
        private long retained;
        public Cache(long budget){if(budget<0)throw new IllegalArgumentException("Negative canopy geometry budget");this.budget=budget;}
        public Snapshot prepare(NativeCanopyRenderSnapshot visual,double progress,int x,int y,int z){
            if(!Double.isFinite(progress)||progress<0||progress>1)throw new IllegalArgumentException("Canopy progress");
            var key=new Key(visual,Double.doubleToLongBits(progress),x,y,z);var hit=entries.get(key);if(hit!=null)return hit;
            var next=new Snapshot(visual,progress,x,y,z);long cost=next.retainedBytes()+4096; // key/settings/channels and map overhead
            if(cost<=budget){while(retained+cost>budget){var iterator=entries.entrySet().iterator();var old=iterator.next();retained-=old.getValue().retainedBytes()+4096;iterator.remove();}entries.put(key,next);retained+=cost;}return next;
        }
        public long retainedBytes(){return retained;}
        public int size(){return entries.size();}
        public void clear(){entries.clear();retained=0;}
    }
}

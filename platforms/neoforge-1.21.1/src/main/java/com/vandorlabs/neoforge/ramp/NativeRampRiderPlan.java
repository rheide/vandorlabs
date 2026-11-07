package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.ramp.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.*;

/** Same-tick all-rider preflight. The owner must preflight physical publication before commit. */
public final class NativeRampRiderPlan {
    private record Entry(Entity entity,Vec3 position,AABB body,Vec3 velocity,Vec3 delta){}
    private final ServerLevel level;private final BlockPos root;private final NativeRampFootprint.Spec profile;private final boolean shaped,fromOriginal,toOriginal;private final double previous,current;private final long tick;private final List<Entry> entries;private boolean consumed;private int correctionFailures;
    private NativeRampRiderPlan(ServerLevel level,NativeRampFootprint.Spec spec,boolean shaped,double previous,double current,boolean fromOriginal,boolean toOriginal,List<Entry> entries){this.level=level;profile=spec;this.shaped=shaped;this.previous=previous;this.current=current;this.fromOriginal=fromOriginal;this.toOriginal=toOriginal;this.root=spec.root().immutable();tick=level.getGameTime();this.entries=List.copyOf(entries);}
    /** Admission is bound to the exact immutable profile and the flags used to select actors. */
    boolean matches(ServerLevel level,NativeRampFootprint.Spec spec,boolean shaped,double previous,double current,boolean fromOriginal,boolean toOriginal){NativeRampRiders.requireThread(level);return this.level==level&&profile==spec&&this.shaped==shaped&&Double.compare(this.previous,previous)==0&&Double.compare(this.current,current)==0&&this.fromOriginal==fromOriginal&&this.toOriginal==toOriginal;}
    public int size(){return entries.size();}public int correctionFailures(){return correctionFailures;}
    /** Null denotes an obstructed rider. Cold queries and unsupported profiles refuse explicitly. */
    public static NativeRampRiderPlan prepare(ServerLevel level,NativeRampFootprint.Spec spec,boolean shaped,double previous,double current,boolean fromOriginal,boolean toOriginal){
        NativeRampRiders.requireThread(level);
        if(!Double.isFinite(previous)||!Double.isFinite(current)||previous<0||previous>1||current<0||current>1)throw new IllegalArgumentException("Ramp rider poses");
        if(spec.extend()&&!spec.moat())return new NativeRampRiderPlan(level,spec,shaped,previous,current,fromOriginal,toOriginal,List.of());
        long work=0;for(var origin:spec.origins())work+=(long)(shaped?origin.shapes().size():1)*(spec.elevator()?1:ControllerPlatform.treadCount(spec.treadPixels()));if(work>131072)throw new IllegalArgumentException("Ramp rider query geometry budget");
        var moat=spec.moat()?NativeRampRiderTargets.moatSurfaces(spec,previous,current,fromOriginal,toOriginal):null;var bounds=moat==null?query(spec,shaped,previous,fromOriginal):moat.bounds().inflate(0,.6,0);loaded(level,bounds);
        var entities=new ArrayList<Entity>();level.getEntities(EntityTypeTest.forClass(Entity.class),bounds,entity->!entity.isSpectator(),entities,8193);if(entities.size()>8192)throw new IllegalArgumentException("Ramp rider count budget");
        var candidates=new ArrayList<NativeRampRiderTargets.Candidate>(entities.size());for(int i=0;i<entities.size();i++){var entity=entities.get(i);candidates.add(new NativeRampRiderTargets.Candidate(i,entity.getBoundingBox(),entity instanceof Player,NativeRampRiders.available(entity),entity.getDeltaMovement().y,entity.isPickable()));}
        var targets=NativeRampRiderTargets.select(spec,shaped,previous,current,fromOriginal,toOriginal,candidates,moat);if(targets==null)return null;var entries=new ArrayList<Entry>(targets.size());
        for(var entry:targets.entrySet()){var entity=entities.get(entry.getKey().key());var delta=entry.getValue();if(!NativeRampRiders.clear(entity,delta,NativeRampRiders.obstacles(level,spec.root(),entity,entity.getBoundingBox().expandTowards(delta).inflate(1e-7))))return null;entries.add(new Entry(entity,entity.position(),entity.getBoundingBox(),entity.getDeltaMovement(),delta));}
        return new NativeRampRiderPlan(level,spec,shaped,previous,current,fromOriginal,toOriginal,entries);
    }
    public boolean ready(){NativeRampRiders.requireThread(level);if(consumed||tick!=level.getGameTime())return false;for(var entry:entries){var entity=entry.entity;if(entity.level()!=level||!NativeRampRiders.available(entity)||!entry.position.equals(entity.position())||!entry.body.equals(entity.getBoundingBox())||!entry.velocity.equals(entity.getDeltaMovement()))return false;}return true;}
    /** Revalidate foreign geometry after journal-owned source/part publication, before any rider moves. */
    public boolean recheck(){if(!ready())return false;for(var entry:entries)if(!NativeRampRiders.clear(entry.entity,entry.delta,NativeRampRiders.obstacles(level,root,entry.entity,entry.body.expandTowards(entry.delta).inflate(1e-7))))return false;return true;}
    /** Single use; no rider moves if any prepared entity changed during command preparation. */
    public boolean commit(NativeRampRiders.Sync sync){
        if(!ready())return false;for(var entry:entries)if(entry.entity instanceof ServerPlayer)Objects.requireNonNull(sync,"Ramp player correction sender");consumed=true;
        var steps=new ArrayList<NativeRampRiderBatch.Step>(entries.size());for(var entry:entries)steps.add(new NativeRampRiderBatch.Step(entry.entity,entry.delta,false));correctionFailures=NativeRampRiderBatch.commit(level,steps,sync);return true;
    }
    private static AABB query(NativeRampFootprint.Spec s,boolean shaped,double previous,boolean fromOriginal){
        AABB bounds=null;var direction=RampGeometry.Direction.valueOf(s.facing().name());
        for(var origin:s.origins()){var p=origin.position();int row=p.getX()*s.facing().getStepX()+p.getZ()*s.facing().getStepZ()-s.minimumAlong();
            if(s.verticalLayout()){for(var piece:NativeRampRiderTargets.verticalPieces(s,origin,previous,fromOriginal,false,0)){var b=piece.box;var query=new AABB(b.minX,b.maxY-.15,b.minZ,b.maxX,b.maxY+.5,b.maxZ);bounds=bounds==null?query:bounds.minmax(query);}continue;}
            if(shaped){for(var box:NativeRampRiderTargets.treads(s,origin,row,previous,fromOriginal)){var query=box.inflate(0,.2,0);bounds=bounds==null?query:bounds.minmax(query);}continue;}
            if(s.travel()==RampGeometry.VERTICAL){var query=new AABB(p.getX(),p.getY()+s.high()+Math.min(0,Math.min(s.start(),s.end()))-.2,p.getZ(),p.getX()+1,p.getY()+s.high()+Math.max(0,Math.max(s.start(),s.end()))+.2,p.getZ()+1);bounds=bounds==null?query:bounds.minmax(query);}
            else for(int step=0;step<(s.elevator()?1:ControllerPlatform.treadCount(s.treadPixels()));step++){var box=RampGeometry.movingTread(direction,p.getX(),p.getY(),p.getZ(),s.low(),s.high(),row,s.length(),s.start(),s.end(),s.treadPixels(),step,previous,previous,s.elevator(),s.travel(),false,s.fast(),s.interpolation());var query=new AABB(box.minX,box.minY-.2,box.minZ,box.maxX,box.maxY+.2,box.maxZ);bounds=bounds==null?query:bounds.minmax(query);}
        }if(bounds==null)throw new IllegalArgumentException("Empty Ramp rider query");return bounds;
    }
    private static void loaded(ServerLevel level,AABB bounds){for(double value:new double[]{bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ})if(!Double.isFinite(value)||Math.abs(value)>30000001)throw new IllegalArgumentException("Ramp rider query bounds");int x0=Mth.floor(bounds.minX)>>4,x1=Mth.floor(bounds.maxX)>>4,z0=Mth.floor(bounds.minZ)>>4,z1=Mth.floor(bounds.maxZ)>>4;if((long)(x1-x0+1)*(z1-z0+1)>64)throw new IllegalArgumentException("Ramp rider query work budget");for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(level.getChunkSource().getChunkNow(x,z)==null)throw new IllegalArgumentException("Ramp rider selection waits for loaded chunks");}
}

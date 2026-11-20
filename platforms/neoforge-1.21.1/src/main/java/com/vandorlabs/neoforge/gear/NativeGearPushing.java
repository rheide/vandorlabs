package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.door.NativeLargeDoorLifecycle;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Bounded native terrain contacts. Pushed blocks never become arm payload ownership. */
final class NativeGearPushing {
    private static final int MAX_BLOCKS=12,MAX_SCAN=8192;
    record Plan(List<NativeGearSnapshot.Cell> before,List<NativeGearSnapshot.Cell> after,CompoundTag action){}
    private static boolean enabled(NativeGearEntity arm){return arm.canPushBlocks()&&arm.settings().pushBlocks();}
    private static boolean ordinary(BlockState state){return !state.isAir()&&!state.hasBlockEntity()&&state.getFluidState().isEmpty()
            &&state.getPistonPushReaction()==PushReaction.NORMAL&&state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO)
            &&state.getDestroySpeed(EmptyBlockGetter.INSTANCE,BlockPos.ZERO)>=0;}
    private static void loaded(NativeGearEntity arm,BlockPos at){var level=(ServerLevel)arm.getLevel();if(!NativeGearFormation.loaded(level,at)||!level.getWorldBorder().isWithinBounds(at)||Math.abs((long)at.getX()-arm.getBlockPos().getX())>64||Math.abs((long)at.getY()-arm.getBlockPos().getY())>64||Math.abs((long)at.getZ()-arm.getBlockPos().getZ())>64)throw new IllegalArgumentException("Load the complete push path inside the world border");}
    private static void permission(NativeGearEntity arm,BlockPos at){permission(arm,at,true);}
    private static void permission(NativeGearEntity arm,BlockPos at,boolean checkHold){loaded(arm,at);var actor=arm.actor();var level=(ServerLevel)arm.getLevel();if(actor==null||!actor.isAlive()||actor.isSpectator()||actor.level()!=level||!level.mayInteract(actor,at)||!actor.mayUseItemAt(at,arm.mount(),actor.getMainHandItem())||checkHold&&com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level,at))throw new IllegalArgumentException("You cannot push blocks here");}
    private static void movable(NativeGearEntity arm,NativeGearSnapshot.Cell cell,boolean event){if(!ordinary(cell.state)||cell.tile()!=null||cell.section||arm.members().contains(cell.pos))throw new IllegalArgumentException("Push up to twelve ordinary solid blocks; tile entities and immovable blocks stay protected");permission(arm,cell.pos);permission(arm,cell.pos.relative(arm.mount()));if(event&&net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent((ServerLevel)arm.getLevel(),cell.pos,cell.state,arm.actor())).isCanceled())throw new IllegalArgumentException("A protection rule prevented pushing the block");}

    /** Simulate every contact before removing the original materialized attachment. */
    static void preflight(NativeGearEntity arm){
        if(!enabled(arm)||!arm.target()||arm.progress()!=0||arm.payload()==null||!arm.payload().materialized())return;
        var world=(ServerLevel)arm.getLevel();var own=new HashSet<BlockPos>();for(var cell:arm.payload().cells())own.add(cell.pos);
        var originals=new HashSet<BlockPos>();var virtual=new HashMap<BlockPos,NativeGearSnapshot.Cell>();
        int distance=(int)Math.ceil(Math.abs(arm.pose(0).footOffsetY()-arm.pose(1).footOffsetY()));
        if((long)own.size()*distance>MAX_SCAN)throw new IllegalArgumentException("Push path exceeds the bounded arm scan");
        for(int step=1;step<=distance;step++){
            var pending=new ArrayDeque<BlockPos>();for(var source:own)pending.add(source.relative(arm.mount(),step));
            var pushed=new LinkedHashMap<BlockPos,NativeGearSnapshot.Cell>();var visited=new HashSet<BlockPos>();
            while(!pending.isEmpty()){
                var at=pending.removeFirst();if(!visited.add(at)||arm.members().contains(at))continue;loaded(arm,at);
                NativeGearSnapshot.Cell cell;
                if(virtual.containsKey(at))cell=virtual.get(at);else if(own.contains(at))cell=null;else{var state=world.getBlockState(at);boolean free=state.isAir()||NativeLargeDoorLifecycle.isOpenPassage(world,at,state);if(!free&&(!ordinary(state)||world.getBlockEntity(at)!=null))throw new IllegalArgumentException("The complete outward push path must contain at most twelve movable solid blocks");cell=free?null:new NativeGearSnapshot.Cell(at,state,null);}
                if(cell==null)continue;
                if(originals.add(cell.pos)){if(originals.size()>MAX_BLOCKS)throw new IllegalArgumentException("The complete outward push path must contain at most twelve movable solid blocks");movable(arm,cell,true);}
                permission(arm,at);permission(arm,at.relative(arm.mount()));pushed.put(at,cell);pending.add(at.relative(arm.mount()));
            }
            for(var at:pushed.keySet())virtual.put(at,null);for(var entry:pushed.entrySet())virtual.put(entry.getKey().relative(arm.mount()),entry.getValue());
        }
    }

    static boolean prepare(NativeGearEntity arm,double next,List<AABB> before,List<AABB> after){
        if(!enabled(arm)||next<=arm.progress())return true;
        var plan=contact(arm,next,true,before,after);if(plan==null)return true;
        var status=NativeGearJournal.push(arm,arm.actor(),plan);
        if(status==NativeGearJournal.Status.PENDING)arm.journalWaiting("Preparing native block contact");else if(status==NativeGearJournal.Status.REFUSED)arm.pushFailure(arm.error());
        return false;
    }
    private static Plan contact(NativeGearEntity arm,double next,boolean events){return contact(arm,next,events,arm.collisionBoxes(),NativeGearGeometry.boxes(arm,next));}
    private static Plan contact(NativeGearEntity arm,double next,boolean events,List<AABB> before,List<AABB> after){
        var world=(ServerLevel)arm.getLevel();var sources=new LinkedHashSet<BlockPos>();int scanned=0;
        for(int n=0;n<after.size();n++){
            var box=after.get(n);if(n<before.size()&&box.equals(before.get(n)))continue;var inside=box.deflate(1e-6);
            int x0=(int)Math.floor(inside.minX),x1=(int)Math.floor(inside.maxX),y0=(int)Math.floor(inside.minY),y1=(int)Math.floor(inside.maxY),z0=(int)Math.floor(inside.minZ),z1=(int)Math.floor(inside.maxZ);
            long count=(long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1);if(count<0||count>MAX_SCAN-scanned)throw new IllegalArgumentException("Push contact scan bound");scanned+=count;
            for(var raw:BlockPos.betweenClosed(x0,y0,z0,x1,y1,z1)){
                var at=raw.immutable();loaded(arm,at);if(arm.members().contains(at))continue;var state=world.getBlockState(at);if(state.isAir())continue;
                boolean intersects=false;for(var collision:state.getCollisionShape(world,at,CollisionContext.empty()).toAabbs())if(collision.move(at).intersects(inside)){intersects=true;break;}if(intersects)sources.add(at);
            }
        }
        if(sources.isEmpty())return null;
        var pending=new ArrayDeque<>(sources);var saved=new LinkedHashMap<BlockPos,NativeGearSnapshot.Cell>();
        while(!pending.isEmpty()){
            var at=pending.removeFirst();if(saved.containsKey(at))continue;loaded(arm,at);var state=world.getBlockState(at);if(state.isAir())continue;
            if(saved.size()>=MAX_BLOCKS)throw new IllegalArgumentException("Push up to twelve ordinary solid blocks");
            // Reject tile owners before serialization or capability probing.
            if(!ordinary(state)||world.getBlockEntity(at)!=null)throw new IllegalArgumentException("Tile entities and immovable blocks stay protected");
            var cell=new NativeGearSnapshot.Cell(at,state,null);movable(arm,cell,events);saved.put(at,cell);pending.add(at.relative(arm.mount()));
        }
        var footprint=new LinkedHashSet<>(saved.keySet());for(var at:saved.keySet())footprint.add(at.relative(arm.mount()));
        var original=new ArrayList<NativeGearSnapshot.Cell>();var result=new ArrayList<NativeGearSnapshot.Cell>();
        for(var at:footprint){permission(arm,at);var state=world.getBlockState(at);if(state.hasBlockEntity()||world.getBlockEntity(at)!=null)throw new IllegalArgumentException("Push destination has a tile owner");original.add(new NativeGearSnapshot.Cell(at,state,null));var source=saved.get(at.relative(arm.mount().getOpposite()));result.add(new NativeGearSnapshot.Cell(at,source==null?Blocks.AIR.defaultBlockState():source.state,null));}
        var action=new CompoundTag();action.putString("Type","PUSH");action.putString("Direction",arm.mount().getName());action.putDouble("NextPhase",next);action.putLongArray("Sources",saved.keySet().stream().mapToLong(BlockPos::asLong).toArray());return new Plan(List.copyOf(original),List.copyOf(result),action);
    }

    /** Pure persisted recipe validation: exact one-cell translation, no payload or tile ownership. */
    static void validate(NativeGearEntity arm,List<NativeGearSnapshot.Cell> before,List<NativeGearSnapshot.Cell> after,NativeGearPayload payload,NativeGearStoredSections sections,CompoundTag action){
        if(!enabled(arm)||!arm.target()||payload!=null&&payload.materialized()||!action.getAllKeys().equals(Set.of("Type","Direction","NextPhase","Sources"))||!action.getString("Type").equals("PUSH")||!action.contains("Direction",Tag.TAG_STRING)||!action.getString("Direction").equals(arm.mount().getName())||!action.contains("NextPhase",Tag.TAG_DOUBLE)||!action.contains("Sources",Tag.TAG_LONG_ARRAY))throw new IllegalArgumentException("Malformed native push recipe");
        double next=action.getDouble("NextPhase");if(!Double.isFinite(next)||next<=arm.progress()||next>1||Math.abs(next-com.vandorlabs.gear.core.GearMotion.advance(arm.progress(),true,1,NativeGearEntity.DURATION))>1e-12)throw new IllegalArgumentException("Push phase changed");
        if(!Objects.equals(payload==null?null:payload.write(),arm.payload()==null?null:arm.payload().write())||!NativeGearStoredSections.write(sections,arm.identity()).equals(NativeGearStoredSections.write(arm.sections(),arm.identity())))throw new IllegalArgumentException("Push cannot change arm payload ownership");
        var positions=action.getLongArray("Sources");if(positions.length==0||positions.length>MAX_BLOCKS||before.size()>2*MAX_BLOCKS||before.size()!=after.size())throw new IllegalArgumentException("Push footprint bound");
        var originals=new HashMap<BlockPos,NativeGearSnapshot.Cell>();var destinations=new HashMap<BlockPos,NativeGearSnapshot.Cell>();for(var cell:before)if(originals.put(cell.pos,cell)!=null||cell.tile()!=null||cell.section||arm.members().contains(cell.pos))throw new IllegalArgumentException("Push original ownership");for(var cell:after)if(destinations.put(cell.pos,cell)!=null||cell.tile()!=null||cell.section)throw new IllegalArgumentException("Push destination ownership");
        var sources=new HashSet<BlockPos>();var footprint=new HashSet<BlockPos>();for(long position:positions){var at=BlockPos.of(position);var source=originals.get(at);if(!sources.add(at)||source==null||!ordinary(source.state))throw new IllegalArgumentException("Push source identity");footprint.add(at);footprint.add(at.relative(arm.mount()));}
        if(!originals.keySet().equals(footprint)||!destinations.keySet().equals(footprint))throw new IllegalArgumentException("Push footprint correspondence");
        for(var at:footprint){var prior=originals.get(at);if(!sources.contains(at)&&!prior.state.isAir())throw new IllegalArgumentException("Push overwrites another block");var incoming=at.relative(arm.mount().getOpposite());var state=sources.contains(incoming)?originals.get(incoming).state:Blocks.AIR.defaultBlockState();if(destinations.get(at).state!=state)throw new IllegalArgumentException("Push must translate each source exactly once");}
    }
    /** Journal already owns these holds; permission events run without a mutation bypass. */
    static void beforeInstall(NativeGearEntity arm,CompoundTag action){
        var level=(ServerLevel)arm.getLevel();for(long position:action.getLongArray("Sources")){
            var at=BlockPos.of(position);permission(arm,at,false);permission(arm,at.relative(arm.mount()),false);var state=level.getBlockState(at);
            if(!ordinary(state)||level.getBlockEntity(at)!=null)throw new IllegalArgumentException("Push source changed before contact");
            if(NativeGearJournal.postPushBreak(arm,at,state))throw new IllegalArgumentException("A protection rule prevented pushing the block");
        }
    }
    static boolean stillContact(NativeGearEntity arm,List<NativeGearSnapshot.Cell> before,List<NativeGearSnapshot.Cell> after,CompoundTag action){var fresh=contact(arm,action.getDouble("NextPhase"),true);if(fresh==null||!fresh.action.equals(action)||fresh.before.size()!=before.size())return false;for(int i=0;i<before.size();i++)if(!fresh.before.get(i).write().equals(before.get(i).write())||!fresh.after.get(i).write().equals(after.get(i).write()))return false;return true;}
    private NativeGearPushing(){}
}

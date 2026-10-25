package com.vandorlabs.neoforge.wall;

import com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot;
import com.vandorlabs.render.LoadedPlaneTraversal;
import com.vandorlabs.render.WallGroupPolicy;
import com.vandorlabs.ui.WallDialog;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Loaded main-thread group discovery; workers receive only immutable wall face publications. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class WallClientSnapshots {
    record Source(WallEntity entity,BlockState state,WallDialog.State settings,WallDialog.Profile profile,WallGeometry.Pose pose){
        WallGroupPolicy.Surface surface(){return new WallGroupPolicy.Surface(state.getBlock(),profile.porthole(),settings.appearance.join,state.getValue(WallBlock.FACING).get3DDataValue(),state.getValue(WallBlock.DEPTH),settings.appearance.shape,settings.main,settings.layout);}
    }
    record Group(WallGroupPolicy.Layout layout,Set<BlockPos> members){}
    private record Dependencies(Set<BlockPos> aperture,Set<BlockPos> main){}
    private static final Set<WallEntity> PENDING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<WallEntity,Dependencies> DEPENDENCIES=new WeakHashMap<>();
    private WallClientSnapshots(){}
    private static void mainThread(){if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("Wall snapshots require the client main thread");}
    private static boolean loaded(ClientLevel level,BlockPos pos){return level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4);}
    private static WallEntity installed(ClientLevel level,BlockPos pos){return loaded(level,pos)&&level.getBlockEntity(pos) instanceof WallEntity wall&&!wall.isRemoved()?wall:null;}
    private static void request(ClientLevel level,BlockPos pos){var wall=installed(level,pos);if(wall!=null)PENDING.add(wall);}
    private static void previous(ClientLevel level,Dependencies old){if(old!=null){for(var p:old.aperture)request(level,p);for(var p:old.main)request(level,p);}}
    @SubscribeEvent public static void update(NativeFluidRenderSnapshot.Update event){if(!(event.host instanceof WallEntity wall)||!(wall.getLevel() instanceof ClientLevel level))return;mainThread();previous(level,DEPENDENCIES.get(wall));PENDING.add(wall);}
    @SubscribeEvent public static void remove(NativeFluidRenderSnapshot.Remove event){if(!(event.host instanceof WallEntity wall)||!(wall.getLevel() instanceof ClientLevel level))return;mainThread();PENDING.remove(wall);previous(level,DEPENDENCIES.remove(wall));}
    public static void reload(){mainThread();for(var wall:List.copyOf(DEPENDENCIES.keySet()))if(wall.getLevel() instanceof ClientLevel level&&installed(level,wall.getBlockPos())==wall)PENDING.add(wall);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null){PENDING.clear();DEPENDENCIES.clear();return;}if(mc.getOverlay()!=null||PENDING.isEmpty())return;mainThread();
        Set<WallEntity> seeds=Collections.newSetFromMap(new IdentityHashMap<>());seeds.addAll(PENDING);PENDING.clear();
        Map<BlockPos,Source> sources=new HashMap<>();Map<BlockPos,Group> aperture=new HashMap<>(),main=new HashMap<>();Set<BlockPos> affected=new HashSet<>();
        for(var wall:seeds)if(wall.getLevel()==mc.level&&installed(mc.level,wall.getBlockPos())==wall){Source own=source(mc.level,wall.getBlockPos(),sources);Group a=group(mc.level,wall.getBlockPos(),own,true,sources,aperture),m=group(mc.level,wall.getBlockPos(),own,false,sources,main);affected.addAll(a.members);affected.addAll(m.members);}
        for(var pos:affected){Source own=source(mc.level,pos,sources);if(own==null)continue;Group a=group(mc.level,pos,own,true,sources,aperture),m=group(mc.level,pos,own,false,sources,main);
            var snapshot=WallMeshEmitter.build(own,a,m,next->source(mc.level,next,sources));DEPENDENCIES.put(own.entity,new Dependencies(a.members,m.members));
            if(own.entity.publishWallRenderSnapshot(snapshot)){mc.levelRenderer.setBlocksDirty(pos.getX()-1,pos.getY()-1,pos.getZ()-1,pos.getX()+1,pos.getY()+1,pos.getZ()+1);}
        }
    }
    private static Source source(ClientLevel level,BlockPos pos,Map<BlockPos,Source> cache){
        if(cache.containsKey(pos))return cache.get(pos);WallEntity entity=installed(level,pos);Source result=entity==null?null:new Source(entity,entity.getBlockState(),entity.wallSettings(),entity.profile(),WallGeometry.live(entity.getBlockState(),level,pos));cache.put(pos.immutable(),result);return result;
    }
    private static int axis(BlockPos pos,Direction direction){return pos.getX()*direction.getStepX()+pos.getY()*direction.getStepY()+pos.getZ()*direction.getStepZ();}
    private static Group group(ClientLevel level,BlockPos pos,Source own,boolean opening,Map<BlockPos,Source> sources,Map<BlockPos,Group> cache){
        Group found=cache.get(pos);if(found!=null)return found;Direction right=own.state.getValue(WallBlock.FACING).getClockWise();Direction[] directions={right,right.getOpposite(),Direction.UP,Direction.DOWN};
        Set<BlockPos> members=opening&&(!own.profile.porthole()||!own.settings.appearance.join)?Set.of(pos):LoadedPlaneTraversal.scan(pos,(p,d)->p.relative(directions[d]),p->loaded(level,p),p->{Source next=source(level,p,sources);return next!=null&&(opening?WallGroupPolicy.aperture(own.surface(),next.surface()):WallGroupPolicy.main(own.surface(),next.surface()));},WallGroupPolicy.LIMIT).members;
        members=Set.copyOf(members);var partitions=WallGroupPolicy.partition(members,p->axis(p,right),BlockPos::getY,WallGroupPolicy.LIMIT);
        for(var entry:partitions.entrySet())cache.put(entry.getKey(),new Group(entry.getValue(),members));return cache.get(pos);
    }
}

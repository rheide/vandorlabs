package com.vandorlabs.neoforge.door;

import com.vandorlabs.render.LargeDoorJoin;
import com.vandorlabs.neoforge.ramp.NativeRampHeldOwners;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Discover adjacent, independently possessed 3×3 doors on the level thread. */
@EventBusSubscriber(modid="vandorlabs")
final class NativeLargeDoorJoins {
    private record Changed(BlockPos anchor,Direction face,boolean horizontal){}
    private static final Map<Level,Set<Changed>> PENDING=new IdentityHashMap<>();

    static boolean eligible(NativeDoorSettings settings){
        return settings.securityMode()==0;
    }

    static boolean compatible(NativeDoorSettings first,NativeDoorSettings second){
        return eligible(first)&&eligible(second)&&first.same(second);
    }

    static LargeDoorJoin.Group<NativeLargeDoorEntity> group(NativeLargeDoorEntity root){
        return group(root,root.joinEnabled());
    }

    private static LargeDoorJoin.Group<NativeLargeDoorEntity> group(NativeLargeDoorEntity root,boolean enabled){
        Level level=root.getLevel();
        if(!enabled||level==null||!eligible(root.settings())||root.opaqueCredential(0)||root.opaqueCredential(1)||root.placementRecovery()||!NativeLargeDoorLifecycle.complete(level,root))
            return LargeDoorJoin.singleton(root);
        var face=root.getBlockState().getValue(NativeLargeDoorBlock.FACING);
        return LargeDoorJoin.resolve(root,(x,y)->{
            var position=NativeLargeDoorLifecycle.cell(root.getBlockPos(),face,root.isHorizontal(),x*LargeDoorJoin.PITCH,y*LargeDoorJoin.PITCH);
            var candidate=anchor(level,position,face,root.isHorizontal());
            return candidate!=null&&candidate.joinEnabled()&&!candidate.opaqueCredential(0)&&!candidate.opaqueCredential(1)&&compatible(root.settings(),candidate.settings())?candidate:null;
        });
    }

    static NativeLargeDoorEntity anchor(Level level,BlockPos pos,Direction face,boolean horizontal){
        if(!NativeLargeDoorLifecycle.loaded(level,pos)||!(level.getBlockEntity(pos) instanceof NativeLargeDoorEntity tile)
                ||!tile.isAnchor()||tile.isHorizontal()!=horizontal||tile.placementRecovery()
                ||tile.getBlockState().getValue(NativeLargeDoorBlock.FACING)!=face
                ||!NativeLargeDoorLifecycle.complete(level,tile))return null;
        return tile;
    }

    static boolean mutable(Level level,NativeLargeDoorEntity root){
        if(root.placementRecovery()||!NativeLargeDoorLifecycle.complete(level,root))return false;
        var face=root.getBlockState().getValue(NativeLargeDoorBlock.FACING);
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)
            if(NativeRampHeldOwners.blocked(level,NativeLargeDoorLifecycle.cell(root.getBlockPos(),face,root.isHorizontal(),x,y)))return false;
        return true;
    }

    static boolean change(Player player,NativeLargeDoorEntity root,boolean enabled){
        if(enabled==root.joinEnabled())return true;
        if(enabled&&!eligible(root.settings()))return false;
        Level level=root.getLevel();
        for(var member:group(root,enabled||root.joinEnabled()).members)
            if(!NativeLargeDoorLifecycle.permitted(level,member,player))return false;
        root.setJoin(enabled);
        root.publish();
        invalidate(root);
        return true;
    }

    static synchronized void invalidate(NativeLargeDoorEntity tile){
        Level level=tile.getLevel();
        if(level==null||!(tile.getBlockState().getBlock() instanceof NativeLargeDoorBlock))return;
        PENDING.computeIfAbsent(level,ignored->new java.util.HashSet<>()).add(
                new Changed(tile.anchorPos(),tile.getBlockState().getValue(NativeLargeDoorBlock.FACING),tile.isHorizontal()));
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        flush(event.getLevel());
    }

    @SubscribeEvent public static synchronized void unload(LevelEvent.Unload event){PENDING.remove(event.getLevel());}

    static void flush(Level level){
        Set<Changed> pending;
        synchronized(NativeLargeDoorJoins.class){pending=PENDING.remove(level);}
        if(pending==null)return;
        Set<NativeLargeDoorEntity> roots=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var changed:pending)for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++){
            var pos=NativeLargeDoorLifecycle.cell(changed.anchor,changed.face,changed.horizontal,x*3,y*3);
            var owner=anchor(level,pos,changed.face,changed.horizontal);
            if(owner!=null)roots.add(owner);
        }
        if(!level.isClientSide)roots.removeIf(root->{
            if(root.isRemoved())return true;
            var block=(NativeLargeDoorBlock)root.getBlockState().getBlock();
            var face=root.getBlockState().getValue(NativeLargeDoorBlock.FACING);
            if(!NativeLargeDoorLifecycle.supported(level,root.getBlockPos(),face,root.isHorizontal()))
                NativeLargeDoorLifecycle.neighbor(level,root.getBlockPos(),block);
            return root.isRemoved();
        });
        // Refresh visual group membership even on the client, where the blocks arrive independently.
        for(var root:roots)root.resolveJoinedAssembly();
        for(var root:roots)root.refreshJoinedAssembly();
    }

    private NativeLargeDoorJoins(){}
}

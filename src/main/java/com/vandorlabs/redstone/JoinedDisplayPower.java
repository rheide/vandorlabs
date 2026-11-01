package com.vandorlabs.redstone;

import com.vandorlabs.tiles.*;
import com.vandorlabs.surface.JoinedRectangles;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Local power only: receiving channels never become a feedback path through joined displays. */
public final class JoinedDisplayPower {
    private static final Map<World,Epoch> epochs=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Epoch {volatile long revision;}
    private static Epoch epoch(World world){synchronized(epochs){return epochs.computeIfAbsent(world,w->new Epoch());}}
    public static final class Group {
        private final List<TileEntityAnimatedScreenSelector> members;
        private boolean invalid;private final Epoch epoch;private final long revision;
        private long tick=Long.MIN_VALUE;
        private volatile int power;
        private Group(List<TileEntityAnimatedScreenSelector> members){this.members=new ArrayList<>(members);epoch=epoch(members.get(0).getWorld());revision=epoch.revision;}
        public void invalidate(){invalid=true;members.clear();}
        public void powerChanged(){tick=Long.MIN_VALUE;}
        private int level(World world){
            long now=world.getTotalWorldTime();if(tick==now)return power;
            tick=now;int next=0;
            for(TileEntityAnimatedScreenSelector tile:members){
                if(tile.isInvalid()||!world.isBlockLoaded(tile.getPos(),false)||world.getTileEntity(tile.getPos())!=tile){invalid=true;continue;}
                next=Math.max(next,LoadedRedstonePower.level(world,tile.getPos()));
            }
            int previous=power;power=next;
            if(previous!=next){GlassMaterials.changed();if(!(world instanceof com.vandorlabs.vehicle.VehicleWorld))for(TileEntityAnimatedScreenSelector tile:members)if(world.isBlockLoaded(tile.getPos(),false))world.checkLightFor(net.minecraft.world.EnumSkyBlock.BLOCK,tile.getPos());}
            return power;
        }
    }
    public static void changed(TileEntityAnimatedScreenSelector tile){
        if(tile.getWorld()!=null)epoch(tile.getWorld()).revision++;
        if(tile.joinedDisplayPower!=null)tile.joinedDisplayPower.invalidate();
    }
    public static int level(TileEntityAnimatedScreenSelector tile){
        World world=tile.getWorld();
        if(world==null||!TileEntityAnimatedScreenSelector.supportsActivation(tile.getBlockType())||tile.activationMode()==0||!DisplayJoinGeometry.supportsJoining(tile))return LoadedRedstonePower.level(world,tile.getPos());
        World authority=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).parent:world;
        if(authority.isRemote&&!com.vandorlabs.VandorLabs.proxy.canSampleDisplayPower())return tile.joinedDisplayPower==null?0:tile.joinedDisplayPower.power;
        Group group=tile.joinedDisplayPower;
        if(group==null||group.invalid||group.revision!=group.epoch.revision){build(tile);group=tile.joinedDisplayPower;}
        return group.level(world);
    }
    private static boolean matching(TileEntityAnimatedScreenSelector a,TileEntityAnimatedScreenSelector b){
        if(a.activationMode()!=b.activationMode()||a.isRedstoneInverted()!=b.isRedstoneInverted()||!DisplayJoinGeometry.sameShape(a,b)||a.frameAppearance.displayFit!=b.frameAppearance.displayFit)return false;
        if(a.frameAppearance.split!=b.frameAppearance.split||a.frameAppearance.split&&(a.frameAppearance.splitTop!=b.frameAppearance.splitTop||a.frameAppearance.splitBottom!=b.frameAppearance.splitBottom))return false;
        for(int i=0;i<4;i++)if(a.activationTexture(i)!=b.activationTexture(i)||a.activationPlayback(i)!=b.activationPlayback(i)||a.activationSpeed(i)!=b.activationSpeed(i))return false;
        return true;
    }
    private static void build(TileEntityAnimatedScreenSelector first){
        World world=first.getWorld();IBlockState state=world.getBlockState(first.getPos());DisplayJoinGeometry.Basis basis=DisplayJoinGeometry.basis(first,state);
        Map<BlockPos,TileEntityAnimatedScreenSelector> members=new LinkedHashMap<>();ArrayDeque<BlockPos> pending=new ArrayDeque<>();members.put(first.getPos(),first);pending.add(first.getPos());
        while(!pending.isEmpty()&&members.size()<1024){BlockPos at=pending.removeFirst();for(BlockPos step:basis.steps){BlockPos next=at.add(step);if(members.containsKey(next)||!world.isBlockLoaded(next,false)||world.getBlockState(next)!=state)continue;TileEntity raw=world.getTileEntity(next);if(!(raw instanceof TileEntityAnimatedScreenSelector)||!matching(first,(TileEntityAnimatedScreenSelector)raw))continue;members.put(next,(TileEntityAnimatedScreenSelector)raw);pending.add(next);if(members.size()>=1024)break;}}
        if(members.size()>=1024){for(TileEntityAnimatedScreenSelector member:members.values())member.joinedDisplayPower=new Group(Collections.singletonList(member));return;}
        Map<Long,TileEntityAnimatedScreenSelector> cells=new HashMap<>();for(Map.Entry<BlockPos,TileEntityAnimatedScreenSelector> entry:members.entrySet())cells.put(basis.cell(entry.getKey()),entry.getValue());
        for(JoinedRectangles.Rect rect:JoinedRectangles.partition(cells.keySet())){
            List<TileEntityAnimatedScreenSelector> joined=new ArrayList<>();for(int y=0;y<rect.height;y++)for(int x=0;x<rect.width;x++)joined.add(cells.get(JoinedRectangles.cell(rect.x+x,rect.y+y)));
            Group group=new Group(joined);for(TileEntityAnimatedScreenSelector member:joined)member.joinedDisplayPower=group;
        }
    }
    private JoinedDisplayPower(){}
}

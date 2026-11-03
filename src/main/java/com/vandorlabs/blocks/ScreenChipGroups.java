package com.vandorlabs.blocks;

import com.vandorlabs.tiles.*;
import com.vandorlabs.world.WorldListener;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Event-invalidated physical reader lookup for a joined screen, on either side. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs")
public final class ScreenChipGroups {
    public static final class Group {
        public final List<TileEntityAnimatedScreenSelector> members;
        public final TileEntityAnimatedScreenSelector owner;
        public final TileEntityTokenSlot reader;
        public final int columns,rows,minColumn,minRow;
        public final AxisAlignedBB bounds;
        Group(List<TileEntityAnimatedScreenSelector> members){
            this.members=members;TileEntityAnimatedScreenSelector first=members.get(0);DisplayJoinGeometry.Basis basis=DisplayJoinGeometry.basis(first,first.getWorld().getBlockState(first.getPos()));
            int minX=Integer.MAX_VALUE,minY=minX,maxX=Integer.MIN_VALUE,maxY=maxX;TileEntityTokenSlot selected=null;AxisAlignedBB box=new AxisAlignedBB(first.getPos());
            for(TileEntityAnimatedScreenSelector tile:members){long cell=basis.cell(tile.getPos());int x=(int)(cell>>32),y=(int)cell;minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);box=box.union(new AxisAlignedBB(tile.getPos()));TileEntityTokenSlot candidate=ScreenChipSupport.attached(tile);if(candidate!=null&&(selected==null||selected.isEmpty()&&!candidate.isEmpty()))selected=candidate;}
            TileEntityAnimatedScreenSelector anchor=first;
            EnumFacing facing=first.getWorld().getBlockState(first.getPos()).getValue(BlockAnimatedScreenSelector.FACING);int anchorX=facing==EnumFacing.UP?maxX:minX,anchorY=facing==EnumFacing.DOWN?maxY:minY;
            for(TileEntityAnimatedScreenSelector tile:members){long cell=basis.cell(tile.getPos());if((int)(cell>>32)==anchorX&&(int)cell==anchorY){anchor=tile;break;}}
            owner=anchor;columns=maxX-minX+1;rows=maxY-minY+1;minColumn=minX;minRow=minY;bounds=box.grow(.1);reader=selected;
        }
    }
    private static final class Cache extends WorldListener {
        final Map<BlockPos,Group> groups=new HashMap<>();int revision=-1;
        @Override public void notifyBlockUpdate(World world,BlockPos at,IBlockState a,IBlockState b,int flags){if(groups.containsKey(at)){groups.clear();return;}for(EnumFacing side:EnumFacing.values())if(groups.containsKey(at.offset(side))){groups.clear();return;}}
        @Override public void markBlockRangeForRenderUpdate(int a,int b,int c,int d,int e,int f){for(BlockPos p:groups.keySet())if((long)p.getX()>=a-1L&&(long)p.getX()<=d+1L&&(long)p.getY()>=b-1L&&(long)p.getY()<=e+1L&&(long)p.getZ()>=c-1L&&(long)p.getZ()<=f+1L){groups.clear();return;}}
    }
    public static int builds;
    private static final Map<World,java.lang.ref.WeakReference<Cache>> CACHES=new WeakHashMap<>();
    public static synchronized Group group(TileEntityAnimatedScreenSelector tile){
        World world=tile.getWorld();if(!DisplayJoinGeometry.installed(tile)||tile.getBlockType().getClass()!=BlockAnimatedScreenSelector.class)return null;java.lang.ref.WeakReference<Cache> reference=CACHES.get(world);Cache cache=reference==null?null:reference.get();if(cache==null){cache=new Cache();CACHES.put(world,new java.lang.ref.WeakReference<>(cache));world.addEventListener(cache);}
        int revision=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).tileRevision:-1;if(cache.revision!=revision){cache.groups.clear();cache.revision=revision;}
        Group found=cache.groups.get(tile.getPos());if(found!=null)return found;
        builds++;Group group=new Group(DisplayEditGroups.members(tile));if(cache.groups.size()+group.members.size()>16384)cache.groups.clear();for(TileEntityAnimatedScreenSelector member:group.members)cache.groups.put(member.getPos(),group);return group;
    }
    public static boolean canEdit(net.minecraft.entity.player.EntityPlayer player,TileEntityAnimatedScreenSelector tile){World world=tile.getWorld();BlockPos at=tile.getPos();return world==player.world&&world.isBlockLoaded(at)&&!com.vandorlabs.gear.ArmTransactions.locked(world,at)&&player.canPlayerEdit(at,EnumFacing.UP,player.getHeldItemMainhand())&&world.isBlockModifiable(player,at);}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static synchronized void unload(net.minecraftforge.event.world.WorldEvent.Unload event){java.lang.ref.WeakReference<Cache> reference=CACHES.remove(event.getWorld());Cache cache=reference==null?null:reference.get();if(cache!=null)event.getWorld().removeEventListener(cache);}
    private ScreenChipGroups(){}
}

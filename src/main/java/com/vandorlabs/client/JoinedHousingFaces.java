package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Bounded, loaded-only texture planes; queried at chunk rebuild, never each render frame. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class JoinedHousingFaces {
    private static final Map<World,Cache> CACHES=new WeakHashMap<>();
    private static final class Cache extends WorldRenderListener {
        final Map<BlockPos,HousingFaceLayouts.Plane[]> planes=new LinkedHashMap<BlockPos,HousingFaceLayouts.Plane[]>(128,.75F,true){protected boolean removeEldestEntry(Map.Entry<BlockPos,HousingFaceLayouts.Plane[]> e){return size()>8192;}};
        boolean notifying;
        long revision=-1;int tileRevision=-1;
        @Override public void notifyBlockUpdate(World world,BlockPos pos,IBlockState before,IBlockState after,int flags){
            synchronized(JoinedHousingFaces.class){planes.clear();}
            if(!notifying&&(before.getBlock().getClass()==BlockProgrammableBlock.class||after.getBlock().getClass()==BlockProgrammableBlock.class)){
                notifying=true;try{world.markBlockRangeForRenderUpdate(pos.add(-16,-16,-16),pos.add(16,16,16));}finally{notifying=false;}
            }
        }
        @Override public void markBlockRangeForRenderUpdate(int x1,int y1,int z1,int x2,int y2,int z2){synchronized(JoinedHousingFaces.class){planes.clear();}}
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static synchronized void unload(net.minecraftforge.event.world.WorldEvent.Unload event){Cache cache=CACHES.remove(event.getWorld());if(cache!=null)event.getWorld().removeEventListener(cache);}
    public static synchronized HousingFaceLayouts sample(TileEntityAnimatedScreenSelector tile,int visible){
        World world=tile.getWorld();if(world==null||!world.isRemote||!JoinedHousingPolicy.joined(tile.getMainTextureLayout()))return HousingFaceLayouts.NONE;
        Cache cache=CACHES.get(world);if(cache==null){cache=new Cache();CACHES.put(world,cache);world.addEventListener(cache);}
        int tileRevision=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).tileRevision:-1;
        if(cache.revision!=GlassMaterials.revision()||cache.tileRevision!=tileRevision){cache.planes.clear();cache.revision=GlassMaterials.revision();cache.tileRevision=tileRevision;}
        HousingFaceLayouts.Plane[] result=new HousingFaceLayouts.Plane[6];boolean joined=false;
        for(EnumFacing face:EnumFacing.values())if((visible&(1<<face.getIndex()))!=0){
            HousingFaceLayouts.Plane[] stored=cache.planes.get(tile.getPos());
            if(stored==null||stored[face.getIndex()]==null)discover(world,tile,face,cache);
            stored=cache.planes.get(tile.getPos());if(stored!=null){result[face.getIndex()]=stored[face.getIndex()];HousingFaceLayouts.Plane p=result[face.getIndex()];joined|=p!=null&&(p.size(0)*p.size(1)*p.size(2)>1);}
        }
        return joined?new HousingFaceLayouts(result):HousingFaceLayouts.NONE;
    }
    private static int material(TileEntityAnimatedScreenSelector tile,EnumFacing face){
        EnumFacing facing=tile.getWorld().getBlockState(tile.getPos()).getValue(BlockAnimatedScreenSelector.FACING);int rotation=((int)(180-facing.getHorizontalAngle())/90)&3;
        EnumFacing local=face;if(face.getAxis()!=EnumFacing.Axis.Y)for(int i=0;i<rotation;i++)local=local.rotateY();
        return tile.getFaceTextures().texture(local.getIndex(),tile.getHousingTexture());
    }
    private static boolean matches(World world,TileEntityAnimatedScreenSelector root,BlockPos at,EnumFacing face){
        if(!world.isBlockLoaded(at))return false;IBlockState state=world.getBlockState(at);
        if(state.getBlock().getClass()!=BlockProgrammableBlock.class)return false;
        EnumFacing facing=state.getValue(BlockAnimatedScreenSelector.FACING),rootFacing=world.getBlockState(root.getPos()).getValue(BlockAnimatedScreenSelector.FACING);
        if(facing!=rootFacing)return false;
        TileEntity raw=world.getTileEntity(at);if(!(raw instanceof TileEntityAnimatedScreenSelector))return false;
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)raw;
        BlockPos neighbor=at.offset(face);if(world.isBlockLoaded(neighbor)&&world.getBlockState(neighbor).isOpaqueCube()&&world.getBlockState(neighbor).isFullCube())return false;
        int layout=tile.getMainTextureLayout(),rootLayout=root.getMainTextureLayout();if(layout!=rootLayout)return false;
        int selected=material(tile,face),rootMaterial=material(root,face);
        return JoinedHousingPolicy.matches(rootFacing.getIndex(),rootLayout,rootMaterial,facing.getIndex(),layout,selected);
    }
    private static void discover(World world,TileEntityAnimatedScreenSelector root,EnumFacing face,Cache cache){
        BlockPos origin=root.getPos();
        JoinedHousingPolicy.Result<HousingFaceLayouts.Plane> result=JoinedHousingPolicy.discover(
                new JoinedHousingPolicy.Cell(origin.getX(),origin.getY(),origin.getZ()),face.getIndex(),
                cell->matches(world,root,new BlockPos(cell.x,cell.y,cell.z),face),HousingFaceLayouts.Plane::new);
        for(Map.Entry<JoinedHousingPolicy.Cell,HousingFaceLayouts.Plane> entry:result.planes.entrySet()){
            JoinedHousingPolicy.Cell cell=entry.getKey();BlockPos at=new BlockPos(cell.x,cell.y,cell.z);
            cache.planes.computeIfAbsent(at,k->new HousingFaceLayouts.Plane[6])[face.getIndex()]=entry.getValue();
        }
    }
}

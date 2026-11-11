package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded wall geometry cache; porthole slice identity also tracks distant group changes. */
public final class DiagonalWallMeshCache {
    static final int MAX_ENTRIES=4096,MAX_VERTICES=524288;
    static final long MAX_BYTES=16L*1024*1024; // Includes shared lazy vertex formats and owner/index overhead.
    private static final Map<World,WorldCache> WORLDS=new WeakHashMap<>();
    private DiagonalWallMeshCache() { }

    static StaticSurfaceMesh get(TileEntityAnimatedScreenSelector tile,IBlockState state,
            TextureAtlasSprite wall,TextureAtlasSprite metal) {
        return get(tile,state,wall,metal,null);
    }
    static StaticSurfaceMesh get(TileEntityAnimatedScreenSelector tile,IBlockState state,
            TextureAtlasSprite wall,TextureAtlasSprite metal,PortholeHex.Slice slice) {
        return entry(tile,state,wall,metal,slice).mesh;
    }
    static StaticSurfaceMesh glass(TileEntityAnimatedScreenSelector tile,IBlockState state,
            TextureAtlasSprite wall,TextureAtlasSprite metal,PortholeHex.Slice slice) {
        return entry(tile,state,wall,metal,slice).glass;
    }
    static StaticSurfaceMesh frame(TileEntityAnimatedScreenSelector tile,IBlockState state,TextureAtlasSprite glass) {
        return entry(tile,state,glass,glass,null).frame;
    }
    private static Entry entry(TileEntityAnimatedScreenSelector tile,IBlockState state,
            TextureAtlasSprite wall,TextureAtlasSprite metal,PortholeHex.Slice slice) {
        World world=tile.getWorld();WorldCache cache=WORLDS.get(world);
        if(cache==null) {
            cache=new WorldCache();WORLDS.put(world,cache);world.addEventListener(cache);
        }
        int settings=(tile.isDiagonalHalfHeight()?1:0)|(tile.isDiagonalFullWidth()?2:0)|(tile.getDiagonalFill()<<2)|(tile.isGlassFramed()?128:0);
        Entry entry=cache.entries.get(tile.getPos());
        if(entry!=null && entry.tile.get()==tile && entry.state==state && entry.settings==settings
                && entry.frameFront==tile.frameAppearance.front && entry.frameInterior==tile.frameAppearance.interior && entry.frameExterior==tile.frameAppearance.exteriorChoice()
                && entry.wall==wall && entry.metal==metal && entry.slice==slice)return entry;
        cache.remove(tile.getPos());
        StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
        TEAnimatedScreenSelector.drawConfiguredWall(capture,tile,state,wall,metal,slice);
        StaticSurfaceMesh mesh=capture.finish(),glass=null,frame=null;
        boolean hideLeft=false,hideRight=false,hideFront=false,hideBack=false;
        if(state.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalGlass) {
            net.minecraft.util.EnumFacing right=state.getValue(com.vandorlabs.blocks.BlockProgrammableWall.FACING).rotateY();
            for(int side=0;side<2;side++) {
                BlockPos next=tile.getPos().offset(side==0?right.getOpposite():right);
                if(!world.isBlockLoaded(next))continue;
                IBlockState other=world.getBlockState(next);
                if(other.getBlock()==state.getBlock()
                        && com.vandorlabs.blocks.BlockProgrammableWall.geometry(world,next)==com.vandorlabs.blocks.BlockProgrammableWall.geometry(world,tile.getPos())
                        && com.vandorlabs.blocks.BlockProgrammableWall.fill(world,next)==tile.getDiagonalFill()
                        && com.vandorlabs.blocks.DiagonalPanelGeometry.samePlane(tile.getPos(),state,next,other,
                                com.vandorlabs.blocks.BlockProgrammableWall.geometry(world,tile.getPos())))
                {
                    mesh=mesh.withoutPlane(0,side==0?0:16);
                    if(side==0)hideLeft=true;else hideRight=true;
                }
            }
        }
        if(state.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalGlass && !tile.isDiagonalHalfHeight()) {
            net.minecraft.util.EnumFacing facing=state.getValue(com.vandorlabs.blocks.BlockProgrammableWall.FACING);
            net.minecraft.util.EnumFacing[] directions={facing.rotateYCCW(),facing.rotateY(),facing,facing.getOpposite()};
            for(int side=0;side<4;side++) {
                BlockPos next=tile.getPos().offset(directions[side]);if(!world.isBlockLoaded(next))continue;
                IBlockState other=world.getBlockState(next);
                if(other.getBlock()!=state.getBlock()
                        || other.getValue(com.vandorlabs.blocks.BlockProgrammableWall.FACING).getAxis()==facing.getAxis()
                        || other.getValue(com.vandorlabs.blocks.BlockProgrammableWall.INVERTED)!=state.getValue(com.vandorlabs.blocks.BlockProgrammableWall.INVERTED)
                        || com.vandorlabs.blocks.BlockProgrammableWall.geometry(world,next)!=com.vandorlabs.blocks.BlockProgrammableWall.geometry(world,tile.getPos())
                        || com.vandorlabs.blocks.BlockProgrammableWall.fill(world,next)!=tile.getDiagonalFill())continue;
                mesh=mesh.withoutPlane(side<2?0:2,(side&1)==0?0:16);
                if(side==0)hideLeft=true;else if(side==1)hideRight=true;else if(side==2)hideFront=true;else hideBack=true;
            }
        }
        if(state.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalGlass && tile.isGlassFramed()) {
            TextureAtlasSprite rail=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite(tile.frameAppearance.rail("vandorlabs:blocks/space_doors/medium/double_frame_metal"));
            TextureAtlasSprite interior=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(tile.frameAppearance.interior("vandorlabs:blocks/programmable_glass/metal_side"));
            TextureAtlasSprite exterior=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(tile.frameAppearance.exterior("vandorlabs:blocks/programmable_glass/metal_side"));
            frame=mesh.outlineFrame(rail,wall,interior,exterior,hideLeft,hideRight,hideFront,hideBack,
                    com.vandorlabs.blocks.DiagonalPanelGeometry.coveredEnd(world,tile.getPos(),state,false),
                    com.vandorlabs.blocks.DiagonalPanelGeometry.coveredEnd(world,tile.getPos(),state,true),
                    tile.isDiagonalHalfHeight()?2:1);
        }
        if(slice!=null && !TEAnimatedScreenSelector.solidDiagonalPortholeJunction(tile,state)) {
            capture=StaticSurfaceMesh.capture();
            TEAnimatedScreenSelector.drawPortholeGlassGeometry(capture,slice,
                    TEAnimatedScreenSelector.portholeMesh(tile,state));
            glass=capture.finish();
        }
        mesh=cache.acquire(mesh);glass=cache.acquire(glass);frame=cache.acquire(frame);
        Entry built=new Entry(tile,state,settings,wall,metal,mesh,glass,frame,slice);
        if(built.vertices()<=MAX_VERTICES) {
            cache.entries.put(tile.getPos().toImmutable(),built);
            cache.vertices+=built.vertices();cache.bytes+=512;
            while(cache.entries.size()>MAX_ENTRIES || cache.bytes>MAX_BYTES)
                cache.remove(cache.entries.keySet().iterator().next());
        }else {cache.release(mesh);cache.release(glass);cache.release(frame);}
        return built;
    }

    static void invalidate(World world,int x1,int y1,int z1,int x2,int y2,int z2) {
        WorldCache cache=WORLDS.get(world);if(cache!=null)cache.invalidate(x1,y1,z1,x2,y2,z2);
    }
    static int entryCount(World world) { WorldCache cache=WORLDS.get(world);return cache==null?0:cache.entries.size(); }
    static boolean ownerCached(World world,BlockPos pos){WorldCache cache=WORLDS.get(world);return cache!=null&&cache.entries.containsKey(pos);}
    static long retainedBytes(World world) { WorldCache cache=WORLDS.get(world);return cache==null?0:cache.bytes; }
    static int sharedCount(World world) { WorldCache cache=WORLDS.get(world);return cache==null?0:cache.shared.size(); }
    static int vertexCount(World world) { WorldCache cache=WORLDS.get(world);return cache==null?0:cache.vertices; }
    private static void unload(World world) {
        WorldCache cache=WORLDS.remove(world);if(cache!=null)world.removeEventListener(cache);
    }
    public static void clear() {
        for(Map.Entry<World,WorldCache> entry:WORLDS.entrySet())entry.getKey().removeEventListener(entry.getValue());
        WORLDS.clear();GlassFrameMesh.clear();
    }

    private static final class Entry {
        final WeakReference<TileEntityAnimatedScreenSelector> tile;
        final IBlockState state;
        final int settings,frameFront,frameInterior,frameExterior;
        final TextureAtlasSprite wall,metal;
        final StaticSurfaceMesh mesh,glass,frame;
        final PortholeHex.Slice slice;
        int vertices(){return mesh.vertexCount()+(glass==null?0:glass.vertexCount())+(frame==null?0:frame.vertexCount());}
        Entry(TileEntityAnimatedScreenSelector tile,IBlockState state,int settings,
                TextureAtlasSprite wall,TextureAtlasSprite metal,StaticSurfaceMesh mesh,StaticSurfaceMesh glass,StaticSurfaceMesh frame,PortholeHex.Slice slice) {
            this.tile=new WeakReference<>(tile);this.state=state;this.settings=settings;
            this.frameFront=tile.frameAppearance.front;this.frameInterior=tile.frameAppearance.interior;this.frameExterior=tile.frameAppearance.exteriorChoice();
            this.wall=wall;this.metal=metal;this.mesh=mesh;this.glass=glass;this.frame=frame;this.slice=slice;
        }
    }
    /** Exact emitted float records are the sharing key, including atlas UVs and normals.
     * Local contacts and authored clipping are resolved before interning. */
    private static final class MeshKey {
        final StaticSurfaceMesh mesh;final int hash;
        MeshKey(StaticSurfaceMesh mesh){this.mesh=mesh;hash=mesh.geometryHash();}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return other instanceof MeshKey&&mesh.sameGeometry(((MeshKey)other).mesh);}
    }
    private static final class SharedMesh {
        final StaticSurfaceMesh mesh;int references;
        SharedMesh(StaticSurfaceMesh mesh){this.mesh=mesh;}
    }
    private static final class WorldCache extends WorldRenderListener {
        final LinkedHashMap<BlockPos,Entry> entries=new LinkedHashMap<>(64,.75F,true);
        final Map<MeshKey,SharedMesh> shared=new HashMap<>();
        int vertices;long bytes;
        StaticSurfaceMesh acquire(StaticSurfaceMesh mesh){
            if(mesh==null)return null;MeshKey key=new MeshKey(mesh);SharedMesh found=shared.get(key);
            if(found==null){found=new SharedMesh(mesh);shared.put(key,found);bytes+=192+mesh.cacheRetainedBytes();}
            found.references++;return found.mesh;
        }
        void release(StaticSurfaceMesh mesh){
            if(mesh==null)return;MeshKey key=new MeshKey(mesh);SharedMesh found=shared.get(key);
            if(found==null)throw new IllegalStateException("Untracked wall mesh");
            if(--found.references==0){shared.remove(key);bytes-=192+mesh.cacheRetainedBytes();}
        }
        void removed(Entry entry){vertices-=entry.vertices();bytes-=512;release(entry.mesh);release(entry.glass);release(entry.frame);}
        void remove(BlockPos pos) { Entry removed=entries.remove(pos);if(removed!=null)removed(removed); }
        void invalidate(int x1,int y1,int z1,int x2,int y2,int z2) {
            if(entries.isEmpty())return;
            int minX=Math.min(x1,x2)-1,maxX=Math.max(x1,x2)+1;
            int minY=Math.min(y1,y2)-1,maxY=Math.max(y1,y2)+1;
            int minZ=Math.min(z1,z2)-1,maxZ=Math.max(z1,z2)+1;
            // Block updates touch 27 owners; geometry-setting updates cover
            // at most 125. Both stay bounded during large Duplifier applies.
            if(maxX-minX<=4 && maxY-minY<=4 && maxZ-minZ<=4) {
                BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
                for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++)remove(pos.setPos(x,y,z));
                return;
            }
            Iterator<Map.Entry<BlockPos,Entry>> it=entries.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<BlockPos,Entry> entry=it.next();BlockPos pos=entry.getKey();
                if(pos.getX()>=minX && pos.getX()<=maxX && pos.getY()>=minY && pos.getY()<=maxY
                        && pos.getZ()>=minZ && pos.getZ()<=maxZ) { removed(entry.getValue());it.remove(); }
            }
        }
        @Override public void notifyBlockUpdate(World world,BlockPos pos,IBlockState before,IBlockState after,int flags) {
            invalidate(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ());
        }
        @Override public void markBlockRangeForRenderUpdate(int x1,int y1,int z1,int x2,int y2,int z2) { invalidate(x1,y1,z1,x2,y2,z2); }

    }
    public static final class Events {
        @SubscribeEvent public void worldUnloaded(WorldEvent.Unload event) {
            if(event.getWorld().isRemote) {
                unload(event.getWorld());
                GlassSurfaceGroups.unload(event.getWorld());
                TEAnimatedScreenSelector.unloadGroups(event.getWorld());
            }
        }
        @SubscribeEvent public void chunkLoaded(ChunkEvent.Load event) { changed(event); }
        @SubscribeEvent public void chunkUnloaded(ChunkEvent.Unload event) { changed(event); }
        private void changed(ChunkEvent event) {
            if(!event.getWorld().isRemote)return;
            int x=event.getChunk().x*16,z=event.getChunk().z*16;
            GlassSurfaceGroups.invalidate(event.getWorld(),x,0,z,x+15,255,z+15);
            invalidate(event.getWorld(),x,0,z,x+15,255,z+15);
        }
    }
}

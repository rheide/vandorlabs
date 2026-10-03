package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.items.ProgrammableSettings;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;

/** Real vertex buffers and world event listeners, without a GL context. */
final class CommonRenderChecks {
    static void run() {
        walls(); wallFamilies(); doors(); settings(); distance(); clipping(); RampRenderChecks.run();
        System.out.println("PASS: cached wall/door vertex equivalence, live lightmaps, invalidation and limits; no-op settings; distant gear/ramp policy; clipping equivalence");
    }
    private static TextureAtlasSprite sprite(String name) {
        TextureAtlasSprite sprite=new TextureAtlasSprite(name){};
        sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(256,256,32,48,false);
        return sprite;
    }
    private static byte[] bytes(Consumer<BufferBuilder> draw) {
        BufferBuilder buffer=new BufferBuilder(4096);buffer.begin(7,BlockSurfaceFormat.get());
        draw.accept(buffer);buffer.finishDrawing();
        byte[] result=new byte[buffer.getVertexCount()*BlockSurfaceFormat.get().getNextOffset()];
        ByteBuffer data=buffer.getByteBuffer().duplicate();data.position(0);data.get(result);return result;
    }
    private static void put(NonRenderingChecks.MemoryWorld world,BlockPos pos,IBlockState state) {
        IBlockState before=world.getBlockState(pos);world.setBlockState(pos,state,2);
        world.notifyBlockUpdate(pos,before,state,2);
    }
    private static void wallFamilies() {
        TextureAtlasSprite wall=sprite("wall"),trim=sprite("trim");
        BlockPos pos=new BlockPos(15,100,15);
        float oldX=OpenGlHelper.lastBrightnessX,oldY=OpenGlHelper.lastBrightnessY;
        try {
            for(BlockProgrammableWall block:new BlockProgrammableWall[]{
                    new BlockProgrammableWall("audit_plain",BlockProgrammableWall.Shape.PLAIN),
                    new BlockProgrammableWall("audit_porthole",BlockProgrammableWall.Shape.PORTHOLE),
                    new BlockProgrammablePortholeBlock(),
                    new BlockProgrammableWall("audit_diagonal_porthole",BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE)})
                for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int mode=0;mode<(block.isDiagonalShape()?3:1);mode++) {
                    NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
                    IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing);
                    put(world,pos,state);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
                    tile.setDiagonalGeometry(mode,0);
                    PortholeHex.Slice slice=block.isPortholeShape()?new PortholeHex(1,1,PortholeHex.ROUND).slice(0,0):null;
                    OpenGlHelper.lastBrightnessX=80;OpenGlHelper.lastBrightnessY=192;
                    StaticSurfaceMesh mesh=DiagonalWallMeshCache.get(tile,state,wall,trim,slice);
                    final PortholeHex.Slice current=slice;
                    require(Arrays.equals(bytes(b->TEAnimatedScreenSelector.drawConfiguredWall(b,tile,state,wall,trim,current)),
                            bytes(b->mesh.draw(b,192,80))),"wall family cache differs from direct vertices");
                    require(mesh==DiagonalWallMeshCache.get(tile,state,wall,trim,slice),"unchanged wall family rebuilt");
                    OpenGlHelper.lastBrightnessX=16;OpenGlHelper.lastBrightnessY=240;
                    world.notifyLightSet(pos);
                    require(mesh==DiagonalWallMeshCache.get(tile,state,wall,trim,slice),"lighting evicted wall family geometry");
                    require(Arrays.equals(bytes(b->TEAnimatedScreenSelector.drawConfiguredWall(b,tile,state,wall,trim,current)),
                            bytes(b->mesh.draw(b,240,16))),"wall family cached stale lightmap");
                    if(slice!=null) {
                        StaticSurfaceMesh glass=DiagonalWallMeshCache.glass(tile,state,wall,trim,slice);
                        require(glass==DiagonalWallMeshCache.glass(tile,state,wall,trim,slice),"glass geometry rebuilt every frame");
                        require(Arrays.equals(bytes(b->TEAnimatedScreenSelector.drawPortholeGlassGeometry(b,current,
                                        TEAnimatedScreenSelector.portholeMesh(tile,state))),bytes(b->glass.draw(b,240,16))),
                                "glass cache differs from direct vertices");
                        PortholeHex.Slice expanded=new PortholeHex(2,1,PortholeHex.ROUND).slice(0,0);
                        require(mesh!=DiagonalWallMeshCache.get(tile,state,wall,trim,expanded),"distant group-size change left stale wall");
                    }
                    new DiagonalWallMeshCache.Events().worldUnloaded(new WorldEvent.Unload(world));
                }
        } finally {OpenGlHelper.lastBrightnessX=oldX;OpenGlHelper.lastBrightnessY=oldY;}
        System.out.println("PASS: all wall-family caches retain live lighting, track porthole group changes and reuse glass geometry");
    }
    private static void walls() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        TextureAtlasSprite wall=sprite("wall"),trim=sprite("trim");BlockPos pos=new BlockPos(15,100,15);
        BlockProgrammableWall block=(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;
        float oldX=OpenGlHelper.lastBrightnessX,oldY=OpenGlHelper.lastBrightnessY;
        try {
            for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})
                for(int mode=0;mode<3;mode++)for(int fill=0;fill<4;fill++)for(int neighbor=0;neighbor<4;neighbor++) {
                    world.clear();IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,face)
                            .withProperty(BlockProgrammableWall.INVERTED,inverted);
                    put(world,pos,state);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
                    tile.setDiagonalGeometry(mode,fill);
                    if(neighbor==1)put(world,pos.offset(face),Blocks.STONE.getDefaultState());
                    if(neighbor==2) {
                        BlockPos n=pos.offset(face.getOpposite());put(world,n,state.withProperty(BlockProgrammableWall.INVERTED,!inverted));
                        ((TileEntityAnimatedScreenSelector)world.getTileEntity(n)).setDiagonalGeometry(mode,fill);
                    }
                    if(neighbor==3) {
                        BlockPos n=pos.up();put(world,n,state);
                        ((TileEntityAnimatedScreenSelector)world.getTileEntity(n)).setDiagonalGeometry(mode,fill);
                    }
                    OpenGlHelper.lastBrightnessX=80;OpenGlHelper.lastBrightnessY=192;
                    byte[] direct=bytes(b->TEAnimatedScreenSelector.drawConfiguredDiagonalWall(b,tile,state,wall,trim));
                    StaticSurfaceMesh mesh=DiagonalWallMeshCache.get(tile,state,wall,trim);
                    require(Arrays.equals(direct,bytes(b->mesh.draw(b,192,80))),"cached wall vertex mismatch");
                    require(mesh==DiagonalWallMeshCache.get(tile,state,wall,trim),"unchanged wall rebuilt");
                    OpenGlHelper.lastBrightnessX=16;OpenGlHelper.lastBrightnessY=240;
                    require(Arrays.equals(bytes(b->TEAnimatedScreenSelector.drawConfiguredDiagonalWall(b,tile,state,wall,trim)),
                            bytes(b->mesh.draw(b,240,16))),"wall cached stale lightmap");
                }
            world.clear();put(world,pos,block.getDefaultState());TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
            IBlockState state=world.getBlockState(pos);StaticSurfaceMesh mesh=DiagonalWallMeshCache.get(tile,state,wall,trim);
            world.notifyBlockUpdate(pos.add(5,0,0),state,state,2);
            require(mesh==DiagonalWallMeshCache.get(tile,state,wall,trim),"distant update evicted wall");
            world.notifyLightSet(pos);
            require(mesh==DiagonalWallMeshCache.get(tile,state,wall,trim),"light update rebuilt geometry");
            put(world,pos.up().south(),state);
            require(mesh!=DiagonalWallMeshCache.get(tile,state,wall,trim),"diagonal row update left stale geometry");
            mesh=DiagonalWallMeshCache.get(tile,state,wall,trim);
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(pos.up().south())).setDiagonalGeometry(2,0);
            require(mesh!=DiagonalWallMeshCache.get(tile,state,wall,trim),"neighbor tile settings left stale geometry");
            mesh=DiagonalWallMeshCache.get(tile,state,wall,trim);
            world.markBlockRangeForRenderUpdate(pos.add(-1,-1,-1),pos.add(1,1,1));
            require(mesh!=DiagonalWallMeshCache.get(tile,state,wall,trim),"geometry neighborhood range left stale wall");
            NonRenderingChecks.MemoryWorld otherWorld=new NonRenderingChecks.MemoryWorld(true);
            put(otherWorld,pos,state);
            TileEntityAnimatedScreenSelector otherTile=(TileEntityAnimatedScreenSelector)otherWorld.getTileEntity(pos);
            mesh=DiagonalWallMeshCache.get(tile,state,wall,trim);
            require(mesh!=DiagonalWallMeshCache.get(otherTile,state,wall,trim),"shared geometry across worlds");
            new DiagonalWallMeshCache.Events().worldUnloaded(new WorldEvent.Unload(otherWorld));
            require(mesh==DiagonalWallMeshCache.get(tile,state,wall,trim),"unloading another world evicted current mesh");
            NBTTagCompound tag=tile.getUpdateTag();tag.setInteger("DiagonalFill",3);
            tile.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,tag));
            require(mesh!=DiagonalWallMeshCache.get(tile,state,wall,trim),"packet settings left stale geometry");
            mesh=DiagonalWallMeshCache.get(tile,state,wall,trim);
            require(mesh!=DiagonalWallMeshCache.get(tile,state,sprite("new atlas wall"),trim),"atlas sprite replacement left stale UVs");
            new DiagonalWallMeshCache.Events().chunkUnloaded(new ChunkEvent.Unload(new Chunk(world,1,0)));
            require(DiagonalWallMeshCache.entryCount(world)==0,"adjacent chunk unload left stale wall");
            DiagonalWallMeshCache.get(tile,state,wall,trim);
            new DiagonalWallMeshCache.Events().chunkLoaded(new ChunkEvent.Load(new Chunk(world,1,0)));
            require(DiagonalWallMeshCache.entryCount(world)==0,"adjacent chunk load left stale wall");
            DiagonalWallMeshCache.get(tile,state,wall,trim);
            new DiagonalWallMeshCache.Events().worldUnloaded(new WorldEvent.Unload(world));
            require(DiagonalWallMeshCache.entryCount(world)==0,"unloaded world retained geometry");
            DiagonalWallMeshCache.clear();world.clear();
            StaticSurfaceMesh oldest=null;TileEntityAnimatedScreenSelector first=null;
            for(int i=0;i<DiagonalWallMeshCache.MAX_ENTRIES+1;i++) {
                BlockPos p=new BlockPos(i*3,100,0);put(world,p,state);
                TileEntityAnimatedScreenSelector t=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                StaticSurfaceMesh current=DiagonalWallMeshCache.get(t,state,wall,trim);
                if(i==0){oldest=current;first=t;}
            }
            require(DiagonalWallMeshCache.entryCount(world)<=DiagonalWallMeshCache.MAX_ENTRIES
                    && DiagonalWallMeshCache.vertexCount(world)<=DiagonalWallMeshCache.MAX_VERTICES,"unbounded wall cache");
            require(oldest!=DiagonalWallMeshCache.get(first,state,wall,trim),"wall cache failed to evict oldest entry");
            DiagonalWallMeshCache.clear();require(DiagonalWallMeshCache.entryCount(world)==0,"resource clear retained wall geometry");
            DiagonalWallMeshCache.get(first,state,wall,trim);world.markBlockRangeForRenderUpdate(first.getPos(),first.getPos());
            require(DiagonalWallMeshCache.entryCount(world)==0,"listener not restored after resource clear");
        } finally {DiagonalWallMeshCache.clear();OpenGlHelper.lastBrightnessX=oldX;OpenGlHelper.lastBrightnessY=oldY;}
    }
    private static void doors() {
        TextureAtlasSprite sprite=sprite("door"),hinge=sprite("door/hinge");
        for(double inset:new double[]{0,1/16D})for(boolean tiled:new boolean[]{false,true}) {
            double[] bounds={inset,inset,12.24/16,1-inset,2-inset,14.24/16};
            StaticSurfaceMesh mesh=SelectedDoorFaceCache.build(bounds,sprite,4,tiled);
            for(int light:new int[]{(192<<16)|80,(240<<16)|16})
                require(Arrays.equals(bytes(b->SelectedDoorFaceCache.emit(b,bounds,sprite,4,tiled,light)),
                        bytes(b->mesh.draw(b,light>>>16,light&65535))),"cached door Fit/Tile vertex mismatch");
        }
        final List<BakedQuad> originals=new ArrayList<>();
        for(EnumFacing face:EnumFacing.values()) {
            int[] data=new int[28];for(int i=0;i<4;i++){data[7*i]=Float.floatToIntBits(i%2);data[7*i+1]=Float.floatToIntBits(i/2*2);data[7*i+2]=Float.floatToIntBits(.875F);}
            originals.add(new BakedQuad(data,-1,face,sprite));
            originals.add(new BakedQuad(data.clone(),-1,face,hinge));
        }
        final int[] calls={0};
        IBakedModel model=new IBakedModel() {
            public List<BakedQuad> getQuads(IBlockState s,EnumFacing f,long seed){calls[0]++;return originals;}
            public boolean isAmbientOcclusion(){return true;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}
            public TextureAtlasSprite getParticleTexture(){return sprite;}public ItemCameraTransforms getItemCameraTransforms(){return ItemCameraTransforms.DEFAULT;}
            public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
        };
        SelectedDoorGeometry selected=new SelectedDoorGeometry(model);int built=calls[0];
        for(int side=-1;side<6;side++) {
            EnumFacing face=side<0?null:EnumFacing.getFront(side);
            List<BakedQuad> expected=new ArrayList<>();for(BakedQuad q:originals)
                if(q.getSprite()==hinge || q.getFace().getAxis()!=EnumFacing.Axis.Z)expected.add(q);
            require(selected.getQuads(null,face,42).equals(expected),"door filtering changed");
            require(selected.getQuads(null,face,42)==selected.getQuads(null,face,42),"door quads recreated");
        }
        require(calls[0]==built,"door queries original model each frame");
    }
    private static void settings() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos p=new BlockPos(10,100,10);
        for(IBlockState state:new IBlockState[]{ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),ModBlocks.PROGRAMMABLE_DIAGONAL_WALL.getDefaultState()}) {
            put(world,p,state);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
            tile.setDiagonalGeometry(2,3);tile.setHousingTexture(4);tile.setCeilingPosition(1);tile.setWallPosition(1);
            NBTTagCompound copy=ProgrammableSettings.capture(world,p);
            long revision=tile.getSettingsRevision();world.updates=world.dirty=world.renderUpdates=world.lightChecks=0;
            require(ProgrammableSettings.apply(world,p,copy),"identical settings no longer applicable");
            tile.setSelectedScreen(tile.getSelectedScreen());tile.setInputPanel(tile.getInputPanel());tile.setSecondaryInputPanel(tile.getSecondaryInputPanel());
            tile.setRedstoneEnabled(tile.isRedstoneEnabled());tile.setDisplayMode(tile.getDisplayMode());tile.setFramed(tile.isFramed());
            tile.setAnimationSpeedIndex(tile.getAnimationSpeedIndex());tile.setSmallInput(tile.isSmallInput());tile.setCeilingPosition(1);
            tile.setCeilingMounted(tile.isCeilingMounted());tile.setWallPosition(1);tile.setSurfaceTexture(0,tile.getSurfaceTexture(0));
            tile.setDiagonalGeometry(2,3);
            require(revision==tile.getSettingsRevision() && world.updates==0 && world.dirty==0 && world.renderUpdates==0 && world.lightChecks==0,
                    "identical settings dirtied/rebuilt a block");
            copy.setInteger(ProgrammableSettings.WALL_TEXTURE,2);
            require(ProgrammableSettings.apply(world,p,copy) && tile.getHousingTexture()==2 && tile.getSettingsRevision()>revision
                    && world.updates>0 && world.dirty>0,"changed finish not persisted or synchronized");
        }
    }
    private static void distance() {
        require(new TileEntityLandingGear().getMaxRenderDistanceSquared()==Double.MAX_VALUE,"gear retained 64-block cutoff");
        require(new TileEntityControlledRamp().getMaxRenderDistanceSquared()==Double.MAX_VALUE,"ramp retained 64-block cutoff");
        require(new TileEntityAnimatedScreenSelector().getMaxRenderDistanceSquared()==4096,"unrelated tiles changed distance");
        for(int size=0;size<5;size++) {
            TileEntityLandingGear gear=new TileEntityLandingGear();gear.setPos(new BlockPos(10,100,10));
            NBTTagCompound tag=new NBTTagCompound();tag.setInteger("GearSize",size);gear.readFromNBT(tag);gear.setPos(new BlockPos(10,100,10));
            require(gear.getRenderBoundingBox().maxY==101 && gear.getRenderBoundingBox().minY<=96,"gear frustum bounds changed");
        }
    }
    private static void clipping() {
        Random random=new Random(123);
        for(int i=0;i<2000;i++) {
            double x=random.nextInt(9)-4,y=random.nextInt(9)-4,z=random.nextInt(9)-4;
            double[][] vertices={{x,y,z,0,0},{x+2,y,z,1,0},{x+2,y+2,z,1,1},{x,y+2,z,0,1}};
            double[] bounds=i%3==0?null:i%3==1?new double[]{-2,-2,-2,3,3,3}:new double[]{-2,-2,-2,3,3,3,-1,-1,-1,1,1,1};
            List<double[]> expected=LegacyDiagonalClip.quads(bounds,vertices),actual=com.vandorlabs.render.DiagonalMeshClip.quads(bounds,vertices);
            require(expected.size()==actual.size(),"clipping changed vertex count");
            for(int v=0;v<expected.size();v++)require(Arrays.equals(expected.get(v),actual.get(v)),"clipping changed tessellation/attributes");
        }
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

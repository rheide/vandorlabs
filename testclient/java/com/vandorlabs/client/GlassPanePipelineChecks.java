package com.vandorlabs.client;

import com.vandorlabs.CommonProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.VertexBufferUploader;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.relauncher.CoreModManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import sun.misc.Unsafe;

/**
 * Actual RenderChunk worker compilation, ordinary extended-state/model dispatch,
 * VBO upload and production ownership/admission checks in a headless GL context.
 * The isolated world/cache supply deterministic cells, not prebuilt vertex data.
 * This does not substitute for OptiFine or shader-pack acceptance in a live world.
 */
public final class GlassPanePipelineChecks {
    static final int WIDTH=256,HEIGHT=256,LIGHT=(128<<16)|32;
    static Unsafe unsafe;
    static Minecraft mc;
    static Fixture fixture;
    static Sprite sourceSprite,nativeSprite,frameSprite;
    static int atlasTexture,lightTexture,checks;
    static BlockProgrammableGlass block;
    static BufferedImage gold;
    static final BlockPos ORIGIN=BlockPos.ORIGIN;
    static final class Sprite extends TextureAtlasSprite {
        Sprite(String name,int size,int x,boolean translucent){
            super(name);setIconWidth(size);setIconHeight(size);initSprite(size*3,size,x,0,false);
            int[] pixels=new int[size*size];java.util.Arrays.fill(pixels,translucent?0x80ffffff:0xffffffff);
            List<int[][]> frames=new ArrayList<>();frames.add(new int[][]{pixels});setFramesTextureData(frames);
        }
    }
    static final class SceneWorld extends WorldClient {
        Map<BlockPos,IBlockState> states;Map<BlockPos,TileEntity> tiles;int light;
        SceneWorld(){super(null,null,0,net.minecraft.world.EnumDifficulty.NORMAL,null);}
        @Override public IBlockState getBlockState(BlockPos pos){return states.getOrDefault(pos,Blocks.AIR.getDefaultState());}
        @Override public TileEntity getTileEntity(BlockPos pos){return tiles.get(pos);}
        @Override public boolean isBlockLoaded(BlockPos pos){return true;}
        @Override public boolean isBlockLoaded(BlockPos pos,boolean empty){return true;}
        @Override public int getCombinedLight(BlockPos pos,int minimum){return (light&0xffff0000)|Math.max(light&65535,minimum*16);}
        @Override public void markChunkDirty(BlockPos pos,TileEntity tile){}
        @Override public boolean checkLight(BlockPos pos){return true;}
        @Override public void updateComparatorOutputLevel(BlockPos pos,net.minecraft.block.Block block){}
        @Override public long getTotalWorldTime(){return 1000;}
        @Override public Biome getBiome(BlockPos pos){return Biomes.PLAINS;}
        @Override public WorldType getWorldType(){return WorldType.DEFAULT;}
    }
    static final class Cache extends ChunkCache {
        SceneWorld source;
        Cache(){super(null,ORIGIN,ORIGIN,0);}
        @Override public boolean isEmpty(){return false;}
        @Override public IBlockState getBlockState(BlockPos pos){return source.getBlockState(pos);}
        @Override public TileEntity getTileEntity(BlockPos pos){return source.getTileEntity(pos);}
        @Override public TileEntity getTileEntity(BlockPos pos,Chunk.EnumCreateEntityType type){return source.getTileEntity(pos);}
        @Override public int getCombinedLight(BlockPos pos,int minimum){return source.getCombinedLight(pos,minimum);}
        @Override public Biome getBiome(BlockPos pos){return Biomes.PLAINS;}
        @Override public WorldType getWorldType(){return WorldType.DEFAULT;}
        @Override public boolean isAirBlock(BlockPos pos){return getBlockState(pos).getBlock()==Blocks.AIR;}
        @Override public boolean isSideSolid(BlockPos pos,EnumFacing side,boolean defaultValue){return false;}
    }
    static final class SceneChunk extends RenderChunk {
        SceneChunk(SceneWorld world,RenderGlobal global,int index){super(world,global,index);}
        @Override protected ChunkCache createRegionRenderCache(net.minecraft.world.World world,BlockPos from,BlockPos to,int border){
            try{Cache cache=(Cache)unsafe.allocateInstance(Cache.class);cache.source=(SceneWorld)world;return cache;}
            catch(InstantiationException error){throw new AssertionError(error);}
        }
    }
    static final class Global extends RenderGlobal {
        int marks;
        Global(){super(null);}
        @Override public void updateTileEntities(Collection<TileEntity> remove,Collection<TileEntity> add){}
        @Override public void markBlockRangeForRenderUpdate(int x,int y,int z,int maxX,int maxY,int maxZ){marks++;super.markBlockRangeForRenderUpdate(x,y,z,maxX,maxY,maxZ);}
    }
    static final class EmptyModel implements IBakedModel {
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long random){return Collections.emptyList();}
        public boolean isAmbientOcclusion(){return false;}public boolean isGui3d(){return false;}public boolean isBuiltInRenderer(){return false;}
        public TextureAtlasSprite getParticleTexture(){return frameSprite;}public ItemCameraTransforms getItemCameraTransforms(){return ItemCameraTransforms.DEFAULT;}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
    }
    static final class Fixture implements AutoCloseable {
        final SceneWorld world;final Global renderer;final ViewFrustum view;final SceneChunk chunk;
        final List<TileEntityProgrammableGlass> cells=new ArrayList<>();
        final ExecutorService worker=Executors.newSingleThreadExecutor();
        Fixture()throws Exception{
            world=(SceneWorld)unsafe.allocateInstance(SceneWorld.class);world.states=new HashMap<>();world.tiles=new HashMap<>();world.light=LIGHT;
            set(world,net.minecraft.world.World.class,"isRemote",true);
            set(world,net.minecraft.world.World.class,"eventListeners",new ArrayList<net.minecraft.world.IWorldEventListener>());
            set(world,net.minecraft.world.World.class,"loadedTileEntityList",new ArrayList<TileEntity>());
            mc.world=world;
            renderer=(Global)unsafe.allocateInstance(Global.class);mc.renderGlobal=renderer;
            view=(ViewFrustum)unsafe.allocateInstance(ViewFrustum.class);set(view,ViewFrustum.class,"countChunksX",1);set(view,ViewFrustum.class,"countChunksY",16);set(view,ViewFrustum.class,"countChunksZ",1);
            view.renderChunks=new RenderChunk[16];
            for(int y=0;y<16;y++){SceneChunk next=new SceneChunk(world,renderer,y);next.setPosition(0,y*16,0);view.renderChunks[y]=next;}
            chunk=(SceneChunk)view.renderChunks[0];set(renderer,RenderGlobal.class,"viewFrustum",view);
        }
        void wall(int width,int height,boolean emissive,boolean hole)throws Exception{
            int frame=-1;
            if(emissive)for(int i=0;i<ScreenHousingTextures.BUILTIN_COUNT;i++){
                com.google.gson.JsonObject e=ScreenHousingTextures.entry(i);
                if(e!=null&&e.has("frameLight")&&e.get("frameLight").getAsInt()>0&&"Frame".equals(ScreenHousingTextures.category(i))){frame=i;break;}
            }
            need(!emissive||frame>=0,"shipped emissive frame fixture is missing");
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                if(hole&&x==width/2&&y==height/2)continue;
                BlockPos pos=new BlockPos(x,y,1);TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();
                tile.setAppearance(1,emissive);tile.setJoin(true);tile.setShade(goldShade());tile.setPanelFacing(EnumFacing.NORTH);
                if(emissive)tile.frameAppearance.front=frame;
                tile.setWorld(world);tile.setPos(pos);world.states.put(pos,block.getDefaultState());world.tiles.put(pos,tile);world.loadedTileEntityList.add(tile);cells.add(tile);
            }
        }
        void queue(){for(TileEntityProgrammableGlass tile:cells){GlassFrameChunks.changed(tile);GlassPaneTerrain.changed(tile);}}
        void flush(){GlassFrameChunks.flush();GlassPaneTerrain.flush();}
        CompiledChunk compileAndUpload()throws Exception{
            return compileAndUpload(chunk);
        }
        CompiledChunk compileAndUpload(SceneChunk target)throws Exception{
            ChunkCompileTaskGenerator task=target.makeCompileTaskChunk();task.setRegionRenderCacheBuilder(new RegionRenderCacheBuilder());
            task.setStatus(ChunkCompileTaskGenerator.Status.COMPILING);
            worker.submit(()->target.rebuildChunk(8,8,20,task)).get(30,TimeUnit.SECONDS);
            CompiledChunk compiled=task.getCompiledChunk();need(compiled!=null,"RenderChunk did not produce a compiled result");
            task.setStatus(ChunkCompileTaskGenerator.Status.UPLOADING);
            for(BlockRenderLayer layer:BlockRenderLayer.values())if(compiled.isLayerStarted(layer)){
                BufferBuilder buffer=task.getRegionRenderCacheBuilder().getWorldRendererByLayer(layer);
                if(layer==BlockRenderLayer.TRANSLUCENT&&!compiled.isLayerEmpty(layer))need(buffer.getVertexCount()>0,"native translucent build empty");
                if(layer==BlockRenderLayer.CUTOUT_MIPPED&&!compiled.isLayerEmpty(layer)&&!cells.isEmpty()
                        &&cells.stream().allMatch(tile->tile.frameAppearance.light()>0)){
                    BufferBuilder.State state=buffer.getVertexState();int[] data=state.getRawBuffer();int stride=state.getVertexFormat().getIntegerSize(),light=state.getVertexFormat().getUvOffsetById(1)/4;
                    for(int at=light;at<data.length;at+=stride)need((data[at]&65535)==240,"emissive native frame lost full block light");
                }
                VertexBufferUploader uploader=new VertexBufferUploader();uploader.setVertexBuffer(target.getVertexBufferByLayer(layer.ordinal()));uploader.draw(buffer);
            }
            // Publication comes only after the real layer uploads above.
            target.setCompiledChunk(compiled);task.setStatus(ChunkCompileTaskGenerator.Status.DONE);target.clearNeedsUpdate();return compiled;
        }
        public void close()throws Exception{
            mc.world=null;GlassFrameChunks.flush();GlassPaneTerrain.flush();
            for(RenderChunk value:view.renderChunks)value.deleteGlResources();worker.shutdownNow();need(worker.awaitTermination(5,TimeUnit.SECONDS),"worker did not stop");
        }
    }
    static int goldShade(){for(int i=0;i<GlassTextures.IDS.length;i++)if(GlassTextures.IDS[i].equals("gold"))return i;throw new AssertionError("Gold palette missing");}
    static void need(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    static Field field(Class<?> owner,String name)throws Exception{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field;}
    static void set(Object owner,Class<?> type,String name,Object value)throws Exception{field(type,name).set(owner,value);}
    static int texture(BufferedImage image){
        ByteBuffer pixels=BufferUtils.createByteBuffer(image.getWidth()*image.getHeight()*4);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int p=image.getRGB(x,y);pixels.put((byte)(p>>16)).put((byte)(p>>8)).put((byte)p).put((byte)(p>>24));}pixels.flip();
        int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,image.getWidth(),image.getHeight(),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);return texture;
    }
    static void initialize()throws Exception{
        net.minecraft.init.Bootstrap.register();set(null,CoreModManager.class,"deobfuscatedEnvironment",true);unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
        mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);
        for(Field value:Minecraft.class.getDeclaredFields()){
            value.setAccessible(true);
            if(Modifier.isStatic(value.getModifiers())&&value.getType()==Minecraft.class)value.set(null,mc);
            else if(value.getType()==Thread.class)value.set(mc,Thread.currentThread());
        }
        mc.gameSettings=new GameSettings();mc.gameSettings.useVbo=true;mc.gameSettings.ambientOcclusion=0;mc.displayWidth=WIDTH;mc.displayHeight=HEIGHT;
        mc.player=(net.minecraft.client.entity.EntityPlayerSP)unsafe.allocateInstance(net.minecraft.client.entity.EntityPlayerSP.class);Entity camera=mc.player;camera.posX=8;camera.posY=8;camera.posZ=20;set(mc,Minecraft.class,"renderViewEntity",camera);
        OpenGlHelper.defaultTexUnit=GL13.GL_TEXTURE0;OpenGlHelper.lightmapTexUnit=GL13.GL_TEXTURE1;
        OpenGlHelper.GL_ARRAY_BUFFER=GL15.GL_ARRAY_BUFFER;OpenGlHelper.GL_STATIC_DRAW=GL15.GL_STATIC_DRAW;
        set(null,OpenGlHelper.class,"vboSupported",true);
        ResourceLocation source=new ResourceLocation("vandorlabs:textures/blocks/programmable_glass/low/gold.png");
        InputStream input=GlassPanePipelineChecks.class.getResourceAsStream("/assets/vandorlabs/"+source.getResourcePath());if(input==null)input=new SharedTextureResources().getInputStream(source);
        try(InputStream stream=input){gold=ImageIO.read(stream);}
        int size=gold.getWidth();BufferedImage atlas=new BufferedImage(size*3,size,BufferedImage.TYPE_INT_ARGB),single=GlassPaneTextures.transform(gold,true,false);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){atlas.setRGB(x,y,gold.getRGB(x,y));atlas.setRGB(x+size,y,single.getRGB(x,y));atlas.setRGB(x+size*2,y,0xffffffff);}
        atlasTexture=texture(atlas);sourceSprite=new Sprite("vandorlabs:checks/source",size,0,true);nativeSprite=new Sprite("vandorlabs:checks/single",size,size,true);frameSprite=new Sprite("vandorlabs:checks/frame",size,size*2,false);
        TextureMap map=new TextureMap(""){@Override public TextureAtlasSprite getAtlasSprite(String name){return name.contains("native_glass")?nativeSprite:name.contains("programmable_glass")&&!name.contains("metal")?sourceSprite:frameSprite;}};
        TextureManager textures=new TextureManager(null){@Override public void bindTexture(ResourceLocation location){
            if(TextureMap.LOCATION_BLOCKS_TEXTURE.equals(location))GlStateManager.bindTexture(atlasTexture);else super.bindTexture(location);
        }};
        for(Field value:Minecraft.class.getDeclaredFields())if(!Modifier.isStatic(value.getModifiers())){
            value.setAccessible(true);if(value.getType()==TextureMap.class)value.set(mc,map);if(value.getType()==TextureManager.class)value.set(mc,textures);
        }
        Object resources=java.lang.reflect.Proxy.newProxyInstance(net.minecraft.client.resources.IResourceManager.class.getClassLoader(),new Class[]{net.minecraft.client.resources.IReloadableResourceManager.class},(proxy,method,values)->{
            if(method.getName().equals("getResource")){
                ResourceLocation id=(ResourceLocation)values[0];InputStream stream=GlassPanePipelineChecks.class.getResourceAsStream("/assets/"+id.getResourceDomain()+"/"+id.getResourcePath());
                if(stream==null)stream=new SharedTextureResources().getInputStream(id);final InputStream found=stream;
                return java.lang.reflect.Proxy.newProxyInstance(net.minecraft.client.resources.IResource.class.getClassLoader(),new Class[]{net.minecraft.client.resources.IResource.class},(resource,call,arguments)->{
                    if(call.getName().equals("getInputStream"))return found;if(call.getName().equals("getResourceLocation"))return id;if(call.getName().equals("getResourcePackName"))return "packaged-fixture";
                    if(call.getName().equals("close")){found.close();return null;}return call.getReturnType()==boolean.class?false:null;
                });
            }
            return null;
        });
        for(Field value:Minecraft.class.getDeclaredFields())if(!Modifier.isStatic(value.getModifiers())&&net.minecraft.client.resources.IResourceManager.class.isAssignableFrom(value.getType())){value.setAccessible(true);value.set(mc,resources);}
        for(Field value:TextureManager.class.getDeclaredFields())if(!Modifier.isStatic(value.getModifiers())&&net.minecraft.client.resources.IResourceManager.class.isAssignableFrom(value.getType())){value.setAccessible(true);value.set(textures,resources);}
        Class<?> variant=Class.forName("com.vandorlabs.client.GlassPaneTextures$Variant");Constructor<?> ctor=variant.getDeclaredConstructor(int.class,int.class,BufferedImage.class);ctor.setAccessible(true);
        Constructor<GlassPaneTextures.Selection> selection=GlassPaneTextures.Selection.class.getDeclaredConstructor(TextureAtlasSprite.class,variant);selection.setAccessible(true);
        GlassPaneTextures.Selection[] choices=new GlassPaneTextures.Selection[GlassTextures.IDS.length*GlassTextures.TINT_COUNT];
        for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<GlassTextures.TINT_COUNT;tint++)choices[shade*GlassTextures.TINT_COUNT+tint]=selection.newInstance(nativeSprite,ctor.newInstance(shade,tint,gold));
        set(null,GlassPaneTextures.class,"selections",choices);
        final IBakedModel glassModel=new GlassFrameModel(new EmptyModel()),empty=new EmptyModel();
        BlockModelShapes shapes=new BlockModelShapes(null){@Override public IBakedModel getModelForState(IBlockState state){return state.getBlock() instanceof BlockProgrammableGlass?glassModel:empty;}};
        BlockRendererDispatcher blocks=new BlockRendererDispatcher(shapes,new BlockColors());
        for(Field value:Minecraft.class.getDeclaredFields())if(value.getType()==BlockRendererDispatcher.class){value.setAccessible(true);value.set(mc,blocks);}
        block=new BlockProgrammableGlass("pipeline_glass_checks");
        VandorLabs.proxy=new CommonProxy(){@Override public void glassFrameAppearanceChanged(TileEntityProgrammableGlass tile){GlassFrameChunks.changed(tile);GlassPaneTerrain.changed(tile);}};
        @SuppressWarnings("unchecked") Map<Class<? extends TileEntity>,Object> renderers=(Map<Class<? extends TileEntity>,Object>)field(TileEntityRendererDispatcher.class,"renderers").get(TileEntityRendererDispatcher.instance);
        TEProgrammableGlass tileRenderer=new TEProgrammableGlass();tileRenderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);renderers.put(TileEntityProgrammableGlass.class,tileRenderer);
        BufferedImage light=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){int c=32+Math.max(x,y)*223/15;light.setRGB(x,y,0xff000000|(c<<16)|(c<<8)|c);}
        GL13.glActiveTexture(GL13.GL_TEXTURE1);lightTexture=texture(light);GL13.glActiveTexture(GL13.GL_TEXTURE0);
        need(GL11.glGetError()==0,"initialization GL error");
    }
    static void pipeline()throws Exception{
        try(Fixture current=new Fixture()){
            fixture=current;current.wall(16,16,true,true);current.queue();current.flush();
            TileEntityProgrammableGlass interior=(TileEntityProgrammableGlass)current.world.getTileEntity(new BlockPos(3,3,1));
            need(interior.frameAppearance.light()>0,"fixture lost emissive frame selection");
            need(GlassFrameMesh.get(interior).vertexCount()==0,"joined interior still has frame geometry");
            need(!GlassPaneTerrain.rendered(interior),"pane became owned before native compilation/upload");
            CompiledChunk compiled=current.compileAndUpload();
            need(!compiled.isLayerEmpty(BlockRenderLayer.TRANSLUCENT),"actual chunk builder omitted native panes");
            int[] records=compiled.getState().getRawBuffer();int stride=compiled.getState().getVertexFormat().getIntegerSize();int light=compiled.getState().getVertexFormat().getUvOffsetById(1)/4;
            need(records.length/stride==current.cells.size()*8,"native chunk pane count differs from occupied cells");
            for(int at=light;at<records.length;at+=stride)need((records[at]&65535)==(LIGHT&65535),"emissive frame forced native glass fullbright");
            need(compiled.getTileEntities().size()==current.cells.size(),"real compiler did not produce expected TESR list");
            current.flush();current.flush();
            for(TileEntityProgrammableGlass tile:current.cells)need(GlassPaneTerrain.rendered(tile),"native pane failed ownership at "+tile.getPos()+" "+GlassPaneTerrain.diagnostics());
            need(compiled.getTileEntities().isEmpty(),"settled native opaque emissive frames/panes still reach TESR dispatch");
            int marks=current.renderer.marks;
            for(int i=0;i<20;i++)current.flush();
            need(current.renderer.marks==marks,"stationary scene continuously requests chunk rebuilds");
            BlockPos removed=new BlockPos(3,3,1);current.world.tiles.remove(removed);current.world.states.remove(removed);current.world.loadedTileEntityList.remove(interior);interior.invalidate();
            GlassPaneTerrain.changed(interior);GlassFrameChunks.changed(interior);current.world.notifyBlockUpdate(removed,block.getDefaultState(),Blocks.AIR.getDefaultState(),3);current.flush();
            current.compileAndUpload();current.flush();current.flush();
            need(!GlassPaneTerrain.rendered(interior),"removed owner retained native admission");
            System.out.println("GlassPanePipeline PASS cells=255 real RenderChunk worker/model/VBO/ownership/admission, emissive frame lighting, empty joined interiors, hole, removal, stationary rebuild stability; "+GlassPaneTerrain.diagnostics());
        }
    }
    static void coldFrameRegistration()throws Exception{
        try(Fixture current=new Fixture()){
            fixture=current;current.wall(3,3,true,false);
            // Deliberately run the pane manager before the frame manager even
            // has an Entry. There can be no awaiting-frame poll registered yet.
            for(TileEntityProgrammableGlass tile:current.cells)GlassPaneTerrain.changed(tile);
            GlassPaneTerrain.flush();CompiledChunk paneOnly=current.compileAndUpload();GlassPaneTerrain.flush();
            TileEntityProgrammableGlass border=(TileEntityProgrammableGlass)current.world.getTileEntity(new BlockPos(0,0,1));
            need(border.frameChunkPublication==null,"cold frame fixture registered a frame prematurely");
            need(GlassPaneTerrain.rendered(border)&&paneOnly.getTileEntities().contains(border),"pane-only handoff lost its unregistered frame fallback");
            Object entry=border.paneTerrainPublication,section=field(entry.getClass(),"section").get(entry);
            Field dirty=field(section.getClass(),"admissionsDirty");
            need(!dirty.getBoolean(section),"initial pane admission sweep did not settle");

            for(TileEntityProgrammableGlass tile:current.cells)GlassFrameChunks.changed(tile);
            GlassFrameChunks.flush();
            need(border.frameChunkMesh!=null&&!GlassFrameChunks.rendered(border),"frame manager skipped its real pending native mesh");
            need(dirty.getBoolean(section),"cold frame registration did not invalidate pane admissions");
            GlassPaneTerrain.flush();
            need(paneOnly.getTileEntities().contains(border),"pending frame upload lost its fallback admission");

            CompiledChunk framed=current.compileAndUpload();GlassPaneTerrain.flush();
            need(GlassFrameChunks.rendered(border)&&!dirty.getBoolean(section),"frame-start phase did not settle uploaded frame before pane admissions");
            need(framed.getTileEntities().isEmpty(),"cold registered frames left settled pane owners in the TESR list");
            GlassFrameChunks.flush();GlassPaneTerrain.flush();
            need(!dirty.getBoolean(section)&&framed.getTileEntities().isEmpty(),"repeated settled-frame sweep churned pane admission");
            System.out.println("GlassPaneColdFrames PASS real cold registration, upload, same-phase readiness settlement and final all-owner admission");
        }
    }
    static void blendedEmissiveFrames()throws Exception{
        Sprite opaque=frameSprite;frameSprite=new Sprite("vandorlabs:checks/blended-frame",gold.getWidth(),gold.getWidth()*2,true);
        GlassFrameChunks.reload();
        try(Fixture current=new Fixture()){
            fixture=current;current.wall(3,3,true,false);current.queue();current.flush();
            TileEntityProgrammableGlass border=current.cells.get(0),interior=(TileEntityProgrammableGlass)current.world.getTileEntity(new BlockPos(1,1,1));
            GlassFrameModel.Mesh frame=GlassFrameModel.build(border,block.getDefaultState());
            need(!frame.translucent.isEmpty(),"fixture did not contain blended frame faces");
            need(border.frameChunkMesh==null,"emissive blended frame entered ordinarily lit native translucent layer");
            CompiledChunk compiled=current.compileAndUpload();current.flush();current.flush();
            for(TileEntityProgrammableGlass tile:current.cells)need(GlassPaneTerrain.rendered(tile),"blended emissive frame rejected its native pane");
            int[] records=compiled.getState().getRawBuffer();int stride=compiled.getState().getVertexFormat().getIntegerSize(),light=compiled.getState().getVertexFormat().getUvOffsetById(1)/4;
            for(int at=light;at<records.length;at+=stride)need((records[at]&65535)==(LIGHT&65535),"blended emissive frame made its native pane fullbright");
            need(compiled.getTileEntities().size()==1&&compiled.getTileEntities().contains(border)&&!compiled.getTileEntities().contains(interior),"only the retained blended-frame anchor should retain TESR admission");
            final class Capture extends net.minecraft.client.renderer.Tessellator {
                int draws,vertices;Capture(){super(4096);}
                @Override public void draw(){
                    BufferBuilder.State state=getBuffer().getVertexState();int[] raw=state.getRawBuffer();int size=state.getVertexFormat().getIntegerSize(),offset=state.getVertexFormat().getUvOffsetById(1)/4;
                    need(raw.length>0,"emissive fallback drew an empty frame");
                    for(int at=offset;at<raw.length;at+=size)need((raw[at]&65535)==240,"actual blended frame fallback lost full block light");
                    draws++;vertices+=raw.length/size;super.draw();
                }
            }
            Field singleton=null;for(Field candidate:net.minecraft.client.renderer.Tessellator.class.getDeclaredFields())if(Modifier.isStatic(candidate.getModifiers())&&candidate.getType()==net.minecraft.client.renderer.Tessellator.class)singleton=candidate;
            need(singleton!=null,"Tessellator singleton unavailable");singleton.setAccessible(true);Object old=singleton.get(null),base=unsafe.staticFieldBase(singleton);long offset=unsafe.staticFieldOffset(singleton);Capture capture=new Capture();
            unsafe.putObject(base,offset,capture);long retainedDraws=GlassFrameRetained.mainDraws;
            try{
                TEProgrammableGlass renderer=new TEProgrammableGlass();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);
                net.minecraftforge.client.ForgeHooksClient.setRenderPass(0);renderer.render(border,0,0,0,0,-1,1);
                need(capture.draws==0&&GlassFrameRetained.mainDraws==retainedDraws+1,"retained blended frame escaped to legacy drawing or native-owned pane repeated");
                net.minecraftforge.client.ForgeHooksClient.setRenderPass(1);renderer.render(border,0,0,0,0,-1,1);
                need(capture.draws==0&&GlassFrameRetained.mainDraws==retainedDraws+1,"native-owned pane or emissive frame repeated in translucent TESR pass");
            }finally{unsafe.putObject(base,offset,old);net.minecraftforge.client.ForgeHooksClient.setRenderPass(-1);}
            System.out.println("GlassPaneBlendedFrames PASS native ordinarily lit panes, one retained frame anchor, actual fullbright frame VBO and no translucent redraw");
        }finally{frameSprite=opaque;GlassFrameChunks.reload();}
    }
    public static void main(String[] args)throws Exception{
        initialize();pipeline();coldFrameRegistration();blendedEmissiveFrames();need(GL11.glGetError()==0,"pipeline left OpenGL error");
        GL11.glDeleteTextures(atlasTexture);GL11.glDeleteTextures(lightTexture);
        System.out.println("GlassPanePipeline PASS checks="+checks+"; isolated real Forge pipeline, no OptiFine shader-pack or hardware FPS acceptance");
    }
    private GlassPanePipelineChecks(){}
}

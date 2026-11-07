package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.ControlMountSettings;
import com.vandorlabs.persistence.PowerLeverSize;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.util.*;

/** Generation-owned mounted meshes; section workers consume only immutable visual model data. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeMountedControlModel extends BakedModelWrapper<BakedModel> {
    private record Key(BakedModel source,BlockState state,int height,int tilt,int direction,int rotation,int size){}
    private record Sources(List<Block> blocks,List<BakedModel> items){}
    private final Block block;
    private final ControlMountSettings itemMount;
    /** Source variant: physical size for power levers, authored style for four-step controls. */
    private final int itemSize;
    private final BlockState listedState;
    private final Map<BlockState,BakedModel> poses;
    private final Sources sources;
    private static final int MESH_BUDGET=16*1024*1024;
    // One budget for every listed state and configured item in this resource generation.
    // Source identity separates item/world artwork and resource-pack models with the same pose.
    private final com.google.common.cache.Cache<Key,List<BakedQuad>> meshes;
    private static com.google.common.cache.Cache<Key,List<BakedQuad>> meshCache(){return com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(MESH_BUDGET)
        .weigher((Key key,List<BakedQuad> quads)->168+quads.stream().mapToInt(q->96+q.getVertices().length*4).sum()).build();}
    private static java.lang.ref.WeakReference<Object> preparedModels=new java.lang.ref.WeakReference<>(null);
    private static com.google.common.cache.Cache<Key,List<BakedQuad>> preparedMeshes;
    private static synchronized com.google.common.cache.Cache<Key,List<BakedQuad>> meshCache(Object models){if(preparedModels.get()!=models){preparedModels=new java.lang.ref.WeakReference<>(models);preparedMeshes=meshCache();}return preparedMeshes;}
    private java.lang.ref.WeakReference<List<BakedQuad>> itemQuads;
    private NativeMountedControlModel(BakedModel base,Block block,ControlMountSettings itemMount,int itemSize,Map<BlockState,BakedModel> poses,Sources sources,BlockState listedState,com.google.common.cache.Cache<Key,List<BakedQuad>> meshes){super(base);boolean reader=block instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock;readerItems=reader?new BakedModel[64]:null;readerComponents=reader?new java.lang.ref.WeakReference<?>[128]:null;readerVisuals=reader?new ItemVisual[128]:null;this.meshes=meshes;this.block=block;this.itemMount=itemMount;this.itemSize=itemSize;this.poses=poses;this.sources=sources;this.listedState=listedState;this.items=com.google.common.cache.CacheBuilder.newBuilder().maximumSize(block instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock?128:16).build();}
    private static int defaultSize(Block block){return block instanceof NativePowerLeverBlock lever?lever.defaultSize():block instanceof NativeSignalControlBlock signal&&signal.hasSelectableType()?signal.controlType():0;}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){
        List<Block> blocks=List.of(VandorLabsBootstrap.PUSH_BUTTON.get(),VandorLabsBootstrap.ROCKER_SWITCH.get(),VandorLabsBootstrap.COMPACT_POWER_LEVER.get(),VandorLabsBootstrap.INDUSTRIAL_POWER_LEVER.get(),VandorLabsBootstrap.SMALL_POWER_LEVER.get(),VandorLabsBootstrap.LARGE_POWER_LEVER.get(),VandorLabsBootstrap.THRUSTER_LEVER.get(),VandorLabsBootstrap.AIRLINER_THROTTLE.get(),VandorLabsBootstrap.FIGHTER_THROTTLE.get(),VandorLabsBootstrap.WALL_SLIDER.get());
        prepare(event.getModels(),blocks);
    }
    /** Inactive batch adapter for additional Source mounted families; registration stays caller-owned. */
    public static void prepare(Map<net.minecraft.client.resources.model.ModelResourceLocation,BakedModel> models,List<? extends Block> blocks){
        Map<BlockState,BakedModel> resolved=new HashMap<>();Map<net.minecraft.client.resources.model.ModelResourceLocation,BlockState> states=new HashMap<>();Map<ResourceLocation,Block> supported=new HashMap<>();Map<Block,BakedModel> itemModels=new HashMap<>();Map<Block,Boolean> readerStates=new HashMap<>();
        for(Block block:blocks){var name=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);supported.put(name,block);var item=models.get(new net.minecraft.client.resources.model.ModelResourceLocation(name,"inventory"));if(item==null)throw new IllegalStateException("Missing mounted item "+name);boolean isReader=block instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock;if(isReader)readerStates.put(block,com.vandorlabs.neoforge.credentials.NativeCredentialReaderModels.canonicalStates((com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock)block));itemModels.put(block,isReader&&!com.vandorlabs.neoforge.credentials.NativeCredentialReaderModels.canonicalItem((com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock)block)?item:NativeAuthoredControlBake.correct(item,null,NativeMountedControlGeometry.authoredItem(block instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock reader?reader.mountedFamily():name.getPath())));
            for(var state:block.getStateDefinition().getPossibleStates()){var location=net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);var model=models.get(location);if(model==null)throw new IllegalStateException("Missing mounted pose "+state);resolved.put(state,isReader&&(!readerStates.get(block)||!com.vandorlabs.neoforge.credentials.NativeCredentialReaderModels.canonicalFrame((com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock)block,state))?model:NativeAuthoredControlBake.correct(model,state,NativeMountedControlGeometry.authored(state,block instanceof NativeSignalControlBlock?state.getValue(NativeSignalControlBlock.ROTATION):state.hasProperty(NativeMountedSwitchBlock.ROTATION)?state.getValue(NativeMountedSwitchBlock.ROTATION):0,defaultSize(block))));states.put(location,state);}
        }
        Map<BlockState,BakedModel> poses=Map.copyOf(resolved);Map<Block,Sources> sources=new HashMap<>();
        for(Block block:blocks){Block small=block,large=block;if(block instanceof NativePowerLeverBlock lever){small=lever.isTwin()?VandorLabsBootstrap.SMALL_POWER_LEVER.get():VandorLabsBootstrap.COMPACT_POWER_LEVER.get();large=lever.isTwin()?VandorLabsBootstrap.LARGE_POWER_LEVER.get():VandorLabsBootstrap.INDUSTRIAL_POWER_LEVER.get();}if(block instanceof NativeSignalControlBlock signal&&signal.hasSelectableType()){List<Block> family=List.of(VandorLabsBootstrap.THRUSTER_LEVER.get(),VandorLabsBootstrap.AIRLINER_THROTTLE.get(),VandorLabsBootstrap.FIGHTER_THROTTLE.get());sources.put(block,new Sources(family,family.stream().map(itemModels::get).toList()));}else sources.put(block,new Sources(List.of(small,large),List.of(itemModels.get(small),itemModels.get(large))));}
        var meshes=meshCache(models);
        models.replaceAll((id,model)->{Block block=supported.get(id.id());return block==null?model:new NativeMountedControlModel(states.containsKey(id)?poses.get(states.get(id)):itemModels.get(block),block,ControlMountSettings.of(0,0,0),defaultSize(block),poses,sources.get(block),states.get(id),meshes);});
    }
    private BlockState sourcePose(BlockState state,int size,int rotation){
        if(block instanceof NativePowerLeverBlock)return sources.blocks.get(size).defaultBlockState().setValue(NativePowerLeverBlock.FACING,state.getValue(NativePowerLeverBlock.FACING)).setValue(NativePowerLeverBlock.FLOOR,state.getValue(NativePowerLeverBlock.FLOOR)).setValue(NativePowerLeverBlock.POWERED,state.getValue(NativePowerLeverBlock.POWERED));
        if(block instanceof NativeSignalControlBlock)return state.setValue(NativeSignalControlBlock.ROTATION,NativeSignalControlPoses.rotation(state,rotation));
        return state.setValue(NativeMountedSwitchBlock.ROTATION,NativeMountedControlGeometry.rotation(state,rotation));
    }
    private BlockState itemPose(){
        if(block instanceof NativePowerLeverBlock lever)return block.defaultBlockState().setValue(NativePowerLeverBlock.FLOOR,lever.isTwin()).setValue(NativePowerLeverBlock.FACING,Direction.NORTH);
        if(block instanceof NativeSignalControlBlock signal)return block.defaultBlockState().setValue(NativeSignalControlBlock.FACING,signal.hasSelectableType()?Direction.UP:Direction.NORTH);
        return block.defaultBlockState().setValue(NativeMountedSwitchBlock.FACING,block instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock?Direction.NORTH:Direction.UP);
    }
    @Override public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon(){return getParticleIcon(ModelData.EMPTY);}
    @Override public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon(ModelData data){
        if(listedState==null)return originalModel.getParticleIcon(data);var visual=data.get(NativeBinaryControlBlockEntity.VISUAL);int size=visual==null||block instanceof NativeSignalControlBlock?defaultSize(block):visual.size();int rotation=visual==null?0:visual.rotation();return poses.get(sourcePose(listedState,size,rotation)).getParticleIcon(data);
    }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
    @Override public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,net.minecraft.core.BlockPos pos,BlockState state,ModelData data){return data;}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
        var visual=data.get(NativeBinaryControlBlockEntity.VISUAL);ControlMountSettings mount=visual==null?itemMount:visual.mount();
        boolean power=block instanceof NativePowerLeverBlock,signal=block instanceof NativeSignalControlBlock;int size=visual==null||signal?itemSize:visual.size();
        BlockState pose=state==null?itemPose():state;int rotation=power?NativeMountedControlGeometry.powerRotation(pose):signal?NativeSignalControlPoses.rotation(pose,visual==null?pose.getValue(NativeSignalControlBlock.ROTATION):visual.rotation()):NativeMountedControlGeometry.rotation(pose,visual==null?pose.getValue(NativeMountedSwitchBlock.ROTATION):visual.rotation());
        BlockState sourcePose=sourcePose(pose,size,rotation);if(!power)pose=sourcePose;BakedModel sourceModel=state==null?originalModel:poses.get(sourcePose);
        if(mount.height==0&&mount.tilt==0)return sourceModel.getQuads(state==null?null:sourcePose,side,random,data,layer);
        if(side!=null)return List.of();
        boolean cacheable=NativeAuthoredControlBake.isStatic(sourceModel),readerItem=state==null&&block instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock;
        List<BakedQuad> cached=cacheable&&readerItem&&itemQuads!=null?itemQuads.get():null;if(cached!=null)return cached;
        Key key=new Key(sourceModel,pose,mount.height,mount.tilt,mount.direction,rotation,size);
        cached=cacheable?meshes.getIfPresent(key):null;if(cached!=null){if(readerItem)itemQuads=new java.lang.ref.WeakReference<>(cached);return cached;}
        var transform=power?NativeMountedControlGeometry.powerTransform(pose,mount,size):signal?NativeMountedControlGeometry.signalTransform(pose,mount,rotation):NativeMountedControlGeometry.transform(pose,mount,rotation);List<BakedQuad> result=new ArrayList<>();
        double itemOffset=0;
        if(state==null&&block instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock){double back=Double.NEGATIVE_INFINITY;for(Direction face:faces())for(BakedQuad quad:sourceModel.getQuads(null,face,random,data,layer)){int[] raw=quad.getVertices();int stride=raw.length/4;for(int vertex=0;vertex<4;vertex++)back=Math.max(back,Float.intBitsToFloat(raw[vertex*stride+2]));}if(Double.isFinite(back))itemOffset=NativeMountedControlGeometry.pose(pose,rotation).support().maxZ-back;}
        for(Direction source:faces())for(BakedQuad quad:sourceModel.getQuads(state==null?null:sourcePose,source,random,data,layer)){
            int[] raw=quad.getVertices();int stride=raw.length/4;Vec3[] points=new Vec3[4];boolean bottom=true;
            for(int i=0;i<4;i++){int at=i*stride;double x=Float.intBitsToFloat(raw[at]),y=Float.intBitsToFloat(raw[at+1]),z=Float.intBitsToFloat(raw[at+2]);if(itemOffset!=0)z+=itemOffset;bottom&=Math.abs(transform.depth(x,y,z))<(signal?1e-7:1e-6);Vec3 point=transform.transform(x,y,z);points[i]=itemOffset==0?point:new Vec3(point.x,point.y,point.z-itemOffset);}
            if(bottom){Vec3[] projected=new Vec3[4];for(int i=0;i<4;i++){if(itemOffset==0)projected[i]=transform.project(points[i].x,points[i].y,points[i].z);else{Vec3 point=transform.project(points[i].x,points[i].y,points[i].z+itemOffset);projected[i]=new Vec3(point.x,point.y,point.z-itemOffset);}}add(result,quad,projected);for(int i=0;i<4;i++){int next=(i+1)%4;add(result,quad,new Vec3[]{points[i],points[next],projected[next],projected[i]});}}
            else add(result,quad,points);
        }
        cached=List.copyOf(result);if(cacheable){meshes.put(key,cached);if(readerItem)itemQuads=new java.lang.ref.WeakReference<>(cached);}return cached;
    }
    private record ItemVisual(ControlMountSettings mount,int size,boolean typedSize,int controlType,boolean valid){}
    private static final ItemVisual EMPTY_ITEM=new ItemVisual(ControlMountSettings.of(0,0,0),0,false,0,false);
    private static final com.mojang.serialization.MapDecoder<ItemVisual> ITEM=new com.mojang.serialization.MapDecoder.Implementation<>(){
        @Override public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops){return java.util.stream.Stream.of("RedstoneChannelSettings").map(ops::createString);}
        @Override public <T> com.mojang.serialization.DataResult<ItemVisual> decode(com.mojang.serialization.DynamicOps<T> ops,com.mojang.serialization.MapLike<T> input){
            T nested=input.get("RedstoneChannelSettings");if(nested==null)return com.mojang.serialization.DataResult.success(EMPTY_ITEM);var map=ops.getMap(nested).result();if(map.isEmpty())return com.mojang.serialization.DataResult.success(EMPTY_ITEM);input=map.get();
            int h=integer(input.get(ControlMountSettings.HEIGHT)),t=ControlMountSettings.migratedTilt(integer(input.get(ControlMountSettings.TILT)),input.get(ControlMountSettings.VERSION)!=null),d=integer(input.get(ControlMountSettings.DIRECTION));Object size=input.get(PowerLeverSize.KEY);boolean valid=ControlMountSettings.valid(h,t,d);
            return com.mojang.serialization.DataResult.success(new ItemVisual(valid?ControlMountSettings.of(h,t,d):EMPTY_ITEM.mount,size instanceof net.minecraft.nbt.IntTag tag?tag.getAsInt():0,size instanceof net.minecraft.nbt.IntTag,integer(input.get("ControlType")),valid));
        }
        private int integer(Object value){return value instanceof net.minecraft.nbt.NumericTag number?number.getAsInt():0;}
    };
    private final com.google.common.cache.Cache<net.minecraft.world.item.component.CustomData,ItemVisual> projected=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(128).build();
    private final com.google.common.cache.Cache<Integer,BakedModel> items;
    private final BakedModel[] readerItems;
    private final java.lang.ref.WeakReference<?>[] readerComponents;
    private final ItemVisual[] readerVisuals;
    private int readerNext;
    private ItemVisual readerVisual(net.minecraft.world.item.component.CustomData custom){for(int i=0;i<readerComponents.length;i++){var reference=readerComponents[i];if(reference!=null&&reference.get()==custom)return readerVisuals[i];}ItemVisual value=custom.read(ITEM).result().orElse(EMPTY_ITEM);readerComponents[readerNext]=new java.lang.ref.WeakReference<>(custom);readerVisuals[readerNext]=value;readerNext=(readerNext+1)%128;return value;}
    private static final class ReaderOverride {final java.lang.ref.WeakReference<BakedModel> source;final java.lang.ref.WeakReference<?>[] models=new java.lang.ref.WeakReference<?>[64];ReaderOverride(BakedModel source){this.source=new java.lang.ref.WeakReference<>(source);}}
    private ReaderOverride[] readerOverrides;private int nextReaderOverride;
    private BakedModel readerSelected(BakedModel source,ControlMountSettings mount,int size){if(source==originalModel&&mount.height==itemMount.height&&mount.tilt==itemMount.tilt&&mount.direction==itemMount.direction)return this;int key=mount.height+4*(mount.tilt+4*mount.direction);if(readerOverrides==null)readerOverrides=new ReaderOverride[16];ReaderOverride found=null;for(var entry:readerOverrides)if(entry!=null&&entry.source.get()==source){found=entry;break;}if(found==null){found=new ReaderOverride(source);readerOverrides[nextReaderOverride]=found;nextReaderOverride=(nextReaderOverride+1)%readerOverrides.length;}var reference=found.models[key];BakedModel result=reference==null?null:(BakedModel)reference.get();if(result==null){result=new NativeMountedControlModel(source,block,mount,size,poses,sources,null,meshes);found.models[key]=new java.lang.ref.WeakReference<>(result);}return result;}
    private final ItemOverrides overrides=new ItemOverrides(){
        @Override public BakedModel resolve(BakedModel original,net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel world,net.minecraft.world.entity.LivingEntity entity,int seed){
            var custom=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);if(custom==null)return NativeMountedControlModel.this;
            ItemVisual visual;if(block instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock)visual=readerVisual(custom);else{visual=projected.getIfPresent(custom);if(visual==null){visual=custom.read(ITEM).result().orElse(EMPTY_ITEM);projected.put(custom,visual);}}
            boolean signal=block instanceof NativeSignalControlBlock;
            if(!signal&&!visual.valid)return NativeMountedControlModel.this;
            ControlMountSettings mount=visual.valid?visual.mount:EMPTY_ITEM.mount;
            // Legacy canonical Thruster items select/clamp numeric ControlType before applying mount data.
            // Hidden siblings keep their authored style; invalid mounts retain that selected flat model.
            int size=signal?(block==VandorLabsBootstrap.THRUSTER_LEVER.get()?Math.max(0,Math.min(2,visual.controlType)):defaultSize(block)):PowerLeverSize.itemChoice(visual.typedSize,visual.size,defaultSize(block));if(!signal&&!PowerLeverSize.valid(size))return NativeMountedControlModel.this;if(block instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock){var selected=originalModel.getOverrides().resolve(originalModel,stack,world,entity,seed);if(selected!=null&&selected!=originalModel)return readerSelected(selected,mount,size);}
            if(mount.height==0&&mount.tilt==0&&size==itemSize)return NativeMountedControlModel.this;
            int key=mount.height+4*(mount.tilt+4*mount.direction)+64*size;boolean reader=block instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock;BakedModel model=reader?readerItems[key%64]:items.getIfPresent(key);if(model==null){BakedModel base=sources.items.get(size);model=new NativeMountedControlModel(base,signal?sources.blocks.get(size):block,mount,size,poses,sources,null,meshes);if(reader)readerItems[key%64]=model;else items.put(key,model);}return model;
        }
    };
    @Override public ItemOverrides getOverrides(){return overrides;}
    private volatile ItemTransforms padded;
    @Override public ItemTransforms getTransforms(){
        ItemTransforms base=originalModel.getTransforms();if(block instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock&&!NativeAuthoredControlBake.isStatic(originalModel)){if(itemMount.height==0&&itemMount.tilt==0)return NativeItemPadding.needsFit(this,base)?NativeItemPadding.fit(this,base):base;return NativeItemPadding.fit(this,base);}if(itemMount.height==0&&itemMount.tilt==0){if(!(block instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock))return base;ItemTransforms cached=padded;if(cached!=null)return cached;return padded=NativeItemPadding.needsFit(this,base)?NativeItemPadding.fit(this,base):base;}ItemTransforms cached=padded;if(cached!=null)return cached;
        padded=NativeItemPadding.fit(this,base);return padded;
    }
    @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){getTransforms().getTransform(context).apply(left,pose);return this;}
    private final List<BakedModel> passes=List.of(this);
    @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){if(!(block instanceof com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock)||NativeAuthoredControlBake.isStatic(originalModel))return passes;var source=originalModel.getRenderPasses(stack,fabulous);if(source.size()==1&&source.get(0)==originalModel)return passes;var result=new ArrayList<BakedModel>();for(var model:source)result.add(model==originalModel?this:readerSelected(model,itemMount,itemSize));return List.copyOf(result);}
    private static final Direction[] FACES={null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
    private static Direction[] faces(){return FACES;}
    private static void add(List<BakedQuad> result,BakedQuad source,Vec3[] points){
        Vec3 normal=Vec3.ZERO;for(int i=1;i<3&&normal.lengthSqr()<1e-14;i++)normal=points[i].subtract(points[0]).cross(points[i+1].subtract(points[0]));if(normal.lengthSqr()<1e-14)return;double length=(float)Math.sqrt(normal.lengthSqr());normal=length<1e-4?Vec3.ZERO:new Vec3(normal.x/length,normal.y/length,normal.z/length);
        int[] raw=source.getVertices().clone();int stride=raw.length/4;int packed=((int)Math.round(normal.x*127)&255)|(((int)Math.round(normal.y*127)&255)<<8)|(((int)Math.round(normal.z*127)&255)<<16);
        for(int i=0;i<4;i++){int at=i*stride;raw[at]=Float.floatToRawIntBits((float)points[i].x);raw[at+1]=Float.floatToRawIntBits((float)points[i].y);raw[at+2]=Float.floatToRawIntBits((float)points[i].z);raw[at+7]=packed;}
        result.add(new BakedQuad(raw,source.getTintIndex(),Direction.getNearest((float)normal.x,(float)normal.y,(float)normal.z),source.getSprite(),source.isShade(),source.hasAmbientOcclusion()));
    }
}

package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.blocks.ProgrammableHousingState;
import com.vandorlabs.persistence.SaveSchema;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.tiles.FaceTextures;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

/** Frozen-renderer parity, bounded retention and paired preparation costs without a graphics context. */
public final class StairRetextureChecks {
    private static final com.sun.management.ThreadMXBean METER=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static volatile Object consumed;
    private static final AtomicInteger comparisons=new AtomicInteger();
    private static final EnumFacing[] SIDES={null,EnumFacing.DOWN,EnumFacing.UP,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST};
    private static void need(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private static long bytes(){return METER.getThreadAllocatedBytes(Thread.currentThread().getId());}
    private static final class Atlas implements Function<String,TextureAtlasSprite> {
        final ConcurrentMap<String,TextureAtlasSprite> sprites=new ConcurrentHashMap<>();final int generation;
        Atlas(int generation){this.generation=generation;}
        public TextureAtlasSprite apply(String name){return sprites.computeIfAbsent(name,key->{
            TextureAtlasSprite sprite=new TextureAtlasSprite(key){};
            int aspect=Math.floorMod(key.hashCode(),3);sprite.setIconWidth(aspect==1?32:16);sprite.setIconHeight(aspect==2?32:16);
            int cell=Math.floorMod(key.hashCode()+generation,128);sprite.initSprite(1024,1024,(cell%16)*64,(cell/16)*64,false);return sprite;
        });}
    }
    private static IBlockState shape(EnumFacing facing,BlockStairs.EnumHalf half,BlockStairs.EnumShape shape){
        return ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half).withProperty(BlockStairs.SHAPE,shape);
    }
    private static IBlockState input(IBlockState shape,int settings,int finish,int side,int layout,FaceTextures faces){
        return ((IExtendedBlockState)shape).withProperty(ProgrammableHousingState.STAIR_GEOMETRY,settings)
                .withProperty(ProgrammableHousingState.FINISH,finish).withProperty(ProgrammableHousingState.SIDE_FINISH,side)
                .withProperty(ProgrammableHousingState.TILE_SIDES,layout).withProperty(ProgrammableHousingState.FACES,faces);
    }
    private static IBlockState input(IBlockState shape,int settings,int profile,int layout){
        FaceTextures faces=profile==0?FaceTextures.DEFAULT:new FaceTextures(profile!=3,profile==1?new int[]{1,2,3,4,5,6}:new int[]{-1,2,-1,4,-1,6});
        return input(shape,settings,profile==0?0:ScreenHousingTextures.DEFAULT_STORAGE,profile==2?9:-1,layout,faces);
    }
    private static SimpleBakedModel model(IBlockState shape,TextureAtlasSprite sprite){
        List<BakedQuad> general=new ArrayList<>();Map<EnumFacing,List<BakedQuad>> faces=new EnumMap<>(EnumFacing.class);
        for(EnumFacing face:EnumFacing.values())faces.put(face,new ArrayList<>());
        List<BakedQuad> quads=ReferenceStairRetextureMesh.quads(shape,8,sprite);
        for(int i=0;i<quads.size();i++){
            BakedQuad source=quads.get(i);int[] data=source.getVertexData().clone();
            // Native color/tint/diffuse payloads must survive retexturing as well as positions and UVs.
            for(int vertex=0;vertex<4;vertex++)data[vertex*7+3]=0xff000000|((i*197+vertex*31)&0xffffff);
            BakedQuad quad=new BakedQuad(data,(i%3)-1,source.getFace(),sprite,(i&1)==0,DefaultVertexFormats.ITEM);
            if((i&3)==0)general.add(quad);else faces.get(quad.getFace()).add(quad);
        }
        return new SimpleBakedModel(general,faces,true,true,sprite,ItemCameraTransforms.DEFAULT,new ItemOverrideList(Collections.emptyList()));
    }
    private static SimpleBakedModel model(List<BakedQuad> quads,TextureAtlasSprite sprite){
        Map<EnumFacing,List<BakedQuad>> faces=new EnumMap<>(EnumFacing.class);for(EnumFacing face:EnumFacing.values())faces.put(face,Collections.emptyList());
        return new SimpleBakedModel(quads,faces,true,true,sprite,ItemCameraTransforms.DEFAULT,new ItemOverrideList(Collections.emptyList()));
    }
    private static void same(List<BakedQuad> expected,List<BakedQuad> actual){
        need(expected.size()==actual.size(),"quad count changed: "+expected.size()+"/"+actual.size());
        for(int i=0;i<expected.size();i++){
            BakedQuad a=expected.get(i),b=actual.get(i);
            need(a.getFace()==b.getFace()&&a.getSprite()==b.getSprite()&&a.getTintIndex()==b.getTintIndex()
                    &&a.shouldApplyDiffuseLighting()==b.shouldApplyDiffuseLighting()&&a.getFormat().equals(b.getFormat())
                    &&Arrays.equals(a.getVertexData(),b.getVertexData()),"complete stair vertex/sprite/tint/lighting record changed at quad "+i);
        }
        comparisons.incrementAndGet();
    }
    /** Use the actual Forge JSON baker; only its graphics/resource-manager owner is inert.
     * The same reflection fixture pattern is used by the repository's artwork oracles. */
    private static final class ForgeSource {
        final Object owner;final java.lang.reflect.Constructor<?> wrapper;final java.lang.reflect.Method baker;
        ForgeSource()throws Exception {
            java.lang.reflect.Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field.get(null);
            owner=unsafe.allocateInstance(net.minecraftforge.client.model.ModelLoader.class);field=ModelBakery.class.getDeclaredField("faceBakery");field.setAccessible(true);field.set(owner,new FaceBakery());
            Class<?> type=Class.forName("net.minecraftforge.client.model.ModelLoader$VanillaModelWrapper");wrapper=type.getDeclaredConstructors()[0];wrapper.setAccessible(true);
            baker=type.getDeclaredMethod("bakeImpl",net.minecraftforge.common.model.IModelState.class,VertexFormat.class,Function.class);baker.setAccessible(true);
        }
        IBakedModel bake(String id,int x,int y,boolean uvlock,Atlas atlas)throws Exception {
            ModelBlock authored=DoorArtworkLegacyChecks.load(id);Object value=wrapper.newInstance(owner,new net.minecraft.util.ResourceLocation(id),authored,uvlock,
                    new net.minecraftforge.client.model.animation.ModelBlockAnimation(com.google.common.collect.ImmutableMap.of(),com.google.common.collect.ImmutableMap.of()));
            return (IBakedModel)baker.invoke(value,ModelRotation.getModelRotation(x,y),DefaultVertexFormats.ITEM,(Function<net.minecraft.util.ResourceLocation,TextureAtlasSprite>)(name->atlas.apply(name.toString())));
        }
    }
    private static IBlockState animated(IBlockState source,net.minecraftforge.common.model.IModelState animation){
        net.minecraft.block.properties.IProperty<?>[] listed={BlockStairs.FACING,BlockStairs.HALF,BlockStairs.SHAPE};
        net.minecraftforge.common.property.IUnlistedProperty<?>[] unlisted={ProgrammableHousingState.FINISH,ProgrammableHousingState.SIDE_FINISH,ProgrammableHousingState.FACES,
                ProgrammableHousingState.TILE_SIDES,ProgrammableHousingState.STAIR_GEOMETRY,net.minecraftforge.common.property.Properties.AnimationProperty};
        IExtendedBlockState state=(IExtendedBlockState)new net.minecraftforge.common.property.ExtendedBlockState(ModBlocks.PROGRAMMABLE_STAIRS,listed,unlisted).getBaseState();
        state=(IExtendedBlockState)state.withProperty(BlockStairs.FACING,source.getValue(BlockStairs.FACING)).withProperty(BlockStairs.HALF,source.getValue(BlockStairs.HALF)).withProperty(BlockStairs.SHAPE,source.getValue(BlockStairs.SHAPE));
        return ((IExtendedBlockState)input(state,8,1,2)).withProperty(net.minecraftforge.common.property.Properties.AnimationProperty,animation);
    }
    private static int signature(List<BakedQuad> quads){int hash=1;for(BakedQuad quad:quads)hash=31*hash+Arrays.hashCode(quad.getVertexData());return hash;}
    private static void forgeSources()throws Exception {
        Atlas atlas=new Atlas(0);ForgeSource loader=new ForgeSource();com.google.gson.JsonObject variants;
        try(java.io.Reader reader=new java.io.InputStreamReader(StairRetextureChecks.class.getResourceAsStream("/assets/vandorlabs/blockstates/programmable_stairs.json"),"UTF-8")){
            variants=new com.google.gson.JsonParser().parse(reader).getAsJsonObject().getAsJsonObject("variants");
        }
        ProgrammableStairsModel.Bake bake=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM);int variantsTested=0;
        for(IBlockState pose:ModBlocks.PROGRAMMABLE_STAIRS.getBlockState().getValidStates()){
            String key="facing="+pose.getValue(BlockStairs.FACING).getName()+",half="+pose.getValue(BlockStairs.HALF).getName()+",shape="+pose.getValue(BlockStairs.SHAPE).getName();
            com.google.gson.JsonObject variant=variants.getAsJsonObject(key);IBakedModel source=loader.bake(variant.get("model").getAsString(),variant.has("x")?variant.get("x").getAsInt():0,variant.has("y")?variant.get("y").getAsInt():0,variant.has("uvlock")&&variant.get("uvlock").getAsBoolean(),atlas);
            need(source.getClass().getName().equals("net.minecraftforge.client.model.ModelLoader$VanillaModelWrapper$1"),"Forge JSON source class changed; review cache eligibility");
            ReferenceStairRetextureModel previous=new ReferenceStairRetextureModel(source,atlas);ProgrammableStairsModel current=bake.wrap(source);
            for(int profile=0;profile<4;profile++)for(int layout=0;layout<3;layout++)for(int settings:new int[]{8,1|(2<<5)}){
                IBlockState configured=input(pose,settings,profile,layout);
                for(EnumFacing side:SIDES){List<BakedQuad> actual=current.getQuads(configured,side,0);same(previous.getQuads(configured,side,0),actual);need(actual==current.getQuads(configured,side,915),"actual Forge bake did not reuse static results");}
            }
            variantsTested++;
        }
        need(bake.cachedResults()>0&&bake.cachedGeometries()>0,"actual Forge wrapper never admitted geometry/results");
        IBlockState pose=shape(EnumFacing.EAST,BlockStairs.EnumHalf.BOTTOM,BlockStairs.EnumShape.STRAIGHT);
        IBakedModel straight=loader.bake("vandorlabs:programmable_stairs_straight",0,0,false,atlas),inner=loader.bake("vandorlabs:programmable_stairs_inner",0,0,false,atlas);
        ProgrammableStairsModel.Bake changingBake=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM);ProgrammableStairsModel changing=changingBake.wrap(straight);
        ReferenceStairRetextureModel animationReference=new ReferenceStairRetextureModel(straight,atlas);List<BakedQuad> pinned=changing.getQuads(input(pose,8,1,2),null,0);long stored=changingBake.cachedResults();Set<Integer> animationOutputs=new HashSet<>();
        for(net.minecraftforge.common.model.IModelState animation:new net.minecraftforge.common.model.IModelState[]{null,ModelRotation.X0_Y0,ModelRotation.X0_Y90,ModelRotation.X0_Y180})for(EnumFacing side:SIDES){
            IBlockState state=animated(pose,animation);List<BakedQuad> actual=changing.getQuads(state,side,0);same(animationReference.getQuads(state,side,0),actual);if(side==null)animationOutputs.add(signature(actual));
        }
        need(animationOutputs.size()>1&&changingBake.cachedResults()==stored,"Forge animation was frozen or admitted to the ordinary profile cache");need(changing.getQuads(input(pose,8,1,2),null,0)==pinned,"animation displaced the ordinary static result");
        IBakedModel weighted=new WeightedBakedModel.Builder().add(straight,1).add(inner,2).build();ProgrammableStairsModel.Bake weightedBake=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM);ProgrammableStairsModel weightedModel=weightedBake.wrap(weighted);ReferenceStairRetextureModel weightedReference=new ReferenceStairRetextureModel(weighted,atlas);Set<Integer> selected=new HashSet<>();
        for(int i=0;i<24;i++)for(EnumFacing side:SIDES){long seed=((long)i)<<16;IBlockState configured=input(pose,8,1,2);List<BakedQuad> actual=weightedModel.getQuads(configured,side,seed);same(weightedReference.getQuads(configured,side,seed),actual);if(side==null)selected.add(signature(actual));}
        need(selected.size()>1&&weightedBake.cachedResults()==0,"weighted source lost seed selection or was cached as static");
        IBakedModel inventory=loader.bake("vandorlabs:item/programmable_stairs",0,0,false,atlas);ProgrammableStairsModel item=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM).wrap(inventory);ReferenceStairRetextureModel oldItem=new ReferenceStairRetextureModel(inventory,atlas);ItemStack stack=new ItemStack(net.minecraft.init.Items.STICK);
        for(int tread:new int[]{1,8,16})for(int layout=0;layout<3;layout++){
            stack.setTagInfo("BlockEntityTag",itemTag(tread,2,layout,true));IBakedModel before=oldItem.getOverrides().handleItemState(oldItem,stack,null,null),after=item.getOverrides().handleItemState(item,stack,null,null);
            for(EnumFacing side:SIDES)same(before.getQuads(null,side,0),after.getQuads(null,side,0));sameTransforms(before.getItemCameraTransforms(),after.getItemCameraTransforms());
        }
        System.out.println("stair-retexture actual-forge PASS authored JSON variants="+variantsTested+", UV-lock/rotations/culling records, wrapper cache admission, active/null AnimationProperty bypass, weighted seeds and actual inventory padding");
        IBlockState nativeInput=input(pose,8,1,2);ReferenceStairRetextureModel previous=new ReferenceStairRetextureModel(straight,atlas);ProgrammableStairsModel current=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM).wrap(straight);
        measure("forge-native-straight",16384,count->{for(int i=0;i<count;i++)consumed=previous.getQuads(nativeInput,null,i);},count->{for(int i=0;i<count;i++)consumed=current.getQuads(nativeInput,null,i);});
        measure("forge-native-seven-sides",4096,count->{for(int i=0;i<count;i++)for(EnumFacing side:SIDES)consumed=previous.getQuads(nativeInput,side,i);},count->{for(int i=0;i<count;i++)for(EnumFacing side:SIDES)consumed=current.getQuads(nativeInput,side,i);});
        IBlockState fine=input(pose,1|(2<<5),1,2);measure("forge-fine-rolled",512,count->{for(int i=0;i<count;i++)consumed=previous.getQuads(fine,null,i);},count->{for(int i=0;i<count;i++)consumed=current.getQuads(fine,null,i);});
        stack.setTagInfo("BlockEntityTag",itemTag(1,2,2,true));measure("forge-configured-item",64,count->{for(int i=0;i<count;i++)consumed=oldItem.getOverrides().handleItemState(oldItem,stack,null,null).getQuads(null,null,0);},count->{for(int i=0;i<count;i++)consumed=item.getOverrides().handleItemState(item,stack,null,null).getQuads(null,null,0);});
    }
    private static void parity(){
        Atlas atlas=new Atlas(0);ProgrammableStairsModel.Bake bake=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM);
        for(IBlockState state:ModBlocks.PROGRAMMABLE_STAIRS.getBlockState().getValidStates()){
            IBakedModel source=model(state,atlas.apply("particle"));ReferenceStairRetextureModel previous=new ReferenceStairRetextureModel(source,atlas);ProgrammableStairsModel current=bake.wrap(source);
            for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++)for(int profile=0;profile<4;profile++)for(int layout=0;layout<3;layout++){
                IBlockState configured=input(state,tread|(roll<<5),profile,layout);
                for(EnumFacing side:SIDES){
                    List<BakedQuad> actual=current.getQuads(configured,side,0);same(previous.getQuads(configured,side,0),actual);
                    need(actual==current.getQuads(input(state,tread|(roll<<5),profile,layout),side,971),"equal immutable appearance did not reuse output");
                    if(!actual.isEmpty())try{actual.set(0,actual.get(0));throw new AssertionError("cached result list is mutable");}catch(UnsupportedOperationException expected){}
                }
            }
            for(EnumFacing side:SIDES)same(previous.getQuads(null,side,2),current.getQuads(null,side,2));
            need(current.isAmbientOcclusion()==previous.isAmbientOcclusion()&&current.isGui3d()==previous.isGui3d()&&!current.isBuiltInRenderer(),"delegate model flags changed");
        }
        need(bake.cachedResultBytes()<=ProgrammableStairsModel.MAX_RESULT_BYTES&&bake.cachedGeometryBytes()<=ProgrammableStairsModel.MAX_GEOMETRY_BYTES,"matrix exceeded cache byte budget");
        System.out.println("stair-retexture parity PASS all 600 poses, 4 appearance profiles, 3 layouts, 7 culling sides, default items and exact complete records; comparisons="+comparisons.get());
    }
    private static void modelAndSeedControls(){
        Atlas atlas=new Atlas(0);IBlockState shape=shape(EnumFacing.EAST,BlockStairs.EnumHalf.BOTTOM,BlockStairs.EnumShape.STRAIGHT);
        IBlockState configured=input(shape,8,1,2);ProgrammableStairsModel.Bake bake=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM);
        SimpleBakedModel first=model(shape,atlas.apply("source-a")),second=model(shape.withProperty(BlockStairs.HALF,BlockStairs.EnumHalf.TOP),atlas.apply("source-b"));
        ProgrammableStairsModel a=bake.wrap(first),b=bake.wrap(second);
        for(EnumFacing side:SIDES){same(new ReferenceStairRetextureModel(first,atlas).getQuads(configured,side,17),a.getQuads(configured,side,17));same(new ReferenceStairRetextureModel(second,atlas).getQuads(configured,side,17),b.getQuads(configured,side,17));}
        AtomicInteger calls=new AtomicInteger();
        SimpleBakedModel dynamic=new SimpleBakedModel(Collections.emptyList(),new EnumMap<>(EnumFacing.class),true,true,first.getParticleTexture(),ItemCameraTransforms.DEFAULT,new ItemOverrideList(Collections.emptyList())){
            @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){calls.incrementAndGet();return ((seed+(state.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP?1:0))&1)==0?first.getQuads(state,side,seed):second.getQuads(state,side,seed);}
        };
        ProgrammableStairsModel varying=bake.wrap(dynamic);ReferenceStairRetextureModel expected=new ReferenceStairRetextureModel(dynamic,atlas);long stored=bake.cachedResults();
        for(int i=0;i<32;i++)for(EnumFacing side:SIDES){IBlockState state=configured.withProperty(BlockStairs.HALF,(i&2)==0?BlockStairs.EnumHalf.BOTTOM:BlockStairs.EnumHalf.TOP);same(expected.getQuads(state,side,i),varying.getQuads(state,side,i));}
        need(calls.get()==32*7*2,"dynamic delegate stopped observing seed/state calls");need(bake.cachedResults()==stored,"dynamic delegate was admitted to a full-result cache");
        // An apparently static SimpleBakedModel can still contain a dynamic quad subclass.
        BakedQuad original=first.getQuads(configured,null,0).get(0);AtomicInteger version=new AtomicInteger();
        BakedQuad moving=new BakedQuad(original.getVertexData(),original.getTintIndex(),original.getFace(),original.getSprite(),true,DefaultVertexFormats.ITEM){
            @Override public int[] getVertexData(){int[] data=super.getVertexData().clone();data[0]=Float.floatToRawIntBits(version.get()*.125F);return data;}
        };
        SimpleBakedModel movable=model(Collections.singletonList(moving),original.getSprite());ProgrammableStairsModel movingModel=bake.wrap(movable);
        for(int value=0;value<4;value++){version.set(value);same(new ReferenceStairRetextureModel(movable,atlas).getQuads(configured,null,0),movingModel.getQuads(configured,null,0));}
        need(bake.cachedResults()==stored,"dynamic quad was cached through its static container");
        AtomicInteger particleVersion=new AtomicInteger(),particleCalls=new AtomicInteger();
        SimpleBakedModel particles=new SimpleBakedModel(Collections.emptyList(),new EnumMap<>(EnumFacing.class),true,true,first.getParticleTexture(),ItemCameraTransforms.DEFAULT,new ItemOverrideList(Collections.emptyList())){
            @Override public TextureAtlasSprite getParticleTexture(){
                particleCalls.incrementAndGet();TextureAtlasSprite sprite=new TextureAtlasSprite("ephemeral-particle"){};sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(256,256,particleVersion.get()*16,0,false);return sprite;
            }
        };
        ProgrammableStairsModel.Bake particleBake=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM);ProgrammableStairsModel particleModel=particleBake.wrap(particles);
        IBlockState custom=input(shape,4|(1<<5),2,2);
        for(int versionValue=0;versionValue<5;versionValue++){particleVersion.set(versionValue);same(new ReferenceStairRetextureModel(particles,atlas).getQuads(custom,null,0),particleModel.getQuads(custom,null,0));}
        need(particleCalls.get()==10&&particleBake.cachedResults()==0&&particleBake.cachedGeometries()==0,"dynamic/non-atlas particle sprites were retained");
        SimpleBakedModel empty=model(Collections.emptyList(),atlas.apply("empty"));ProgrammableStairsModel emptyModel=bake.wrap(empty);
        for(EnumFacing side:SIDES){need(emptyModel.getQuads(configured,side,0).isEmpty(),"empty native culling source changed");need(emptyModel.getQuads(null,side,0).isEmpty(),"empty unconfigured item source changed");}
        for(int invalid:new int[]{0,3,12,17,31,32,96,-1,Integer.MIN_VALUE,Integer.MAX_VALUE})for(EnumFacing side:SIDES){
            IBlockState input=input(shape,invalid,2,2);List<BakedQuad> result;
            try{result=new ReferenceStairRetextureModel(first,atlas).getQuads(input,side,0);}
            catch(RuntimeException failure){
                try{a.getQuads(input,side,0);throw new AssertionError("invalid packed settings stopped rejecting "+invalid);}
                catch(RuntimeException actual){need(failure.getClass()==actual.getClass()&&Objects.equals(failure.getMessage(),actual.getMessage()),"invalid packed settings changed failure");}
                continue;
            }
            same(result,a.getQuads(input,side,0));
        }
        System.out.println("stair-retexture source PASS independent models/sprites, dynamic model/quad/particle negative controls, seed/state calls, empty sources and invalid packed settings");
    }
    private static VertexFormat expanded(){return new VertexFormat(DefaultVertexFormats.ITEM).addElement(DefaultVertexFormats.TEX_2S)
            .addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.GENERIC,4));}
    private static VertexFormat reordered(){return new VertexFormat().addElement(DefaultVertexFormats.POSITION_3F).addElement(DefaultVertexFormats.TEX_2F)
            .addElement(DefaultVertexFormats.COLOR_4UB).addElement(DefaultVertexFormats.NORMAL_3B).addElement(DefaultVertexFormats.PADDING_1B);}
    private static void fields(List<BakedQuad> narrow,List<BakedQuad> changed,VertexFormat format){
        need(narrow.size()==changed.size(),"format switch changed geometry count");
        for(int i=0;i<narrow.size();i++){
            BakedQuad a=narrow.get(i),b=changed.get(i);int[] x=a.getVertexData(),y=b.getVertexData();int stride=format.getIntegerSize();
            need(b.getFormat().equals(format)&&y.length==4*stride,"quad format/record width inconsistent");
            need(a.getFace()==b.getFace()&&a.getSprite()==b.getSprite()&&a.getTintIndex()==b.getTintIndex()&&a.shouldApplyDiffuseLighting()==b.shouldApplyDiffuseLighting(),"format switch lost metadata");
            for(int vertex=0;vertex<4;vertex++){
                for(int axis=0;axis<3;axis++)need(x[vertex*7+axis]==y[vertex*stride+axis],"position changed with format");
                need(x[vertex*7+3]==y[vertex*stride+format.getColorOffset()/4],"color changed with format");
                for(int uv=0;uv<2;uv++)need(x[vertex*7+4+uv]==y[vertex*stride+format.getUvOffsetById(0)/4+uv],"UV changed with format");
                need(x[vertex*7+6]==y[vertex*stride+format.getNormalOffset()/4],"normal changed with format");
            }
        }
    }
    private static void formatsAndResources()throws Exception {
        Atlas atlas=new Atlas(0);AtomicReference<VertexFormat> live=new AtomicReference<>(DefaultVertexFormats.ITEM);
        IBlockState shape=shape(EnumFacing.NORTH,BlockStairs.EnumHalf.TOP,BlockStairs.EnumShape.INNER_LEFT),configured=input(shape,1|(2<<5),1,2);
        SimpleBakedModel source=model(shape,atlas.apply("particle"));ProgrammableStairsModel.Bake bake=new ProgrammableStairsModel.Bake(atlas,live::get);ProgrammableStairsModel current=bake.wrap(source);
        List<BakedQuad> narrow=current.getQuads(configured,null,0);VertexFormat wideFormat=expanded();live.set(wideFormat);List<BakedQuad> wide=current.getQuads(configured,null,0);fields(narrow,wide,wideFormat);
        need(wide!=narrow&&current.getQuads(configured,null,4)==wide,"format switch did not replace/reuse its generation");
        VertexFormat moved=reordered();live.set(moved);fields(narrow,current.getQuads(configured,null,0),moved);
        live.set(DefaultVertexFormats.ITEM);List<BakedQuad> restored=current.getQuads(configured,null,0);same(narrow,restored);need(restored!=narrow&&restored!=wide,"restoration resurrected a retired generation");
        // The same mutable global format object can change in place, as shader loaders do.
        VertexFormat mutable=new VertexFormat(DefaultVertexFormats.ITEM);live.set(mutable);List<BakedQuad> beforeMutation=current.getQuads(configured,null,0);mutable.addElement(DefaultVertexFormats.TEX_2S);List<BakedQuad> afterMutation=current.getQuads(configured,null,0);fields(narrow,afterMutation,mutable);same(narrow,beforeMutation);need(beforeMutation!=afterMutation,"in-place format mutation was not detected");
        Atlas nextAtlas=new Atlas(11);ProgrammableStairsModel.Bake nextBake=new ProgrammableStairsModel.Bake(nextAtlas,()->DefaultVertexFormats.ITEM);
        SimpleBakedModel nextSource=model(shape,nextAtlas.apply("particle"));List<BakedQuad> reloaded=nextBake.wrap(nextSource).getQuads(configured,null,0);
        same(new ReferenceStairRetextureModel(nextSource,nextAtlas).getQuads(configured,null,0),reloaded);need(reloaded.get(0).getSprite()!=narrow.get(0).getSprite(),"resource rebake reused an old atlas sprite");same(narrow,restored);
        // Preserve every non-UV word of a native extended record, including packed light and shader payload.
        VertexFormat nativeFormat=expanded();BakedQuad ordinary=source.getQuads(configured,null,0).get(0);int[] plain=ordinary.getVertexData(),payload=new int[4*nativeFormat.getIntegerSize()];
        for(int vertex=0;vertex<4;vertex++){System.arraycopy(plain,vertex*7,payload,vertex*nativeFormat.getIntegerSize(),7);for(int extra=7;extra<nativeFormat.getIntegerSize();extra++)payload[vertex*nativeFormat.getIntegerSize()+extra]=0x62000000+vertex*31+extra;}
        SimpleBakedModel extendedSource=model(Collections.singletonList(new BakedQuad(payload,7,ordinary.getFace(),ordinary.getSprite(),false,nativeFormat)),ordinary.getSprite());
        ProgrammableStairsModel.Bake extendedBake=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM);ProgrammableStairsModel extendedModel=extendedBake.wrap(extendedSource);IBlockState vanilla=input(shape,8,2,1);
        List<BakedQuad> expected=new ReferenceStairRetextureModel(extendedSource,atlas).getQuads(vanilla,null,0),actual=extendedModel.getQuads(vanilla,null,0);same(expected,actual);need(actual!=extendedModel.getQuads(vanilla,null,0)&&extendedBake.cachedResults()==0,"independent nonstandard source format was cached");
        // OptiFine/legacy adapters may expose expanded records with the old stored
        // format (or compact records with an extended format). Do not introduce a
        // new exception or infer an undocumented shader layout for those sources.
        for(boolean widened:new boolean[]{true,false}){
            int stride=widened?14:7;int[] legacy=new int[4*stride];
            for(int vertex=0;vertex<4;vertex++){System.arraycopy(plain,vertex*7,legacy,vertex*stride,7);for(int extra=7;extra<stride;extra++)legacy[vertex*stride+extra]=0x41000000+vertex*31+extra;}
            SimpleBakedModel legacySource=model(Collections.singletonList(new BakedQuad(legacy,7,ordinary.getFace(),ordinary.getSprite(),false,widened?DefaultVertexFormats.ITEM:nativeFormat)),ordinary.getSprite());
            ProgrammableStairsModel.Bake legacyBake=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM);ProgrammableStairsModel legacyModel=legacyBake.wrap(legacySource);
            List<BakedQuad> oldLegacy=new ReferenceStairRetextureModel(legacySource,atlas).getQuads(vanilla,null,0),newLegacy=legacyModel.getQuads(vanilla,null,0);same(oldLegacy,newLegacy);
            need(newLegacy!=legacyModel.getQuads(vanilla,null,0)&&legacyBake.cachedResults()==0,"mismatched legacy records entered the retained result cache");
        }
        oldWorkerFormat(atlas,source,configured);
        System.out.println("stair-retexture lifecycle PASS expanded/reordered/restored/in-place formats, immutable retired records, native extended payload, mismatched legacy records, resource rebake and in-flight retirement");
    }
    private static void oldWorkerFormat(Atlas atlas,IBakedModel source,IBlockState input)throws Exception {
        AtomicReference<VertexFormat> live=new AtomicReference<>(DefaultVertexFormats.ITEM);CountDownLatch building=new CountDownLatch(1),resume=new CountDownLatch(1);AtomicBoolean paused=new AtomicBoolean();
        Function<String,TextureAtlasSprite> delayed=name->{if(Thread.currentThread().getName().equals("stair-old-format")&&paused.compareAndSet(false,true)){
            building.countDown();try{need(resume.await(10,TimeUnit.SECONDS),"old stair worker was not resumed");}catch(InterruptedException failure){throw new AssertionError(failure);}}
            return atlas.apply(name);};
        ProgrammableStairsModel.Bake bake=new ProgrammableStairsModel.Bake(delayed,live::get);ProgrammableStairsModel current=bake.wrap(source);ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"stair-old-format"));Future<List<BakedQuad>> pending=worker.submit(()->current.getQuads(input,null,0));
        try{
            need(building.await(10,TimeUnit.SECONDS),"old worker did not reach atlas lookup");VertexFormat expanded=expanded();live.set(expanded);List<BakedQuad> wide=current.getQuads(input,null,0);resume.countDown();List<BakedQuad> narrow=pending.get(10,TimeUnit.SECONDS);fields(narrow,wide,expanded);
            need(current.getQuads(input,null,0)==wide,"late old worker repopulated new result cache");need(bake.cachedResults()==1&&bake.cachedGeometries()==1,"retired worker repopulated new source cache");
        }finally{resume.countDown();worker.shutdownNow();}
    }
    private static NBTTagCompound itemTag(int tread,int roll,int layout,boolean enabled){
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger(SaveSchema.Screen.HOUSING_TEXTURE,9);tag.setInteger("SideTexture",4);tag.setBoolean("FaceTexturesEnabled",enabled);tag.setIntArray("FaceTextures",new int[]{1,2,3,4,5,6});tag.setInteger("MainTextureLayout",layout);tag.setInteger(StairState.TREAD,tread);tag.setInteger(StairState.ROLL,roll);return tag;
    }
    private static void sameTransforms(ItemCameraTransforms a,ItemCameraTransforms b){
        for(ItemCameraTransforms.TransformType type:ItemCameraTransforms.TransformType.values()){
            ItemTransformVec3f x=a.getTransform(type),y=b.getTransform(type);need(x.rotation.equals(y.rotation)&&x.translation.equals(y.translation)&&x.scale.equals(y.scale),"configured item padding changed: "+type);
        }
    }
    private static void items(){
        Atlas atlas=new Atlas(0);IBlockState shape=shape(EnumFacing.EAST,BlockStairs.EnumHalf.BOTTOM,BlockStairs.EnumShape.STRAIGHT);IBakedModel source=model(shape,atlas.apply("particle"));
        ProgrammableStairsModel.Bake bake=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM);ProgrammableStairsModel current=bake.wrap(source);ReferenceStairRetextureModel previous=new ReferenceStairRetextureModel(source,atlas);ItemStack stack=new ItemStack(net.minecraft.init.Items.STICK);
        need(current.getOverrides()==current.getOverrides(),"override handler churned");need(current.getOverrides().handleItemState(current,stack,null,null)==current,"untagged item did not retain original model");int count=0;
        for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++)for(int layout=0;layout<3;layout++)for(boolean enabled:new boolean[]{false,true}){
            NBTTagCompound tag=itemTag(tread,roll,layout,enabled);stack.setTagInfo("BlockEntityTag",tag);NBTTagCompound untouched=tag.copy();
            IBakedModel before=previous.getOverrides().handleItemState(previous,stack,null,null),after=current.getOverrides().handleItemState(current,stack,null,null),again=current.getOverrides().handleItemState(current,stack,null,null);
            for(EnumFacing side:SIDES){List<BakedQuad> expected=before.getQuads(null,side,0),actual=after.getQuads(null,side,0);same(expected,actual);need(actual==again.getQuads(null,side,0),"configured item wrappers did not share results");}
            sameTransforms(before.getItemCameraTransforms(),after.getItemCameraTransforms());need(tag.equals(untouched),"item resolution changed caller NBT");count++;
        }
        NBTTagCompound tag=itemTag(1,2,2,true);stack.setTagInfo("BlockEntityTag",tag);IBakedModel frozen=current.getOverrides().handleItemState(current,stack,null,null);List<BakedQuad> frozenQuads=frozen.getQuads(null,null,0);int[] saved=frozenQuads.get(0).getVertexData().clone();
        tag.setInteger(SaveSchema.Screen.HOUSING_TEXTURE,3);tag.setInteger(StairState.TREAD,16);tag.setIntArray("FaceTextures",new int[]{6,5,4,3,2,1});
        IBakedModel changed=current.getOverrides().handleItemState(current,stack,null,null);same(previous.getOverrides().handleItemState(previous,stack,null,null).getQuads(null,null,0),changed.getQuads(null,null,0));need(Arrays.equals(saved,frozen.getQuads(null,null,0).get(0).getVertexData()),"mutating input NBT changed a prior item snapshot");
        // Legacy/typed fallback, out-of-range values and absent face arrays still use the released parser.
        for(int kind=0;kind<4;kind++){
            NBTTagCompound legacy=new NBTTagCompound();if(kind==1)legacy.setByte(StairState.TREAD,(byte)4);if(kind==2){legacy.setInteger(StairState.TREAD,3);legacy.setInteger(StairState.ROLL,-4);}if(kind==3){legacy.setInteger(StairState.TREAD,16);legacy.setInteger(StairState.ROLL,99);legacy.setBoolean("SlabTileSides",true);}stack.setTagInfo("BlockEntityTag",legacy);
            IBakedModel before=previous.getOverrides().handleItemState(previous,stack,null,null),after=current.getOverrides().handleItemState(current,stack,null,null);for(EnumFacing side:SIDES)same(before.getQuads(null,side,0),after.getQuads(null,side,0));
        }
        System.out.println("stair-retexture items PASS configured geometry/material/layout/disabled faces, padding, shared outputs, mutable caller NBT and legacy typed/default values; configurations="+count);
    }
    private static void retention(){
        Atlas atlas=new Atlas(0);IBlockState shape=shape(EnumFacing.NORTH,BlockStairs.EnumHalf.BOTTOM,BlockStairs.EnumShape.INNER_LEFT);IBakedModel source=model(shape,atlas.apply("particle"));
        ReferenceStairRetextureModel previous=new ReferenceStairRetextureModel(source,atlas);ProgrammableStairsModel.Bake bake=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM);ProgrammableStairsModel current=bake.wrap(source);
        int choices=Math.min(ScreenHousingTextures.BUILTIN_COUNT,256),configurations=choices*3;long sourceBytes=0,previousSources=0,firstVariants=0;
        for(int i=0;i<configurations;i++){
            IBlockState configured=input(shape,1,i%choices,-1,i/choices,FaceTextures.DEFAULT);same(previous.getQuads(configured,null,0),current.getQuads(configured,null,0));
            if(i==0){sourceBytes=bake.cachedGeometryBytes();previousSources=previous.retainedSourceQuads();}
            need(bake.cachedGeometryBytes()==sourceBytes&&bake.cachedGeometries()==1,"material churn created more source geometry");
            if(i==choices-1)firstVariants=previous.retainedVariants();
        }
        need(previous.retainedSourceQuads()==previousSources,"reference material churn changed geometry identity");need(previous.retainedVariants()==firstVariants*3,"material churn did not exercise three layouts per quad/material");
        need(bake.cachedResultBytes()<=ProgrammableStairsModel.MAX_RESULT_BYTES&&bake.cachedResults()<configurations,"material churn did not saturate the bounded result cache");
        System.out.println("stair-retexture material-churn PASS profiles="+configurations+" previous-source-quads="+previousSources+" previous-variants="+previous.retainedVariants()+" previous-estimated-owned-bytes="+previous.estimatedRetainedBytes()+" current-results="+bake.cachedResults()+" current-result-estimated-bytes="+bake.cachedResultBytes()+" current-geometries="+bake.cachedGeometries()+" current-geometry-estimated-bytes="+sourceBytes);
        // This is a separate valid-pose stress of one wrapper's public API, not ordinary
        // material edits: it crosses the old per-wrapper 512-source-list admission limit.
        previous=new ReferenceStairRetextureModel(source,atlas);bake=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM);current=bake.wrap(source);int poses=0;
        for(IBlockState pose:ModBlocks.PROGRAMMABLE_STAIRS.getBlockState().getValidStates())for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++)if((tread|(roll<<5))!=8){
            IBlockState configured=input(pose,tread|(roll<<5),2,1);same(previous.getQuads(configured,null,0),current.getQuads(configured,null,0));poses++;
        }
        need(previous.geometryEntries()<=512&&previous.geometryEntries()<poses,"geometry stress did not evict reference source lists");need(previous.retainedSourceQuads()>previous.activeSourceQuads(),"reference retexture map did not retain evicted source quads");
        need(bake.cachedGeometryBytes()<=ProgrammableStairsModel.MAX_GEOMETRY_BYTES&&bake.cachedGeometries()<poses,"geometry cache did not respect its byte limit");need(bake.cachedResultBytes()<=ProgrammableStairsModel.MAX_RESULT_BYTES,"geometry churn exceeded result budget");
        System.out.println("stair-retexture geometry-eviction PASS poses="+poses+" previous-active-lists="+previous.geometryEntries()+" previous-active-source-quads="+previous.activeSourceQuads()+" previous-retained-source-quads="+previous.retainedSourceQuads()+" previous-estimated-owned-bytes="+previous.estimatedRetainedBytes()+" current-results="+bake.cachedResults()+" current-result-estimated-bytes="+bake.cachedResultBytes()+" current-geometries="+bake.cachedGeometries()+" current-geometry-estimated-bytes="+bake.cachedGeometryBytes());
    }
    private static void concurrent()throws Exception {
        Atlas atlas=new Atlas(0);IBlockState shape=shape(EnumFacing.WEST,BlockStairs.EnumHalf.TOP,BlockStairs.EnumShape.OUTER_RIGHT);IBakedModel source=model(shape,atlas.apply("particle"));
        ProgrammableStairsModel.Bake bake=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM);ProgrammableStairsModel[] wrappers=new ProgrammableStairsModel[8];for(int i=0;i<wrappers.length;i++)wrappers[i]=bake.wrap(source);
        ReferenceStairRetextureModel previous=new ReferenceStairRetextureModel(source,atlas);List<IBlockState> inputs=new ArrayList<>();List<List<BakedQuad>> expected=new ArrayList<>();
        for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++)for(int profile=0;profile<3;profile++){
            IBlockState input=input(shape,tread|(roll<<5),profile,profile);inputs.add(input);expected.add(previous.getQuads(input,null,0));
        }
        ExecutorService workers=Executors.newFixedThreadPool(8);CountDownLatch start=new CountDownLatch(1);List<Future<?>> jobs=new ArrayList<>();
        try{for(int worker=0;worker<8;worker++){final int index=worker;jobs.add(workers.submit(()->{start.await();Random random=new Random(index);for(int n=0;n<1800;n++){int at=random.nextInt(inputs.size());same(expected.get(at),wrappers[index].getQuads(inputs.get(at),null,n));}return null;}));}start.countDown();for(Future<?> job:jobs)job.get();}
        finally{workers.shutdownNow();}
        need(bake.cachedResultBytes()<=ProgrammableStairsModel.MAX_RESULT_BYTES&&bake.cachedGeometryBytes()<=ProgrammableStairsModel.MAX_GEOMETRY_BYTES,"concurrent publication exceeded budgets");
        List<BakedQuad> pinned=wrappers[0].getQuads(inputs.get(0),null,0);for(ProgrammableStairsModel wrapper:wrappers)need(wrapper.getQuads(inputs.get(0),null,0)==pinned,"wrappers did not share a canonical published result");
        // Exceed the result budget while all workers admit different fine-stair
        // materials. This exercises concurrent eviction, not just cold insertion.
        List<IBlockState> pressure=new ArrayList<>();List<List<BakedQuad>> pressureExpected=new ArrayList<>();
        for(int texture=0;texture<96;texture++)for(int layout=0;layout<3;layout++){
            IBlockState state=input(shape,1|(2<<5),texture,-1,layout,FaceTextures.DEFAULT);pressure.add(state);pressureExpected.add(previous.getQuads(state,null,0));
        }
        ProgrammableStairsModel.Bake crowdedBake=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM);ProgrammableStairsModel crowded=crowdedBake.wrap(source);
        ExecutorService admissionWorkers=Executors.newFixedThreadPool(8);CountDownLatch admit=new CountDownLatch(1);List<Future<?>> admissions=new ArrayList<>();
        try{for(int worker=0;worker<8;worker++){final int index=worker;admissions.add(admissionWorkers.submit(()->{admit.await();for(int n=0;n<240;n++){
            int at=(n*37+index*11)%pressure.size();same(pressureExpected.get(at),crowded.getQuads(pressure.get(at),null,n));need(crowdedBake.cachedResultBytes()<=ProgrammableStairsModel.MAX_RESULT_BYTES,"concurrent eviction exceeded weighted budget");
        }return null;}));}admit.countDown();for(Future<?> admission:admissions)admission.get();}finally{admissionWorkers.shutdownNow();}
        need(crowdedBake.cachedResults()<pressure.size()&&crowdedBake.cachedGeometries()==1,"concurrent pressure did not evict result profiles independently of geometry");
        System.out.println("stair-retexture concurrency PASS eight cold/evicting workers, mixed pose/material records, shared canonical results and aggregate cache budgets");
    }
    private interface Work {void run(int count);}
    private static void measure(String profile,int calls,Work old,Work current){
        Work[] work={old,current};long[][] wall=new long[2][21],cpu=new long[2][21],allocation=new long[2][21];
        for(int batch=-25;batch<21;batch++)for(int order=0;order<2;order++){
            int algorithm=(batch+order)&1;long memory=bytes(),thread=METER.getCurrentThreadCpuTime(),start=System.nanoTime();work[algorithm].run(calls);
            long elapsed=System.nanoTime()-start,used=METER.getCurrentThreadCpuTime()-thread,allocated=bytes()-memory;
            if(batch>=0){wall[algorithm][batch]=elapsed;cpu[algorithm][batch]=used;allocation[algorithm][batch]=allocated;}
        }
        for(int algorithm=0;algorithm<2;algorithm++){
            Arrays.sort(wall[algorithm]);Arrays.sort(cpu[algorithm]);Arrays.sort(allocation[algorithm]);
            System.out.printf(Locale.ROOT,"stair-retexture profile=%s algorithm=%s calls=%d wall-median-ns=%d cpu-median-ns=%d allocated-median-bytes=%d%n",profile,algorithm==0?"previous":"bounded-results",calls,wall[algorithm][10],cpu[algorithm][10],allocation[algorithm][10]);
        }
        need(allocation[1][10]<allocation[0][10],"warm result reuse did not reduce allocation: "+profile);
    }
    private static void costs(){
        Atlas atlas=new Atlas(0);IBlockState shape=shape(EnumFacing.NORTH,BlockStairs.EnumHalf.BOTTOM,BlockStairs.EnumShape.INNER_LEFT);IBakedModel source=model(shape,atlas.apply("particle"));
        for(int settings:new int[]{8,1|(2<<5)}){
            IBlockState input=input(shape,settings,1,2);ReferenceStairRetextureModel previous=new ReferenceStairRetextureModel(source,atlas);ProgrammableStairsModel current=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM).wrap(source);
            same(previous.getQuads(input,null,0),current.getQuads(input,null,0));measure(settings==8?"warm-native":"warm-fine-rolled",4096,count->{for(int i=0;i<count;i++)consumed=previous.getQuads(input,null,i);},count->{for(int i=0;i<count;i++)consumed=current.getQuads(input,null,i);});
        }
        IBlockState nativeInput=input(shape,8,1,2);ReferenceStairRetextureModel oldNative=new ReferenceStairRetextureModel(source,atlas);ProgrammableStairsModel newNative=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM).wrap(source);
        for(EnumFacing side:SIDES)same(oldNative.getQuads(nativeInput,side,0),newNative.getQuads(nativeInput,side,0));
        measure("warm-native-seven-sides",1024,count->{for(int i=0;i<count;i++)for(EnumFacing side:SIDES)consumed=oldNative.getQuads(nativeInput,side,i);},count->{for(int i=0;i<count;i++)for(EnumFacing side:SIDES)consumed=newNative.getQuads(nativeInput,side,i);});
        ReferenceStairRetextureModel previous=new ReferenceStairRetextureModel(source,atlas);ProgrammableStairsModel current=new ProgrammableStairsModel.Bake(atlas,()->DefaultVertexFormats.ITEM).wrap(source);ItemStack stack=new ItemStack(net.minecraft.init.Items.STICK);stack.setTagInfo("BlockEntityTag",itemTag(1,2,2,true));
        measure("configured-item-resolution",64,count->{for(int i=0;i<count;i++)consumed=previous.getOverrides().handleItemState(previous,stack,null,null).getQuads(null,null,0);},count->{for(int i=0;i<count;i++)consumed=current.getOverrides().handleItemState(current,stack,null,null).getQuads(null,null,0);});
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();METER.setThreadAllocatedMemoryEnabled(true);METER.setThreadCpuTimeEnabled(true);
        forgeSources();if(args.length==1&&args[0].equals("forge-only"))return;
        parity();modelAndSeedControls();formatsAndResources();items();retention();concurrent();costs();
        System.out.println("stair-retexture PASS frozen native-format renderer parity, bounded per-bake caches and 25 warmup/21 alternating measured preparation batches; estimates exclude Minecraft-owned models/atlas and transient workers, timings are not hardware FPS");
    }
}

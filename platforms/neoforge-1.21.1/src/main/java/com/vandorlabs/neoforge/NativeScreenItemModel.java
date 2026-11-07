package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.screen.NativeScreenItemSettings;
import com.vandorlabs.render.ScreenDiagonalItemMesh;
import com.vandorlabs.screen.ScreenFamily;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Inventory adapter: six authored static templates and the released diagonal control variant. */
public final class NativeScreenItemModel extends BakedModelWrapper<BakedModel> {
    private record Material(String texture,float u,float v){}
    private record Region(TextureAtlasSprite sprite,float u,float v){}
    private static final Map<String,Material> MATERIALS=materials();
    private static Map<String,Material> materials(){var stream=NativeScreenItemModel.class.getResourceAsStream("/assets/vandorlabs/data/screen_item_sprites.json");if(stream==null)throw new IllegalStateException("Missing Screen item sprite mapping");try(var reader=new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)){java.lang.reflect.Type type=new com.google.gson.reflect.TypeToken<Map<String,Material>>(){}.getType();return Map.copyOf(new com.google.gson.Gson().fromJson(reader,type));}catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}}
    /** Complete atlas dependencies include the authored side sprite and vanilla diagonal accents. */
    static Set<String> textures(){var names=new HashSet<String>();for(var material:MATERIALS.values())names.add(material.texture());for(boolean controls:new boolean[]{false,true})for(var quad:ScreenDiagonalItemMesh.quads(controls))if(quad.texture.startsWith("minecraft:blocks/concrete_"))names.add("minecraft:block/"+quad.texture.substring("minecraft:blocks/concrete_".length())+"_concrete");return Set.copyOf(names);}
    private static final Direction[] SIDES={null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    private final boolean diagonal;private final Function<String,TextureAtlasSprite> atlas;private final Map<TextureAtlasSprite,Region> regions;
    private final com.google.common.cache.Cache<CustomData,Boolean> choices=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(256).build();
    private final Selected plain,controls;
    private final com.google.common.cache.Cache<BakedModel,Selected> overrides=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumWeight(4*1024*1024).weigher((BakedModel key,Selected value)->value.bytes).build();
    public NativeScreenItemModel(BakedModel source,ScreenFamily family,Function<String,TextureAtlasSprite> atlas){
        super(source);this.atlas=atlas;diagonal=family==ScreenFamily.DIAGONAL;var mapped=new IdentityHashMap<TextureAtlasSprite,Region>();for(var value:MATERIALS.values()){var sprite=atlas.apply(value.texture());mapped.put(sprite,new Region(sprite,value.u(),value.v()));}regions=Collections.unmodifiableMap(mapped);plain=new Selected(source,false);controls=diagonal?new Selected(source,true):plain;
    }
    private boolean controls(ItemStack stack){var component=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(component==null)component=stack.get(DataComponents.CUSTOM_DATA);if(component==null)return false;var present=choices.getIfPresent(component);if(present!=null)return present;boolean value=false;try{var raw=NativeScreenItemSettings.configuration(stack);value=raw!=null&&raw.getCompound("RedstonePrimary").getBoolean("Enabled");}catch(IllegalArgumentException malformed){/* A malformed icon must not alter its retained item. */}choices.put(component,value);return value;}
    private final ItemOverrides selector=new ItemOverrides(){@Override public BakedModel resolve(BakedModel original,ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){if(diagonal)return controls(stack)?controls:plain;var resolved=originalModel.getOverrides().resolve(originalModel,stack,level,entity,seed);if(resolved==null||resolved==originalModel)return plain;return selected(resolved);}};
    @Override public ItemOverrides getOverrides(){return selector;}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return plain.getQuads(state,side,random);}
    private Selected selected(BakedModel model){var found=overrides.getIfPresent(model);if(found==null){found=new Selected(model,false);if(found.bytes<4*1024*1024)overrides.put(model,found);}return found;}
    private Region region(String original){var mapped=MATERIALS.get(original);if(mapped!=null)return new Region(atlas.apply(mapped.texture()),mapped.u(),mapped.v());if(original.startsWith("minecraft:blocks/concrete_"))return new Region(atlas.apply("minecraft:block/"+original.substring("minecraft:blocks/concrete_".length())+"_concrete"),1,1);throw new IllegalArgumentException("Unknown Source Screen item material "+original);}
    private List<BakedQuad> retarget(List<BakedQuad> source){var result=new ArrayList<BakedQuad>(source.size());for(var quad:source){var region=regions.get(quad.getSprite());result.add(region!=null&&(region.u()!=1||region.v()!=1)?NativeItemQuads.retexture(quad,region.sprite(),region.u(),region.v()):quad);}return List.copyOf(result);}
    private List<BakedQuad> diagonal(boolean controls){var result=new ArrayList<BakedQuad>();for(var source:ScreenDiagonalItemMesh.quads(controls)){var material=region(source.texture);int[] raw=new int[32];for(int vertex=0;vertex<4;vertex++){int at=vertex*8;for(int axis=0;axis<3;axis++)raw[at+axis]=Float.floatToRawIntBits((float)(source.value(vertex,axis)/16));raw[at+3]=-1;raw[at+4]=Float.floatToRawIntBits(material.sprite().getU((float)source.value(vertex,3)/16*material.u()));raw[at+5]=Float.floatToRawIntBits(material.sprite().getV((float)source.value(vertex,4)/16*material.v()));}result.add(new BakedQuad(raw,-1,Direction.from3DDataValue(source.face()),material.sprite(),true,false));}return List.copyOf(result);}
    private final class Selected extends BakedModelWrapper<BakedModel> {
        private final List<BakedQuad>[] faces;private final ItemTransforms transforms;private final List<BakedModel> passes=List.of(this);private final int bytes;
        @SuppressWarnings("unchecked") Selected(BakedModel source,boolean controls){super(source);List<BakedQuad>[] built=diagonal||NativeDoorArtwork.isStatic(source)?new List[7]:null;long size=512;if(built!=null)for(int side=0;side<7;side++){built[side]=diagonal?(side==0?diagonal(controls):List.of()):retarget(source.getQuads(null,SIDES[side],RandomSource.create(42),ModelData.EMPTY,null));for(var quad:built[side])size+=96L+quad.getVertices().length*4L;}faces=!diagonal&&size>4*1024*1024?null:built;bytes=(int)Math.min(Integer.MAX_VALUE,size);transforms=NativeItemPadding.fit(this,source.getTransforms());}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){return faces!=null?faces[side==null?0:side.ordinal()+1]:retarget(originalModel.getQuads(state,side,random,data,layer));}
        @Override public boolean useAmbientOcclusion(){return diagonal?false:originalModel.useAmbientOcclusion();}
        @Override public ItemTransforms getTransforms(){return transforms;}
        @Override public BakedModel applyTransform(ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){transforms.getTransform(context).apply(left,pose);return this;}
        @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){if(faces!=null)return passes;var source=originalModel.getRenderPasses(stack,fabulous);if(source.size()==1&&source.get(0)==originalModel)return passes;var result=new ArrayList<BakedModel>(source.size());for(var model:source)result.add(model==originalModel?this:selected(model));return List.copyOf(result);}
        @Override public ItemOverrides getOverrides(){return ItemOverrides.EMPTY;}
    }
}

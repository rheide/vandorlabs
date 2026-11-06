package com.vandorlabs.neoforge;

import com.google.common.cache.*;
import com.vandorlabs.neoforge.client.NativeBlockMaterials;
import com.vandorlabs.neoforge.light.*;
import com.vandorlabs.tiles.ProgrammableLightTextures;
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

/** Inactive bake-owned selector. Source inventory artwork uses manual saved state, independent of world power. */
public final class NativeLightItemModel extends BakedModelWrapper<BakedModel> {
    private static final int BUDGET=4*1024*1024;
    private static final Direction[] FACES={null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    private record Surface(TextureAtlasSprite sprite,float u,float v){}
    private record Key(BakedModel source,Surface surface){
        @Override public int hashCode(){return System.identityHashCode(source)*31+Objects.hashCode(surface);}
        @Override public boolean equals(Object other){return other instanceof Key key&&source==key.source&&Objects.equals(surface,key.surface);}
    }
    private record Selection(NativeLightItemStyle style,java.lang.ref.WeakReference<Selected> model){}
    private final BakedModel[] sources=new BakedModel[12];private final Set<TextureAtlasSprite> lamps;
    private final Function<String,TextureAtlasSprite> atlas;
    private final Cache<CustomData,Selection> components=CacheBuilder.newBuilder().weakKeys().maximumSize(256).build();
    private final Cache<Key,Selected> meshes=CacheBuilder.newBuilder().maximumWeight(BUDGET).weigher((Key key,Selected model)->model.bytes).build();
    private Selection empty;
    public NativeLightItemModel(BakedModel fallback,NativeLightBlock.Kind kind,Function<String,BakedModel> models,Function<String,TextureAtlasSprite> atlas){
        super(Objects.requireNonNull(fallback));this.atlas=Objects.requireNonNull(atlas);
        String name=switch(kind){case FULL->"programmable_light";case FRAME->"programmable_light_frame";case SLAB->"programmable_light_slab";};
        var sprites=new HashSet<TextureAtlasSprite>();
        for(int style=0;style<ProgrammableLightTextures.IDS.length;style++)for(int lit=0;lit<2;lit++){
            String suffix=ProgrammableLightTextures.IDS[style]+(lit==1?"_on":"_off");
            var model=models.apply("vandorlabs:item/configured/"+name+"_"+suffix);sources[style*2+lit]=model==null?fallback:model;
            String entryId="light_"+ProgrammableLightTextures.IDS[style]+(lit==1?"":"_off");
            var entry=ModernFinishCatalog.ENTRIES.stream().filter(value->value.id().equals(entryId)).findFirst().orElseThrow();sprites.add(atlas.apply(entry.texture()));
        }
        lamps=Set.copyOf(sprites);
    }
    private Selected selected(NativeLightItemStyle style,BakedModel source){
        Surface surface=null;if(style.faceTexture()>=0){var custom=NativeBlockMaterials.material(style.faceTexture());var entry=ModernFinishCatalog.entry(style.faceTexture());surface=new Surface(custom==null?atlas.apply(entry.texture()):custom.lower(),custom==null?entry.u():1,custom==null?entry.v():1);}
        var key=new Key(source,surface);var result=meshes.getIfPresent(key);if(result==null){result=new Selected(source,surface,style);if(result.bytes<=BUDGET)meshes.put(key,result);}return result;
    }
    private Selected selection(ItemStack stack){
        var component=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(component==null)component=stack.get(DataComponents.CUSTOM_DATA);
        var result=component==null?empty:components.getIfPresent(component);
        NativeLightItemStyle style;
        if(result!=null){var found=result.model().get();if(found!=null)return found;style=result.style();}
        else{try{style=NativeLightItemStyle.read(NativeLightItem.configuration(stack));}catch(IllegalArgumentException malformed){style=NativeLightItemStyle.DEFAULT;}}
        var model=selected(style,sources[style.modelKey()]);result=new Selection(style,new java.lang.ref.WeakReference<>(model));
        if(component==null)empty=result;else components.put(component,result);return model;
    }

    private final ItemOverrides selector=new ItemOverrides(){
        @Override public BakedModel resolve(BakedModel original,ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){var base=selection(stack);var source=base.original();var resolved=source.getOverrides().resolve(source,stack,level,entity,seed);return resolved==null||resolved==source?base:selected(base.style,resolved);}
    };
    @Override public ItemOverrides getOverrides(){return selector;}
    private final class Selected extends BakedModelWrapper<BakedModel> {
        private final NativeLightItemStyle style;private final List<BakedQuad>[] faces;private final ItemTransforms transforms;private final Surface surface;private final int bytes;private final List<BakedModel> passes=List.of(this);
        @SuppressWarnings("unchecked") Selected(BakedModel source,Surface surface,NativeLightItemStyle style){
            super(source);this.surface=surface;this.style=style;faces=NativeDoorArtwork.isStatic(source)?new List[FACES.length]:null;long cost=512;
            if(faces!=null)for(int side=0;side<FACES.length;side++){var quads=new ArrayList<BakedQuad>();for(var quad:source.getQuads(null,FACES[side],RandomSource.create(42),ModelData.EMPTY,null)){var changed=surface!=null&&lamps.contains(quad.getSprite())?NativeItemQuads.retexture(quad,surface.sprite(),surface.u(),surface.v()):quad;quads.add(changed);cost+=96L+changed.getVertices().length*4L;}faces[side]=List.copyOf(quads);}
            bytes=(int)Math.min(Integer.MAX_VALUE,cost);transforms=NativeItemPadding.fit(this,source.getTransforms());
        }
        BakedModel original(){return originalModel;}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){if(faces!=null)return faces[side==null?0:side.ordinal()+1];
            var source=originalModel.getQuads(state,side,random,data,layer);if(surface==null)return source;
            var changed=new ArrayList<BakedQuad>(source.size());for(var quad:source)changed.add(lamps.contains(quad.getSprite())?NativeItemQuads.retexture(quad,surface.sprite(),surface.u(),surface.v()):quad);return List.copyOf(changed);}
        @Override public ItemTransforms getTransforms(){return transforms;}
        @Override public BakedModel applyTransform(ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){transforms.getTransform(context).apply(left,pose);return this;}
        @Override public TextureAtlasSprite getParticleIcon(){return surface==null?originalModel.getParticleIcon():surface.sprite();}
        @Override public TextureAtlasSprite getParticleIcon(ModelData data){return surface==null?originalModel.getParticleIcon(data):surface.sprite();}
        @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){if(faces!=null)return passes;var source=originalModel.getRenderPasses(stack,fabulous);if(source.size()==1&&source.get(0)==originalModel)return passes;var result=new ArrayList<BakedModel>(source.size());for(var model:source)result.add(model==originalModel?this:selected(style,model));return List.copyOf(result);}
        @Override public ItemOverrides getOverrides(){return ItemOverrides.EMPTY;}
    }
}

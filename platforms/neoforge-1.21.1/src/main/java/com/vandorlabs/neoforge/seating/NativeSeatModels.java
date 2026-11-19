package com.vandorlabs.neoforge;

import com.google.common.cache.*;
import com.vandorlabs.neoforge.seating.*;
import com.vandorlabs.neoforge.seating.client.NativeSeatScreen;
import com.vandorlabs.persistence.ChairSettings;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.*;

/** Immutable authored body selections; no permanent renderer or per-frame NBT preparation. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeSeatModels {
    private static volatile Bake active;
    public record CacheDiagnostics(long retainedMeshBytes,long descriptorBytes,long maxMeshBytes,long itemBuilds,long descriptorHits){}
    public static CacheDiagnostics cacheDiagnostics(){var bake=active;if(bake==null)return new CacheDiagnostics(0,0,0,0,0);bake.selected.cleanUp();var retained=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Selected,Boolean>());retained.addAll(bake.selected.asMap().values());long descriptors=0,defaults=0;for(var item:bake.items){item.components.cleanUp();retained.add(item.plain);defaults+=item.plain.bytes;descriptors+=item.components.size()*256L;}long bytes=0;for(var model:retained)bytes+=model.bytes;return new CacheDiagnostics(bytes,descriptors,16L*1024*1024+defaults,bake.itemBuilds,bake.descriptorHits);}
    private static final ModelProperty<Key> KEY=new ModelProperty<>();
    private record Key(NativeChairEntity.View view,Direction facing,int part,int arms,boolean item){}
    private static List<String> names(){var out=new ArrayList<String>();for(int style=0;style<5;style++)for(int height=0;height<3;height++)out.add("chair_"+style+"_"+height);for(String kind:new String[]{"luxury_seat","military_seat"})for(int height=0;height<3;height++)for(int part=0;part<4;part++)out.add(kind+"_"+height+"_"+part);for(int height=0;height<4;height++)for(int arms=0;arms<4;arms++)out.add("pilot_"+height+"_"+arms);out.addAll(List.of("advanced_chair","advanced_left","advanced_right","cockpit"));return out;}
    private static ResourceLocation id(String name){return ResourceLocation.parse("vandorlabs:block/native_seating/"+name);}
    @SubscribeEvent public static void additional(ModelEvent.RegisterAdditional event){if(NativeSeatBindings.enabled())for(var name:names())event.register(ModelResourceLocation.standalone(id(name)));}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(NativeSeatBindings.enabled())event.register(NativeSeatBindings.MENU,NativeSeatScreen::new);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){if(NativeSeatBindings.enabled())event.registerEntityRenderer(NativeSeatBindings.MOUNT,net.minecraft.client.renderer.entity.NoopRenderer::new);}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){if(!NativeSeatBindings.enabled())return;var bake=new Bake(event.getModels());active=bake;for(var kind:NativeSeatKind.values()){
        var block=NativeSeatBindings.BLOCKS.get(kind);for(var state:block.getStateDefinition().getPossibleStates()){var location=BlockModelShaper.stateToModelLocation(state);event.getModels().put(location,new WorldModel(Objects.requireNonNull(event.getModels().get(location)),bake));}
        var location=new ModelResourceLocation(ResourceLocation.parse("vandorlabs:"+kind.id),"inventory");var source=Objects.requireNonNull(event.getModels().get(location));event.getModels().put(location,new ItemModel(source,bake,kind));
    }}
    private static NativeChairEntity.View defaults(NativeSeatKind kind){return new NativeChairEntity.View(kind,0,1,0,true,0,0,0);}
    private static String body(Key key){var v=key.view;if(v.kind()==NativeSeatKind.COCKPIT)return "cockpit";return v.kind()==NativeSeatKind.ADVANCED?"advanced_chair":v.kind()==NativeSeatKind.CHAIR?"chair_"+v.style()+"_"+v.height():v.kind()==NativeSeatKind.PILOT?"pilot_"+v.pilotHeight()+"_"+key.arms:v.kind().id+"_"+v.height()+"_"+key.part;}
    private static final class Bake {
        final Map<String,BakedModel> bodies=new HashMap<>();final List<ItemModel> items=new ArrayList<>();long itemBuilds,descriptorHits;
        final Cache<Key,Selected> selected=CacheBuilder.newBuilder().maximumWeight(16*1024*1024).weigher((Key key,Selected model)->model.bytes).build();
        Bake(Map<ModelResourceLocation,BakedModel> models){for(var name:names())bodies.put(name,Objects.requireNonNull(models.get(ModelResourceLocation.standalone(id(name))),"Missing authored seat "+name));}
        Selected select(Key key,BakedModel transforms){var found=selected.getIfPresent(key);if(found!=null)return found;var result=new Selected(key,Objects.requireNonNull(bodies.get(body(key))),transforms,bodies);if(key.item)itemBuilds++;if(result.bytes<=16*1024*1024)selected.put(key,result);return result;}
    }
    private static boolean row(BlockAndTintGetter region,BlockPos pos,BlockState own,NativeChairEntity.View view){if(!NativeSeatBlock.loaded(region,pos))return false;var other=region.getBlockState(pos);if(!(other.getBlock() instanceof NativeSeatBlock)||!other.is(own.getBlock())||other.getValue(NativeSeatBlock.UPPER))return false;var tile=region.getBlockEntity(pos);if(!(tile instanceof NativeChairEntity neighbor)||neighbor.inert())return false;var v=neighbor.view();return ChairSettings.connects(true,true,true,other.getValue(NativeSeatBlock.FACING)==own.getValue(NativeSeatBlock.FACING),view.height(),v.height(),v.join());}
    private static boolean solid(BlockAndTintGetter region,BlockPos pos){if(!NativeSeatBlock.loaded(region,pos))return false;var state=region.getBlockState(pos);return !(state.getBlock() instanceof NativeSeatBlock)&&Block.isShapeFullBlock(state.getCollisionShape(region,pos));}
    private static final class WorldModel extends BakedModelWrapper<BakedModel> {
        private final Bake bake;WorldModel(BakedModel base,Bake bake){super(base);this.bake=bake;}
        @Override public ModelData getModelData(BlockAndTintGetter region,BlockPos pos,BlockState state,ModelData data){if(NativeSeatBlock.proxy(state))return ModelData.EMPTY;var kind=((NativeSeatBlock)state.getBlock()).kind;var view=data.get(NativeChairEntity.VIEW);if(view==null){var tile=region.getBlockEntity(pos);view=tile instanceof NativeChairEntity owner?owner.view():defaults(kind);}var face=state.getValue(NativeSeatBlock.FACING);int part=0,arms=0;if(kind.connected())part=ChairSettings.part(view.join(),row(region,pos.relative(face.getCounterClockWise()),state,view),row(region,pos.relative(face.getClockWise()),state,view));if(kind==NativeSeatKind.PILOT)arms=(solid(region,pos.relative(face.getCounterClockWise()))?1:0)|(solid(region,pos.relative(face.getClockWise()))?2:0);return data.derive().with(KEY,new Key(view,face,part,arms,false)).build();}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type){if(state==null||NativeSeatBlock.proxy(state)||side!=null)return List.of();var key=data.get(KEY);if(key==null){var view=data.get(NativeChairEntity.VIEW);if(view==null)view=defaults(((NativeSeatBlock)state.getBlock()).kind);key=new Key(view,state.getValue(NativeSeatBlock.FACING),view.part(),view.arms(),false);}return bake.select(key,originalModel).quads;}
    }
    private static final class ItemModel extends BakedModelWrapper<BakedModel> {
        private final Bake bake;private final NativeSeatKind kind;private final Selected plain;
        private final Cache<CustomData,Key> components=CacheBuilder.newBuilder().weakKeys().maximumWeight(64*1024).weigher((CustomData ignored,Key key)->256).build();
        private final ItemOverrides overrides=new ItemOverrides(){@Override public BakedModel resolve(BakedModel original,ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){var data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return plain;var found=components.getIfPresent(data);if(found!=null){bake.descriptorHits++;return bake.select(found,originalModel);}var tag=data.copyTag().getCompound(NativeChairEntity.ITEM_SETTINGS);var v=new NativeChairEntity.View(kind,ChairSettings.style(tag.getInt(ChairSettings.STYLE)),ChairSettings.height(tag.contains(ChairSettings.HEIGHT,net.minecraft.nbt.Tag.TAG_INT),tag.getInt(ChairSettings.HEIGHT)),ChairSettings.angle(tag.getInt(ChairSettings.ANGLE)),!tag.contains(ChairSettings.JOIN)||tag.getBoolean(ChairSettings.JOIN),ChairSettings.pilotHeight(tag.getInt(ChairSettings.PILOT_HEIGHT)),0,0);var key=new Key(v,kind==NativeSeatKind.CHAIR?Direction.SOUTH:Direction.NORTH,0,0,true);components.put(data,key);return bake.select(key,originalModel);}};
        ItemModel(BakedModel base,Bake bake,NativeSeatKind kind){super(base);this.bake=bake;this.kind=kind;plain=select(defaults(kind));bake.items.add(this);}
        Selected select(NativeChairEntity.View view){return bake.select(new Key(view,kind==NativeSeatKind.CHAIR?Direction.SOUTH:Direction.NORTH,0,0,true),originalModel);}
        @Override public ItemOverrides getOverrides(){return overrides;}
    }
    private static final class Selected extends BakedModelWrapper<BakedModel> {
        final List<BakedQuad> quads;final int bytes;final ItemTransforms transforms;final List<BakedModel> passes=List.of(this);
        Selected(Key key,BakedModel body,BakedModel transformSource,Map<String,BakedModel> bodies) {
            super(body);
            double degrees=key.view.kind()==NativeSeatKind.CHAIR?key.view.angle()*45+key.facing.toYRot():key.facing.toYRot()+180;
            double radians=Math.toRadians(degrees),c=Math.cos(radians),s=Math.sin(radians);
            var all=new ArrayList<BakedQuad>();
            boolean advanced=key.view.kind()==NativeSeatKind.ADVANCED;
            int height=advanced?key.view.pilotHeight():3;
            append(all,body,c,s,height,net.minecraft.world.phys.Vec3.ZERO);
            if(advanced){
                append(all,bodies.get("advanced_left"),c,s,height,NativeAdvancedChairGeometry.gripPivot(3,key.view.left()));
                append(all,bodies.get("advanced_right"),c,s,height,NativeAdvancedChairGeometry.gripPivot(4,key.view.right()));
            }
            quads=List.copyOf(all);
            long cost=256;
            for(var quad:quads)cost+=96L+4L*quad.getVertices().length;
            bytes=(int)Math.min(Integer.MAX_VALUE,cost);
            transforms=key.item?NativeItemPadding.fit(this,transformSource.getTransforms()):body.getTransforms();
        }

        private static void append(List<BakedQuad> out,BakedModel body,double c,double s,int height,net.minecraft.world.phys.Vec3 pivot){
            for(var side:new Direction[]{null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})
                for(var quad:body.getQuads(null,side,RandomSource.create(0),ModelData.EMPTY,null))
                    out.add(turn(quad,c,s,height,pivot));
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return side==null?quads:List.of();}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type){return getQuads(state,side,random);}
        @Override public ItemTransforms getTransforms(){return transforms;}
        @Override public BakedModel applyTransform(ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){transforms.getTransform(context).apply(left,pose);return this;}
        @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){return passes;}
        @Override public ItemOverrides getOverrides(){return ItemOverrides.EMPTY;}
    }
    private static BakedQuad turn(BakedQuad quad,double c,double s,int height,net.minecraft.world.phys.Vec3 pivot) {
        int[] raw=quad.getVertices().clone();
        int stride=raw.length/4;
        double verticalScale=1;
        if(height!=3){
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int i=0;i<4;i++){
                int index=i*stride+1;
                double y=Float.intBitsToFloat(raw[index])+pivot.y;
                min=Math.min(min,y);max=Math.max(max,y);
                raw[index]=Float.floatToRawIntBits((float)NativeAdvancedChairGeometry.height(y,height));
            }
            if(max-min>1e-9)verticalScale=(NativeAdvancedChairGeometry.height(max,height)-NativeAdvancedChairGeometry.height(min,height))/(max-min);
        }
        for(int i=0;i<4;i++){
            int at=i*stride;
            double x=Float.intBitsToFloat(raw[at])+pivot.x-.5,z=Float.intBitsToFloat(raw[at+2])+pivot.z-.5;
            if(height==3&&pivot.y!=0)raw[at+1]=Float.floatToRawIntBits((float)(Float.intBitsToFloat(raw[at+1])+pivot.y));
            raw[at]=Float.floatToRawIntBits((float)(.5+c*x-s*z));
            raw[at+2]=Float.floatToRawIntBits((float)(.5+s*x+c*z));
            int normal=raw[at+7];
            double nx=(byte)normal,ny=(byte)(normal>>8),nz=(byte)(normal>>16);
            if(height!=3&&verticalScale!=1){
                double scaledY=ny/verticalScale;
                double length=Math.sqrt(nx*nx+scaledY*scaledY+nz*nz);
                double recorded=Math.sqrt(nx*nx+ny*ny+nz*nz);
                if(length>1e-9){nx*=recorded/length;ny=scaledY*recorded/length;nz*=recorded/length;}
            }
            int rx=(int)Math.round(c*nx-s*nz),rz=(int)Math.round(s*nx+c*nz);
            raw[at+7]=(normal&0xFF000000)|(rx&255)|(((int)Math.round(ny)&255)<<8)|((rz&255)<<16);
        }
        var old=quad.getDirection();
        var face=Direction.getNearest((float)(c*old.getStepX()-s*old.getStepZ()),old.getStepY(),(float)(s*old.getStepX()+c*old.getStepZ()));
        return new BakedQuad(raw,quad.getTintIndex(),face,quad.getSprite(),quad.isShade(),quad.hasAmbientOcclusion());
    }
    private NativeSeatModels(){}
}

package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.NativeBlockMaterials;
import com.vandorlabs.neoforge.client.JoinedCubeSnapshots;
import com.vandorlabs.persistence.HousingSettings;
import com.vandorlabs.render.HousingFacePlane;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Actual baked Trigger models and real BE projections; no fake world or copied UV equations. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class TriggerModelChecks {
    private static int ticks,stage;
    private static CompletableFuture<Void> reload;
    private static BakedModel[] frozenModels;
    private static ModelData frozenParticleData;
    private static net.minecraft.client.renderer.texture.TextureAtlasSprite frozenParticle;
    private static final int OFF=150,LOW=8,MEDIUM=384,ON=13,SIDES=9;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Native Trigger model: "+message);}
    private static int expected(boolean bands,int exact,int signal){
        if(exact>=0&&signal!=exact)return OFF;
        if(!bands)return exact>=0||signal>0?ON:OFF;
        return signal==0?OFF:signal<=5?LOW:signal<=10?MEDIUM:ON;
    }
    private static BakedQuad quad(BakedModel model,net.minecraft.world.level.block.state.BlockState state,Direction face,ModelData data){
        var result=model.getQuads(state,face,RandomSource.create(0),data,null);require(result.size()==1,"face quad count "+face+"="+result.size());return result.getFirst();
    }
    private static CompoundTag tag(int layout,boolean front,boolean bands,int exact,int channel,int local){
        var tag=new CompoundTag();tag.putInt(HousingSettings.VERSION,1);tag.putInt(HousingSettings.MAIN,OFF);tag.putInt(HousingSettings.LAYOUT,layout);
        tag.putInt("TriggerOnTexture",ON);tag.putInt("TriggerLowTexture",LOW);tag.putInt("TriggerMediumTexture",MEDIUM);tag.putInt("TriggerSidesTexture",SIDES);tag.putBoolean("TriggerFrontOnly",front);tag.putBoolean("TriggerLevelStates",bands);tag.putInt("TriggerExactLevel",exact);tag.putInt("ChannelLevel",channel);tag.putInt("TriggerLocalLevel",local);
        tag.putBoolean(HousingSettings.FACES_ENABLED,true);tag.putIntArray(HousingSettings.FACES,new int[]{2,3,4,5,6,7});tag.putIntArray("TriggerArtworkModes",new int[]{0,1,2,1});tag.putIntArray("TriggerArtworkSpeeds",new int[]{0,1,2,1});tag.putString("UnknownModelFixtureField","kept");return tag;
    }
    private static void unchangedPayload(ItemStack before,ItemStack after){require(ItemStack.matches(before,after),"item render/sample changed raw or dormant saved payload");}
    private static BakedModel item(Minecraft mc,boolean trigger,CompoundTag data,boolean nested){
        var stack=new ItemStack(trigger?VandorLabsBootstrap.TRIGGER_ITEM.get():VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());
        if(nested){var wrapper=new CompoundTag();wrapper.put("BlockEntityTag",data.copy());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(wrapper));}else stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(data));
        var before=stack.copy();var model=mc.getItemRenderer().getModel(stack,null,null,0);unchangedPayload(before,stack);
        require(model.getParticleIcon(ModelData.EMPTY).contents().name().toString().equals(ModernFinishCatalog.entry(data.getInt(HousingSettings.MAIN)).texture()),"configured item particle did not use raw OFF/main material");
        if(trigger){require(NativeBlockMaterials.sample(stack)==data.getInt(HousingSettings.MAIN),"configured Trigger inventory sample ignored raw OFF material");unchangedPayload(before,stack);}
        return model;
    }
    private static void itemChecks(Minecraft mc,HolderLookup.Provider registries){
        for(boolean nested:new boolean[]{false,true}){
            var data=tag(0,true,false,0,0,0);data.remove(HousingSettings.LAYOUT);
            var implicit=item(mc,true,data,nested);var explicit=data.copy();explicit.putInt(HousingSettings.LAYOUT,0);var stretch=item(mc,true,explicit,nested);
            var cube=item(mc,false,data,nested);explicit.putInt(HousingSettings.LAYOUT,1);var tile=item(mc,true,explicit,nested);
            for(var face:Direction.values()){
                var a=implicit.getQuads(null,face,RandomSource.create(0)).getFirst();var b=stretch.getQuads(null,face,RandomSource.create(0)).getFirst();var c=cube.getQuads(null,face,RandomSource.create(0)).getFirst();var d=tile.getQuads(null,face,RandomSource.create(0)).getFirst();
                require(a.getSprite().contents().name().toString().equals(ModernFinishCatalog.entry(OFF).texture()),"Trigger item selected exact0 ON instead of saved OFF");require(Arrays.equals(a.getVertices(),b.getVertices()),"Trigger missing layout did not default Stretch0");require(Arrays.equals(c.getVertices(),d.getVertices()),"cube missing layout no longer defaults Tile1");require(!Arrays.equals(a.getVertices(),d.getVertices()),"wide fixture did not distinguish Stretch/Tile");
            }
            var raw=data.copy();raw.remove(HousingSettings.VERSION);raw.putInt(HousingSettings.MAIN,15);var model=item(mc,true,raw,nested);require(model.getQuads(null,Direction.NORTH,RandomSource.create(0)).getFirst().getSprite().contents().name().toString().equals(ModernFinishCatalog.entry(15).texture()),"raw unversioned OFF item unexpectedly migrated as world state");
        }
        var state=VandorLabsBootstrap.TRIGGER_BLOCK.get().defaultBlockState();var tile=new NativeTriggerBlockEntity(BlockPos.ZERO,state);var raw=tag(99,true,true,0,0,0);raw.putBoolean(HousingSettings.TILE_SIDES,false);tile.loadWithComponents(raw,registries);var saved=tile.saveWithoutMetadata(registries);
        require(saved.getInt(HousingSettings.LAYOUT)==0&&!saved.getBoolean(HousingSettings.TILE_SIDES)&&Arrays.equals(saved.getIntArray(HousingSettings.FACES),raw.getIntArray(HousingSettings.FACES))&&saved.getString("UnknownModelFixtureField").equals("kept"),"render projection changed established invalid-layout fallback/dormant faces or unknown data: "+saved);require(Arrays.equals(saved.getIntArray("TriggerArtworkModes"),raw.getIntArray("TriggerArtworkModes"))&&Arrays.equals(saved.getIntArray("TriggerArtworkSpeeds"),raw.getIntArray("TriggerArtworkSpeeds")),"static Trigger rendering altered dormant playback");
    }
    private static void particleChecks(Minecraft mc){
        int custom=com.vandorlabs.tiles.CustomMaterialIdentity.flattenedBlockIdentifier("minecraft:stone");
        var projected=ModelData.builder().with(ProgrammableBlockEntity.SETTINGS,new ProgrammableBlockDialog.State(custom,0,false,new int[]{-1,-1,-1,-1,-1,-1},-1)).build();
        var trigger=mc.getBlockRenderer().getBlockModel(VandorLabsBootstrap.TRIGGER_BLOCK.get().defaultBlockState());var cube=mc.getBlockRenderer().getBlockModel(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState());
        var tag=tag(0,false,false,-1,0,0);tag.putInt(HousingSettings.MAIN,custom);var item=item(mc,true,tag,false);var sprite=NativeBlockMaterials.material(custom).lower();
        if(frozenModels==null){frozenModels=new BakedModel[]{trigger,cube,item};frozenParticleData=projected;frozenParticle=sprite;}
        Map<Integer,ModernFinishCatalog.Entry> restore=new HashMap<>();NativeBlockMaterials.snapshot().forEach((choice,material)->restore.put(choice,material.entry()));ModernFinishCatalog.publishCustom(Map.of());
        try{require(trigger.getParticleIcon(projected)==sprite&&cube.getParticleIcon(projected)==sprite&&item.getParticleIcon(ModelData.EMPTY)==sprite,"particle lookup consulted global availability instead of model snapshot");
            require(frozenModels[0].getParticleIcon(frozenParticleData)==frozenParticle&&frozenModels[1].getParticleIcon(frozenParticleData)==frozenParticle&&frozenModels[2].getParticleIcon(ModelData.EMPTY)==frozenParticle,"resource reload changed old model-owned world/item particles");}
        finally{ModernFinishCatalog.publishCustom(restore);}
    }
    public static void run(Minecraft mc){
        var registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);int cases=0,faces=0;
        var planes=new HousingFacePlane[6];Arrays.fill(planes,new HousingFacePlane(new int[]{-1,-2,0},new int[]{2,3,1}));var injected=new JoinedCubeFaces(planes);
        long requests=JoinedCubeSnapshots.stats().requests();
        for(Direction facing:Direction.values())for(int layout=0;layout<3;layout++)for(boolean front:new boolean[]{false,true})for(boolean bands:new boolean[]{false,true})for(int exact=-1;exact<16;exact++)for(int signal=0;signal<16;signal++){
            var state=VandorLabsBootstrap.TRIGGER_BLOCK.get().defaultBlockState().setValue(ProgrammableBlock.FACING,facing);var tile=new NativeTriggerBlockEntity(BlockPos.ZERO,state);tile.loadWithComponents(tag(layout,front,bands,exact,signal,0),registries);
            var data=tile.getModelData().derive().with(ProgrammableBlockEntity.JOINED,injected).build();var model=mc.getBlockRenderer().getBlockModel(state);require(model instanceof ProgrammableModel,"Trigger world model not wrapped");model.getModelData(null,BlockPos.ZERO,state,data);
            int main=expected(bands,exact,signal);require(model.getParticleIcon(data).contents().name().toString().equals(ModernFinishCatalog.entry(main).texture()),"world particle ignored projected signal material");var cubeState=VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState().setValue(ProgrammableBlock.FACING,facing);var cube=mc.getBlockRenderer().getBlockModel(cubeState);var expectedState=new ProgrammableBlockDialog.State(main,layout,front,new int[]{SIDES,SIDES,main,SIDES,SIDES,SIDES},-1);var cubeData=ModelData.builder().with(ProgrammableBlockEntity.SETTINGS,expectedState).build();
            for(Direction face:Direction.values()){
                var actual=quad(model,state,face,data);var reference=quad(cube,cubeState,face,cubeData);int choice=front&&ProgrammableModel.localFace(face,facing)!=2?SIDES:main;
                require(actual.getSprite().contents().name().toString().equals(ModernFinishCatalog.entry(choice).texture()),"projected signal material facing="+facing+" layout="+layout+" front="+front+" bands="+bands+" exact="+exact+" level="+signal+" face="+face);
                require(Arrays.equals(actual.getVertices(),reference.getVertices())&&actual.getTintIndex()==reference.getTintIndex()&&actual.isShade()==reference.isShade(),"Trigger geometry/UV/shading differs from validated unjoined cube mapping");faces++;
            }
            if(signal==15){tile.setChannelLevel(0);var localTag=tag(layout,front,bands,exact,0,15);tile.loadWithComponents(localTag,registries);require(tile.getModelData().get(ProgrammableBlockEntity.SETTINGS).mainTexture==expected(bands,exact,15),"saved local level ignored by visual projection");}
            cases++;
        }
        particleChecks(mc);require(JoinedCubeSnapshots.stats().requests()==requests,"Trigger renderer requested cube joining");itemChecks(mc,registries);CubeReferenceChecks.run(mc);
        System.out.println("[vandorlabs][trigger-model] matrix PASS states="+cases+" faces="+faces+" all facings/layouts levels0..15 exact/bands/frontOnly; actual BE projection, no joining, uniform raw OFF item/default0, model-owned particles and dormant settings");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!"true".equals(System.getenv("VANDOR_TRIGGER_MODEL_CHECKS")))return;var mc=Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(++ticks>6000)throw new IllegalStateException("Trigger model smoke timeout stage="+stage);if(ticks<100||mc.getOverlay()!=null)return;
        if(stage==0){run(mc);reload=mc.reloadResourcePacks();stage=1;}else if(stage==1&&reload.isDone()){reload.join();run(mc);System.out.println("[vandorlabs][trigger-model] client PASS matrix repeated after resource reload; development models only, live Trigger UI/channel interaction is a separate check");stage=2;mc.stop();}
    }
}

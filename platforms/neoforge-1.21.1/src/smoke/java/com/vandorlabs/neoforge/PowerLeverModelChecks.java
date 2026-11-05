package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.persistence.ControlMountSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Streaming independent current-Forge geometry, collision components and padded item reference. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class PowerLeverModelChecks {
    private static int ticks,stage;private static CompletableFuture<Void> reload;
    private static BakedModel frozenModel;private static BlockState frozenState;private static ModelData frozenData;private static List<BakedQuad> frozenQuads;private static net.minecraft.client.renderer.texture.TextureAtlasSprite frozenParticle;
    private static final Direction[] FACES={null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Power lever model: "+message);}
    private static NativePowerLeverBlock block(String id){return switch(id){case "compact_power_lever"->VandorLabsBootstrap.COMPACT_POWER_LEVER.get();case "industrial_power_lever"->VandorLabsBootstrap.INDUSTRIAL_POWER_LEVER.get();case "small_power_lever"->VandorLabsBootstrap.SMALL_POWER_LEVER.get();case "large_power_lever"->VandorLabsBootstrap.LARGE_POWER_LEVER.get();default->throw new IllegalArgumentException(id);};}
    private static NativePowerLeverBlock source(NativePowerLeverBlock block,int size){return block(block.isTwin()?(size==0?"small_power_lever":"large_power_lever"):(size==0?"compact_power_lever":"industrial_power_lever"));}
    private static List<BakedQuad> quads(BakedModel model,BlockState state,ModelData data){List<BakedQuad> result=new ArrayList<>();for(Direction face:FACES)result.addAll(model.getQuads(state,face,RandomSource.create(0),data,null));return result;}
    private static CompoundTag itemTag(String kind,int mount){var tag=new CompoundTag();ControlMountSettings.write(new ModernHousingTags(tag),mount%4,mount/4%4,mount/16);
        if(kind.startsWith("int"))tag.putInt("PowerLeverSize",kind.equals("intMIN")?Integer.MIN_VALUE:kind.equals("intMAX")?Integer.MAX_VALUE:Integer.parseInt(kind.substring(3)));
        else if(kind.startsWith("byte"))tag.putByte("PowerLeverSize",Byte.parseByte(kind.substring(4)));else if(kind.startsWith("long"))tag.putLong("PowerLeverSize",Long.parseLong(kind.substring(4)));else if(kind.startsWith("float"))tag.putFloat("PowerLeverSize",Float.parseFloat(kind.substring(5)));else if(kind.startsWith("string"))tag.putString("PowerLeverSize",kind.substring(6));return tag;
    }
    private static ItemStack stack(NativePowerLeverBlock block,CompoundTag tag){var result=new ItemStack(block);var wrapper=new CompoundTag();wrapper.put("RedstoneChannelSettings",tag);wrapper.putString("UnknownItemField","retained");result.set(DataComponents.CUSTOM_DATA,CustomData.of(wrapper));return result;}
    private static void item(Minecraft mc,JsonObject row,ItemStack stack,String key){
        var before=stack.copy();var model=mc.getItemRenderer().getModel(stack,null,null,0);BinaryControlModelChecks.compare(row.getAsJsonArray("quads"),quads(model,null,ModelData.EMPTY),key);var gui=model.getTransforms().gui;float[] actual={gui.rotation.x(),gui.rotation.y(),gui.rotation.z(),gui.translation.x(),gui.translation.y(),gui.translation.z(),gui.scale.x(),gui.scale.y(),gui.scale.z()};for(int i=0;i<actual.length;i++)require(Math.abs(actual[i]-row.getAsJsonArray("gui").get(i).getAsFloat())<.00003,key+" GUI index="+i+" actual="+actual[i]+" expected="+row.getAsJsonArray("gui").get(i));require(ItemStack.matches(before,stack),key+" changed raw item data");
    }
    private static void dynamicGuard(Minecraft mc){
        var state=VandorLabsBootstrap.COMPACT_POWER_LEVER.get().defaultBlockState();int[] consumed={0};var dynamic=new net.neoforged.neoforge.client.model.BakedModelWrapper<BakedModel>(mc.getBlockRenderer().getBlockModel(state)){
            @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,net.minecraft.client.renderer.RenderType layer){consumed[0]++;return super.getQuads(state,side,random,data,layer);}
        };
        require(NativeAuthoredControlBake.correct(dynamic,state,NativeMountedControlGeometry.authored(state,0,0))==dynamic&&!NativeAuthoredControlBake.isStatic(dynamic)&&consumed[0]==0,"unsupported dynamic model was frozen or consumed random quads during bake");
    }
    private static void run(Minecraft mc)throws Exception{
        dynamicGuard(mc);String filename=System.getenv("VANDOR_POWER_REFERENCE");require(filename!=null,"reference required");boolean baseOnly="true".equals(System.getenv("VANDOR_POWER_BASE_POSES"));int worlds=0,items=0,maxCost=0,failures=0;Map<String,JsonObject> originals=new HashMap<>();
        try(var reader=Files.newBufferedReader(Path.of(filename))){var header=JsonParser.parseString(reader.readLine()).getAsJsonObject();require(header.get("format").getAsInt()==1,"reference format");String line;
            while((line=reader.readLine())!=null){if(baseOnly&&!mountZero(line))continue;JsonObject row=JsonParser.parseString(line).getAsJsonObject();String key=row.get("key").getAsString();String[] parts=key.split("/");NativePowerLeverBlock block=block(parts[0]);
                if(row.get("kind").getAsString().equals("world")){
                    Direction face=Direction.byName(parts[1]);boolean floor=Boolean.parseBoolean(parts[2]),on=Boolean.parseBoolean(parts[3]);int size=Integer.parseInt(parts[4]),mount=Integer.parseInt(parts[5]);var settings=ControlMountSettings.of(mount%4,mount/4%4,mount/16);var state=block.defaultBlockState().setValue(NativePowerLeverBlock.FACING,face).setValue(NativePowerLeverBlock.FLOOR,floor).setValue(NativePowerLeverBlock.POWERED,on);var data=ModelData.builder().with(NativeBinaryControlBlockEntity.VISUAL,new NativeBinaryControlBlockEntity.Visual(settings,3,size)).build();var model=mc.getBlockRenderer().getBlockModel(state);require(model instanceof NativeMountedControlModel,"unwrapped "+key);var emitted=quads(model,state,data);try{BinaryControlModelChecks.compare(row.getAsJsonArray("quads"),emitted,key);}catch(IllegalStateException mismatch){if(!baseOnly)throw mismatch;if(++failures<=12)System.out.println("[vandorlabs][power-model] base mismatch "+mismatch.getMessage());}
                    if(!baseOnly){var actual=NativeMountedControlGeometry.powerBoxes(state,settings,size);var expected=row.getAsJsonArray("boxes");require(actual.size()==expected.size(),key+" component count expected="+expected.size()+" actual="+actual.size());for(int i=0;i<actual.size();i++){var b=actual.get(i);double[] coordinates={b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ};for(int axis=0;axis<6;axis++)require(Math.abs(coordinates[axis]-expected.get(i).getAsJsonArray().get(axis).getAsDouble())<1e-12,key+" component="+i+" axis="+axis);}
                    }
                    var target=source(block,size).defaultBlockState().setValue(NativePowerLeverBlock.FACING,face).setValue(NativePowerLeverBlock.FLOOR,floor).setValue(NativePowerLeverBlock.POWERED,on);require(model.getParticleIcon(data)==mc.getBlockRenderer().getBlockModel(target).getParticleIcon(ModelData.EMPTY),key+" particle ignored effective visual size");
                    if(frozenModel==null&&size!=block.defaultSize()&&mount==63){frozenModel=model;frozenState=state;frozenData=data;frozenQuads=emitted;frozenParticle=model.getParticleIcon(data);}if(!baseOnly)maxCost=Math.max(maxCost,NativeMountedControlGeometry.powerRetainedCost(state,settings,size));worlds++;if(!baseOnly&&worlds%1024==0)System.out.println("[vandorlabs][power-model] progress world="+worlds);
                }else{
                    String kind=parts[1];int mount=Integer.parseInt(parts[2]);var tag=itemTag(kind,mount);var stack=stack(block,tag);try{item(mc,row,stack,key);}catch(IllegalStateException mismatch){if(!baseOnly)throw mismatch;if(++failures<=12)System.out.println("[vandorlabs][power-model] base mismatch "+mismatch.getMessage());}if(kind.equals("absent")&&mount==0)originals.put(parts[0],row);
                    int size=tag.contains("PowerLeverSize",3)?tag.getInt("PowerLeverSize"):block.defaultSize();if(size<0||size>1)size=block.defaultSize();var model=mc.getItemRenderer().getModel(stack,null,null,0);var defaultSource=mc.getItemRenderer().getModel(new ItemStack(source(block,size)),null,null,0);require(model.getParticleIcon(ModelData.EMPTY)==defaultSource.getParticleIcon(ModelData.EMPTY),key+" item particle ignored selected/default size");items++;if(!baseOnly&&items%1024==0)System.out.println("[vandorlabs][power-model] progress items="+items);
                }
            }
        }
        require(failures==0,"base pose mismatches="+failures+"; see bounded summaries");require(worlds==(baseOnly?128:8192)&&items==(baseOnly?56:3584),"incomplete reference worlds="+worlds+" items="+items);
        for(var entry:originals.entrySet())for(String malformed:new String[]{"height","wrapper","root"}){
            var block=block(entry.getKey());var tag=itemTag("int"+(1-block.defaultSize()),63);var stack=stack(block,tag);if(malformed.equals("height")){tag.putInt("BaseHeight",4);stack=stack(block,tag);}else{var root=tag.copy();if(malformed.equals("wrapper"))root.putString("RedstoneChannelSettings","invalid");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));}item(mc,entry.getValue(),stack,"malformed "+entry.getKey()+" "+malformed);
        }
        if(frozenModel!=null){var old=quads(frozenModel,frozenState,frozenData);require(old.size()==frozenQuads.size(),"old mesh count changed");for(int i=0;i<old.size();i++)require(Arrays.equals(old.get(i).getVertices(),frozenQuads.get(i).getVertices())&&old.get(i).getSprite()==frozenQuads.get(i).getSprite(),"old model changed after reload");require(frozenModel.getParticleIcon(frozenData)==frozenParticle,"old model particle changed after reload");}
        long bytes=NativeMountedControlGeometry.cacheCost();require(bytes<=8*1024*1024,"shape cache over budget "+bytes);System.out.println("[vandorlabs][power-model] "+(baseOnly?"base matrix":"matrix")+" PASS world="+worlds+" items="+items+" all4IDs heading/floor/state/sizes/mounts; malformed items; component/wedge collision; GUI; selected-size particles (native API), old snapshots; maxShapeBytes="+maxCost+" retained="+bytes);
    }
    private static boolean mountZero(String line){int start=line.indexOf("\"key\":\"")+7,end=line.indexOf('"',start);if(start<7||end<start)throw new IllegalArgumentException("Malformed reference key");return line.substring(start,end).endsWith("/0");}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!"true".equals(System.getenv("VANDOR_POWER_MODEL_CHECKS")))return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(++ticks>12000)throw new IllegalStateException("Power model timeout stage="+stage);if(ticks<100||mc.getOverlay()!=null)return;
        if(stage==0&&"true".equals(System.getenv("VANDOR_POWER_SHAPE_COST"))){PowerShapeCostChecks.run();stage=2;mc.stop();return;}
        if(stage==0&&"true".equals(System.getenv("VANDOR_POWER_BASE_POSES"))){run(mc);System.out.println("[vandorlabs][power-model] base PASS render-only all variants; full mount/collision/reload coverage separate");stage=2;mc.stop();return;}
        if(stage==0){run(mc);reload=mc.reloadResourcePacks();stage=1;}else if(stage==1&&reload.isDone()){reload.join();run(mc);System.out.println("[vandorlabs][power-model] client PASS before/after reload; development models only");stage=2;mc.stop();}
    }
}

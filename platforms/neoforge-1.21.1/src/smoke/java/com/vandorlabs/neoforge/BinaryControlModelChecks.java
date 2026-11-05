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

/** Compares actual emitted native meshes to an independent current-Forge renderer export. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class BinaryControlModelChecks {
    private static int ticks,stage;private static CompletableFuture<Void> reload;
    private static BakedModel frozenModel;private static BlockState frozenState;private static ModelData frozenData;private static List<BakedQuad> frozenQuads;
    private static final Direction[] FACES={null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Binary control model: "+message);}
    private static List<BakedQuad> quads(BakedModel model,BlockState state,ModelData data){List<BakedQuad> result=new ArrayList<>();for(Direction face:FACES)result.addAll(model.getQuads(state,face,RandomSource.create(0),data,null));return result;}
    static void compare(JsonArray expected,List<BakedQuad> actual,String key){
        require(expected.size()==actual.size(),key+" quadcount expected="+expected.size()+" actual="+actual.size());boolean[] used=new boolean[actual.size()];
        for(JsonElement value:expected){JsonObject row=value.getAsJsonObject();boolean found=false;for(int i=0;i<actual.size();i++)if(!used[i]&&matches(row,actual.get(i))){used[i]=true;found=true;break;}if(!found)throw new IllegalStateException("Binary control model: "+key+" missing reference quad "+row+" closest "+closest(row,actual));}
    }
    private static String closest(JsonObject row,List<BakedQuad> actual){double best=Double.POSITIVE_INFINITY;BakedQuad nearest=null;for(var q:actual){double sum=0;var raw=q.getVertices();int stride=raw.length/4;for(var p:row.getAsJsonArray("points")){double distance=Double.POSITIVE_INFINITY;for(int i=0;i<4;i++){double d=0;for(int a=0;a<3;a++)d+=Math.abs(Float.intBitsToFloat(raw[i*stride+a])-p.getAsJsonArray().get(a).getAsDouble());distance=Math.min(distance,d);}sum+=distance;}if(sum<best){best=sum;nearest=q;}}if(nearest==null)return "none";var raw=nearest.getVertices();int stride=raw.length/4;List<String> points=new ArrayList<>();for(int i=0;i<4;i++)points.add(Float.intBitsToFloat(raw[i*stride])+","+Float.intBitsToFloat(raw[i*stride+1])+","+Float.intBitsToFloat(raw[i*stride+2])+","+(Float.intBitsToFloat(raw[i*stride+4])-nearest.getSprite().getU0())/(nearest.getSprite().getU1()-nearest.getSprite().getU0())+","+(Float.intBitsToFloat(raw[i*stride+5])-nearest.getSprite().getV0())/(nearest.getSprite().getV1()-nearest.getSprite().getV0()));return "delta="+best+" face="+nearest.getDirection()+" shade="+nearest.isShade()+" sprite="+nearest.getSprite().contents().name()+" points="+points;}
    private static boolean matches(JsonObject row,BakedQuad quad){
        if(!row.get("sprite").getAsString().equals(quad.getSprite().contents().name().toString())||!row.get("face").getAsString().equals(quad.getDirection().getSerializedName())||row.get("shade").getAsBoolean()!=quad.isShade())return false;
        int[] raw=quad.getVertices();int stride=raw.length/4;boolean[] used=new boolean[4];for(JsonElement value:row.getAsJsonArray("points")){var p=value.getAsJsonArray();boolean found=false;for(int i=0;i<4;i++)if(!used[i]){
            boolean match=true;for(int axis=0;axis<3;axis++)match&=Math.abs(Float.intBitsToFloat(raw[i*stride+axis])-p.get(axis).getAsDouble())<.00003;
            float u=(Float.intBitsToFloat(raw[i*stride+4])-quad.getSprite().getU0())/(quad.getSprite().getU1()-quad.getSprite().getU0()),v=(Float.intBitsToFloat(raw[i*stride+5])-quad.getSprite().getV0())/(quad.getSprite().getV1()-quad.getSprite().getV0());
            // Vanilla versions shrink atlas UV endpoints differently; compare within0.001of sprite extent.
            match&=Math.abs(u-p.get(3).getAsDouble())<.001&&Math.abs(v-p.get(4).getAsDouble())<.001;
            if(match){used[i]=true;found=true;break;}
        }if(!found)return false;}return true;
    }
    private static void run(Minecraft mc)throws Exception{
        String filename=System.getenv("VANDOR_BINARY_REFERENCE");require(filename!=null,"reference required");JsonObject reference;try(var reader=Files.newBufferedReader(Path.of(filename))){reference=JsonParser.parseReader(reader).getAsJsonObject();}int count=0,items=0,maxCost=0;
        for(var entry:reference.getAsJsonObject("world").entrySet()){
            String[] key=entry.getKey().split("/");NativeMountedSwitchBlock block=key[0].equals("push_button")?VandorLabsBootstrap.PUSH_BUTTON.get():VandorLabsBootstrap.ROCKER_SWITCH.get();Direction face=Direction.byName(key[1]);int rotation=Integer.parseInt(key[2]),mount=Integer.parseInt(key[4]);
            BlockState state=block.defaultBlockState().setValue(NativeMountedSwitchBlock.FACING,face).setValue(NativeMountedSwitchBlock.ON,Boolean.parseBoolean(key[3])).setValue(NativeMountedSwitchBlock.ROTATION,rotation);ControlMountSettings settings=ControlMountSettings.of(mount%4,mount/4%4,mount/16);ModelData data=ModelData.builder().with(NativeBinaryControlBlockEntity.VISUAL,new NativeBinaryControlBlockEntity.Visual(settings,rotation)).build();var model=mc.getBlockRenderer().getBlockModel(state);require(model instanceof NativeMountedControlModel,"unwrapped "+entry.getKey());var quads=quads(model,state,data);
            if(frozenModel==null&&mount==63){frozenModel=model;frozenState=state;frozenData=data;frozenQuads=quads;}
            compare(entry.getValue().getAsJsonArray(),quads,entry.getKey());var bounds=NativeMountedControlGeometry.bounds(state,settings,rotation).inflate(.00003);for(var q:quads){var raw=q.getVertices();int stride=raw.length/4;for(int i=0;i<4;i++)require(bounds.contains(Float.intBitsToFloat(raw[i*stride]),Float.intBitsToFloat(raw[i*stride+1]),Float.intBitsToFloat(raw[i*stride+2])),"outside actual shape "+entry.getKey());}if(face.getAxis()==Direction.Axis.Y&&rotation!=0){var rawState=state.setValue(NativeMountedSwitchBlock.ROTATION,0);var rawModel=mc.getBlockRenderer().getBlockModel(rawState);compare(entry.getValue().getAsJsonArray(),quads(rawModel,rawState,data),entry.getKey()+"/retainedTileRotation");}
            maxCost=Math.max(maxCost,NativeMountedControlGeometry.retainedCost(state,settings,rotation));count++;
        }
        for(var entry:reference.getAsJsonObject("items").entrySet()){
            String[] key=entry.getKey().split("/");int mount=Integer.parseInt(key[1]);var stack=new ItemStack(key[0].equals("push_button")?VandorLabsBootstrap.PUSH_BUTTON_ITEM.get():VandorLabsBootstrap.ROCKER_SWITCH_ITEM.get());var tag=new CompoundTag();ControlMountSettings.write(new ModernHousingTags(tag),mount%4,mount/4%4,mount/16);var wrapper=new CompoundTag();wrapper.put("RedstoneChannelSettings",tag);wrapper.putString("OpaqueFixture","retained");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(wrapper));var before=stack.copy();var model=mc.getItemRenderer().getModel(stack,null,null,0);require(ItemStack.matches(before,stack),"item mutated");var row=entry.getValue().getAsJsonObject();compare(row.getAsJsonArray("quads"),quads(model,null,ModelData.EMPTY),entry.getKey());var gui=model.getTransforms().gui;float[] actual={gui.rotation.x(),gui.rotation.y(),gui.rotation.z(),gui.translation.x(),gui.translation.y(),gui.translation.z(),gui.scale.x(),gui.scale.y(),gui.scale.z()};for(int i=0;i<actual.length;i++)require(Math.abs(actual[i]-row.getAsJsonArray("gui").get(i).getAsFloat())<.00003,"item GUI transform "+entry.getKey()+" index="+i+" actual="+actual[i]+" expected="+row.getAsJsonArray("gui").get(i));items++;
        }
        for(var block:List.of(VandorLabsBootstrap.PUSH_BUTTON.get(),VandorLabsBootstrap.ROCKER_SWITCH.get()))for(boolean wrongType:new boolean[]{false,true}){
            var stack=new ItemStack(block);var root=new CompoundTag();ControlMountSettings.write(new ModernHousingTags(root),3,3,3);if(wrongType)root.putString("RedstoneChannelSettings","invalid");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));var before=stack.copy();var model=mc.getItemRenderer().getModel(stack,null,null,0);String family=block.isMomentary()?"push_button":"rocker_switch";compare(reference.getAsJsonObject("items").getAsJsonObject(family+"/0").getAsJsonArray("quads"),quads(model,null,ModelData.EMPTY),"unrelatedRootMountIgnored");require(ItemStack.matches(before,stack),"unrelated item payload changed");
        }
        if(frozenModel!=null){var old=quads(frozenModel,frozenState,frozenData);require(old.size()==frozenQuads.size(),"oldmodel quadcount changed afterreload");for(int i=0;i<old.size();i++)require(Arrays.equals(old.get(i).getVertices(),frozenQuads.get(i).getVertices())&&old.get(i).getSprite()==frozenQuads.get(i).getSprite(),"oldmodel mesh mutated afterreload");}
        long cacheCost=NativeMountedControlGeometry.cacheCost();require(cacheCost<=8*1024*1024,"shape cache overbudget="+cacheCost);System.out.println("[vandorlabs][binary-model] cache PASS maxEntryBytes="+maxCost+" retainedBytes="+cacheCost+" entries="+NativeMountedControlGeometry.cachedShapes());
        System.out.println("[vandorlabs][binary-model] matrix PASS world="+count+" items="+items+" actual legacy components/filledsupports/GUIpadding");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!"true".equals(System.getenv("VANDOR_BINARY_MODEL_CHECKS")))return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(++ticks>6000)throw new IllegalStateException("Binary model timeout stage="+stage);if(ticks<100||mc.getOverlay()!=null)return;
        if(stage==0){run(mc);reload=mc.reloadResourcePacks();stage=1;}else if(stage==1&&reload.isDone()){reload.join();run(mc);System.out.println("[vandorlabs][binary-model] client PASS before/afterreload; development models only");stage=2;mc.stop();}
    }
}

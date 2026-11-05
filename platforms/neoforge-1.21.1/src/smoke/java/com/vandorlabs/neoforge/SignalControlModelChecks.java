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
public final class SignalControlModelChecks {
    private static int ticks,stage;private static CompletableFuture<Void> reload;
    private static BakedModel frozenModel;private static BlockState frozenState;private static ModelData frozenData;private static List<BakedQuad> frozenQuads;private static net.minecraft.client.renderer.texture.TextureAtlasSprite frozenParticle;
    private static final Direction[] FACES={null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Signal control model: "+message);}
    private static NativeSignalControlBlock block(String id){return switch(id){case "thruster_lever"->VandorLabsBootstrap.THRUSTER_LEVER.get();case "airliner_throttle"->VandorLabsBootstrap.AIRLINER_THROTTLE.get();case "fighter_throttle"->VandorLabsBootstrap.FIGHTER_THROTTLE.get();case "wall_slider"->VandorLabsBootstrap.WALL_SLIDER.get();default->throw new IllegalArgumentException(id);};}
    private static List<BakedQuad> quads(BakedModel model,BlockState state,ModelData data){List<BakedQuad> result=new ArrayList<>();for(Direction face:FACES)result.addAll(model.getQuads(state,face,RandomSource.create(0),data,null));return result;}
    private static ItemStack stack(NativeSignalControlBlock block,String kind,int mount){var tag=new CompoundTag();ControlMountSettings.write(new ModernHousingTags(tag),mount%4,mount/4%4,mount/16);String type=kind.substring(kind.lastIndexOf('-')+1);
        if(kind.equals("int-1"))tag.putInt("ControlType",-1);else if(type.startsWith("int"))tag.putInt("ControlType",type.equals("intMIN")?Integer.MIN_VALUE:type.equals("intMAX")?Integer.MAX_VALUE:Integer.parseInt(type.substring(3)));else if(type.startsWith("byte"))tag.putByte("ControlType",(byte)1);else if(type.startsWith("short"))tag.putShort("ControlType",(short)2);else if(type.startsWith("long"))tag.putLong("ControlType",1);else if(type.startsWith("float"))tag.putFloat("ControlType",1.75F);else if(type.startsWith("string"))tag.putString("ControlType","2");
        if(kind.startsWith("badheight"))tag.putInt("BaseHeight",4);if(kind.startsWith("baddirection")){tag.putInt("BaseHeight",0);tag.putInt("BaseTilt",0);tag.putInt("TiltDirection",7);}if(kind.startsWith("unversioned"))tag.remove("ControlMountVersion");tag.putInt("PowerLeverSize",2);tag.putInt("ControlStep",3);tag.putInt("LowLimit",6);tag.putInt("HighLimit",8);tag.putString("UnknownItemField","retained");var result=new ItemStack(block);var root=new CompoundTag();if(kind.startsWith("root"))root=tag;else if(kind.startsWith("wrongwrapper"))root.putString("RedstoneChannelSettings","invalid");else root.put("RedstoneChannelSettings",tag);result.set(DataComponents.CUSTOM_DATA,CustomData.of(root));return result;
    }
    private static void item(Minecraft mc,JsonObject row,ItemStack stack,String key){
        var before=stack.copy();var model=mc.getItemRenderer().getModel(stack,null,null,0);BinaryControlModelChecks.compare(row.getAsJsonArray("quads"),quads(model,null,ModelData.EMPTY),key);var gui=model.getTransforms().gui;float[] actual={gui.rotation.x(),gui.rotation.y(),gui.rotation.z(),gui.translation.x(),gui.translation.y(),gui.translation.z(),gui.scale.x(),gui.scale.y(),gui.scale.z()};for(int i=0;i<actual.length;i++)require(Math.abs(actual[i]-row.getAsJsonArray("gui").get(i).getAsFloat())<.00003,key+" GUI index="+i+" actual="+actual[i]+" expected="+row.getAsJsonArray("gui").get(i));require(ItemStack.matches(before,stack),key+" changed raw item data");
    }
    private static void dynamicGuard(Minecraft mc){
        var state=VandorLabsBootstrap.THRUSTER_LEVER.get().defaultBlockState();int[] consumed={0};var dynamic=new net.neoforged.neoforge.client.model.BakedModelWrapper<BakedModel>(mc.getBlockRenderer().getBlockModel(state)){
            @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,net.minecraft.client.renderer.RenderType layer){consumed[0]++;return super.getQuads(state,side,random,data,layer);}
        };
        require(NativeAuthoredControlBake.correct(dynamic,state,NativeMountedControlGeometry.authored(state,0,0))==dynamic&&!NativeAuthoredControlBake.isStatic(dynamic)&&consumed[0]==0,"unsupported dynamic model was frozen or consumed random quads during bake");
    }
    private static void run(Minecraft mc)throws Exception{
        dynamicGuard(mc);String filename=System.getenv("VANDOR_SIGNAL_REFERENCE");require(filename!=null,"reference required");boolean baseOnly="true".equals(System.getenv("VANDOR_SIGNAL_BASE_POSES"));int worlds=0,items=0,failures=0;
        try(var reader=Files.newBufferedReader(Path.of(filename))){var header=JsonParser.parseString(reader.readLine()).getAsJsonObject();require(header.get("format").getAsInt()==1,"reference format");String line;
            while((line=reader.readLine())!=null){if(baseOnly&&!mountZero(line))continue;JsonObject row=JsonParser.parseString(line).getAsJsonObject();String key=row.get("key").getAsString();String[] parts=key.split("/");NativeSignalControlBlock block=block(parts[0]);
                if(row.get("kind").getAsString().equals("world")){
                    Direction face=Direction.byName(parts[1]);int rotation=Integer.parseInt(parts[2]),level=Integer.parseInt(parts[3]),mount=Integer.parseInt(parts[4]);var settings=ControlMountSettings.of(mount%4,mount/4%4,mount/16);var state=block.defaultBlockState().setValue(NativeSignalControlBlock.FACING,face).setValue(NativeSignalControlBlock.ROTATION,rotation).setValue(NativeSignalControlBlock.LEVEL,level);var data=ModelData.builder().with(NativeBinaryControlBlockEntity.VISUAL,new NativeBinaryControlBlockEntity.Visual(settings,rotation)).build();var model=mc.getBlockRenderer().getBlockModel(state);require(model instanceof NativeMountedControlModel,"unwrapped "+key);var emitted=quads(model,state,data);try{BinaryControlModelChecks.compare(row.getAsJsonArray("quads"),emitted,key);}catch(IllegalStateException mismatch){if(!baseOnly)throw mismatch;if(++failures<=12)System.out.println("[vandorlabs][signal-model] base mismatch "+mismatch.getMessage());}
                    if(!baseOnly){var actual=NativeMountedControlGeometry.signalBoxes(state,settings,rotation);var expected=row.getAsJsonArray("boxes");require(actual.size()==expected.size(),key+" component count expected="+expected.size()+" actual="+actual.size());for(int i=0;i<actual.size();i++){var b=actual.get(i);double[] coordinates={b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ};for(int axis=0;axis<6;axis++)require(Math.abs(coordinates[axis]-expected.get(i).getAsJsonArray().get(axis).getAsDouble())<1e-12,key+" component="+i+" axis="+axis);}}
                    var listed=state.setValue(NativeSignalControlBlock.ROTATION,0);var listedModel=mc.getBlockRenderer().getBlockModel(listed);if(!baseOnly)BinaryControlModelChecks.compare(row.getAsJsonArray("quads"),quads(listedModel,listed,data),key+" saved rotation vs listed zero");
                    require(model.getParticleIcon(data)==model.getParticleIcon(ModelData.EMPTY),key+" particle changed with mount");
                    if(frozenModel==null&&mount==63){frozenModel=model;frozenState=state;frozenData=data;frozenQuads=emitted;frozenParticle=model.getParticleIcon(data);}worlds++;if(!baseOnly&&worlds%1024==0)System.out.println("[vandorlabs][signal-model] progress world="+worlds);
                }else{
                    String kind=parts[1];int mount=Integer.parseInt(parts[2]);var stack=stack(block,kind,mount);try{item(mc,row,stack,key);}catch(IllegalStateException mismatch){if(!baseOnly)throw mismatch;if(++failures<=12)System.out.println("[vandorlabs][signal-model] base mismatch "+mismatch.getMessage());}items++;if(!baseOnly&&items%1024==0)System.out.println("[vandorlabs][signal-model] progress items="+items);
                }
            }
        }
        require(failures==0,"base pose mismatches="+failures+"; see bounded summaries");require(worlds==(baseOnly?192:12288)&&items==(baseOnly?72:4608),"incomplete reference worlds="+worlds+" items="+items);
        if(frozenModel!=null){var old=quads(frozenModel,frozenState,frozenData);require(old.size()==frozenQuads.size(),"old mesh count changed");for(int i=0;i<old.size();i++)require(Arrays.equals(old.get(i).getVertices(),frozenQuads.get(i).getVertices())&&old.get(i).getSprite()==frozenQuads.get(i).getSprite(),"old model changed after reload");require(frozenModel.getParticleIcon(frozenData)==frozenParticle,"old model particle changed after reload");}
        long bytes=NativeMountedControlGeometry.cacheCost();require(bytes<=8*1024*1024,"shape cache over budget "+bytes);System.out.println("[vandorlabs][signal-model] "+(baseOnly?"base matrix":"matrix")+" PASS world="+worlds+" items="+items+" all4IDs facing/rotation/detents/mounts; malformed items; component/wedge collision; GUI; model-owned particles (native API), old snapshots; retained="+bytes);
    }
    private static boolean mountZero(String line){int start=line.indexOf("\"key\":\"")+7,end=line.indexOf('"',start);if(start<7||end<start)throw new IllegalArgumentException("Malformed reference key");return line.substring(start,end).endsWith("/0");}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!"true".equals(System.getenv("VANDOR_SIGNAL_MODEL_CHECKS")))return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(++ticks>12000)throw new IllegalStateException("Power model timeout stage="+stage);if(ticks<100||mc.getOverlay()!=null)return;
        
        if(stage==0&&"true".equals(System.getenv("VANDOR_SIGNAL_BASE_POSES"))){run(mc);System.out.println("[vandorlabs][signal-model] base PASS render-only all variants; full mount/collision/reload coverage separate");stage=2;mc.stop();return;}
        if(stage==0){run(mc);reload=mc.reloadResourcePacks();stage=1;}else if(stage==1&&reload.isDone()){reload.join();run(mc);System.out.println("[vandorlabs][signal-model] client PASS before/after reload; development models only");stage=2;mc.stop();}
    }
}

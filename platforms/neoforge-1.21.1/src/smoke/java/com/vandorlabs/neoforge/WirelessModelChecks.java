package com.vandorlabs.neoforge;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Canonical endpoint cube roles/UV arrow directions and actual padded item models. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class WirelessModelChecks {
    private static int ticks,stage;
    private static CompletableFuture<Void> reload;
    private static BakedModel frozen;
    private static BlockState frozenState;
    private static List<BakedQuad> frozenQuads;
    private static Object frozenParticle;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Wireless model: "+message);}
    private static List<BakedQuad> quads(BakedModel model,BlockState state){var result=new ArrayList<BakedQuad>();result.addAll(model.getQuads(state,null,RandomSource.create(0),ModelData.EMPTY,null));for(Direction side:Direction.values())result.addAll(model.getQuads(state,side,RandomSource.create(0),ModelData.EMPTY,null));return result;}
    private static Vec3 basis(BakedQuad quad,int coordinate){int[] raw=quad.getVertices();int stride=raw.length/4;double mean=0;for(int i=0;i<4;i++)mean+=Float.intBitsToFloat(raw[i*stride+coordinate])/4;Vec3 value=Vec3.ZERO;for(int i=0;i<4;i++){double weight=Float.intBitsToFloat(raw[i*stride+coordinate])-mean;value=value.add(Float.intBitsToFloat(raw[i*stride])*weight,Float.intBitsToFloat(raw[i*stride+1])*weight,Float.intBitsToFloat(raw[i*stride+2])*weight);}return value.normalize();}
    private static void run(Minecraft mc){int poses=0,arrows=0,items=0;
        for(String kind:new String[]{"transmitter","receiver"}){
            var id=ResourceLocation.fromNamespaceAndPath("vandorlabs","redstone_"+kind+"_block");require(BuiltInRegistries.BLOCK.containsKey(id),"unregistered endpoint "+id);var block=BuiltInRegistries.BLOCK.get(id);
            for(Direction port:Direction.values()){
                var state=block.defaultBlockState().setValue(DirectionalBlock.FACING,port);var model=mc.getBlockRenderer().getBlockModel(state);require(!(model instanceof NativeMountedControlModel)&&!(model instanceof ProgrammableModel),"endpoint gained unrelated custom backend");var quads=quads(model,state);require(quads.size()==6,id+"/"+port+" six cube quads");var faces=EnumSet.noneOf(Direction.class);
                for(var quad:quads){require(faces.add(quad.getDirection()),"duplicate cube face");var sprite=quad.getSprite().contents();var name=sprite.name().getPath();require(name.startsWith("blocks/redstone_"+kind+"/"),"wrong endpoint material "+name);require(sprite.width()==128&&sprite.height()==128,"canonical 128px texture "+name);String leaf=name.substring(name.lastIndexOf('/')+1);
                    if(leaf.equals("port"))require(quad.getDirection()==port,"physical port face "+state);else if(leaf.equals("type"))require(quad.getDirection()==port.getOpposite(),"wireless type opposite physical port "+state);else{Vec3 u=basis(quad,4),v=basis(quad,5),arrow=switch(leaf){case "up"->v.scale(-1);case "down"->v;case "left"->u.scale(-1);case "right"->u;default->throw new IllegalStateException("Unknown arrow "+name);};require(arrow.normalize().dot(new Vec3(port.getStepX(),port.getStepY(),port.getStepZ()))>.999,"arrow flow "+state+"/"+quad.getDirection());arrows++;}
                    int[] raw=quad.getVertices();int stride=raw.length/4;for(int i=0;i<4;i++)for(int axis=0;axis<3;axis++){float p=Float.intBitsToFloat(raw[i*stride+axis]);require(Math.abs(p)<.000001||Math.abs(p-1)<.000001,"authored unit cube coordinate "+p);}
                }
                require(model.getParticleIcon(ModelData.EMPTY).contents().name().getPath().equals("blocks/redstone_"+kind+"/port"),"particle port texture");if(frozen==null){frozen=model;frozenState=state;frozenQuads=quads;frozenParticle=model.getParticleIcon(ModelData.EMPTY);}poses++;
            }
            for(boolean configured:new boolean[]{false,true}){var stack=new ItemStack(block);if(configured){var tag=new CompoundTag();tag.putInt("Channel",123);tag.putInt("Range",64);tag.putString("UnknownItemField","opaque");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}var before=stack.copy();var model=mc.getItemRenderer().getModel(stack,null,null,0);require(ItemStack.matches(before,stack),"item projection mutated data");require(quads(model,null).size()==6,"item cube quads");var gui=model.getTransforms().gui;require(gui.rotation.x()==30&&gui.rotation.y()==225&&gui.rotation.z()==0&&gui.scale.x()==.5F&&gui.scale.y()==.5F&&gui.scale.z()==.5F,"canonical GUI transform");
                double minX=1,minY=1,maxX=-1,maxY=-1;for(var q:quads(model,null)){int[] raw=q.getVertices();int stride=raw.length/4;for(int i=0;i<4;i++){double x=(Float.intBitsToFloat(raw[i*stride])-.5)*gui.scale.x(),y=(Float.intBitsToFloat(raw[i*stride+1])-.5)*gui.scale.y(),z=(Float.intBitsToFloat(raw[i*stride+2])-.5)*gui.scale.z();double ry=Math.toRadians(gui.rotation.y()),rx=Math.toRadians(gui.rotation.x());double xx=x*Math.cos(ry)+z*Math.sin(ry);z=z*Math.cos(ry)-x*Math.sin(ry);x=xx;y=y*Math.cos(rx)-z*Math.sin(rx);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}}require(minX>-.44&&maxX<.44&&minY>-.44&&maxY<.44,"rotated GUI padding all sides");items++;}
        }
        var old=quads(frozen,frozenState);require(old.size()==frozenQuads.size(),"old model quad count");for(int i=0;i<old.size();i++)require(Arrays.equals(old.get(i).getVertices(),frozenQuads.get(i).getVertices())&&old.get(i).getSprite()==frozenQuads.get(i).getSprite(),"old model changed after reload");require(frozen.getParticleIcon(ModelData.EMPTY)==frozenParticle,"old particle changed after reload");require(poses==12&&arrows==48&&items==4,"incomplete matrix");System.out.println("[vandorlabs][wireless-model] matrix PASS12poses/48arrowvectors/4plain-configureditems; canonical textures/particles; rotated GUI padding; immutable old vanilla models");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!"true".equals(System.getenv("VANDOR_WIRELESS_MODEL_CHECKS")))return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(++ticks>12000)throw new IllegalStateException("Wireless model timeout stage="+stage);if(ticks<100||mc.getOverlay()!=null)return;if(stage==0){run(mc);reload=mc.reloadResourcePacks();stage=1;}else if(stage==1&&reload.isDone()){reload.join();run(mc);System.out.println("[vandorlabs][wireless-model] client PASS before/after reload; development models only");stage=2;mc.stop();}}
    private WirelessModelChecks(){}
}

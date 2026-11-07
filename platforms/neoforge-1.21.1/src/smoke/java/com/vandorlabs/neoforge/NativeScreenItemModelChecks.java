package com.vandorlabs.neoforge;

import com.vandorlabs.render.ScreenDiagonalItemMesh;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Actual atlas, authored models, diagonal component selector and GUI padding across reload. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeScreenItemModelChecks {
    private static final String[] NAMES={"programmable_viewscreen","programmable_console","programmable_half_console","programmable_half_input","programmable_input","programmable_diagonal_screen","programmable_diagonal_half_screen"};
    private static boolean active(){return "true".equals(System.getenv("VANDOR_SCREEN_ITEM_RENDER_CHECK"));}
    private static ModelResourceLocation id(String name){return ModelResourceLocation.standalone(ResourceLocation.parse("vandorlabs:item/"+name));}
    private static void need(boolean value,String why){if(!value)throw new AssertionError("Screen inventory: "+why);}
    @SubscribeEvent public static void additional(ModelEvent.RegisterAdditional event){if(active())for(String name:NAMES)event.register(id(name));}
    static BakedModel[] run(){
        var mc=Minecraft.getInstance();var atlas=mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);var result=new BakedModel[8];var canonical=new BakedModel[8];int fields=0;
        com.google.gson.JsonObject materials;try(var reader=new java.io.InputStreamReader(NativeScreenItemModelChecks.class.getResourceAsStream("/assets/vandorlabs/data/screen_item_sprites.json"),java.nio.charset.StandardCharsets.UTF_8)){materials=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        var regions=new java.util.IdentityHashMap<net.minecraft.client.renderer.texture.TextureAtlasSprite,float[]>();for(var member:materials.entrySet()){var value=member.getValue().getAsJsonObject();regions.put(atlas.apply(ResourceLocation.parse(value.get("texture").getAsString())),new float[]{value.get("u").getAsFloat(),value.get("v").getAsFloat()});}
        for(int family=0;family<7;family++){
            var source=mc.getModelManager().getModel(id(NAMES[family]));need(source!=mc.getModelManager().getMissingModel(),"authored model "+NAMES[family]);
            var wrapper=new NativeScreenItemModel(source,ScreenFamily.values()[family],name->atlas.apply(ResourceLocation.parse(name)));
            for(boolean enabled:new boolean[]{false,true}){
                var stack=new ItemStack(Items.STONE);var tag=new CompoundTag();var row=new CompoundTag();row.putBoolean("Enabled",enabled);tag.put("RedstonePrimary",row);tag.putString("selectedScreen","unrelated");tag.putInt("housingTexture",14);stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(tag));
                var selected=wrapper.getOverrides().resolve(wrapper,stack,null,null,0);need(selected.getRenderPasses(stack,false).getFirst()==selected,"selected item pass");padded(selected);
                if(NativeScreenClientBindings.installed()){
                    var actualStack=new ItemStack(com.vandorlabs.neoforge.screen.NativeScreenRegistrations.ITEMS.get(ScreenFamily.values()[family]).get());actualStack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(tag));var actual=mc.getItemRenderer().getModel(actualStack,null,null,0);padded(actual);
                    for(int side=-1;side<6;side++){var direction=side<0?null:Direction.values()[side];var expected=selected.getQuads(null,direction,RandomSource.create(42),ModelData.EMPTY,null);var emitted=actual.getQuads(null,direction,RandomSource.create(42),ModelData.EMPTY,null);need(expected.size()==emitted.size(),"canonical ItemRenderer uses accepted authored mesh");for(int q=0;q<expected.size();q++)need(java.util.Arrays.equals(expected.get(q).getVertices(),emitted.get(q).getVertices())&&expected.get(q).getSprite()==emitted.get(q).getSprite(),"canonical item positions/colors/normals/atlas crop family="+family+" controls="+enabled+" side="+side+" quad="+q);}
                    canonical[family==5&&enabled?7:family]=actual;
                }
                int count=0;for(int side=-1;side<6;side++)for(var quad:selected.getQuads(null,side<0?null:Direction.values()[side],RandomSource.create(42),ModelData.EMPTY,null)){count++;need(!quad.getSprite().contents().name().getPath().equals("missingno"),"stitched authored sprite");}
                need(count>0,"visible authored quads");
                if(family!=5)for(int side=-1;side<6;side++){var direction=side<0?null:Direction.values()[side];var before=source.getQuads(null,direction,RandomSource.create(42),ModelData.EMPTY,null);var after=selected.getQuads(null,direction,RandomSource.create(42),ModelData.EMPTY,null);need(before.size()==after.size(),"authored static quad count");for(int q=0;q<before.size();q++){var old=before.get(q);var fraction=regions.get(old.getSprite());var expected=fraction==null?old:NativeItemQuads.retexture(old,old.getSprite(),fraction[0],fraction[1]);need(java.util.Arrays.equals(expected.getVertices(),after.get(q).getVertices()),"authored static positions/colors/normals/cropped UV");fields+=expected.getVertices().length;}}

                if(family==5){var expected=ScreenDiagonalItemMesh.quads(enabled);var actual=selected.getQuads(null,null,RandomSource.create(42),ModelData.EMPTY,null);need(expected.size()==actual.size(),"diagonal source quad count");for(int q=0;q<expected.size();q++){var old=expected.get(q);var next=actual.get(q);need(next.getDirection().ordinal()==old.face(),"source face");var raw=next.getVertices();for(int vertex=0;vertex<4;vertex++)for(int axis=0;axis<3;axis++){need(raw[vertex*8+axis]==Float.floatToRawIntBits((float)(old.value(vertex,axis)/16)),"source position");fields++;}var descriptor=materials.get(old.texture);var sprite=descriptor==null?atlas.apply(ResourceLocation.parse("minecraft:block/"+old.texture.substring("minecraft:blocks/concrete_".length())+"_concrete")):atlas.apply(ResourceLocation.parse(descriptor.getAsJsonObject().get("texture").getAsString()));need(next.getSprite()==sprite,"source diagonal sprite");float usedU=descriptor==null?1:descriptor.getAsJsonObject().get("u").getAsFloat(),usedV=descriptor==null?1:descriptor.getAsJsonObject().get("v").getAsFloat();for(int vertex=0;vertex<4;vertex++){need(raw[vertex*8+4]==Float.floatToRawIntBits(sprite.getU((float)old.value(vertex,3)/16*usedU)),"source diagonal U");need(raw[vertex*8+5]==Float.floatToRawIntBits(sprite.getV((float)old.value(vertex,4)/16*usedV)),"source diagonal V");fields+=2;}}result[enabled?7:5]=selected;}
                else {if(enabled)need(selected==result[family],"six authored icons ignore configuration");result[family]=selected;}
                for(int repeat=0;repeat<100;repeat++)need(wrapper.getOverrides().resolve(wrapper,stack,null,null,0)==selected,"stable immutable component/model");
            }
        }
        System.out.println("[vandorlabs][screen-item-render] atlas PASS seven authored models/eight variants source position fields="+fields+" selected passes/components/padding; canonical="+NativeScreenClientBindings.installed());return NativeScreenClientBindings.installed()?canonical:result;
    }
    private static void padded(BakedModel model){
        var gui=model.getTransforms().gui;double limit=15/32D;
        for(int face=-1;face<6;face++)for(var quad:model.getQuads(null,face<0?null:Direction.values()[face],RandomSource.create(0),ModelData.EMPTY,null)){
            int[] raw=quad.getVertices();int stride=raw.length/4;for(int vertex=0;vertex<4;vertex++){
                int at=vertex*stride;double x=(Float.intBitsToFloat(raw[at])-.5)*gui.scale.x(),y=(Float.intBitsToFloat(raw[at+1])-.5)*gui.scale.y(),z=(Float.intBitsToFloat(raw[at+2])-.5)*gui.scale.z();double rz=Math.toRadians(gui.rotation.z()),ry=Math.toRadians(gui.rotation.y()),rx=Math.toRadians(gui.rotation.x());double a=x*Math.cos(rz)-y*Math.sin(rz);y=x*Math.sin(rz)+y*Math.cos(rz);x=a;a=x*Math.cos(ry)+z*Math.sin(ry);z=z*Math.cos(ry)-x*Math.sin(ry);x=a;y=y*Math.cos(rx)-z*Math.sin(rx);x+=gui.translation.x();y+=gui.translation.y();need(Math.abs(x)<limit&&Math.abs(y)<limit,"GUI padding after authored rotation");
            }
        }
    }
}

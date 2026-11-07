package com.vandorlabs.neoforge;

import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Actual public inventory selector and ItemRenderer sheet; no synthetic inventory models. */
public final class NativePropulsionInventoryChecks {
 private static void need(boolean yes,String text){if(!yes)throw new AssertionError("Engine inventory: "+text);}
 private static ItemStack stack(String id,int shape,boolean alternate){var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse("vandorlabs:"+id));need(item!=Items.AIR,"public item "+id);var stack=new ItemStack(item);var tag=new CompoundTag();tag.putInt("PropulsionShape",shape);tag.putInt("PropulsionSideTexture",alternate?64:9);tag.putInt("ChannelLevel",alternate?0:15);tag.putBoolean("ManualOn",!alternate);var root=new CompoundTag();root.put("BlockEntityTag",tag);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));return stack;}
 public static Screen run(){var mc=Minecraft.getInstance();mc.getToasts().clear();var sheet=new ArrayList<ItemStack>();int checks=0;for(String id:new String[]{"rocket_thruster","ion_drive","plasma_vent","impulse_engine","antigravity_plate","repulsor_array","vertical_hover_thruster"})for(int shape=0;shape<3;shape++){var stack=stack(id,shape,false);var selected=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);need(selected!=mc.getModelManager().getMissingModel(),"canonical selector "+id+shape);padded(selected);var alternate=mc.getItemRenderer().getModel(stack(id,shape,true),mc.level,mc.player,0);var clamped=mc.getItemRenderer().getModel(stack(id,shape==0?-1:shape==2?99:1,false),mc.level,mc.player,0);for(int face=-1;face<6;face++){var side=face<0?null:Direction.values()[face];var expected=NativePropulsionModel.currentBake().itemQuads(id,shape,side,RandomSource.create(42));var actual=selected.getQuads(null,side,RandomSource.create(42),ModelData.EMPTY,null);need(actual==expected,"authored backend identity "+id+shape);need(actual==alternate.getQuads(null,side,RandomSource.create(42),ModelData.EMPTY,null),"Source ignores saved world brightness/side texture");need(actual==clamped.getQuads(null,side,RandomSource.create(42),ModelData.EMPTY,null),"Source clamps item shape");checks+=3;}sheet.add(stack);}System.out.println("[vandorlabs][propulsion-world] canonical inventory PASS variants=21 exactAuthored/shapeClamp/worldSettingsIgnored/padding checks="+checks);return new Sheet(List.copyOf(sheet));}
 private static final class Sheet extends Screen {private final List<ItemStack> items;Sheet(List<ItemStack> items){super(Component.literal("Authored engine inventory"));this.items=items;}@Override public void render(GuiGraphics graphics,int x,int y,float partial){graphics.fill(0,0,width,height,0xff203040);for(int i=0;i<items.size();i++){int px=30+i%7*70,py=50+i/7*60;graphics.fill(px-1,py-1,px+17,py+17,0xff000000);graphics.renderItem(items.get(i),px,py);}}@Override public boolean isPauseScreen(){return false;}}
    private static void padded(BakedModel model){
        var gui=model.getTransforms().gui;double limit=15/32D;
        for(int face=-1;face<6;face++)for(var quad:model.getQuads(null,face<0?null:Direction.values()[face],RandomSource.create(0),ModelData.EMPTY,null)){
            int[] data=quad.getVertices();int stride=data.length/4;for(int vertex=0;vertex<4;vertex++){
                int at=vertex*stride;double x=(Float.intBitsToFloat(data[at])-.5)*gui.scale.x(),y=(Float.intBitsToFloat(data[at+1])-.5)*gui.scale.y(),z=(Float.intBitsToFloat(data[at+2])-.5)*gui.scale.z();double rz=Math.toRadians(gui.rotation.z()),ry=Math.toRadians(gui.rotation.y()),rx=Math.toRadians(gui.rotation.x());double a=x*Math.cos(rz)-y*Math.sin(rz);y=x*Math.sin(rz)+y*Math.cos(rz);x=a;a=x*Math.cos(ry)+z*Math.sin(ry);z=z*Math.cos(ry)-x*Math.sin(ry);x=a;y=y*Math.cos(rx)-z*Math.sin(rx);x+=gui.translation.x();y+=gui.translation.y();need(Math.abs(x)<limit&&Math.abs(y)<limit,"GUI padding after authored rotation");
            }
        }
    }
}

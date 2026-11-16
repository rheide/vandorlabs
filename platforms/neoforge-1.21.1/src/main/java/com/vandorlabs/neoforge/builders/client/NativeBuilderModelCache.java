package com.vandorlabs.neoforge.builders.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/** Exactly six immutable meshes; atomically replaced on resource reload, with no retained world references. */
public final class NativeBuilderModelCache {
    public static final List<String> IDS=List.of("synthesis_tower","assembly_press","enclosed_gantry");
    public static final RenderType LAYER=RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
    private record Generation(Map<String,NativeBuilderModel> meshes,long bytes){}
    private static final Map<String,NativeBuilderModel> rackMeshes=new HashMap<>();
    private static final NativeBuilderModel[][] batteryBars=new NativeBuilderModel[4][37];
    public static NativeBuilderModel batteryBar(int bay,int pixels){return batteryBars[bay][Math.clamp(pixels,0,36)];}
    private static volatile Generation generation=new Generation(Map.of(),0);
    public static ResourceLocation model(String design,boolean core){if(!IDS.contains(design))throw new IllegalArgumentException("Unknown builder "+design);return ResourceLocation.fromNamespaceAndPath("vandorlabs","block/builders/"+design+"/"+(core?"core":"complete"));}
    public static void bake(Map<ModelResourceLocation,BakedModel> models){
        var meshes=new LinkedHashMap<String,NativeBuilderModel>();long bytes=0;
        for(String id:IDS)for(boolean core:new boolean[]{false,true}){var source=Objects.requireNonNull(models.get(ModelResourceLocation.standalone(model(id,core))),id);var mesh=new NativeBuilderModel(source);meshes.put(id+(core?"/core":""),mesh);bytes+=mesh.retainedBytes();}
        rackMeshes.clear();
        for(int bay=0;bay<4;bay++){
            for(String name:List.of("slot_empty_"+bay,"indicator_"+bay+"_off","indicator_"+bay+"_on")){var model=new NativeBuilderModel(Objects.requireNonNull(models.get(ModelResourceLocation.standalone(ResourceLocation.parse("vandorlabs:block/module_rack/"+name))),name));rackMeshes.put(name,model);bytes+=model.retainedBytes();}
            for(int charge=0;charge<5;charge++)for(String prefix:List.of("slot_battery_","slot_seated_battery_")){String name=prefix+bay+"_"+charge;var model=new NativeBuilderModel(Objects.requireNonNull(models.get(ModelResourceLocation.standalone(ResourceLocation.parse("vandorlabs:block/module_rack/"+name))),name));rackMeshes.put(name,model);bytes+=model.retainedBytes();}
        }
        for(int bay=0;bay<4;bay++)for(int fill=0;fill<=36;fill++){var bar=new NativeBuilderModel(com.vandorlabs.neoforge.rack.client.NativeRackBatteryBar.quads(models,net.minecraft.core.Direction.SOUTH,bay,fill));batteryBars[bay][fill]=bar;bytes+=bar.retainedBytes();}
        if(bytes>4*1024*1024)throw new IllegalStateException("Builder mesh generation byte budget");generation=new Generation(Map.copyOf(meshes),bytes);
    }
    public static boolean ready(){return generation.meshes.size()==6;}
    public static NativeBuilderModel mesh(String id,boolean core){return Objects.requireNonNull(generation.meshes.get(id+(core?"/core":"")),"Unbaked builder "+id);}
    public static NativeBuilderModel battery(int level){return rackMeshes.get("slot_seated_battery_0_"+Math.clamp(level,0,4));}
    public static NativeBuilderModel rack(String name){return rackMeshes.get(name);}
    public static long retainedBytes(){return generation.bytes;}
    public static int width(String id){return switch(id){case "synthesis_tower"->3;case "assembly_press"->4;case "enclosed_gantry"->5;default->throw new IllegalArgumentException(id);};}
    public static int height(String id){return id.equals("synthesis_tower")?5:id.equals("enclosed_gantry")?4:3;}
    /** Centered, complete textured multiblock. Caller owns GUI/world screen projection and rotation. */
    public static void preview(String id,PoseStack poses,MultiBufferSource buffers,int light){
        if(!ready())return;poses.pushPose();try{poses.translate(-width(id)/2D,-height(id)/2D,-1.5);mesh(id,false).emit(poses.last(),buffers.getBuffer(LAYER),light,OverlayTexture.NO_OVERLAY);}finally{poses.popPose();}
    }
    private NativeBuilderModelCache(){}
}

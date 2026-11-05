package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.tiles.CustomMaterialIdentity;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Assessment only: no sampler registration, no saved data rewrites. Runs at the title screen. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class MaterialSpriteCollisionChecks {
    private static int ticks,stage;
    private static JsonArray before;
    private static CompletableFuture<Void> reload;
    private static final LegacyVanillaMaterialAliases ALIASES=new LegacyVanillaMaterialAliases();
    private static TextureAtlasSprite sprite(Minecraft mc,BlockState state,boolean upper){
        if(state.getBlock() instanceof DoorBlock)state=state.setValue(DoorBlock.HALF,upper?DoubleBlockHalf.UPPER:DoubleBlockHalf.LOWER);
        var model=mc.getBlockRenderer().getBlockModel(state);
        if(state.getBlock() instanceof DoorBlock){
            for(var face:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})for(var quad:model.getQuads(state,face,RandomSource.create(0),ModelData.EMPTY,null))if(usable(quad.getSprite()))return quad.getSprite();
            for(var quad:model.getQuads(state,null,RandomSource.create(0),ModelData.EMPTY,null))if(quad.getDirection().getAxis()!=Direction.Axis.Y&&usable(quad.getSprite()))return quad.getSprite();
        }
        var result=model.getParticleIcon(ModelData.EMPTY);
        return usable(result)?result:mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.parse(ModernFinishCatalog.entry(0).texture()));
    }
    private static boolean usable(TextureAtlasSprite sprite){return sprite!=null&&!sprite.contents().name().getPath().equals("missingno");}
    private static JsonArray sample(Minecraft mc)throws Exception{
        JsonArray rows=new JsonArray();int overlaps=0,conflicts=0,doors=0;
        try(var reader=new BufferedReader(new InputStreamReader(MaterialSpriteCollisionChecks.class.getResourceAsStream("/assets/vandorlabs/data/legacy_vanilla_blocks.tsv"),StandardCharsets.UTF_8))){
            for(String line;(line=reader.readLine())!=null;){
                if(line.startsWith("#")||line.isBlank())continue;String name=line.split("\\t")[1];var id=ResourceLocation.parse(name);
                if(!BuiltInRegistries.BLOCK.containsKey(id))continue;
                Block current=BuiltInRegistries.BLOCK.get(id);int choice=CustomMaterialIdentity.identifier(name,0);var tag=ALIASES.state(choice);
                if(tag==null)throw new AssertionError("Missing original metadata0 alias "+name);
                var aliasId=ResourceLocation.parse(tag.getString("Name"));if(!BuiltInRegistries.BLOCK.containsKey(aliasId))throw new AssertionError("Missing flattened block "+tag);
                BlockState alias=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),tag);
                JsonObject row=new JsonObject();row.addProperty("name",name);row.addProperty("choice",choice);row.addProperty("legacyState",alias.toString());row.addProperty("defaultState",current.defaultBlockState().toString());
                boolean match=true;for(boolean upper:new boolean[]{false,true}){
                    var old=sprite(mc,alias,upper);var modern=sprite(mc,current.defaultBlockState(),upper);
                    if(!usable(old)||!usable(modern))throw new AssertionError("Missing selected/fallback atlas sprite "+name);
                    row.addProperty(upper?"aliasUpper":"aliasLower",old.contents().name().toString());row.addProperty(upper?"defaultUpper":"defaultLower",modern.contents().name().toString());match&=old==modern;
                }
                row.addProperty("match",match);if(!match)conflicts++;if(current instanceof DoorBlock)doors++;overlaps++;rows.add(row);
            }
        }
        if(overlaps<100||doors<1)throw new AssertionError("Unexpected overlap coverage "+overlaps+"/"+doors);
        System.out.println("[vandorlabs][material-sprites] assessment overlaps="+overlaps+" conflicts="+conflicts+" doors="+doors+" phase="+stage);
        return rows;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored)throws Exception{
        String path=System.getProperty("vandorlabs.materialSpriteAssessment","");if(path.isEmpty())return;Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}
        if(++ticks>6000)throw new AssertionError("Material sprite assessment timeout stage="+stage);
        if(mc.getOverlay()!=null||ticks<100)return;
        if(stage==0){before=sample(mc);reload=mc.reloadResourcePacks();stage=1;}
        else if(stage==1&&reload.isDone()){
            reload.join();var after=sample(mc);if(!before.equals(after))throw new AssertionError("Resource reload changed alias/default assessment");
            JsonObject output=new JsonObject();output.addProperty("format",1);output.addProperty("reloadStable",true);output.add("rows",after);
            Path target=Path.of(path);Files.createDirectories(target.toAbsolutePath().getParent());Files.writeString(target,new GsonBuilder().setPrettyPrinting().create().toJson(output));
            System.out.println("[vandorlabs][material-sprites] native PASS actual atlas sprite identity, both door halves, particle fallback, reload stable; conflicts are reported not accepted");stage=2;mc.stop();
        }
    }
}

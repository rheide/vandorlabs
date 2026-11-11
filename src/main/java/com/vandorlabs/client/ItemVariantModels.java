package com.vandorlabs.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Prepared inventory variants; no item stacks, NBT, worlds or entities are retained. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class ItemVariantModels {
    static final int MAX_ENTRIES=256;
    static final long MAX_BYTES=16L*1024*1024;
    private static final BoundedRenderCache<Key,Prepared> CACHE=new BoundedRenderCache<>(MAX_ENTRIES,MAX_BYTES);
    private static VertexFormat itemFormat,blockFormat;
    private static long generation;
    private static long preparations;

    private ItemVariantModels(){}

    /** Identity of the baked owner scopes keys to its base model/resource generation. */
    static final class Key {
        final IBakedModel owner;
        final TextureAtlasSprite main,side;
        final int flags,layout,mount;
        Key(IBakedModel owner,TextureAtlasSprite main,TextureAtlasSprite side,int flags,int layout,int mount){
            this.owner=owner;this.main=main;this.side=side;this.flags=flags;this.layout=layout;this.mount=mount;
        }
        @Override public int hashCode(){int h=System.identityHashCode(owner);h=31*h+System.identityHashCode(main);h=31*h+System.identityHashCode(side);h=31*h+flags;h=31*h+layout;return 31*h+mount;}
        @Override public boolean equals(Object other){if(!(other instanceof Key))return false;Key k=(Key)other;return owner==k.owner&&main==k.main&&side==k.side&&flags==k.flags&&layout==k.layout&&mount==k.mount;}
    }

    static synchronized IBakedModel find(Key key){currentGeneration();return CACHE.get(key);}
    static IBakedModel retain(Key key,IBakedModel model){return retain(key,model,model.getItemCameraTransforms(),false);}
    static synchronized IBakedModel retain(Key key,IBakedModel model,ItemCameraTransforms transforms,boolean pad){
        long current=currentGeneration();
        // Custom/weighted models can depend on data outside an appearance key.
        if(!staticModel(model)||!staticItemQuads(model))return model;
        Prepared prepared=Prepared.create(model,transforms,pad,current);
        if(prepared==null)return model;
        preparations++;
        CACHE.put(key,prepared,prepared.bytes);
        return prepared;
    }
    static boolean staticModel(IBakedModel model){
        if(nativeModel(model))return true;
        if(model.getClass()==RetexturedItemModel.class)return staticModel(((RetexturedItemModel)model).itemBase());
        if(model.getClass()==TextureLayoutItemModel.class)return staticModel(((TextureLayoutItemModel)model).itemBase());
        if(model.getClass()==SignalControlModel.class)return staticModel(((SignalControlModel)model).itemBase());
        return false;
    }
    static boolean nativeModel(IBakedModel model){
        // The exact Forge JSON wrapper delegates null-state item queries to the
        // native immutable bake. An arbitrary subclass may supply animation.
        return model.getClass()==SimpleBakedModel.class
                ||model.getClass().getName().equals("net.minecraftforge.client.model.ModelLoader$VanillaModelWrapper$1");
    }
    static boolean staticQuad(BakedQuad quad){
        return quad.getClass()==BakedQuad.class||quad.getClass()==net.minecraftforge.client.model.pipeline.UnpackedBakedQuad.class;
    }
    private static boolean staticItemQuads(IBakedModel model){
        if(model.getClass()==RetexturedItemModel.class)return staticItemQuads(((RetexturedItemModel)model).itemBase());
        if(model.getClass()==TextureLayoutItemModel.class)return staticItemQuads(((TextureLayoutItemModel)model).itemBase());
        if(model.getClass()==SignalControlModel.class)return staticItemQuads(((SignalControlModel)model).itemBase());
        // Check the source before our wrappers replace its quad classes.
        for(int face=-1;face<6;face++)for(BakedQuad quad:model.getQuads(null,face<0?null:EnumFacing.getFront(face),0))
            if(!staticQuad(quad))return false;
        return true;
    }
    static synchronized long currentGeneration(){
        if(itemFormat==null || !itemFormat.equals(DefaultVertexFormats.ITEM) || !blockFormat.equals(DefaultVertexFormats.BLOCK)){
            CACHE.clear();generation++;
            itemFormat=new VertexFormat(DefaultVertexFormats.ITEM);blockFormat=new VertexFormat(DefaultVertexFormats.BLOCK);
        }
        return generation;
    }
    static synchronized void clear(){CACHE.clear();itemFormat=null;blockFormat=null;generation++;}
    static synchronized long retainedBytes(){return CACHE.retainedBytes();}
    static synchronized int size(){return CACHE.size();}
    static synchronized long preparationCount(){return preparations;}
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event){clear();}
    @SubscribeEvent public static void bake(ModelBakeEvent event){clear();}
    @SubscribeEvent public static void unload(WorldEvent.Unload event){if(event.getWorld().isRemote)clear();}

    static long quadBytes(List<BakedQuad> quads){long bytes=64L+8L*quads.size();for(BakedQuad q:quads)bytes+=256L+8L*q.getVertexData().length;return bytes;}

    private static final class Prepared implements IBakedModel {
        final IBakedModel delegate;
        final List<List<BakedQuad>> faces;
        final ItemCameraTransforms transforms;
        final long generation,bytes;
        static Prepared create(IBakedModel delegate,ItemCameraTransforms transforms,boolean pad,long generation){
            List<List<BakedQuad>> faces=new ArrayList<>(7);long bytes=512;
            for(int face=-1;face<6;face++){
                List<BakedQuad> copied=new ArrayList<>();long retained=64;
                for(BakedQuad quad:delegate.getQuads(null,face<0?null:EnumFacing.getFront(face),0)){
                    VertexFormat format=new VertexFormat(quad.getFormat());
                    int[] records=quad.getVertexData().clone();
                    if(records.length!=4*format.getIntegerSize())return null;
                    // Keep retained records private. Consumers historically received
                    // fresh transformed arrays and may edit the data/format they read.
                    // OptiFine may also resize ordinary BakedQuad data from a global flag.
                    copied.add(new BakedQuad(records,quad.getTintIndex(),quad.getFace(),quad.getSprite(),quad.shouldApplyDiffuseLighting(),format){
                        @Override public int[] getVertexData(){return records.clone();}
                        @Override public VertexFormat getFormat(){return new VertexFormat(format);}
                    });
                    retained+=264L+8L*records.length;
                }
                List<BakedQuad> immutable=Collections.unmodifiableList(copied);faces.add(immutable);bytes+=retained;
            }
            return new Prepared(delegate,Collections.unmodifiableList(faces),transforms,pad,generation,bytes);
        }
        private Prepared(IBakedModel delegate,List<List<BakedQuad>> faces,ItemCameraTransforms transforms,boolean pad,long generation,long bytes){
            this.delegate=delegate;this.faces=faces;this.generation=generation;this.bytes=bytes;
            this.transforms=pad?ControlItemPadding.fit(this,transforms):transforms;
        }
        @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){
            return state==null&&seed==0&&generation==currentGeneration()?faces.get(side==null?0:side.getIndex()+1):delegate.getQuads(state,side,seed);
        }
        @Override public boolean isAmbientOcclusion(){return delegate.isAmbientOcclusion();}
        @Override public boolean isGui3d(){return delegate.isGui3d();}
        @Override public boolean isBuiltInRenderer(){return delegate.isBuiltInRenderer();}
        @Override public TextureAtlasSprite getParticleTexture(){return delegate.getParticleTexture();}
        @Override public ItemCameraTransforms getItemCameraTransforms(){return generation==currentGeneration()?transforms:delegate.getItemCameraTransforms();}
        @Override public ItemOverrideList getOverrides(){return delegate.getOverrides();}
    }
}

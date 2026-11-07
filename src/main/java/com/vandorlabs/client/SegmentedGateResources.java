package com.vandorlabs.client;

import com.vandorlabs.segmented.SegmentedGateJoin;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.*;
import net.minecraft.util.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded active-pack loader. Each prepared model graph belongs to one atlas generation only. */
public final class SegmentedGateResources {
    private static final int MAX_FILE_BYTES=65536,MAX_GRAPH_BYTES=2*1024*1024,MAX_MODELS=64;
    public static final class Prepared {
        private ModelBlock[][] models;
        private Prepared(ModelBlock[][] models){this.models=models;}
        public void stitch(TextureMap atlas){
            if(models==null)throw new IllegalStateException("Retired gate model graph");Set<String> textures=new HashSet<>();for(ModelBlock[] row:models)for(ModelBlock model:row)for(net.minecraft.client.renderer.block.model.BlockPart part:model.getElements())for(net.minecraft.client.renderer.block.model.BlockPartFace face:part.mapFaces.values())textures.add(model.resolveTextureName(face.texture));for(String texture:textures)atlas.registerSprite(new ResourceLocation(texture));
        }
        /** Releases parsed JSON/model ownership after conversion to immutable primitive meshes. */
        public SegmentedGateMeshes bake(TextureMap atlas){if(models==null)throw new IllegalStateException("Retired gate model graph");SegmentedGateMeshes result=new SegmentedGateMeshes(models,name->atlas.getAtlasSprite(name));models=null;return result;}
    }
    public static Prepared prepare(IResourceManager resources){
        try{Map<ResourceLocation,ModelBlock> loaded=new HashMap<>();Set<ResourceLocation> pending=new HashSet<>();int[] bytes={0};ModelBlock[][] models=new ModelBlock[3][4];int[] masks={0,2,3,1};for(int kind=0;kind<3;kind++)for(int part=0;part<4;part++)models[kind][part]=load(resources,new ResourceLocation(SegmentedGateJoin.model(kind,masks[part])),loaded,pending,bytes,0);return new Prepared(models);}catch(IOException|RuntimeException failure){throw new IllegalStateException("Cannot prepare active Segmented gate artwork",failure);}
    }
    private static ModelBlock load(IResourceManager resources,ResourceLocation id,Map<ResourceLocation,ModelBlock> loaded,Set<ResourceLocation> pending,int[] total,int depth)throws IOException{
        ModelBlock found=loaded.get(id);if(found!=null)return found;if(depth>=16||loaded.size()+pending.size()>=MAX_MODELS||!pending.add(id))throw new IOException("Segmented gate model graph capacity/cycle");
        ResourceLocation path=new ResourceLocation(id.getResourceDomain(),"models/"+id.getResourcePath()+".json");ModelBlock model;
        try(IResource resource=resources.getResource(path);InputStream input=resource.getInputStream()){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1){total[0]+=count;if(bytes.size()+count>MAX_FILE_BYTES||total[0]>MAX_GRAPH_BYTES)throw new IOException("Segmented gate model byte capacity");bytes.write(buffer,0,count);}model=ModelBlock.deserialize(new StringReader(new String(bytes.toByteArray(),StandardCharsets.UTF_8)));
        }
        if(model.getParentLocation()!=null)model.parent=load(resources,model.getParentLocation(),loaded,pending,total,depth+1);pending.remove(id);loaded.put(id,model);return model;
    }
    private SegmentedGateResources(){}
}

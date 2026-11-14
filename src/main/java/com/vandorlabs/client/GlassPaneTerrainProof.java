package com.vandorlabs.client;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;

/** Verifies the pane records actually installed in Minecraft's translucent buffer. */
final class GlassPaneTerrainProof {
    static final class Result {
        final boolean verified;
        final Set<BlockPos> present,wantedPresent,physicalPresent,wantedPhysicalPresent;
        final Map<BlockPos,GlassPaneTerrainMesh> matched;
        private Result(boolean verified,Set<BlockPos> present,Set<BlockPos> wantedPresent,
                Set<BlockPos> physicalPresent,Set<BlockPos> wantedPhysicalPresent,
                Map<BlockPos,GlassPaneTerrainMesh> matched){
            this.verified=verified;this.present=Collections.unmodifiableSet(present);
            this.wantedPresent=Collections.unmodifiableSet(wantedPresent);
            this.physicalPresent=Collections.unmodifiableSet(physicalPresent);
            this.wantedPhysicalPresent=Collections.unmodifiableSet(wantedPhysicalPresent);
            this.matched=Collections.unmodifiableMap(matched);
        }
    }

    private static final Result UNKNOWN=empty(false),EMPTY=empty(true);
    private static final ClassValue<PublicAccess> PUBLIC_ACCESS=new ClassValue<PublicAccess>(){
        @Override protected PublicAccess computeValue(Class<?> type){return new PublicAccess(type);}
    };
    private static Result empty(boolean verified){
        return new Result(verified,Collections.emptySet(),Collections.emptySet(),Collections.emptySet(),
                Collections.emptySet(),Collections.emptyMap());
    }

    static boolean supports(VertexFormat format){return Layout.read(format)!=null;}

    static Result inspect(CompiledChunk current,BlockPos origin,
            Map<BlockPos,List<GlassPaneTerrainMesh>> candidates,Map<BlockPos,GlassPaneTerrainMesh> wanted){
        if(current==null)return UNKNOWN;
        if(current==CompiledChunk.DUMMY)return EMPTY;
        BufferBuilder.State state=current.getState();
        if(state==null)return current.isLayerEmpty(BlockRenderLayer.TRANSLUCENT)?EMPTY:UNKNOWN;
        Layout layout=Layout.read(state.getVertexFormat());
        int[] raw=state.getRawBuffer();
        if(layout==null||raw==null||raw.length%(layout.stride*4)!=0)return UNKNOWN;
        if(raw.length==0)return EMPTY;

        // Index only expected records. Unrelated terrain costs one bounded scan
        // and no per-quad allocation; shader/light fields are deliberately ignored.
        Map<Key,Count> material=new HashMap<>(),geometry=new HashMap<>();
        Map<BlockPos,List<Candidate>> expected=new LinkedHashMap<>();
        Set<BlockPos> positions=new HashSet<>(candidates.keySet());positions.addAll(wanted.keySet());
        for(BlockPos pos:positions){
            if((pos.getX()&~15)!=origin.getX()||(pos.getY()&~15)!=origin.getY()||(pos.getZ()&~15)!=origin.getZ())continue;
            List<Candidate> list=new ArrayList<>();
            GlassPaneTerrainMesh desired=wanted.get(pos);
            if(desired!=null&&!add(list,pos,origin,desired,material,geometry))return UNKNOWN;
            List<GlassPaneTerrainMesh> known=candidates.get(pos);
            if(known!=null)for(GlassPaneTerrainMesh mesh:known){
                if(mesh==null||mesh==desired)continue;
                boolean duplicate=false;for(Candidate prior:list)if(prior.mesh==mesh){duplicate=true;break;}
                if(!duplicate&&!add(list,pos,origin,mesh,material,geometry))return UNKNOWN;
            }
            if(!list.isEmpty())expected.put(pos,list);
        }
        if(expected.isEmpty())return EMPTY;
        TextureAtlasSprite[] sprites=quadSprites(state,raw.length/(layout.stride*4));
        Key texture=new Key(20),shape=new Key(12);
        for(int at=0,quad=0;at<raw.length;at+=layout.stride*4,quad++){
            if(read(texture,raw,at,layout,0,0,0,true)){
                texture.sprite=sprites==null?null:sprites[quad];texture.updateHash();
                Count textured=material.get(texture);if(textured!=null)textured.seen++;
            }
            if(read(shape,raw,at,layout,0,0,0,false)){
                Count physical=geometry.get(shape);if(physical!=null)physical.seen++;
            }
        }
        Set<BlockPos> present=new HashSet<>(),wantedPresent=new HashSet<>(),physical=new HashSet<>(),wantedPhysical=new HashSet<>();
        Map<BlockPos,GlassPaneTerrainMesh> matched=new HashMap<>();
        for(Map.Entry<BlockPos,List<Candidate>> entry:expected.entrySet()){
            BlockPos pos=entry.getKey();GlassPaneTerrainMesh desired=wanted.get(pos);
            for(Candidate candidate:entry.getValue()){
                if(complete(candidate.geometry)){
                    physical.add(pos);if(candidate.mesh==desired)wantedPhysical.add(pos);
                }
                if(complete(candidate.atlas)||complete(candidate.atlasWithSprite)||complete(candidate.single)){
                    present.add(pos);if(candidate.mesh==desired)wantedPresent.add(pos);
                    if(!matched.containsKey(pos))matched.put(pos,candidate.mesh);
                }
            }
        }
        return new Result(true,present,wantedPresent,physical,wantedPhysical,matched);
    }

    private static boolean add(List<Candidate> candidates,BlockPos pos,BlockPos origin,GlassPaneTerrainMesh mesh,
            Map<Key,Count> material,Map<Key,Count> geometry){
        Layout layout=Layout.read(mesh.format);if(layout==null||mesh.quads.isEmpty())return false;
        int size=mesh.quads.size();Candidate candidate=new Candidate(mesh,size);
        float x=pos.getX()-origin.getX(),y=pos.getY()-origin.getY(),z=pos.getZ()-origin.getZ();
        PublicAccess access=PUBLIC_ACCESS.get(mesh.sprite.getClass());Method singleU=access.singleU,singleV=access.singleV;
        if(singleU!=null&&singleV!=null)candidate.single=new Count[size];
        for(int i=0;i<size;i++){
            BakedQuad quad=mesh.quads.get(i);int[] raw=quad.getVertexData();
            if(raw.length!=layout.stride*4)return false;
            Key shape=new Key(12),atlas=new Key(20);
            if(!read(shape,raw,0,layout,x,y,z,false)||!read(atlas,raw,0,layout,x,y,z,true))return false;
            candidate.geometry[i]=intern(geometry,shape);candidate.atlas[i]=intern(material,atlas);
            Key withSprite=atlas.copy();withSprite.sprite=mesh.sprite;withSprite.updateHash();
            candidate.atlasWithSprite[i]=intern(material,withSprite);
            if(candidate.single!=null){
                Key local=atlas.copy();local.sprite=mesh.sprite;
                try{
                    for(int vertex=0;vertex<4;vertex++){
                        local.data[vertex*5+3]=bits(((Number)singleU.invoke(mesh.sprite,Float.intBitsToFloat(local.data[vertex*5+3]))).floatValue());
                        local.data[vertex*5+4]=bits(((Number)singleV.invoke(mesh.sprite,Float.intBitsToFloat(local.data[vertex*5+4]))).floatValue());
                    }
                    local.canonicalize();candidate.single[i]=intern(material,local);
                }catch(ReflectiveOperationException|RuntimeException unsupported){candidate.single=null;}
            }
        }
        candidates.add(candidate);return true;
    }

    private static Count intern(Map<Key,Count> index,Key key){
        Count value=index.get(key);if(value==null){value=new Count();index.put(key,value);}return value;
    }
    private static boolean complete(Count[] counts){
        if(counts==null)return false;
        for(int i=0;i<counts.length;i++){
            Count count=counts[i];if(count==null)return false;
            int needed=1;for(int j=0;j<i;j++)if(counts[j]==count)needed++;
            if(count.seen<needed)return false;
        }
        return true;
    }
    private static final class Count {int seen;}
    private static final class Candidate {
        final GlassPaneTerrainMesh mesh;
        final Count[] geometry,atlas,atlasWithSprite;
        Count[] single;
        Candidate(GlassPaneTerrainMesh mesh,int quads){this.mesh=mesh;geometry=new Count[quads];atlas=new Count[quads];atlasWithSprite=new Count[quads];}
    }

    private static final class Layout {
        final int stride,position,uv;
        Layout(int stride,int position,int uv){this.stride=stride;this.position=position;this.uv=uv;}
        static Layout read(VertexFormat format){
            if(format==null||format.getNextOffset()<=0||(format.getNextOffset()&3)!=0)return null;
            int position=-1,uv=-1;
            for(int i=0;i<format.getElementCount();i++){
                VertexFormatElement element=format.getElement(i);int offset=format.getOffset(i);
                if(element.getUsage()==VertexFormatElement.EnumUsage.POSITION){
                    if(element.getType()!=VertexFormatElement.EnumType.FLOAT||element.getElementCount()!=3||(offset&3)!=0)return null;
                    position=offset/4;
                }else if(element.getUsage()==VertexFormatElement.EnumUsage.UV&&element.getIndex()==0){
                    if(element.getType()!=VertexFormatElement.EnumType.FLOAT||element.getElementCount()!=2||(offset&3)!=0)return null;
                    uv=offset/4;
                }
            }
            int stride=format.getIntegerSize();
            return position<0||uv<0||position+3>stride||uv+2>stride?null:new Layout(stride,position,uv);
        }
    }

    private static boolean read(Key key,int[] raw,int at,Layout layout,float x,float y,float z,boolean uv){
        int size=uv?5:3;double cx=0,cy=0,cz=0;
        for(int vertex=0;vertex<4;vertex++){
            int source=at+vertex*layout.stride+layout.position,target=vertex*size;
            float px=Float.intBitsToFloat(raw[source])+x,py=Float.intBitsToFloat(raw[source+1])+y,pz=Float.intBitsToFloat(raw[source+2])+z;
            if(!Float.isFinite(px)||!Float.isFinite(py)||!Float.isFinite(pz))return false;
            key.data[target]=bits(px);key.data[target+1]=bits(py);key.data[target+2]=bits(pz);cx+=px;cy+=py;cz+=pz;
            if(uv){
                source=at+vertex*layout.stride+layout.uv;
                float u=Float.intBitsToFloat(raw[source]),v=Float.intBitsToFloat(raw[source+1]);
                if(!Float.isFinite(u)||!Float.isFinite(v))return false;
                key.data[target+3]=bits(u);key.data[target+4]=bits(v);
            }
        }
        // Vanilla positions are section-relative. Render Regions can add a
        // multiple of16; recover that common origin without renderer internals.
        float ox=(float)(Math.floor(cx/64)*16),oy=(float)(Math.floor(cy/64)*16),oz=(float)(Math.floor(cz/64)*16);
        for(int vertex=0;vertex<4;vertex++){
            int target=vertex*size;
            key.data[target]=bits(Float.intBitsToFloat(key.data[target])-ox);
            key.data[target+1]=bits(Float.intBitsToFloat(key.data[target+1])-oy);
            key.data[target+2]=bits(Float.intBitsToFloat(key.data[target+2])-oz);
        }
        key.sprite=null;key.canonicalize();return true;
    }
    private static int bits(float value){return value==0?0:Float.floatToRawIntBits(value);}

    /** Cyclic vertex rotation is harmless; reversed winding is a different face. */
    private static final class Key {
        final int[] data;
        TextureAtlasSprite sprite;
        int hash;
        Key(int size){data=new int[size];}
        Key copy(){Key copy=new Key(data.length);System.arraycopy(data,0,copy.data,0,data.length);copy.sprite=sprite;copy.hash=hash;return copy;}
        void canonicalize(){
            int size=data.length/4,first=0;
            for(int vertex=1;vertex<4;vertex++){
                for(int component=0;component<size;component++){
                    int difference=Integer.compare(data[vertex*size+component],data[first*size+component]);
                    if(difference<0)first=vertex;
                    if(difference!=0)break;
                }
            }
            for(int rotation=0;rotation<first;rotation++)for(int component=0;component<size;component++){
                int value=data[component];data[component]=data[size+component];data[size+component]=data[2*size+component];
                data[2*size+component]=data[3*size+component];data[3*size+component]=value;
            }
            updateHash();
        }
        void updateHash(){hash=31*Arrays.hashCode(data)+System.identityHashCode(sprite);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return other instanceof Key&&sprite==((Key)other).sprite&&Arrays.equals(data,((Key)other).data);}
    }
    private static Method publicMethod(Class<?> type,String name,Class<?>... parameters){
        try{return type.getMethod(name,parameters);}catch(NoSuchMethodException|SecurityException absent){return null;}
    }
    private static final class PublicAccess {
        final Method singleU,singleV,quadSprites;
        PublicAccess(Class<?> type){singleU=publicMethod(type,"toSingleU",float.class);singleV=publicMethod(type,"toSingleV",float.class);quadSprites=publicMethod(type,"getQuadSprites");}
    }
    private static TextureAtlasSprite[] quadSprites(BufferBuilder.State state,int quads){
        // Optional public OptiFine extension. Unknown UVs do not invent tint
        // proof, but matching physical geometry still prevents a second draw.
        Method method=PUBLIC_ACCESS.get(state.getClass()).quadSprites;if(method==null)return null;
        try{
            Object value=method.invoke(state);
            return value instanceof TextureAtlasSprite[]&&((TextureAtlasSprite[])value).length>=quads?(TextureAtlasSprite[])value:null;
        }catch(ReflectiveOperationException|RuntimeException unsupported){return null;}
    }
    private GlassPaneTerrainProof(){}
}

package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.canopy.CanopyMesh;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.lwjgl.util.vector.Vector3f;

import java.util.*;

public final class CanopyModels {
    @SubscribeEvent
    public void register(ModelRegistryEvent event) {
        for (net.minecraft.block.Block b : ModBlocks.BLOCKS)
            if (b instanceof BlockCanopy)
                net.minecraftforge.client.model.ModelLoader.setCustomStateMapper(
                        b,
                        new net.minecraft.client.renderer.block.statemap.StateMap.Builder()
                                .ignore(BlockCanopy.FACING)
                                .build());
    }

    @SubscribeEvent
    public void textures(TextureStitchEvent.Pre event) {
        for (String m : CanopyMesh.MATERIALS)
            event.getMap().registerSprite(new ResourceLocation("vandorlabs:blocks/canopy/" + m));
    }

    @SubscribeEvent
    public void bake(ModelBakeEvent event) {
        Icon.CONFIGURED.clear();
        TECanopy.invalidate();
        FixedCanopyChunks.reload();
        for (net.minecraft.block.Block b : ModBlocks.BLOCKS)
            if (b instanceof BlockCanopy) {
                String id = ((BlockCanopy) b).kind;
                if(b instanceof BlockFixedCanopyGlass){ModelResourceLocation key=new ModelResourceLocation(b.getRegistryName(),"normal");event.getModelRegistry().putObject(key,new FixedCanopyModel(event.getModelRegistry().getObject(key)));}
                event.getModelRegistry()
                        .putObject(
                                new ModelResourceLocation(b.getRegistryName(), "inventory"),
                                new Icon(id));
            }
    }

    public static final class Icon implements IBakedModel {
        private final String kind;
        private static final int BUDGET=8*1024*1024;
        private static int retained;
        private static final Map<Key,Icon> CONFIGURED=new LinkedHashMap<Key,Icon>(32,.75F,true){@Override public void clear(){super.clear();retained=0;}};
        private final ItemOverrideList overrides=new ItemOverrideList(Collections.emptyList()){
            @Override public IBakedModel handleItemState(IBakedModel original,net.minecraft.item.ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity){return selected(stack.getTagCompound());}
        };
        private Key hotKey;private java.lang.ref.WeakReference<Icon> hotModel;
        private final Binding[] bindings=new Binding[128];private int nextBinding;
        private static final class Binding {final java.lang.ref.WeakReference<net.minecraft.nbt.NBTTagCompound> tag;final Key key;final java.lang.ref.WeakReference<Icon> model;Binding(net.minecraft.nbt.NBTTagCompound tag,Key key,Icon model){this.tag=new java.lang.ref.WeakReference<>(tag);this.key=key;this.model=new java.lang.ref.WeakReference<>(model);}}
        private static final class Key {
            final String kind;final int depth,scale,shade,tint,angle,material,layout;
            Key(String kind,net.minecraft.nbt.NBTTagCompound tag){this.kind=kind;depth=value(tag,"CanopyDepth",16);scale=value(tag,"CanopyScale",71);shade=number(tag,"CanopyShade",0);tint=value(tag,"CanopyTint",1);angle=number(tag,"CanopyAngle",1);material=number(tag,"CanopyMaterial",-1);layout=layout(tag);}
            private static int number(net.minecraft.nbt.NBTTagCompound tag,String name,int fallback){return tag!=null&&tag.hasKey(name)?tag.getInteger(name):fallback;}
            private static int value(net.minecraft.nbt.NBTTagCompound tag,String name,int fallback){return tag!=null&&tag.hasKey(name,3)?tag.getInteger(name):fallback;}
            private static int layout(net.minecraft.nbt.NBTTagCompound tag){return value(tag,"CanopyTextureLayout",0)==2?2:tag!=null&&tag.getBoolean("CanopyTileMaterial")?1:0;}
            boolean matches(net.minecraft.nbt.NBTTagCompound tag){return depth==value(tag,"CanopyDepth",16)&&scale==value(tag,"CanopyScale",71)&&shade==number(tag,"CanopyShade",0)&&tint==value(tag,"CanopyTint",1)&&angle==number(tag,"CanopyAngle",1)&&material==number(tag,"CanopyMaterial",-1)&&layout==layout(tag);}
            @Override public int hashCode(){int h=kind.hashCode();h=h*31+depth;h=h*31+scale;h=h*31+shade;h=h*31+tint;h=h*31+angle;h=h*31+material;return h*31+layout;}
            @Override public boolean equals(Object value){if(!(value instanceof Key))return false;Key k=(Key)value;return kind.equals(k.kind)&&depth==k.depth&&scale==k.scale&&shade==k.shade&&tint==k.tint&&angle==k.angle&&material==k.material&&layout==k.layout;}
        }
        private IBakedModel selected(net.minecraft.nbt.NBTTagCompound tag){
            Icon hot=hotModel==null?null:hotModel.get();if(hot!=null&&hotKey.matches(tag))return hot;
            for(Binding binding:bindings)if(binding!=null&&binding.tag.get()==tag&&binding.key.matches(tag)){Icon found=binding.model.get();if(found!=null){hotKey=binding.key;hotModel=binding.model;return found;}}
            Key key=new Key(kind,tag);Icon result=CONFIGURED.get(key);
            if(result==null){com.vandorlabs.canopy.LargeCanopySettings settings=new com.vandorlabs.canopy.LargeCanopySettings();if(tag!=null)settings.read(tag);result=new Icon(kind,settings);int bytes=result.bytes();
                if(bytes<=BUDGET){CONFIGURED.put(key,result);retained+=bytes;Iterator<Icon> old=CONFIGURED.values().iterator();while(retained>BUDGET&&old.hasNext()){retained-=old.next().bytes();old.remove();}}
            }
            Binding binding=new Binding(tag,key,result);bindings[nextBinding]=binding;nextBinding=(nextBinding+1)%bindings.length;hotKey=key;hotModel=binding.model;return result;
        }
        private int bytes(){return 2048+quads.size()*(96+28*4);}
        static int retainedBytes(){return retained;}

        private final List<BakedQuad> quads = new ArrayList<>();
        private final TextureAtlasSprite particle;
        private final ItemCameraTransforms transforms;

        Icon(String id){this(id,null);}
        Icon(String id,com.vandorlabs.canopy.LargeCanopySettings settings) {
            kind=id;if(id.equals("regular_canopy_glass"))id="regular_1x1_00";List<CanopyMesh.Face> faces=settings!=null && com.vandorlabs.canopy.LargeCanopySettings.supports(id)?com.vandorlabs.canopy.LargeCanopyGeometry.faces(id.equals("large_front_canopy_glass")?5:3,settings,true):id.equals("long_canopy")?com.vandorlabs.canopy.OpeningCanopyGeometry.faces(1,3,1,false):CanopyMesh.MODELS.get(id);
            if(settings!=null && (id.equals("canopy") || id.equals("long_canopy")))faces=com.vandorlabs.canopy.OpeningCanopyGeometry.faces(1,id.equals("long_canopy")?3:2,Math.min(id.equals("long_canopy")?3:2,settings.angle),false);
            com.vandorlabs.canopy.CanopyItemDetail bounds=new com.vandorlabs.canopy.CanopyItemDetail();
            for(CanopyMesh.Face face:faces)for(Vec3d point:face.vertices)bounds.include(point.x,point.y,point.z);
            List<CanopyMesh.Face> detail=new ArrayList<>();
            for(CanopyMesh.Face face:faces){double area=0;for(int i=1;i<face.vertices.length-1;i++)area+=face.vertices[i].subtract(face.vertices[0]).crossProduct(face.vertices[i+1].subtract(face.vertices[0])).lengthVector()/2;if(bounds.keep(face.baseMaterial,area))detail.add(face);}
            faces=detail;
            if(settings!=null)faces=com.vandorlabs.canopy.CanopyFinishes.faces(faces,settings.shade,settings.tint,settings.material,settings.textureLayout(),com.vandorlabs.VandorLabs.proxy.materialAspect(settings.material));
            faces=com.vandorlabs.canopy.CanopyMaterialUv.drawable(com.vandorlabs.canopy.CanopySurfaces.visible(faces));
            for(CanopyMesh.Face original:faces){
                CanopyMesh.Face f=new CanopyMesh.Face();f.vertices=new Vec3d[original.vertices.length];f.uv=original.uv;f.material=original.material;f.baseMaterial=original.baseMaterial;f.doubleSided=original.doubleSided;
                for(int i=0;i<f.vertices.length;i++){Vec3d p=original.vertices[i];f.vertices[i]=new Vec3d(bounds.x(p.x),bounds.y(p.y),bounds.z(p.z));}
                float opacity=settings==null?1F:com.vandorlabs.canopy.CanopyFinishes.opacity(original,settings.material,settings.tint);
                if(f.vertices.length==4&&Math.abs(f.vertices[1].subtract(f.vertices[0]).crossProduct(f.vertices[2].subtract(f.vertices[0])).dotProduct(f.vertices[3].subtract(f.vertices[0])))<1E-10){
                    add(f,new int[]{0,1,2,3},opacity);if(f.doubleSided)add(f,new int[]{3,2,1,0},opacity);
                }else for(int triangle=1;triangle<f.vertices.length-1;triangle++){add(f,new int[]{0,triangle,triangle+1,triangle+1},opacity);if(f.doubleSided)add(f,new int[]{triangle+1,triangle,0,0},opacity);}
            }
            particle =
                    Minecraft.getMinecraft()
                            .getTextureMapBlocks()
                            .getAtlasSprite(spriteName(faces.get(0).material));
            ItemTransformVec3f gui =
                    new ItemTransformVec3f(
                            new Vector3f(25, 225, 0), new Vector3f(0, 0, 0), new Vector3f(1, 1, 1));
            ItemCameraTransforms source =
                    new ItemCameraTransforms(
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT,
                            gui,
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT);
            {
                // All canopy kits occupy the same ordinary block-sized item envelope.
                ItemTransformVec3f third = new ItemTransformVec3f(
                        new Vector3f(75, 45, 0), new Vector3f(0, 2.5F / 16, 0),
                        new Vector3f(.375F, .375F, .375F));
                ItemTransformVec3f firstRight = new ItemTransformVec3f(
                        new Vector3f(0, 45, 0), new Vector3f(0, 0, 0),
                        new Vector3f(.4F, .4F, .4F));
                ItemTransformVec3f firstLeft = new ItemTransformVec3f(
                        new Vector3f(0, 225, 0), new Vector3f(0, 0, 0),
                        new Vector3f(.4F, .4F, .4F));
                if(!kind.equals("regular_canopy_glass")){
                    third=new ItemTransformVec3f(new Vector3f(25,225,0),third.translation,third.scale);
                    firstRight=new ItemTransformVec3f(new Vector3f(25,225,0),firstRight.translation,firstRight.scale);
                    firstLeft=new ItemTransformVec3f(new Vector3f(25,225,0),firstLeft.translation,firstLeft.scale);
                }
                source = new ItemCameraTransforms(third, third, firstLeft, firstRight,
                        source.head, source.gui,
                        new ItemTransformVec3f(new Vector3f(25,225,0),new Vector3f(0,3F/16,0),new Vector3f(.25F,.25F,.25F)),
                        new ItemTransformVec3f(new Vector3f(25,225,0),new Vector3f(),new Vector3f(.5F,.5F,.5F)));
            }
            transforms = ControlItemPadding.fit(this, source);
        }

        private static String spriteName(String material){return material.contains(":")?material:"vandorlabs:blocks/canopy/"+material;}
        private void add(CanopyMesh.Face f, int[] index,float opacity) {
            int[] data = new int[28];
            TextureAtlasSprite sprite =
                    Minecraft.getMinecraft()
                            .getTextureMapBlocks()
                            .getAtlasSprite(spriteName(f.material));
            Vec3d normal =
                    f.vertices[index[1]]
                            .subtract(f.vertices[index[0]])
                            .crossProduct(f.vertices[index[2]].subtract(f.vertices[index[0]]))
                            .normalize();
            for (int i = 0; i < 4; i++) {
                int v = index[i], offset = i * 7;
                Vec3d p = f.vertices[v];
                data[offset] = Float.floatToRawIntBits((float) p.x);
                data[offset + 1] = Float.floatToRawIntBits((float) p.y);
                data[offset + 2] = Float.floatToRawIntBits((float) p.z);
                data[offset + 3] = (Math.round(opacity*255)<<24)|0xffffff;
                data[offset + 4] =
                        Float.floatToRawIntBits(sprite.getInterpolatedU(f.uv[v][0] * 16));
                data[offset + 5] =
                        Float.floatToRawIntBits(sprite.getInterpolatedV((1 - f.uv[v][1]) * 16));
                data[offset + 6] =
                        ((int) (normal.x * 127) & 255)
                                | (((int) (normal.y * 127) & 255) << 8)
                                | (((int) (normal.z * 127) & 255) << 16);
            }
            quads.add(
                    new BakedQuad(
                            data,
                            -1,
                            EnumFacing.getFacingFromVector(
                                    (float) normal.x, (float) normal.y, (float) normal.z),
                            sprite,
                            true,
                            DefaultVertexFormats.ITEM));
        }

        public List<BakedQuad> getQuads(IBlockState s, EnumFacing side, long seed) {
            return side == null ? quads : Collections.emptyList();
        }

        public boolean isAmbientOcclusion() {
            return false;
        }

        public boolean isGui3d() {
            return true;
        }

        public boolean isBuiltInRenderer() {
            return false;
        }

        public TextureAtlasSprite getParticleTexture() {
            return particle;
        }

        public ItemCameraTransforms getItemCameraTransforms() {
            return transforms;
        }

        public ItemOverrideList getOverrides() {
            return overrides;
        }
    }
}

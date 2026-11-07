package com.vandorlabs.neoforge;

import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.render.PanelCornerGeometry;
import java.util.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.model.data.ModelData;

/** World-free target-record and item contracts; shared emission has frozen actual Forge raw goldens. */
public final class NativeFlatWallModelChecks {
    private static final class Sprite extends TextureAtlasSprite {Sprite(SpriteContents contents){super(TextureAtlas.LOCATION_BLOCKS,contents,16,16,0,0);}}
    private static int checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static double triangle(int[] data,int a,int b,int c){double[] u=new double[3],v=new double[3];for(int axis=0;axis<3;axis++){u[axis]=Float.intBitsToFloat(data[b*8+axis])-Float.intBitsToFloat(data[a*8+axis]);v[axis]=Float.intBitsToFloat(data[c*8+axis])-Float.intBitsToFloat(data[a*8+axis]);}double x=u[1]*v[2]-u[2]*v[1],y=u[2]*v[0]-u[0]*v[2],z=u[0]*v[1]-u[1]*v[0];return Math.sqrt(x*x+y*y+z*z)/2;}
    private static double area(List<BakedQuad> quads){double area=0;for(var quad:quads){var data=quad.getVertices();area+=triangle(data,0,1,2)+triangle(data,0,2,3);}return area;}
    private static BakedModel original(List<BakedQuad> quads,TextureAtlasSprite sprite){Map<Direction,List<BakedQuad>> sides=new EnumMap<>(Direction.class);for(var side:Direction.values())sides.put(side,List.of());var none=ItemTransform.NO_TRANSFORM;var gui=new ItemTransform(new org.joml.Vector3f(30,225,0),new org.joml.Vector3f(),new org.joml.Vector3f(.75F));var transforms=new ItemTransforms(none,none,none,none,none,gui,none,none,com.google.common.collect.ImmutableMap.of());return new SimpleBakedModel(quads,sides,false,true,true,sprite,transforms,ItemOverrides.EMPTY);}
    private static void records(NativeFlatWallGeometry.Geometry geometry,List<BakedQuad> quads){
        need(!quads.isEmpty()&&quads.size()%2==0,"complete doubled surfaces");var bounds=geometry.bounds();for(int i=0;i<quads.size();i+=2){var front=quads.get(i);var back=quads.get(i+1);need(!front.isShade()&&!front.hasAmbientOcclusion()&&front.getDirection()==back.getDirection(),"legacy panel shade/direction");int[] a=front.getVertices(),b=back.getVertices();for(int v=0;v<4;v++){int offset=v*8;double x=Float.intBitsToFloat(a[offset]),y=Float.intBitsToFloat(a[offset+1]),z=Float.intBitsToFloat(a[offset+2]);need(x>=bounds.minX-1e-6&&x<=bounds.maxX+1e-6&&y>=bounds.minY-1e-6&&y<=bounds.maxY+1e-6&&z>=bounds.minZ-1e-6&&z<=bounds.maxZ+1e-6,"current geometry envelope");for(int n=0;n<8;n++)need(a[((4-v)&3)*8+n]==b[offset+n],"backface retains geometry/UV/normal payload");need(a[offset+7]==a[7]&&a[offset+3]==-1,"uniform normal/white vertex");}}
    }
    public static void main(String[] ignored)throws Exception{
        Map<ResourceLocation,SpriteContents> contents=new HashMap<>();Map<ResourceLocation,TextureAtlasSprite> sprites=new HashMap<>();java.util.function.Function<ResourceLocation,TextureAtlasSprite> texture=id->sprites.computeIfAbsent(id,key->{var image=new NativeImage(16,16,false);var value=new SpriteContents(key,new FrameSize(16,16),image,ResourceMetadata.EMPTY);contents.put(key,value);return new Sprite(value);});
        try{
            Map<Integer,ProgrammableModel.MaterialSnapshot> surfaces=new HashMap<>();for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++){var entry=ModernFinishCatalog.ENTRIES.get(i);surfaces.put(i,new ProgrammableModel.MaterialSnapshot(texture.apply(ResourceLocation.parse(entry.texture())),entry.u(),entry.v()));}surfaces.put(1288700935,new ProgrammableModel.MaterialSnapshot(texture.apply(ResourceLocation.fromNamespaceAndPath("vandorlabs","cpu-portrait")),.5F,1));
            int frame=-1;for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++){var f=ModernFinishCatalog.ENTRIES.get(i).frame();if(f!=null&&f.border()&&f.rail()!=null){frame=i;break;}}need(frame>=0,"current authored frame metadata");
            var bake=new NativeFlatWallModel.Bake(Map.copyOf(surfaces),material->texture.apply(material.texture()));var root=(NativeFlatWallModel)bake.wrap(original(List.of(),surfaces.get(0).sprite()),false);int textureCount=sprites.size(),worlds=0;
            Boolean[] hands={null,false,true};for(Direction facing:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})for(int depth=0;depth<3;depth++)for(Boolean front:hands)for(Boolean back:hands)for(int tails=0;tails<4;tails++){
                if(front==null&&back==null&&tails==0)continue;var join=new PanelCornerGeometry.FlatCorner(front==null?null:front?12D:0D,back==null?null:back?12D:0D,front,back,(tails&1)!=0,(tails&2)!=0);double near=com.vandorlabs.render.PanelDepthPolicy.start(depth);if(join.right()-join.left()<1e-7&&(join.frontArm==null||near<1e-7)&&(join.backArm==null||12-near<1e-7))continue;var geometry=NativeFlatWallGeometry.build(facing,depth,join).withLayout(new NativePanelLayout(1,1,3,2,(tails&1)!=0,(tails&2)!=0));
                for(int main:new int[]{0,150,1288700935})for(int interior:new int[]{-1,0,frame}){var base=root.worldQuads(geometry,main,interior,0);records(geometry,base);double expected=area(base);for(int mode=1;mode<=2;mode++){var mapped=root.worldQuads(geometry,main,interior,mode);records(geometry,mapped);need(Math.abs(area(mapped)-expected)<.00005,"group subdivision preserves surface area");}need(base==root.worldQuads(geometry,main,interior,0),"generation cache reuse");worlds++;}
            }
            var straight=NativeFlatWallGeometry.straight(Direction.NORTH,1);var framed=root.worldQuads(straight,0,frame,0);Set<TextureAtlasSprite> used=new HashSet<>();for(var quad:framed)used.add(quad.getSprite());for(var edge:bake.interiors.get(frame).edges())need(used.contains(edge.sprite()),"all four directional frame edges selected");need(sprites.size()==textureCount,"no atlas reads on worker projections");
            var flat=NativePanelQuads.flat(Direction.NORTH,6,null,new NativePanelQuads.Material(surfaces.get(0)),bake.originalMetal,0,NativePanelLayout.UNIT);List<BakedQuad> itemQuads=new ArrayList<>();for(int i=0;i<flat.size();i+=2)itemQuads.add(flat.get(i));BakedModel itemBase=original(itemQuads,surfaces.get(0).sprite());var item=(NativeFlatWallModel)bake.wrap(itemBase,true);
            List<Tag> tags=List.of(IntTag.valueOf(3),ByteTag.valueOf((byte)3),LongTag.valueOf(3),FloatTag.valueOf(3.75F),StringTag.valueOf("3"),IntTag.valueOf(-1),IntTag.valueOf(1288700935),LongTag.valueOf(1288700935L+(1L<<32)),IntTag.valueOf(0x40001234));int items=0;
            for(Tag main:tags){var payload=new CompoundTag();payload.put("housingTexture",main.copy());payload.putInt("SideTexture",3);payload.putInt("FrameInterior",frame);payload.putInt("MainTextureLayout",2);payload.putBoolean("FaceTexturesEnabled",true);payload.putIntArray("FaceTextures",new int[]{3,3,3,3,3,3});payload.putString("Opaque","retained");var wrapper=new CompoundTag();wrapper.put("BlockEntityTag",payload);var custom=CustomData.of(wrapper);var projected=item.resolveItem(custom);var values=projected.getQuads(null,null,RandomSource.create(0),ModelData.EMPTY,null);need(values.size()==itemQuads.size()&&custom.copyTag().equals(wrapper),"item shape/opaque data preserved");int raw=main instanceof NumericTag number?number.getAsInt():0;boolean external=raw>=com.vandorlabs.tiles.MaterialChoicePolicy.CUSTOM_BASE;int selected=bake.index(external||main instanceof IntTag?raw:0);for(int q=0;q<values.size();q++){boolean body=itemQuads.get(q).getSprite()==surfaces.get(0).sprite();TextureAtlasSprite expected=external||body?surfaces.get(selected).sprite():itemQuads.get(q).getSprite();need(values.get(q).getSprite()==expected,"typed builtin/numeric custom cap asymmetry");for(int v=0;v<4;v++)for(int axis=0;axis<3;axis++)need(values.get(q).getVertices()[v*8+axis]==itemQuads.get(q).getVertices()[v*8+axis],"item geometry unchanged");}need(projected.getTransforms()==itemBase.getTransforms(),"canonical item display transform");items++;}
            NativeWallProjectionCacheChecks.run(()->new NativeFlatWallModel.Bake(surfaces,m->texture.apply(m.texture())));
            bake.quads.cleanUp();long bytes=0;for(var values:bake.quads.asMap().values())bytes+=384+values.stream().mapToInt(quad->96+quad.getVertices().length*4).sum();need(bytes<=16*1024*1024,"generation weighted mesh limit");System.out.println("[vandorlabs][flat-wall-model] CPU PASS worldProfiles="+worlds+" itemTypes="+items+" checks="+checks+" cacheBytes="+bytes+" shared actual-Forge polygon kernels; native records/group area/frame slots/typed items; live authored models separate");
        }finally{for(var value:contents.values())value.close();}
    }
}

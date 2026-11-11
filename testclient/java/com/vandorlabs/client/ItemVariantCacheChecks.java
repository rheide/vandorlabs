package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.property.*;
import org.lwjgl.util.vector.Vector3f;

/** Complete-record parity, safe admission and allocation evidence for inventory variants. */
public final class ItemVariantCacheChecks {
    private static final EnumFacing[] SIDES={null,EnumFacing.DOWN,EnumFacing.UP,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST};
    private static volatile Object consumed;
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class Atlas implements Function<String,TextureAtlasSprite> {
        final Map<String,TextureAtlasSprite> sprites=new HashMap<>();
        public TextureAtlasSprite apply(String name){return sprites.computeIfAbsent(name,key->{TextureAtlasSprite sprite=new TextureAtlasSprite(key){};sprite.setIconWidth(16);sprite.setIconHeight(16);int cell=Math.floorMod(key.hashCode(),128);sprite.initSprite(256,256,cell%16*16,cell/16*16,false);return sprite;});}
    }
    private static VertexFormat extended(){
        // Move UV0 as well as appending components: hard-coded offsets corrupt this record.
        return new VertexFormat().addElement(DefaultVertexFormats.POSITION_3F).addElement(DefaultVertexFormats.COLOR_4UB)
                .addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.GENERIC,4))
                .addElement(DefaultVertexFormats.TEX_2F).addElement(DefaultVertexFormats.NORMAL_3B).addElement(DefaultVertexFormats.PADDING_1B)
                .addElement(new VertexFormatElement(1,VertexFormatElement.EnumType.SHORT,VertexFormatElement.EnumUsage.UV,2));
    }
    private static BakedQuad quad(TextureAtlasSprite sprite,VertexFormat format,EnumFacing face){
        int stride=format.getIntegerSize(),uv=format.getUvOffsetById(0)/4;int[] data=new int[stride*4];
        for(int i=0;i<data.length;i++)data[i]=0x5a051000+i;
        for(int i=0;i<4;i++){
            double u=i==1||i==2?.875:.125,v=i>=2?.75:0;
            double x=u,y=v,z=.25;
            if(face.getAxis()==EnumFacing.Axis.Y){y=face==EnumFacing.UP?.5:0;z=v;}
            else if(face.getAxis()==EnumFacing.Axis.X){x=face==EnumFacing.EAST?.875:.125;z=u;}
            else z=face==EnumFacing.SOUTH?.75:0;
            int at=i*stride;data[at]=Float.floatToRawIntBits((float)x);data[at+1]=Float.floatToRawIntBits((float)y);data[at+2]=Float.floatToRawIntBits((float)z);
            data[at+uv]=Float.floatToRawIntBits(sprite.getInterpolatedU(i==1||i==2?16:0));data[at+uv+1]=Float.floatToRawIntBits(sprite.getInterpolatedV(i>=2?16:0));
        }
        return new BakedQuad(data,face.getIndex()%3-1,face,sprite,face.getIndex()%2==0,format);
    }
    private static Map<EnumFacing,List<BakedQuad>> emptyFaces(){Map<EnumFacing,List<BakedQuad>> result=new EnumMap<>(EnumFacing.class);for(EnumFacing face:EnumFacing.values())result.put(face,Collections.emptyList());return result;}
    private static SimpleBakedModel model(Atlas atlas,VertexFormat format){
        Map<EnumFacing,List<BakedQuad>> faces=emptyFaces();
        for(EnumFacing face:EnumFacing.values())faces.put(face,Collections.singletonList(quad(atlas.apply(face==EnumFacing.NORTH?"vandorlabs:blocks/programmable_glass/metal_side":face==EnumFacing.UP?"vandorlabs:blocks/lightbar_wall_on":"source"),format,face)));
        ItemTransformVec3f gui=new ItemTransformVec3f(new Vector3f(30,225,0),new Vector3f(0,0,0),new Vector3f(.85F,.85F,.85F));
        ItemCameraTransforms transforms=new ItemCameraTransforms(ItemTransformVec3f.DEFAULT,ItemTransformVec3f.DEFAULT,ItemTransformVec3f.DEFAULT,ItemTransformVec3f.DEFAULT,ItemTransformVec3f.DEFAULT,gui,ItemTransformVec3f.DEFAULT,ItemTransformVec3f.DEFAULT);
        return new SimpleBakedModel(Collections.singletonList(quad(atlas.apply("source"),format,EnumFacing.UP)),faces,true,true,atlas.apply("source"),transforms,ItemOverrideList.NONE);
    }
    private static void same(List<BakedQuad> expected,List<BakedQuad> actual){
        need(expected.size()==actual.size(),"prepared quad count changed");
        for(int i=0;i<expected.size();i++){
            BakedQuad a=expected.get(i),b=actual.get(i);
            need(a.getFace()==b.getFace()&&a.getSprite()==b.getSprite()&&a.getTintIndex()==b.getTintIndex()&&a.shouldApplyDiffuseLighting()==b.shouldApplyDiffuseLighting()
                    &&a.getFormat().equals(b.getFormat())&&Arrays.equals(a.getVertexData(),b.getVertexData()),"prepared geometry/UV/tint/shading/complete format changed at quad "+i);
        }
    }
    private static void same(IBakedModel expected,IBakedModel actual){for(EnumFacing side:SIDES)same(expected.getQuads(null,side,0),actual.getQuads(null,side,0));}
    private static IBakedModel resolve(IBakedModel model,ItemStack stack){return model.getOverrides().handleItemState(model,stack,null,null);}
    private static ItemStack stack(Block block,String key,NBTTagCompound tag){ItemStack stack=new ItemStack(new ItemBlock(block));stack.setTagInfo(key,tag);return stack;}
    private static NBTTagCompound material(int choice,int side,int layout){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("housingTexture",choice);if(side>=0)tag.setInteger("SideTexture",side);tag.setInteger("MainTextureLayout",layout);return tag;}
    private static void materials(){
        ItemVariantModels.clear();Atlas atlas=new Atlas();int comparisons=0;
        for(VertexFormat format:new VertexFormat[]{new VertexFormat(DefaultVertexFormats.ITEM),extended()}){
            SimpleBakedModel base=model(atlas,format);
            for(Block block:new Block[]{new BlockProgrammableBlock(),new BlockProgrammableSlab(),new BlockProgrammableWall("variant_porthole",BlockProgrammableWall.Shape.PORTHOLE)}){
                ConfiguredMaterialItemModel owner=new ConfiguredMaterialItemModel(base,false,atlas);
                need(owner.getOverrides()==owner.getOverrides(),"override list recreated on each lookup");
                for(int choice:new int[]{-1,0,CustomBlockMaterials.ID_BASE,CustomBlockMaterials.ID_BASE+1})for(int side:new int[]{-1,2,4})for(int layout=0;layout<3;layout++){
                    NBTTagCompound tag=material(choice,side,layout);ItemStack stack=stack(block,"BlockEntityTag",tag);
                    boolean slab=block instanceof BlockProgrammableSlab,porthole=block instanceof BlockProgrammableWall;
                    TextureAtlasSprite main=CustomBlockMaterials.isCustom(choice)?atlas.apply(ScreenHousingTextures.texture(choice,true)):null;
                    IBakedModel expected=base;
                    if((slab||porthole)&&side>=0)expected=new RetexturedItemModel(base,main,atlas.apply(ScreenHousingTextures.texture(side)),slab,porthole);
                    else if(main!=null)expected=new RetexturedItemModel(base,main,false);
                    if(!porthole)expected=new TextureLayoutItemModel(expected,layout);
                    IBakedModel actual=resolve(owner,stack);same(expected,actual);comparisons++;
                    tag.setInteger("redstoneChannel",47);tag.setString("Unrelated","large gameplay state is not a model key");stack.setCount(19);
                    need(resolve(owner,stack)==actual,"nonappearance NBT/count created a new variant");
                    if(expected!=base){
                        List<BakedQuad> first=actual.getQuads(null,null,0);need(first==actual.getQuads(null,null,0),"warm item draw rebuilt its quads");
                        try{first.clear();throw new AssertionError("prepared list is mutable");}catch(UnsupportedOperationException correct){}
                        need(actual.getQuads(block.getDefaultState(),null,0)!=first&&actual.getQuads(null,null,17)!=first,"state or nonzero seed was served a frozen inventory list");
                        same(expected.getQuads(block.getDefaultState(),null,17),actual.getQuads(block.getDefaultState(),null,17));
                    }
                }
                NBTTagCompound tag=material(CustomBlockMaterials.ID_BASE,2,1);ItemStack mutable=stack(block,"BlockEntityTag",tag);IBakedModel a=resolve(owner,mutable);
                tag.setInteger("housingTexture",CustomBlockMaterials.ID_BASE+1);IBakedModel b=resolve(owner,mutable);need(a!=b,"changed appearance reused old key");
                tag.setInteger("housingTexture",CustomBlockMaterials.ID_BASE);need(resolve(owner,mutable)==a,"mutating original NBT corrupted the prior key");
            }
            ConfiguredMaterialItemModel light=new ConfiguredMaterialItemModel(base,true,atlas);NBTTagCompound tag=new NBTTagCompound();
            need(light.configured(net.minecraft.init.Blocks.STONE,tag)==base,"absent light face changed the default model");
            tag.setInteger("LightFaceTexture",3);same(new RetexturedItemModel(base,atlas.apply(ScreenHousingTextures.texture(3,true)),true),light.configured(net.minecraft.init.Blocks.STONE,tag));
            payload(base,new RetexturedItemModel(base,atlas.apply("replacement")),false);
            payload(base,new TextureLayoutItemModel(base,1),false);
        }
        System.out.println("item-variant-cache materials PASS cube/slab/porthole/light choices, layouts, side faces, appearance-only mutable-NBT isolation, complete records and state/seed delegation; comparisons="+comparisons);
    }
    private static void payload(IBakedModel before,IBakedModel after,boolean positions){
        for(EnumFacing side:SIDES){List<BakedQuad> a=before.getQuads(null,side,0),b=after.getQuads(null,side,0);need(a.size()==b.size(),"payload fixture changed topology");
            for(int q=0;q<a.size();q++){
                int[] source=a.get(q).getVertexData(),target=b.get(q).getVertexData();VertexFormat format=a.get(q).getFormat();int stride=format.getIntegerSize(),uv=format.getUvOffsetById(0)/4;
                need(source.length==target.length,"extended record was truncated");
                for(int i=0;i<source.length;i++)if(i%stride!=uv&&i%stride!=uv+1&&(!positions||i%stride>=3))need(source[i]==target[i],"non-UV extended payload changed at "+i);
            }
        }
    }
    private static IBakedModel dynamic(SimpleBakedModel a,SimpleBakedModel b,AtomicInteger calls){return new SimpleBakedModel(Collections.emptyList(),emptyFaces(),true,true,a.getParticleTexture(),a.getItemCameraTransforms(),ItemOverrideList.NONE){
        @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){calls.incrementAndGet();int level=state!=null&&state.getPropertyKeys().contains(BlockSignalControl.LEVEL)?state.getValue(BlockSignalControl.LEVEL):0;return ((seed+level)&1)==0?a.getQuads(state,side,seed):b.getQuads(state,side,seed);}
    };}
    private static void dynamicSources(){
        ItemVariantModels.clear();Atlas atlas=new Atlas();SimpleBakedModel base=model(atlas,new VertexFormat(DefaultVertexFormats.ITEM));AtomicInteger calls=new AtomicInteger();
        SimpleBakedModel other=model(new Atlas(),new VertexFormat(DefaultVertexFormats.ITEM));IBakedModel dynamic=dynamic(base,other,calls);
        ConfiguredMaterialItemModel owner=new ConfiguredMaterialItemModel(dynamic,false,atlas);Block block=new BlockProgrammableBlock();NBTTagCompound tag=material(CustomBlockMaterials.ID_BASE,-1,1);
        IBakedModel result=owner.configured(block,tag);need(ItemVariantModels.size()==0,"unknown model was admitted");
        for(int seed=0;seed<4;seed++)for(EnumFacing side:SIDES)same(new TextureLayoutItemModel(new RetexturedItemModel(dynamic,atlas.apply(ScreenHousingTextures.texture(CustomBlockMaterials.ID_BASE,true))),1).getQuads(null,side,seed),result.getQuads(null,side,seed));
        need(calls.get()==56,"unknown model stopped observing live seed queries");
        BakedQuad original=base.getQuads(null,null,0).get(0);AtomicInteger version=new AtomicInteger();
        BakedQuad changing=new BakedQuad(original.getVertexData(),original.getTintIndex(),original.getFace(),original.getSprite(),true,original.getFormat()){
            @Override public int[] getVertexData(){int[] data=super.getVertexData().clone();data[0]=Float.floatToRawIntBits(version.get()*.125F);return data;}
        };
        SimpleBakedModel container=new SimpleBakedModel(Collections.singletonList(changing),emptyFaces(),true,true,original.getSprite(),base.getItemCameraTransforms(),ItemOverrideList.NONE);
        ConfiguredMaterialItemModel changingOwner=new ConfiguredMaterialItemModel(container,false,atlas);IBakedModel live=changingOwner.configured(block,tag);
        for(int value=1;value<4;value++){version.set(value);need(live.getQuads(null,null,0).get(0).getVertexData()[0]==Float.floatToRawIntBits(value*.125F),"dynamic quad frozen through its static container");}
        need(ItemVariantModels.size()==0,"dynamic quad was retained");
        System.out.println("item-variant-cache fallback PASS unknown models and dynamic quad subclasses retain live behavior");
    }
    private static void consumerMutation(){
        ItemVariantModels.clear();Atlas atlas=new Atlas();Block block=new BlockProgrammableSlab();
        for(VertexFormat format:new VertexFormat[]{new VertexFormat(DefaultVertexFormats.ITEM),extended()}){
            SimpleBakedModel base=model(atlas,format);ConfiguredMaterialItemModel owner=new ConfiguredMaterialItemModel(base,false,atlas);
            NBTTagCompound tag=material(CustomBlockMaterials.ID_BASE,2,1);IBakedModel prepared=owner.configured(block,tag);
            IBakedModel expected=new TextureLayoutItemModel(new RetexturedItemModel(base,atlas.apply(ScreenHousingTextures.texture(CustomBlockMaterials.ID_BASE,true)),atlas.apply(ScreenHousingTextures.texture(2)),true,false),1);
            for(EnumFacing side:SIDES){
                List<BakedQuad> quads=prepared.getQuads(null,side,0);
                for(BakedQuad quad:quads){
                    int[] records=quad.getVertexData(),before=records.clone();Arrays.fill(records,0xdeadbeef);
                    need(Arrays.equals(before,quad.getVertexData()),"consumer mutation poisoned retained positions/UVs/extended payload");
                    VertexFormat exposed=quad.getFormat();exposed.clear();
                    need(quad.getFormat().equals(format),"consumer mutation poisoned retained vertex format");
                }
                need(prepared.getQuads(null,side,0)==quads,"mutation forced geometry rebuilding instead of safe boundary copies");
                same(expected.getQuads(null,side,0),prepared.getQuads(null,side,0));
            }
            need(owner.configured(block,tag)==prepared,"consumer mutation displaced the cached appearance");
            same(expected,owner.configured(block,tag));
        }
        System.out.println("item-variant-cache consumer mutation PASS normal/extended record and format writes cannot poison warm variants; defensive boundary copies remain");
    }
    private static void unsupportedUv(){
        ItemVariantModels.clear();Atlas atlas=new Atlas();TextureAtlasSprite sprite=atlas.apply("unsupported-uv");
        VertexFormat prefix=new VertexFormat().addElement(DefaultVertexFormats.POSITION_3F).addElement(DefaultVertexFormats.COLOR_4UB);
        VertexFormat unaligned=new VertexFormat(prefix).addElement(DefaultVertexFormats.PADDING_1B).addElement(DefaultVertexFormats.TEX_2F)
                .addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.BYTE,VertexFormatElement.EnumUsage.PADDING,3));
        VertexFormat[] formats={prefix,new VertexFormat(prefix).addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.SHORT,VertexFormatElement.EnumUsage.UV,2)),
                new VertexFormat(prefix).addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.UV,3)),unaligned};
        for(VertexFormat format:formats){
            int[] records=new int[format.getIntegerSize()*4];for(int i=0;i<records.length;i++)records[i]=0x5a001000+i;
            BakedQuad quad=new BakedQuad(records,-1,EnumFacing.UP,sprite,true,format);
            IBakedModel custom=new SimpleBakedModel(Collections.singletonList(quad),emptyFaces(),true,true,sprite,ItemCameraTransforms.DEFAULT,ItemOverrideList.NONE){};
            IBakedModel retextured=new RetexturedItemModel(custom,atlas.apply("replacement")),layout=new TextureLayoutItemModel(custom,1);
            need(retextured.getQuads(null,null,0).get(0)==quad&&layout.getQuads(null,null,0).get(0)==quad,"missing/unsupported UV0 was interpreted or replaced");
            ConfiguredMaterialItemModel owner=new ConfiguredMaterialItemModel(custom,false,atlas);
            IBakedModel configured=owner.configured(new BlockProgrammableSlab(),material(CustomBlockMaterials.ID_BASE,2,1));
            need(configured.getQuads(null,null,0).get(0)==quad&&ItemVariantModels.size()==0,"uncached custom UV format did not retain its source quad");
            need(Arrays.equals(records,quad.getVertexData()),"unsupported UV fallback modified source records");
        }
        System.out.println("item-variant-cache UV fallback PASS absent, nonfloat, non-FLOAT2 and unaligned UV0 stay unchanged for uncached custom models");
    }
    private static void controls(){
        ItemVariantModels.clear();Atlas atlas=new Atlas();SimpleBakedModel base=model(atlas,extended());BlockSignalControl block=new BlockSignalControl("cache_control","thruster_lever",0);
        SignalControlModel owner=new SignalControlModel(base,block),uncached=new SignalControlModel(dynamic(base,base,new AtomicInteger()),block);int cases=0;
        for(int height=0;height<4;height++)for(int tilt=0;tilt<4;tilt++)for(int direction=0;direction<4;direction++){
            NBTTagCompound tag=new NBTTagCompound();tag.setInteger("ControlMountVersion",2);tag.setInteger("BaseHeight",height);tag.setInteger("BaseTilt",tilt);tag.setInteger("TiltDirection",direction);
            ItemStack stack=stack(block,"RedstoneChannelSettings",tag);IBakedModel actual=resolve(owner,stack),expected=resolve(uncached,stack);same(expected,actual);
            ItemCameraTransforms transform=actual.getItemCameraTransforms();ItemTransformVec3f a=expected.getItemCameraTransforms().gui,b=transform.gui;
            need(a.rotation.equals(b.rotation)&&a.translation.equals(b.translation)&&a.scale.equals(b.scale),"cached control padding differs from fresh fitting");
            tag.setInteger("LowLimit",2);tag.setInteger("HighLimit",12);tag.setInteger("redstoneChannel",998);need(resolve(owner,stack)==actual,"control logic values changed appearance key");
            if(height!=0||tilt!=0){
                need(actual.getItemCameraTransforms()==transform,"warm control rebuilt padded transforms");
                for(EnumFacing side:SIDES)for(BakedQuad quad:actual.getQuads(null,side,0)){
                    int[] data=quad.getVertexData();int stride=data.length/4;for(int v=0;v<4;v++){
                        Vec3d p=ControlItemPadding.project(new Vec3d(Float.intBitsToFloat(data[v*stride]),Float.intBitsToFloat(data[v*stride+1]),Float.intBitsToFloat(data[v*stride+2])),b);
                        need(Math.abs(p.x+b.translation.x)<=.376&&Math.abs(p.y+b.translation.y)<=.376,"rotated control icon lacks slot padding");
                    }
                }
            }
            cases++;
        }
        BakedQuad source=base.getQuads(null,null,0).get(0);List<BakedQuad> transformed=new ArrayList<>();
        SignalControlModel.addQuad(transformed,source,new Vec3d[]{new Vec3d(0,0,0),new Vec3d(1,0,0),new Vec3d(1,1,0),new Vec3d(0,1,0)});
        int stride=source.getFormat().getIntegerSize(),normal=source.getFormat().getNormalOffset()/4;int[] original=source.getVertexData(),changed=transformed.get(0).getVertexData();
        for(int v=0;v<4;v++)for(int component=3;component<stride;component++)if(component==normal)need((original[v*stride+component]&0xff000000)==(changed[v*stride+component]&0xff000000),"normal rewrite erased adjacent payload byte");else need(original[v*stride+component]==changed[v*stride+component],"mount rewrite changed nonposition/nonnormal payload");
        IExtendedBlockState state=((IExtendedBlockState)block.getDefaultState()).withProperty(BlockSignalControl.MOUNT,5);
        List<BakedQuad> pinned=owner.getQuads(state,null,0);need(pinned==owner.getQuads(state,null,0),"known world pose no longer reuses geometry");
        IBlockState lateLevel=state.withProperty(BlockSignalControl.LEVEL,1);
        IExtendedBlockState earlyLevel=((IExtendedBlockState)block.getDefaultState().withProperty(BlockSignalControl.LEVEL,1)).withProperty(BlockSignalControl.MOUNT,5);
        List<BakedQuad> level=owner.getQuads(lateLevel,null,0);
        need(level!=pinned,"world key omitted listed state");
        need(owner.getQuads(earlyLevel,null,0)==level,"listed/unlisted assignment order changed a complete appearance key");
        same(uncached.getQuads(lateLevel,null,0),level);
        IBlockState lateFacing=state.withProperty(BlockVandorSwitch.FACING,EnumFacing.EAST);
        List<BakedQuad> turned=owner.getQuads(lateFacing,null,0);
        need(turned!=pinned,"listed facing change after unlisted mount reused the prior orientation");
        same(uncached.getQuads(lateFacing,null,0),turned);
        need(owner.getQuads(state,null,19)!=pinned,"world key omitted seed");
        IUnlistedProperty<Integer> extra=ProgrammableHousingState.integer("foreign_animation");
        IExtendedBlockState unknown=(IExtendedBlockState)new ExtendedBlockState(block,new IProperty[]{BlockVandorSwitch.FACING,BlockVandorSwitch.ON,BlockVandorSwitch.ROTATION,BlockSignalControl.LEVEL},new IUnlistedProperty[]{BlockSignalControl.MOUNT,extra}).getBaseState();
        unknown=unknown.withProperty(BlockSignalControl.MOUNT,5).withProperty(extra,1);
        need(owner.getQuads(unknown,null,0)!=owner.getQuads(unknown,null,0),"unknown extended state was admitted to world cache");
        ItemVariantModels.clear();need(owner.getQuads(state,null,0)!=pinned,"world pose survived resource generation invalidation");
        System.out.println("item-variant-cache controls PASS all 64 mounts, padded GUI transforms, full normal-adjacent payload, listed state/seed/unknown-state guards and lifecycle");
    }
    private static void lifecycleAndBounds(){
        ItemVariantModels.clear();Atlas atlas=new Atlas();SimpleBakedModel base=model(atlas,new VertexFormat(DefaultVertexFormats.ITEM));
        ItemVariantModels.Key key=new ItemVariantModels.Key(base,null,null,0,0,0);IBakedModel first=ItemVariantModels.retain(key,new RetexturedItemModel(base,atlas.apply("next")));
        List<BakedQuad> retained=first.getQuads(null,null,0);ItemVariantModels.stitch(null);need(ItemVariantModels.find(key)==null,"texture stitch retained the old sprites");
        ItemVariantModels.retain(key,new RetexturedItemModel(base,atlas.apply("next")));ItemVariantModels.bake(null);need(ItemVariantModels.find(key)==null,"model bake retained the old owner");
        ItemVariantModels.retain(key,new RetexturedItemModel(base,atlas.apply("next")));VertexFormat saved=new VertexFormat(DefaultVertexFormats.ITEM);
        try{
            DefaultVertexFormats.ITEM.addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.GENERIC,4));
            need(ItemVariantModels.find(key)==null,"in-place shader format expansion kept prepared variants");
            need(first.getQuads(null,null,0)!=retained,"retired prepared object exposed old cached lists");
            need(retained.get(0).getFormat().equals(saved),"retained quad format mutated with global format");
        }finally{
            DefaultVertexFormats.ITEM.clear();for(int i=0;i<saved.getElementCount();i++)DefaultVertexFormats.ITEM.addElement(saved.getElement(i));
            ItemVariantModels.currentGeneration();
        }
        for(int i=0;i<ItemVariantModels.MAX_ENTRIES+20;i++)ItemVariantModels.retain(new ItemVariantModels.Key(base,null,null,i,0,0),base);
        need(ItemVariantModels.size()==ItemVariantModels.MAX_ENTRIES&&ItemVariantModels.retainedBytes()<=ItemVariantModels.MAX_BYTES,"item entry bound failed");
        BakedQuad quad=base.getQuads(null,null,0).get(0);
        SimpleBakedModel medium=new SimpleBakedModel(Collections.nCopies(2000,quad),emptyFaces(),true,true,quad.getSprite(),base.getItemCameraTransforms(),ItemOverrideList.NONE);
        ItemVariantModels.clear();for(int i=0;i<40;i++)ItemVariantModels.retain(new ItemVariantModels.Key(medium,null,null,i,0,0),medium);
        need(ItemVariantModels.size()<40&&ItemVariantModels.retainedBytes()<=ItemVariantModels.MAX_BYTES,"variable mesh size escaped retained-byte limit");
        long bytes=ItemVariantModels.retainedBytes();int count=ItemVariantModels.size();
        int oversized=(int)(ItemVariantModels.MAX_BYTES/(256L+8L*quad.getVertexData().length))+1;
        SimpleBakedModel huge=new SimpleBakedModel(Collections.nCopies(oversized,quad),emptyFaces(),true,true,quad.getSprite(),base.getItemCameraTransforms(),ItemOverrideList.NONE);
        ItemVariantModels.Key large=new ItemVariantModels.Key(huge,null,null,0,0,0);IBakedModel usable=ItemVariantModels.retain(large,huge);
        need(usable.getQuads(null,null,0).size()==oversized&&ItemVariantModels.find(large)==null&&ItemVariantModels.retainedBytes()==bytes&&ItemVariantModels.size()==count,"oversized item displaced retained variants or stopped rendering");
        ItemVariantModels.clear();System.out.println("item-variant-cache lifecycle PASS texture/model reload, shader format mutation/restoration, immutable retained formats, entry/byte bounds and oversized fallback");
    }
    private static void consume(IBakedModel model){
        long checksum=0;
        for(EnumFacing side:SIDES)for(BakedQuad quad:model.getQuads(null,side,0)){
            int[] records=quad.getVertexData();VertexFormat format=quad.getFormat();
            // Escape both consumer-owned copies and visit every component, rather
            // than timing only the cached list lookup and hiding boundary costs.
            consumed=records;consumed=format;checksum+=format.getIntegerSize();for(int word:records)checksum+=word;
        }
        consumed=checksum;consumed=model.getItemCameraTransforms();
    }
    private static void costEvidence(){
        Atlas atlas=new Atlas();SimpleBakedModel base=model(atlas,new VertexFormat(DefaultVertexFormats.ITEM));Block block=new BlockProgrammableSlab();NBTTagCompound tag=material(CustomBlockMaterials.ID_BASE,2,1);
        TextureAtlasSprite main=atlas.apply(ScreenHousingTextures.texture(CustomBlockMaterials.ID_BASE,true)),side=atlas.apply(ScreenHousingTextures.texture(2));
        ConfiguredMaterialItemModel owner=new ConfiguredMaterialItemModel(base,false,atlas);consume(owner.configured(block,tag));long preparations=ItemVariantModels.preparationCount();
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);
        long[][] allocation=new long[2][9],time=new long[2][9];int calls=1024;
        for(int batch=-8;batch<9;batch++)for(int order=0;order<2;order++){
            int path=(batch+order)&1;long before=meter.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime();
            for(int i=0;i<calls;i++)consume(path==0?new TextureLayoutItemModel(new RetexturedItemModel(base,main,side,true,false),1):owner.configured(block,tag));
            long elapsed=System.nanoTime()-start,allocated=meter.getThreadAllocatedBytes(Thread.currentThread().getId())-before;if(batch>=0){allocation[path][batch]=allocated;time[path][batch]=elapsed;}
        }
        for(int path=0;path<2;path++){Arrays.sort(allocation[path]);Arrays.sort(time[path]);System.out.println("item-variant-cache cost path="+(path==0?"uncached-variants":"prepared-variants")+" consumer=complete-records-and-formats calls="+calls+" median-allocated-bytes="+allocation[path][4]+" median-wall-ns="+time[path][4]);}
        need(ItemVariantModels.preparationCount()==preparations,"warm override path rebuilt prepared models");
        need(allocation[1][4]<allocation[0][4],"prepared inventory path did not reduce allocation after defensive boundary copies");
        need(allocation[1][4]>0,"allocation fixture did not include defensive consumer copies");
    }
    public static void main(String[] ignored)throws Exception{
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy(){@Override public String customTexture(int choice){return "variant-check:custom/"+choice;}};
        BoundedRenderCacheChecks.main(ignored);materials();dynamicSources();consumerMutation();unsupportedUv();controls();lifecycleAndBounds();costEvidence();
        System.out.println("item-variant-cache PASS CPU/allocation checks; hardware FPS, live atlas reload and shader-pack acceptance remain client checks");
    }
}

package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.persistence.*;
import com.vandorlabs.tiles.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelBakeEvent;

/** Picked stacks already contain the settings; their inventory models must consume them too. */
public final class ConfiguredPickItemModels implements IBakedModel {
    private static final int DOOR=0,TRAPDOOR=1,PORTHOLE=2;
    private static final BoundedRenderCache<Key,ItemAppearanceMesh> CACHE=new BoundedRenderCache<>(128,8L*1024*1024);
    private static long generation=-1;
    private final IBakedModel base;
    private final int kind;
    private final boolean large;
    private final IBakedModel[] glass;

    private static final class Key {
        final ConfiguredPickItemModels owner;final int[] values;
        Key(ConfiguredPickItemModels owner,int... values){this.owner=owner;this.values=values;}
        public int hashCode(){return 31*System.identityHashCode(owner)+Arrays.hashCode(values);}
        public boolean equals(Object raw){return raw instanceof Key&&owner==((Key)raw).owner&&Arrays.equals(values,((Key)raw).values);}
    }

    /** Run after the existing scale/material wrappers so unchanged models keep their transforms. */
    public static void install(ModelBakeEvent event){
        CACHE.clear();generation=ItemVariantModels.currentGeneration();
        for(ModelResourceLocation location:new ArrayList<>(event.getModelRegistry().getKeys())){
            if(!"vandorlabs".equals(location.getResourceDomain())||!"inventory".equals(location.getVariant()))continue;
            String path=location.getResourcePath();int kind=-1;boolean large=false;IBakedModel[] glass=new IBakedModel[0];
            if(path.startsWith("detailed_doors/")&&!path.contains("_paired_")&&(path.endsWith("_left_leaf")||path.endsWith("_left_leaf_no_hinges"))){
                kind=DOOR;large=path.contains("/large_");String nativePath=path.replace("/large_","/").replace("_left_leaf_no_hinges","").replace("_left_leaf","");
                glass=new IBakedModel[large?2:1];
                for(int side=0;side<glass.length;side++)glass[side]=event.getModelRegistry().getObject(new ModelResourceLocation("vandorlabs:"+nativePath+(large?"_paired":"")+(side==0?"_left_glass":"_right_glass"),"inventory"));
            }else if(path.startsWith("configured/programmable_trapdoor_")||path.startsWith("configured/programmable_diagonal_trapdoor_"))kind=TRAPDOOR;
            else if(path.startsWith("configured/programmable_porthole_wall_")||path.startsWith("configured/programmable_porthole_block_")||path.equals("programmable_diagonal_porthole"))kind=PORTHOLE;
            IBakedModel base=event.getModelRegistry().getObject(location);
            if(kind>=0&&base!=null)event.getModelRegistry().putObject(location,new ConfiguredPickItemModels(base,kind,large,glass));
        }
    }
    private ConfiguredPickItemModels(IBakedModel base,int kind,boolean large,IBakedModel[] glass){this.base=base;this.kind=kind;this.large=large;this.glass=glass;}
    private final ItemOverrideList overrides=new ItemOverrideList(Collections.emptyList()){
        @Override public IBakedModel handleItemState(IBakedModel original,ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity){
            NBTTagCompound tag=stack.getSubCompound(kind==DOOR?"SpaceDoorSettings":"BlockEntityTag");
            // Raw metadata models are also used by world door rendering. Never add
            // item-only faces to those temporary, unconfigured stacks.
            if(tag==null)return base;
            long current=ItemVariantModels.currentGeneration();if(generation!=current){CACHE.clear();generation=current;}
            if(kind==DOOR)return door(tag);
            Block block=Block.getBlockFromItem(stack.getItem());
            return kind==TRAPDOOR?trapdoor(block,tag):porthole(block,tag);
        }
    };
    private static TextureAtlasSprite sprite(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);}
    private ItemAppearanceMesh retain(Key key,List<BakedQuad> quads,TextureAtlasSprite particle,boolean pad){ItemAppearanceMesh result=new ItemAppearanceMesh(base,quads,particle,pad);CACHE.put(key,result,result.bytes);return result;}
    private IBakedModel door(NBTTagCompound tag){
        int choice=tag.hasKey("DoorFaceTexture",3)&&tag.getInteger("DoorFaceTexture")>=0?ScreenHousingTextures.clamp(tag.getInteger("DoorFaceTexture")):-1;
        int layout=tag.hasKey("DoorTextureLayout",3)&&com.vandorlabs.render.TextureLayoutMode.valid(tag.getInteger("DoorTextureLayout"))?tag.getInteger("DoorTextureLayout"):tag.getBoolean("DoorTileTexture")?1:0;
        FrameAppearance frame=new FrameAppearance();frame.read(tag);DoorGlassAppearance pane=DoorGlassAppearance.read(tag);
        Key key=new Key(this,choice,layout,frame.front,frame.interior,pane.material,pane.shade,pane.tint,pane.layout);
        ItemAppearanceMesh cached=CACHE.get(key);if(cached!=null)return cached;
        List<BakedQuad> raw=ItemAppearanceMesh.all(base),out=new ArrayList<>();
        IBakedModel framed=FrameDoorModel.get(base,frame,large?3:1);
        String nativeLeaf=base.getParticleTexture().getIconName();
        for(BakedQuad q:ItemAppearanceMesh.all(framed))if(choice<0||q.getFace().getAxis()!=EnumFacing.Axis.Z||!q.getSprite().getIconName().equals(nativeLeaf))out.add(q);
        TextureAtlasSprite particle=choice<0?base.getParticleTexture():sprite(ScreenHousingTextures.fullTexture(choice));
        if(choice>=0){
            boolean split=CustomBlockMaterials.isCustom(choice)&&CustomBlockTextures.isDoor(choice);
            TextureAtlasSprite upper=split?CustomBlockTextures.sprite(choice,true):particle,lower=split?CustomBlockTextures.sprite(choice,false):particle;
            for(int hand=0;hand<(large?2:1);hand++){
                List<BakedQuad> leaf=new ArrayList<>();
                for(BakedQuad q:raw)if(q.getSprite().getIconName().equals(nativeLeaf)&&(!large||(meanX(q)<.5)==(hand==0)))leaf.add(q);
                if(leaf.isEmpty())continue;
                SelectedDoorGeometry geometry=new SelectedDoorGeometry(new ItemAppearanceMesh(base,leaf,base.getParticleTexture(),false));
                TextureAtlasSprite edge=sprite("vandorlabs:blocks/programmable_glass/metal_side");
                ItemAppearanceMesh.Capture capture=new ItemAppearanceMesh.Capture(1,1,1,0,0,particle,upper,lower,edge);
                SelectedDoorFaceCache.get(geometry,choice,layout,large?3:1,ScreenHousingTextures.doorArtworkMirror(choice,hand==1)).draw(capture,0,0);
                out.addAll(capture.finish());
                if(ScreenHousingTextures.reactorArtwork(choice)){
                    int material=pane.material==DoorGlassAppearance.ORIGINAL?-1:pane.material;
                    TextureAtlasSprite glazing=sprite(GlassMaterials.texture(material,pane.shade,1,pane.tint));
                    ItemAppearanceMesh.Capture paneCapture=new ItemAppearanceMesh.Capture(1,1,1,0,0,glazing);
                    ReactorDoorGlazing.get(geometry,pane,true,ScreenHousingTextures.doorArtworkMirror(choice,hand==1),large?3:1).drawColored(paneCapture,0,0,1,1,1,GlassMaterials.opacity(material,pane.tint));
                    out.addAll(paneCapture.finish());
                }
            }
        }else if(pane.material!=DoorGlassAppearance.ORIGINAL&&glassConfigurable(tag)){
            // Generated inventory door previews omit the separate world glass pass.
            // Include it when glazing is explicitly chosen, using the native outline.
            for(int hand=0;hand<glass.length;hand++)if(glass[hand]!=null){
                IBakedModel finish=DoorGlassModel.get(glass[hand],pane,large?1.5:1);
                for(BakedQuad q:ItemAppearanceMesh.all(finish))out.add(large?scaled(q,.5,.5,1D/3,hand*.5):q);
            }
        }
        return retain(key,out,particle,true);
    }
    private static boolean glassConfigurable(NBTTagCompound tag){int design=tag.getInteger("SpaceDesign");return design==15||design==16||design==29;}
    private static double meanX(BakedQuad q){int[] d=q.getVertexData();int stride=d.length/4;double x=0;for(int v=0;v<4;v++)x+=Float.intBitsToFloat(d[v*stride]);return x/4;}
    private static BakedQuad scaled(BakedQuad q,double x,double y,double z,double offsetX){int[] d=q.getVertexData().clone();int stride=d.length/4;for(int v=0;v<4;v++){d[v*stride]=Float.floatToRawIntBits((float)(Float.intBitsToFloat(d[v*stride])*x+offsetX));d[v*stride+1]=Float.floatToRawIntBits((float)(Float.intBitsToFloat(d[v*stride+1])*y));d[v*stride+2]=Float.floatToRawIntBits((float)(Float.intBitsToFloat(d[v*stride+2])*z));}return new BakedQuad(d,q.getTintIndex(),q.getFace(),q.getSprite(),q.shouldApplyDiffuseLighting(),q.getFormat());}
    private IBakedModel trapdoor(Block block,NBTTagCompound tag){
        if(!(block instanceof BlockProgrammableTrapdoor))return base;
        boolean diagonal=block instanceof BlockProgrammableDiagonalTrapdoor;
        TrapdoorData data=TrapdoorData.read(new NbtPrimitiveData(tag),diagonal,ScreenHousingTextures.DEFAULT_TRAPDOOR,ScreenHousingTextures::clamp);
        Key key=new Key(this,data.texture,data.position,data.layout(),data.cover?1:0,data.sliding?1:0,diagonal?1:0,tag.getBoolean("DiagonalInverted")?1:0);
        ItemAppearanceMesh cached=CACHE.get(key);if(cached!=null)return cached;
        // Decode only appearance; no channels, linked members, world or live state.
        NBTTagCompound clean=new NBTTagCompound();data.write(new NbtPrimitiveData(clean));clean.setBoolean("DiagonalInverted",tag.getBoolean("DiagonalInverted"));
        TileEntityProgrammableTrapdoor tile=diagonal?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();tile.readFromNBT(clean);
        IBlockState state=block.getDefaultState().withProperty(BlockProgrammableTrapdoor.FACING,EnumFacing.NORTH);
        if(diagonal)state=state.withProperty(BlockProgrammableTrapdoor.HALF,tag.getBoolean("DiagonalInverted")?net.minecraft.block.BlockTrapDoor.DoorHalf.TOP:net.minecraft.block.BlockTrapDoor.DoorHalf.BOTTOM);
        TextureAtlasSprite main=sprite(ScreenHousingTextures.fullTexture(data.texture)),edge=sprite("vandorlabs:blocks/programmable_glass/metal_side");
        boolean split=CustomBlockMaterials.isCustom(data.texture)&&CustomBlockTextures.isDoor(data.texture);
        ItemAppearanceMesh.Capture capture=new ItemAppearanceMesh.Capture(1,1,1,0,0,main,edge,split?CustomBlockTextures.sprite(data.texture,true):null,split?CustomBlockTextures.sprite(data.texture,false):null);
        TEProgrammableTrapdoor.drawConfiguredLeaf(capture,main,tile,state,0,0);
        return retain(key,capture.finish(),main,true);
    }
    private IBakedModel porthole(Block block,NBTTagCompound tag){
        if(!(block instanceof BlockProgrammableWall)||!((BlockProgrammableWall)block).isPortholeShape())return base;
        int main=tag.hasKey("housingTexture",3)?ScreenHousingTextures.clamp(tag.getInteger("housingTexture")):0;
        int side=tag.hasKey("SideTexture",3)&&tag.getInteger("SideTexture")>=0?ScreenHousingTextures.clamp(tag.getInteger("SideTexture")):-1;
        int shape=Math.max(0,Math.min(3,tag.getInteger("PortholeShape")));
        FrameAppearance frame=new FrameAppearance();frame.read(tag);
        PanelGlassSettings pane=PanelGlassSettings.read(new NbtPrimitiveData(tag),GlassMaterials::clamp);
        boolean thick=block instanceof BlockProgrammablePortholeBlock;
        boolean diagonal=((BlockProgrammableWall)block).isDiagonalShape();
        int slope=WallSettings.geometry(tag.getBoolean(WallSettings.HALF),tag.getBoolean(WallSettings.FULL));
        Key key=new Key(this,main,side,shape,frame.interior,frame.exteriorChoice(),pane.material,pane.shade,pane.tint,pane.textureLayout(),thick?1:0,diagonal?slope:-1);
        ItemAppearanceMesh cached=CACHE.get(key);if(cached!=null)return cached;
        String fallback=side>=0?ScreenHousingTextures.texture(side):"vandorlabs:blocks/programmable_glass/metal_side";
        TextureAtlasSprite wall=sprite(ScreenHousingTextures.texture(main)),inner=sprite(frame.interior(fallback)),outer=sprite(frame.exterior(fallback)),glass=sprite(GlassMaterials.texture(pane.material,pane.shade,1,pane.tint));
        ItemAppearanceMesh.Capture capture=new ItemAppearanceMesh.Capture(1D/16,1D/16,thick?1D/4:1D/16,0,thick?-1.5:0,wall,inner,outer,glass);
        int border=thick?1:0;PortholeHex.Slice opening=PortholeGeometryCache.slice(1,1,shape,border,0,0);
        DiagonalPortholeMesh projection=diagonal?new DiagonalPortholeMesh(slope,false,Double.NaN,Double.NaN,null):null;
        TEAnimatedScreenSelector.drawPortholeBar(capture,wall,inner,outer,opening,thick?16:4,projection);
        StaticSurfaceMesh.Capture raw=StaticSurfaceMesh.independentCapture();TEAnimatedScreenSelector.drawPortholeGlassGeometry(raw,opening,projection);
        StaticSurfaceMesh paneMesh=raw.finish();GlassSurfaceGroups.Layout paneLayout=new GlassSurfaceGroups.Layout(0,0,1,1);
        double bx=pane.material<0?0:(2+border)/16D,by=pane.material<0?0:(4+border)/16D;int mode=pane.material<0?0:pane.textureLayout();
        paneMesh=mode==0?paneMesh.glassTexture(glass,paneLayout,true,bx,by):paneMesh.textureLayout(glass,paneLayout,true,bx,by,mode);
        paneMesh.drawColored(capture,0,0,1,1,1,GlassMaterials.opacity(pane.material,pane.tint));
        return retain(key,capture.finish(),wall,true);
    }
    static long retainedBytes(){return CACHE.retainedBytes();}
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return base.getQuads(state,side,seed);}
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    public boolean isGui3d(){return base.isGui3d();}
    public boolean isBuiltInRenderer(){return base.isBuiltInRenderer();}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}
    public ItemOverrideList getOverrides(){return overrides;}
}

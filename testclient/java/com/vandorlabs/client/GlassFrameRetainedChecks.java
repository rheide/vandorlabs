package com.vandorlabs.client;

import com.vandorlabs.CommonProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.blocks.PanelDepth;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.CoreModManager;
import sun.misc.Unsafe;

/** Actual retained frame records, independent of the pane and its light/material path. */
public final class GlassFrameRetainedChecks {
    private static int checks,cases;
    private static final List<Integer> EMISSIVE=new ArrayList<>();
    private static final Field VERTICES=field(StaticSurfaceMesh.class,"vertices");
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Field field(Class<?> owner,String name){try{Field result=owner.getDeclaredField(name);result.setAccessible(true);return result;}catch(Exception failure){throw new AssertionError(failure);}}
    private static InputStream resource(ResourceLocation id)throws Exception{
        InputStream stream=GlassFrameRetainedChecks.class.getResourceAsStream("/assets/"+id.getResourceDomain()+"/"+id.getResourcePath());
        return stream==null?new SharedTextureResources().getInputStream(id):stream;
    }
    private static final class Sprite extends TextureAtlasSprite {
        Sprite(String name,BufferedImage image,int slot){
            super(name);setIconWidth(image.getWidth());setIconHeight(image.getHeight());
            setFramesTextureData(new ArrayList<>(Collections.singletonList(new int[][]{image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth())})));
            initSprite(4096,4096,(slot&15)*256,(slot>>4)*256,false);
        }
    }
    private static final class Atlas extends TextureMap {
        final Map<String,TextureAtlasSprite> sprites=new LinkedHashMap<>();
        Atlas(){super("");}
        @Override public TextureAtlasSprite getAtlasSprite(String name){
            TextureAtlasSprite existing=sprites.get(name);if(existing!=null)return existing;
            BufferedImage pixels=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)pixels.setRGB(x,y,0xffffffff);
            Sprite sprite=new Sprite(name,pixels,sprites.size());sprites.put(name,sprite);return sprite;
        }
        void authored(int choice)throws Exception{
            String name=ScreenHousingTextures.texture(choice);ResourceLocation id=new ResourceLocation(name.replace(":blocks/",":textures/blocks/")+".png");
            BufferedImage image;try(InputStream stream=resource(id)){image=ImageIO.read(stream);}
            need(image!=null,"missing shipped frame image "+name);int partial=0;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int alpha=image.getRGB(x,y)>>>24;if(alpha>0&&alpha<255)partial++;}
            need(partial>0,"emissive catalog fixture no longer contains authored partial alpha "+name);
            sprites.put(name,new Sprite(name,image,sprites.size()));
        }
    }
    private static void initialize()throws Exception{
        field(CoreModManager.class,"deobfuscatedEnvironment").setBoolean(null,true);Bootstrap.register();VandorLabs.proxy=new CommonProxy();
        Unsafe unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);Minecraft mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);
        Atlas atlas=new Atlas();
        Object resources=Proxy.newProxyInstance(net.minecraft.client.resources.IResourceManager.class.getClassLoader(),new Class[]{net.minecraft.client.resources.IReloadableResourceManager.class},(proxy,method,args)->{
            if(!method.getName().equals("getResource"))return null;
            ResourceLocation id=(ResourceLocation)args[0];InputStream stream=resource(id);
            return Proxy.newProxyInstance(net.minecraft.client.resources.IResource.class.getClassLoader(),new Class[]{net.minecraft.client.resources.IResource.class},(item,call,values)->{
                if(call.getName().equals("getInputStream"))return stream;if(call.getName().equals("getResourceLocation"))return id;
                if(call.getName().equals("close")){stream.close();return null;}return call.getReturnType()==boolean.class?false:null;
            });
        });
        for(Field value:Minecraft.class.getDeclaredFields()){
            value.setAccessible(true);
            if(Modifier.isStatic(value.getModifiers())&&value.getType()==Minecraft.class)value.set(null,mc);
            else if(!Modifier.isStatic(value.getModifiers())){
                if(value.getType()==TextureMap.class)value.set(mc,atlas);
                if(net.minecraft.client.resources.IResourceManager.class.isAssignableFrom(value.getType()))value.set(mc,resources);
            }
        }
        for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++){
            com.google.gson.JsonObject entry=ScreenHousingTextures.entry(choice);
            if(entry!=null&&entry.has("frameLight")&&entry.get("frameLight").getAsInt()>0){EMISSIVE.add(choice);atlas.authored(choice);}
        }
        need(EMISSIVE.size()==8,"expand the eight actual catalog frame cases when adding artwork");
    }
    private static VertexFormat normals(){return new VertexFormat(DefaultVertexFormats.BLOCK).addElement(DefaultVertexFormats.NORMAL_3B).addElement(DefaultVertexFormats.PADDING_1B);}
    private static VertexFormat extended(){return normals().addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.PADDING,6));}
    private static TileEntityProgrammableGlass tile(NonRenderingChecks.MemoryWorld world,BlockPos pos,IBlockState state,int front,boolean joined){
        TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.frameAppearance.front=front;tile.frameAppearance.interior=EMISSIVE.get((EMISSIVE.indexOf(front)+1)%EMISSIVE.size());tile.frameAppearance.exterior=-1;
        tile.setWorld(world);tile.setPos(pos);world.tiles.put(pos,tile);world.states.put(pos,state);tile.setJoin(joined);return tile;
    }
    private static float value(int[] records,int offset){return Float.intBitsToFloat(records[offset]);}
    private static void same(float actual,double expected,String why){need(Float.floatToRawIntBits(actual)==Float.floatToRawIntBits((float)expected),why+" expected="+expected+" actual="+actual);}
    private static int[] records(TileEntityProgrammableGlass tile,IBlockState state,VertexFormat format,BlockPos origin)throws Exception{
        int[] raw=GlassFrameRetained.capture(tile,state,format,origin);float[] before=(float[])VERTICES.get(GlassFrameMesh.get(tile));
        int stride=format.getIntegerSize(),uv=format.getUvOffsetById(0)/4,light=format.getUvOffsetById(1)/4;boolean rotated=state.getValue(BlockGlassWall.ROTATED);
        double depth=PanelDepth.offset(state.getValue(BlockGlassWall.DEPTH))/16D;
        int packed=tile.getWorld().getCombinedLight(tile.getPos(),tile.frameAppearance.light());if(tile.frameAppearance.light()>0)packed=(packed&0xffff0000)|240;
        need(raw.length==before.length/8*stride,"retention filled a hole or duplicated authored frame faces");
        for(int vertex=0;vertex<before.length/8;vertex++){
            int at=vertex*stride,source=vertex*8;double x=before[source]/16D,y=before[source+1]/16D,z=before[source+2]/16D;
            same(value(raw,at),tile.getPos().getX()-origin.getX()+(rotated?1-z-depth:x),"frame X placement");
            same(value(raw,at+1),tile.getPos().getY()-origin.getY()+y,"frame Y placement");
            same(value(raw,at+2),tile.getPos().getZ()-origin.getZ()+(rotated?x:z+depth),"frame Z placement");
            same(value(raw,at+uv),before[source+3],"authored U altered");same(value(raw,at+uv+1),before[source+4],"authored V altered");
            need(raw[at+light]==packed,"frame emissivity lost or pane light substituted");need(raw[at+format.getColorOffset()/4]==-1,"authored alpha/color altered");
            if(format.hasNormal()){
                float nx=before[source+5],ny=before[source+6],nz=before[source+7];int expected=((byte)(int)((rotated?-nz:nx)*127)&255)|(((byte)(int)(ny*127)&255)<<8)|(((byte)(int)((rotated?nx:nz)*127)&255)<<16);
                need((raw[at+format.getNormalOffset()/4]&0xffffff)==expected,"rotated frame normal changed");
            }
        }
        cases++;return raw;
    }
    private static void coverage(BlockProgrammableGlass block,boolean rotated,int depth,VertexFormat format)throws Exception{
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);BlockPos origin=new BlockPos(-320,80,12192);
        IBlockState state=block.getDefaultState().withProperty(BlockGlassWall.ROTATED,rotated).withProperty(BlockGlassWall.DEPTH,depth);
        List<TileEntityProgrammableGlass> cells=new ArrayList<>();int index=0;
        for(int row=0;row<3;row++)for(int column=0;column<3;column++)if(row!=1||column!=1)
            cells.add(tile(world,origin.add(rotated?0:column,row,rotated?column:0),state,EMISSIVE.get(index++),true));
        List<int[]> parts=new ArrayList<>();int size=0;
        for(TileEntityProgrammableGlass tile:cells){int[] raw=records(tile,state,format,origin);need(raw.length>0,"missing perimeter/hole frame geometry");parts.add(raw);size+=raw.length;
            GlassFrameModel.Mesh nativeMesh=GlassFrameModel.build(tile,state,format);need(!nativeMesh.translucent.isEmpty(),"actual partial-alpha artwork classified as opaque");need(!GlassFrameChunks.nativeLighting(nativeMesh,tile.frameAppearance.light()),"mixed-light frame unexpectedly went through the shared native light path");}
        int[] joined=new int[size];int at=0;for(int[] part:parts){System.arraycopy(part,0,joined,at,part.length);at+=part.length;}
        GlassFrameRetained.Section section=new GlassFrameRetained.Section(GlassFrameRetained.sectionKey(origin));
        GlassFrameRetained.Group group=new GlassFrameRetained.Group(section,cells,format,joined);
        need(group.members.size()==8&&group.anchor==cells.get(0),"retained frame owner/anchor mismatch");need(group.bounds.minY==80&&group.bounds.maxY==83,"group culling bounds omit joined members");
        need(group.bytes>=joined.length*8,"budget excludes CPU/GPU record copies");
        VertexFormat saved=new VertexFormat(group.format);format.addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.UBYTE,VertexFormatElement.EnumUsage.PADDING,4));
        need(group.format.equals(saved),"frame group retained mutable live format");cells.get(0).setPos(origin.east(7));need(group.positions.get(group.anchor).equals(origin),"moving tile changed saved group placement");
        GlassSurfaceGroups.unload(world);
    }
    private static void panesDoNotChangeFrame(BlockProgrammableGlass block)throws Exception{
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);BlockPos pos=new BlockPos(7,65,3);IBlockState state=block.getDefaultState();
        TileEntityProgrammableGlass tile=tile(world,pos,state,EMISSIVE.get(0),false);VertexFormat format=normals();int[] original=records(tile,state,format,BlockPos.ORIGIN);
        int custom=0;for(;custom<ScreenHousingTextures.BUILTIN_COUNT;custom++)if(com.vandorlabs.tiles.GlassMaterials.valid(custom))break;
        need(custom<ScreenHousingTextures.BUILTIN_COUNT,"custom glass fixture missing");
        for(int tint=0;tint<4;tint++)for(int material:new int[]{-1,custom}){
            tile.setMaterial(material);tile.setAppearance(tint,true);
            need(Arrays.equals(original,records(tile,state,format,BlockPos.ORIGIN)),"pane opacity/material changed stationary frame records");
        }
        GlassSurfaceGroups.unload(world);
    }
    private static void joinedHole(BlockProgrammableGlass block)throws Exception{
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);BlockPos origin=new BlockPos(32,64,48);IBlockState state=block.getDefaultState();
        TileEntityProgrammableGlass[][] tiles=new TileEntityProgrammableGlass[3][3];
        for(int row=0;row<3;row++)for(int column=0;column<3;column++)tiles[row][column]=tile(world,origin.add(column,row,0),state,EMISSIVE.get(0),true);
        need(GlassSurfaceGroups.frameMask(tiles[1][1])==0,"complete joined interior retained per-block borders");
        need(records(tiles[1][1],state,normals(),origin).length==0,"empty joined interior retained frame vertices");
        int before=records(tiles[1][0],state,normals(),origin).length;BlockPos hole=tiles[1][1].getPos();world.tiles.remove(hole);world.states.remove(hole);GlassSurfaceGroups.invalidate(world);
        need((GlassSurfaceGroups.frameMask(tiles[1][0])&2)!=0,"opening an interior cell did not restore the adjoining hole rim");
        need(records(tiles[1][0],state,normals(),origin).length>before,"new hole boundary did not acquire frame geometry");
        GlassSurfaceGroups.unload(world);
    }
    private static void boundaries(BlockProgrammableGlass block){
        IBlockState state=block.getDefaultState();BlockPos pos=new BlockPos(-1,80,-1);GlassFrameRetained.Key key=new GlassFrameRetained.Key(state,3,pos);
        need(key.equals(new GlassFrameRetained.Key(state,3,pos.add(7,6,0))),"coplanar mixed frame finishes cannot share a draw");
        need(!key.equals(new GlassFrameRetained.Key(state,3,pos.south())),"parallel physical planes merged");
        need(!key.equals(new GlassFrameRetained.Key(state,4,pos)),"shader material identity merged");
        need(GlassFrameRetained.sectionKey(pos)==new BlockPos(-16,80,-16).toLong(),"negative render-section boundary rounded incorrectly");
    }
    public static void main(String[] args)throws Exception{
        initialize();BlockProgrammableGlass block=new BlockProgrammableGlass("retained_frame_checks");boundaries(block);panesDoNotChangeFrame(block);joinedHole(block);
        for(boolean rotated:new boolean[]{false,true})for(int depth=0;depth<3;depth++){
            coverage(block,rotated,depth,new VertexFormat(DefaultVertexFormats.BLOCK));coverage(block,rotated,depth,normals());coverage(block,rotated,depth,extended());
        }
        System.out.println("GlassFrameRetained PASS checks="+checks+" captures="+cases+" actual eight alpha-blended catalog fronts, exact legacy positions/UVs/normals, fullbright block light, preserved sky light, sparse joins, independent pane finishes, immutable formats/positions and bounded record accounting");
    }
    private GlassFrameRetainedChecks(){}
}

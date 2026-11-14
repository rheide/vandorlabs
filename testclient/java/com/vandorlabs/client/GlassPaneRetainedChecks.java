package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.CoreModManager;

/** Real retained writers: sparse coverage, per-cell light/UV, placement and material boundaries. */
public final class GlassPaneRetainedChecks {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static final class Sprite extends TextureAtlasSprite {
        Sprite(String suffix,int x){super("vandorlabs:checks/retained-"+suffix);setIconWidth(32);setIconHeight(32);initSprite(512,512,x,64,false);}
    }
    private static VertexFormat normals(){return new VertexFormat(DefaultVertexFormats.BLOCK).addElement(DefaultVertexFormats.NORMAL_3B).addElement(DefaultVertexFormats.PADDING_1B);}
    // OptiFine owns the trailing shader fields; ordinary vertex methods skip
    // those padding bytes, then SVertexBuilder fills their actual payload.
    private static VertexFormat extended(){return normals().addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.PADDING,6));}
    private static TileEntityProgrammableGlass tile(NonRenderingChecks.MemoryWorld world,BlockPos pos,IBlockState state){
        TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.setWorld(world);tile.setPos(pos);
        world.states.put(pos,state);world.tiles.put(pos,tile);return tile;
    }
    private static GlassPaneRetained.Key key(IBlockState state,TextureAtlasSprite sprite,BlockPos pos,boolean single){return new GlassPaneRetained.Key(state,sprite,7,1,single,true,pos);}
    private static float value(int[] raw,int at){return Float.intBitsToFloat(raw[at]);}
    private static void coverage(BlockProgrammableGlass block,VertexFormat format,boolean rotated,int depth,boolean single){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        IBlockState state=block.getDefaultState().withProperty(BlockGlassWall.ROTATED,rotated).withProperty(BlockGlassWall.DEPTH,depth);
        BlockPos origin=new BlockPos(1280,80,-12192);List<TileEntityProgrammableGlass> cells=new ArrayList<>();
        for(int row=0;row<3;row++)for(int column=0;column<3;column++)if(row!=1||column!=1)
            cells.add(tile(world,origin.add(rotated?0:column,row,rotated?column:0),state));
        Sprite sprite=new Sprite("gold",32);GlassPaneRetained.Key key=key(state,sprite,origin,single);
        int[] raw=GlassPaneRetained.capture(key,cells,format,origin);
        int stride=format.getIntegerSize(),uv=format.getUvOffsetById(0)/4,light=format.getUvOffsetById(1)/4;
        int vertices=single?8:16;
        need(raw.length==cells.size()*vertices*stride,"sparse wall introduced or removed cell vertices");
        for(int cell=0;cell<cells.size();cell++){
            BlockPos pos=cells.get(cell).getPos();int expectedLight=world.getCombinedLight(pos,0);
            for(int vertex=0;vertex<vertices;vertex++){
                int at=(cell*vertices+vertex)*stride;
                need(raw[at+light]==expectedLight,"retained group lost per-cell lighting: stride="+format.getNextOffset()+" lightOffset="+light+" cell="+cell+" vertex="+vertex+" expected="+Integer.toHexString(expectedLight)+" actual="+Integer.toHexString(raw[at+light]));
                float x=value(raw,at),y=value(raw,at+1),z=value(raw,at+2);
                need(y>=pos.getY()-origin.getY()&&y<=pos.getY()-origin.getY()+1,"vertical placement drift");
                double along=rotated?z:x,offset=rotated?pos.getZ()-origin.getZ():pos.getX()-origin.getX();
                need(along>=offset&&along<=offset+1,"group filled missing cells or crossed authored cell bounds");
                need(value(raw,at+uv)>=sprite.getMinU()&&value(raw,at+uv)<=sprite.getMaxU(),"U escaped atlas sprite");
                need(value(raw,at+uv+1)>=sprite.getMinV()&&value(raw,at+uv+1)<=sprite.getMaxV(),"V escaped atlas sprite");
                need(raw[at+format.getColorOffset()/4]==-1,"authored tint received extra vertex alpha");
            }
            int first=cell*vertices*stride;
            double cross0=cross(raw,first,stride,rotated),cross1=cross(raw,first+4*stride,stride,rotated);
            need(cross0*cross1<0,"opposing sheet windings no longer cull to one face");
            double plane0=value(raw,first+(rotated?0:2)),plane1=value(raw,first+4*stride+(rotated?0:2));
            need(plane0==plane1,"one physical retained sheet has separated front/back positions");
            if(!single)need(Math.abs(value(raw,first+8*stride+(rotated?0:2))-plane0)>.1,"explicit double-sheet mode collapsed thickness");
        }
        GlassPaneRetained.Section section=new GlassPaneRetained.Section(GlassPaneRetained.sectionKey(origin));
        GlassPaneRetained.Group group=new GlassPaneRetained.Group(section,key,cells,format,raw);
        need(group.members.size()==8&&group.vertices==8*vertices,"retained group changed owner count");
        need(group.anchor==cells.get(0),"anchor is not deterministic");
        need(group.bounds.minY==80&&group.bounds.maxY==83,"group visibility bounds omit joined rows");
        need(group.bytes>=raw.length*8,"retained budget omits CPU/GPU record copies");
        VertexFormat stored=new VertexFormat(group.format);format.addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.UBYTE,VertexFormatElement.EnumUsage.PADDING,4));
        need(group.format.equals(stored),"retained group borrowed a mutable vertex format");
        cells.get(0).setPos(origin.add(8,0,0));need(group.positions.get(group.anchor).equals(origin),"owner move mutated retained placement");
        GlassSurfaceGroups.unload(world);
    }
    private static double cross(int[] raw,int at,int stride,boolean rotated){
        int x=rotated?2:0;
        double ax=value(raw,at+stride+x)-value(raw,at+x),ay=value(raw,at+stride+1)-value(raw,at+1);
        double bx=value(raw,at+2*stride+x)-value(raw,at+x),by=value(raw,at+2*stride+1)-value(raw,at+1);
        return ax*by-ay*bx;
    }
    private static void boundaries(BlockProgrammableGlass block){
        IBlockState state=block.getDefaultState().withProperty(BlockGlassWall.ROTATED,false).withProperty(BlockGlassWall.DEPTH,0);
        Sprite gold=new Sprite("gold",32),blue=new Sprite("blue",96);BlockPos origin=new BlockPos(0,64,0);
        GlassPaneRetained.Key key=key(state,gold,origin,true);
        need(key.equals(key(state,gold,origin.add(7,8,0),true)),"coplanar joined cells cannot share records");
        need(!key.equals(key(state,gold,origin.south(),true)),"parallel depth planes merged");
        need(!key.equals(key(state,blue,origin,true)),"different atlas materials merged");
        need(!key.equals(key(state,gold,origin,false)),"single/double culling modes merged");
        need(!key.equals(new GlassPaneRetained.Key(state,gold,8,1,true,true,origin)),"shader material IDs merged");
        need(!key.equals(new GlassPaneRetained.Key(state,gold,7,.5F,true,true,origin)),"per-material opacity merged");
        need(!key.equals(new GlassPaneRetained.Key(state,gold,7,1,true,false,origin)),"custom/no-cull geometry merged with palette/cull geometry");
        need(GlassPaneRetained.sectionKey(new BlockPos(-1,64,-1))==new BlockPos(-16,64,-16).toLong(),"negative section boundary rounded toward zero");
        need(GlassPaneRetained.sectionKey(origin)!=GlassPaneRetained.sectionKey(origin.east(16)),"groups crossed render-section boundary");
    }
    public static void main(String[] args)throws Exception{
        Field environment=CoreModManager.class.getDeclaredField("deobfuscatedEnvironment");environment.setAccessible(true);environment.setBoolean(null,true);
        Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();
        BlockProgrammableGlass block=new BlockProgrammableGlass("retained_glass_checks");boundaries(block);
        for(boolean rotated:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(boolean single:new boolean[]{false,true}){
            coverage(block,normals(),rotated,depth,single);coverage(block,extended(),rotated,depth,single);
        }
        System.out.println("GlassPaneRetained PASS checks="+checks+" sparse joined walls, actual writers, authored UVs, cell lighting, opposing culling, immutable formats/positions and material/section boundaries");
    }
}

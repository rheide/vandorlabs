package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.screen.ScreenFamily;
import com.vandorlabs.tiles.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.FloatBuffer;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Original whole-role row renderer, real font trimming and GL transforms; only final submissions are recorded. */
final class ScreenRowsGLOracle {
    private static final class Capture extends BufferBuilder {
        final List<JsonObject> draws=new ArrayList<>();final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);
        Capture(){super(1<<17);}
        @Override public void begin(int mode,net.minecraft.client.renderer.vertex.VertexFormat format){matrix.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);super.begin(mode,format);}
        void finish(){finishDrawing();if(getVertexCount()==0||getByteBuffer().getInt(12)!=0xFF1D140A)return;JsonObject draw=new JsonObject();draw.addProperty("stride",getVertexFormat().getNextOffset());draw.addProperty("vertices",getVertexCount());draw.add("matrix",matrix(matrix));JsonArray raw=new JsonArray();for(int i=0;i<getVertexCount()*getVertexFormat().getNextOffset();i+=4)raw.add(getByteBuffer().getInt(i));draw.add("data",raw);draws.add(draw);}
    }
    private static JsonArray matrix(FloatBuffer values){JsonArray result=new JsonArray();for(int i=0;i<16;i++)result.add(Float.floatToRawIntBits(values.get(i)));return result;}
    private static final class CpuTessellator extends Tessellator {final Capture buffer;CpuTessellator(Capture buffer){super(16);this.buffer=buffer;}@Override public BufferBuilder getBuffer(){return buffer;}@Override public void draw(){buffer.finish();}}
    private static final class SpyFont extends FontRenderer {
        final FontRenderer original;final List<JsonObject> text=new ArrayList<>();final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);
        SpyFont(Minecraft mc,FontRenderer original){super(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);this.original=original;}
        @Override public String trimStringToWidth(String value,int width){return original.trimStringToWidth(value,width);}
        @Override public int drawString(String value,int x,int y,int color){JsonObject command=new JsonObject();command.addProperty("text",value);command.addProperty("x",x);command.addProperty("y",y);command.addProperty("color",color);matrix.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);command.add("matrix",matrix(matrix));text.add(command);return original.getStringWidth(value);}
    }
    static void capture(Minecraft mc,File target)throws Exception{
        Field singleton=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(Tessellator.class,"INSTANCE","field_78398_a"),modifiers=Field.class.getDeclaredField("modifiers");modifiers.setAccessible(true);int flags=singleton.getModifiers();modifiers.setInt(singleton,flags&~Modifier.FINAL);Object oldTess=singleton.get(null);Capture capture=new Capture();FontRenderer oldFont=mc.fontRenderer;SpyFont font=new SpyFont(mc,oldFont);singleton.set(null,new CpuTessellator(capture));mc.fontRenderer=font;
        BlockPos pos=new BlockPos(mc.player.posX,240,mc.player.posZ);IBlockState oldState=mc.world.getBlockState(pos);net.minecraft.tileentity.TileEntity oldTile=mc.world.getTileEntity(pos);long time=mc.world.getTotalWorldTime();int profiles=0,rectangles=0,texts=0;Gson gson=new Gson();int front=ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1");GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
        try(PrintWriter out=new PrintWriter(target,"UTF-8")){
            for(ScreenFamily family:ScreenFamily.values()){BlockAnimatedScreenSelector block=null;for(net.minecraft.block.Block candidate:ModBlocks.BLOCKS)if(candidate.getRegistryName().getResourcePath().equals(family.id))block=(BlockAnimatedScreenSelector)candidate;if(block==null)throw new AssertionError(family);
                for(IBlockState state:((net.minecraft.block.Block)block).getBlockState().getValidStates())for(int slot=0;slot<2;slot++)if(family.supportsSlot(slot))for(int variant=0;variant<7;variant++)for(boolean framed:new boolean[]{false,true}){
                    mc.world.setBlockState(pos,state,2);NBTTagCompound tag=((net.minecraft.block.BlockContainer)block).createNewTileEntity(null,0).writeToNBT(new NBTTagCompound());tag.setInteger("FrameFront",framed?front:-1);tag.setBoolean("FrameTile",true);tag.setInteger("displayMode",2);tag.setBoolean("smallInput",variant==3);tag.setInteger("wallPosition",variant%3);tag.setBoolean("CeilingMounted",variant==4);tag.setInteger("CeilingPosition",1);
                    NBTTagCompound controls=new NBTTagCompound();controls.setBoolean("Enabled",true);controls.setString("Title",variant==0?"":"CONTROL DECK ALPHA BETA GAMMA");NBTTagList rows=new NBTTagList();int count=variant==0?0:variant==2?family.standaloneControlRows(slot):variant==4?4:1;
                    for(int i=0;i<count;i++){NBTTagCompound row=new NBTTagCompound();boolean slider=variant==4||variant==2&&i%3==1,pulse=variant>=5||variant==2&&i%3==2;row.setString("Label",slider?"WIDE SLIDER":"Toggle with wider label");row.setIntArray("RedstoneChannels",new int[]{93700+i});row.setBoolean("Slider",slider);row.setBoolean("Pulse",pulse);row.setInteger("Min",5);row.setInteger("Max",15);row.setInteger("Level",i%4*5);row.setIntArray("Levels",new int[]{i%4*5});row.setBoolean("Active",variant==3||i%2==1);row.setLong("PulseUntil",variant==6?100:120);rows.appendTag(row);}controls.setTag("Rows",rows);tag.setTag(slot==0?"RedstonePrimary":"RedstoneSecondary",controls);
                    TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();((net.minecraft.tileentity.TileEntity)tile).readFromNBT(tag);((net.minecraft.tileentity.TileEntity)tile).setWorld(mc.world);((net.minecraft.tileentity.TileEntity)tile).setPos(pos);mc.world.setTileEntity(pos,tile);mc.world.setTotalWorldTime(112);capture.draws.clear();font.text.clear();GL11.glLoadIdentity();net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance.getRenderer(tile).render(tile,0,0,0,0,-1,1);if(capture.draws.size()!=1||font.text.isEmpty())throw new AssertionError("Missing actual Source row submission "+family+"/"+slot+"/"+variant+" draws="+capture.draws.size());
                    RedstoneScreenContents actual=tile.redstoneScreen(slot);JsonObject profile=new JsonObject();profile.addProperty("id",family.id);profile.addProperty("meta",((net.minecraft.block.Block)block).getMetaFromState(state));profile.addProperty("slot",slot);profile.addProperty("variant",variant);profile.addProperty("frame",framed);profile.addProperty("small",tile.isSmallInput());profile.addProperty("wall",tile.getWallPosition(-1));profile.addProperty("ceiling",tile.isCeilingMounted());profile.addProperty("ceilingPosition",tile.getCeilingPosition(-1));profile.addProperty("title",actual.title());com.vandorlabs.render.ScreenSurface.Quad content=FrameSurfaceRenderer.content(tile,RedstoneScreenInteractions.surface(state,tile,slot));JsonArray quad=new JsonArray();for(com.vandorlabs.render.ScreenSurface.Vertex vertex:content.vertices){quad.add(vertex.x);quad.add(vertex.y);quad.add(vertex.z);}quad.add(content.nx);quad.add(content.ny);quad.add(content.nz);profile.add("content",quad);JsonArray actualRows=new JsonArray();for(RedstoneScreenContents.Row row:actual.rows()){JsonObject r=new JsonObject();r.addProperty("label",row.label);r.addProperty("trim",oldFont.trimStringToWidth(row.label,row.slider?50:86));r.addProperty("slider",row.slider);r.addProperty("pulse",row.pulse);r.addProperty("active",row.active());r.addProperty("segment",row.selectedSegment());actualRows.add(r);}profile.add("rows",actualRows);profile.addProperty("trimTitle",oldFont.trimStringToWidth(actual.title(),112));profile.add("draw",capture.draws.get(0));profile.add("text",gson.toJsonTree(font.text));out.println(gson.toJson(profile));profiles++;rectangles+=capture.draws.get(0).get("vertices").getAsInt()/4;texts+=font.text.size();
                }
            }
        }finally{mc.world.setTotalWorldTime(time);mc.world.setBlockState(pos,oldState,2);if(oldTile!=null)mc.world.setTileEntity(pos,oldTile);GL11.glPopMatrix();GL11.glPopAttrib();mc.fontRenderer=oldFont;singleton.set(null,oldTess);modifiers.setInt(singleton,flags);}
        if(profiles!=1148||target.length()>32*1024*1024)throw new AssertionError("Source row coverage/size "+profiles+" bytes="+target.length());System.out.println("[vandorlabs][screen-rows-source] profiles="+profiles+" rectangles="+rectangles+" text="+texts+" bytes="+target.length());
    }
}

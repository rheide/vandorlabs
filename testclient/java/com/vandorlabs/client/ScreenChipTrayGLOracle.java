package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import java.io.*;
import java.lang.reflect.*;
import java.nio.FloatBuffer;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
/** Actual orientedMount, authored baked frame and overlays under real released GL transforms. */
final class ScreenChipTrayGLOracle {
 private static final class Capture extends BufferBuilder {final List<JsonObject> draws=new ArrayList<>();final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);Capture(){super(1<<16);}@Override public void begin(int mode,net.minecraft.client.renderer.vertex.VertexFormat format){matrix.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);super.begin(mode,format);}void finish(){finishDrawing();if(getVertexCount()==0)return;JsonObject draw=new JsonObject();draw.addProperty("stride",getVertexFormat().getNextOffset());draw.addProperty("vertices",getVertexCount());JsonArray pose=new JsonArray();for(int i=0;i<16;i++)pose.add(Float.floatToRawIntBits(matrix.get(i)));draw.add("matrix",pose);JsonArray data=new JsonArray();for(int i=0;i<getVertexCount()*getVertexFormat().getNextOffset();i+=4)data.add(getByteBuffer().getInt(i));draw.add("data",data);draws.add(draw);}}
 private static final class CpuTessellator extends Tessellator {final Capture buffer;CpuTessellator(Capture buffer){super(16);this.buffer=buffer;}@Override public BufferBuilder getBuffer(){return buffer;}@Override public void draw(){buffer.finish();}}
 private static final class Reader extends TileEntityTokenSlot {int variant;Reader(){super(true);}@Override public boolean rejected(){return variant==2;}@Override public boolean indicatorOn(){return variant==1;}@Override public int effectiveAllowed(){return 0;}}
 static void capture(Minecraft mc,File target)throws Exception{
  Field singleton=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(Tessellator.class,"INSTANCE","field_78398_a"),modifiers=Field.class.getDeclaredField("modifiers");modifiers.setAccessible(true);int flags=singleton.getModifiers();modifiers.setInt(singleton,flags&~Modifier.FINAL);Object previous=singleton.get(null);Capture capture=new Capture();singleton.set(null,new CpuTessellator(capture));Field frames=ScreenChipSupport.class.getDeclaredField("FRAMES");frames.setAccessible(true);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();int profiles=0;
  try(PrintWriter out=new PrintWriter(target,"UTF-8")){for(net.minecraft.block.Block candidate:new net.minecraft.block.Block[]{new BlockProgrammableConsole(),new BlockProgrammableHalfConsole(),new BlockProgrammableDiagonalScreen(),new BlockDiagonalHalfConsole("programmable_diagonal_half_screen")}){BlockAnimatedScreenSelector block=(BlockAnimatedScreenSelector)candidate;for(net.minecraft.block.state.IBlockState state:((net.minecraft.block.Block)block).getBlockState().getValidStates())for(boolean ceiling:new boolean[]{false,true})for(int position=0;position<3;position++){
   TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)((net.minecraft.block.BlockContainer)block).createNewTileEntity(null,0);NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("CeilingMounted",ceiling);tag.setInteger("CeilingPosition",position);((net.minecraft.tileentity.TileEntity)tile).readFromNBT(tag);((Map<?,?>)frames.get(null)).clear();MachineMountSurface surface=ScreenChipSupport.mount(state,tile);FloatBuffer basis=BufferUtils.createFloatBuffer(16);basis.put(new float[]{(float)surface.u.x,(float)surface.u.y,(float)surface.u.z,0,(float)surface.v.x,(float)surface.v.y,(float)surface.v.z,0,(float)-surface.normal.x,(float)-surface.normal.y,(float)-surface.normal.z,0,0,0,0,1}).flip();Reader reader=new Reader();((net.minecraft.tileentity.TileEntity)reader).setWorld(mc.world);((net.minecraft.tileentity.TileEntity)reader).setPos(((net.minecraft.entity.Entity)mc.player).getPosition());for(int variant=0;variant<3;variant++){reader.variant=variant;capture.draws.clear();GL11.glLoadIdentity();TETokenSlot.orientedMount(reader,surface,basis,0,0,0,0);if(capture.draws.size()!=2)throw new AssertionError("Tray actual frame+overlay submissions "+capture.draws.size());JsonObject row=new JsonObject();row.addProperty("id",block.getRegistryName().getResourcePath());row.addProperty("meta",((net.minecraft.block.Block)block).getMetaFromState(state));row.addProperty("ceiling",ceiling);row.addProperty("position",position);row.addProperty("variant",variant);JsonArray draws=new JsonArray();for(JsonObject draw:capture.draws)draws.add(draw);row.add("draws",draws);out.println(row);profiles++;}
  }}if(profiles!=576)throw new AssertionError("Tray complete pose coverage "+profiles);}finally{singleton.set(null,previous);modifiers.setInt(singleton,flags);GL11.glPopMatrix();GL11.glPopAttrib();}System.out.println("[vandorlabs][screen-chip-tray-source] PASS profiles="+profiles+" actual orientedMount/model/overlay GL capture");
 }
}

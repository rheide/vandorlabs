package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.programmer.ChipJournal;
import com.vandorlabs.tiles.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

/** Whole released regular-Screen TESR, actual joined owner submission and font/GL matrices. */
final class ScreenJoinedJournalGLOracle {
 private static final int[][] SHAPES={{1,1},{2,1},{1,2},{2,2},{3,2},{2,3},{3,1},{1,3}};
 static void capture(Minecraft mc,File target)throws Exception{
  Field singleton=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(Tessellator.class,"INSTANCE","field_78398_a"),modifiers=Field.class.getDeclaredField("modifiers");modifiers.setAccessible(true);int flags=singleton.getModifiers();modifiers.setInt(singleton,flags&~Modifier.FINAL);Object previous=singleton.get(null);ScreenJournalGLOracle.Capture capture=new ScreenJournalGLOracle.Capture();FontRenderer originalFont=mc.fontRenderer;ScreenJournalGLOracle.SpyFont font=new ScreenJournalGLOracle.SpyFont(mc,originalFont);singleton.set(null,new ScreenJournalGLOracle.CpuTessellator(capture));mc.fontRenderer=font;GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();int profiles=0;Block screen=Block.getBlockFromName("vandorlabs:programmable_viewscreen"),readerBlock=Block.getBlockFromName("vandorlabs:data_chip_slot");BlockPos base=new BlockPos((mc.player.chunkCoordX<<4)+8,240,(mc.player.chunkCoordZ<<4)+8);int frame=ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1");if(frame<0)throw new AssertionError("Released frame material unavailable");List<BlockPos> touched=new ArrayList<>();
  try(PrintWriter out=new PrintWriter(target,"UTF-8")){for(EnumFacing face:EnumFacing.values())for(int[] shape:SHAPES)for(int variant=0;variant<3;variant++){
   int width=shape[0],height=shape[1];IBlockState state=screen.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,face);List<TileEntityAnimatedScreenSelector> tiles=new ArrayList<>();mc.world.setBlockState(base,state,2);touched.add(base);TileEntityAnimatedScreenSelector seed=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(base);DisplayJoinGeometry.Basis basis=DisplayJoinGeometry.basis(seed,state);
   for(int y=0;y<height;y++)for(int x=0;x<width;x++){BlockPos at=base.add(basis.steps[0].getX()*x+basis.steps[2].getX()*y,basis.steps[0].getY()*x+basis.steps[2].getY()*y,basis.steps[0].getZ()*x+basis.steps[2].getZ()*y);mc.world.setBlockState(at,state,2);if(!touched.contains(at))touched.add(at);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(at);NBTTagCompound raw=new NBTTagCompound();raw.setInteger("PrimarySurfaceTexture",-4);raw.setInteger("ChipDisplayPage",1);raw.setInteger("FrameFront",variant==0?-1:frame);raw.setBoolean("FrameTile",variant==2);raw.setBoolean("DisplayFit",variant==2);((TileEntity)tile).readFromNBT(raw);((TileEntity)tile).setWorld(mc.world);((TileEntity)tile).setPos(at);tiles.add(tile);}
   BlockPos mounted=((TileEntity)tiles.get(tiles.size()-1)).getPos().offset(face);mc.world.setBlockState(mounted,readerBlock.getDefaultState().withProperty(BlockVandorSwitch.FACING,face),2);touched.add(mounted);TileEntityTokenSlot reader=(TileEntityTokenSlot)mc.world.getTileEntity(mounted);ItemStack chip=new ItemStack(ModItems.DATA_CHIP,1,1);ChipJournal.write(chip,true,"JOINED SOURCE JOURNAL",new String[]{"FIRST PAGE\nFIRST LINE","SECOND PAGE\nSECOND LINE","THIRD PAGE"});reader.setInventorySlotContents(0,chip);ScreenChipGroups.Group group=ScreenChipGroups.group(tiles.get(0));if(group==null||group.columns!=width||group.rows!=height||group.members.size()!=width*height||group.reader!=reader)throw new AssertionError("Actual joined layout/reader identity "+face+" "+width+"x"+height);
   group.owner.chipDisplay();group.owner.chipPage=1;capture.draws.clear();font.text.clear();int before=ScreenChipRenderer.submissions;TEAnimatedScreenSelector renderer=new TEAnimatedScreenSelector();for(TileEntityAnimatedScreenSelector tile:group.members){GL11.glLoadIdentity();renderer.render(tile,0,0,0,0,-1,1);}
   if(ScreenChipRenderer.submissions-before!=1||font.text.isEmpty())throw new AssertionError("Whole-role joined canvas must submit once with visible journal text");JsonObject row=new JsonObject();row.addProperty("facing",face.getIndex());row.addProperty("columns",width);row.addProperty("rows",height);row.addProperty("frame",variant);long cell=basis.cell(((TileEntity)group.owner).getPos());row.addProperty("ownerColumn",(int)(cell>>32)-group.minColumn);row.addProperty("ownerRow",(int)cell-group.minRow);row.addProperty("submissions",ScreenChipRenderer.submissions-before);row.addProperty("page",group.owner.chipPage);com.vandorlabs.render.ScreenSurface.Quad q=FrameSurfaceRenderer.content(group.owner,RedstoneScreenInteractions.surface(state,group.owner,0));JsonArray content=new JsonArray();for(double value:new double[]{q.topRight.x,q.topRight.y,q.topRight.z,q.topLeft.x,q.bottomRight.y,q.bottomRight.z,q.ny,q.nz})content.add(value);row.add("content",content);row.add("text",new Gson().toJsonTree(font.text));JsonArray draws=new JsonArray();for(JsonObject draw:capture.draws){JsonObject compact=new JsonObject();compact.add("stride",draw.get("stride"));compact.add("vertices",draw.get("vertices"));compact.add("matrix",draw.get("matrix"));draws.add(compact);}row.add("draws",draws);out.println(row);profiles++;
   for(BlockPos at:touched)mc.world.setBlockToAir(at);touched.clear();
  }if(profiles!=144)throw new AssertionError("Joined journal complete coverage "+profiles);}finally{for(BlockPos at:touched)mc.world.setBlockToAir(at);mc.fontRenderer=originalFont;singleton.set(null,previous);modifiers.setInt(singleton,flags);GL11.glPopMatrix();GL11.glPopAttrib();}System.out.println("[vandorlabs][screen-joined-journal-source] PASS profiles="+profiles+" actualwholeTESR/owneronly/fontGL");
 }
}

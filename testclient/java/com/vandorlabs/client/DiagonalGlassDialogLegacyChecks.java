package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.lang.reflect.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Frozen actual compact legacy controls, including the dormant hidden frame button. */
public final class DiagonalGlassDialogLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)uf.get(null);
  GuiProgrammableWall screen=(GuiProgrammableWall)unsafe.allocateInstance(GuiProgrammableWall.class);Field tf=GuiProgrammableWall.class.getDeclaredField("tile");tf.setAccessible(true);Method controls=GuiProgrammableWall.class.getDeclaredMethod("diagonalGlassControls",ProgrammableDialogLayout.class);controls.setAccessible(true);
  NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(0,80,0);BlockProgrammableDiagonalGlass block=new BlockProgrammableDiagonalGlass();java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");int cases=0;
  for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean inv:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int fill=0;fill<4;fill++)for(int join=0;join<12;join++)for(int material:new int[]{-1,7})for(int tint:new int[]{0,3,7}){
   world.setBlockState(pos,block.getDefaultState().withProperty(BlockProgrammableWall.FACING,face).withProperty(BlockProgrammableWall.INVERTED,inv),2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);tile.setDiagonalGeometry(mode,fill);tile.setDiagonalJoin(join);tile.setGlassMaterial(material);tile.setGlassAppearance(tint,(fill&1)!=0);tile.setGlassLayout((fill&2)==0,(join&1)==0);tile.setGlassTextureLayout(join%3);tf.set(screen,tile);
   for(int[] size:new int[][]{{640,360},{320,220}}){@SuppressWarnings("unchecked") java.util.List<GuiButton> rows=(java.util.List<GuiButton>)controls.invoke(screen,new ProgrammableDialogLayout(size[0],size[1]));for(GuiButton b:rows){String value=b.id+":"+b.x+":"+b.y+":"+b.width+":"+b.height+":"+b.displayString+":"+b.enabled+":"+b.visible+";";digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}cases++;}
  }
  StringBuilder text=new StringBuilder();for(byte b:digest.digest())text.append(String.format("%02x",b&255));String expected="b1400329944f945fa254f32182649c82319c986dd95c3a96d12cc5beda7e6a48";if(!expected.isEmpty()&&!expected.equals(text.toString()))throw new AssertionError("actual diagonal glass presentation changed "+text);System.out.println("Actual diagonal glass dialog PASS cases="+cases+" golden="+text);
 }
}

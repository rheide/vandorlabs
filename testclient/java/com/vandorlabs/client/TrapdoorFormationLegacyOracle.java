package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual specialized placement discovers pairs/squares/rectangles/staggered and bent surfaces. */
public final class TrapdoorFormationLegacyOracle {
 private static final BlockPos ORIGIN=new BlockPos(10,80,10);
 private static List<BlockPos> cells(int shape,int mode,EnumFacing face,boolean inverted,boolean diagonal){
  EnumFacing across=diagonal?face.rotateYCCW():EnumFacing.EAST,along=diagonal?(mode==2?face.getOpposite():EnumFacing.UP):EnumFacing.SOUTH;
  List<BlockPos> out=new ArrayList<>();int width=shape==0?2:shape==1?1:shape==3?3:shape==6||shape==7?9:2,height=shape==0||shape==6?1:2;
  for(int row=0;row<height;row++)for(int col=0;col<width;col++){if((shape==5||shape>=9)&&row==1&&col==1)continue;BlockPos p=ORIGIN.offset(across,col).offset(along,row);if((shape==4||shape==10)&&row!=0)p=mode==2?p.up(inverted?-row:row):p.offset(inverted?face:face.getOpposite(),row);out.add(p);}return out;
 }
 private static List<int[]> orders(int count){List<int[]> result=new ArrayList<>();int[] a=new int[count];for(int i=0;i<count;i++)a[i]=i;if(count<=4)permutations(a,0,result);else{result.add(a.clone());for(int i=0;i<count;i++)a[i]=count-1-i;result.add(a.clone());for(int i=0;i<count;i++)a[i]=(i+count/2)%count;result.add(a.clone());}return result;}
 private static void permutations(int[] a,int at,List<int[]> out){if(at==a.length){out.add(a.clone());return;}for(int i=at;i<a.length;i++){int old=a[at];a[at]=a[i];a[i]=old;permutations(a,at+1,out);old=a[at];a[at]=a[i];a[i]=old;}}
 private static JsonArray snapshots(NonRenderingChecks.MemoryWorld w,List<BlockPos> cells){JsonArray a=new JsonArray();for(BlockPos p:cells)if(w.tiles.get(p) instanceof TileEntityProgrammableTrapdoor){JsonObject n=TrapdoorPlacementLegacyOracle.snapshot(w,p);n.addProperty("pos",p.toLong());a.add(n);}return a;}
 public static void main(String[] args)throws Exception{ItemBlock[] items=TrapdoorPlacementLegacyOracle.initialize();Path output=Paths.get(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");int profiles=0;try(java.io.BufferedWriter writer=Files.newBufferedWriter(output,StandardCharsets.UTF_8)){
  for(int family=0;family<2;family++)for(int mode=0;mode<3;mode++)for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int shape=0;shape<(family==0?8:11);shape++){
   List<BlockPos> cells=cells(shape,mode,face,inverted,family==1);for(int[] order:orders(cells.size())){NonRenderingChecks.MemoryWorld w=new NonRenderingChecks.MemoryWorld(false);BlockProgrammableTrapdoor block=(BlockProgrammableTrapdoor)items[family].getBlock();JsonObject record=new JsonObject();record.addProperty("family",family);record.addProperty("mode",mode);record.addProperty("facing",face.getHorizontalIndex());record.addProperty("inverted",inverted);record.addProperty("shape",shape);JsonArray input=new JsonArray(),stages=new JsonArray();
    for(int index:order){BlockPos p=cells.get(index);TileEntityProgrammableTrapdoor isolated=family==0?new TileEntityProgrammableTrapdoor():new TileEntityProgrammableDiagonalTrapdoor();isolated.configure(0,mode,index%2!=0,index%3,com.vandorlabs.redstone.ChannelList.of(11+index));NBTTagCompound tag=isolated.itemSettings();tag.removeTag("TrapdoorCoverFacing");tag.setBoolean("TrapdoorSlideIntoWall",index%2!=0);tag.setBoolean("TrapdoorSlideOverSurface",index%2==0);ItemStack held=new ItemStack(items[family]);held.setTagInfo("BlockEntityTag",tag);net.minecraft.block.state.IBlockState state=block.getDefaultState().withProperty(BlockTrapDoor.FACING,shape==9&&index==2?face.getOpposite():face).withProperty(BlockTrapDoor.HALF,(inverted^(shape==8&&index>=2||shape==9&&index==2))?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM);
     JsonObject n=new JsonObject();n.addProperty("pos",p.toLong());n.addProperty("tag",tag.toString());n.addProperty("facing",state.getValue(BlockTrapDoor.FACING).getHorizontalIndex());n.addProperty("top",state.getValue(BlockTrapDoor.HALF)==BlockTrapDoor.DoorHalf.TOP);input.add(n);if(!items[family].placeBlockAt(held,null,w,p,EnumFacing.UP,.5F,.5F,.5F,state))throw new AssertionError("specialized formation placement");stages.add(snapshots(w,cells));
    }
    record.add("input",input);record.add("stages",stages);BlockPos removed=cells.get(order[0]);TileEntityProgrammableTrapdoor owner=(TileEntityProgrammableTrapdoor)w.getTileEntity(removed);record.addProperty("pickBefore",block.configuredDrop(owner).getSubCompound("BlockEntityTag").toString());block.breakBlock(w,removed,w.getBlockState(removed));w.states.put(removed,net.minecraft.init.Blocks.AIR.getDefaultState());record.addProperty("harvestAfter",block.configuredDrop(owner).getSubCompound("BlockEntityTag").toString());record.add("survivors",snapshots(w,cells));String line=record.toString()+"\n";writer.write(line);digest.update(line.getBytes(StandardCharsets.UTF_8));profiles++;com.vandorlabs.redstone.RedstoneChannels.worldUnloaded(new net.minecraftforge.event.world.WorldEvent.Unload(w));
   }
  }
  }StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));if(profiles!=4080||!hash.toString().equals("06a275758a78a9a3d24043ea9adb02f5e33028d9a5427de0f110c5b94e494da0"))throw new AssertionError("Released Trapdoor formation changed "+hash);System.out.println("Actual Trapdoor formation/removal PASS profiles="+profiles+" golden="+hash);
 }
}

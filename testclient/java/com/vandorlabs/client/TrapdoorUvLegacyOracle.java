package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.render.*;
import java.io.*;
import java.util.*;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Actual released tile/group resolution, block bounds/collision and block/offset rays. */
public final class TrapdoorUvLegacyOracle {
 private static final BlockPos ORIGIN=new BlockPos(16,64,-16);
 private static final double[] POSES={0,.125,.5,.875,1};
 private static JsonArray numbers(double... values){JsonArray out=new JsonArray();for(double value:values)out.add(value);return out;}
 private static JsonArray corners(double[][] values){JsonArray out=new JsonArray();for(double[] value:values)out.add(numbers(value));return out;}
 private static JsonArray box(AxisAlignedBB b){return numbers(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
 private static JsonElement hit(RayTraceResult h){if(h==null)return JsonNull.INSTANCE;JsonObject out=new JsonObject();out.add("point",numbers(h.hitVec.x,h.hitVec.y,h.hitVec.z));out.addProperty("face",h.sideHit.getIndex());return out;}
 private static NBTTagCompound tag(BlockPos pos,int mode,boolean diagonal,EnumFacing face,boolean inverted,int flags,List<BlockPos> members){NBTTagCompound n=new NBTTagCompound();n.setInteger("x",pos.getX());n.setInteger("y",pos.getY());n.setInteger("z",pos.getZ());n.setInteger("housingTexture",ScreenHousingTextures.DEFAULT_TRAPDOOR);n.setInteger("TrapdoorPosition",mode);n.setBoolean("TrapdoorSliding",(flags&1)!=0);n.setBoolean("TrapdoorCover",!diagonal&&(flags&2)!=0);n.setBoolean("TrapdoorSlideOverSurface",!diagonal&&(flags&4)!=0);n.setBoolean("TrapdoorSlideIntoWall",diagonal&&(flags&4)!=0);n.setInteger("TrapdoorCoverFacing",face.getHorizontalIndex());n.setBoolean("DiagonalReverse",(flags&8)!=0);n.setBoolean("DiagonalRotationFlipped",(flags&16)!=0);n.setInteger("DiagonalFacing",face.getHorizontalIndex());n.setBoolean("DiagonalInverted",inverted);n.setInteger("DiagonalHingeSide",flags%3);n.setInteger("DiagonalGroupFacing",face.getHorizontalIndex());NBTTagList list=new NBTTagList();for(BlockPos member:members)list.appendTag(new NBTTagLong(member.toLong()));n.setTag("TrapdoorAssembly",list);n.setDouble("TrapdoorHinge",(flags&16)==0?-.375:1.9375);n.setDouble("TrapdoorTravel",2.9375);return n;}
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();BlockProgrammableTrapdoor regular=new BlockProgrammableTrapdoor();BlockProgrammableDiagonalTrapdoor diagonalBlock=new BlockProgrammableDiagonalTrapdoor();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;int doorChoice=-1;for(int i=0;i<ScreenHousingTextures.BUILTIN_COUNT;i++){int choice=ScreenHousingTextures.choiceAt(i);if(ScreenHousingTextures.isDoor(choice)){doorChoice=choice;break;}}if(doorChoice<0)throw new AssertionError("Door artwork missing");
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(boolean diagonal:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(EnumFacing face:EnumFacing.HORIZONTALS)for(int flags=0;flags<32;flags++){
    boolean inverted=diagonal&&(flags&2)!=0,cover=!diagonal&&(flags&2)!=0,assembly=(flags&8)!=0&&!cover;BlockPos origin=(flags&16)==0?ORIGIN:new BlockPos(29999980,64,-29999980);BlockProgrammableTrapdoor block=diagonal?diagonalBlock:regular;NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);List<BlockPos> members=new ArrayList<>();members.add(origin);if(assembly){EnumFacing width=diagonal?face.rotateYCCW():EnumFacing.EAST,along=diagonal?(mode==2?face.getOpposite():EnumFacing.UP):EnumFacing.SOUTH;members.add(origin.offset(width));members.add(origin.offset(along));members.add(origin.offset(width).offset(along));}
    for(int i=0;i<members.size();i++){BlockPos at=members.get(i);EnumFacing peerFace=diagonal&&i>=2&&(flags&16)!=0?face.getOpposite():face;boolean peerInverted=diagonal&&i>=2&&(flags&16)!=0?!inverted:inverted;IBlockState state=block.getDefaultState().withProperty(BlockTrapDoor.FACING,peerFace).withProperty(BlockTrapDoor.HALF,(diagonal?peerInverted:mode==2)?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM);world.setBlockState(at,state,2);TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)world.getTileEntity(at);tile.readFromNBT(tag(at,mode,diagonal,peerFace,peerInverted,flags,assembly?members:Collections.emptyList()));}
    TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)world.getTileEntity(origin);if(cover&&(flags&16)!=0){BlockPos opposite=origin.offset(face,3);world.setBlockState(opposite,regular.getDefaultState().withProperty(BlockTrapDoor.FACING,face.getOpposite()),2);((TileEntityProgrammableTrapdoor)world.getTileEntity(opposite)).readFromNBT(tag(opposite,mode,false,face.getOpposite(),false,flags,Collections.emptyList()));}
    List<TileEntityProgrammableTrapdoor> group=tile.group();if(group.size()!=(assembly?4:1))throw new AssertionError("Actual group mismatch");IBlockState state=world.getBlockState(origin);
    for(int rawLayout:new int[]{-1,0,1,2})for(boolean rawTile:new boolean[]{false,true})for(boolean door:new boolean[]{false,true})for(double aspect:new double[]{.5,1,2}){
     NBTTagCompound saved=tag(origin,mode,diagonal,face,inverted,flags,assembly?members:Collections.emptyList());saved.setInteger("TrapdoorTextureLayout",rawLayout);saved.setBoolean("TrapdoorTileTexture",rawTile);saved.setInteger("housingTexture",door?doorChoice:ScreenHousingTextures.DEFAULT_TRAPDOOR);tile.readFromNBT(saved);
     double[][] closed=new double[8][3],uv=new double[8][3];boolean tiled=TEProgrammableTrapdoor.materialCoordinates(tile,state,group,closed,uv,aspect);
     JsonObject record=new JsonObject();record.addProperty("diagonal",diagonal);record.addProperty("mode",mode);record.addProperty("face",face.getHorizontalIndex());record.addProperty("flags",flags);record.addProperty("rawLayout",rawLayout);record.addProperty("layout",tile.getTextureLayout());record.addProperty("rawTile",rawTile);record.addProperty("door",door);record.addProperty("aspect",aspect);record.addProperty("tiled",tiled);record.add("closed",corners(closed));record.add("uv",corners(uv));out.println(gson.toJson(record));profiles++;
    }
   }
  }
  SegmentedReferenceGolden.check(file,"85d4a983a0b979c44b1182238853beefcff2fd0257999b36ae5cadc47aa01cd1");
  System.out.println("Trapdoor actual material coordinates PASS profiles="+profiles+" group/cover/large coordinates/raw layout and tile flag/door/aspect");
 }
}

package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.render.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Actual released tile/group resolution, block bounds/collision and block/offset rays. */
public final class TrapdoorConfiguredLegacyOracle {
 private static final BlockPos ORIGIN=new BlockPos(16,64,-16);
 private static final double[] POSES={0,.125,.5,.875,1};
 private static JsonArray numbers(double... values){JsonArray out=new JsonArray();for(double value:values)out.add(value);return out;}
 private static JsonArray corners(double[][] values){JsonArray out=new JsonArray();for(double[] value:values)out.add(numbers(value));return out;}
 private static JsonArray box(AxisAlignedBB b){return numbers(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
 private static JsonElement hit(RayTraceResult h){if(h==null)return JsonNull.INSTANCE;JsonObject out=new JsonObject();out.add("point",numbers(h.hitVec.x,h.hitVec.y,h.hitVec.z));out.addProperty("face",h.sideHit.getIndex());return out;}
 private static NBTTagCompound tag(BlockPos pos,int mode,boolean diagonal,EnumFacing face,boolean inverted,int flags,List<BlockPos> members){NBTTagCompound n=new NBTTagCompound();n.setInteger("x",pos.getX());n.setInteger("y",pos.getY());n.setInteger("z",pos.getZ());n.setInteger("housingTexture",ScreenHousingTextures.DEFAULT_TRAPDOOR);n.setInteger("TrapdoorPosition",mode);n.setBoolean("TrapdoorSliding",(flags&1)!=0);n.setBoolean("TrapdoorCover",!diagonal&&(flags&2)!=0);n.setBoolean("TrapdoorSlideOverSurface",!diagonal&&(flags&4)!=0);n.setBoolean("TrapdoorSlideIntoWall",diagonal&&(flags&4)!=0);n.setInteger("TrapdoorCoverFacing",face.getHorizontalIndex());n.setBoolean("DiagonalReverse",(flags&8)!=0);n.setBoolean("DiagonalRotationFlipped",(flags&16)!=0);n.setInteger("DiagonalFacing",face.getHorizontalIndex());n.setBoolean("DiagonalInverted",inverted);n.setInteger("DiagonalHingeSide",flags%3);n.setInteger("DiagonalGroupFacing",face.getHorizontalIndex());NBTTagList list=new NBTTagList();for(BlockPos member:members)list.appendTag(new NBTTagLong(member.toLong()));n.setTag("TrapdoorAssembly",list);n.setDouble("TrapdoorHinge",(flags&16)==0?-.375:1.9375);n.setDouble("TrapdoorTravel",2.9375);return n;}
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();BlockProgrammableTrapdoor regular=new BlockProgrammableTrapdoor();BlockProgrammableDiagonalTrapdoor diagonalBlock=new BlockProgrammableDiagonalTrapdoor();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;long vertices=0;setup(file);
  try(PrintWriter out=new PrintWriter(file,"UTF-8")){
   for(boolean diagonal:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(EnumFacing face:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.EAST})for(int flags:new int[]{0,1,2,3,4,5,24,19}){
    boolean inverted=diagonal&&(flags&2)!=0,cover=!diagonal&&(flags&2)!=0,assembly=(flags&8)!=0&&!cover;BlockPos origin=(flags&16)==0?ORIGIN:new BlockPos(29999980,64,-29999980);BlockProgrammableTrapdoor block=diagonal?diagonalBlock:regular;NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);List<BlockPos> members=new ArrayList<>();members.add(origin);if(assembly){EnumFacing width=diagonal?face.rotateYCCW():EnumFacing.EAST,along=diagonal?(mode==2?face.getOpposite():EnumFacing.UP):EnumFacing.SOUTH;members.add(origin.offset(width));members.add(origin.offset(along));members.add(origin.offset(width).offset(along));}
    for(int i=0;i<members.size();i++){BlockPos at=members.get(i);EnumFacing peerFace=diagonal&&i>=2&&(flags&16)!=0?face.getOpposite():face;boolean peerInverted=diagonal&&i>=2&&(flags&16)!=0?!inverted:inverted;IBlockState state=block.getDefaultState().withProperty(BlockTrapDoor.FACING,peerFace).withProperty(BlockTrapDoor.HALF,(diagonal?peerInverted:mode==2)?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM);world.setBlockState(at,state,2);TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)world.getTileEntity(at);tile.readFromNBT(tag(at,mode,diagonal,peerFace,peerInverted,flags,assembly?members:Collections.emptyList()));}
    TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)world.getTileEntity(origin);if(cover&&(flags&16)!=0){BlockPos opposite=origin.offset(face,3);world.setBlockState(opposite,regular.getDefaultState().withProperty(BlockTrapDoor.FACING,face.getOpposite()),2);((TileEntityProgrammableTrapdoor)world.getTileEntity(opposite)).readFromNBT(tag(opposite,mode,false,face.getOpposite(),false,flags,Collections.emptyList()));}
    List<TileEntityProgrammableTrapdoor> group=tile.group();if(group.size()!=(assembly?4:1))throw new AssertionError("Actual group mismatch");IBlockState state=world.getBlockState(origin);
    for(int material=0;material<CHOICES.length;material++)for(int layout=0;layout<3;layout++)for(boolean rawTile:new boolean[]{false,true}){
     for(TileEntityProgrammableTrapdoor leaf:group){IBlockState peerState=world.getBlockState(leaf.getPos());NBTTagCompound saved=tag(leaf.getPos(),mode,diagonal,peerState.getValue(BlockTrapDoor.FACING),peerState.getValue(BlockTrapDoor.HALF)==BlockTrapDoor.DoorHalf.TOP,flags,assembly?members:Collections.emptyList());saved.setInteger("housingTexture",CHOICES[material]);saved.setInteger("TrapdoorTextureLayout",layout);saved.setBoolean("TrapdoorTileTexture",rawTile);leaf.readFromNBT(saved);}
     boolean tall=diagonal&&mode!=2,widthX=face.getAxis()==(diagonal?EnumFacing.Axis.Z:EnumFacing.Axis.X);int ou=widthX?origin.getX():origin.getZ(),ov=tall?origin.getY():widthX?origin.getZ():origin.getX();double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=Double.NEGATIVE_INFINITY,maxV=maxU;
     for(TileEntityProgrammableTrapdoor leaf:group){double u=widthX?leaf.getPos().getX():leaf.getPos().getZ(),v=tall?leaf.getPos().getY():widthX?leaf.getPos().getZ():leaf.getPos().getX();minU=Math.min(minU,u);minV=Math.min(minV,v);maxU=Math.max(maxU,u+1);maxV=Math.max(maxV,v+1);}
     if(tile.isCover()){BlockPos at=origin.offset(face);double offset=tile.coverOffset();minU=(widthX?at.getX():at.getZ())+offset*(widthX?face.getFrontOffsetX():face.getFrontOffsetZ());maxU=minU+1;minV=(widthX?at.getZ():at.getX())+offset*(widthX?face.getFrontOffsetZ():face.getFrontOffsetX());maxV=minV+1;}
     int joins=(Integer)JOINS.invoke(null,tile,state,group);
     for(double progress:new double[]{0,.5,1}){
      BufferBuilder buffer=new BufferBuilder(65536);buffer.begin(7,BlockSurfaceFormat.get());TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(tile.getHousingTexture()));TEProgrammableTrapdoor.drawConfiguredLeaf(buffer,sprite,tile,state,progress,0xD00070);buffer.finishDrawing();
      JsonObject row=new JsonObject();row.addProperty("diagonal",diagonal);row.addProperty("position",mode);row.addProperty("turns",BlockProgrammableTrapdoor.quarterTurns(face));row.addProperty("inverted",inverted);row.addProperty("sliding",tile.isSliding());row.addProperty("cover",tile.isCover());row.addProperty("surface",tile.isSlideOverSurface());row.addProperty("intoWall",tile.isSlideIntoWall());row.addProperty("hinge",tile.motionHinge());row.addProperty("travel",tile.motionTravel());row.addProperty("overhang",tile.coverOverhang());row.addProperty("reverse",diagonal&&((TileEntityProgrammableDiagonalTrapdoor)tile).rotationReverse(group));row.addProperty("closedReverse",diagonal&&((TileEntityProgrammableDiagonalTrapdoor)tile).rotationReverse(Collections.emptyList()));row.addProperty("lift",diagonal?((TileEntityProgrammableDiagonalTrapdoor)tile).slideLiftDirection(group):1);row.add("origin",numbers(origin.getX(),origin.getY(),origin.getZ()));row.add("artwork",numbers(minU-ou,minV-ov,maxU-ou,maxV-ov));row.addProperty("material",material);row.addProperty("choice",CHOICES[material]);row.addProperty("layout",layout);row.addProperty("rawTile",rawTile);row.addProperty("joins",joins);row.addProperty("progress",progress);JsonArray data=new JsonArray();for(int i=0;i<buffer.getVertexCount()*32;i+=4)data.add(buffer.getByteBuffer().getInt(i));row.add("vertices",data);out.println(gson.toJson(row));profiles++;vertices+=buffer.getVertexCount();
     }
    }
   }
  }
  SegmentedReferenceGolden.check(file,"e83ba1a2c779e83947f4f26cc6770bd7aae5c792b8c41817d9435915138fa3fb");
  System.out.println("Trapdoor actual configured emitter PASS profiles="+profiles+" vertices="+vertices+" full regular/diagonal pipeline/groupUV/frame joins/custom Door/material branches");
 }
 private static final int[] CHOICES=new int[6];private static Method JOINS;
 private static TextureAtlasSprite sprite(String name,int x,int y,int height){TextureAtlasSprite sprite=new TextureAtlasSprite(name){};sprite.setIconWidth(16);sprite.setIconHeight(height);sprite.initSprite(256,256,x,y,false);return sprite;}
 private static void setup(File output)throws Exception{
  TrapdoorBorderLegacyOracle.main(new String[]{new File(output.getParentFile(),"trapdoor-configured-border-helper.jsonl").getAbsolutePath()});
  CHOICES[0]=ScreenHousingTextures.DEFAULT_TRAPDOOR;CHOICES[1]=-1;CHOICES[2]=ScreenHousingTextures.choiceAt(ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1"));CHOICES[3]=-1;CHOICES[4]=MaterialChoicePolicy.CUSTOM_BASE+7;CHOICES[5]=-1;
  for(int i=0;i<ScreenHousingTextures.BUILTIN_COUNT;i++){int choice=ScreenHousingTextures.choiceAt(i);if(CHOICES[5]<0&&FrameAppearance.valid(choice)&&!FrameAppearance.border(choice))CHOICES[5]=choice;if(CHOICES[1]<0&&ScreenHousingTextures.isDoor(choice))CHOICES[1]=choice;if(CHOICES[3]<0&&FrameAppearance.valid(choice)&&FrameAppearance.border(choice)&&FrameAppearance.aspect(choice)==2)CHOICES[3]=choice;}if(CHOICES[1]<0||CHOICES[3]<0||CHOICES[5]<0)throw new AssertionError("Source authored materials");
  final TextureAtlasSprite standard=sprite("oracle/default",32,64,16),door=sprite("oracle/door",64,64,32),frame=sprite("oracle/frame",96,64,16),half=sprite("oracle/half_frame",112,64,16),edge=sprite("oracle/edge",48,80,16),solid=sprite("oracle/solid_frame",192,64,16),lower=sprite("oracle/custom_lower",128,16,16),upper=sprite("oracle/custom_upper",160,16,16);final Map<String,TextureAtlasSprite> sprites=new HashMap<>();sprites.put(ScreenHousingTextures.fullTexture(CHOICES[0]),standard);sprites.put(ScreenHousingTextures.fullTexture(CHOICES[1]),door);sprites.put(ScreenHousingTextures.fullTexture(CHOICES[2]),frame);sprites.put(ScreenHousingTextures.fullTexture(CHOICES[3]),half);sprites.put(ScreenHousingTextures.fullTexture(CHOICES[5]),solid);sprites.put("vandorlabs:blocks/programmable_glass/metal_side",edge);sprites.put(lower.getIconName(),lower);
  TextureMap atlas=new TextureMap(""){@Override public TextureAtlasSprite getAtlasSprite(String name){TextureAtlasSprite value=sprites.get(name);if(value==null)throw new AssertionError("Unfrozen sprite "+name);return value;}};for(Field f:Minecraft.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&f.getType()==TextureMap.class){f.setAccessible(true);f.set(Minecraft.getMinecraft(),atlas);}
  for(String name:new String[]{"LOWER","UPPER"}){Field f=SegmentedPanelLegacyChecks.class.getDeclaredField(name);f.setAccessible(true);f.set(null,name.equals("LOWER")?lower:upper);}Field states=CustomBlockTextures.class.getDeclaredField("STATES");states.setAccessible(true);((Map<Integer,IBlockState>)states.get(null)).put(CHOICES[4],new SegmentedPanelLegacyChecks.Door().getDefaultState());Field loaded=CustomBlockTextures.class.getDeclaredField("loaded");loaded.setAccessible(true);loaded.setBoolean(null,true);
  com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy(){@Override public boolean customDoor(int choice){return CustomBlockTextures.isDoor(choice);}@Override public String customTexture(int choice){return CustomBlockTextures.texture(choice);}};
  JOINS=TEProgrammableTrapdoor.class.getDeclaredMethod("frameJoins",TileEntityProgrammableTrapdoor.class,IBlockState.class,List.class);JOINS.setAccessible(true);
 }
}

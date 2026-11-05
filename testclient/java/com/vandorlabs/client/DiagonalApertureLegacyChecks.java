package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.nio.file.*;
import java.util.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Captures actual current aperture groups and direct contacts, independently of native discovery. */
public final class DiagonalApertureLegacyChecks {
 static int scenes,queries,solid,flips;static java.security.MessageDigest digest;
 static void hash(long n){for(int i=56;i>=0;i-=8)digest.update((byte)(n>>>i));}
 static void output(DiagonalLayoutLegacyChecks.Scene scene,String name,List<BlockPos> selected,java.io.PrintWriter writer)throws Exception{
  JsonObject record=new JsonObject();record.addProperty("profile",name);record.addProperty("chunkLimited",scene.world.chunkLimit);JsonArray cells=new JsonArray(),outputs=new JsonArray();
  for(BlockPos pos:scene.cells){boolean loaded=scene.world.isBlockLoaded(pos),limit=scene.world.chunkLimit;scene.world.chunkLimit=false;net.minecraft.block.state.IBlockState state=scene.world.getBlockState(pos);net.minecraft.tileentity.TileEntity raw=scene.world.getTileEntity(pos);if(!(raw instanceof TileEntityAnimatedScreenSelector)){scene.world.chunkLimit=limit;continue;}TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)raw;JsonObject in=new JsonObject();in.add("pos",DiagonalLayoutLegacyChecks.xyz(pos));in.addProperty("facing",state.getValue(BlockProgrammableWall.FACING).getIndex());in.addProperty("inverted",state.getValue(BlockProgrammableWall.INVERTED));in.addProperty("half",tile.isDiagonalHalfHeight());in.addProperty("full",tile.isDiagonalFullWidth());in.addProperty("join",tile.isJoinPortholes());in.addProperty("shape",tile.getPortholeShape());in.addProperty("diagonalJoin",tile.getDiagonalJoin());in.addProperty("art",tile.getHousingTexture());in.addProperty("fill",tile.getDiagonalFill());in.addProperty("loaded",loaded);cells.add(in);scene.world.chunkLimit=limit;}
  java.lang.reflect.Method adjacent=TEAnimatedScreenSelector.class.getDeclaredMethod("adjacentPorthole",TileEntityAnimatedScreenSelector.class,net.minecraft.block.state.IBlockState.class,EnumFacing.class);adjacent.setAccessible(true);
  for(BlockPos pos:selected){if(!scene.world.isBlockLoaded(pos)||scene.world.getTileEntity(pos)==null)continue;TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)scene.world.getTileEntity(pos);net.minecraft.block.state.IBlockState state=scene.world.getBlockState(pos);boolean filled=TEAnimatedScreenSelector.solidDiagonalPortholeJunction(tile,state);TEAnimatedScreenSelector.PortholeGroup group=TEAnimatedScreenSelector.portholeGroup(tile,state);EnumFacing facing=state.getValue(BlockProgrammableWall.FACING),right=facing.rotateY(),up=tile.isDiagonalHalfHeight()?facing.getOpposite():EnumFacing.UP;
   int column=PanelPlane.axis(pos,group.right)-group.minAxis,row=PanelPlane.axis(pos,group.up)-group.minY,mask=0;EnumFacing[] directions={right.getOpposite(),right,up.getOpposite(),up};for(int i=0;i<4;i++)if((Boolean)adjacent.invoke(null,tile,state,directions[i]))mask|=1<<i;
   JsonObject out=new JsonObject();out.add("pos",DiagonalLayoutLegacyChecks.xyz(pos));JsonArray layout=new JsonArray();long[] values={column,row,group.columns,group.rows,right!=group.right?1:0,up!=group.up?1:0};for(long n:values){layout.add(n);hash(n);}out.add("layout",layout);out.addProperty("mask",mask);out.addProperty("solid",filled);outputs.add(out);hash(pos.toLong());hash(mask);hash(filled?1:0);queries++;if(filled)solid++;if(right!=group.right||up!=group.up)flips++;
  }
  record.add("cells",cells);record.add("queries",outputs);String json=new Gson().toJson(record);digest.update(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));writer.println(json);scenes++;
 }
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new net.minecraft.util.ResourceLocation("vandorlabs","diagonal_aperture_fixture"));DiagonalLayoutLegacyChecks.PORT=new BlockProgrammableWall("diagonal_aperture_fixture",BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE);digest=java.security.MessageDigest.getInstance("SHA-256");Path file=Paths.get(System.getProperty("vandorlabs.diagonalApertureExport","build/test-artifacts/diagonal-aperture-forge.jsonl"));Files.createDirectories(file.toAbsolutePath().getParent());
  try(java.io.PrintWriter writer=new java.io.PrintWriter(Files.newBufferedWriter(file))){
   for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int profile=0;profile<10;profile++){
    DiagonalLayoutLegacyChecks.Scene scene=new DiagonalLayoutLegacyChecks.Scene();BlockPos origin=profile==9?new BlockPos(14,80,14):new BlockPos(-7,80,-11),base=origin,changed=null;
    for(int y=0;y<3;y++){if(y>0)base=DiagonalLayoutLegacyChecks.row(scene,base,mode,profile==1,profile==2);net.minecraft.block.state.IBlockState prior=scene.world.getBlockState(base);EnumFacing f=y==0?face:prior.getValue(BlockProgrammableWall.FACING);boolean inv=y==0?inverted:prior.getValue(BlockProgrammableWall.INVERTED);for(int x=0;x<3;x++){BlockPos p=base.offset(face.rotateY(),x);DiagonalLayoutLegacyChecks.cell(scene,p,DiagonalLayoutLegacyChecks.PORT,f,inv,mode,0,0,1);if(x==1&&y==1)changed=p;}}
    TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)scene.world.getTileEntity(changed);if(profile==3)scene.world.setBlockState(changed,Blocks.AIR.getDefaultState(),2);if(profile>=4&&profile<=8){net.minecraft.nbt.NBTTagCompound tag=tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound());if(profile==4)tag.setBoolean("JoinPortholes",false);if(profile==5)tag.setInteger("PortholeShape",1);if(profile==6){tag.setBoolean("DiagonalHalfHeight",true);tag.setBoolean("DiagonalFullWidth",!tile.isDiagonalFullWidth());}if(profile==7){tag.setInteger("housingTexture",4);tag.setInteger("DiagonalFill",3);}if(profile==8)tag.setInteger("DiagonalJoin",7);tile.readFromNBT(tag);}if(profile==9)scene.world.chunkLimit=true;
    output(scene,"matrix/"+face+"/"+inverted+"/"+mode+"/"+profile,new ArrayList<>(scene.cells),writer);
   }
   for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int variant=0;variant<8;variant++)for(int override:new int[]{0,2}){
    DiagonalLayoutLegacyChecks.Scene scene=new DiagonalLayoutLegacyChecks.Scene();BlockPos p=new BlockPos(8,88,8);EnumFacing left=face.rotateYCCW(),right=face.rotateY();int hands=variant/2;
    DiagonalLayoutLegacyChecks.cell(scene,p,DiagonalLayoutLegacyChecks.PORT,face,inverted,1,0,0,1);
    DiagonalLayoutLegacyChecks.cell(scene,p.offset(face),DiagonalLayoutLegacyChecks.PORT,(hands&1)==0?left:right,inverted,1,0,0,1);
    DiagonalLayoutLegacyChecks.cell(scene,p.offset(face.getOpposite()),DiagonalLayoutLegacyChecks.PORT,(hands&2)==0?left:right,inverted,1,0,0,1);
    DiagonalLayoutLegacyChecks.cell(scene,p.offset((variant&1)==0?left:right),DiagonalLayoutLegacyChecks.PORT,face,inverted,1,0,0,1);
    ((TileEntityAnimatedScreenSelector)scene.world.getTileEntity(p)).setDiagonalJoin(override);
    output(scene,"cross/"+face+"/"+inverted+"/"+variant+"/"+override,new ArrayList<>(scene.cells),writer);
   }
   for(int mode=0;mode<3;mode++)for(int size:new int[]{4095,4096,4097}){DiagonalLayoutLegacyChecks.Scene scene=new DiagonalLayoutLegacyChecks.Scene();BlockPos start=new BlockPos(-2048,80,-32);for(int x=0;x<size;x++)DiagonalLayoutLegacyChecks.cell(scene,start.east(x),DiagonalLayoutLegacyChecks.PORT,EnumFacing.NORTH,false,mode,0,0,1);output(scene,"cap/"+mode+"/"+size,Arrays.asList(start,start.east(size/2),start.east(size-1)),writer);}
  }
  StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));if(!hash.toString().equals("4d2798a3fc2abd0b089ae51ced87d5713abcc1fd102f2ddc77acbe78335462d7"))throw new AssertionError("Actual aperture reference changed "+hash);if(solid!=32||flips!=313||scenes!=377||queries!=2619)throw new AssertionError("Incomplete aperture baseline");System.out.println("Actual Forge diagonal aperture PASS scenes="+scenes+" queries="+queries+" solid="+solid+" flips="+flips+" golden="+hash);
 }
}

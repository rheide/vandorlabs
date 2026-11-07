package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.ramp.ControllerPlatform;
import com.vandorlabs.ramp.RampGeometry;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.AABB;

/** Loaded-only source selection. This value does not authorize mutation or replace durable preparation. */
public final class NativeRampCapture {
 public record Source(BlockPos position,BlockState state,List<AABB> shapes){public Source{position=position.immutable();shapes=List.copyOf(shapes);}}
 public record Plan(List<Source> sources,Direction facing,int minimumAlong,int length,int sourceY,double low,double high){public Plan{sources=List.copyOf(sources);}}
 public record Result(Plan plan,String error){public boolean accepted(){return plan!=null;}}
 private static final String MATERIAL="Front block must be stairs, a slab, full glass, Programmable Block, or an ordinary solid block";
 private static Result refused(String error){return new Result(null,error);}
 private static boolean loaded(ServerLevel level,BlockPos pos){return !level.isOutsideBuildHeight(pos)&&level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
 private static net.minecraft.world.level.block.entity.BlockEntity tile(ServerLevel level,BlockPos pos){return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4).getBlockEntities().get(pos);}
 private static BlockState state(ServerLevel level,BlockPos pos){return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4).getBlockState(pos);}
 private static boolean programmable(BlockState state){return state.is(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get())||state.is(VandorLabsBootstrap.PROGRAMMABLE_SLAB.get())||state.is(VandorLabsBootstrap.PROGRAMMABLE_STAIRS.get());}
 /** Source stores STRAIGHT; its derived stair join is captured separately, not a material discriminator. */
 private static BlockState materialState(BlockState state){return state.getBlock() instanceof StairBlock?state.setValue(StairBlock.SHAPE,StairsShape.STRAIGHT):state;}
 private static boolean same(ServerLevel level,BlockPos seed,BlockPos candidate,BlockState material,boolean textures){
  if(!materialState(state(level,candidate)).equals(materialState(material)))return false;
  if(!textures||!programmable(material))return true;
  if(!(tile(level,seed) instanceof ProgrammableBlockEntity a)||!(tile(level,candidate) instanceof ProgrammableBlockEntity b))return false;
  var x=a.settings();var y=b.settings();if(x.layout!=y.layout)return false;
  if(material.getBlock() instanceof NativeProgrammableStairs&&(!(a instanceof ProgrammableShapeEntity sa)||!(b instanceof ProgrammableShapeEntity sb)||sa.appearance().sideTexture()!=sb.appearance().sideTexture()))return false;
  for(int i=0;i<6;i++)if((x.facesEnabled&&x.face(i)>=0?x.face(i):x.mainTexture)!=(y.facesEnabled&&y.face(i)>=0?y.face(i):y.mainTexture))return false;
  return true;
 }
 public static Result select(NativeRampEntity owner,ServerPlayer actor){
  if(!(owner.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread()||owner.inert()||owner.isRemoved()||!loaded(level,owner.getBlockPos())||tile(level,owner.getBlockPos())!=owner)return refused("Ramp controller is unavailable");
  if(actor==null||actor.level()!=level||!actor.isAlive()||actor.isSpectator())return refused("Ramp requires an authorized actor");
  var root=owner.getBlockPos();var seed=root.relative(owner.scanFacing());if(!loaded(level,seed))return refused("Load all platform chunks first");
  var material=state(level,seed);var block=material.getBlock();boolean known=programmable(material);
  // Dynamic physics sources require their own capture lifecycle; ordinary solidity alone is insufficient.
  if(block instanceof FallingBlock||block instanceof TntBlock)return refused("Dynamic source blocks are not supported");
  if(material.getDestroySpeed(level,seed)<0||material.hasBlockEntity()&&!known||!known&&!(block instanceof SlabBlock)&&!(block instanceof StairBlock)&&!(block==Blocks.GLASS||block instanceof StainedGlassBlock)&&!material.isCollisionShapeFullBlock(level,seed))return refused(MATERIAL);
  var settings=owner.settings();var facing=Direction.from3DDataValue(settings.resolvedDirection(owner.horizontalFacing().get3DDataValue()));
  var direction=RampGeometry.Direction.valueOf(facing.name());boolean[] cold={false};
  java.util.function.Predicate<ControllerPlatform.Cell> match=c->{var pos=new BlockPos(c.x,c.y,c.z);if(!loaded(level,pos)){cold[0]=true;return false;}return same(level,seed,pos,material,settings.matchTextures());};
  var first=new ControllerPlatform.Cell(seed.getX(),seed.getY(),seed.getZ());
  var selected=settings.moat()?ControllerPlatform.discoverVertical(first,direction,match):settings.verticalLayout()?ControllerPlatform.discoverPanel(first,direction,match):ControllerPlatform.discover(first,direction,match);
  if(cold[0])return refused("Load all platform chunks first");if(selected.isEmpty())return refused("No platform blocks found");
  int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE,minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;
  var sources=new ArrayList<Source>(selected.size());double low=0,high=1;
  for(var cell:selected){var pos=new BlockPos(cell.x,cell.y,cell.z);var current=state(level,pos);
   if(NativeRampHeldOwners.blocked(level,pos)||!level.mayInteract(actor,pos)||!actor.mayUseItemAt(pos,owner.scanFacing(),actor.getMainHandItem()))return refused("You cannot modify the platform here");
   if(known&&(!(tile(level,pos) instanceof ProgrammableBlockEntity)||block instanceof NativeProgrammableStairs&&!(tile(level,pos) instanceof ProgrammableShapeEntity)))return refused("Programmable source tile is missing");
   List<AABB> boxes;
   if(block instanceof NativeProgrammableStairs){var shape=(ProgrammableShapeEntity)tile(level,pos);boxes=NativeProgrammableStairs.solidBoxes(current,shape.stairState().packed());}
   else if(block instanceof StairBlock)boxes=NativeRampSourceShapes.vanillaStairs(current.getValue(StairBlock.FACING),current.getValue(StairBlock.HALF)==Half.TOP,current.getValue(StairBlock.SHAPE));
   else if(block instanceof NativeProgrammableSlab)boxes=NativeProgrammableSlab.solidBoxes(current);
   else if(block instanceof SlabBlock){var half=current.getValue(SlabBlock.TYPE);boxes=List.of(new AABB(0,half==SlabType.TOP?.5:0,0,1,half==SlabType.BOTTOM?.5:1,1));}
   else boxes=List.of(new AABB(0,0,0,1,1,1));
   if(pos.equals(seed)){low=boxes.stream().mapToDouble(b->b.minY).min().orElse(0);high=boxes.stream().mapToDouble(b->b.maxY).max().orElse(1);}
   sources.add(new Source(pos,current,boxes));int along=settings.verticalLayout()&&!settings.moat()?cell.y:cell.x*facing.getStepX()+cell.z*facing.getStepZ();min=Math.min(min,along);max=Math.max(max,along);minY=Math.min(minY,cell.y);maxY=Math.max(maxY,cell.y);minX=Math.min(minX,cell.x);maxX=Math.max(maxX,cell.x);minZ=Math.min(minZ,cell.z);maxZ=Math.max(maxZ,cell.z);
  }
  if(settings.escalator()!=0){int span=settings.verticalLayout()?maxY-minY+1:facing.getAxis()==Direction.Axis.X?maxX-minX+1:maxZ-minZ+1;int area=settings.verticalLayout()?span*(facing.getAxis()==Direction.Axis.X?maxZ-minZ+1:maxX-minX+1):(maxX-minX+1)*(maxZ-minZ+1);if(area!=sources.size()||span*16<=settings.treadPixels())return refused(settings.verticalLayout()?"Escalators need a rectangular source panel":"Escalators need a rectangular platform with at least two treads");}
  return new Result(new Plan(sources,facing,min,settings.moat()?maxY-minY+1:max-min+1,settings.verticalLayout()&&!settings.moat()?min:minY,low,high),"");
 }
 private NativeRampCapture(){}
}

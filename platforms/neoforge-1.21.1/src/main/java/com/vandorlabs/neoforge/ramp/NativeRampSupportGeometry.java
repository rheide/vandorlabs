package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.*;

/** World-free proof over the same authored emitter used for installed collision. */
final class NativeRampSupportGeometry {
 static boolean supported(NativeRampAssembly assembly,double pose,double distance,AABB body){
  int x0=Mth.floor(body.minX),x1=Mth.floor(body.maxX-1e-7),z0=Mth.floor(body.minZ),z1=Mth.floor(body.maxZ-1e-7),y1=Mth.floor(body.minY);long cells=2L*(x1-x0+1)*(z1-z0+1);if(cells<1||cells>32)throw new IllegalArgumentException("Ramp support footprint query bound");var settings=assembly.settings();var motion=new NativeRampPartData.Motion(0,0,pose,pose,1,false,pose);var belt=settings.escalator()==0?NativeRampPartData.Belt.NONE:new NativeRampPartData.Belt(settings.escalator(),settings.speed(),distance,0,false,distance);long pieces=0;
  for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)for(int y=y1-1;y<=y1;y++){var cell=new BlockPos(x,y,z);if(!assembly.potential().contains(cell))continue;var data=assembly.part(cell,motion,belt);var geometry=com.vandorlabs.ramp.RampBeltPolicy.deployed(belt.mode(),false,pose)?NativeRampBeltGeometry.snapshot(data.spec(),distance).geometry():NativeRampGeometry.snapshot(data.spec(),pose);
   for(var piece:geometry.pieces()){if(++pieces>131072)throw new IllegalArgumentException("Ramp support emitted-piece budget");var box=piece.box().move(cell);if(box.maxX>body.minX+1e-7&&box.minX<body.maxX-1e-7&&box.maxZ>body.minZ+1e-7&&box.minZ<body.maxZ-1e-7&&Math.abs(body.minY-box.maxY)<1e-6)return true;}
  }return false;
 }
 static Vec3 rebase(NativeRampAssembly assembly,NativeRampSupportStamp stamp,double pose,double distance){
  if(!supported(assembly,stamp.pose,stamp.distance,stamp.body))throw new IllegalArgumentException("Saved Ramp support does not match its authenticated recipe");
  Vec3 delta=Vec3.ZERO;boolean samePose=Double.compare(stamp.pose,pose)==0,sameDistance=Double.compare(stamp.distance,distance)==0;
  if(assembly.settings().escalator()!=0&&(!samePose||!sameDistance))throw new IllegalArgumentException("Belt support phase adoption is not yet proven");
  if(!samePose){var candidate=new NativeRampRiderTargets.Candidate(0,stamp.body,stamp.type.equals("minecraft:player"),true,0,true);var selected=NativeRampRiderTargets.select(assembly.footprint(),assembly.original().getBlock() instanceof StairBlock,stamp.pose,pose,false,false,List.of(candidate));if(selected==null)throw new IllegalArgumentException("Saved Ramp support cannot be mapped safely");var moved=selected.get(candidate);if(moved!=null)delta=moved;else if(!assembly.footprint().extend()||assembly.footprint().moat())throw new IllegalArgumentException("Saved actor has no authored Ramp transport");}
  if(!supported(assembly,pose,distance,stamp.body.move(delta)))throw new IllegalArgumentException("Adopted Ramp pose does not support the mapped actor");return delta;
 }
 private NativeRampSupportGeometry(){}
}

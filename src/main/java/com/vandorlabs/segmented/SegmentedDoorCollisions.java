package com.vandorlabs.segmented;

import java.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Moved panels remain physical beyond vanilla's scan of their stationary block cells. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class SegmentedDoorCollisions {
 private static final com.google.common.cache.Cache<SegmentedDoorTile,Column> CACHE=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumWeight(8*1024*1024).weigher((SegmentedDoorTile root,Column value)->512+128*value.boxes.size()).build();
 private static long builds;
 private static final class Column {
  final int x,y,z,height,width,column,direction,pixels,position,rotation;final boolean straight;final double progress;final List<AxisAlignedBB> boxes;final AxisAlignedBB bounds;
  Column(SegmentedDoorTile root,double progress){x=root.getPos().getX();y=root.getPos().getY();z=root.getPos().getZ();height=root.height;width=root.columns;column=root.column;direction=root.settings.direction;pixels=root.settings.treadPixels;position=root.settings.position;rotation=((int)(180-root.facing().getHorizontalAngle())/90)&3;straight=root.settings.straight;this.progress=progress;SegmentedPanelCollision.Builder builder=new SegmentedPanelCollision.Builder();for(int row=0;row<height;row++)SegmentedPanelCollision.append(builder,x,y,z,rotation,row,height,column,width,progress,direction,straight,pixels,root.settings.depthOffset());List<AxisAlignedBB> prepared=new ArrayList<>();AxisAlignedBB total=null;for(double[] b:builder.boxes()){AxisAlignedBB box=new AxisAlignedBB(b[0],b[1],b[2],b[3],b[4],b[5]);prepared.add(box);total=total==null?box:total.union(box);}boxes=Collections.unmodifiableList(prepared);bounds=total;builds++;}
  boolean matches(SegmentedDoorTile root,double p){return progress==p&&x==root.getPos().getX()&&y==root.getPos().getY()&&z==root.getPos().getZ()&&height==root.height&&width==root.columns&&column==root.column&&direction==root.settings.direction&&pixels==root.settings.treadPixels&&position==root.settings.position&&straight==root.settings.straight&&rotation==(((int)(180-root.facing().getHorizontalAngle())/90)&3);}
 }
 public static long builds(){return builds;}
 public static void remove(SegmentedDoorTile tile){CACHE.invalidate(tile);}
 @SubscribeEvent public static void collisions(GetCollisionBoxesEvent event){
  AxisAlignedBB query=event.getAabb();for(SegmentedDoorTile root:SegmentedDoorInteractions.INDEX.query(event.getWorld(),query.grow(34).expand(0,32,0),tile->tile.isRoot()&&tile.ready()&&(tile.targetOpen||tile.pose(0)>0))){double progress=root.pose(0);Column cached=CACHE.getIfPresent(root);if(cached==null||!cached.matches(root,progress)){cached=new Column(root,progress);CACHE.put(root,cached);}if(cached.bounds!=null&&cached.bounds.intersects(query))for(AxisAlignedBB box:cached.boxes)if(box.intersects(query))event.getCollisionBoxesList().add(box);}
 }
 @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){CACHE.asMap().keySet().removeIf(tile->tile.getWorld()==event.getWorld());}
 private SegmentedDoorCollisions(){}
}

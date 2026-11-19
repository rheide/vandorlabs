package com.vandorlabs.neoforge.navigation.observer;
import java.util.*;import com.vandorlabs.redstone.ChannelList;import com.vandorlabs.navigation.NavigationRange;import com.vandorlabs.neoforge.world.NativeLoadedTileIndex;import com.vandorlabs.neoforge.gear.*;import net.minecraft.core.BlockPos;import net.minecraft.world.level.Level;import net.minecraft.world.phys.*;
/** Source discovery only visits already installed tiles and captured immutable payloads. */
public final class NativeNavigationSources {
 public record Area(int x,int z,int radius,int radarRadius,NativeObserverEntity stationary,boolean sentinel){}
 private static final NativeLoadedTileIndex<NativeObserverEntity> SOURCES=new NativeLoadedTileIndex<>(NativeObserverEntity.class);
 public static void add(NativeObserverEntity t){SOURCES.add(t);}public static void remove(NativeObserverEntity t){SOURCES.remove(t);}
 public static List<Area> matching(Level world,BlockPos display,ChannelList selected){return matching(world,display,selected,true);}
 public static boolean noPower(Level world,BlockPos display,ChannelList selected){return !matching(world,display,selected,false).isEmpty()&&matching(world,display,selected,true).isEmpty();}
 private static List<Area> matching(Level world,BlockPos display,ChannelList selected,boolean powered){if(selected==null||selected.isEmpty())return List.of();var result=new ArrayList<Area>();var bounds=new AABB(display).inflate(256);SOURCES.visit(world,bounds,t->!t.inert()&&t.formed()&&!t.ownsRecovery()&&t.powered()==powered,t->{if(link(t.channels(),selected,t.center(),display,t.range())){int radar=NavigationRange.radarRadius(t.design().id());result.add(area(t.center(),t.radius(),t.radar()?radar:0,t,radar==NavigationRange.SENTINEL_RADAR_RADIUS));}});
 if(!powered)NativeObserverPayloadSources.visit(world,bounds,(position,radius,channels,range,radarRadius,sentinel)->{if(link(channels,selected,position,display,range))result.add(area(position,radius,radarRadius,null,sentinel));});return List.copyOf(new LinkedHashSet<>(result));}
 private static boolean link(ChannelList source,ChannelList selected,Vec3 center,BlockPos display,int range){return !source.intersect(selected).isEmpty()&&center.distanceToSqr(new Vec3(display.getX()+.5,display.getY(),display.getZ()+.5))<=range*(double)range;}
 private static Area area(Vec3 center,int radius,int radarRadius,NativeObserverEntity stationary,boolean sentinel){return new Area((int)Math.floor(center.x),(int)Math.floor(center.z),radius,radarRadius,stationary,sentinel);}
 private NativeNavigationSources(){}
}
package com.vandorlabs.neoforge.gear;

import net.minecraft.core.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;

/** The exact installed root stays authoritative; proximity follows actual mechanism parts beyond source cells. */
public final class NativeGearAccess {
    public static boolean menu(Player actor,NativeGearEntity root){return menu(actor,root,null);}
    /** Keep an existing configuration session through its actor's exact transfer hold; admission still uses menu(). */
    static boolean session(Player actor,NativeGearEntity root){return menu(actor,root,NativeGearJournal.sessionHold(root,actor));}
    private static boolean menu(Player actor,NativeGearEntity root,java.util.UUID admitted){var world=root.getLevel();if(world==null||actor.level()!=world||!actor.isAlive()||actor.isSpectator()||root.inert()||!root.controller()||NativeGearFormation.at(world,root.getBlockPos())!=root||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blockedForBreak(world,root.getBlockPos(),admitted)||!world.mayInteract(actor,root.getBlockPos())||!actor.mayUseItemAt(root.getBlockPos(),Direction.UP,actor.getMainHandItem()))return false;var point=actor.position();for(var box:NativeGearGeometry.mechanismBoxes(root,root.progress())){double x=Math.max(Math.max(box.minX-point.x,0),point.x-box.maxX),y=Math.max(Math.max(box.minY-point.y,0),point.y-box.maxY),z=Math.max(Math.max(box.minZ-point.z,0),point.z-box.maxZ);if(x*x+y*y+z*z<=64)return true;}return false;}
    public static boolean rayLoaded(net.minecraft.world.level.Level world,Vec3 start,Vec3 end){for(double value:new double[]{start.x,start.y,start.z,end.x,end.y,end.z})if(!Double.isFinite(value))return false;int x0=((int)Math.floor(Math.min(start.x,end.x)))>>4,x1=((int)Math.floor(Math.max(start.x,end.x)))>>4,z0=((int)Math.floor(Math.min(start.z,end.z)))>>4,z1=((int)Math.floor(Math.max(start.z,end.z)))>>4;if((long)(x1-x0+1)*(z1-z0+1)>25)return false;for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(world.getChunkSource().getChunkNow(x,z)==null)return false;return true;}
    private NativeGearAccess(){}
}

package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One dedicated entry point for ordinary and Configurizer interactions. Inactive until bound. */
public final class NativeCanopyMenus {
    private NativeCanopyMenus(){}
    public static boolean usable(Player player,NativeCanopyEntity owner){
        var level=owner.getLevel();
        if(level instanceof ServerLevel server&&!server.getServer().isSameThread())return false;
        // Source selectionBounds computes current geometry. Prepare only the pending local publication.
        if(level instanceof ServerLevel&&owner.available()&&NativeCanopyLifecycle.installed(owner))NativeCanopyLifecycle.prepare(owner);
        if(level==null||player.level()!=level||!player.isAlive()||player.isSpectator()||!owner.available()
            ||!NativeCanopyLifecycle.installed(owner)||NativeCanopyLifecycle.owner(owner)!=owner
            ||!owner.publication().draws()||owner.publication().geometry()==null)return false;
        if(level instanceof ServerLevel server&&(!server.getServer().isSameThread()||!NativeRedstoneChannels.open(server)))return false;
        var pos=owner.getBlockPos();
        return distanceSq(player.getEyePosition(),owner.publication().geometry().bounds())<=64
            &&level.mayInteract(player,pos)&&player.mayUseItemAt(pos,net.minecraft.core.Direction.UP,player.getMainHandItem());
    }
    /** Distance to the closest point, preserving Source large/overhanging selection reach. */
    public static double distanceSq(Vec3 eye,AABB box){
        double x=Math.max(box.minX-eye.x,Math.max(0,eye.x-box.maxX));
        double y=Math.max(box.minY-eye.y,Math.max(0,eye.y-box.maxY));
        double z=Math.max(box.minZ-eye.z,Math.max(0,eye.z-box.maxZ));
        return x*x+y*y+z*z;
    }
    public static boolean open(Player player,Level level,BlockPos hit,MenuType<NativeCanopyMenu> type){
        if(type==null||!NativeConfigurationAccess.canConfigure(player))return false;
        if(level instanceof ServerLevel server&&!server.getServer().isSameThread())return false;
        var member=NativeCanopyLifecycle.at(level,hit);if(member==null)return false;
        var owner=NativeCanopyLifecycle.owner(member);if(!usable(player,owner))return false;
        if(player instanceof ServerPlayer serverPlayer)serverPlayer.openMenu(new SimpleMenuProvider(
            (id,inventory,ignored)->new NativeCanopyMenu(type,id,owner),owner.getBlockState().getBlock().getName()),
            buffer->buffer.writeBlockPos(owner.getBlockPos()));
        return true;
    }
}

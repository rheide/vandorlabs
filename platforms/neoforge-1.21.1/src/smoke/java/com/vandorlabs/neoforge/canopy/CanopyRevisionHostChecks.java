package com.vandorlabs.neoforge.canopy;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Persisted publication revisions and queue-only cooperative notifications over installed owners. */
final class CanopyRevisionHostChecks {
    private static int checks;private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Canopy revision host "+why);}
    static void run(ServerLevel world){var changed=new ArrayList<BlockPos>();var previous=NativeCanopyNotifications.bindChanged((level,pos)->{need(level==world&&world.getServer().isSameThread(),"notification stays on owning world thread");changed.add(pos);});var pos=new BlockPos(104,230,40);NativeCanopyEntity host=null;
        try{var legacy=new CompoundTag();legacy.putLong("AnchorOffset",0);legacy.putString("PrivateCanopyRevisionOwner","retained");host=CanopyHostChecks.put(world,pos,"canopy",Direction.NORTH,legacy);host.onLoad();need(changed.isEmpty(),"onLoad local-only, no cooperative discovery callback");NativeCanopyLifecycle.prepare(host);need(changed.equals(List.of(pos)),"installed preparation announces owner once");need(host.getUpdateTag(world.registryAccess()).getLong(NativeCanopyEntity.MOTION_REVISION)==0,"old absence starts at zero");host.notifyChanged();need(changed.size()==2,"normal publication notifies retry seam");need(host.getUpdateTag(world.registryAccess()).getLong(NativeCanopyEntity.MOTION_REVISION)==1,"publication advances revision");var saved=host.saveWithoutMetadata(world.registryAccess());need(saved.contains(NativeCanopyEntity.MOTION_REVISION,Tag.TAG_LONG)&&saved.getLong(NativeCanopyEntity.MOTION_REVISION)==1,"revision persists with owner");host.loadWithComponents(saved,world.registryAccess());host.notifyChanged();need(host.getUpdateTag(world.registryAccess()).getLong(NativeCanopyEntity.MOTION_REVISION)==2,"reloaded owner continues publication sequence");
            var wrap=saved.copy();wrap.putLong(NativeCanopyEntity.MOTION_REVISION,Long.MAX_VALUE);host.loadWithComponents(wrap,world.registryAccess());host.notifyChanged();need(host.getUpdateTag(world.registryAccess()).getLong(NativeCanopyEntity.MOTION_REVISION)==Long.MIN_VALUE,"fixed-width sequence wraps without gameplay failure");var bad=saved.copy();bad.putInt(NativeCanopyEntity.MOTION_REVISION,7);host.loadWithComponents(bad,world.registryAccess());need(host.inert()&&host.recoverySnapshot().equals(bad),"malformed own revision retains original inert snapshot");var failed=host.saveWithoutMetadata(world.registryAccess());need(failed.getTagType(NativeCanopyEntity.MOTION_REVISION)==Tag.TAG_INT&&failed.getString("PrivateCanopyRevisionOwner").equals("retained"),"inert save retains original field type/private envelope");
            var powerTag=legacy.copy();powerTag.putBoolean("CanopyRedstone",true);host.loadWithComponents(powerTag,world.registryAccess());NativeCanopyLifecycle.prepare(host);NativeCanopyControl.refreshPower(host);need(!host.links().open(),"unpowered default polarity initially closed");
            var source=pos.east();world.setBlock(source,Blocks.REDSTONE_BLOCK.defaultBlockState(),3);need(host.links().open(),"actual neighboring block callback opens powered owner");
            need(NativeCanopyControl.toggle(host)&&!host.links().open(),"manual close overrides unchanged local signal");world.neighborChanged(pos,Blocks.STONE,source);need(!host.links().open(),"unchanged neighboring input does not undo manual command");
            world.setBlock(source,Blocks.AIR.defaultBlockState(),3);need(!host.links().open(),"actual power-off edge remains closed");world.setBlock(source,Blocks.REDSTONE_BLOCK.defaultBlockState(),3);need(host.links().open(),"new power edge reopens");world.setBlock(source,Blocks.AIR.defaultBlockState(),18);
        }finally{if(host!=null){host.removing(true);world.setBlock(pos,Blocks.AIR.defaultBlockState(),18);}NativeCanopyNotifications.bindChanged(previous);}System.out.println("[vandorlabs][canopy-host] revision/notifications PASS checks="+checks);
    }
}

package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockControlledRamp;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.vehicle.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;

/** Exercise Forge survival harvest checks against the expanded world, through actual confirmation. */
public final class VehicleAuthorizationRuntimeChecks {
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("vehicle authorization: "+text);}
    private static final class Breaks {
        final Set<BlockPos> seen=new HashSet<>();final BlockPos cancel;
        Breaks(BlockPos cancel){this.cancel=cancel;}
        @SubscribeEvent public void broken(BlockEvent.BreakEvent event){require(event.getState()==event.getWorld().getBlockState(event.getPos()),"event state differs from actual world at "+event.getPos());seen.add(event.getPos().toImmutable());if(event.getPos().equals(cancel))event.setCanceled(true);}
    }
    @SuppressWarnings("unchecked")
    public static void check(EntityPlayerMP player,VehicleStructure craft,BlockPos origin,VehicleEscalators.WorldSnapshot expanded,Set<BlockPos> sections,int controllerY)throws Exception {
        GameType oldMode=player.interactionManager.getGameType();ItemStack held=player.getHeldItemMainhand();boolean flying=player.capabilities.isFlying;
        java.lang.reflect.Field previewsField=VehicleService.class.getDeclaredField("previews");previewsField.setAccessible(true);Map<UUID,Object> previews=(Map<UUID,Object>)previewsField.get(null);Object previous=previews.remove(player.getUniqueID());
        try{
            player.interactionManager.setGameType(GameType.SURVIVAL);player.capabilities.isFlying=true;player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));
            Breaks all=new Breaks(null);MinecraftForge.EVENT_BUS.register(all);try{VehicleService.authorizeAssembly(player,craft,origin);}finally{MinecraftForge.EVENT_BUS.unregister(all);}
            for(VehicleStructure.Cell cell:expanded.structure.cells)require(all.seen.contains(expanded.origin.add(cell.pos)),"expanded cell lacks a protection event");
            BlockPos upper=null;for(BlockPos section:sections)if(section.getY()>controllerY && player.world.getBlockState(section).getBlock() instanceof BlockControlledRamp){upper=section;break;}
            require(upper!=null,"fixture must include generated upper ramp cells");
            Breaks denied=new Breaks(upper);MinecraftForge.EVENT_BUS.register(denied);
            try{
                Class<?> type=Class.forName("com.vandorlabs.vehicle.VehicleService$Preview");java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(WorldServer.class,VehicleStructure.class,BlockPos.class,EntityGroundVehicle.class);constructor.setAccessible(true);Object preview=constructor.newInstance(player.getServerWorld(),craft,origin,null);java.lang.reflect.Field token=type.getDeclaredField("token");token.setAccessible(true);previews.put(player.getUniqueID(),preview);
                VehicleService.confirm(player,(UUID)token.get(preview));
            }finally{MinecraftForge.EVENT_BUS.unregister(denied);}
            require(denied.seen.contains(upper),"manual confirmation bypassed generated upper-cell protection");
            require(expanded.structure.matches(player.world,expanded.origin) && !VehicleTransfer.pending(player.getServerWorld()),"cancelled protection changed expanded blocks or left a transfer journal");
        }finally{
            previews.remove(player.getUniqueID());if(previous!=null)previews.put(player.getUniqueID(),previous);
            player.interactionManager.setGameType(oldMode);player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,held);player.capabilities.isFlying=flying;player.sendPlayerAbilities();
        }
    }
    private VehicleAuthorizationRuntimeChecks(){}
}

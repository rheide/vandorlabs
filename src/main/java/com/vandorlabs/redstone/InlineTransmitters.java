package com.vandorlabs.redstone;
import com.vandorlabs.container.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import java.lang.ref.WeakReference;
import java.util.*;
/** Authenticated built-in transmission settings; no block item inventory. */
public final class InlineTransmitters {
    private static final Map<Container,Binding> BINDINGS=Collections.synchronizedMap(new WeakHashMap<Container,Binding>());
    private InlineTransmitters(){}
    public static Binding binding(Container container){return BINDINGS.get(container);}
    public static void attach(Container container,EntityPlayer player){
        if(BINDINGS.containsKey(container)||container instanceof ContainerTransmitterItem)return;
        TileEntity tile=com.vandorlabs.network.MessageTransmitterSlot.openedTile(container);
        if(tile instanceof RedstoneChannelMember&&!(tile instanceof com.vandorlabs.tiles.TileEntitySecuredDisplay))BINDINGS.put(container,new Binding(container));
    }
    public static final class Binding {
        private final WeakReference<Container> owner;
        Binding(Container container){owner=new WeakReference<>(container);}
        public RedstoneChannelMember source(int selector){Container c=owner.get();TileEntity tile=c==null?null:com.vandorlabs.network.MessageTransmitterSlot.openedTile(c);return selector==0&&tile instanceof RedstoneChannelMember?(RedstoneChannelMember)tile:null;}
        public boolean usable(EntityPlayer player){Container c=owner.get();return c!=null&&player.openContainer==c&&c.canInteractWith(player);}
    }
    public static boolean usable(EntityPlayer player,RedstoneChannelMember member){TileEntity tile=member.channelTile();if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld view=(com.vandorlabs.vehicle.VehicleWorld)tile.getWorld();com.vandorlabs.vehicle.EntityGroundVehicle craft=view.craft;if(craft==null||craft.view!=view||craft.structure==null||craft.isDead||craft.ownershipBlocked||craft.transferInProgress||view.getTileEntity(tile.getPos())!=tile||craft.world!=player.world)return false;net.minecraft.util.math.Vec3d at=craft.toWorld(new net.minecraft.util.math.Vec3d(tile.getPos()).addVector(.5,.5,.5));BlockPos pos=new BlockPos(at);return player.getDistanceSq(at.x,at.y,at.z)<=64&&craft.world.isBlockLoaded(pos)&&craft.world.isBlockModifiable(player,pos)&&player.canPlayerEdit(pos,net.minecraft.util.EnumFacing.UP,player.getHeldItemMainhand())&&!player.isDead&&!player.isSpectator();}return tile.getWorld()!=null&&tile.getWorld().isBlockLoaded(tile.getPos())&&tile.getWorld().getTileEntity(tile.getPos())==tile&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,tile.getWorld(),tile.getPos());}
}

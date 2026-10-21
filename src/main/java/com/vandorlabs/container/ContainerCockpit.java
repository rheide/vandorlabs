package com.vandorlabs.container;

import com.vandorlabs.tiles.TileEntityCockpit;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Dialog updates are tied to a fresh reachable anchor in blocks or a captured craft. */
public final class ContainerCockpit extends Container {
    public final TileEntityCockpit tile;
    public final EntityGroundVehicle craft;
    public ContainerCockpit(TileEntityCockpit tile,EntityGroundVehicle craft){this.tile=tile;this.craft=craft;}
    public static ContainerCockpit resolve(EntityPlayer player,World world,int x,int y,int z){
        TileEntityCockpit root;EntityGroundVehicle craft=null;
        if(z==Integer.MIN_VALUE){net.minecraft.entity.Entity e=world.getEntityByID(x);if(!(e instanceof EntityGroundVehicle))return null;craft=(EntityGroundVehicle)e;if(craft.view==null)return null;
            root=com.vandorlabs.cockpit.CockpitAssembly.root(craft.view,new BlockPos(y&63,(y>>6)&63,(y>>12)&63));
        }else root=com.vandorlabs.cockpit.CockpitAssembly.root(world,new BlockPos(x,y,z));
        if(root==null)return null;ContainerCockpit result=new ContainerCockpit(root,craft);return result.canInteractWith(player)?result:null;
    }
    public static void open(EntityPlayer player,TileEntityCockpit tile){
        if(tile.getWorld() instanceof VehicleWorld){EntityGroundVehicle craft=((VehicleWorld)tile.getWorld()).craft;if(craft==null)return;BlockPos p=tile.getPos();player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_COCKPIT,player.world,craft.getEntityId(),p.getX()|(p.getY()<<6)|(p.getZ()<<12),Integer.MIN_VALUE);
        }else{BlockPos p=tile.getPos();player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_COCKPIT,player.world,p.getX(),p.getY(),p.getZ());}
    }
    @Override public boolean canInteractWith(EntityPlayer player){
        if(player==null || player.isDead || player.isSpectator() || tile.isInvalid())return false;
        if(craft==null)return com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,tile.getWorld(),tile.getPos()) && tile.getWorld().getTileEntity(tile.getPos())==tile;
        if(craft.isDead || craft.ownershipBlocked || craft.transferInProgress || craft.world!=player.world || craft.view==null || craft.view.getTileEntity(tile.getPos())!=tile)return false;
        Vec3d at=craft.toWorld(new Vec3d(tile.getPos()).addVector(.5,.5,.5));BlockPos p=new BlockPos(at);
        return player.getDistanceSq(at.x,at.y,at.z)<=64 && player.world.isBlockModifiable(player,p) && player.canPlayerEdit(p,net.minecraft.util.EnumFacing.UP,player.getHeldItemMainhand());
    }
    public void update(EntityPlayerMP player,int operation,NBTTagCompound values){
        if(!canInteractWith(player))return;int previousHeight=tile.getHeight();
        if(operation==1){int i=values.getInteger("Control");double column=values.getDouble("Column");if(i<0 || i>=tile.controls.size() || !Double.isFinite(column) || column<0 || column>1)return;tile.controls.get(i).activate(column);
        }else if(operation==2){if(tile.data().seat==null)return;VehicleService.screenMode(player,tile.getPos(),craft,values.getInteger("Mode"));return;
        }else if(operation==0){
            if(values.hasKey("Height",3)&&tile.design().equals("chair_siderests")){int height=values.getInteger("Height");if(height<0||height>3)return;tile.setHeight(height);}
            if(values.hasKey("SideTexture",3))tile.setSideTexture(values.getInteger("SideTexture"));
            if(values.hasKey("Display",3)){int index=values.getInteger("Display");if(index<0 || index>=tile.displays.size())return;TileEntityCockpit.Display d=tile.displays.get(index);d.read(values);}
            if(values.hasKey("Control",3)){int index=values.getInteger("Control");if(index<0 || index>=tile.controls.size())return;ChannelList channels=ChannelList.parse(values.getString("Channels"));if(channels==null)return;TileEntityCockpit.Control c=tile.controls.get(index);c.kind=Math.max(0,Math.min(2,values.getInteger("Kind")));String label=values.getString("Label");c.label=label.substring(0,Math.min(32,label.length()));c.setRedstoneChannels(channels);}
            if(values.hasKey("Pilot",1)){ChannelList channels=ChannelList.parse(values.getString("Channels"));if(channels==null)return;tile.setTaxiMode(values.getBoolean("Taxi"));tile.setVehicleChannels(channels,player.world);tile.configure(Math.max(0,Math.min(2,values.getInteger("Trigger"))),tile.getHeight(),player.world);
                for(int mode=0;mode<3;mode++){ChannelList list=ChannelList.parse(values.getString("Mode"+mode));if(list!=null)tile.setModeChannels(mode,list);}
            }
        }else return;
        tile.updatePower();tile.changed();if(craft!=null){craft.signals.cockpitChanged(tile);if(previousHeight!=tile.getHeight())VehicleCockpits.refreshGeometry(craft);}
    }
    @Override public net.minecraft.item.ItemStack transferStackInSlot(EntityPlayer player,int index){return net.minecraft.item.ItemStack.EMPTY;}
}

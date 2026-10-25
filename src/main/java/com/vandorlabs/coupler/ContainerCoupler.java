package com.vandorlabs.coupler;

import com.vandorlabs.vehicle.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Both interactions use the same authorized dialog in world blocks and carried vehicles. */
public final class ContainerCoupler extends net.minecraft.inventory.Container {
    public final TileCoupler tile;
    public ContainerCoupler(TileCoupler tile){this.tile=tile;}
    public static ContainerCoupler resolve(EntityPlayer player,World world,BlockPos pos){return at(player,world,pos);}
    public static ContainerCoupler resolve(EntityPlayer player,World world,int x,int y,int z){
        if(z==Integer.MIN_VALUE){net.minecraft.entity.Entity entity=world.getEntityByID(x);if(!(entity instanceof EntityGroundVehicle))return null;EntityGroundVehicle craft=(EntityGroundVehicle)entity;if(craft.view==null||y<0||y>0x3ffff)return null;return at(player,craft.view,new BlockPos(y&63,(y>>6)&63,(y>>12)&63));}
        return at(player,world,new BlockPos(x,y,z));
    }
    private static ContainerCoupler at(EntityPlayer player,World world,BlockPos pos){if(!world.isBlockLoaded(pos))return null;TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof TileCoupler)||!(world.getBlockState(pos).getBlock() instanceof BlockCoupler))return null;ContainerCoupler container=new ContainerCoupler((TileCoupler)raw);return container.canInteractWith(player)?container:null;}
    public static void open(EntityPlayer player,World world,BlockPos pos){
        World parent=world instanceof VehicleWorld?((VehicleWorld)world).parent:world;
        if(parent.isRemote||at(player,world,pos)==null)return;((TileCoupler)world.getTileEntity(pos)).authorize(player);
        if(world instanceof VehicleWorld){EntityGroundVehicle craft=((VehicleWorld)world).craft;if(craft==null)return;if(player instanceof net.minecraft.entity.player.EntityPlayerMP)craft.authorizeCouplingGui((net.minecraft.entity.player.EntityPlayerMP)player,(TileCoupler)world.getTileEntity(pos));player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_COUPLER,parent,craft.getEntityId(),pos.getX()|(pos.getY()<<6)|(pos.getZ()<<12),Integer.MIN_VALUE);}
        else player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_COUPLER,world,pos.getX(),pos.getY(),pos.getZ());
    }
    @Override public boolean canInteractWith(EntityPlayer player){
        World world=tile.getWorld();if(player==null||player.isDead||player.isSpectator()||world==null||tile.isInvalid()||!world.isBlockLoaded(tile.getPos())||world.getTileEntity(tile.getPos())!=tile||!(world.getBlockState(tile.getPos()).getBlock() instanceof BlockCoupler))return false;
        if(!(world instanceof VehicleWorld))return com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,tile.getPos());
        EntityGroundVehicle craft=((VehicleWorld)world).craft;if(craft==null||craft.isDead||craft.ownershipBlocked||craft.transferInProgress||craft.view!=world||craft.world!=player.world)return false;
        Vec3d at=craft.toWorld(new Vec3d(tile.getPos()).addVector(.5,.5,.5));BlockPos target=new BlockPos(at);
        return player.getDistanceSq(at.x,at.y,at.z)<=64&&player.world.isBlockLoaded(target)&&player.world.isBlockModifiable(player,target)&&player.canPlayerEdit(target,net.minecraft.util.EnumFacing.UP,player.getHeldItemMainhand());
    }
}

package com.vandorlabs.coupler;

import com.vandorlabs.vehicle.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Both interactions use the same authorized dialog in world blocks and carried vehicles. */
public final class ContainerCoupler extends net.minecraft.inventory.Container {
    public final TileCoupler tile;public final CouplerControl control;public final VehicleArmCouplerControl captured;
    public ContainerCoupler(TileCoupler tile){this.tile=tile;control=tile;captured=null;}
    public ContainerCoupler(VehicleArmCouplerControl captured){tile=null;this.captured=captured;control=captured;}
    public static ContainerCoupler resolve(EntityPlayer player,World world,BlockPos pos){return at(player,world,pos);}
    public static ContainerCoupler resolve(EntityPlayer player,World world,int x,int y,int z){
        if(CouplerGuiAddress.captured(z)){if(!CouplerGuiAddress.validRoot(y))return null;int packed=z-Integer.MIN_VALUE-1;if(!CouplerGuiAddress.validCell(packed))return null;net.minecraft.entity.Entity entity=world.getEntityByID(x);if(!(entity instanceof EntityGroundVehicle))return null;VehicleArmCouplerControl target=VehicleArmCouplerControl.resolve((EntityGroundVehicle)entity,CouplerGuiAddress.unpack(y),CouplerGuiAddress.unpack(packed));ContainerCoupler result=target==null?null:new ContainerCoupler(target);return result!=null&&result.canInteractWith(player)?result:null;}
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
    public static void openCaptured(EntityPlayer player,VehicleArmCouplerControl target){
        if(target==null||target.craft.world.isRemote||!target.valid(player))return;target.initialize(player);target.authorize(player);EntityGroundVehicle craft=target.craft;BlockPos root=target.address.arm,cell=target.address.cell;
        if((root.getX()|root.getY()|root.getZ())<0||root.getX()>63||root.getY()>63||root.getZ()>63||(cell.getX()|cell.getY()|cell.getZ())<0||cell.getX()>127||cell.getY()>127||cell.getZ()>127)return;
        VehicleNetwork.send((net.minecraft.entity.player.EntityPlayerMP)player,craft.structure,craft.getEntityId(),craft.getUniqueID(),new BlockPos(craft),false,false);
        player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_COUPLER,craft.world,craft.getEntityId(),CouplerGuiAddress.root(root),CouplerGuiAddress.cellZ(cell));
    }
    public void configure(EntityPlayer player,com.vandorlabs.redstone.ChannelList channels,boolean inverted,Boolean articulation){
        if(!canInteractWith(player))return;EntityGroundVehicle craft=captured!=null?captured.craft:tile.getWorld() instanceof VehicleWorld?((VehicleWorld)tile.getWorld()).craft:null;BlockPos at=captured!=null?captured.alias():tile.getPos();
        if(craft!=null&&articulation!=null&&!craft.articulation.canConfigure(at,articulation)){control.status("Straighten this joint before disabling articulation");return;}
        if(craft!=null&&captured==null)craft.authorizeCouplingGui((net.minecraft.entity.player.EntityPlayerMP)player,tile);control.authorize(player);control.configure(channels,inverted,articulation==null?control.articulation():articulation);
    }
    @Override public boolean canInteractWith(EntityPlayer player){
        if(captured!=null)return captured.valid(player);
        World world=tile.getWorld();if(player==null||player.isDead||player.isSpectator()||world==null||tile.isInvalid()||!world.isBlockLoaded(tile.getPos())||world.getTileEntity(tile.getPos())!=tile||!(world.getBlockState(tile.getPos()).getBlock() instanceof BlockCoupler))return false;
        if(!(world instanceof VehicleWorld))return com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,tile.getPos());
        EntityGroundVehicle craft=((VehicleWorld)world).craft;if(craft==null||craft.isDead||craft.ownershipBlocked||craft.transferInProgress||craft.view!=world||craft.world!=player.world)return false;
        Vec3d at=craft.toWorld(new Vec3d(tile.getPos()).addVector(.5,.5,.5));BlockPos target=new BlockPos(at);
        return player.getDistanceSq(at.x,at.y,at.z)<=64&&player.world.isBlockLoaded(target)&&player.world.isBlockModifiable(player,target)&&player.canPlayerEdit(target,net.minecraft.util.EnumFacing.UP,player.getHeldItemMainhand());
    }
}

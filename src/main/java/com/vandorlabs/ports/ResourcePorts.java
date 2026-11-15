package com.vandorlabs.ports;

import com.vandorlabs.items.ConfigurationAccess;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;

public final class ResourcePorts {
    public static final ResourcePortBlock POWER=new ResourcePortBlock(ResourcePortBlock.Kind.POWER);
    public static final ResourcePortBlock FLUID=new ResourcePortBlock(ResourcePortBlock.Kind.FLUID);
    public static final ResourcePortBlock MATTER=new ResourcePortBlock(ResourcePortBlock.Kind.MATTER);
    public static final ResourcePortBlock[] BLOCKS={POWER,FLUID,MATTER};
    private ResourcePorts(){}
    public static void registerTiles(){GameRegistry.registerTileEntity(PowerPortTile.class,"vandorlabs:power_port");GameRegistry.registerTileEntity(FluidPortTile.class,"vandorlabs:fluid_port");GameRegistry.registerTileEntity(MatterPortTile.class,"vandorlabs:matter_port");}
    /** Ordinary interaction and Configurizer use this same checked dedicated dialog. */
    public static boolean open(EntityPlayer player,World world,BlockPos pos){
        if(!world.isBlockLoaded(pos)||!(world.getTileEntity(pos) instanceof ResourcePortTile)||((ResourcePortTile)world.getTileEntity(pos)).inert()||!ConfigurationAccess.canEditDialog(player,world,pos))return false;
        if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_RESOURCE_PORT,world,pos.getX(),pos.getY(),pos.getZ());return true;
    }
}

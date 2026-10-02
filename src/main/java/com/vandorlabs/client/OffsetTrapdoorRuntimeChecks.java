package com.vandorlabs.client;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockProgrammableTrapdoor;
import com.vandorlabs.network.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Aim at the actual offset leaf, use the real interaction packet and check both collision worlds. */
final class OffsetTrapdoorRuntimeChecks {
    private final BlockPos pos;
    private int scenario,stage,wait;
    OffsetTrapdoorRuntimeChecks(BlockPos pos){this.pos=pos;}
    boolean tick(Minecraft mc) {
        if(wait>0){wait--;return false;}
        if(stage==0) {
            final int index=scenario;
            mc.player.inventory.setInventorySlotContents(mc.player.inventory.currentItem,net.minecraft.item.ItemStack.EMPTY);
            mc.getIntegratedServer().addScheduledTask(()->{
                World world=mc.getIntegratedServer().getWorld(0);
                TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)world.getTileEntity(pos);
                leaf.configureGroup(leaf.getHousingTexture(),0,index>=8,0,0,false,true,true,EnumFacing.HORIZONTALS[index%4]);leaf.requestOpen(index%8>=4);
                AxisAlignedBB box=OffsetTrapdoorInteractions.bounds(leaf);Vec3d center=box.getCenter();EnumFacing facing=leaf.coverFacing();
                Vec3d eye=index%8>=4?center.addVector(2*facing.getFrontOffsetX(),0,2*facing.getFrontOffsetZ()):center.addVector(0,2,0);
                EntityPlayerMP owner=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());
                owner.inventory.setInventorySlotContents(owner.inventory.currentItem,net.minecraft.item.ItemStack.EMPTY);owner.inventoryContainer.detectAndSendChanges();
                owner.capabilities.isFlying=true;owner.sendPlayerAbilities();
                owner.connection.setPlayerLocation(eye.x,eye.y-owner.getEyeHeight(),eye.z,index%8>=4?facing.getOpposite().getHorizontalAngle():0,index%8>=4?0:90);
            });
            stage=1;wait=30;return false;
        }
        if(stage==1) {
            RayTraceResult hit=mc.objectMouseOver;
            if(hit==null || hit.typeOfHit!=RayTraceResult.Type.BLOCK || !pos.equals(hit.getBlockPos()))throw new IllegalStateException("real offset mouse selection missed scenario "+scenario+": "+hit);
            for(World world:new World[]{mc.world,mc.getIntegratedServer().getWorld(0)}) {
                TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)world.getTileEntity(pos);
                AxisAlignedBB box=OffsetTrapdoorInteractions.bounds(leaf);Vec3d center=box.getCenter();
                AxisAlignedBB query=new AxisAlignedBB(center.x-.04,center.y-.04,center.z-.04,center.x+.04,center.y+.04,center.z+.04);
                if(!world.getCollisionBoxes(null,query).contains(box))throw new IllegalStateException("actual offset collision event missed scenario "+scenario);
            }
            if(!(mc.playerController instanceof OffsetTrapdoorController))throw new IllegalStateException("offset picking is not installed in the input phase");
            net.minecraft.client.settings.KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
            stage=2;wait=15;return false;
        }
        if(stage==2) {
            World world=mc.getIntegratedServer().getWorld(0);
            if(world.getBlockState(pos).getValue(BlockProgrammableTrapdoor.OPEN)==(scenario%8>=4))throw new IllegalStateException("offset click did not reach owner on server: "+scenario);
            if(++scenario<16){stage=0;return false;}
            System.out.println("[vandorlabs][reprolab] offset-trapdoor-hitbox-runtime PASS (all hinges, both states, both movements, real aim/use input and client/server collision)");
            mc.getIntegratedServer().addScheduledTask(()->{
                TileEntityProgrammableTrapdoor root=(TileEntityProgrammableTrapdoor)world.getTileEntity(pos);root.setCover(false);root.requestOpen(false);
                for(int z=0;z<2;z++)for(int x=0;x<2;x++)if(x!=0 || z!=0)TrapdoorGallery.place(world,pos.add(x,0,z),false,root.getHousingTexture(),0,false,EnumFacing.NORTH);
                root.configureGroup(root.getHousingTexture(),0,false,0,0,false,false,true,EnumFacing.NORTH);
                EntityPlayerMP owner=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());owner.connection.setPlayerLocation(pos.getX()+.5,pos.getY(),pos.getZ()-2,0,0);
            });
            stage=3;wait=30;return false;
        }
        if(stage==3) {
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP owner=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());owner.openGui(VandorLabs.instance,GuiHandler.GUI_PROGRAMMABLE_TRAPDOOR,owner.world,pos.getX(),pos.getY(),pos.getZ());
            });stage=4;wait=20;return false;
        }
        if(stage==4) {
            if(!(mc.currentScreen instanceof GuiProgrammableTrapdoor))throw new IllegalStateException("joined offset guard GUI missing");
            GuiProgrammableTrapdoor gui=(GuiProgrammableTrapdoor)mc.currentScreen;
            java.util.List<net.minecraft.client.gui.GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreen.class,gui,"buttonList","field_146292_n");
            boolean guarded=false;for(net.minecraft.client.gui.GuiButton button:buttons)if(button.id==7)guarded=!button.enabled;
            if(!guarded)throw new IllegalStateException("joined Next block control still enabled");
            gui.actionPerformed(new net.minecraft.client.gui.GuiButton(7,0,0,"Next block"));
            TileEntityProgrammableTrapdoor root=(TileEntityProgrammableTrapdoor)mc.world.getTileEntity(pos);
            PacketHandler.INSTANCE.sendToServer(new MessageProgrammableTrapdoor(pos,root.getHousingTexture(),0,false,0,0,false,true,true,EnumFacing.NORTH));
            stage=5;wait=20;return false;
        }
        for(World world:new World[]{mc.world,mc.getIntegratedServer().getWorld(0)}) {
            TileEntityProgrammableTrapdoor root=(TileEntityProgrammableTrapdoor)world.getTileEntity(pos);
            if(root.group().size()!=4)throw new IllegalStateException("server offset request dissolved joined trapdoor");
            for(TileEntityProgrammableTrapdoor leaf:root.group())if(leaf.isCover())throw new IllegalStateException("server offset request moved joined leaf");
        }
        mc.player.closeScreen();System.out.println("[vandorlabs][reprolab] trapdoor-group-offset-guard PASS");return true;
    }
}

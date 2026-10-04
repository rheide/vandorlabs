package com.vandorlabs.client;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.tiles.TileEntityRedstoneScreen;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import javax.imageio.ImageIO;

/** Real row clicks plus add/edit/remove/save/reopen over the integrated connection. */
final class RedstoneScreenRuntimeChecks {
    private static final BlockPos POS=new BlockPos(8,80,8);
    private static int shape,stage,ticks;
    private static Vec3d hit;
    private static com.google.common.util.concurrent.ListenableFuture<?> pending;
    static void tick(Minecraft mc,File output){
        if(stage==99)return;
        try{
            if(++ticks>400)throw new IllegalStateException("screen timeout shape="+shape+" stage="+stage);
            if(pending!=null){if(!pending.isDone())return;pending.get();pending=null;}
            if(stage==0){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP owner=owner(mc);owner.closeScreen();owner.capabilities.isCreativeMode=true;owner.capabilities.isFlying=true;owner.sendPlayerAbilities();
                    owner.inventory.clear();owner.inventory.setInventorySlotContents(0,new net.minecraft.item.ItemStack(ModBlocks.PROGRAMMABLE_REDSTONE_SCREEN));
                    owner.inventory.setInventorySlotContents(1,new net.minecraft.item.ItemStack(ModBlocks.PROGRAMMABLE_DIAGONAL_REDSTONE_SCREEN));owner.inventory.currentItem=8;
                    owner.inventoryContainer.detectAndSendChanges();owner.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(8));
                    for(BlockPos p:BlockPos.getAllInBox(POS.add(-3,-3,-3),POS.add(3,3,3)))owner.world.setBlockToAir(p);
                    IBlockState state=fixtureState();
                    owner.world.setBlockState(POS,state,3);
                    TileEntityRedstoneScreen tile=(TileEntityRedstoneScreen)owner.world.getTileEntity(POS);
                    require(tile.configureRows(Arrays.asList("Doors","Lights","Unlinked"),Arrays.asList(ChannelList.of(14901,14902),ChannelList.of(14902),ChannelList.EMPTY),0),"fixture rows");
                    aim(owner,state,0);
                });next(1);return;
            }
            if(stage==1 && ticks>25){capture(mc,output,"off");click(mc);next(2);return;}
            if(stage==2 && ticks>15){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    TileEntityRedstoneScreen tile=tile(mc);require(tile.rows().get(0).active() && tile.rows().get(1).active(),"in-world row button failed to power channels");
                    net.minecraft.item.ItemStack picked=((BlockAnimatedScreenSelector)owner(mc).world.getBlockState(POS).getBlock()).createConfiguredDrop(tile);
                    net.minecraft.nbt.NBTTagCompound row=picked.getSubCompound("BlockEntityTag").getTagList("RedstoneRows",10).getCompoundTagAt(0);
                    require(row.getString("Label").equals("Doors") && row.getIntArray("RedstoneChannels").length==2 && !row.hasKey("LatchedChannels"),"picked screen lost configuration or copied live power");
                });capture(mc,output,"on");next(3);return;
            }
            if(stage==3){pending=mc.getIntegratedServer().addScheduledTask(()->aim(owner(mc),owner(mc).world.getBlockState(POS),1));next(4);return;}
            if(stage==4 && ticks>15){click(mc);next(5);return;}
            if(stage==5 && ticks>15){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    TileEntityRedstoneScreen tile=tile(mc);require(!tile.rows().get(0).active() && !tile.rows().get(1).active(),"partial bank incorrectly active");
                    require(RedstoneChannels.allPowered(owner(mc).world,ChannelList.of(14901)) && !RedstoneChannels.allPowered(owner(mc).world,ChannelList.of(14902)),"partial click bridged bank");
                });capture(mc,output,"mixed");next(6);return;
            }
            if(stage==6){open(mc);next(7);return;}
            if(stage==7 && ticks>15 && mc.currentScreen instanceof GuiRedstoneScreen){
                GuiRedstoneScreen gui=(GuiRedstoneScreen)mc.currentScreen;
                gui.actionPerformed(new GuiButton(2,0,0,"Add"));
                field(gui,"labelField").setText("Ramp bank");field(gui,"channelField").setText("14903, 14904");
                gui.actionPerformed(new GuiButton(4,0,0,"Done"));next(8);return;
            }
            if(stage==8 && ticks>15){pending=mc.getIntegratedServer().addScheduledTask(()->{
                TileEntityRedstoneScreen tile=tile(mc);require(tile.rows().size()==4 && tile.rows().get(3).label.equals("Ramp bank") && tile.rows().get(3).channels.equals(ChannelList.of(14903,14904)),"GUI row packet lost edits");
                TileEntityRedstoneScreen copy=new TileEntityRedstoneScreen();copy.readFromNBT(tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));require(copy.rows().size()==4 && copy.rows().get(3).channels.equals(tile.rows().get(3).channels),"saved rows lost");
            });next(9);return;}
            if(stage==9){open(mc);next(10);return;}
            if(stage==10 && ticks>15 && mc.currentScreen instanceof GuiRedstoneScreen){
                capture(mc,output,"dialog");GuiRedstoneScreen gui=(GuiRedstoneScreen)mc.currentScreen;
                gui.actionPerformed(new GuiButton(3,0,0,"Remove"));gui.actionPerformed(new GuiButton(4,0,0,"Done"));next(11);return;
            }
            if(stage==11 && ticks>15){pending=mc.getIntegratedServer().addScheduledTask(()->require(tile(mc).rows().size()==3 && tile(mc).rows().get(0).label.equals("Lights"),"GUI row removal failed"));next(12);return;}
            if(stage==12){System.out.println("[vandorlabs][reprolab] redstone-screen-runtime PASS shape="+shape);shape++;next(shape<14?0:99);if(stage==99)mc.shutdown();}
        }catch(Exception e){throw new IllegalStateException("redstone-screen live check shape="+shape+" stage="+stage,e);}
    }
    private static void next(int value){stage=value;ticks=0;}
    private static EntityPlayerMP owner(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static TileEntityRedstoneScreen tile(Minecraft mc){return (TileEntityRedstoneScreen)owner(mc).world.getTileEntity(POS);}
    private static GuiTextField field(GuiRedstoneScreen gui,String name)throws ReflectiveOperationException{Field f=GuiRedstoneScreen.class.getDeclaredField(name);f.setAccessible(true);return (GuiTextField)f.get(gui);}
    private static void open(Minecraft mc){pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=owner(mc);p.openGui(VandorLabs.instance,GuiHandler.GUI_REDSTONE_SCREEN,p.world,POS.getX(),POS.getY(),POS.getZ());});}
    private static void aim(EntityPlayerMP owner,IBlockState state,int row){
        ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state);double u=109/128D,v=(24+row*12+5)/128D;
        Vec3d localHit=new Vec3d(q.topRight.x+(q.topLeft.x-q.topRight.x)*u,q.topRight.y+(q.bottomRight.y-q.topRight.y)*v,q.topRight.z+(q.bottomRight.z-q.topRight.z)*v);
        EnumFacing facing=RedstoneScreenInteractions.facing(state);
        hit=worldPoint(localHit,facing);
        Vec3d eye=worldPoint(localHit.addVector(0,q.ny*32,q.nz*32),facing),look=hit.subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.atan2(look.y,Math.sqrt(look.x*look.x+look.z*look.z)));
        owner.connection.setPlayerLocation(eye.x,eye.y-owner.getEyeHeight(),eye.z,yaw,pitch);
    }
    private static IBlockState fixtureState(){
        if(shape==0 || shape>=3 && shape<=7){
            EnumFacing[] directions={EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.UP,EnumFacing.DOWN};
            return ModBlocks.PROGRAMMABLE_REDSTONE_SCREEN.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,directions[shape==0?0:shape-2]);
        }
        EnumFacing facing=shape<3?EnumFacing.NORTH:shape<10?EnumFacing.EAST:shape<12?EnumFacing.SOUTH:EnumFacing.WEST;
        return ModBlocks.PROGRAMMABLE_DIAGONAL_REDSTONE_SCREEN.getDefaultState().withProperty(BlockProgrammableDiagonalScreen.FACING,facing).withProperty(BlockProgrammableDiagonalScreen.INVERTED,shape==2 || shape>=8 && shape%2==1);
    }
    private static Vec3d worldPoint(Vec3d point,EnumFacing facing){
        double x=point.x/16-.5,y=point.y/16-.5,z=point.z/16-.5,wx=x,wy=y,wz=z;
        switch(facing){case EAST:wx=-z;wz=x;break;case SOUTH:wx=-x;wz=-z;break;case WEST:wx=z;wz=-x;break;
            case UP:wx=-x;wy=-z;wz=-y;break;case DOWN:wy=z;wz=-y;break;default:break;}
        return new Vec3d(POS.getX()+wx+.5,POS.getY()+wy+.5,POS.getZ()+wz+.5);
    }
    private static void click(Minecraft mc){
        RayTraceResult trace=mc.objectMouseOver;
        require(trace!=null && trace.typeOfHit==RayTraceResult.Type.BLOCK && POS.equals(trace.getBlockPos()),"crosshair did not select screen");
        mc.playerController.processRightClickBlock(mc.player,mc.world,POS,trace.sideHit,trace.hitVec,EnumHand.MAIN_HAND);
    }
    private static void capture(Minecraft mc,File output,String name)throws java.io.IOException{ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_redstone_screen_"+shape+"_"+name+".png"));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}

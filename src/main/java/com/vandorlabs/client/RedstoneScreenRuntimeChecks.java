package com.vandorlabs.client;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.tiles.RedstoneScreenContents;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
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
        if(shape>=28){ScreenCopyAndParticleRuntimeChecks.tick(mc,output);return;}
        if(stage==99)return;
        try{
            if(++ticks>400)throw new IllegalStateException("screen timeout shape="+shape+" stage="+stage);
            if(pending!=null){if(!pending.isDone())return;pending.get();pending=null;}
            if(stage==0){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP owner=owner(mc);owner.closeScreen();owner.capabilities.isCreativeMode=true;owner.capabilities.isFlying=true;owner.sendPlayerAbilities();
                    owner.inventory.clear();owner.inventory.setInventorySlotContents(0,new net.minecraft.item.ItemStack(ModBlocks.ANIMATED_SCREEN_SELECTOR));
                    owner.inventory.setInventorySlotContents(1,new net.minecraft.item.ItemStack(ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN));owner.inventory.currentItem=8;
                    owner.inventoryContainer.detectAndSendChanges();owner.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(8));
                    for(BlockPos p:BlockPos.getAllInBox(POS.add(-3,-3,-3),POS.add(3,3,3)))owner.world.setBlockToAir(p);
                    IBlockState state=fixtureState();
                    owner.world.setBlockState(POS,state,3);
                    RedstoneScreenContents tile=((TileEntityAnimatedScreenSelector)owner.world.getTileEntity(POS)).redstoneScreen(slot());
                    require(tile.configureRows(Arrays.asList("Doors","Lights","Unlinked"),Arrays.asList(ChannelList.of(14901,14902),ChannelList.of(14902),ChannelList.EMPTY),0),"fixture rows");
                    if(shape==16 || shape==21){tile.tile().setCeilingMounted(true);tile.tile().setCeilingPosition(1);}
                    if(shape==18)tile.tile().setWallPosition(1);
                    aim(owner,state,0);
                });next(20);return;
            }
            if(stage==20 && ticks>25){pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=owner(mc);p.openGui(VandorLabs.instance,GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR,p.world,POS.getX(),POS.getY(),POS.getZ());});next(22);return;}
            if(stage==22 && ticks>15 && mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer){capture(mc,output,"picker");chooseRedstone(mc);next(21);return;}
            if(stage==21 && ticks>15 && mc.currentScreen instanceof GuiRedstoneScreen){((GuiRedstoneScreen)mc.currentScreen).actionPerformed(new GuiButton(4,0,0,"Done"));next(1);return;}
            if(stage==1 && ticks>25){capture(mc,output,"off");click(mc);next(2);return;}
            if(stage==2 && ticks>15){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    RedstoneScreenContents tile=tile(mc);require(tile.rows().get(0).active() && tile.rows().get(1).active(),"in-world row button failed to power channels");
                    net.minecraft.item.ItemStack picked=((BlockAnimatedScreenSelector)owner(mc).world.getBlockState(POS).getBlock()).createConfiguredDrop(tile.tile());
                    net.minecraft.nbt.NBTTagCompound row=picked.getSubCompound("BlockEntityTag").getCompoundTag(slot()==0?"RedstonePrimary":"RedstoneSecondary").getTagList("Rows",10).getCompoundTagAt(0);
                    require(row.getString("Label").equals("Doors") && row.getIntArray("RedstoneChannels").length==2 && !row.hasKey("LatchedChannels"),"picked screen lost configuration or copied live power");
                });capture(mc,output,"on");next(3);return;
            }
            if(stage==3){pending=mc.getIntegratedServer().addScheduledTask(()->aim(owner(mc),owner(mc).world.getBlockState(POS),1));next(4);return;}
            if(stage==4 && ticks>15){click(mc);next(5);return;}
            if(stage==5 && ticks>15){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    RedstoneScreenContents tile=tile(mc);require(!tile.rows().get(0).active() && !tile.rows().get(1).active(),"partial bank incorrectly active");
                    require(RedstoneChannels.allPowered(owner(mc).world,ChannelList.of(14901)) && !RedstoneChannels.allPowered(owner(mc).world,ChannelList.of(14902)),"partial click bridged bank");
                });capture(mc,output,"mixed");next(6);return;
            }
            if(stage==6){open(mc);next(7);return;}
            if(stage==7 && ticks>15 && mc.currentScreen instanceof GuiRedstoneScreen){
                GuiRedstoneScreen gui=(GuiRedstoneScreen)mc.currentScreen;
                gui.actionPerformed(new GuiButton(2,0,0,"Add"));
                field(gui,"titleField").setText("Bridge controls");field(gui,"labelField").setText("Ramp bank");field(gui,"channelField").setText("14903, 14904");
                gui.actionPerformed(new GuiButton(5,0,0,"Up"));
                require(field(gui,"labelField").getText().equals("Ramp bank") && field(gui,"channelField").getText().equals("14903, 14904"),"moving row keeps editor and channel bank");
                gui.actionPerformed(new GuiButton(6,0,0,"Down"));gui.actionPerformed(new GuiButton(5,0,0,"Up"));
                gui.actionPerformed(new GuiButton(4,0,0,"Done"));next(8);return;
            }
            if(stage==8 && ticks>15){pending=mc.getIntegratedServer().addScheduledTask(()->{
                RedstoneScreenContents tile=tile(mc);require(tile.title().equals(expectedTitle()) && tile.rows().size()==4 && tile.rows().get(2).label.equals("Ramp bank") && tile.rows().get(2).channels.equals(ChannelList.of(14903,14904)),"GUI row packet lost edits");
                RedstoneScreenContents copy=new RedstoneScreenContents(new TileEntityAnimatedScreenSelector(),slot());copy.readFromNBT(tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));require(copy.rows().size()==4 && copy.rows().get(2).channels.equals(tile.rows().get(2).channels),"saved rows lost");
            });next(9);return;}
            if(stage==9){open(mc);next(10);return;}
            if(stage==10 && ticks>15 && mc.currentScreen instanceof GuiRedstoneScreen){
                capture(mc,output,"dialog");GuiRedstoneScreen gui=(GuiRedstoneScreen)mc.currentScreen;
                require(field(gui,"titleField").getText().equals(expectedTitle()),"reopened header lost");
                field(gui,"labelField").setText("WWWWWWWWWWWWWWWWWWWWWWWW");gui.actionPerformed(new GuiButton(4,0,0,"Done"));
                require(mc.currentScreen==gui,"overwide label saved");field(gui,"labelField").setText("Doors");
                require(field(gui,"titleField").getVisible()==!RedstoneScreenInteractions.half(fixtureState().getBlock(),slot()),"half-height header field visibility differs");
                if(!RedstoneScreenInteractions.half(fixtureState().getBlock(),slot())){
                    field(gui,"titleField").setText("WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW");gui.actionPerformed(new GuiButton(4,0,0,"Done"));
                    require(mc.currentScreen==gui,"overwide header saved");field(gui,"titleField").setText("Bridge controls");
                }
                gui.actionPerformed(new GuiButton(3,0,0,"Remove"));gui.actionPerformed(new GuiButton(4,0,0,"Done"));next(11);return;
            }
            if(stage==11 && ticks>15){pending=mc.getIntegratedServer().addScheduledTask(()->require(tile(mc).rows().size()==3 && tile(mc).rows().get(0).label.equals("Lights"),"GUI row removal failed"));next(12);return;}
            if(stage==12){System.out.println("[vandorlabs][reprolab] redstone-screen-runtime PASS shape="+shape);if(Boolean.getBoolean("vandorlabs.redstoneScreenFocused")){
                int[] fixtures={0,17,21,22,23,25,26,27};int index=0;while(index<fixtures.length && fixtures[index]!=shape)index++;shape=index+1<fixtures.length?fixtures[index+1]:28;
            }else shape++;next(shape<28?0:99);}
        }catch(Exception e){throw new IllegalStateException("redstone-screen live check shape="+shape+" stage="+stage,e);}
    }
    private static void next(int value){stage=value;ticks=0;}
    private static EntityPlayerMP owner(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static RedstoneScreenContents tile(Minecraft mc){return ((TileEntityAnimatedScreenSelector)owner(mc).world.getTileEntity(POS)).redstoneScreen(slot());}
    private static GuiTextField field(GuiRedstoneScreen gui,String name)throws ReflectiveOperationException{Field f=GuiRedstoneScreen.class.getDeclaredField(name);f.setAccessible(true);return (GuiTextField)f.get(gui);}
    private static void open(Minecraft mc){pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=owner(mc);p.openGui(VandorLabs.instance,slot()==0?GuiHandler.GUI_REDSTONE_SCREEN:GuiHandler.GUI_REDSTONE_SCREEN_SECONDARY,p.world,POS.getX(),POS.getY(),POS.getZ());});}
    private static void aim(EntityPlayerMP owner,IBlockState state,int row){
        ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state,((TileEntityAnimatedScreenSelector)owner.world.getTileEntity(POS)),slot());double u=(row==0?30:109)/128D,v=(RedstoneScreenInteractions.rowTop(state.getBlock(),slot())+row*12+5)/(double)RedstoneScreenInteractions.displayHeight(state.getBlock(),slot());
        Vec3d localHit=new Vec3d(q.topRight.x+(q.topLeft.x-q.topRight.x)*u,q.topRight.y+(q.bottomRight.y-q.topRight.y)*v,q.topRight.z+(q.bottomRight.z-q.topRight.z)*v);
        EnumFacing facing=RedstoneScreenInteractions.facing(state);
        hit=worldPoint(localHit,facing);
        Vec3d eye=worldPoint(localHit.addVector(0,q.ny*32,q.nz*32),facing),look=hit.subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.atan2(look.y,Math.sqrt(look.x*look.x+look.z*look.z)));
        owner.connection.setPlayerLocation(eye.x,eye.y-owner.getEyeHeight(),eye.z,yaw,pitch);
    }
    private static void chooseRedstone(Minecraft mc)throws Exception{
        Object gui=mc.currentScreen;
        if(slot()==1){if(gui instanceof GuiAnimatedScreenSelector)((GuiAnimatedScreenSelector)gui).actionPerformed(new GuiButton(91,0,0,"Controls"));else ((GuiProgrammableHalfConsole)gui).actionPerformed(new GuiButton(91,0,0,"Controls"));}
        String fieldName=shape>=26?(slot()==1?"topList":"bottomList"):slot()==1?"inputList":"screenList";
        Field pickerField=gui.getClass().getDeclaredField(fieldName);pickerField.setAccessible(true);ScreenTextureList picker=(ScreenTextureList)pickerField.get(gui);
        Field f=ScreenTextureList.class.getDeclaredField("list");f.setAccessible(true);HousingTextureList list=(HousingTextureList)f.get(picker);
        f=HousingTextureList.class.getDeclaredField("rows");f.setAccessible(true);java.util.List<?> rows=(java.util.List<?>)f.get(list);int index=rows.indexOf(-100001);require(index>=0,"artwork picker lacks Redstone option");
        f=HousingTextureList.class.getDeclaredField("count");f.setAccessible(true);int count=f.getInt(list),scroll=Math.max(0,index-count+1);
        f=HousingTextureList.class.getDeclaredField("scroll");f.setAccessible(true);f.setInt(list,scroll);
        f=HousingTextureList.class.getDeclaredField("x");f.setAccessible(true);int x=f.getInt(list)+3;
        f=HousingTextureList.class.getDeclaredField("y");f.setAccessible(true);int y=f.getInt(list)+(index-scroll)*HousingTextureList.ROW_HEIGHT+10;
        if(gui instanceof GuiAnimatedScreenSelector)((GuiAnimatedScreenSelector)gui).mouseClicked(x,y,0);else if(gui instanceof GuiProgrammableInput)((GuiProgrammableInput)gui).mouseClicked(x,y,0);else ((GuiProgrammableHalfConsole)gui).mouseClicked(x,y,0);
    }
    private static String expectedTitle(){return RedstoneScreenInteractions.half(fixtureState().getBlock(),slot())?RedstoneScreenContents.DEFAULT_TITLE:"Bridge controls";}
    private static int slot(){return shape==25 || shape==27?1:0;}
    private static IBlockState fixtureState(){
        if(shape>=14){
            if(shape<=21){boolean full=shape<=16;return (full?ModBlocks.PROGRAMMABLE_FULL_INPUT:ModBlocks.PROGRAMMABLE_INPUT).getDefaultState()
                .withProperty(BlockProgrammableInput.FACING,EnumFacing.NORTH).withProperty(BlockProgrammableInput.KEYBOARD,shape==15 || shape==16 || shape==20 || shape==21).withProperty(BlockProgrammableInput.UPPER,shape==19 || shape==16 || shape==21);}
            if(shape<=23)return net.minecraft.block.Block.REGISTRY.getObject(new ResourceLocation("vandorlabs","programmable_diagonal_half_console")).getDefaultState().withProperty(BlockDiagonalHalfConsole.UPPER,shape==23);
            return (shape<=25?ModBlocks.PROGRAMMABLE_CONSOLE:ModBlocks.PROGRAMMABLE_HALF_CONSOLE).getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.NORTH);
        }
        if(shape==0 || shape>=3 && shape<=7){
            EnumFacing[] directions={EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.UP,EnumFacing.DOWN};
            return ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,directions[shape==0?0:shape-2]);
        }
        EnumFacing facing=shape<3?EnumFacing.NORTH:shape<10?EnumFacing.EAST:shape<12?EnumFacing.SOUTH:EnumFacing.WEST;
        return ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN.getDefaultState().withProperty(BlockProgrammableDiagonalScreen.FACING,facing).withProperty(BlockProgrammableDiagonalScreen.INVERTED,shape==2 || shape>=8 && shape%2==1);
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

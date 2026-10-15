package com.vandorlabs.client;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/** Real picker packets must change both rendered and interactive surface modes. */
public final class ScreenModeTransitionRuntimeChecks {
    private static final BlockPos POS=new BlockPos(160,100,160);
    private static final List<Block> blocks=new ArrayList<>();private static final List<Integer> slots=new ArrayList<>();
    private static int scenario,stage,frame,total;
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("screen mode: "+message+" scenario="+scenario+" stage="+stage);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static TileEntityAnimatedScreenSelector tile(Minecraft mc){return (TileEntityAnimatedScreenSelector)world(mc).getTileEntity(POS);}
    private static int slot(){return slots.get(scenario);}
    public static void begin(Minecraft mc)throws Exception{
        scenario=stage=frame=total=0;blocks.clear();slots.clear();
        Block[] variants={ModBlocks.ANIMATED_SCREEN_SELECTOR,ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN,ModBlocks.PROGRAMMABLE_INPUT,ModBlocks.PROGRAMMABLE_FULL_INPUT,ModBlocks.PROGRAMMABLE_CONSOLE,ModBlocks.PROGRAMMABLE_HALF_CONSOLE,Block.getBlockFromName("vandorlabs:programmable_diagonal_half_console")};
        for(Block block:variants)for(int slot=0;slot<2;slot++)if(RedstoneScreenInteractions.supportsSlot(block,slot)){blocks.add(block);slots.add(slot);}
        mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer w=world(mc);for(int i=0;i<blocks.size();i++){
                Block block=blocks.get(i);int slot=slots.get(i);w.setBlockToAir(POS);w.setBlockState(POS,block.getDefaultState(),3);TileEntityAnimatedScreenSelector tile=tile(mc);
                tile.setSurfaceTexture(slot,TileEntityAnimatedScreenSelector.VEHICLE_SURFACE);require(tile.hasVehicleStatus(slot),"Status initial mode");
                require(tile.redstoneScreen(slot).configureRows(Arrays.asList("Control"),Arrays.asList(ChannelList.EMPTY),0),"configure direct Redstone rows");
                require(tile.hasRedstoneScreen(slot)&&!tile.hasVehicleStatus(slot),"direct Redstone configuration replaces previous Status renderer/interaction");
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntityAnimatedScreenSelector loaded=(TileEntityAnimatedScreenSelector)block.createTileEntity(w,block.getDefaultState());loaded.setWorld(w);loaded.setPos(POS);loaded.readFromNBT(saved);require(loaded.hasRedstoneScreen(slot)&&!loaded.hasVehicleStatus(slot),"mixed old saved Status override cannot mask enabled Redstone rows");
                BlockPos local=new BlockPos(0,1,0);VehicleStructure.Cell cell=new VehicleStructure.Cell(local,block.getDefaultState(),VehicleTileData.translated(saved,local.subtract(POS)));VehicleWorld view=new VehicleWorld(w,new VehicleStructure(Arrays.asList(cell,new VehicleStructure.Cell(new BlockPos(2,0,0),ModBlocks.PILOT_SEAT.getDefaultState(),null)),new BlockPos(2,0,0),EnumFacing.NORTH,1));
                TileEntityAnimatedScreenSelector captured=(TileEntityAnimatedScreenSelector)view.getTileEntity(local);require(captured.hasRedstoneScreen(slot)&&!captured.hasVehicleStatus(slot),"captured old mixed state selects effective Redstone mode");
                for(int choice:new int[]{TileEntityAnimatedScreenSelector.VEHICLE_SURFACE,TileEntityAnimatedScreenSelector.REDSTONE_SURFACE,-1,TileEntityAnimatedScreenSelector.VEHICLE_SURFACE}){tile.setSurfaceTexture(slot,choice);require(tile.getSurfaceTexture(slot)==choice&&tile.hasVehicleStatus(slot)==(choice==TileEntityAnimatedScreenSelector.VEHICLE_SURFACE)&&tile.hasRedstoneScreen(slot)==(choice==TileEntityAnimatedScreenSelector.REDSTONE_SURFACE),"shared surface transition remains exclusive: "+choice);}
            }
            System.out.println("[vandorlabs][reprolab] screen-mode-world PASS all-supported-slots exclusive-transitions old-mixed-NBT captured-state");
        }).get();prepare(mc);
    }
    private static void prepare(Minecraft mc)throws Exception{
        mc.player.closeScreen();mc.displayGuiScreen(null);mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP p=player(mc);p.setSneaking(false);p.capabilities.isCreativeMode=true;p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));p.inventoryContainer.detectAndSendChanges();
            WorldServer w=world(mc);for(BlockPos at:BlockPos.getAllInBox(POS.add(-3,-2,-3),POS.add(3,4,3)))w.setBlockToAir(at);w.setBlockState(POS,blocks.get(scenario).getDefaultState(),3);tile(mc).setSurfaceTexture(slot(),TileEntityAnimatedScreenSelector.VEHICLE_SURFACE);p.setPositionAndUpdate(POS.getX()+.5,POS.getY(),POS.getZ()-2);
        }).get();stage=0;frame=0;
    }
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void mouse(GuiScreen gui,int x,int y)throws Exception{Method m;try{m=GuiScreen.class.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);}catch(NoSuchMethodException e){m=GuiScreen.class.getDeclaredMethod("func_73864_a",int.class,int.class,int.class);}m.setAccessible(true);m.invoke(gui,x,y,0);}
    private static void button(GuiScreen gui,int id)throws Exception{List<GuiButton> list=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");for(GuiButton b:list)if(b.id==id){require(b.enabled,"enabled picker tab/button "+id);mouse(gui,b.x+b.width/2,b.y+b.height/2);return;}throw new IllegalStateException("missing screen-mode button "+id);}
    private static void choose(Minecraft mc,int sentinel)throws Exception{
        GuiScreen gui=mc.currentScreen;if(slot()==1)button(gui,91);
        String name=gui instanceof GuiProgrammableHalfConsole?(slot()==1?"topList":"bottomList"):slot()==1?"inputList":"screenList";
        HousingTextureList list=(HousingTextureList)field(field(gui,name),"list");List<?> rows=(List<?>)field(list,"rows");int index=rows.indexOf(sentinel),count=(Integer)field(list,"count");require(index>=0,"picker contains requested live surface");int scroll=Math.max(0,index-count+1);Field f=HousingTextureList.class.getDeclaredField("scroll");f.setAccessible(true);f.setInt(list,scroll);mouse(gui,(Integer)field(list,"x")+4,(Integer)field(list,"y")+(index-scroll)*HousingTextureList.ROW_HEIGHT+10);
    }
    private static void open(Minecraft mc)throws Exception{mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=player(mc);p.openGui(VandorLabs.instance,GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR,world(mc),POS.getX(),POS.getY(),POS.getZ());}).get();}
    private static boolean picker(Minecraft mc){return mc.currentScreen instanceof GuiAnimatedScreenSelector||mc.currentScreen instanceof GuiProgrammableInput||mc.currentScreen instanceof GuiProgrammableHalfConsole;}
    private static void next(){stage++;frame=0;}
    public static boolean tick(Minecraft mc,File output)throws Exception{
        require(++total<1800,"bounded actual mode-transition timeout");frame++;
        if(stage==0&&frame>12){if(mc.world.getBlockState(POS).getBlock()!=blocks.get(scenario)||mc.player.getHeldItemMainhand().getItem()!=ModItems.CONFIGURIZER)return false;open(mc);next();return false;}
        if(stage==1){require(frame<100,"Status picker did not open");if(!picker(mc)||frame<5)return false;choose(mc,-100001);next();return false;}
        if(stage==2){require(frame<100,"Redstone child did not open");if(!(mc.currentScreen instanceof GuiRedstoneScreen)||frame<5)return false;mc.getIntegratedServer().addScheduledTask(()->require(tile(mc).hasRedstoneScreen(slot())&&!tile(mc).hasVehicleStatus(slot()),"actual picker packet replaces server Status mode")).get();TileEntityAnimatedScreenSelector client=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(POS);require(client.hasRedstoneScreen(slot())&&!client.hasVehicleStatus(slot()),"client receives effective Redstone renderer mode");VehicleTaxiRuntimeChecks.screenshot(mc,output,"shot_screen_status_to_redstone_"+scenario+".png");button(mc.currentScreen,4);next();return false;}
        if(stage==3&&frame>8){open(mc);next();return false;}
        if(stage==4){require(frame<100,"return picker did not open");if(!picker(mc)||frame<5)return false;choose(mc,-100002);next();return false;}
        if(stage==5&&frame>8){mc.getIntegratedServer().addScheduledTask(()->require(tile(mc).hasVehicleStatus(slot())&&!tile(mc).hasRedstoneScreen(slot()),"actual reverse picker packet restores exclusive Status mode")).get();TileEntityAnimatedScreenSelector client=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(POS);require(client.hasVehicleStatus(slot())&&!client.hasRedstoneScreen(slot()),"client receives exclusive Status mode after reverse transition");if(++scenario==blocks.size()){mc.player.closeScreen();System.out.println("[vandorlabs][reprolab] screen-mode-client PASS actual-picker-mouse Status-Redstone-Status all-screen-input-console-slots top-half-console network-sync screenshots");return true;}prepare(mc);}
        return false;
    }
}

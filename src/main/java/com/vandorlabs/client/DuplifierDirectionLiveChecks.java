package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** Real item opening, option packets, reverse clicks, rendered pages and grouped apply. */
final class DuplifierDirectionLiveChecks {
    private static int ticks,phase;
    static boolean tick(Minecraft mc,File output)throws Exception {
        mc.gameSettings.pauseOnLostFocus=false;if(++ticks%20!=0)return false;
        if(phase==0){mc.player.closeScreen();mc.playerController.setGameType(GameType.CREATIVE);server(mc,()->{
            EntityPlayerMP p=player(mc);p.interactionManager.setGameType(GameType.CREATIVE);p.inventory.currentItem=0;
            p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.DUPLIFIER));p.capabilities.isFlying=true;p.sendPlayerAbilities();
            p.connection.setPlayerLocation(240.5,91,243,180,0);p.inventoryContainer.detectAndSendChanges();
        });phase++;return false;}
        if(phase==1){boolean ready=mc.player.isCreative()&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().getItem()==ModItems.DUPLIFIER;
            if(!ready){require(ticks<240,"sync phase="+phase+" mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand());return false;}
            mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);phase++;return false;}
        require(mc.currentScreen instanceof GuiDuplifier,"dialog did not open phase="+phase);
        GuiDuplifier gui=(GuiDuplifier)mc.currentScreen;
        if(phase==2){require(!DuplifierApplyOptions.ignoreDirectionality(mc.player.getHeldItemMainhand()),"new tool defaults");click(gui,98,0);click(gui,96,0);}
        if(phase==3){check(mc,true);DuplifierRuntimeChecks.checkDialog(gui);for(int page=0;page<DuplifierApplyOptions.PAGES.length;page++){
            gui.actionPerformed(button(gui,page));GuiButton left=button(gui,98),right=button(gui,96);
            require(left.y==right.y&&right.x>=left.x+left.width+8&&left.width==right.width,"top toggles are not spaced columns");
            require(mc.fontRenderer.getStringWidth(left.displayString)<=left.width-12&&mc.fontRenderer.getStringWidth(right.displayString)<=right.width-12,"top toggle text clipped");
            snapshot(mc,gui,output,"duplifier-direction-page-"+page);
        }}
        if(phase==4){check(mc,true);click(gui,96,1);}
        if(phase==5){check(mc,false);mc.player.closeScreen();mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);}
        if(phase==6){require(button(gui,96).displayString.endsWith("Off"),"reopened direction option lost state");snapshot(mc,gui,output,"duplifier-direction-off");click(gui,96,1);}
        if(phase==7){check(mc,true);mc.player.closeScreen();server(mc,()->{DuplifierRuntimeChecks.run(player(mc));worldChecks(player(mc));});DuplifierMaskRuntimeChecks.run();System.out.println("[vandorlabs][reprolab] duplifier-direction-live PASS real item opening held/container packets toggle persistence reverse clicks five rendered pages cube/slab/stairs strict/ignored mounting data undo adjacent connected regressions");return true;}
        phase++;return false;
    }
    private static void worldChecks(EntityPlayerMP player){
        World world=player.world;BlockPos origin=new BlockPos(240,100,240);
        for(Block block:new Block[]{ModBlocks.PROGRAMMABLE_BLOCK,ModBlocks.PROGRAMMABLE_SLAB,ModBlocks.PROGRAMMABLE_STAIRS})for(boolean ignore:new boolean[]{false,true}){
            int count=block==ModBlocks.PROGRAMMABLE_BLOCK?6:block==ModBlocks.PROGRAMMABLE_SLAB?2:8;
            Map<BlockPos,IBlockState> states=new LinkedHashMap<>();
            for(int i=0;i<count;i++){
                BlockPos pos=origin.east(i);world.setBlockToAir(pos);IBlockState state=block.getDefaultState();
                if(block==ModBlocks.PROGRAMMABLE_BLOCK)state=state.withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.values()[i]);
                else if(block==ModBlocks.PROGRAMMABLE_SLAB)state=state.withProperty(BlockProgrammableSlab.HALF,i==0?BlockSlab.EnumBlockHalf.BOTTOM:BlockSlab.EnumBlockHalf.TOP);
                else state=state.withProperty(BlockStairs.FACING,EnumFacing.HORIZONTALS[i%4]).withProperty(BlockStairs.HALF,i<4?BlockStairs.EnumHalf.BOTTOM:BlockStairs.EnumHalf.TOP);
                world.setBlockState(pos,state,2);states.put(pos,state);
                if(block==ModBlocks.PROGRAMMABLE_BLOCK){TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);tile.setCeilingMounted((i&1)!=0);tile.setWallPosition(i%3);tile.setCeilingPosition(i%3);}
            }
            ItemStack tool=new ItemStack(ModItems.DUPLIFIER);DuplifierApplyOptions.setIgnoreDirectionality(tool,ignore);
            NBTTagCompound copied=new NBTTagCompound();copied.setInteger(ProgrammableSettings.WALL_TEXTURE,4);tool.setTagInfo(ItemDuplifier.SETTINGS_TAG,copied);
            DuplifierUndo.begin(player,world);int changed=DuplifierConnectedApply.apply(world,origin,tool,player,EnumFacing.UP);DuplifierUndo.finish(player);
            require(changed==(ignore?count:1),"group selection "+block+" ignore="+ignore+" count="+changed);
            int index=0;for(Map.Entry<BlockPos,IBlockState> entry:states.entrySet()){
                require(world.getBlockState(entry.getKey()).equals(entry.getValue()),"changed target orientation "+block);
                require(((TileEntityAnimatedScreenSelector)world.getTileEntity(entry.getKey())).getHousingTexture()==(ignore||index++==0?4:0),"group texture "+block);
            }
            require(DuplifierUndo.undo(player),"group undo failed "+block);
            for(BlockPos pos:states.keySet()){require(((TileEntityAnimatedScreenSelector)world.getTileEntity(pos)).getHousingTexture()==0,"group undo texture");world.setBlockToAir(pos);}
        }
    }
    private static void check(Minecraft mc,boolean ignore)throws Exception{server(mc,()->{
        ItemStack tool=player(mc).getHeldItemMainhand();require(DuplifierApplyOptions.connected(tool)&&DuplifierApplyOptions.ignoreDirectionality(tool)==ignore,"server flag round trip");
        ItemStack saved=new ItemStack(tool.writeToNBT(new NBTTagCompound()));require(DuplifierApplyOptions.ignoreDirectionality(saved)==ignore,"saved flag");
    });}
    @SuppressWarnings("unchecked") private static GuiButton button(GuiDuplifier gui,int id){List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");return buttons.stream().filter(b->b.id==id).findFirst().get();}
    private static void click(GuiDuplifier gui,int id,int mouse)throws Exception{GuiButton b=button(gui,id);gui.mouseClicked(b.x+b.width/2,b.y+b.height/2,mouse);}
    private static void snapshot(Minecraft mc,GuiDuplifier gui,File output,String name)throws Exception{mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();mc.entityRenderer.disableLightmap();net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);gui.drawScreen(-1,-1,0);ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,name+".png"));}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static void server(Minecraft mc,Runnable work)throws Exception{mc.getIntegratedServer().addScheduledTask(work).get();}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Duplifier direction live: "+message);}
    private DuplifierDirectionLiveChecks(){}
}

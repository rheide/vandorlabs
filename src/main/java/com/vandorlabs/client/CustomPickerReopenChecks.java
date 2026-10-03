package com.vandorlabs.client;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.GuiHandler;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Reopen actual authorized containers and verify the Custom row remains visible/selected. */
final class CustomPickerReopenChecks {
    private final BlockPos pos;
    private int type,stage,wait;
    private static final String[] NAMES={"block","slab","door","trapdoor"};
    CustomPickerReopenChecks(BlockPos pos){this.pos=pos;}
    private int guiId(){return type==2?GuiHandler.GUI_SPACE_DOOR:type==3?GuiHandler.GUI_PROGRAMMABLE_TRAPDOOR:GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR;}
    boolean tick(Minecraft mc) {
        if(wait-->0)return false;
        int choice=CustomBlockMaterials.choice(new ItemStack(Blocks.STONE));
        if(stage==0) {
            final Block block=type==0?ModBlocks.PROGRAMMABLE_BLOCK:type==1?ModBlocks.PROGRAMMABLE_SLAB:type==2?Block.getBlockFromName("vandorlabs:programmable_door"):ModBlocks.PROGRAMMABLE_TRAPDOOR;
            mc.getIntegratedServer().addScheduledTask(()->{
                World world=mc.getIntegratedServer().getWorld(0);world.setBlockToAir(pos);
                for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)world.setBlockState(pos.add(x,-1,z),Blocks.STONE.getDefaultState(),3);
                world.setBlockState(pos,block.getDefaultState(),3);
                TileEntity tile=world.getTileEntity(pos);
                if(tile instanceof TileEntityAnimatedScreenSelector)((TileEntityAnimatedScreenSelector)tile).setHousingTexture(choice);
                else if(tile instanceof TileEntitySpaceDoor)((TileEntitySpaceDoor)tile).setFaceTexture(choice);
                else if(tile instanceof TileEntityProgrammableTrapdoor)((TileEntityProgrammableTrapdoor)tile).configure(choice,0,false,0,0);
                else throw new IllegalStateException("Custom reopen fixture tile missing");
                EntityPlayerMP owner=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());owner.connection.setPlayerLocation(pos.getX()+.5,pos.getY(),pos.getZ()-2,0,0);
            });stage=1;wait=30;return false;
        }
        if(stage==1 || stage==3) {
            final int id=guiId();mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP owner=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());owner.openGui(VandorLabs.instance,id,owner.world,pos.getX(),pos.getY(),pos.getZ());
            });stage++;wait=20;return false;
        }
        check(mc.currentScreen,choice);
        if(stage==2){mc.player.closeScreen();stage=3;wait=10;return false;}
        try {
            java.awt.image.BufferedImage shot=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            javax.imageio.ImageIO.write(shot,"png",new java.io.File(System.getProperty("vandorlabs.reprolab"),"shot_custom_"+NAMES[type]+"_texture_gui.png"));
        } catch(java.io.IOException e){throw new IllegalStateException("Custom reopen capture failed",e);}
        System.out.println("[vandorlabs][reprolab] custom-picker-reopen-runtime PASS "+NAMES[type]);
        mc.player.closeScreen();stage=0;wait=10;return ++type==NAMES.length;
    }
    private void check(GuiScreen gui,int choice) {
        try {
            String field=type==3?"textures":"textureList";
            if(gui==null)throw new IllegalStateException("Custom "+NAMES[type]+" container did not reopen (stage "+stage+")");
            java.lang.reflect.Field picker=gui.getClass().getDeclaredField(field);picker.setAccessible(true);HousingTextureList list=(HousingTextureList)picker.get(gui);
            if(list.selected()!=choice)throw new IllegalStateException("reopened dialog lost Custom material value");
            java.lang.reflect.Field selected=HousingTextureList.class.getDeclaredField("selected"),scroll=HousingTextureList.class.getDeclaredField("scroll"),rows=HousingTextureList.class.getDeclaredField("rows"),count=HousingTextureList.class.getDeclaredField("count");
            for(java.lang.reflect.Field f:new java.lang.reflect.Field[]{selected,scroll,rows,count})f.setAccessible(true);
            java.util.List<?> visible=(java.util.List<?>)rows.get(list);int row=visible.indexOf(selected.getInt(list));
            if(selected.getInt(list)!=-100000 || row<scroll.getInt(list) || row>=scroll.getInt(list)+count.getInt(list))throw new IllegalStateException("reopened Custom selection is hidden or highlights a built-in material");
        } catch(ReflectiveOperationException e){throw new IllegalStateException("Custom reopen picker inspection failed",e);}
    }
}

package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.*;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;

/** Real material-picker clicks, server save checks, atlas variants and captures. */
final class MaterialFinishRuntimeChecks {
    private static final BlockPos POS=new BlockPos(240,90,240);
    private static final String[] FINISHES={"satin_silver","cool_steel","pale_titanium"};
    private static final boolean FILESYSTEM_ONLY=Boolean.getBoolean("vandorlabs.filesystemDiscoveryChecksOnly");
    private static int ticks,phase,finish;
    static boolean tick(Minecraft mc,File output)throws Exception {
        mc.gameSettings.pauseOnLostFocus=false;
        if(++ticks%20!=0)return false;
        if(phase==4&&FILESYSTEM_ONLY){mc.player.closeScreen();System.out.println("[vandorlabs][reprolab] filesystem-discovery-live PASS actual category row click server packet client synchronization saved-ID reload three configured models");return true;}
        if(phase==4){if(!CubeDialogRuntimeChecks.tick(mc,output,POS))return false;mc.player.closeScreen();System.out.println("[vandorlabs][reprolab] material-finishes-live PASS three Materials picker rows real save packets client synchronization NBT reload nine configured block/slab/stairs models screenshots");return true;}
        if(phase==0){
            if(!FILESYSTEM_ONLY)PbrMaterialRuntimeChecks.run(mc);
            mc.player.closeScreen();mc.playerController.setGameType(GameType.CREATIVE);if(FILESYSTEM_ONLY)mc.player.inventory.currentItem=0;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=player(mc);player.interactionManager.setGameType(GameType.CREATIVE);
                if(!FILESYSTEM_ONLY)TextureJoinItemRuntimeChecks.run(player,POS.add(5,10,0));
                player.inventory.currentItem=0;player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));
                player.capabilities.isFlying=true;player.sendPlayerAbilities();
                player.world.setBlockState(POS,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);
                player.connection.setPlayerLocation(POS.getX()+.5,POS.getY()+1,POS.getZ()+3,180,0);
                player.inventoryContainer.detectAndSendChanges();
            }).get();phase++;return false;
        }
        if(phase==1){
            boolean ready=mc.player.isCreative()&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().getItem()==ModItems.CONFIGURIZER&&mc.world.getTileEntity(POS)!=null;
            if(FILESYSTEM_ONLY)ready &= mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.getDistanceSq(POS)<=16;
            if(!ready){require(ticks<240,"fixture synchronization stage="+phase+" mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPosition()+" tile="+mc.world.getTileEntity(POS));return false;}
            mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=player(mc);p.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR,p.world,POS.getX(),POS.getY(),POS.getZ());}).get();
            phase++;return false;
        }
        require(mc.currentScreen instanceof GuiProgrammableWall,"picker did not open stage="+phase);
        GuiProgrammableWall gui=(GuiProgrammableWall)mc.currentScreen;
        int choice=FILESYSTEM_ONLY?FilesystemTextures.identifier("Reference/portrait.png"):ScreenHousingTextures.screenIndex("materials/"+FINISHES[finish]);
        require(choice>0&&(FILESYSTEM_ONLY?"Reference":"Materials").equals(ScreenHousingTextures.category(choice)),"material category/choice missing");
        if(phase==2){
            HousingTextureList picker=gui.materialPicker();picker.setSelected(choice);
            // Reveal its category/row without preselecting the value that the
            // real click must change and send to the server.
            field("selected").setInt(picker,((TileEntityAnimatedScreenSelector)mc.world.getTileEntity(POS)).getHousingTexture());
            Field rows=field("rows"),scroll=field("scroll"),x=field("x"),y=field("y");
            @SuppressWarnings("unchecked") List<Integer> values=(List<Integer>)rows.get(picker);
            int row=values.indexOf(FILESYSTEM_ONLY?ScreenHousingTextures.localIndex(choice):choice);require(row>=0,"material omitted from picker");
            gui.mouseClicked(x.getInt(picker)+8,y.getInt(picker)+(row-scroll.getInt(picker))*HousingTextureList.ROW_HEIGHT+8,0);
            phase++;return false;
        }
        mc.getIntegratedServer().addScheduledTask(()->{
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)player(mc).world.getTileEntity(POS);
            require(tile.getHousingTexture()==choice,"real picker packet did not save "+(FILESYSTEM_ONLY?"filesystem portrait":FINISHES[finish]));
            TileEntityAnimatedScreenSelector saved=new TileEntityAnimatedScreenSelector();saved.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
            require(saved.getHousingTexture()==choice,"material save/reload changed choice");
        }).get();
        require(((TileEntityAnimatedScreenSelector)mc.world.getTileEntity(POS)).getHousingTexture()==choice,"client choice did not synchronize");
        if(FILESYSTEM_ONLY){
            net.minecraft.block.state.IBlockState state=mc.world.getBlockState(POS);
            net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);boolean textured=false;
            for(net.minecraft.client.renderer.block.model.BakedQuad quad:model.getQuads(state.getBlock().getExtendedState(state,mc.world,POS),null,0))if(quad.getSprite().getIconName().equals(ScreenHousingTextures.fullTexture(choice)))textured=true;
            require(textured,"discovered filesystem artwork missing from actual world model");
            require(Math.abs(UnifiedTextureSprites.aspect(mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(choice)))-.5)<1e-5,"filesystem portrait lost authored aspect");
        }
        for(net.minecraft.block.Block block:new net.minecraft.block.Block[]{ModBlocks.PROGRAMMABLE_BLOCK,ModBlocks.PROGRAMMABLE_SLAB,ModBlocks.PROGRAMMABLE_STAIRS}){
            ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();tag.setInteger("housingTexture",choice);stack.setTagInfo("BlockEntityTag",tag);
            net.minecraft.client.renderer.block.model.IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);
            boolean textured=false;
            for(int side=-1;side<6;side++)for(net.minecraft.client.renderer.block.model.BakedQuad quad:model.getQuads(null,side<0?null:net.minecraft.util.EnumFacing.getFront(side),0))
                if(quad.getSprite().getIconName().equals(ScreenHousingTextures.fullTexture(choice)))textured=true;
            require(textured,"configured item rendered material "+block);
        }
        mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();mc.entityRenderer.disableLightmap();net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);gui.drawScreen(-1,-1,0);
        ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"material-"+(FILESYSTEM_ONLY?"filesystem-portrait":FINISHES[finish])+".png"));
        if(++finish==(FILESYSTEM_ONLY?1:FINISHES.length)){phase=4;return false;}
        phase=2;return false;
    }
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static Field field(String name)throws Exception{Field field=HousingTextureList.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Material finishes: "+message);}
    private MaterialFinishRuntimeChecks(){}
}

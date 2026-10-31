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

/** Full-screen taxonomy, old saved identities, real picker clicks and actual animation decoding. */
final class ScreenCatalogRuntimeChecks {
    private static final BlockPos POS=new BlockPos(240,90,240);
    private static final String[] IDS={"company_pinnacle_full_biology","company_paladyne_full_medical","company_seyr_full_reactor","company_seyr_full_communications","company_seyr_full_navigation","specialist_bare_sensors","analysis_bare_surface_map","cruiser_command_left","ship_freighter","bridge_bare_ship_status"};
    private static final String[] HALF={"console_inputs/company_pinnacle_half_biology_static","console_inputs/company_paladyne_half_medical_static","console_inputs/company_seyr_half_communications_static","console_inputs/company_seyr_half_navigation_static","console_inputs/company_seyr_half_reactor_static","console_inputs/specialist_bare_sensors_static","console_inputs/analysis_bare_station_static","console_inputs/analysis_bare_targeting_static","console_inputs/bridge_bare_pilot_static","console_inputs/analysis_framed_surface_map_static","console_inputs/input_surfaces_keyboard_static","controls27/controls27_half_height_01_reactor_bottom","controls27/controls27_corporate_pinnacle_01_keyboard","controls27/controls27_dense_pinnacle_01_engineering_faders","controls27/controls27_dense_pinnacle_03_navigation_bottom","controls27/controls27_half_height_six_faders","controls27/controls27_corporate_pinnacle_05_three_alignment_faders","controls27/controls27_corporate_seyr_06_selection_wheel_slider"};
    private static int ticks,phase,fixture;private static boolean done;
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(done)return true;
        mc.gameSettings.pauseOnLostFocus=false;if(++ticks%20!=0)return false;
        if(phase==0){
            aliases(mc);CompanyScreenLayerRuntimeChecks.run(mc,output);
            mc.player.closeScreen();mc.playerController.setGameType(GameType.CREATIVE);
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=player(mc);p.interactionManager.setGameType(GameType.CREATIVE);p.inventory.currentItem=0;p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));p.capabilities.isFlying=true;p.sendPlayerAbilities();
                p.world.setBlockState(POS,ModBlocks.PROGRAMMABLE_CONSOLE.getDefaultState(),2);p.connection.setPlayerLocation(POS.getX()+.5,POS.getY()+1,POS.getZ()+3,180,0);p.inventoryContainer.detectAndSendChanges();
            }).get();phase++;return false;
        }
        if(phase==1){
            boolean ready=mc.player.isCreative()&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().getItem()==ModItems.CONFIGURIZER&&mc.world.getTileEntity(POS)!=null;
            if(!ready){require(ticks<240,"fixture synchronization stage="+phase+" mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPosition());return false;}
            mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=player(mc);p.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR,p.world,POS.getX(),POS.getY(),POS.getZ());}).get();phase++;return false;
        }
        require(mc.currentScreen instanceof GuiAnimatedScreenSelector,"screen picker did not open phase="+phase);GuiAnimatedScreenSelector gui=(GuiAnimatedScreenSelector)mc.currentScreen;
        boolean half=fixture>=IDS.length;String path=half?HALF[fixture-IDS.length]:IDS[fixture]+"_static";String expected=path.startsWith("controls27/")?null:path.replaceFirst("^console_inputs/","").replaceFirst("_static$","");int slot=half?1:0;
        int choice=ScreenHousingTextures.screenIndex(path);require(choice>0&&ScreenHousingTextures.visible(choice),"active display/control fixture missing "+path);
        if(phase==2){
            Field tab=GuiAnimatedScreenSelector.class.getDeclaredField("textureTab");tab.setAccessible(true);
            if(tab.getInt(gui)!=(half?1:0)){
                java.util.List<net.minecraft.client.gui.GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreen.class,gui,"buttonList","field_146292_n");
                net.minecraft.client.gui.GuiButton b=buttons.stream().filter(value->value.id==(half?91:90)).findFirst().get();gui.mouseClicked(b.x+4,b.y+4,0);
            }
            Field nativeField=GuiAnimatedScreenSelector.class.getDeclaredField(half?"inputList":"screenList");nativeField.setAccessible(true);Field listField=ScreenTextureList.class.getDeclaredField("list");listField.setAccessible(true);HousingTextureList picker=(HousingTextureList)listField.get(nativeField.get(gui));
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(POS);int previous=tile.getSurfaceTexture(slot);if(previous<0)previous=ScreenHousingTextures.screenIndex((half?"console_inputs/"+tile.getInputPanel():tile.getSelectedScreen())+"_static");picker.setSelected(choice);field("selected").setInt(picker,previous);
            @SuppressWarnings("unchecked") List<Integer> rows=(List<Integer>)field("rows").get(picker);int row=rows.indexOf(choice);require(row>=0,"display/control omitted from its folder");
            gui.mouseClicked(field("x").getInt(picker)+8,field("y").getInt(picker)+(row-field("scroll").getInt(picker))*HousingTextureList.ROW_HEIGHT+8,0);phase++;return false;
        }
        mc.getIntegratedServer().addScheduledTask(()->{
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)player(mc).world.getTileEntity(POS);selected(tile,half,choice,expected);
            TileEntityAnimatedScreenSelector loaded=new TileEntityAnimatedScreenSelector();loaded.readFromNBT(tile.writeToNBT(new NBTTagCompound()));selected(loaded,half,choice,expected);
        }).get();
        selected((TileEntityAnimatedScreenSelector)mc.world.getTileEntity(POS),half,choice,expected);
        mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();mc.entityRenderer.disableLightmap();net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);gui.drawScreen(-1,-1,0);
        ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"screen-catalog-"+path.replace('/','_')+".png"));
        if(++fixture==IDS.length+HALF.length){mc.player.closeScreen();done=true;System.out.println("[vandorlabs][reprolab] screen-catalog-live PASS Displays Gold Cool Classic Ship both heights separate company/Classic controls actual clicks packets save/reload original Seyr animations screenshots");return true;}
        phase=2;return false;
    }
    private static void selected(TileEntityAnimatedScreenSelector tile,boolean half,int choice,String expected){
        if(expected==null)require(tile.getSurfaceTexture(half?1:0)==choice,"static control selection did not save "+choice);
        else require((half?tile.getInputPanel():tile.getSelectedScreen()).equals(expected)&&tile.getSurfaceTexture(half?1:0)<0,"native display selection did not save "+expected);
    }
    private static void aliases(Minecraft mc){
        int count=0;for(int i=0;i<ScreenHousingTextures.IDS.length;i++){
            com.google.gson.JsonObject entry=ScreenHousingTextures.entry(i);if(entry==null||!"Screens".equals(ScreenHousingTextures.category(i))||!entry.has("retireAnimation"))continue;
            String source=entry.get("source").getAsString();boolean half=source.startsWith("console_inputs/");String old=source.replaceFirst("^console_inputs/","").replaceFirst("_static$","");
            require(!ScreenHousingTextures.visible(i),"retired display remains listed");if(!half)require(!ModBlocks.DISPLAY_SCREEN_IDS.contains(old),"retired full display remains selectable");if(!entry.has("alias"))continue;
            int target=ScreenHousingTextures.clamp(i);require(target!=i&&ScreenHousingTextures.visible(target),"saved numeric choice did not resolve");String replacement=ScreenHousingTextures.entry(target).get("source").getAsString().replaceFirst("^console_inputs/","").replaceFirst("_static$","");
            TileEntityAnimatedScreenSelector seed=new TileEntityAnimatedScreenSelector();seed.frameAppearance.artworkVersion=1;if(half){seed.setInputPanel(old);seed.setSecondaryInputPanel(old);}else seed.setSelectedScreen(old);
            NBTTagCompound saved=seed.writeToNBT(new NBTTagCompound());TileEntityAnimatedScreenSelector loaded=new TileEntityAnimatedScreenSelector();loaded.readFromNBT(saved);NBTTagCompound after=loaded.writeToNBT(new NBTTagCompound());
            require((half?loaded.getInputPanel():loaded.getSelectedScreen()).equals(replacement),"saved display identity did not resolve "+old);require(after.getString(half?"inputPanel":"selectedScreen").equals(old),"use-time alias rewrote original snapshot identity");
            if(half){require(loaded.getSecondaryInputPanel().equals(replacement)&&after.getString("secondaryInputPanel").equals(old),"secondary half identity changed");require(mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/console_inputs/"+replacement+"_off")!=mc.getTextureMapBlocks().getMissingSprite(),"half display OFF atlas sprite missing");}count++;
        }
        require(count==46,"expected 42 company and four Bridge Sensors redirects: "+count);System.out.println("[vandorlabs][reprolab] screen-catalog-aliases PASS saved-identities="+count+" both heights stable numeric choices original snapshot NBT retained");
    }
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static Field field(String name)throws Exception{Field f=HousingTextureList.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Screen catalog: "+message);}
}

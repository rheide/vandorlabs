package com.vandorlabs.client;

import java.io.File;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;

final class GlassVisualChecks {
    private static int ticks;
    static boolean tick(Minecraft mc,File output) throws Exception {
        ticks++;mc.gameSettings.pauseOnLostFocus=false;
        if(ticks==1){mc.displayGuiScreen(null);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.gameSettings.renderDistanceChunks=4;}
        if(ticks==80) {
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                MaterialRuntimeChecks.run(player);GlassAppearanceRuntimeChecks.run(player);
                JoinedGlassRuntimeChecks.run(player);JoinedGlassRuntimeChecks.buildGallery(player.world);
                GlassAppearanceRuntimeChecks.buildGallery(player.world);
                player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();player.connection.setPlayerLocation(45,7,33,0,20);
            }).get();
            mc.player.capabilities.allowFlying=true;mc.player.capabilities.isFlying=true;mc.player.setPositionAndRotation(45,7,33,0,20);
            mc.gameSettings.hideGUI=true;
        }
        if(ticks==140) {
            GlassJoinContourChecks.run(mc,output);GlassInsertLightingChecks.run(mc,output);TrapdoorFrameChecks.run(mc,output);
            RuntimeResourceChecks.animations(mc);
            GlassAppearanceRuntimeChecks.checkRenderMeshes(mc.world,output);save(mc,output,"glass-strengths-and-frames");
            mc.gameSettings.hideGUI=false;
            openDialog(mc,new BlockPos(42,5,40),com.vandorlabs.GuiHandler.GUI_PROGRAMMABLE_GLASS);
        }
        if(ticks==160) {
            checkDialog(mc,false);ProgrammableDialogRuntimeChecks.check(mc);
            ((GuiProgrammableGlass)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(4,0,0,""));
            ((GuiProgrammableGlass)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(5,0,0,""));
            ((GuiProgrammableGlass)mc.currentScreen).choose(0);
            ((GuiProgrammableGlass)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(6,0,0,""));
        }
        if(ticks==180) {
            checkDialog(mc,false);ProgrammableDialogRuntimeChecks.check(mc);save(mc,output,"glass-regular-settings");
            openDialog(mc,new BlockPos(42,5,43),com.vandorlabs.GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR);
        }
        if(ticks==200) {
            checkDialog(mc,true);ProgrammableDialogRuntimeChecks.check(mc);
            ((GuiProgrammableWall)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(113,0,0,""));
            ((GuiProgrammableWall)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(114,0,0,""));
            ((GuiProgrammableWall)mc.currentScreen).choose(0);
            ((GuiProgrammableWall)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(115,0,0,""));
            ((GuiProgrammableWall)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(116,0,0,""));
        }
        if(ticks==220) {
            checkDialog(mc,true);ProgrammableDialogRuntimeChecks.check(mc);save(mc,output,"glass-diagonal-settings");mc.player.closeScreen();mc.gameSettings.hideGUI=true;
            mc.getIntegratedServer().addScheduledTask(()->mc.getIntegratedServer().getPlayerList().getPlayers().get(0).connection.setPlayerLocation(45.5,5,47,0,30)).get();
            mc.player.setPositionAndRotation(45.5,5,47,0,30);
        }
        if(ticks==250){
            save(mc,output,"glass-bare-joined-edges");
            mc.getIntegratedServer().addScheduledTask(()->mc.getIntegratedServer().getPlayerList().getPlayers().get(0).connection.setPlayerLocation(41,7,60,-35,25)).get();
            mc.player.setPositionAndRotation(41,7,60,-35,25);
        }
        if(ticks==280){save(mc,output,"glass-w-joined-edges");camera(mc,57.5,8,41,0,42);}
        if(ticks==310){save(mc,output,"glass-closed-frame-top-regular-diagonal");camera(mc,57.5,3.5,41,0,-21);}
        if(ticks==340){save(mc,output,"glass-closed-frame-bottom-regular-diagonal");camera(mc,61,6,43,67,16);}
        if(ticks==370){save(mc,output,"glass-closed-frame-side-regular-diagonal");
            mc.getIntegratedServer().addScheduledTask(()->{
                for(int type=0;type<2;type++)for(int tint=0;tint<4;tint++){
                    BlockPos p=new BlockPos(70+(2-tint)*3,5,40+type*4);
                    for(int dx=0;dx<2;dx++)for(int dy=0;dy<2;dy++){
                        BlockPos cell=p.add(dx,dy,0);mc.getIntegratedServer().getEntityWorld().setBlockState(cell,(type==0?net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_glass"):com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS).getDefaultState(),3);
                        net.minecraft.nbt.NBTTagCompound appearance=new net.minecraft.nbt.NBTTagCompound();appearance.setInteger(com.vandorlabs.items.ProgrammableSettings.GLASS_MATERIAL,0);appearance.setInteger(com.vandorlabs.items.ProgrammableSettings.GLASS_TINT,tint);
                        com.vandorlabs.items.ProgrammableSettings.apply(mc.getIntegratedServer().getEntityWorld(),cell,appearance);
                        mc.getIntegratedServer().getEntityWorld().setBlockState(cell.south(2),net.minecraft.init.Blocks.GOLD_BLOCK.getDefaultState(),3);
                    }
                }
            }).get();camera(mc,73.5,9,33,0,24);
        }
        if(ticks==410){save(mc,output,"glass-material-opacity");camera(mc,92.5,7,32,0,18);}
        if(ticks==440){JoinedGlassRuntimeChecks.renderChecks(mc.world);save(mc,output,"glass-joined-tile-stretch");camera(mc,92.5,7,40,0,18);}
        if(ticks==470){save(mc,output,"diagonal-glass-joined-tile-stretch");mc.gameSettings.hideGUI=false;openDialog(mc,new BlockPos(106,5,56),com.vandorlabs.GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR);}
        if(ticks==490){GuiProgrammableWall gui=(GuiProgrammableWall)mc.currentScreen;gui.actionPerformed(new net.minecraft.client.gui.GuiButton(129,0,0,""));ProgrammableDialogRuntimeChecks.check(mc);gui.choose(HousingTextureList.CANOPY_GLASS_BASE+18);gui.actionPerformed(new net.minecraft.client.gui.GuiButton(113,0,0,""));}
        if(ticks==510){checkWindowDialog(mc,new BlockPos(106,5,56));save(mc,output,"porthole-glass-settings");openDialog(mc,new BlockPos(106,5,64),com.vandorlabs.GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR);}
        if(ticks==530){GuiProgrammableWall gui=(GuiProgrammableWall)mc.currentScreen;gui.actionPerformed(new net.minecraft.client.gui.GuiButton(129,0,0,""));gui.choose(0);gui.actionPerformed(new net.minecraft.client.gui.GuiButton(115,0,0,""));gui.actionPerformed(new net.minecraft.client.gui.GuiButton(113,0,0,""));ProgrammableDialogRuntimeChecks.check(mc);}
        if(ticks==550){checkWindowDialog(mc,new BlockPos(106,5,64));save(mc,output,"diagonal-porthole-glass-settings");mc.player.closeScreen();mc.gameSettings.hideGUI=true;camera(mc,92.5,7,48,0,18);}
        if(ticks==580){save(mc,output,"porthole-joined-tile-stretch");camera(mc,92.5,7,56,0,18);}
        if(ticks==610){save(mc,output,"diagonal-porthole-joined-tile-stretch");camera(mc,143,6.5,34.5,14,15);}
        if(ticks==650){save(mc,output,"porthole-octagon-wall-pipes");mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            net.minecraft.block.Block[] blocks={net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_door"),com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_TRAPDOOR};
            for(int i=0;i<blocks.length;i++){BlockPos pos=new BlockPos(150+i*4,5,40);player.world.setBlockState(pos.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);player.world.setBlockState(pos,blocks[i].getDefaultState(),3);blocks[i].onBlockPlacedBy(player.world,pos,player.world.getBlockState(pos),player,new net.minecraft.item.ItemStack(blocks[i]));}
        }).get();mc.gameSettings.hideGUI=false;openDialog(mc,new BlockPos(150,5,40),com.vandorlabs.GuiHandler.GUI_SPACE_DOOR);}
        if(ticks==670){if(!(mc.currentScreen instanceof GuiSpaceDoor))throw new IllegalStateException("shared door picker did not open");ProgrammableDialogRuntimeChecks.check(mc);openDialog(mc,new BlockPos(154,5,40),com.vandorlabs.GuiHandler.GUI_PROGRAMMABLE_TRAPDOOR);}
        if(ticks==690){if(!(mc.currentScreen instanceof GuiProgrammableTrapdoor))throw new IllegalStateException("shared trapdoor picker did not open");ProgrammableDialogRuntimeChecks.check(mc);System.out.println("[vandorlabs] glass-render-live PASS strengths, closed frame caps, W joins, joined materials, porthole palettes and dialogs, opaque octagonal Wall Pipes example, shared door and trapdoor dialogs");return true;}
        return false;
    }
    private static void camera(Minecraft mc,double x,double y,double z,float yaw,float pitch)throws Exception{mc.getIntegratedServer().addScheduledTask(()->mc.getIntegratedServer().getPlayerList().getPlayers().get(0).connection.setPlayerLocation(x,y,z,yaw,pitch)).get();mc.player.setPositionAndRotation(x,y,z,yaw,pitch);}
    private static void openDialog(Minecraft mc,BlockPos pos,int gui) throws Exception {
        // Let the server close the old window before opening its replacement. A client close
        // packet queued here can otherwise arrive after openGui and close the new container.
        mc.displayGuiScreen(null);
        mc.player.setPositionAndRotation(pos.getX()+.5,pos.getY(),pos.getZ()-1.5,0,10);
        mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            player.closeScreen();
            player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.motionY=0;player.sendPlayerAbilities();
            player.world.setBlockState(pos.add(0,-1,-2),net.minecraft.init.Blocks.STONE.getDefaultState(),3);
            player.connection.setPlayerLocation(pos.getX()+.5,pos.getY(),pos.getZ()-1.5,0,10);
            player.openGui(com.vandorlabs.VandorLabs.instance,gui,player.world,pos.getX(),pos.getY(),pos.getZ());
        }).get();
    }
    private static void checkDialog(Minecraft mc,boolean diagonal) throws Exception {
        if(diagonal?!(mc.currentScreen instanceof GuiProgrammableWall):!(mc.currentScreen instanceof GuiProgrammableGlass))
            throw new IllegalStateException("Glass dialog did not open through server container");
        BlockPos pos=new BlockPos(42,5,diagonal?43:40);
        net.minecraft.tileentity.TileEntity clientTile=mc.world.getTileEntity(pos);
        final int tint=diagonal?((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)clientTile).getGlassTint()
                :((com.vandorlabs.tiles.TileEntityProgrammableGlass)clientTile).getTint();
        final boolean frame=diagonal?((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)clientTile).isGlassFramed()
                :((com.vandorlabs.tiles.TileEntityProgrammableGlass)clientTile).isFramed();
        mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            boolean correct=diagonal?player.openContainer instanceof com.vandorlabs.container.ContainerAnimatedScreenSelector
                    :player.openContainer instanceof com.vandorlabs.container.ContainerProgrammableGlass;
            if(!correct || !player.openContainer.canInteractWith(player))
                throw new IllegalStateException("Glass dialog server container is missing or out of reach: container="+player.openContainer.getClass().getSimpleName()+", player="+player.getPositionVector()+", target="+pos+", distanceSq="+player.getDistanceSq(pos));
            net.minecraft.tileentity.TileEntity tile=player.world.getTileEntity(pos);
            int actualTint=diagonal?((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).getGlassTint()
                    :((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile).getTint();
            boolean actualFrame=diagonal?((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).isGlassFramed()
                    :((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile).isFramed();
            int material=diagonal?((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)clientTile).getGlassMaterial():((com.vandorlabs.tiles.TileEntityProgrammableGlass)clientTile).getMaterial();
            int actualMaterial=diagonal?((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).getGlassMaterial():((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile).getMaterial();
            if(material!=actualMaterial)throw new IllegalStateException("Glass material did not synchronize to server");
            boolean stretch=diagonal?((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)clientTile).isGlassStretch():((com.vandorlabs.tiles.TileEntityProgrammableGlass)clientTile).isStretch();
            boolean actualStretch=diagonal?((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).isGlassStretch():((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile).isStretch();
            if(stretch!=actualStretch)throw new IllegalStateException("Glass layout did not synchronize to server");
            if(actualTint!=tint || actualFrame!=frame)
                throw new IllegalStateException("Glass dialog appearance did not synchronize to server");
        }).get();
    }
    private static void checkWindowDialog(Minecraft mc,BlockPos pos)throws Exception{
        com.vandorlabs.tiles.TileEntityAnimatedScreenSelector client=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos);
        net.minecraft.nbt.NBTTagCompound appearance=client.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
        mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)player.world.getTileEntity(pos);
            net.minecraft.nbt.NBTTagCompound actual=tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
            for(String key:new String[]{"GlassShade","GlassTint","GlassMaterial","GlassStretch","GlassTextureJoin"})if(!appearance.getTag(key).equals(actual.getTag(key)))throw new IllegalStateException("Porthole setting did not synchronize: "+key);
            if(tile.getHousingTexture()!=8 || tile.getSideTexture()!=26)throw new IllegalStateException("Glass tab changed main/side textures");
        }).get();System.out.println("[vandorlabs][reprolab] porthole-glass-dialog PASS "+pos);
    }
    private static void save(Minecraft mc,File output,String name) throws Exception {
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,name+".png"));
    }
}

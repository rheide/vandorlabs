package com.vandorlabs.client;

import java.io.File;
import java.util.*;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

/** Focused 2.7 vehicle light/sign/boundary, settings undo and changed dialog paths. */
final class Artwork27RuntimeChecks {
    private static int ticks;
    private static boolean synchronousDone;
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Artwork 2.7: "+message);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(++ticks==1){mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;}
        if(synchronousDone){if(!InlineTransmitterLiveChecks.tick(mc,output))return false;System.out.println("[vandorlabs][reprolab] artwork27-focused PASS");return true;}
        if(ticks!=80)return false;
        mc.getIntegratedServer().callFromMainThread(()->{
            net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);
            net.minecraft.entity.player.EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            VehicleWorldRuntimeChecks.run(world);DuplifierRuntimeChecks.run(actor);RampAnalogRuntimeChecks.run(world,actor);RampAuthorizationRuntimeChecks.run(world,actor);UtilityStorageRuntimeChecks.run(world);RedstoneRangeRuntimeChecks.run(world,actor);com.vandorlabs.redstone.TransmitterMigrationChecks.run();
            BlockPos base=new BlockPos(1000,200,1000);world.getChunkFromBlockCoords(base);
            List<BlockPos> probes=Arrays.asList(base,base.east(),base.west(),base.north());
            try{
                world.setBlockState(base,Blocks.STONE.getDefaultState(),2);
                world.setBlockState(base.east(),Blocks.WATER.getDefaultState(),2);
                world.setBlockState(base.west(),net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface").getDefaultState(),2);
                world.setBlockState(base.north(),net.minecraft.block.Block.getBlockFromName("vandorlabs:controlled_ramp").getDefaultState(),2);
                VehicleScan scan=VehicleScan.attachment(world,base,Collections.emptySet());while(!scan.step(256)){}
                require(scan.error==null&&scan.result!=null&&scan.result.cells.size()==1,"water/surface/foreign ramp boundary captured surroundings: "+scan.error);
            }finally{for(BlockPos at:probes)world.setBlockToAir(at);}
            System.out.println("[vandorlabs][reprolab] vehicle27-world PASS saved-sign-json local-block-light water-surface-foreign-ramp-boundaries undo-later-edit-guard");
            return null;
        }).get();
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if("Surface".equals(ScreenHousingTextures.category(i)))for(boolean high:new boolean[]{false,true}){
            String id=com.vandorlabs.turntable.TurntableTextures.fromMaterialChoice(ScreenHousingTextures.choiceAt(i));
            require(mc.getTextureMapBlocks().getAtlasSprite(com.vandorlabs.turntable.TurntableTextures.texture(id,high))!=mc.getTextureMapBlocks().getMissingSprite(),"standalone/joined Surface sprite "+id);
        }
        FrameAppearanceRuntimeChecks.run();FrameEdgeRuntimeChecks.run(mc,output);VehicleControllerDialogChecks.run(mc,output);VehicleRenderRuntimeChecks.run(mc);FrameFramebufferChecks.run(mc,output);CompanyScreenLayerRuntimeChecks.run(mc,output);GearDialogChecks.run(mc,output);VehicleDynamicLights.runChecks(mc);turntableBounds(mc);
        com.vandorlabs.tiles.TileEntityProgrammableLight light=new com.vandorlabs.tiles.TileEntityProgrammableLight();light.setWorld(mc.world);light.setPos(BlockPos.ORIGIN);
        GuiProgrammableLight gui=new GuiProgrammableLight(mc.player.inventory,light);mc.displayGuiScreen(gui);
        try{
            for(int[] size:new int[][]{{320,240},{460,340}}){
                gui.setWorldAndResolution(mc,size[0],size[1]);
                for(int page=0;page<4;page++){
                    gui.actionPerformed(new GuiButton(90+page,0,0,"Page"));checkButtons(gui);
                    if(page==3){List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");require(buttons.stream().noneMatch(button->button.displayString.contains("Transmitter")),"receiver-only light exposes transmitter access");}
                    GuiOptionCycleRuntimeChecks.check(mc);
                }
            }
        }finally{mc.displayGuiScreen(null);}
        synchronousDone=true;return false;
    }
    private static void turntableBounds(Minecraft mc)throws Exception{
        com.vandorlabs.tiles.TileEntityTurntableController tile=new com.vandorlabs.tiles.TileEntityTurntableController();tile.setWorld(mc.world);tile.setPos(BlockPos.ORIGIN);GuiTurntable gui=new GuiTurntable(new com.vandorlabs.container.ContainerTurntable(tile));GuiScreen previous=mc.currentScreen;net.minecraft.inventory.Container container=mc.player.openContainer;
        try{mc.displayGuiScreen(gui);for(int[] size:new int[][]{{320,240},{460,340}}){gui.setWorldAndResolution(mc,size[0],size[1]);for(int page:new int[]{90,91}){gui.actionPerformed(new GuiButton(page,0,0,"Page"));checkButtons(gui);}}}finally{mc.displayGuiScreen(previous);mc.player.openContainer=container;}
        System.out.println("[vandorlabs][reprolab] turntable-tabs-bounds PASS compact320 regular460 both-pages");
    }
    private static void checkButtons(GuiScreen gui){
        List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");
        for(int i=0;i<buttons.size();i++){GuiButton a=buttons.get(i);if(!a.visible)continue;
            require(a.x>=0&&a.y>=0&&a.x+a.width<=gui.width&&a.y+a.height<=gui.height,"dialog button outside viewport: "+a.id);
            for(int j=0;j<i;j++){GuiButton b=buttons.get(j);if(b.visible)require(a.x>=b.x+b.width||b.x>=a.x+a.width||a.y>=b.y+b.height||b.y>=a.y+a.height,"dialog buttons overlap: "+a.id+"/"+b.id);}
        }
    }
}

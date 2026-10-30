package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.container.*;
import com.vandorlabs.redstone.RedstoneChannelMember;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/** Registry-complete evidence: every block gets a manifest entry, never a GUI-class sample. */
final class CompleteRedstoneDialogAudit {
    private static List<GuiButton> buttons(GuiScreen gui){return ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");}
    private static void action(GuiScreen gui,GuiButton button)throws Exception{ReflectionHelper.findMethod(GuiScreen.class,"actionPerformed","func_146284_a",GuiButton.class).invoke(gui,button);}
    static void run(Minecraft mc,File output,BlockPos pos)throws Exception{
        net.minecraft.block.state.IBlockState previous=mc.world.getBlockState(pos);TileEntity original=mc.world.getTileEntity(pos);GuiScreen old=mc.currentScreen;net.minecraft.inventory.Container container=mc.player.openContainer;
        List<String> failures=new ArrayList<>();int registered=0,captured=0,shots=0,skipped=0;
        try(PrintWriter manifest=new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(output,"redstone-dialog-manifest.tsv")),java.nio.charset.StandardCharsets.UTF_8))){
            manifest.println("registry_id\ttile_class\tgui_class\tstatus\treason\tpage\tviewport\tscreenshot\tlayout_review");
            try{for(Block block:Block.REGISTRY){if(!"vandorlabs".equals(block.getRegistryName().getResourceDomain()))continue;registered++;String id=block.getRegistryName().getResourcePath(),tileName="",guiName="";
                try{
                    TileEntity seed=block.createTileEntity(mc.world,block.getDefaultState());tileName=seed==null?"":seed.getClass().getSimpleName();String reason=skip(seed,block);
                    if(reason!=null){manifest.println(id+"\t"+tileName+"\t\tskipped\t"+reason+"\t\t\t\t");skipped++;continue;}
                    mc.world.setBlockState(pos,block.getDefaultState(),2);TileEntity tile=mc.world.getTileEntity(pos);if(tile instanceof com.vandorlabs.gear.GearTileEntity)tile=GearDialogChecks.fixture(mc,(com.vandorlabs.gear.GearBlock)block);GuiContainer gui=create(mc,block,tile);
                    if(gui==null){manifest.println(id+"\t"+tileName+"\t\tskipped\tno configurable Redstone path\t\t\t\t");skipped++;continue;}
                    guiName=gui.getClass().getSimpleName();gui.inventorySlots.windowId=-97;mc.displayGuiScreen(gui);captured++;
                    for(int[] size:new int[][]{{320,240},{460,340}}){
                        gui.setWorldAndResolution(mc,size[0],size[1]);boolean redstone=false;
                        for(GuiButton tab:new ArrayList<>(buttons(gui)))if(tab.visible&&tab.displayString.equals("Redstone")){if(tab.enabled)action(gui,tab);redstone=true;break;}
                        if(!redstone&&!(gui instanceof GuiPilotModes||gui instanceof com.vandorlabs.copier.client.GuiCopyController||gui instanceof GuiCoupler||gui instanceof GuiRedstoneChannel))throw new IllegalStateException("Expected configurable Redstone page is absent");
                        String file="shot_redstone_all_"+id+"_"+size[0]+".png";
                        FrameFramebufferChecks.renderDialog(mc,gui,new File(output,file),-1,-1);shots++;
                        String review=review(gui);manifest.println(id+"\t"+tileName+"\t"+guiName+"\tcaptured\tregistered configurable variant\tRedstone\t"+size[0]+"x"+size[1]+"\t"+file+"\t"+review);
                        if(!review.equals("PASS"))failures.add(id+" "+size[0]+": "+review);
                        if(tile instanceof TileEntityAnimatedScreenSelector)for(int slot=0;slot<2;slot++)if(RedstoneScreenInteractions.supportsSlot(block,slot)){
                            TileEntityAnimatedScreenSelector screen=(TileEntityAnimatedScreenSelector)tile;int savedSurface=screen.getSurfaceTexture(slot);screen.setSurfaceTexture(slot,TileEntityAnimatedScreenSelector.REDSTONE_SURFACE);screen.redstoneScreen(slot).configureRows(Arrays.asList("Doors"),Arrays.asList(com.vandorlabs.redstone.ChannelList.of(65291)),screen.getHousingTexture());
                            GuiContainer sourceGui=create(mc,block,tile);sourceGui.inventorySlots.windowId=-97;mc.displayGuiScreen(sourceGui);sourceGui.setWorldAndResolution(mc,size[0],size[1]);
                            for(GuiButton tab:new ArrayList<>(buttons(sourceGui)))if(tab.visible&&tab.displayString.equals("Redstone")){if(tab.enabled)action(sourceGui,tab);break;}
                            String sourceFile="shot_redstone_all_"+id+"_source"+slot+"_"+size[0]+".png";FrameFramebufferChecks.renderDialog(mc,sourceGui,new File(output,sourceFile),-1,-1);shots++;String sourceReview=review(sourceGui);manifest.println(id+"\t"+tileName+"\t"+guiName+"\tcaptured\tnative surface source settings\tSource"+slot+"\t"+size[0]+"x"+size[1]+"\t"+sourceFile+"\t"+sourceReview);if(!sourceReview.equals("PASS"))failures.add(id+" source"+slot+" "+size[0]+": "+sourceReview);
                            for(GuiButton tab:new ArrayList<>(buttons(sourceGui)))if(tab.id==90+slot){if(tab.enabled)action(sourceGui,tab);break;}
                            java.lang.reflect.Method rows=sourceGui.getClass().getDeclaredMethod("openRows",int.class);rows.setAccessible(true);rows.invoke(sourceGui,slot);java.lang.reflect.Method rowState=sourceGui.getClass().getDeclaredMethod("rowsPage");rowState.setAccessible(true);if(!Boolean.TRUE.equals(rowState.invoke(sourceGui)))throw new IllegalStateException("Native rows capture did not enter its actual surface editor");
                            String rowFile="shot_redstone_all_"+id+"_rows"+slot+"_"+size[0]+".png";FrameFramebufferChecks.renderDialog(mc,sourceGui,new File(output,rowFile),-1,-1);shots++;String rowReview=review(sourceGui);manifest.println(id+"\t"+tileName+"\t"+guiName+"\tcaptured\tnative control target editor\tRows"+slot+"\t"+size[0]+"x"+size[1]+"\t"+rowFile+"\t"+rowReview);if(!rowReview.equals("PASS"))failures.add(id+" rows"+slot+" "+size[0]+": "+rowReview);
                            screen.setSurfaceTexture(slot,savedSurface);mc.displayGuiScreen(gui);gui.initGui();
                        }
                        if(gui instanceof GuiModularGear||gui instanceof GuiRampController){String tabName=gui instanceof GuiModularGear?"Mechanism":"Ramp",pageName=gui instanceof GuiModularGear?"Mechanism":"General";for(GuiButton tab:new ArrayList<>(buttons(gui)))if(tab.displayString.equals(tabName)){if(tab.enabled)action(gui,tab);break;}String context="shot_redstone_all_"+id+"_"+pageName.toLowerCase(java.util.Locale.ROOT)+"_"+size[0]+".png";FrameFramebufferChecks.renderDialog(mc,gui,new File(output,context),-1,-1);shots++;String contextReview=review(gui);manifest.println(id+"\t"+tileName+"\t"+guiName+"\tcaptured\tmechanism context\t"+pageName+"\t"+size[0]+"x"+size[1]+"\t"+context+"\t"+contextReview);if(!contextReview.equals("PASS"))failures.add(id+" "+pageName+" "+size[0]+": "+contextReview);}
                    }
                }catch(Exception failure){String detail=failure.toString().replace('\t',' ').replace('\n',' ');failures.add(id+": "+detail);manifest.println(id+"\t"+tileName+"\t"+guiName+"\tFAILED\t"+detail+"\t\t\t\t");}
                manifest.flush();
            }}finally{mc.displayGuiScreen(old);mc.player.openContainer=container;mc.world.setBlockState(pos,previous,2);if(original!=null)mc.world.setTileEntity(pos,original);}
            manifest.println("# registered="+registered+" captured="+captured+" skipped="+skipped+" screenshots="+shots+" issues="+failures.size());
        }
        for(String failure:failures)System.out.println("[vandorlabs][reprolab] redstone-audit ISSUE "+failure);
        System.out.println("[vandorlabs][reprolab] redstone-registry-audit "+(failures.isEmpty()?"PASS":"FAIL")+" registered="+registered+" captured="+captured+" skipped="+skipped+" screenshots="+shots+" issues="+failures.size()+" manifest=redstone-dialog-manifest.tsv");
        if(!failures.isEmpty())throw new IllegalStateException("Redstone registry audit found "+failures.size()+" issues; inspect manifest and captured screenshots");
    }
    private static String skip(TileEntity tile,Block block){
        if(tile==null)return "no tile or configurable Redstone controls";
        if(tile instanceof com.vandorlabs.gear.track.TrackTile)return "track appearance only; arm controller owns Redstone";
        if(tile instanceof TileEntityProgrammableGlass)return "glass appearance only; no Redstone dialog";
        if(tile instanceof TileEntityCanopy&&block instanceof BlockCanopy&&!(com.vandorlabs.canopy.LargeCanopySettings.supports(((BlockCanopy)block).kind)||((BlockCanopy)block).opening()))return "static canopy has no Redstone controls";
        if(tile instanceof TileEntityAnimatedScreenSelector&&!(tile instanceof TileEntityProgrammableTrigger||tile instanceof TileEntityProgrammableLight)&&(block instanceof BlockProgrammableWall||block instanceof BlockProgrammableBlock||block instanceof BlockProgrammableSlab||block instanceof BlockProgrammableStairs||tile instanceof TileEntityHexTank))return "appearance/storage dialog only; no Redstone controls";
        return null;
    }
    private static GuiContainer create(Minecraft mc,Block block,TileEntity tile){
        if(tile instanceof com.vandorlabs.copier.CopyControllerTile)return new com.vandorlabs.copier.client.GuiCopyController(new com.vandorlabs.copier.CopyControllerContainer((com.vandorlabs.copier.CopyControllerTile)tile));
        if(tile instanceof com.vandorlabs.gear.GearTileEntity)return new GuiModularGear(new ContainerModularGear((com.vandorlabs.gear.GearTileEntity)tile));
        if(tile instanceof TileEntityCockpit)return new GuiCockpit(new ContainerCockpit((TileEntityCockpit)tile,null));
        if(tile instanceof TileEntityWirelessRedstone)return new GuiWirelessRedstone(new ContainerWirelessRedstone((TileEntityWirelessRedstone)tile));
        if(tile instanceof TileEntitySecuredDisplay)return new GuiNavigationTable(new ContainerTokenSlot(mc.player.inventory,(TileEntityTokenSlot)tile));
        if(tile instanceof TileEntityTokenSlot)return new GuiTokenSlot(new ContainerTokenSlot(mc.player.inventory,(TileEntityTokenSlot)tile));
        if(tile instanceof com.vandorlabs.plate.PlateTile)return new GuiPressurePlate(new com.vandorlabs.plate.PlateContainer((com.vandorlabs.plate.PlateTile)tile));
        if(tile instanceof TileEntityProgrammableTrapdoor)return new GuiProgrammableTrapdoor((TileEntityProgrammableTrapdoor)tile);
        if(tile instanceof TileEntityRampController)return new GuiRampController((TileEntityRampController)tile);
        if(tile instanceof TileEntityTurntableController)return new GuiTurntable(new ContainerTurntable((TileEntityTurntableController)tile));
        if(tile instanceof TileEntityLandingGear)return new GuiLandingGear((TileEntityLandingGear)tile);
        if(tile instanceof TileEntityCanopy)return new GuiLargeCanopy((TileEntityCanopy)tile);
        if(tile instanceof TileEntityVehicleController)return new GuiPilotModes((TileEntityVehicleController)tile);
        if(tile instanceof TileEntityAutopilot)return new GuiAutopilot((TileEntityAutopilot)tile);
        if(tile instanceof com.vandorlabs.coupler.TileCoupler)return new GuiCoupler(new com.vandorlabs.coupler.ContainerCoupler((com.vandorlabs.coupler.TileCoupler)tile));
        if(tile instanceof TileEntitySpaceDoor)return new GuiSpaceDoor((TileEntitySpaceDoor)tile);
        if(tile instanceof TileEntityProgrammableLight)return new GuiProgrammableLight(mc.player.inventory,(TileEntityProgrammableLight)tile);
        if(tile instanceof TileEntityProgrammableTrigger)return new GuiProgrammableTrigger(mc.player.inventory,(TileEntityProgrammableTrigger)tile);
        if(tile instanceof TileEntityAnimatedScreenSelector){TileEntityAnimatedScreenSelector screen=(TileEntityAnimatedScreenSelector)tile;
            if(block instanceof BlockProgrammableFullInput)return new GuiAnimatedScreenSelector(mc.player.inventory,screen);
            if(block instanceof BlockProgrammableInput||block instanceof BlockDiagonalHalfConsole)return new GuiProgrammableInput(mc.player.inventory,screen);
            if(block instanceof BlockProgrammableHalfConsole)return new GuiProgrammableHalfConsole(mc.player.inventory,screen);
            return new GuiAnimatedScreenSelector(mc.player.inventory,screen);
        }
        return tile instanceof RedstoneChannelMember?new GuiRedstoneChannel((RedstoneChannelMember)tile):null;
    }
    private static String review(GuiContainer gui)throws Exception{
        List<String> issues=new ArrayList<>();if(gui.getGuiLeft()<0||gui.getGuiTop()<0||gui.getGuiLeft()+gui.getXSize()>gui.width||gui.getGuiTop()+gui.getYSize()>gui.height)issues.add("panel rectangle outside viewport: "+gui.getGuiLeft()+","+gui.getGuiTop()+" "+gui.getXSize()+"x"+gui.getYSize());List<GuiButton> visible=new ArrayList<>();for(GuiButton b:buttons(gui))if(b.visible){visible.add(b);if(b.x<0||b.y<0||b.x+b.width>gui.width||b.y+b.height>gui.height)issues.add("button outside viewport:"+b.displayString);if(b instanceof DialogTabButton&&Minecraft.getMinecraft().fontRenderer.getStringWidth(b.displayString)>b.width-6)issues.add("tab caption clipped:"+b.displayString);}
        for(int i=0;i<visible.size();i++)for(int j=i+1;j<visible.size();j++){GuiButton a=visible.get(i),b=visible.get(j);if(a.x<b.x+b.width&&a.x+a.width>b.x&&a.y<b.y+b.height&&a.y+a.height>b.y)issues.add("buttons overlap:"+a.displayString+"/"+b.displayString);}
        for(GuiButton source:InlineTransmitterWidget.controls(gui)){int useful=Math.min(400,gui.getXSize()-24);if(source.width<useful-8||Math.abs((source.x-gui.getGuiLeft())*2+source.width-gui.getXSize())>4)issues.add("transmitter retains empty column: x="+(source.x-gui.getGuiLeft())+" width="+source.width+" useful="+useful);}
        // A dedicated receiver page has no artwork preview: a single receiver must use its useful width.
        List<RedstoneActivationButton> activation=new ArrayList<>();for(GuiButton b:visible)if(b instanceof RedstoneActivationButton)activation.add((RedstoneActivationButton)b);
        boolean dedicated=!(gui instanceof GuiTurntable||gui instanceof GuiCockpit||gui instanceof com.vandorlabs.copier.client.GuiCopyController);
        if(dedicated&&activation.size()==1){GuiButton a=activation.get(0);int useful=Math.min(400,gui.getXSize()-24);if(Math.abs((a.x-gui.getGuiLeft())*2+a.width-gui.getXSize())>4||a.width<useful-4)issues.add("receiver activation retains empty column: x="+(a.x-gui.getGuiLeft())+" width="+a.width+" useful="+useful);
            for(GuiTextField f:fields(gui))if(f.getVisible()&&(f.x!=a.x||f.width!=a.width))issues.add("receive field not aligned/full width: x="+(f.x-gui.getGuiLeft())+" width="+f.width+" activation="+a.width);
        }
        if(gui instanceof GuiProgrammableTrigger)for(GuiTextField field:fields(gui))if(field.getVisible()&&(field.x!=gui.getGuiLeft()+12||field.width!=gui.getXSize()-24))issues.add("trigger receiver retains empty material column");
        if(gui instanceof GuiModularGear){com.vandorlabs.gear.GearTileEntity tile=ReflectionHelper.getPrivateValue(GuiModularGear.class,(GuiModularGear)gui,"tile");for(GuiButton b:visible)if(b.id==0){if(b.x!=gui.getGuiLeft()+12||b.width!=gui.getXSize()-24)issues.add("arm activation does not span useful width");if(b.enabled==tile.isFixedMount())issues.add("fixed/moving arm activation enabled state incorrect");}for(GuiTextField f:fields(gui))if(f.getVisible()&&(f.x!=gui.getGuiLeft()+12||f.width!=gui.getXSize()-24))issues.add("arm receive field does not span useful width");}
        return issues.isEmpty()?"PASS":String.join("; ",issues);
    }
    private static List<GuiTextField> fields(GuiContainer gui)throws Exception{
        List<GuiTextField> result=new ArrayList<>();for(Class<?> type=gui.getClass();type!=null&&type!=GuiContainer.class;type=type.getSuperclass())for(Field field:type.getDeclaredFields()){field.setAccessible(true);Object value=field.get(gui);if(value instanceof GuiTextField)result.add((GuiTextField)value);else if(value instanceof ReceiverPane)result.add(((ReceiverPane)value).field());}return result;
    }
}

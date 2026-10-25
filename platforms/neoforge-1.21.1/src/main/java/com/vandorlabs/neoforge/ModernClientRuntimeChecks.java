package com.vandorlabs.neoforge;

import com.vandorlabs.ui.ProgrammableBlockDialog;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Opt-in native client rendering, menu input and network round-trip checks. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class ModernClientRuntimeChecks {
    private static int ticks,totalTicks,stage,stageStart,screenTick,redstoneTick,wirelessTick,filesystemTick,familyTick,screenFilesystemTick,joinedTick,controlTick,triggerTick,extendedControlTick,expandedIconTick;
    private static String screenshot;
    private static long renderedFrames;
    public static long renderedFrames(){return renderedFrames;}
    private static final Set<String> captured=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static void require(boolean value,String message) { if(!value)throw new IllegalStateException("Modern client: "+message); }
    public static boolean captured(String name){return captured.contains(name);}
    public static void capture(String name) { screenshot=name; }
    @SubscribeEvent public static void clearOverlays(RenderFrameEvent.Pre event) {
        if(Boolean.getBoolean("vandorlabs.modernClientSmoke")) { Minecraft.getInstance().getToasts().clear();Minecraft.getInstance().gui.getChat().clearMessages(true); }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if(!Boolean.getBoolean("vandorlabs.modernClientSmoke"))return;
        renderedFrames++;if(screenshot==null)return;
        Minecraft mc=Minecraft.getInstance();String name=screenshot;screenshot=null;
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),component->{captured.add(name);System.out.println("[vandorlabs][modern] screenshot "+name+": "+component.getString());});
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("vandorlabs.modernClientSmoke"))return;
        Minecraft mc=Minecraft.getInstance();
        if(totalTicks==0)mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        require(++totalTicks<6000,"startup/login timed out before placed-world checks");
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) { onboarding.onClose();return; }
        if(mc.player==null||mc.level==null||mc.gameMode==null)return;
        ticks++;
        require(ticks<6000,"smoke timed out at stage "+stage);
        BlockPos pos=new BlockPos(0,80,0);
        if(stage==0&&System.getProperty("vandorlabs.modernClientSuite","").equals("extended-controls")){if(ticks<60)return;stage=47;extendedControlTick=0;return;}
        if(stage==0&&System.getProperty("vandorlabs.modernClientSuite","").equals("trigger")){if(ticks<60)return;stage=46;triggerTick=0;return;}
        if(stage==0&&System.getProperty("vandorlabs.modernClientSuite","").equals("joined-controls")){if(ticks<60)return;stage=44;joinedTick=0;return;}
        if(stage==0) {
            if(ticks<60||!(mc.level.getBlockEntity(pos) instanceof ProgrammableBlockEntity))return;
            mc.options.guiScale().set(2);mc.resizeDisplay();
            mc.player.setYRot(0);mc.player.setXRot(16);
            mc.setScreen(null);geometry(mc,pos);capture("modern-world.png");stage=1;stageStart=ticks;
        } else if(stage==1&&ticks-stageStart>20) {
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
            mc.player.setShiftKeyDown(true);
            mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(.5,80.5,0),Direction.NORTH,pos,false));
            stage=2;stageStart=ticks;
        } else if(stage==2) {
            if(!(mc.screen instanceof ModernClient.FinishScreen))return;
            var before=ModernClient.smokeSettings();var panel=new ProgrammableDialogLayout(mc.screen.width,mc.screen.height);
            var control=ProgrammableBlockDialog.controls(panel,before).stream().filter(c->c.bounds.id==ProgrammableBlockDialog.TEXTURE_LAYOUT).findFirst().orElseThrow().bounds;
            require(mc.screen.mouseClicked(control.x+3,control.y+3,1),"right click not accepted");
            require(ModernClient.smokeSettings().layout==Math.floorMod(before.layout-1,3),"right-click did not select previous layout");
            mc.screen.mouseClicked(control.x+3,control.y+3,0);
            require(ModernClient.smokeSettings().layout==before.layout,"left/right cycling mismatch");
            int oldScroll=ModernClient.smokeScroll();mc.screen.mouseScrolled(panel.listX+10,panel.top+80,0,-1);
            require(ModernClient.smokeScroll()>=oldScroll,"wheel direction changed");
            int x=panel.listX+panel.listWidth+3,y=ProgrammableBlockDialog.listY(panel,before)+ProgrammableBlockDialog.listRows(panel,before)*22-3;
            mc.screen.mouseClicked(x,y,0);mc.screen.mouseDragged(x,y-20,0,0,-20);mc.screen.mouseReleased(x,y-20,0);
            ModernClient.smokeCenterSelection();capture("modern-programmable-block-gui2.png");stage=3;stageStart=ticks;
        } else if(stage==3&&ticks-stageStart>30) {
            var tile=(ProgrammableBlockEntity)mc.level.getBlockEntity(pos);
            require(tile.settings().layout==ModernClient.smokeSettings().layout,"settings did not return from server");
            mc.options.guiScale().set(3);mc.resizeDisplay();ModernClient.smokeCenterSelection();capture("modern-programmable-block.png");stage=31;stageStart=ticks;
            System.out.println("[vandorlabs][modern] block-client PASS real world model/atlas quads, actual menu, left/right layout, wheel/drag and server roundtrip");
        } else if(stage==31&&ticks-stageStart>20) {
            mc.options.guiScale().set(2);mc.resizeDisplay();mc.player.closeContainer();stage=32;filesystemTick=0;
        } else if(stage==32) {
            if(com.vandorlabs.neoforge.client.FilesystemClientChecks.tick(mc,filesystemTick++)) {stage=4;screenTick=0;}
        } else if(stage==4) {
            if(com.vandorlabs.neoforge.screen.ScreenClientChecks.tick(mc,screenTick++)) { capture("modern-screen.png");stage=43;screenFilesystemTick=0; }
        } else if(stage==43) {
            if(com.vandorlabs.neoforge.client.ScreenFilesystemClientChecks.tick(mc,screenFilesystemTick++)) {stage=41;redstoneTick=0;}
        } else if(stage==41) {
            if(com.vandorlabs.neoforge.screen.RedstoneClientChecks.tick(mc,redstoneTick++)) { stage=40;wirelessTick=0; }
        } else if(stage==40) {
            if(com.vandorlabs.neoforge.redstone.WirelessClientChecks.tick(mc,wirelessTick++)) { stage=42;familyTick=0; }
        } else if(stage==42) {
            if(com.vandorlabs.neoforge.screen.ScreenFamilyClientChecks.tick(mc,familyTick++)) {stage=44;joinedTick=0;}
        } else if(stage==44) {
            if(com.vandorlabs.neoforge.client.JoinedCubeClientChecks.tick(mc,joinedTick++)) {stage=45;controlTick=0;}
        } else if(stage==45) {
            if(com.vandorlabs.neoforge.control.ControlClientChecks.tick(mc,controlTick++)) {stage=46;triggerTick=0;}
        } else if(stage==46) {
            if(com.vandorlabs.neoforge.trigger.TriggerClientChecks.tick(mc,triggerTick++)) {stage=47;extendedControlTick=0;}
        } else if(stage==47) {
            if(com.vandorlabs.neoforge.control.ExtendedControlClientChecks.tick(mc,extendedControlTick++)) {stage=48;expandedIconTick=0;}
        } else if(stage==48) {
            if(com.vandorlabs.neoforge.control.ExpandedControlClientModelChecks.tick(mc,expandedIconTick++)) {stage=5;stageStart=ticks;}
        } else if(stage==5&&ticks-stageStart>40&&(System.getProperty("vandorlabs.modernClientSuite","").equals("extended-controls")||System.getProperty("vandorlabs.modernClientSuite","").equals("trigger")||System.getProperty("vandorlabs.modernClientSuite","").equals("joined-controls")||captured.containsAll(Set.of("modern-world.png","modern-programmable-block.png","modern-programmable-block-gui2.png","modern-screen.png","modern-filesystem-world.png","modern-filesystem-picker-portrait.png","modern-filesystem-picker-missing.png","modern-filesystem-picker-wide.png","modern-redstone-overlay-on.png","modern-redstone-editor.png","modern-redstone-editor-scale3.png","modern-redstone-editor-scale2.png","modern-redstone-overlay-eight.png","modern-wireless-world.png","modern-wireless-receiver-dialog.png","modern-wireless-transmitter-dialog.png")))) {
            System.out.println("[vandorlabs][modern] client-smoke PASS placed world, native dialogs and rendered screenshots");mc.stop();stage=6;
        }
    }
    private static void matterGeometry(Minecraft mc) {
        var state=mc.level.getBlockState(BasicHousingRuntimeChecks.MATTER_POS);
        require(state.is(VandorLabsModern.MATTER_BLOCK.get()),"actual matter block absent");
        var model=mc.getBlockRenderer().getBlockModel(state);int count=0;
        for(Direction face:Direction.values())for(var quad:model.getQuads(state,face,RandomSource.create(0))) {
            count++;require(quad.getSprite().contents().name().toString().equals("vandorlabs:block/matter/matter"),"wrong canonical matter artwork");
            require(quad.getSprite().contents().width()==128&&quad.getSprite().contents().height()==128,"matter artwork resolution drift");
        }
        require(count==6,"matter full cube faces missing");
        var stack=new net.minecraft.world.item.ItemStack(VandorLabsModern.MATTER_BLOCK_ITEM.get());
        var item=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
        var gui=item.getTransforms().getTransform(net.minecraft.world.item.ItemDisplayContext.GUI);
        require(gui.scale.x()==.5F&&gui.scale.y()==.5F&&gui.scale.z()==.5F,"canonical padded matter item scale drift");
    }
    private static void geometry(Minecraft mc,BlockPos pos) {
        matterGeometry(mc);
        var state=mc.level.getBlockState(pos);var tile=(ProgrammableBlockEntity)mc.level.getBlockEntity(pos);var model=mc.getBlockRenderer().getBlockModel(state);
        Set<String> sprites=new HashSet<>();int quads=0;
        for(Direction face:Direction.values())for(var quad:model.getQuads(state,face,RandomSource.create(0),tile.getModelData(),null)) {
            quads++;String sprite=quad.getSprite().contents().name().toString();require(!sprite.contains("missingno"),"missing finish sprite");sprites.add(sprite);
            int[] data=quad.getVertices();require(data.length>=32,"invalid vertex records");int stride=data.length/4;
            for(int vertex=0;vertex<4;vertex++)for(int axis=0;axis<3;axis++) { float value=Float.intBitsToFloat(data[vertex*stride+axis]);require(Float.isFinite(value)&&value>=-0.001&&value<=1.001,"cube geometry out of bounds"); }
        }
        require(quads==6&&sprites.size()>=4,"per-face model data not rendered: quads="+quads+", sprites="+sprites);
    }
    private ModernClientRuntimeChecks() {}
}

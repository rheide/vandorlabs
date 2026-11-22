package com.vandorlabs.neoforge.seating;

import java.nio.file.Files;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import static com.vandorlabs.neoforge.seating.NativeCockpitFrameFixture.*;

@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeCockpitFrameClient {
    private static int stage,ticks,wait;
    private static String screenshot;
    private static java.util.concurrent.CompletableFuture<Void> reload;

    private static void control(com.vandorlabs.neoforge.client.NativeScreenScreen editor,int id,int mouse){
        var control=editor.button(id);
        check(control!=null&&control.active&&editor.mouseClicked(control.getX()+control.getWidth()/2D,
            control.getY()+control.getHeight()/2D,mouse),"actual Cockpit editor control "+id);
    }
    private static void selectSurface(com.vandorlabs.neoforge.client.NativeScreenScreen editor,String label){
        for(int slot=0;slot<NativeCockpitTouch.MAX_SURFACES;slot++){
            if(editor.button(594).getMessage().getString().equals(label))return;
            control(editor,594,0);
        }
        throw new AssertionError("Missing Cockpit surface "+label);
    }
    private static void choose(com.vandorlabs.neoforge.client.NativeScreenScreen editor,String search,int choice){
        var picker=editor.activePicker();
        picker.searchField().setValue(search);
        int row=picker.rows().indexOf(choice);
        while(row>=picker.scroll()+picker.visible()){
            int before=picker.scroll();
            check(editor.mouseScrolled(editor.panel().listX+4,picker.top()+5,0,-1)&&picker.scroll()>before,
                "wheel reaches cockpit artwork choice");
        }
        check(row>=picker.scroll()&&editor.mouseClicked(editor.panel().listX+4,picker.top()+5+(row-picker.scroll())*22,0)
            &&editor.activePicker().selected()==choice,"select real Cockpit artwork row "+search);
    }

    private static void aimThrottle(Minecraft game,NativeChairEntity owner){
        var facing=owner.getBlockState().getValue(NativeSeatBlock.FACING);
        float angle=(float)Math.toRadians(NativeCockpitThrottle.angle(0,owner.throttleStep(0),owner.throttleCount()));
        var local=NativeCockpitThrottle.pivot(0,owner.throttleCount()).add(new Vec3(0,.25,0).xRot(-angle));
        aim(game,Vec3.atLowerCornerOf(ROOT).add(NativeAdvancedChairGeometry.turn(local,facing)));
    }
    private static void clickThrottle(Minecraft game,NativeChairEntity owner){
        var actor=game.player;
        var start=actor.getEyePosition();
        var end=start.add(actor.getViewVector(1).scale(actor.blockInteractionRange()));
        var hit=game.level.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,actor));
        var grip=NativeCockpitThrottle.pick(owner,owner.getBlockState().getValue(NativeSeatBlock.FACING),hit.getBlockPos(),start,end);
        check(grip!=null&&grip.index()==NativeCockpitThrottle.LEFT
            &&hit.getLocation().distanceToSqr(grip.ray().getLocation())<1e-8,"seated ray hits the rendered throttle detent");
        game.gameMode.useItemOn(actor,InteractionHand.MAIN_HAND,hit);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled())return;
        var game=Minecraft.getInstance();
        var player=game.player;
        if(player==null||game.level==null||game.gameMode==null||game.getOverlay()!=null)return;
        check(++ticks<1200,"timeout stage="+stage+" pos="+player.position()+" slot="+player.getInventory().selected+
            " held="+player.getMainHandItem()+" mode="+game.gameMode.getPlayerMode());
        var item=NativeSeatBindings.ITEMS.get(NativeSeatKind.COCKPIT);
        var block=NativeSeatBindings.BLOCKS.get(NativeSeatKind.COCKPIT);
        var owner=NativeSeatBlock.owner(game.level,ROOT);
        switch(stage){
            case 0 -> {
                if(game.gameMode.getPlayerMode()!=GameType.SURVIVAL||player.getInventory().selected!=0
                    ||!player.getMainHandItem().is(item)||player.position().distanceToSqr(START)>.01)return;
                player.setXRot(27);
                screenshot="cockpit-placement-clearance.png";
                stage++;
            }
            case 1 -> {
                if(screenshot!=null)return;
                click(game,ROOT.below(),Direction.UP);
                stage++;
            }
            case 2 -> {
                if(owner==null||!block.complete(game.level,ROOT)||!player.getMainHandItem().isEmpty())return;
                check(owner.getBlockState().getValue(NativeSeatBlock.FACING)==Direction.NORTH,"source player-oriented formation");
                for(int part=0;part<NativeCockpitGeometry.CELLS;part++){
                    var pos=ROOT.offset(NativeCockpitGeometry.offset(part,Direction.NORTH));
                    check(NativeSeatBlock.owner(game.level,pos)==owner,"one root for section "+part);
                    var state=game.level.getBlockState(pos);
                    var model=game.getBlockRenderer().getBlockModel(state);
                    var data=model.getModelData(game.level,pos,state,owner.getModelData());
                    var quads=model.getQuads(state,null,RandomSource.create(0),data,null);
                    check((part==0?!quads.isEmpty():quads.isEmpty())
                        &&quads.stream().noneMatch(quad->quad.getSprite().contents().name().getPath().equals("missingno")),
                        "only the root emits the authored shell, section "+part);
                }
                player.setXRot(18);
                screenshot="cockpit-stationary-formed.png";
                stage++;
            }
            case 3 -> {
                if(screenshot!=null)return;
                select(game,2);
                wait=ticks;stage=20;
            }
            case 20 -> {
                if(ticks<wait+8||!player.getMainHandItem().is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return;
                click(game,MINE,Direction.SOUTH);
                stage++;
            }
            case 21 -> {
                if(!(game.screen instanceof com.vandorlabs.neoforge.client.NativeScreenScreen editor))return;
                check(editor.getMenu().cockpit()&&editor.getTitle().getString().equals("Cockpit Displays"),
                    "normal Configurizer opens the shared touch-row editor");
                selectSurface(editor,"Right arm keyboard");
                for(int row=0;row<3;row++)editor.action(590,1);
                check(!editor.button(590).active,"authored three-row touch capacity");
                editor.rowLabel(2).setValue("Trim");
                editor.rowChannels(2).setValue(Integer.toString(TOUCH_CHANNEL));
                editor.action(702,1);
                selectSurface(editor,"Center monitor");
                for(int row=0;row<6;row++)editor.action(590,1);
                check(!editor.button(590).active,"six center rows");
                for(int scroll=0;scroll<5;scroll++)editor.mouseScrolled(0,0,0,-1);
                int last=editor.rowLabels().size()-1;
                editor.rowLabel(last).setValue("Last");
                editor.rowChannels(last).setValue(Integer.toString(CENTER_CHANNEL));
                selectSurface(editor,"Left monitor");
                control(editor,595,0);
                choose(editor,"Black",NativeCockpitSurfaceEdit.BLACK);
                control(editor,597,0);
                editor.channelField().setValue(Integer.toString(DISPLAY_CHANNEL));
                control(editor,1602,1);
                selectSurface(editor,"Right touch panel");
                control(editor,595,0);
                choose(editor,"Redstone controls",NativeCockpitSurfaceEdit.REDSTONE);
                control(editor,596,0);
                editor.action(590,1);
                editor.rowLabel(0).setValue("Sensors");
                editor.rowChannels(0).setValue(Integer.toString(EXTRA_CHANNEL));
                editor.action(20,1);
                select(game,3);
                wait=ticks;stage++;
            }
            case 22 -> {
                if(ticks<wait+8||game.screen!=null||!player.getMainHandItem().isEmpty()
                    ||owner.cockpitTouch().controls(0).rows().size()!=3)return;
                click(game,MINE,Direction.SOUTH);
                stage=4;
            }
            case 4 -> {
                if(!(player.getVehicle() instanceof NativeChairMount mount)||!ROOT.equals(mount.chairPos()))return;
                var seat=NativeCockpitGeometry.cushion(Direction.NORTH);
                check(Math.abs(player.getX()-ROOT.getX()-seat.x)<.001&&Math.abs(player.getZ()-ROOT.getZ()-seat.z)<.001
                    &&Math.abs(net.minecraft.util.Mth.wrapDegrees(player.getYRot()-mount.getYRot()))<1,"actual authored cushion and heading");
                game.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                player.setXRot(13);
                screenshot="cockpit-stationary-seated.png";
                stage++;
            }
            case 5 -> {
                if(screenshot!=null)return;
                game.options.setCameraType(CameraType.FIRST_PERSON);
                aimLift(game);
                wait=ticks;stage=40;
            }
            case 40 -> {
                if(ticks<wait+8)return;
                check(!owner.lift().retracted()&&owner.lift().revision()==0,"initial deployed bank");
                press(game);
                stage++;
            }
            case 41 -> {
                if(owner.lift().revision()!=1)return;
                check(owner.lift().retracted()&&owner.lift().from()==0,"actual seated screen button retracts");
                wait=ticks;stage++;
            }
            case 42 -> {
                if(ticks<wait+8)return;
                press(game);
                stage++;
            }
            case 43 -> {
                if(owner.lift().revision()!=2)return;
                check(!owner.lift().retracted()&&owner.lift().from()>0&&owner.lift().from()<1,
                    "actual reversal samples the moving server bank");
                wait=ticks;stage++;
            }
            case 44 -> {
                if(ticks<wait+36)return;
                press(game);
                stage++;
            }
            case 45 -> {
                if(owner.lift().revision()!=3||owner.liftPosition()<.999)return;
                var upper=ROOT.north().above();
                check(NativeCockpitGeometry.shape(3,Direction.NORTH,0)!=NativeCockpitGeometry.shape(3,Direction.NORTH,1)
                    &&game.level.getBlockState(upper).getCollisionShape(game.level,upper)==NativeCockpitGeometry.shape(3,Direction.NORTH,1),
                    "client predicts authored retracted collision using the synchronized timeline");
                cameraOverview(game);
                screenshot="cockpit-screens-retracted.png";
                stage++;
            }
            case 46 -> {
                if(screenshot!=null)return;
                aimLift(game);
                wait=ticks;stage++;
            }
            case 47 -> {
                if(ticks<wait+8)return;
                press(game);
                stage++;
            }
            case 48 -> {
                if(owner.lift().revision()!=4||owner.liftPosition()>.001
                    ||com.vandorlabs.neoforge.NativeCockpitLiftRenderer.visualProgress(owner)>.001)return;
                cameraOverview(game);
                screenshot="cockpit-screens-deployed.png";
                stage++;
            }
            case 49 -> {
                if(screenshot!=null)return;
                reload=game.reloadResourcePacks();
                stage++;
            }
            case 50 -> {
                if(reload==null||!reload.isDone())return;
                reload.join();
                check(com.vandorlabs.neoforge.NativeCockpitLiftRenderer.visualProgress(owner)<.001,
                    "resource reload must not replay the completed screen motion");
                var face=NativeCockpitTouch.face(Direction.NORTH);
                var aim=Vec3.atLowerCornerOf(ROOT).add(face.topLeft()).add(face.right().scale(98D/128)).add(face.down().scale(2.5/3));
                aim(game,aim);
                wait=ticks;stage=54;
            }
            case 54 -> {
                if(ticks<wait+8)return;
                var eye=player.getEyePosition();
                var end=eye.add(player.getViewVector(1).scale(4));
                var hit=game.level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
                var panel=NativeCockpitTouch.pick(ROOT,Direction.NORTH,MINE,eye,end,3);
                check(panel!=null&&panel.row()==2&&panel.segment()==2&&hit.getLocation().distanceToSqr(panel.hit().getLocation())<1e-8,
                    "actual seated authored panel target is the third row and third slider detent");
                game.gameMode.useItemOn(player,InteractionHand.MAIN_HAND,hit);
                stage++;
            }
            case 55 -> {
                if(owner.cockpitTouch().controls(0).rows().get(2).level()!=10)return;
                screenshot="cockpit-touch-panel-energized.png";
                stage++;
            }
            case 56 -> {
                if(screenshot!=null)return;
                var face=NativeCockpitTouch.face(Direction.NORTH,NativeCockpitTouch.CENTER,0);
                check(NativeCockpitTouch.face(Direction.NORTH,NativeCockpitTouch.CENTER,1)==null,"stowed monitor is hidden");
                var target=Vec3.atLowerCornerOf(ROOT).add(face.topLeft()).add(face.right().scale(.44)).add(face.down().scale(5.5/6));
                aim(game,target);
                wait=ticks;stage++;
            }
            case 57 -> {
                if(ticks<wait+8)return;
                var eye=player.getEyePosition();
                var end=eye.add(player.getViewVector(1).scale(4));
                var hit=game.level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
                var monitor=owner.cockpitTouch().pick(hit.getBlockPos(),eye,end);
                check(monitor!=null&&monitor.slot()==NativeCockpitTouch.CENTER&&monitor.row()==5,"real seated center monitor sixth row");
                game.gameMode.useItemOn(player,InteractionHand.MAIN_HAND,hit);
                stage++;
            }
            case 58 -> {
                if(!owner.cockpitTouch().controls(NativeCockpitTouch.CENTER).rows().get(5).active(game.level.getGameTime()))return;
                screenshot="cockpit-center-monitor-energized.png";
                stage++;
            }
            case 59 -> {
                if(screenshot!=null)return;
                aimThrottle(game,owner);
                wait=ticks;stage++;
            }
            case 60 -> {
                if(ticks<wait+8)return;
                clickThrottle(game,owner);
                stage++;
            }
            case 61 -> {
                if(owner.armrestLevel(NativeCockpitThrottle.LEFT)!=5)return;
                check(owner.armrestLevel(NativeCockpitThrottle.RIGHT)==0,"physical Cockpit detents are independent");
                select(game,2);
                aimThrottle(game,owner);
                wait=ticks;stage++;
            }
            case 62 -> {
                if(ticks<wait+8||!player.getMainHandItem().is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return;
                clickThrottle(game,owner);
                stage++;
            }
            case 63 -> {
                if(!(game.screen instanceof com.vandorlabs.neoforge.seating.client.NativeSeatScreen editor))return;
                check(editor.getMenu().initialArmrest==NativeCockpitThrottle.LEFT&&editor.getTitle().getString().equals("Cockpit Throttles"),
                    "Configurator aims at same posed throttle and opens its own control");
                editor.armrestChannelsField().setValue(Integer.toString(THROTTLE_CHANNEL));
                var apply=editor.armrestApply();
                check(apply.active&&editor.mouseClicked(apply.getX()+4,apply.getY()+4,0),"configured throttle channel");
                var count=editor.option(13);
                check(editor.mouseClicked(count.getX()+4,count.getY()+4,1),"single throttle changes through physical editor reverse cycle");
                editor.onClose();
                select(game,3);
                stage++;
            }
            case 64 -> {
                if(owner.throttleCount()!=1||!player.getMainHandItem().isEmpty()
                    ||!owner.armrestChannels(NativeCockpitThrottle.LEFT).contains(THROTTLE_CHANNEL))return;
                check(NativeCockpitThrottle.pivot(0,1).x>NativeCockpitThrottle.pivot(0,2).x,
                    "single throttle uses its own authored central pivot");
                aimThrottle(game,owner);
                wait=ticks;stage++;
            }
            case 65 -> {
                if(ticks<wait+8)return;
                clickThrottle(game,owner);
                stage++;
            }
            case 66 -> {
                if(owner.armrestLevel(NativeCockpitThrottle.LEFT)==0)return;
                game.options.keyShift.setDown(true);
                wait=ticks;stage=6;
            }
            case 6 -> {
                if(player.isPassenger()||ticks<wait+8)return;
                game.options.keyShift.setDown(false);
                player.getInventory().selected=1;
                player.connection.send(new ServerboundSetCarriedItemPacket(1));
                wait=ticks;stage++;
            }
            case 7 -> {
                if(ticks<wait+8||!player.getMainHandItem().is(Items.DIAMOND_PICKAXE))return;
                game.gameMode.startDestroyBlock(MINE,Direction.SOUTH);
                stage++;
            }
            case 8 -> {
                if(game.level.isEmptyBlock(ROOT)&&game.level.isEmptyBlock(MINE)){
                    game.gameMode.stopDestroyBlock();
                    wait=ticks;stage++;
                }else game.gameMode.continueDestroyBlock(MINE,Direction.SOUTH);
            }
            case 9 -> {
                if(ticks<wait+8)return;
                System.out.println("[vandorlabs][cockpit-frame] client PASS");
                game.stop();
                stage++;
            }
        }
    }

    private static void cameraOverview(Minecraft game){
        game.player.setXRot(26);
        game.player.xRotO=26;
        game.player.setYRot(180);
        game.player.yRotO=180;
    }

    private static void select(Minecraft game,int slot){
        game.player.getInventory().selected=slot;
        game.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static void aimLift(Minecraft game){aim(game,Vec3.atLowerCornerOf(ROOT).add(1.71875,.68125,.128125));}
    private static void aim(Minecraft game,Vec3 target){
        var delta=target.subtract(game.player.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
        game.player.setYRot(yaw);
        game.player.setXRot(pitch);
        game.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw,pitch,game.player.onGround()));
    }

    private static void press(Minecraft game){
        var eye=game.player.getEyePosition();
        var end=eye.add(game.player.getViewVector(1).scale(4));
        var hit=game.level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,game.player));
        check(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(MINE)
            &&NativeCockpitGeometry.button(ROOT,Direction.NORTH,MINE,eye,end)!=null,"real view points at the native screen-lift button");
        game.gameMode.useItemOn(game.player,InteractionHand.MAIN_HAND,hit);
    }

    private static void click(Minecraft game,BlockPos pos,Direction face){
        var point=Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
        game.gameMode.useItemOn(game.player,InteractionHand.MAIN_HAND,new BlockHitResult(point,face,pos,false));
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) throws Exception {
        if(!enabled()||screenshot==null)return;
        var game=Minecraft.getInstance();
        var path=game.gameDirectory.toPath().resolve("screenshots/"+screenshot);
        try(var image=Screenshot.takeScreenshot(game.getMainRenderTarget())){
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
        }finally{screenshot=null;}
    }
}

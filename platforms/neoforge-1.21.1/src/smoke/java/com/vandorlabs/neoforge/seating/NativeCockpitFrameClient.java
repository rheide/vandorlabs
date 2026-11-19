package com.vandorlabs.neoforge.seating;

import java.nio.file.Files;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
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
                click(game,MINE,Direction.SOUTH);
                stage++;
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
                game.options.keyShift.setDown(true);
                wait=ticks;stage++;
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

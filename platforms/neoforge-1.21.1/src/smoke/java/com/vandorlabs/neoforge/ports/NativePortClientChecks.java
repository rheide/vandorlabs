package com.vandorlabs.neoforge.ports;

import com.vandorlabs.neoforge.ports.client.NativePortScreen;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.ports.ResourcePortSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.network.PacketDistributor;

/** Actual player placement/editor/resource ownership proof, with one atlas/icon screenshot. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativePortClientChecks {
    private static int stage,ticks,checks,window;private static boolean capture,drawn,catalogChecked;private static NativePortData.Configuration configured;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native port client stage="+stage+" tick="+ticks+": "+why);}
    private static void mode(int operation){PacketDistributor.sendToServer(new NativePortClientFixture.Mode(operation));}
    private static boolean ack(Minecraft mc,int operation){return mc.level.getBlockState(NativePortClientFixture.ACK.east(operation)).is(Blocks.STONE);}
    private static NativeResourcePortEntity tile(Minecraft mc,BlockPos pos){return mc.level.getBlockEntity(pos) instanceof NativeResourcePortEntity owner?owner:null;}
    private static void use(Minecraft mc,BlockPos pos,Direction side){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(side.getNormal()).scale(.5)),side,pos,false));}
    private static NativePortScreen screen(Minecraft mc){return mc.screen instanceof NativePortScreen value?value:null;}
    private static void click(NativePortScreen screen,int id,int mouse){var button=screen.control(id);need(button!=null&&button.active&&screen.mouseClicked(button.getX()+button.getWidth()/2D,button.getY()+8,mouse),"actual widget "+id);}
    private static void atlas(Minecraft mc,NativeResourcePortEntity owner){var state=owner.getBlockState();var model=mc.getBlockRenderer().getBlockModel(state);var random=RandomSource.create(0);for(var side:Direction.values()){var quads=model.getQuads(state,side,random,owner.getModelData(),RenderType.solid());need(quads.size()==1,"one cached native face quad "+owner.kind+" "+side);var face=owner.settings().face(side.get3DDataValue());String name=face==ResourcePortSettings.Face.DISABLED?"port_hull":owner.kind.texture+"_"+(face==ResourcePortSettings.Face.INPUT?"in":"out")+"_off";var sprite=quads.getFirst().getSprite();need(sprite.contents().name().getPath().equals("blocks/resource_ports/"+name)&&sprite.contents().width()==64&&sprite.contents().height()==64,"actual native atlas face "+name);var onData=ModelData.builder().with(NativeResourcePortEntity.VISUAL,new NativeResourcePortEntity.Visual(owner.settings(),63,false)).build();var lit=model.getQuads(state,side,random,onData,RenderType.solid());String expected=name.replace("_off","_on");need(lit.size()==1&&lit.getFirst().getSprite().contents().name().getPath().equals("blocks/resource_ports/"+expected),"cached actual on-art quad "+expected);}}
    private static boolean held(Minecraft mc,NativePortKind kind){return mc.player.getMainHandItem().is(NativePortBindings.BLOCKS.get(kind).asItem());}
    private static void catalog(Minecraft mc){
        need(System.getProperty("vandorlabs.resourcePorts")==null&&NativePortBindings.enabled(),"ordinary public registration without enable flag");
        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(),false,mc.level.registryAccess());
        var tab=net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(net.minecraft.resources.ResourceLocation.parse("vandorlabs:vandorlabs"));
        for(var name:new String[]{"power_port","fluid_port","matter_port","battery_block","hexagonal_fuel_tank"}){
            var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vandorlabs",name);var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);var state=block.defaultBlockState();
            need(block!=Blocks.AIR&&item==block.asItem(),"canonical public block/item "+name);
            need(tab.getDisplayItems().stream().filter(stack->stack.is(item)).count()==1,"one creative entry "+name);
            need(net.minecraft.world.item.CreativeModeTabs.searchTab().getDisplayItems().stream().filter(stack->stack.is(item)).count()==1,"one vanilla search entry "+name);
            need(state.getDestroySpeed(mc.level,NativePortClientFixture.POWER)==5&&block.getExplosionResistance()==6&&state.requiresCorrectToolForDrops(),"Source hardness5/resistance6/tool required "+name);
            need(state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)&&state.is(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL),"Source canonical pickaxe/stone tags "+name);
            need(!new ItemStack(net.minecraft.world.item.Items.WOODEN_PICKAXE).isCorrectToolForDrops(state)&&new ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE).isCorrectToolForDrops(state),"Source wood refused/stone accepted "+name);
        }
        catalogChecked=true;System.out.println("[vandorlabs][port-client] catalog PASS canonical5/creative5/search5/SourceHardness5Resistance6StoneTool/noEnableFlag");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!NativePortClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==40&&mc.level==null){System.out.println("[vandorlabs][port-client] client PASS checks="+checks+" publicCatalog5/SourceMetalParity5/actualSurvival3Ports/verticalDefaults/emptyHandAndConfigurator/previousOptions/boundedSettings/stale/nativeAtlas/iconPadding/minePickupPlace37");stage=41;mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;
        need(++ticks<2400,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" screen="+mc.screen);mc.getToasts().clear();mc.gui.getChat().clearMessages(true);
        if(!catalogChecked)catalog(mc);var power=tile(mc,NativePortClientFixture.POWER);var fluid=tile(mc,NativePortClientFixture.FLUID);var matter=tile(mc,NativePortClientFixture.MATTER);var gui=screen(mc);
        switch(stage){
            case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!held(mc,NativePortKind.POWER)||mc.player.position().distanceToSqr(.5,98,-.5)>.01||mc.player.getXRot()<70)return;use(mc,NativePortClientFixture.POWER.below(),Direction.UP);stage=1;}
            case 1->{if(power==null||!mc.player.getMainHandItem().isEmpty())return;need(power.settings().face(1)==ResourcePortSettings.Face.INPUT,"vertical player-facing native default");atlas(mc,power);use(mc,NativePortClientFixture.POWER,Direction.UP);stage=2;}
            case 2->{if(gui==null)return;need(gui.rateField()!=null&&gui.rateField().getValue().isEmpty(),"throughput starts blank");mode(0);click(gui,0,1);stage=3;}
            case 3->{if(!ack(mc,0)||power.settings().face(0)!=ResourcePortSettings.Face.INPUT)return;gui.rateField().setValue("7");click(gui,6,0);stage=4;}
            case 4->{if(power.settings().throughput!=7||!power.settings().pullInputs)return;gui.rateField().setValue("");click(gui,7,1);stage=5;}
            case 5->{if(power.settings().throughput!=0||power.settings().onTexture!=ResourcePortSettings.OnTexture.CONNECTED)return;need(gui.editingConfiguration().settings().throughput==0,"blank means unlimited");gui.rateField().setValue("7");click(gui,21,0);stage=6;}
            case 6->{if(gui.channelField()==null)return;gui.channelField().setValue("1,2");click(gui,8,1);stage=7;}
            case 7->{if(power.configuration().redstoneMode()!=2||!power.configuration().channels().equals(com.vandorlabs.redstone.ChannelList.of(1,2)))return;configured=power.configuration();click(gui,9,0);mode(1);stage=8;}
            case 8->{if(!ack(mc,1)||!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))return;use(mc,NativePortClientFixture.POWER,Direction.UP);stage=9;}
            case 9->{if(gui==null)return;need(gui.editingConfiguration().equals(configured),"same dedicated Configurizer controls/configuration");window=gui.getMenu().containerId;click(gui,22,0);gui.housingPicker().searchField().setValue(com.vandorlabs.neoforge.ModernFinishCatalog.option(1).texture());stage=90;}
            case 90->{var picker=gui.housingPicker();int index=picker.rows().indexOf(1);need(index>=0&&index<picker.visible(),"filtered Housing row");need(gui.mouseClicked(picker.searchField().getX()+8,picker.top()+index*22+10,0),"actual Housing material row");stage=91;}
            case 91->{if(power.settings().housingChoice!=1)return;configured=power.configuration();var state=power.getBlockState();var model=mc.getBlockRenderer().getBlockModel(state);for(var face:Direction.values())if(power.settings().face(face.get3DDataValue())==ResourcePortSettings.Face.DISABLED){var quads=model.getQuads(state,face,RandomSource.create(0),power.getModelData(),RenderType.solid());need(quads.size()==1&&quads.getFirst().getSprite().contents().name().toString().equals(com.vandorlabs.neoforge.ModernFinishCatalog.option(1).texture()),"Housing changes only disabled artwork");need(quads==model.getQuads(state,face,RandomSource.create(0),power.getModelData(),RenderType.solid()),"Housing records reused on repeated draw");}mode(2);capture=true;stage=10;}
            case 10->{if(!ack(mc,2)||capture)return;click(gui,9,0);PacketDistributor.sendToServer(new NativePortSettingsPayload(window,NativePortClientFixture.POWER,NativePortData.placed(Direction.NORTH.get3DDataValue())));mode(3);stage=11;}
            case 11->{if(!ack(mc,3)||!held(mc,NativePortKind.FLUID)||mc.player.position().distanceToSqr(2.5,96,-3)>.01||Math.abs(mc.player.getXRot())>1)return;use(mc,NativePortClientFixture.FLUID.below(),Direction.UP);stage=12;}
            case 12->{if(fluid==null||!mc.player.getMainHandItem().isEmpty())return;atlas(mc,fluid);use(mc,NativePortClientFixture.FLUID,Direction.NORTH);stage=13;}
            case 13->{if(gui==null)return;need(gui.getMenu().tile.kind==NativePortKind.FLUID,"Fluid empty-hand dedicated dialog");mode(4);click(gui,9,0);mode(5);stage=14;}
            case 14->{if(!ack(mc,5)||!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))return;use(mc,NativePortClientFixture.FLUID,Direction.NORTH);stage=15;}
            case 15->{if(gui==null)return;need(gui.getMenu().tile.kind==NativePortKind.FLUID,"Fluid Configurizer dedicated dialog");mode(6);click(gui,9,0);mode(7);stage=16;}
            case 16->{if(!ack(mc,7)||!held(mc,NativePortKind.MATTER)||mc.player.position().distanceToSqr(4.5,96,-3)>.01)return;use(mc,NativePortClientFixture.MATTER.below(),Direction.UP);stage=17;}
            case 17->{if(matter==null||!mc.player.getMainHandItem().isEmpty())return;atlas(mc,matter);use(mc,NativePortClientFixture.MATTER,Direction.NORTH);stage=18;}
            case 18->{if(gui==null)return;need(gui.getMenu().tile.kind==NativePortKind.MATTER,"Matter empty-hand dedicated dialog");mode(8);click(gui,9,0);mode(9);stage=19;}
            case 19->{if(!ack(mc,9)||!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))return;use(mc,NativePortClientFixture.MATTER,Direction.NORTH);stage=20;}
            case 20->{if(gui==null)return;need(gui.getMenu().tile.kind==NativePortKind.MATTER,"Matter Configurizer dedicated dialog");mode(10);click(gui,9,0);mode(11);stage=21;}
            case 21->{if(!ack(mc,11)||!mc.player.getMainHandItem().is(net.minecraft.world.item.Items.IRON_PICKAXE)||mc.player.position().distanceToSqr(.5,96,-3)>.01)return;mc.gameRenderer.pick(1);need(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(NativePortClientFixture.POWER),"fresh physical vanilla mining ray");mc.gameMode.startDestroyBlock(NativePortClientFixture.POWER,Direction.NORTH);stage=22;}
            case 22->{if(!mc.level.getBlockState(NativePortClientFixture.POWER).isAir()){mc.gameMode.continueDestroyBlock(NativePortClientFixture.POWER,Direction.NORTH);return;}mc.options.keyAttack.setDown(false);mc.gameMode.stopDestroyBlock();mode(12);stage=23;}
            case 23->{if(!ack(mc,12)||mc.player.position().distanceToSqr(.5,96,.5)>.01)return;if(mc.player.getInventory().items.stream().noneMatch(stack->stack.is(NativePortBindings.BLOCKS.get(NativePortKind.POWER).asItem())))return;mode(13);stage=24;}
            case 24->{if(!ack(mc,13)||!held(mc,NativePortKind.POWER)||mc.player.getMainHandItem().getCount()!=1)return;use(mc,NativePortClientFixture.REPLACED.below(),Direction.UP);stage=25;}
            case 25->{if(tile(mc,NativePortClientFixture.REPLACED)==null||!mc.player.getMainHandItem().isEmpty())return;need(tile(mc,NativePortClientFixture.REPLACED).configuration().equals(configured),"replacement configuration public roundtrip");mode(14);stage=26;}
            case 26->{if(!ack(mc,14))return;mc.level.disconnect();mc.disconnect();stage=40;}
            default->{}
        }
    }
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){if(!NativePortClientFixture.enabled()||!capture||!(event.getScreen() instanceof NativePortScreen))return;var g=event.getGuiGraphics();for(var kind:NativePortKind.values()){int x=260+kind.ordinal()*40;g.fill(x,312,x+24,336,0xff202830);g.renderItem(new ItemStack(NativePortBindings.BLOCKS.get(kind)),x+4,316);}g.flush();drawn=true;}
    private static boolean kindColor(int rgba,NativePortKind kind){int r=rgba&255,g=rgba>>>8&255,b=rgba>>>16&255;return switch(kind){case POWER->r>70&&r>g*1.4&&r>b*1.4;case FLUID->b>70&&b>r*1.4&&b>g*1.15;case MATTER->r>70&&g>50&&r>b*1.5&&g>b*1.5;};}
    @SubscribeEvent public static void screenshot(RenderFrameEvent.Post event)throws Exception{if(!NativePortClientFixture.enabled()||!capture||!drawn)return;var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){int scale=(int)mc.getWindow().getGuiScale();for(var kind:NativePortKind.values()){int ink=0,colored=0;for(int y=0;y<16*scale;y++)for(int x=0;x<16*scale;x++){int pixel=image.getPixelRGBA((264+kind.ordinal()*40)*scale+x,316*scale+y)&0xffffff;if(pixel==0x302820)continue;if(kindColor(pixel,kind))colored++;ink++;need(x>=scale&&x<15*scale&&y>=scale&&y<15*scale,"actual padded icon "+kind+" x="+x+" y="+y);}need(ink>30,"visible actual inventory icon "+kind);need(colored>=scale*scale,"visible red/blue/yellow kind accent "+kind+" coloredPixels="+colored);}var path=mc.gameDirectory.toPath().resolve("screenshots/native-resource-ports.png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);}capture=false;drawn=false;}
}

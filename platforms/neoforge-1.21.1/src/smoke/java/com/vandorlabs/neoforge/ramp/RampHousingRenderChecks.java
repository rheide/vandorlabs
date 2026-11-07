package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Actual authored JSON bake and item rendering; temporary registry hosts exercise graphics only. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class RampHousingRenderChecks {
    private static final String[] IDS={"programmable_ramp","programmable_ramp_slab"};
    private static final String[] MODES={"ramp","filled_ramp","lift","extend","escalator_up","escalator_down","moat"};
    private static boolean enabled(){return Boolean.getBoolean("vandorlabs.rampHousingCheck");}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("vandorlabs",path);}
    @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private static final Map<String,Block> temporary=new LinkedHashMap<>();
        private static net.minecraft.world.level.block.entity.BlockEntityType<NativeRampEntity> type;
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){
            if(!enabled())return;
            event.register(Registries.BLOCK,h->{for(String name:IDS){if(com.vandorlabs.neoforge.VandorLabsBootstrap.BLOCKS.getEntries().stream().anyMatch(entry->entry.getId().equals(id(name))))continue;var block=name.endsWith("_slab")?new NativeRampSlabBlock(BlockBehaviour.Properties.of(),()->type):new NativeRampBlock(BlockBehaviour.Properties.of(),()->type);temporary.put(name,block);h.register(id(name),block);}});
            event.register(Registries.ITEM,h->temporary.forEach((name,block)->h.register(id(name),new BlockItem(block,new Item.Properties()))));
            event.register(Registries.BLOCK_ENTITY_TYPE,h->{if(temporary.isEmpty())return;type=net.minecraft.world.level.block.entity.BlockEntityType.Builder.<NativeRampEntity>of((pos,state)->new NativeRampEntity(type,pos,state,state.getBlock() instanceof NativeRampSlabBlock),temporary.values().toArray(Block[]::new)).build(null);h.register(id("ramp_housing_graphics"),type);});
        }
    }
    private static final class Gallery extends Screen {
        Gallery(){super(net.minecraft.network.chat.Component.literal("Ramp controller housing"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics gui,int x,int y,float partial){}
    }
    private static int checks,generation,frames;
    private static boolean capture,done;
    private static ItemStack[] stacks;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Ramp housing generation="+generation+": "+why);}
    private static Object field(BlockState state,String name){return state.getValues().entrySet().stream().filter(entry->entry.getKey().getName().equals(name)).findFirst().orElseThrow().getValue();}
    private static void prepare(){
        var mc=Minecraft.getInstance();int states=0;
        for(int family=0;family<2;family++){
            var block=BuiltInRegistries.BLOCK.get(id(IDS[family]));need(block!=Blocks.AIR,"registered graphics host="+IDS[family]);
            for(var state:block.getStateDefinition().getPossibleStates()){
                int mode=(Integer)field(state,"mode");boolean active=(Boolean)field(state,"active");var scan=(Direction)field(state,"active_face");var facing=(Direction)field(state,"facing");var effective=scan.getAxis()==Direction.Axis.Y?scan:facing;
                var model=mc.getBlockRenderer().getBlockModel(state);boolean modeOnFace=false,halfPanel=false,status=false;String front="vandorlabs:block/"+(family==1&&effective.getAxis()!=Direction.Axis.Y?"ramp_slab_"+MODES[mode]+"_"+(active?"on":"off"):"ramp_mode_"+MODES[mode]);int quads=0;
                for(int side=-1;side<6;side++)for(var quad:model.getQuads(state,side<0?null:Direction.from3DDataValue(side),net.minecraft.util.RandomSource.create(0),ModelData.EMPTY,null)){
                    quads++;String sprite=quad.getSprite().contents().name().toString();need(!sprite.contains("missingno"),"actual baked sprite="+state);if(quad.getDirection()==effective&&sprite.equals(front))modeOnFace=true;if(sprite.endsWith(active?"_on":"_off"))status=true;
                    if(family==1){var half=(Half)field(state,"half");float lo=half==Half.BOTTOM?0:.5F,hi=lo+.5F;var vertices=quad.getVertices();for(int vertex=0;vertex<4;vertex++){float y=Float.intBitsToFloat(vertices[vertex*8+1]);need(y>=lo&&y<=hi,"horizontal half-height model on every scan face="+state+" y="+y);}if(quad.getDirection().getAxis()!=Direction.Axis.Y){float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;for(int vertex=0;vertex<4;vertex++){float v=Float.intBitsToFloat(vertices[vertex*8+5]);min=Math.min(min,v);max=Math.max(max,v);}var spriteQuad=quad.getSprite();float actual=(max-min)/(spriteQuad.getV1()-spriteQuad.getV0()),expected=.5F*(1-spriteQuad.uvShrinkRatio());need(Math.abs(actual-expected)<.00001F,"authored half-atlas side after engine UV shrink="+state+" actual="+actual+" expected="+expected);halfPanel=true;}}
                }
                need(quads>=6&&modeOnFace&&status&&(family==0||halfPanel),"actual face artwork/mode="+state+" quads="+quads);states++;
            }
        }
        need(states==1008,"all listed horizontal travel/scan/activity/mode/half states="+states);
        stacks=new ItemStack[16];
        for(int i=0;i<16;i++){
            int family=i>=7&&i<14?1:0,mode=i<14?i%7:0;var tag=new CompoundTag();tag.putByteArray("OpaqueOwner",new byte[65536]);var choices=new CompoundTag();choices.putBoolean("ExtendSegments",mode==1||mode==3);choices.putBoolean("Elevator",mode==2||mode==3);choices.putInt("EscalatorMode",mode==4?1:mode==5?2:0);choices.putBoolean("MoatMode",mode==6);
            if(i==14){var generic=new CompoundTag();generic.putBoolean("MoatMode",true);tag.put("BlockEntityTag",generic);}else if(i==15)tag.putString("RampSettings","wrong type");else tag.put("RampSettings",choices);
            var stack=new ItemStack(BuiltInRegistries.ITEM.get(id(IDS[family])));stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));stacks[i]=stack;need(NativeRampHousingClient.mode(stack)==mode,"outer-only component mode="+i);var model=mc.getItemRenderer().getModel(stack,null,null,0);boolean found=false;for(int side=-1;side<6;side++)for(var quad:model.getQuads(null,side<0?null:Direction.from3DDataValue(side),net.minecraft.util.RandomSource.create(0),ModelData.EMPTY,null))if(quad.getSprite().contents().name().equals(id("block/ramp_mode_"+MODES[mode])))found=true;need(found,"actual clamped item override="+i);
        }
        var original=stacks[6].get(DataComponents.CUSTOM_DATA).copyTag();for(int i=0;i<30000;i++)NativeRampHousingClient.mode(stacks[i%16]);var meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId(),before=meter.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)NativeRampHousingClient.mode(stacks[i%16]);long bytes=meter.getThreadAllocatedBytes(thread)-before;need(bytes<=128&&original.equals(stacks[6].get(DataComponents.CUSTOM_DATA).copyTag()),"warmed read-only mode predicate="+bytes);System.out.println("[vandorlabs][ramp-housing-render] generation="+generation+" states="+states+" items=14 warm10000="+bytes);
    }
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event){
        if(!enabled()||done)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;if(event.getScreen() instanceof AccessibilityOnboardingScreen intro){intro.onClose();return;}if(event.getScreen() instanceof TitleScreen){mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Gallery());return;}if(!(event.getScreen() instanceof Gallery))return;if(reload!=null){if(!reload.isDone())return;reload.join();reload=null;}if(stacks==null)prepare();var gui=event.getGuiGraphics();gui.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff202830);gui.flush();com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);if(frames>=4)for(int i=0;i<16;i++)gui.renderItem(stacks[i],40+i%8*32,40+i/8*32);gui.flush();frames++;capture=frames==4||frames==9;
    }
    @SubscribeEvent public static void shot(RenderFrameEvent.Post event)throws Exception{
        if(!enabled()||!capture||done)return;capture=false;var mc=Minecraft.getInstance();try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){var path=mc.gameDirectory.toPath().resolve("screenshots/ramp-housing-"+generation+"-"+(frames==4?"disabled":"positive")+".png");java.nio.file.Files.createDirectories(path.getParent());shot.writeToFile(path);double scale=mc.getWindow().getGuiScale();for(int i=0;i<16;i++){int ink=0;for(int y=0;y<16*scale;y++)for(int x=0;x<16*scale;x++)if((shot.getPixelRGBA((int)((40+i%8*32)*scale)+x,(int)((40+i/8*32)*scale)+y)&0xffffff)!=0x302820){ink++;need(x>=1&&y>=1&&x<16*scale-1&&y<16*scale-1,"visible item padding="+i+" pixel="+x+","+y);}need(frames==4?ink==0:ink>100,"actual item/disabled="+i+" ink="+ink);}}
        if(frames==9){if(generation++==0){frames=0;stacks=null;reload=mc.reloadResourcePacks();}else{done=true;System.out.println("[vandorlabs][ramp-housing-render] title PASS checks="+checks+" actual models/side UVs/item modes/padding/reload/disabled; placed runtime acceptance separate");mc.stop();}}
    }
}

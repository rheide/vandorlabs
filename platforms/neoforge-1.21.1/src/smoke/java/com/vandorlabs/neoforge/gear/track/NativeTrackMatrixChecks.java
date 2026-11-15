package com.vandorlabs.neoforge.gear.track;

import com.vandorlabs.neoforge.gear.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Installed ordinary placement and actual world ticks for all Track arm frames and styles. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeTrackMatrixChecks {
    private record Profile(int index,BlockPos root,Direction mount,NativeGearDefinition definition,int style,boolean rotate){}
    private static final List<Profile> PROFILES=new ArrayList<>();
    private static int stage,ticks,checks;
    private static boolean running;
    private static void need(boolean value,Profile p,String why){checks++;if(!value)throw new IllegalStateException("Track matrix stage="+stage+" tick="+ticks+" profile="+p+": "+why);}
    private static NativeGearEntity owner(ServerLevel world,Profile p){var tile=NativeGearFormation.at(world,p.root);need(tile!=null,p,"installed owner");return tile;}
    private static BlockPos cell(Profile p,int x,int z){return p.root.offset(NativeGearOrientation.vector(p.mount,new BlockPos(x,-1,z)));}
    private static ItemStack configured(String id,CompoundTag choices,int count){var stack=new ItemStack(NativeGearBindings.ITEMS.get(id),count);var data=new CompoundTag();data.put(NativeGearItem.SETTINGS,choices.copy());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return stack;}
    @SubscribeEvent public static void started(net.neoforged.neoforge.event.server.ServerStartedEvent event){if(!Boolean.getBoolean("vandorlabs.trackMatrix"))return;try{
        var world=event.getServer().overworld();int index=0;for(int size=1;size<=2;size++)for(int mode=0;mode<3;mode++)for(Direction mount:Direction.values())for(int style=0;style<3;style++){
            var definition=mode==0?(size==1?NativeGearDefinition.EXTENDER:NativeGearDefinition.LARGE_EXTENDER):(size==1?NativeGearDefinition.ROTATING:NativeGearDefinition.LARGE_ROTATING);var root=new BlockPos(22000+index%12*16,120,22000+index/12*16);PROFILES.add(new Profile(index++,root,mount,definition,style,mode==2));}
        var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(world);actor.setGameMode(GameType.SURVIVAL);actor.setYRot(0);
        for(var p:PROFILES){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){world.setChunkForced(p.root.getX()/16+x,p.root.getZ()/16+z,true);world.getChunk(p.root.getX()/16+x,p.root.getZ()/16+z);}world.setBlock(p.root.relative(p.mount.getOpposite()),Blocks.STONE.defaultBlockState(),3);for(var floor:BlockPos.betweenClosed(p.root.offset(-4,-4,-4),p.root.offset(4,-4,4)))world.setBlock(floor,Blocks.STONE.defaultBlockState(),3);
            actor.setPos(p.root.getX()+3,p.root.getY()+3,p.root.getZ()-3);var settings=NativeGearSettings.defaults().editableChoices();settings.putBoolean("RotatePayload",p.rotate);settings.putInt("HingeMode",2);settings.putBoolean("MiddleHinge",true);var arm=configured(p.definition.id,settings,2);actor.setItemInHand(InteractionHand.MAIN_HAND,arm);var support=p.root.relative(p.mount.getOpposite());need(actor.gameMode.useItemOn(actor,world,arm,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(p.mount.getNormal()).scale(.5)),p.mount,support,false)).consumesAction()&&arm.getCount()==1,p,"actual supported arm placement");
            var choices=NativeGearSettings.defaults().editableChoices();choices.putInt("TrackStyle",p.style);var tracks=configured("compact_track",choices,5);actor.setItemInHand(InteractionHand.MAIN_HAND,tracks);
            for(int n=0;n<4;n++){var destination=cell(p,n&1,n>>1);BlockPos previous=n==0?p.root:cell(p,n==1?0:n==2?0:1,n==1?0:0);var offset=destination.subtract(previous);var side=Direction.getNearest(offset.getX(),offset.getY(),offset.getZ());need(actor.gameMode.useItemOn(actor,world,tracks,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(previous).add(Vec3.atLowerCornerOf(side.getNormal()).scale(.5)),side,previous,false)).consumesAction(),p,"actual Track placement part="+n);need(world.getBlockEntity(destination) instanceof NativeTrackEntity track&&track.mount()==p.mount&&track.style().ordinal()==p.style,p,"native part/mount/style="+n+" actual="+world.getBlockState(destination)+" held="+tracks+" tile="+world.getBlockEntity(destination));}
            need(tracks.getCount()==1,p,"one consumed item per Track");}
        running=true;
    }catch(Throwable failed){fail(event.getServer(),failed);}}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(!running)return;try{ticks++;var world=event.getServer().overworld();if(ticks>300)throw new IllegalStateException("Track matrix timed out stage="+stage);
        if(stage==0&&ticks>=12){for(var p:PROFILES){var root=owner(world,p);need(root.controller()&&!root.inert()&&root.assembly()!=null&&root.assembly().kind()==com.vandorlabs.gear.core.GearTopology.FootKind.TRACK,p,"complete scheduled formation "+root.error());need(root.assembly().feet().size()==4&&root.trackDescriptor()!=null&&root.trackDescriptor().feet().size()==4,p,"whole immutable 2x2 view");need(!root.collisionBoxes().isEmpty(),p,"authored world collision");need(root.interactionSources().size()==root.interactionBoxes(root.progress()).size(),p,"exact collision-to-source interaction correspondence");long before=NativeGearShapeCache.buildCount();var selected=world.getBlockState(p.root).getShape(world,p.root);need(!selected.isEmpty(),p,"exact full selection");for(int repeat=0;repeat<5;repeat++)world.getBlockState(p.root).getShape(world,p.root);need(NativeGearShapeCache.buildCount()<=before+1,p,"immutable pose selection cache reused");need(world.getBlockState(p.root).getLightBlock(world,p.root)==0,p,"Source zero light opacity avoids geometry rebuilds");need(root.command(false),p,"retraction accepted");}stage=1;}
        else if(stage==1){boolean complete=true;for(var p:PROFILES){var root=owner(world,p);need(!root.blocked(),p,"retraction is clear "+root.error());complete&=root.progress()==0;}if(complete){for(var p:PROFILES){var root=owner(world,p);need(root.command(true),p,"reversal accepted");}stage=2;}}
        else if(stage==2){boolean complete=true;for(var p:PROFILES){var root=owner(world,p);need(!root.blocked(),p,"deployment is clear "+root.error());complete&=root.progress()==1;}if(complete){for(var p:PROFILES){var root=owner(world,p);var saved=root.saveWithFullMetadata(world.registryAccess());var copy=root.getType().create(p.root,root.getBlockState());copy.setLevel(world);copy.loadWithComponents(saved,world.registryAccess());need(copy instanceof NativeGearEntity gear&&!gear.inert()&&gear.trackDescriptor()!=null&&gear.trackDescriptor().write().equals(root.trackDescriptor().write()),p,"saved frame/descriptor roundtrip");world.destroyBlock(cell(p,1,1),false);}stage=3;}}
        else if(stage==3){boolean complete=true;for(var p:PROFILES){var root=owner(world,p);complete&=root.assembly()!=null&&root.assembly().feet().size()==3&&root.trackDescriptor()!=null&&root.trackDescriptor().feet().size()==3;}if(complete){System.out.println("[vandorlabs][modern] track-matrix PASS profiles="+PROFILES.size()+" checks="+checks);running=false;event.getServer().halt(false);}}
    }catch(Throwable failed){fail(event.getServer(),failed);}}
    private static void fail(net.minecraft.server.MinecraftServer server,Throwable failed){running=false;failed.printStackTrace();System.out.println("[vandorlabs][modern] track-matrix FAIL "+failed);server.halt(false);}
    private NativeTrackMatrixChecks(){}
}

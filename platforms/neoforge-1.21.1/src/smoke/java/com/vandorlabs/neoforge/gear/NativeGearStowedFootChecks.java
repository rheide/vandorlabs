package com.vandorlabs.neoforge.gear;

import com.vandorlabs.gear.core.GearFootPlacement;
import com.vandorlabs.gear.core.GearTopology;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Stopped visible Ski extension through both real server interaction handlers, then cold reload. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearStowedFootChecks {
    private record Scene(BlockPos base,Direction mount,NativeGearDefinition arm,boolean joined,boolean rotate){
        BlockPos local(int x,int y,int z){return base.offset(NativeGearOrientation.vector(mount,new BlockPos(x,y,z)));}
        List<BlockPos> columns(){return (joined?List.of(base,local(2,0,0)):List.of(base)).stream().sorted().toList();}
        BlockPos root(){return columns().getFirst();}
        List<BlockPos> feet(){return joined?List.of(local(0,-2,0),local(1,-2,0),local(2,-2,0)):List.of(local(0,-2,0));}
    }
    private record Attempt(NativeGearMechanismTrace.Hit hit,BlockPos added){}
    private static final String ADDED="StowedFootAdded";
    private static final List<Scene> SCENES=new ArrayList<>();
    private static ServerLevel world;private static Actor actor;private static int stage,ticks,checks;private static boolean running;
    private static final class Actor extends net.neoforged.neoforge.common.util.FakePlayer {
        Actor(ServerLevel world){super(world,new com.mojang.authlib.GameProfile(new UUID(0,2211611),"NativeStowedSki"));}
    }
    private static String phase(){return System.getProperty("vandorlabs.gearStowedFootPhase","");}
    private static void need(boolean ok,String message){checks++;if(!ok)throw new AssertionError("Native stowed Ski stage="+stage+" tick="+ticks+": "+message);}
    private static NativeGearEntity root(Scene scene){var root=NativeGearFormation.at(world,scene.root());need(root!=null&&!root.inert(),"healthy primary "+scene);return root;}
    private static void scenes(){int index=0;for(var mount:Direction.values())for(var arm:List.of(NativeGearDefinition.ROTATING,NativeGearDefinition.LARGE_ROTATING))for(boolean joined:new boolean[]{false,true})for(boolean rotate:new boolean[]{false,true}){int n=index++;SCENES.add(new Scene(new BlockPos(26000+n%8*24,110,26000+n/8*24),mount,arm,joined,rotate));}need(SCENES.size()==48,"six mounts/sizes/independentJoined/both modes");int selected=Integer.getInteger("vandorlabs.gearStowedFootSingle",-1);if(selected>=0){var only=SCENES.get(selected);SCENES.clear();SCENES.add(only);}}
    private static void ordinary(Scene scene,ItemStack stack,BlockPos support,Direction face){
        actor.setPos(scene.base.getX()-5,scene.base.getY()+4,scene.base.getZ()-5);actor.setYRot(0);actor.setXRot(0);actor.setShiftKeyDown(false);actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
        need(actor.gameMode.useItemOn(actor,world,stack,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,support,false)).consumesAction(),"Survival placement "+scene+" support="+support+" face="+face+" held="+stack);
    }
    private static void place(Scene scene){
        var settings=NativeGearSettings.defaults().choices();settings.putBoolean("JoinArms",scene.joined);settings.putBoolean("RotatePayload",scene.rotate);settings.putInt("JoinRange",4);
        for(var column:scene.columns()){
            world.setBlock(column.relative(scene.mount.getOpposite()),Blocks.STONE.defaultBlockState(),3);
            var stack=new ItemStack(NativeGearBindings.ITEMS.get(scene.arm.id),2);var data=new CompoundTag();data.put(NativeGearItem.SETTINGS,settings.copy());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            for(int part=0;part<2;part++)ordinary(scene,stack,column.relative(scene.mount,part-1),scene.mount);
            need(stack.isEmpty(),"exact two sections consumed per retained support");
        }
        var ski=new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.SKI.id),scene.feet().size());
        ordinary(scene,ski,scene.local(0,-1,0),scene.mount);
        var direction=NativeGearOrientation.vector(scene.mount,1,0,0);var side=Direction.getNearest(direction.x,direction.y,direction.z);
        for(int x=1;x<scene.feet().size();x++)ordinary(scene,ski,scene.local(x-1,-2,0),side);
        need(ski.isEmpty(),"initial Ski mesh consumes exactly its native pieces");
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){if(phase().isEmpty())return;try{
        world=event.getServer().overworld();actor=new Actor(world);actor.setGameMode(GameType.SURVIVAL);scenes();
        for(var scene:SCENES)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){int cx=(scene.base.getX()>>4)+x,cz=(scene.base.getZ()>>4)+z;world.setChunkForced(cx,cz,true);world.getChunk(cx,cz);}
        if(phase().equals("read")){stage=10;running=true;return;}
        for(var scene:SCENES){for(int x=-7;x<=8;x++)for(int y=-6;y<=7;y++)for(int z=-7;z<=7;z++)world.setBlock(scene.base.offset(x,y,z),Blocks.AIR.defaultBlockState(),18);place(scene);}running=true;
    }catch(Throwable failure){fail(failure);}}
    private static Attempt aim(NativeGearEntity owner){
        var boxes=owner.interactionBoxes(0);var sources=owner.interactionSources();
        for(int i=0;i<boxes.size();i++){
            var source=NativeGearFormation.at(world,sources.get(i));if(source==null||source.definition()!=NativeGearDefinition.SKI)continue;
            var box=boxes.get(i);var center=box.getCenter();
            for(var side:Direction.values())for(double u:new double[]{0,-.35,.35})for(double v:new double[]{0,-.35,.35}){
                var target=switch(side.getAxis()){
                    case X->new Vec3(side.getStepX()>0?box.maxX:box.minX,center.y+u*box.getYsize(),center.z+v*box.getZsize());
                    case Y->new Vec3(center.x+u*box.getXsize(),side.getStepY()>0?box.maxY:box.minY,center.z+v*box.getZsize());
                    case Z->new Vec3(center.x+u*box.getXsize(),center.y+v*box.getYsize(),side.getStepZ()>0?box.maxZ:box.minZ);
                };
                var normal=Vec3.atLowerCornerOf(side.getNormal());var eye=target.add(normal.scale(3));var look=target.subtract(eye).normalize();
                actor.setPos(eye.x,eye.y-actor.getEyeHeight(),eye.z);actor.setYRot((float)Math.toDegrees(Math.atan2(-look.x,look.z)));actor.setXRot((float)-Math.toDegrees(Math.asin(look.y)));
                var hit=NativeGearMechanismTrace.nearest(world,actor.getEyePosition(),actor.getEyePosition().add(actor.getViewVector(1).scale(actor.blockInteractionRange())),NativeGearEntity::progress);
                if(hit==null||hit.owner()!=owner||!hit.source().equals(source.getBlockPos()))continue;
                var local=NativeGearOrientation.inverse(owner.mount(),new BlockPos(hit.worldFace().getNormal()));
                final GearTopology.Cell step;try{step=GearFootPlacement.neighbor(owner.pose(0),local.getX(),local.getY(),local.getZ());}catch(IllegalArgumentException refused){continue;}
                var added=hit.source().offset(NativeGearOrientation.vector(owner.mount(),new BlockPos(step.x,0,step.z)));
                if(!world.isEmptyBlock(added)||owner.members().contains(added))continue;
                var vanilla=world.clip(new net.minecraft.world.level.ClipContext(actor.getEyePosition(),actor.getEyePosition().add(actor.getViewVector(1).scale(actor.blockInteractionRange())),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,actor));
                if(vanilla.getType()!=HitResult.Type.MISS&&actor.getEyePosition().distanceToSqr(vanilla.getLocation())+1e-8<hit.distanceSq()){var member=NativeGearFormation.at(world,vanilla.getBlockPos());if(member==null||member.controllerEntity()!=owner)continue;}
                return new Attempt(hit,added);
            }
        }
        throw new AssertionError("No exposed posed Ski side for "+owner.getBlockPos()+" mode="+owner.mode()+" mount="+owner.mount());
    }
    private static boolean append(NativeGearEntity owner,Attempt attempt,boolean ordinary){
        if(ordinary)return actor.gameMode.useItemOn(actor,world,actor.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(attempt.hit.worldPoint(),attempt.hit.worldFace(),attempt.hit.source(),false)).consumesAction();
        return NativeGearMechanismUsePayload.apply(actor,new NativeGearMechanismUsePayload(owner.getBlockPos(),owner.identity(),2));
    }
    private static String refusal(NativeGearEntity owner,Attempt attempt){
        var source=NativeGearFormation.at(world,attempt.hit.source());var block=(NativeGearBlock)((NativeGearItem)actor.getMainHandItem().getItem()).getBlock();
        var normal=attempt.added.subtract(attempt.hit.source());var side=Direction.getNearest(normal.getX(),normal.getY(),normal.getZ());
        var context=new net.minecraft.world.item.context.BlockPlaceContext(actor,InteractionHand.MAIN_HAND,actor.getMainHandItem(),new BlockHitResult(Vec3.atCenterOf(attempt.added),side,attempt.hit.source(),false)){
            @Override public BlockPos getClickedPos(){return attempt.added;}
            @Override public boolean canPlace(){return world.isEmptyBlock(attempt.added);}
        };
        var placement=NativeGearComponents.placing(owner.mount(),source.getBlockState().getValue(NativeGearBlock.FACING),()->{
            var proposed=block.getStateForPlacement(context);return " nativeCanSurvive="+proposed.canSurvive(world,attempt.added)+" restUnobstructed="+world.isUnobstructed(proposed,attempt.added,net.minecraft.world.phys.shapes.CollisionContext.of(actor))+" restShape="+proposed.getCollisionShape(world,attempt.added).bounds().move(attempt.added);
        });
        var tag=owner.saveWithFullMetadata(world.registryAccess());var offset=NativeGearOrientation.inverse(owner.mount(),attempt.added.subtract(owner.getBlockPos()));
        tag.getList("GearFeet",net.minecraft.nbt.Tag.TAG_INT_ARRAY).add(new net.minecraft.nbt.IntArrayTag(new int[]{offset.getX(),offset.getY(),offset.getZ()}));
        var copy=(NativeGearEntity)blockFor(owner).newBlockEntity(owner.getBlockPos(),owner.getBlockState());copy.loadWithComponents(tag,world.registryAccess());
        var collisions=NativeGearTargets.excluding(owner,Set.of(attempt.added),()->{var result=new ArrayList<String>();var installed=NativeGearFormation.at(world,owner.getBlockPos());result.add("excludedOwner="+NativeGearTargets.excluded(owner)+" excludedInstalled="+(installed!=null&&NativeGearTargets.excluded(installed)));
            for(var box:NativeGearGeometry.mechanismBoxes(copy,0)){var inside=box.deflate(.0001);for(var at:BlockPos.betweenClosed(BlockPos.containing(inside.minX,inside.minY,inside.minZ),BlockPos.containing(inside.maxX,inside.maxY,inside.maxZ))){var state=world.getBlockState(at);var shape=state.getCollisionShape(world,at).move(at.getX(),at.getY(),at.getZ());if(!shape.isEmpty()&&net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(net.minecraft.world.phys.shapes.Shapes.create(inside),shape,net.minecraft.world.phys.shapes.BooleanOp.AND)&&result.size()<6)result.add(at+"="+state+" preview="+inside);} }
            return result;
        });
        var installed=NativeGearFormation.at(world,owner.getBlockPos());var start=actor.getEyePosition();var end=start.add(actor.getViewVector(1).scale(actor.blockInteractionRange()));var recent=NativeGearMechanismTrace.recent(owner,start,end);var vanilla=world.clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,actor));return " recent="+recent+" vanilla="+vanilla+" menu="+NativeGearAccess.menu(actor,owner)+" history="+java.util.Arrays.toString(owner.recentInteractionPhases())+" source="+attempt.hit.source()+" target="+attempt.added+" normal="+attempt.hit.worldFace()+" held="+actor.getMainHandItem().getCount()+" phase="+owner.progress()+" feet="+owner.assembly().feet().size()+" installedSame="+(installed==owner)+" installed="+installed+" removed="+owner.isRemoved()+" ownerAddress="+owner.owner()+" worldTile="+world.getBlockEntity(owner.getBlockPos())+placement+" projectedColliders="+collisions;
    }
    private static NativeGearBlock blockFor(NativeGearEntity owner){return (NativeGearBlock)owner.getBlockState().getBlock();}
    private static void verify(Scene scene,int count){var owner=root(scene);need(owner.getBlockState().getBlock().hasDynamicShape(),"registered arm collision is tile/pose dependent");need(NativeGearTargets.excluding(owner,Set.of(),()->owner.getBlockState().getCollisionShape(world,owner.getBlockPos()).isEmpty()),"own native block shape honors the current exclusion");need(owner.controller()&&owner.assembly().kind()==GearTopology.FootKind.SKI,"one Ski controller "+scene);need(!owner.blocked()&&owner.assembly().feet().size()==count&&owner.columns().size()==scene.columns().size(),"same complete assembly "+scene+" error="+owner.error());for(var member:owner.members()){var tile=NativeGearFormation.at(world,member);need(tile!=null&&tile.mount()==scene.mount&&tile.controllerEntity()==owner,"native mounting/one owner "+member);}for(var column:scene.columns())need(world.getBlockState(column.relative(scene.mount.getOpposite())).is(Blocks.STONE),"mounting support retained");}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!running)return;try{
        need(++ticks<700,"timeout "+SCENES.stream().limit(4).map(s->s+"="+root(s).progress()+"/"+root(s).error()).toList());
        if(stage==0){for(var scene:SCENES){var owner=root(scene);if(!owner.controller()||owner.assembly()==null||owner.assembly().kind()!=GearTopology.FootKind.SKI||owner.assembly().feet().size()!=scene.feet().size()||owner.columns().size()!=scene.columns().size())return;verify(scene,scene.feet().size());owner.authorize(actor);need(owner.command(false),"real formed Ski stow "+scene);}stage=1;ticks=0;return;}
        if(stage==1){for(var scene:SCENES){var owner=root(scene);need(!owner.blocked(),"Ski stow refused "+scene+" "+owner.error());if(owner.progress()!=0||NativeGearJournal.busy(owner))return;}
            for(int n=0;n<SCENES.size();n++){var scene=SCENES.get(n);var owner=root(scene);verify(scene,scene.feet().size());var identity=owner.identity();var attempt=aim(owner);var stack=new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.SKI.id),2);actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setShiftKeyDown(true);
                if(n==0){world.setBlock(attempt.added,Blocks.BEDROCK.defaultBlockState(),3);need(!append(owner,attempt,false)&&stack.getCount()==2&&owner.assembly().feet().size()==scene.feet().size(),"occupied append refuses without item/ownership change");world.setBlock(attempt.added,Blocks.AIR.defaultBlockState(),3);}
                boolean accepted=append(owner,attempt,n%2==1);if(!accepted)System.out.println("[vandorlabs][gear-stowed-foot] refusal "+scene+refusal(owner,attempt));need(accepted,"stopped posed Ski append "+scene+" hit="+attempt.hit);need(stack.getCount()==1,"append consumes exactly one Survival item");need(owner.identity().equals(identity)&&owner.progress()==0&&!owner.target(),"append preserves same identity and stopped phase0");verify(scene,scene.feet().size()+1);need(owner.members().contains(attempt.added),"new native logical cell joins original owner");owner.getPersistentData().putLong(ADDED,attempt.added.asLong());owner.setChanged();
                need(owner.interactionBoxes(0).size()==owner.interactionSources().size(),"same-phase interaction cache invalidated for appended geometry");
            }finish("write");return;
        }
        if(stage==10){for(var scene:SCENES){var owner=root(scene);if(!owner.completeMembers())return;need(owner.progress()==0&&!owner.target(),"cold saved stow phase");verify(scene,scene.feet().size()+1);need(owner.members().contains(BlockPos.of(owner.getPersistentData().getLong(ADDED))),"cold saved appended membership");owner.authorize(actor);need(owner.command(true),"cold reversal after append");}stage=11;ticks=0;return;}
        if(stage==11){for(var scene:SCENES){var owner=root(scene);need(!owner.blocked(),"reverse after appended Ski "+scene+" "+owner.error());if(owner.progress()!=1)return;verify(scene,scene.feet().size()+1);}actor.setShiftKeyDown(false);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));for(var scene:SCENES){var owner=root(scene);var added=BlockPos.of(owner.getPersistentData().getLong(ADDED));actor.setPos(added.getX()+4,added.getY()+2,added.getZ()+4);need(actor.gameMode.destroyBlock(added),"native appended Ski removal");need(world.isEmptyBlock(added),"appended native cell removed");}stage=12;ticks=0;return;}
        if(stage==12){for(var scene:SCENES){var owner=root(scene);if(owner.assembly().feet().size()!=scene.feet().size())return;verify(scene,scene.feet().size());need(owner.progress()==1,"removal preserves native rest endpoint");}finish("read");}
    }catch(Throwable failure){fail(failure);}}
    private static void finish(String marker){running=false;System.out.println("[vandorlabs][gear-stowed-foot] "+marker+" PASS cases="+SCENES.size()+" checks="+checks+" SurvivalPlacement/48sixFaceSizeJoinedMode/posedOperation2AndOrdinaryPlacement/oneItem/samePhase/nativeOwner/obstruction/coldReload/reversal/removal");world.getServer().saveEverything(true,true,true);world.getServer().halt(false);}
    private static void fail(Throwable failure){running=false;failure.printStackTrace();System.out.println("[vandorlabs][gear-stowed-foot] "+phase()+" FAIL "+failure);if(world!=null)world.getServer().halt(false);}
}

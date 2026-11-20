package com.vandorlabs.neoforge.door;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.render.LargeDoorJoin;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Real item placement, permission checks and cold reload of two joined apertures. */
@EventBusSubscriber(modid="vandorlabs")
public final class LargeDoorJoinChecks {
    private static final BlockPos WALL=new BlockPos(31,108,31), HATCH=new BlockPos(63,108,63);
    private static final Direction FACE=Direction.SOUTH;
    private static ServerLevel pendingRead;
    private static int readTicks;
    private static final class Actor extends FakePlayer {
        BlockPos denied;
        Actor(ServerLevel level){super(level,new GameProfile(new UUID(0,531), "LargeDoorJoinTest"));}
        @Override public boolean mayUseItemAt(BlockPos pos,Direction face,ItemStack stack){
            return !pos.equals(denied)&&super.mayUseItemAt(pos,face,stack);
        }
    }

    @SubscribeEvent public static void start(ServerStartedEvent event){
        String phase=System.getProperty("vandorlabs.largeDoorJoinChecks","");
        if(!phase.equals("write")&&!phase.equals("read"))return;
        var level=event.getServer().overworld();
        var actor=new Actor(level);
        actor.setGameMode(GameType.CREATIVE);
        for(boolean horizontal:new boolean[]{false,true}){
            BlockPos origin=horizontal?HATCH:WALL;
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
                level.setChunkForced((origin.getX()>>4)+x,(origin.getZ()>>4)+z,true);
                level.getChunkAt(origin.offset(x*16,0,z*16));
            }
            if(phase.equals("write")){
                placeGroup(level,actor,origin,horizontal);
                verify(level,origin,horizontal,2,2,true);
            }
        }
        if(phase.equals("write"))finish(level,"write");
        else pendingRead=level;
    }

    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        var level=pendingRead;
        if(level==null||event.getServer()!=level.getServer()||++readTicks<6)return;
        pendingRead=null;
        for(boolean horizontal:new boolean[]{false,true}){
            var origin=horizontal?HATCH:WALL;
            verify(level,origin,horizontal,2,2,true);
            var east=cell(origin,horizontal,3,0);
            level.destroyBlock(cell(east,horizontal,1,1),false);
            NativeLargeDoorJoins.flush(level);
            need(NativeLargeDoorLifecycle.root(level,east)==null,"broken physical door has no retained ownership");
            var root=owner(level,origin);
            need(root.joinEnabled()&&root.rendersAssembly(),"left-hand assembly keeps its own Join choice after the neighbour is removed");
            var remaining=root.rootGeometrySnapshot();
            need(remaining.columns()==1&&remaining.rows()==2,"surviving stacked rectangle rebuilds and restores an outer edge");
        }
        finish(level,"read");
    }

    private static void finish(ServerLevel level,String phase){
        System.out.println("[vandorlabs][large-door-join] "+phase+" PASS actual item, four independent anchors, shared control and collision, permission, mount orientation and cold reload");
        level.getServer().halt(false);
    }

    static void prepareClient(ServerLevel level,BlockPos origin){
        var actor=new Actor(level);
        actor.setGameMode(GameType.CREATIVE);
        placeGroup(level,actor,origin,false);
        var root=owner(level,origin);
        need(NativeLargeDoorLifecycle.setState(level,root,false,false),"close the joined client fixture");
        verify(level,origin,false,2,2,false);
    }

    private static void placeGroup(ServerLevel level,Actor actor,BlockPos origin,boolean horizontal){
        for(int x=0;x<6;x++)level.setBlock(cell(origin,horizontal,x,-1),Blocks.STONE.defaultBlockState(),Block.UPDATE_KNOWN_SHAPE);
        var first=place(actor,origin,horizontal,null);
        var secondPos=cell(origin,horizontal,3,0);
        need(!first.joinEnabled(),"fresh item defaults to independent");
        var altered=first.itemSettings();
        altered.putBoolean("SpaceFramed",false);
        altered.putBoolean("SpaceDoorPanel",false);
        altered.putInt("SpaceSlideDirection",2);
        first.installPlacement(NativeLargeDoorPlacement.choices(altered,0),first.getRedstoneChannels());
        var configured=first.itemSettings();
        configured.putBoolean(LargeDoorJoin.TAG,true);
        var second=place(actor,secondPos,horizontal,configured);
        need(second.joinEnabled()&&second.rendersAssembly(),"single opted-in door remains independent");
        actor.setPos(origin.getX()+.5,origin.getY()+1,origin.getZ()+.5);
        var menu=new NativeDoorMenu(NativeDoorRegistrations.MENU.get(),37,first);
        actor.containerMenu=menu;
        actor.denied=cell(secondPos,horizontal,1,1);
        need(!NativeDoorMenu.applyJoin(actor,origin,37,true)&&!first.joinEnabled(),"protected neighbour denies group edit atomically");
        actor.denied=null;
        need(!NativeDoorMenu.applyJoin(actor,origin,38,true),"wrong active menu cannot set Join");
        need(NativeDoorMenu.applyJoin(actor,origin,37,true),"authenticated editor opts first module in");
        verify(level,origin,horizontal,2,1,false);
        verify(level,secondPos,horizontal,2,1,false);
        var upper=place(actor,cell(origin,horizontal,0,3),horizontal,null);
        need(upper.itemSettings().equals(first.itemSettings()),"fresh upper module inherits complete compatible settings");
        need(upper.joinEnabled(),"fresh stacked row inherits Join from a solid supported opted-in base");
        NativeLargeDoorJoins.flush(level);
        need(first.rendersAssembly()&&second.rendersAssembly(),"L-shaped component stays independent");
        var fourth=place(actor,cell(origin,horizontal,3,3),horizontal,null);
        need(fourth.itemSettings().equals(first.itemSettings()),"ordinary fourth placement rejoins the completed rectangle first="+first.itemSettings()+" fourth="+fourth.itemSettings());
        verify(level,origin,horizontal,2,2,false);
        actor.denied=cell(secondPos,horizontal,1,1);
        need(!NativeLargeDoorLifecycle.toggle(level,origin,actor),"manual command denies when any touched reservation is protected");
        verify(level,origin,horizontal,2,2,false);
        actor.denied=null;
        need(NativeLargeDoorLifecycle.toggle(level,secondPos,actor),"member command opens all cells");
    }

    private static NativeLargeDoorEntity place(Actor actor,BlockPos anchor,boolean horizontal,CompoundTag configured){
        var item=NativeLargeDoorRegistrations.ITEM.get();
        var stack=new ItemStack(item,2);
        if(configured!=null){
            var choice=new CompoundTag();
            choice.put("SpaceDoorSettings",configured.copy());
            stack.set(DataComponents.CUSTOM_DATA,CustomData.of(choice));
        }
        var support=cell(anchor,horizontal,1,-1);
        actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
        actor.setYRot(FACE.getOpposite().toYRot());
        actor.setPos(anchor.getX()+.5,anchor.getY()+1,anchor.getZ()+.5);
        var hit=new BlockHitResult(Vec3.atCenterOf(support),horizontal?FACE:Direction.UP,support,false);
        need(item.useOn(new UseOnContext(actor,InteractionHand.MAIN_HAND,hit)).consumesAction()&&stack.getCount()==1,
                "actual item stages nine cells once at "+anchor);
        var owner=owner(actor.serverLevel(),anchor);
        need(owner.getBlockPos().equals(anchor)&&owner.isHorizontal()==horizontal,"exact physical anchor and mounting plane");
        return owner;
    }

    private static void verify(ServerLevel level,BlockPos origin,boolean horizontal,int columns,int rows,boolean open){
        NativeLargeDoorJoins.flush(level);
        var local=owner(level,origin);
        var group=NativeLargeDoorJoins.group(local);
        need(group.columns==columns&&group.rows==rows&&group.members.size()==columns*rows,"canonical bounded rectangle");
        var leader=group.anchor;
        var geometry=leader.rootGeometrySnapshot();
        need(geometry!=null&&geometry.columns()==columns&&geometry.rows()==rows,"one aperture projection");
        for(int x=0;x<columns*3;x++)for(int y=0;y<rows*3;y++){
            var pos=cell(leader.getBlockPos(),horizontal,x,y);
            var part=(NativeLargeDoorEntity)level.getBlockEntity(pos);
            var physical=owner(level,pos);
            need(physical.rendersAssembly()==(physical==leader),"only canonical leader renders and indexes geometry");
            var visual=part.largeVisual();
            need(visual.geometry()==geometry&&visual.column()==x&&visual.row()==y&&geometry.part(x,y).pos().equals(pos),
                    "per-cell collision and fluid view on correct member "+x+"/"+y);
            BlockState state=level.getBlockState(pos);
            need(state.getValue(NativeLargeDoorBlock.OPEN)==open,"raw OPEN state agrees across all members");
        }
    }

    private static NativeLargeDoorEntity owner(ServerLevel level,BlockPos pos){
        var root=NativeLargeDoorLifecycle.root(level,pos);
        need(root!=null,"owned physical door at "+pos);
        return root;
    }

    private static BlockPos cell(BlockPos origin,boolean horizontal,int x,int y){
        return NativeLargeDoorLifecycle.cell(origin,FACE,horizontal,x,y);
    }

    private static void need(boolean value,String message){if(!value)throw new AssertionError("Large Door Join: "+message);}

    private LargeDoorJoinChecks(){}
}

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
        if(phase.equals("write"))opposite(level,actor);
        if(phase.equals("write"))clicks(level,actor);
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
        // Shared grouping: an L of three keeps its bottom pair; the upper module stays single.
        verify(level,origin,horizontal,2,1,false);
        need(upper.rendersAssembly(),"L-shaped component keeps its pair and leaves the upper module single");
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

    /**
     * Reported: four joining doors placed one by one never formed one door, because a door placed
     * from the other side of the wall faced the other way. A door placed beside a joining door from
     * the opposite side takes that door's facing for the same footprint and joins it.
     */
    private static void opposite(ServerLevel level,Actor actor){
        BlockPos origin=WALL.offset(0,0,24);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setChunkForced((origin.getX()>>4)+x,(origin.getZ()>>4)+z,true);level.getChunkAt(origin.offset(x*16,0,z*16));}
        for(var at:BlockPos.betweenClosed(origin.offset(-4,-1,-4),origin.offset(10,7,4)))
            level.setBlock(at,at.getY()==origin.getY()-1?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        var first=place(actor,origin,false,null);
        need(NativeLargeDoorJoins.change(actor,first,true)&&first.joinEnabled(),"first door opts in to Join");
        // The player stands on the other side of the wall and clicks the floor under the next door's middle.
        BlockPos middle=cell(origin,false,4,0),support=middle.below();
        var stack=new ItemStack(NativeLargeDoorRegistrations.ITEM.get(),2);
        actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
        actor.setYRot(FACE.toYRot());
        actor.setPos(middle.getX()+.5,middle.getY(),middle.getZ()+.5);
        need(NativeLargeDoorRegistrations.ITEM.get().useOn(new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false))).consumesAction(),
                "second door placed from the opposite side");
        BlockPos secondAnchor=cell(origin,false,3,0);
        need(level.getBlockEntity(secondAnchor) instanceof NativeLargeDoorEntity second&&second.isAnchor()
                &&second.getBlockState().getValue(NativeLargeDoorBlock.FACING)==FACE,"second door takes the joining door's facing and footprint: "+level.getBlockState(secondAnchor));
        NativeLargeDoorJoins.flush(level);
        var group=NativeLargeDoorJoins.group(first);
        need(group.columns==2&&group.rows==1,"doors placed from opposite sides join: "+group.columns+"x"+group.rows);
        System.out.println("[vandorlabs][large-door-join] opposite-side placement joins");
    }
    /** Sneak-click {@code cell}'s {@code side} at {@code hit} with a Large Door item. */
    private static boolean click(ServerLevel level,Actor actor,BlockPos cell,Direction side,Vec3 hit){
        var stack=new ItemStack(NativeLargeDoorRegistrations.ITEM.get(),2);
        actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setShiftKeyDown(true);
        actor.setPos(hit.x+side.getStepX()*2,cell.getY(),hit.z+side.getStepZ()*2);
        try{return NativeLargeDoorRegistrations.ITEM.get().useOn(new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(hit,side,cell,false))).consumesAction()&&stack.getCount()==1;}
        finally{actor.setShiftKeyDown(false);}
    }
    private static NativeLargeDoorEntity root(ServerLevel level,BlockPos anchor){
        return level.getBlockEntity(anchor) instanceof NativeLargeDoorEntity door&&door.isAnchor()&&NativeLargeDoorLifecycle.complete(level,door)?door:null;
    }
    /**
     * Reported: joining doors one by one. Sneak-clicking an installed door's edge places the next
     * door beside it and joins them; sneak-clicking a joined door's face places the next door beyond
     * the group on that side, so four clicks build a 2x2. A joining door beside a joining door of the
     * same facing is carried by it, with no floor of its own.
     */
    private static void clicks(ServerLevel level,Actor actor){
        BlockPos origin=WALL.offset(0,0,48);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setChunkForced((origin.getX()>>4)+x,(origin.getZ()>>4)+z,true);level.getChunkAt(origin.offset(x*16,0,z*16));}
        for(var at:BlockPos.betweenClosed(origin.offset(-4,-1,-4),origin.offset(14,8,4)))
            level.setBlock(at,at.getY()==origin.getY()-1?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        var first=place(actor,origin,false,null);
        need(!first.joinEnabled(),"a fresh door starts without Join");
        // Edge: the first door's side edge (its third column) toward +across.
        Direction across=FACE.getCounterClockWise();
        BlockPos edgeCell=cell(origin,false,2,1);
        need(click(level,actor,edgeCell,across,Vec3.atCenterOf(edgeCell).add(across.getStepX()*.5,0,across.getStepZ()*.5)),"sneak-clicking a door's edge places the next door");
        var second=root(level,cell(origin,false,3,0));
        need(second!=null&&second.getBlockState().getValue(NativeLargeDoorBlock.FACING)==FACE,"the edge door sits beside the clicked one with its facing");
        need(first.joinEnabled()&&second.joinEnabled()&&second.settings().door().sliding==first.settings().door().sliding,"edge placement copies the door and joins both");
        NativeLargeDoorJoins.flush(level);
        var pair=NativeLargeDoorJoins.group(first);need(pair.columns==2&&pair.rows==1,"edge placement forms a pair: "+pair.columns+"x"+pair.rows);
        // Face: the upper part of each door's front face places a door above it.
        for(var base:new NativeLargeDoorEntity[]{first,second}){
            BlockPos faceCell=cell(base.getBlockPos(),false,1,2);
            need(click(level,actor,faceCell,FACE,Vec3.atCenterOf(faceCell).add(FACE.getStepX()*.5,.4,FACE.getStepZ()*.5)),"sneak-clicking a joined door's upper face places a door above "+base.getBlockPos());
            need(root(level,cell(base.getBlockPos(),false,0,3))!=null,"the face door sits above "+base.getBlockPos());
        }
        NativeLargeDoorJoins.flush(level);
        var square=NativeLargeDoorJoins.group(first);need(square.columns==2&&square.rows==2,"four clicked doors form one 2x2 door: "+square.columns+"x"+square.rows);
        // Beside: a joining door with no floor beneath, carried by the joining door beside it.
        BlockPos hang=cell(origin,false,9,0);
        for(int x=0;x<3;x++)level.setBlock(cell(origin,false,9+x,-1),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        for(int x=0;x<3;x++)level.setBlock(cell(origin,false,12+x,-1),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        var carrier=place(actor,hang,false,null);
        need(NativeLargeDoorJoins.change(actor,carrier,true),"carrier opts in to Join");
        BlockPos carrierEdge=cell(hang,false,2,1);
        need(click(level,actor,carrierEdge,across,Vec3.atCenterOf(carrierEdge).add(across.getStepX()*.5,0,across.getStepZ()*.5)),"a joining door is placed beside the carrier over open air");
        var carried=root(level,cell(hang,false,3,0));
        need(carried!=null&&NativeLargeDoorLifecycle.supported(level,carried.getBlockPos(),FACE,false),"the beside door is carried by its joining neighbour");
        level.updateNeighborsAt(carried.getBlockPos(),net.minecraft.world.level.block.Blocks.AIR);NativeLargeDoorJoins.flush(level);
        need(root(level,cell(hang,false,3,0))!=null,"the carried door survives neighbour checks");
        System.out.println("[vandorlabs][large-door-join] edge, face and beside placement join");
    }
    private static BlockPos cell(BlockPos origin,boolean horizontal,int x,int y){
        return NativeLargeDoorLifecycle.cell(origin,FACE,horizontal,x,y);
    }

    private static void need(boolean value,String message){if(!value)throw new AssertionError("Large Door Join: "+message);}

    private LargeDoorJoinChecks(){}
}

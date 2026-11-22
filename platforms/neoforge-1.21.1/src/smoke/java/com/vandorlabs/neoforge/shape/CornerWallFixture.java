package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ProgrammableMenu;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.glass.CornerGlassGeometryPayload;
import com.vandorlabs.render.CornerGlassGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Corner Walls on NeoForge, matching Forge's CornerWallLiveChecks. The joining player places every
 * flat depth band and every diagonal facing, size, lean and end through the item path. The checks
 * cover opposite anchors, the dialog's Size and Slope edit, and stacked corners. The client then
 * checks its own published joins and the bent model, and captures views on request.
 */
@EventBusSubscriber(modid="vandorlabs")
public final class CornerWallFixture {
    static final BlockPos ORIGIN=new BlockPos(0,120,0),DIALOG=ORIGIN.offset(0,0,40),STACK=ORIGIN.offset(-10,0,-6);
    private static final Direction[] FACINGS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private static int checks;
    static boolean enabled(){return "corner-wall-client".equals(System.getProperty("vandorlabs.transmitterPhase",""));}
    private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError("Corner wall "+text);}
    /** The flat corner beside the depth {@code depth} wall. */
    static BlockPos flatCorner(int depth){return ORIGIN.offset(depth*4+1,0,0);}
    static BlockPos diagonalWall(int mode,int facing,int lean,int end){return ORIGIN.offset(mode*24+facing*6,0,10+lean*10+end*5);}

    @SubscribeEvent public static void start(net.neoforged.neoforge.event.server.ServerStartedEvent event){
        if(!enabled())return;var server=event.getServer();var level=server.overworld();
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
        for(int cx=-2;cx<=5;cx++)for(int cz=-2;cz<=4;cz++)level.setChunkForced(cx,cz,true);
        for(BlockPos at:BlockPos.betweenClosed(ORIGIN.offset(-16,-1,-12),ORIGIN.offset(76,6,52)))
            level.setBlock(at,at.getY()==ORIGIN.getY()-1?Blocks.QUARTZ_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        System.out.println("[vandorlabs][corner-wall] actual server PID="+ProcessHandle.current().pid());
        System.out.println("[vandorlabs][corner-wall] setup PASS");
    }
    @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;
        try{
            var level=player.serverLevel();
            player.setGameMode(GameType.SURVIVAL);
            flat(level,player);diagonal(level,player);dialog(level,player);stack(level,player);
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
            pose(player,"flat");
            System.out.println("[vandorlabs][corner-wall] server PASS checks="+checks+" flat depths, diagonal facing/size/lean/end joins, opposite anchors refused, dialog size and slope, stacked corners");
        }catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][corner-wall] server FAIL "+failure);player.connection.disconnect(net.minecraft.network.chat.Component.literal("corner wall fixture failed"));}
    }
    /** The client asks for each view, and for the stack's removal, with a fixture command. */
    @SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event){
        if(!enabled())return;
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("cornerwall").requires(source->true)
                .then(net.minecraft.commands.Commands.argument("request",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context->{
                    var player=context.getSource().getPlayerOrException();String request=com.mojang.brigadier.arguments.StringArgumentType.getString(context,"request");
                    if(request.equals("remove-stack"))for(int y=0;y<4;y++)player.serverLevel().setBlock(STACK.above(y),Blocks.AIR.defaultBlockState(),3);
                    else pose(player,request);
                    return 1;
                })));
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){
        if(enabled()&&event.getEntity() instanceof ServerPlayer player){System.out.println("[vandorlabs][corner-wall] logout PASS");player.getServer().halt(false);}
    }
    /** Named views: position and look, as in the Forge gallery. */
    static double[] view(String name){
        return switch(name){
            case "flat"->new double[]{4.5,2.5,-3.5,0,35};
            case "diagonal0"->new double[]{9.5,4.5,30,180,32};
            case "diagonal1"->new double[]{33.5,4.5,30,180,32};
            case "diagonal2"->new double[]{57.5,4.5,30,180,32};
            case "stack","stack-removed"->new double[]{STACK.getX()-ORIGIN.getX()+3.5,1.5,STACK.getZ()-ORIGIN.getZ()+3.5,135,-5};
            default->throw new IllegalArgumentException(name);
        };
    }
    private static void pose(ServerPlayer player,String name){
        double[] v=view(name);
        player.connection.teleport(ORIGIN.getX()+v[0],ORIGIN.getY()+v[1],ORIGIN.getZ()+v[2],(float)v[3],(float)v[4]);
    }

    private static boolean place(ServerLevel level,ServerPlayer player,ItemStack stack,BlockPos support,Direction face,double hx,double hz){
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var target=support.relative(face,2);player.teleportTo(target.getX()+.5,target.getY(),target.getZ()+.5);
        var hit=new BlockHitResult(Vec3.atLowerCornerOf(support).add(hx,.5,hz),face,support,false);
        return player.gameMode.useItemOn(player,level,stack,InteractionHand.MAIN_HAND,hit).consumesAction();
    }
    private static NativeCornerWallBlock corner(boolean sloped){return sloped?VandorLabsBootstrap.PROGRAMMABLE_DIAGONAL_CORNER_WALL.get():VandorLabsBootstrap.PROGRAMMABLE_CORNER_WALL.get();}
    private static ItemStack item(boolean sloped){return new ItemStack(sloped?VandorLabsBootstrap.PROGRAMMABLE_DIAGONAL_CORNER_WALL_ITEM.get():VandorLabsBootstrap.PROGRAMMABLE_CORNER_WALL_ITEM.get(),2);}
    private static void flatWall(ServerLevel level,BlockPos at,Direction facing,int depth){
        level.setBlock(at,VandorLabsBootstrap.PROGRAMMABLE_WALL.get().defaultBlockState().setValue(NativePanelBlock.FACING,facing).setValue(NativeFlatWallBlock.DEPTH,depth),3);
    }
    private static void diagonalWall(ServerLevel level,BlockPos at,Direction facing,boolean inverted,int size){
        level.setBlock(at,VandorLabsBootstrap.PROGRAMMABLE_DIAGONAL_WALL.get().defaultBlockState().setValue(NativePanelBlock.FACING,facing).setValue(NativeDiagonalWallBlock.INVERTED,inverted),3);
        need(level.getBlockEntity(at) instanceof NativeWallEntity wall&&wall.configureDiagonal(size,0,0,facing,inverted),"configures the diagonal wall at "+at);
        need(NativeDiagonalWallTopology.mode(level,at)==size,"diagonal wall keeps size "+size);
    }

    /** Flat corners: a wall facing north at each depth band, a corner clicked on its east end, then the matching second wall. */
    private static void flat(ServerLevel level,ServerPlayer player){
        var corner=corner(false);
        for(int depth=0;depth<3;depth++){
            BlockPos at=flatCorner(depth),a=at.west();
            flatWall(level,a,Direction.NORTH,depth);
            var stack=item(false);
            need(place(level,player,stack,a,Direction.EAST,1,.5),"placed against a depth "+depth+" wall");
            need(stack.getCount()==1,"survival placement uses one item");
            var placed=level.getBlockState(at);
            need(placed.getBlock()==corner,"beside the clicked depth "+depth+" wall: "+placed);
            need(NativeCornerWallBlock.joins(level,at,a),"flat corner continues the depth "+depth+" wall: "+placed);
            boolean completed=false;
            for(Direction step:new Direction[]{Direction.NORTH,Direction.SOUTH}){
                BlockPos b=at.relative(step);
                for(Direction facing:new Direction[]{Direction.EAST,Direction.WEST})for(int d=0;d<3&&!completed;d++){
                    flatWall(level,b,facing,d);
                    if(NativeCornerWallBlock.joins(level,at,b))completed=true;
                }
                if(completed)break;
                level.setBlock(b,Blocks.AIR.defaultBlockState(),3);
            }
            need(completed,"a perpendicular flat wall completes the depth "+depth+" corner");
            need(corner.joinedEnds(level,at,level.getBlockState(at),CornerGlassGeometry.FULL)==3,"both ends of the depth "+depth+" corner are joined");
            var host=(NativeWallEntity)level.getBlockEntity(at);NativeWallTopology.refresh(host);
            need(host.wallAppearance().geometry() instanceof NativeCornerWallGeometry published&&published.joined()==3,"publishes both joined ends at depth "+depth);
            // A plain Programmable Wall must not mistake the corner for a straight neighbour.
            var wall=(NativeWallEntity)level.getBlockEntity(a);NativeWallTopology.refresh(wall);
            need(wall.wallAppearance().geometry() instanceof NativeFlatWallGeometry.Geometry flat&&flat.join()==null,"the flat wall stays straight beside a corner at depth "+depth);
        }
    }
    /** Diagonal corners: every facing, size, lean and end of a diagonal wall, as for Diagonal Corner Glass. */
    private static void diagonal(ServerLevel level,ServerPlayer player){
        var corner=corner(true);int joined=0;
        for(int mode=0;mode<3;mode++)for(int f=0;f<4;f++)for(int lean=0;lean<2;lean++)for(int end=0;end<2;end++){
            Direction facing=FACINGS[f];boolean inverted=lean==1;BlockPos at0=diagonalWall(mode,f,lean,end);
            diagonalWall(level,at0,facing,inverted,mode);
            Direction across=end==0?facing.getClockWise():facing.getCounterClockWise();
            double hx=across==Direction.EAST?1:across==Direction.WEST?0:.5,hz=across==Direction.SOUTH?1:across==Direction.NORTH?0:.5;
            String label="facing="+facing+" size="+mode+" inverted="+inverted+" end="+end;
            need(place(level,player,item(true),at0,across,hx,hz),"diagonal corner placed against "+label);
            BlockPos at=at0.relative(across);
            need(level.getBlockState(at).getBlock()==corner,"diagonal corner beside the clicked wall: "+label+" got "+level.getBlockState(at));
            need(corner.size(level,at)==mode,"diagonal corner takes the clicked wall's size: "+label+" got "+corner.size(level,at));
            need(NativeCornerWallBlock.joins(level,at,at0),"diagonal corner continues the slope exactly: "+label+" corner="+level.getBlockState(at));
            BlockPos other=at.relative(across);
            diagonalWall(level,other,facing.getOpposite(),inverted,mode);
            need(!NativeCornerWallBlock.joins(level,at,other),"opposite-anchored wall does not join: "+label);
            level.setBlock(other,Blocks.AIR.defaultBlockState(),3);
            joined++;
        }
        need(joined==48,"every diagonal facing, size, lean and end");
    }
    /** Size and Slope through the real edit handler with the wall dialog's menu open. */
    private static void dialog(ServerLevel level,ServerPlayer player){
        var corner=corner(true);BlockPos at=DIALOG;
        diagonalWall(level,at.west(),Direction.NORTH,false,CornerGlassGeometry.FULL);
        need(place(level,player,item(true),at.west(),Direction.EAST,1,.5),"dialog fixture corner wall");
        player.teleportTo(at.getX()+.5,at.getY(),at.getZ()+3.5);
        // A Survival player edits with the Configurizer in hand.
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
        var menu=new ProgrammableMenu(77,player.getInventory(),at);player.containerMenu=menu;
        need(menu.stillValid(player),"dialog menu is valid for the player");
        int turn=corner.metadata(level.getBlockState(at))&3;
        need(!WallAppearancePayload.cornerWall(player,new CornerGlassGeometryPayload(at,78,CornerGlassGeometry.TALL,false,false)),"another window cannot edit the corner");
        for(int size=0;size<3;size++)for(int flags=0;flags<4;flags++){
            boolean inverted=(flags&1)!=0,far=(flags&2)!=0;
            need(WallAppearancePayload.cornerWall(player,new CornerGlassGeometryPayload(at,77,size,inverted,far)),"dialog edit size "+size+" flags "+flags);
            int now=corner.metadata(level.getBlockState(at));
            need(corner.size(level,at)==size,"dialog sets size "+size);
            need((now&3)==turn&&((now&4)!=0)==inverted&&((now&8)!=0)==far,"dialog sets slope and keeps the bevel's turn: meta="+now);
            var host=(NativeWallEntity)level.getBlockEntity(at);NativeWallTopology.refresh(host);
            need(host.wallAppearance().geometry() instanceof NativeCornerWallGeometry published&&published.corner()==corner.geometry(level.getBlockState(at),size),"publishes the edited bevel");
        }
        player.containerMenu=player.inventoryMenu;
    }
    /** Four flat corner walls stacked beside a four-high wall. */
    private static void stack(ServerLevel level,ServerPlayer player){
        var corner=corner(false);
        for(int y=0;y<4;y++){
            BlockPos a=STACK.west().above(y);
            flatWall(level,a,Direction.NORTH,0);
            need(place(level,player,item(false),a,Direction.EAST,1,.5),"stacked corner wall "+y);
            need(level.getBlockState(STACK.above(y)).getBlock()==corner,"stacked corner wall "+y+" placed");
        }
    }
    private CornerWallFixture(){}
}

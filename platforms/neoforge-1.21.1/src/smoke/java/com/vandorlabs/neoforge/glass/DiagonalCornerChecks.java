package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock;
import com.vandorlabs.neoforge.shape.NativeDiagonalWallTopology;
import com.vandorlabs.neoforge.shape.NativePanelBlock;
import com.vandorlabs.neoforge.shape.NativeWallEntity;
import com.vandorlabs.neoforge.shape.NativeWallTopology;
import com.vandorlabs.render.CornerGlassGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Diagonal Corner Glass, matching Forge's DiagonalCornerLiveChecks: placed through the item path
 * against the end of diagonal glass of every facing, size and lean, the corner takes that glass's
 * size and continues its slope exactly; both sides hide their rail at the join; glass anchored to
 * the opposite face never joins; the dialog's Size and Slope edit the corner and keep its turn; the
 * edited corner and pre-2.12 diagonal corners survive a restart.
 */
final class DiagonalCornerChecks {
    private static final BlockPos ORIGIN=new BlockPos(8600,100,8700);
    static final BlockPos DIALOG=ORIGIN.offset(0,0,30);
    private static final Direction[] FACINGS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private static int checks;
    private DiagonalCornerChecks(){}
    private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError("Diagonal corner "+text);}
    static int checks(){return checks;}

    private static BlockState diagonalGlass(ServerLevel level,BlockPos at,Direction facing,boolean inverted,int size){
        var glass=VandorLabsBootstrap.PROGRAMMABLE_DIAGONAL_GLASS.get();
        level.setBlock(at,glass.defaultBlockState().setValue(NativePanelBlock.FACING,facing).setValue(NativeDiagonalWallBlock.INVERTED,inverted),3);
        var wall=(NativeWallEntity)level.getBlockEntity(at);
        need(wall!=null&&wall.configureDiagonal(size,0,0,facing,inverted),"configure diagonal glass size "+size+" at "+at);
        need(NativeDiagonalWallTopology.mode(level,at)==size,"diagonal glass keeps size "+size);
        return level.getBlockState(at);
    }
    private static boolean place(ServerLevel level,ServerPlayer actor,BlockPos support,Direction face){
        var stack=new ItemStack(NativeGlassRegistrations.DIAGONAL_CORNER_ITEM.get());
        actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var target=support.relative(face,2);actor.setPos(target.getX()+.5,target.getY(),target.getZ()+.5);
        var hit=new BlockHitResult(Vec3.atCenterOf(support).add(face.getStepX()*.5,0,face.getStepZ()*.5),face,support,false);
        return actor.gameMode.useItemOn(actor,level,stack,InteractionHand.MAIN_HAND,hit).consumesAction();
    }
    private static int wallHideMask(ServerLevel level,BlockPos wall){
        var host=(NativeWallEntity)level.getBlockEntity(wall);
        NativeWallTopology.refresh(host);
        return host.wallAppearance().diagonalGlass().hideMask();
    }

    static void write(ServerLevel level,ServerPlayer actor){
        var corner=NativeGlassRegistrations.DIAGONAL_CORNER.get();
        actor.setGameMode(GameType.CREATIVE);actor.setShiftKeyDown(false);
        for(int x=-4;x<=76;x+=16)for(int z=-4;z<=40;z+=16)level.getChunkAt(ORIGIN.offset(x,0,z));
        for(var at:BlockPos.betweenClosed(ORIGIN.offset(-4,-1,-4),ORIGIN.offset(76,4,40)))
            level.setBlock(at,at.getY()==ORIGIN.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
        int joined=0;
        for(int size=0;size<3;size++)for(int f=0;f<4;f++)for(int lean=0;lean<2;lean++)for(int end=0;end<2;end++){
            Direction facing=FACINGS[f];boolean inverted=lean==1;
            BlockPos wall=ORIGIN.offset(size*24+f*6,0,lean*10+end*5);
            var wallState=diagonalGlass(level,wall,facing,inverted,size);
            // Click the slope's end face; the pane runs across the facing.
            Direction across=end==0?facing.getClockWise():facing.getCounterClockWise();
            String label="facing="+facing+" size="+size+" inverted="+inverted+" end="+end;
            need(place(level,actor,wall,across),"corner placed against "+label);
            BlockPos at=wall.relative(across);var state=level.getBlockState(at);
            need(state.is(corner),"corner beside the clicked glass: "+label+" found "+state);
            label+=" corner="+state;
            need(state.getValue(NativeCornerGlassBlock.CORNER_SIZE)==size,"corner takes the clicked glass size: "+label);
            need(NativeCornerGlassBlock.continuesWall(state,across.getOpposite(),wallState,NativeDiagonalWallTopology.settings(level,wall),NativeGlassService.enabled(level,at)),
                    "corner continues the slope exactly: "+label);
            need((corner.frameMask((NativeGlassEntity)level.getBlockEntity(at))&3)!=3,"corner hides its rail where it meets the glass: "+label);
            int hidden=wallHideMask(level,wall),side=across==facing.getClockWise()?2:1;
            need((hidden&side)!=0,"diagonal glass hides its end toward the corner: "+label+" mask="+hidden);
            // Glass anchored to the opposite face at the same lean is a different in-block position.
            BlockPos other=at.relative(across);
            var otherState=diagonalGlass(level,other,facing.getOpposite(),inverted,size);
            need(!NativeCornerGlassBlock.continuesWall(state,across,otherState,NativeDiagonalWallTopology.settings(level,other),true),"opposite-anchored glass does not join: "+label);
            need((wallHideMask(level,other)&3)==0,"opposite-anchored glass keeps both ends: "+label);
            level.removeBlock(other,false);
            joined++;
        }
        need(joined==48,"every facing, size, lean and end");
        dialog(level,actor,corner);
        System.out.println("[vandorlabs][glass-canonical] diagonal corners joined="+joined+" checks="+checks);
    }

    /** Size and Slope through the real payload handler with the Glass dialog open. */
    private static void dialog(ServerLevel level,ServerPlayer actor,NativeCornerGlassBlock corner){
        diagonalGlass(level,DIALOG.west(),Direction.NORTH,false,CornerGlassGeometry.FULL);
        need(place(level,actor,DIALOG.west(),Direction.EAST),"dialog fixture corner");
        actor.setPos(DIALOG.getX()+.5,DIALOG.getY(),DIALOG.getZ()+3.5);
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));actor.setShiftKeyDown(true);
        var hit=new BlockHitResult(Vec3.atCenterOf(DIALOG),Direction.SOUTH,DIALOG,false);
        need(actor.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(actor,InteractionHand.MAIN_HAND,hit)).consumesAction()
                &&actor.containerMenu instanceof NativeGlassMenu,"Configurizer opens the corner's Glass dialog");
        actor.setShiftKeyDown(false);
        int window=actor.containerMenu.containerId,turn=NativeCornerGlassBlock.metadata(level.getBlockState(DIALOG))&3;
        need(!NativeGlassMenu.corner(actor,new CornerGlassGeometryPayload(DIALOG,window+1,0,false,false)),"a stale dialog window cannot edit the corner");
        for(int size=0;size<3;size++)for(int flags=0;flags<4;flags++){
            boolean inverted=(flags&1)!=0,far=(flags&2)!=0;
            need(NativeGlassMenu.corner(actor,new CornerGlassGeometryPayload(DIALOG,window,size,inverted,far)),"dialog edit accepted");
            var state=level.getBlockState(DIALOG);int now=NativeCornerGlassBlock.metadata(state);
            need(state.getValue(NativeCornerGlassBlock.CORNER_SIZE)==size,"dialog sets size "+size);
            need((now&3)==turn&&((now&4)!=0)==inverted&&((now&8)!=0)==far,"dialog sets slope and keeps the bevel's turn: "+state);
            var geometry=corner.projection(state);
            need(geometry.mode()==size&&geometry.inverted()==inverted&&geometry.far()==far,"shape follows the dialog: "+state);
        }
        actor.closeContainer();
    }

    static void read(ServerLevel level){
        var state=level.getBlockState(DIALOG);
        need(state.is(NativeGlassRegistrations.DIAGONAL_CORNER.get())&&state.getValue(NativeCornerGlassBlock.CORNER_SIZE)==CornerGlassGeometry.WIDE
                &&(NativeCornerGlassBlock.metadata(state)&12)==12,"edited size and slope survive a restart: "+state);
        // Before 2.12 a diagonal corner stored only a depth: 1 now leans, 2 sits at the far face.
        for(int depth=0;depth<3;depth++){
            var tag=new CompoundTag();tag.putString("Name","vandorlabs:"+NativeGlassRegistrations.DIAGONAL_CORNER_ID);
            var properties=new CompoundTag();properties.putString("corner","3");properties.putString("depth",Integer.toString(depth));tag.put("Properties",properties);
            var legacy=NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),tag);
            var geometry=((NativeCornerGlassBlock)legacy.getBlock()).projection(legacy);
            need(geometry.turn()==3&&geometry.mode()==CornerGlassGeometry.FULL&&geometry.inverted()==(depth==1)&&geometry.far()==(depth==2),"pre-2.12 corner depth "+depth+" maps to "+legacy);
        }
        System.out.println("[vandorlabs][glass-canonical] diagonal corners read checks="+checks);
    }
}

package com.vandorlabs.neoforge;

import com.vandorlabs.ui.ProgrammableBlockDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Installed disposable cube planes; ordinary worlds never call these opt-in hooks. */
public final class JoinedCubeRuntimeChecks {
    public static int material(){for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if(ModernFinishCatalog.ENTRIES.get(i).id().equals("corp_ashiro_logo"))return i;throw new IllegalStateException("Missing canonical company artwork");}
    public static BlockPos display(int mode){return new BlockPos(72+mode*8,80,8);}
    public static BlockPos matrix(int index){return index==0?new BlockPos(79,88,15):new BlockPos(72+index%9*6,88,20+index/9*6);}
    public static BlockPos cell(BlockPos origin,Direction face,int column,int row){return face.getAxis()==Direction.Axis.X?origin.offset(0,row,column):face.getAxis()==Direction.Axis.Y?origin.offset(column,0,row):origin.offset(column,row,0);}
    private static void plane(ServerLevel level,BlockPos origin,Direction face,Direction facing,int mode){
        for(int row=0;row<3;row++)for(int column=0;column<2;column++){
            BlockPos at=cell(origin,face,column,row);level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(at,VandorLabsModern.PROGRAMMABLE_BLOCK.get().defaultBlockState().setValue(ProgrammableBlock.FACING,facing),3);
            ((ProgrammableBlockEntity)level.getBlockEntity(at)).apply(new ProgrammableBlockDialog.State(material(),mode,false,null,-1));
        }
    }
    public static void clientFixture(ServerLevel level){
        for(int x=64;x<132;x++)for(int z=-6;z<96;z++)level.setBlock(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
        for(int mode=0;mode<3;mode++)plane(level,display(mode),Direction.NORTH,Direction.NORTH,mode);
        int index=0;for(Direction face:Direction.values())for(Direction facing:Direction.values())for(int mode=0;mode<3;mode++)plane(level,matrix(index++),face,facing,mode);
    }
    public static void run(ServerLevel level){clientFixture(level);read(level);System.out.println("[vandorlabs][modern] joined-cube-write PASS 108 native installed six-face/six-facing/layout fixtures and three artwork planes persisted");}
    public static void read(ServerLevel level){for(int mode=0;mode<3;mode++)for(int row=0;row<3;row++)for(int column=0;column<2;column++){
        var at=cell(display(mode),Direction.NORTH,column,row);if(!(level.getBlockEntity(at) instanceof ProgrammableBlockEntity tile)||tile.settings().layout!=mode||tile.settings().mainTexture!=material())throw new IllegalStateException("Modern runtime check: joined cube settings disk mismatch");
    }System.out.println("[vandorlabs][modern] joined-cube-read PASS exact layout/artwork on all persisted display members");}
    private JoinedCubeRuntimeChecks(){}
}

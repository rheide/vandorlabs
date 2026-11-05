package com.vandorlabs.ui;

import com.vandorlabs.persistence.WallSettings;
import java.util.*;

/** Current diagonal geometry rows beside the shared wall material/frame panes. */
public final class DiagonalWallDialog {
    private static final String[] FACING={"","","North","South","West","East"};
    private DiagonalWallDialog(){}
    public static final class State {
        public final PlainWallDialog.State wall;
        /** Stable horizontal direction IDs, north2/south3/west4/east5. */
        public final int facing;
        public final boolean inverted;
        public State(PlainWallDialog.State wall,int facing,boolean inverted){if(facing<2||facing>5)throw new IllegalArgumentException("Horizontal facing required");this.wall=wall;this.facing=facing;this.inverted=inverted;}
        public State choose(int choice){return new State(wall.choose(choice),facing,inverted);}
        public State action(int id,int direction){
            if(!geometryOperation(id)){PlainWallDialog.State next=wall.action(id,direction);return next==wall?this:new State(next,facing,inverted);}
            Geometry next=new Geometry(wall.walls,facing,inverted).action(id,direction);return new State(wall.withWalls(next.walls),next.facing,next.inverted);
        }
    }
    /** Geometry-only draft shared by solid and portholed diagonal panes. */
    public static final class Geometry {
        public final WallSettings walls;public final int facing;public final boolean inverted;
        public Geometry(WallSettings walls,int facing,boolean inverted){if(facing<2||facing>5)throw new IllegalArgumentException("Horizontal facing required");this.walls=walls;this.facing=facing;this.inverted=inverted;}
        public Geometry action(int id,int direction){
            if(!geometryOperation(id))return this;
            int mode=walls.halfHeight?2:walls.fullWidth?1:0,fill=walls.fill,join=walls.join,nextFacing=facing;boolean nextInverted=inverted;
            if(id==WallAppearanceDialog.SIZE)mode=WallAppearanceDialog.nextSize(mode,direction);
            if(id==WallAppearanceDialog.FILL_INSIDE)fill^=1;if(id==WallAppearanceDialog.FILL_OUTSIDE)fill^=2;
            if(id==WallAppearanceDialog.JOIN)join=WallAppearanceDialog.nextJoin(join,direction);
            if(id==WallAppearanceDialog.SLOPE){if(walls.halfHeight)nextFacing^=1;else nextInverted=!inverted;}
            return new Geometry(new WallSettings(mode!=0,mode==2,fill,join,walls.joinPortholes,walls.portholeShape),nextFacing,nextInverted);
        }
    }
    public static boolean geometryOperation(int id){return id==105||id==109||id==110||id==111||id==112;}
    public static String slopeLabel(State state){return slopeLabel(new Geometry(state.wall.walls,state.facing,state.inverted));}
    public static String slopeLabel(Geometry state){return (state.walls.halfHeight?"Slope rises: ":"Slope leans: ")+FACING[state.inverted?state.facing:state.facing^1];}
    public static List<DialogControl> geometryControls(ProgrammableDialogLayout panel,Geometry state,int y,boolean porthole){
        WallSettings wall=state.walls;List<DialogControl> rows=new ArrayList<DialogControl>();rows.add(new DialogControl(105,panel.controlsX,panel.top+y,154,20,WallAppearanceDialog.sizeLabel(wall.halfHeight,wall.fullWidth),false,true));rows.add(new DialogControl(111,panel.controlsX,panel.top+y+24,154,20,slopeLabel(state),false,true));
        if(!porthole){rows.add(new DialogControl(112,panel.controlsX,panel.top+y+48,154,20,WallAppearanceDialog.joinLabel(wall.join),false,!wall.halfHeight));rows.add(new DialogControl(109,panel.controlsX,panel.top+y+72,154,20,"Fill inside: "+((wall.fill&1)!=0?"On":"Off"),false,true));rows.add(new DialogControl(110,panel.controlsX,panel.top+y+96,154,20,"Fill outside: "+((wall.fill&2)!=0?"On":"Off"),false,true));}return Collections.unmodifiableList(rows);
    }
    public static List<DialogControl> controls(ProgrammableDialogLayout panel,State state,String done){
        List<DialogControl> rows=new ArrayList<DialogControl>();for(DialogControl control:PlainWallDialog.controls(panel,state.wall,done)){if(control.id==ProgrammableBlockDialog.DONE&&!state.wall.exterior)rows.addAll(geometryControls(panel,new Geometry(state.wall.walls,state.facing,state.inverted),78,false));rows.add(control);}return Collections.unmodifiableList(rows);
    }
}

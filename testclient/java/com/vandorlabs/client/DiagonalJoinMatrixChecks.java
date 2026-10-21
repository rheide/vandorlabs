package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Every front/back corner and straight-tail combination, independent of insertion order. */
final class DiagonalJoinMatrixChecks {
    private static final net.minecraft.client.renderer.texture.TextureAtlasSprite SPRITE=sprite();
    private static net.minecraft.client.renderer.texture.TextureAtlasSprite sprite(){net.minecraft.client.renderer.texture.TextureAtlasSprite s=new net.minecraft.client.renderer.texture.TextureAtlasSprite("join_matrix"){};s.setIconWidth(16);s.setIconHeight(16);s.initSprite(16,16,0,0,false);return s;}
    static void run(){
        DiagonalContactClipChecks.run();
        BlockProgrammableWall[] blocks={(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_WALL,new BlockProgrammableDiagonalGlass()};
        int cases=0;
        for(BlockProgrammableWall block:blocks)for(int mode=0;mode<3;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)
            for(boolean inverted:new boolean[]{false,true})for(int front=0;front<3;front++)for(int back=0;back<3;back++)for(int tails=0;tails<4;tails++){
                BlockPos root=new BlockPos(8,88,8);
                List<BlockPos> positions=new ArrayList<>();List<IBlockState> states=new ArrayList<>();
                IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
                positions.add(root);states.add(state);
                for(int side=0;side<2;side++){
                    int turn=side==0?front:back;if(turn==0)continue;
                    positions.add(root.offset(side==0?facing:facing.getOpposite()));
                    states.add(state.withProperty(BlockProgrammableWall.FACING,turn==1?facing.rotateYCCW():facing.rotateY()));
                }
                for(int side=0;side<2;side++)if((tails&(1<<side))!=0){positions.add(root.offset(side==0?facing.rotateYCCW():facing.rotateY()));states.add(state);}
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
                List<String> reference=null;
                for(int order=0;order<positions.size();order++){
                    world.clear();
                    for(int j=0;j<positions.size();j++){
                        int i=(j+order)%positions.size();put(world,positions.get(i),states.get(i),mode);
                    }
                    List<String> shape=shape(world,positions);
                    if(reference==null)reference=shape;else require(reference.equals(shape),"placement order changed join geometry");
                    for(int i=0;i<positions.size();i++){
                        world.setBlockState(positions.get(i),net.minecraft.init.Blocks.AIR.getDefaultState(),2);
                        put(world,positions.get(i),states.get(i),mode);
                        require(reference.equals(shape(world,positions)),"remove/replace changed join geometry");
                    }
                }
                if(mode<2){BlockProgrammableWall.Corner corner=block.corner(state,world,root);
                    require((corner!=null)==(front!=0 || back!=0),"corner detection differs from fixture");
                    if(corner!=null){require(Objects.equals(corner.frontRight,front==0?null:front==2),"front handedness");require(Objects.equals(corner.backRight,back==0?null:back==2),"back handedness");}
                }
                cases++;
            }
        for(int id=0;id<GlassTextures.IDS.length;id++){
            TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.setShade(id);
            TileEntityProgrammableGlass restored=new TileEntityProgrammableGlass();restored.readFromNBT(tile.getUpdateTag());require(restored.getShade()==id,"glass finish did not persist");
        }
        VerticalMaterialJoinChecks.run();
        mixed(blocks);
        transparentEnds(blocks);
        flatGlassCaps(blocks[0]);
        flatFrames();
        actualPlanes();
        fullWidthMixedCorners();
        crossedArmSections();
        DiagonalPortholeJoinChecks.run();
        System.out.println("PASS: "+cases+" diagonal wall/glass join topologies, orientations, slopes, placement rotations and remove/replace cycles; all glass finish IDs persist");
    }
    private static void transparentEnds(BlockProgrammableWall[] blocks){
        int cases=0;BlockPos root=new BlockPos(8,88,8);
        for(int mode=0;mode<3;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(boolean upper:new boolean[]{false,true})for(boolean framed:new boolean[]{false,true}){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
            IBlockState wall=blocks[0].getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
            put(world,root,wall,mode);List<String> exposed=shape(world,Collections.singletonList(root));
            BlockPos joined=null;IBlockState glass=null;
            search:for(BlockPos next:DiagonalPanelGeometry.rowCandidates(root,wall,mode,upper))for(EnumFacing direction:new EnumFacing[]{facing,facing.getOpposite()})for(boolean flip:new boolean[]{false,true}){
                IBlockState candidate=blocks[1].getDefaultState().withProperty(BlockProgrammableWall.FACING,direction).withProperty(BlockProgrammableWall.INVERTED,flip);
                if(DiagonalPanelGeometry.rowsMeet(root,wall,next,candidate,mode)){joined=next;glass=candidate;break search;}
            }
            require(joined!=null,"mixed end fixture must meet geometrically");put(world,joined,glass,mode);((TileEntityAnimatedScreenSelector)world.getTileEntity(joined)).setGlassAppearance(0,framed);
            require(!DiagonalPanelGeometry.coveredEnd(world,root,wall,upper),"glass removed opaque wall cap");
            EnumFacing row=mode==2?glass.getValue(BlockProgrammableWall.FACING).getOpposite():EnumFacing.UP;boolean glassUpper=PanelPlane.axis(root.subtract(joined),row)>0;
            require(DiagonalPanelGeometry.coveredEnd(world,joined,glass,glassUpper),"opaque wall no longer covers glass-side seam");
            require(exposed.equals(shape(world,Collections.singletonList(root))),"tile mesh exposes wall interior through glass");
            DiagonalWallState shape=new DiagonalWallState(wall,world,root);require(!(upper?shape.hideUpper:shape.hideLower),"baked wall hides its cap against glass");
            require(!DiagonalWallModel.bake(shape,SPRITE,SPRITE).isEmpty(),"baked mixed wall mesh missing");cases++;
        }
        System.out.println("PASS: "+cases+" transparent/opaque diagonal end joins retain wall caps, both frame settings and rendering paths");
    }
    private static void flatGlassCaps(BlockProgrammableWall wallBlock){
        BlockProgrammableGlass glassBlock=new BlockProgrammableGlass("cap_test_glass");
        BlockProgrammableWall solidBlock=new BlockProgrammableWall("cap_test_wall",BlockProgrammableWall.Shape.PLAIN);
        BlockPos root=new BlockPos(8,88,8);int cases=0;
        for(int mode=0;mode<2;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(boolean upper:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(boolean framed:new boolean[]{false,true}){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
            IBlockState wall=wallBlock.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
            put(world,root,wall,mode);BlockPos next=upper?root.up():root.down();
            IBlockState glass=glassBlock.getDefaultState().withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,depth);
            world.setBlockState(next,glass,2);((TileEntityProgrammableGlass)world.getTileEntity(next)).setAppearance(1,framed);
            double end=BlockProgrammableWall.flatEnd(wall,world,root,upper);if(Double.isNaN(end))continue;
            List<String> collision=collision(world,root);
            require(PanelJoins.connectsFlat(world,next,glass,root),"flat glass must still join the aligned slope");
            DiagonalWallState baked=new DiagonalWallState(wall,world,root);
            require(!(upper?baked.hideUpper:baked.hideLower),"flat glass hides baked wall cap");
            require(hasCap(DiagonalWallModel.bake(baked,SPRITE,SPRITE),upper),"baked wall lacks textured cap under flat glass");
            DiagonalWallQuadCapture capture=new DiagonalWallQuadCapture(facing,false);
            TEAnimatedScreenSelector.drawConfiguredDiagonalWall(capture,(TileEntityAnimatedScreenSelector)world.getTileEntity(root),wall,SPRITE,SPRITE);
            require(hasCap(capture.finish(),upper),"tile-rendered wall lacks textured cap under flat glass");
            EnumFacing canonical=facing.getAxis()==EnumFacing.Axis.X?EnumFacing.EAST:EnumFacing.NORTH;
            world.setBlockState(next,solidBlock.getDefaultState().withProperty(BlockProgrammableWall.FACING,canonical).withProperty(BlockProgrammableWall.DEPTH,depth),2);
            require(BlockProgrammableWall.flatEnd(wall,world,root,upper)==end&&collision.equals(collision(world,root)),"material swap changed aligned geometry");
            baked=new DiagonalWallState(wall,world,root);require(upper?baked.hideUpper:baked.hideLower,"opaque flat continuation must still hide its shared end");
            cases++;
        }
        require(cases>=64,"flat-glass cap coverage");
        System.out.println("PASS: "+cases+" flat-glass wall caps retain nonzero textured faces in baked and tile meshes, matching solid joins and collision");
    }
    private static boolean hasCap(List<net.minecraft.client.renderer.block.model.BakedQuad> quads,boolean upper){
        for(net.minecraft.client.renderer.block.model.BakedQuad quad:quads){
            int[] data=quad.getVertexData();int stride=data.length/4;Vec3d[] points=new Vec3d[4];boolean plane=true;
            for(int v=0;v<4;v++){points[v]=new Vec3d(Float.intBitsToFloat(data[v*stride]),Float.intBitsToFloat(data[v*stride+1]),Float.intBitsToFloat(data[v*stride+2]));plane&=Math.abs(points[v].y-(upper?1:0))<1e-7;}
            if(plane&&points[1].subtract(points[0]).crossProduct(points[2].subtract(points[0])).lengthSquared()>1e-8)return true;
        }
        return false;
    }
    private static void mixed(BlockProgrammableWall[] diagonals){
        BlockProgrammableWall flatWall=new BlockProgrammableWall("mixed_join_wall",BlockProgrammableWall.Shape.PLAIN);
        BlockProgrammableGlass flatGlass=new BlockProgrammableGlass("mixed_join_glass");
        BlockProgrammableWall flatPorthole=new BlockProgrammableWall("mixed_join_porthole",BlockProgrammableWall.Shape.PORTHOLE);
        BlockPos root=new BlockPos(8,88,8);
        for(BlockProgrammableWall diagonal:diagonals)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<2;mode++)for(int depth=0;depth<3;depth++)for(int material=0;material<3;material++){
            boolean glass=material==1;
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
            IBlockState state=diagonal.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
            put(world,root,state,mode);
            EnumFacing flatFacing=glass?(facing.getAxis()==EnumFacing.Axis.X?EnumFacing.EAST:EnumFacing.NORTH):facing;
            IBlockState flat=glass?flatGlass.getDefaultState().withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,depth):(material==2?flatPorthole:flatWall).getDefaultState().withProperty(BlockProgrammableWall.FACING,flatFacing).withProperty(BlockProgrammableWall.DEPTH,depth);
            for(boolean upper:new boolean[]{false,true}){
                double expected=com.vandorlabs.render.DiagonalWallGeometry.near(mode,inverted,upper?1:0);
                double near=PanelJoins.near(flat)/16;if(flatFacing!=facing)near=.75-near;
                int offset=(int)Math.round(expected-near);
                BlockPos next=root.up(upper?1:-1).offset(facing.getOpposite(),offset);
                world.setBlockState(next,flat,2);
                double end=BlockProgrammableWall.flatEnd(state,world,root,upper);
                if(Math.abs(expected-near-offset)<=.1250001){
                    require(Math.abs(end-near-offset)<1e-8,"mixed-material endpoint failed to align");
                    require(PanelJoins.connectsFlat(world,next,flat,root),"flat/diagonal connection is asymmetric");
                } else require(Double.isNaN(end),"distant panel folded the diagonal slope");
                int anchor=PanelJoins.placementDepth(world,root,state,upper?EnumFacing.UP:EnumFacing.DOWN);
                require(Math.abs(PanelDepth.start(anchor)/16D-expected)<=.1250001,"placement did not inherit an aligned end");
                world.setBlockState(next,net.minecraft.init.Blocks.AIR.getDefaultState(),2);
            }
            for(int choice=0;choice<12;choice++){
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(root);tile.setDiagonalJoin(choice);
                TileEntityAnimatedScreenSelector restored=new TileEntityAnimatedScreenSelector();restored.readFromNBT(tile.getUpdateTag());require(restored.getDiagonalJoin()==choice,"join override lost in NBT");
                require((diagonal.corner(state,world,root)!=null)==(choice>=2),"join override geometry missing");
            }
        }
    }
    /** Assert world-space seam sides instead of merely comparing repeated meshes. */
    private static void flatFrames(){
        BlockProgrammableGlass glass=new BlockProgrammableGlass("matrix_flat_glass");
        BlockProgrammableWall wall=new BlockProgrammableWall("matrix_flat_wall",BlockProgrammableWall.Shape.PLAIN);
        BlockProgrammableWall porthole=new BlockProgrammableWall("matrix_flat_porthole",BlockProgrammableWall.Shape.PORTHOLE);
        BlockPos root=new BlockPos(17,91,-23);
        for(boolean rotated:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(int material=0;material<3;material++)for(boolean opposite:new boolean[]{false,true}){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
            IBlockState state=glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,rotated).withProperty(BlockGlassWall.DEPTH,depth);
            world.setBlockState(root,state,2);
            EnumFacing facing=rotated?EnumFacing.EAST:EnumFacing.NORTH;
            EnumFacing left=rotated?EnumFacing.NORTH:EnumFacing.WEST;
            BlockPos neighbor=root.offset(left);
            int mirrored=depth==0?0:3-depth;
            IBlockState next=material==0?state:(material==1?wall:porthole).getDefaultState().withProperty(BlockProgrammableWall.FACING,opposite?facing.getOpposite():facing).withProperty(BlockProgrammableWall.DEPTH,opposite?mirrored:depth);
            world.setBlockState(neighbor,next,2);
            IBlockState actual=glass.getActualState(state,world,root);
            require(!actual.getValue(BlockGlassWall.LEFT) && actual.getValue(BlockGlassWall.RIGHT),"world-left mixed seam was framed on the wrong side");
            require(PanelJoins.connectsFlat(world,root,state,neighbor),"coplanar wall/glass/porthole mismatch");
            world.setBlockState(neighbor,net.minecraft.init.Blocks.AIR.getDefaultState(),2);
            actual=glass.getActualState(state,world,root);
            require(actual.getValue(BlockGlassWall.LEFT) && actual.getValue(BlockGlassWall.RIGHT),"removed neighbor leaves a missing frame");
            world.setBlockState(neighbor,next,2);
            ((TileEntityProgrammableGlass)world.getTileEntity(root)).setJoin(false);
            require(glass.getActualState(state,world,root).getValue(BlockGlassWall.LEFT),"owner Join Off hides a mixed seam");
            ((TileEntityProgrammableGlass)world.getTileEntity(root)).setJoin(true);
            if(material==0){((TileEntityProgrammableGlass)world.getTileEntity(neighbor)).setJoin(false);require(glass.getActualState(state,world,root).getValue(BlockGlassWall.LEFT),"neighbor Join Off hides seam");}
            else {
                IBlockState shifted=next.withProperty(BlockProgrammableWall.DEPTH,(next.getValue(BlockProgrammableWall.DEPTH)+1)%3);
                world.setBlockState(neighbor,shifted,2);
                require(glass.getActualState(state,world,root).getValue(BlockGlassWall.LEFT),"different-depth material hides a frame");
            }
            world.setBlockState(neighbor,next,2);
            BlockPos displaced=neighbor.offset(facing.getOpposite());world.setBlockState(displaced,next,2);
            require(!PanelJoins.connectsFlat(world,root,state,displaced),"parallel panel in another normal cell falsely connects");
        }
    }
    private static void actualPlanes(){
        BlockProgrammableWall diagonal=(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;
        BlockPos root=new BlockPos(31,111,-47);
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++){
            IBlockState state=diagonal.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
            BlockPos side=root.offset(facing.rotateY());
            require(DiagonalPanelGeometry.samePlane(root,state,side,state,mode),"side-by-side porthole planes must align");
            require(!DiagonalPanelGeometry.samePlane(root,state,side.offset(facing.getOpposite()),state,mode),"displaced porthole plane accepted by state alone");
            BlockPos shift=new BlockPos(-53,7,29);
            require(DiagonalPanelGeometry.samePlane(root.add(shift),state,side.add(shift),state,mode),"porthole plane matching depends on world origin");
        }
    }
    /** Mixed materials must retain the same joined footprint as an all-wall fixture. */
    private static void fullWidthMixedCorners(){
        BlockProgrammableWall[] materials={new BlockProgrammableWall("matrix_diagonal_wall",BlockProgrammableWall.Shape.DIAGONAL),
                new BlockProgrammableDiagonalGlass(),new BlockProgrammableWall("matrix_diagonal_porthole",BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE)};
        BlockPos root=new BlockPos(41,96,-31);int checked=0;
        for(int kind=0;kind<3;kind++)for(int variant=0;variant<(kind==1?12:4);variant++)
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})
                for(int assignment=0;assignment<9;assignment++)for(int order=0;order<(kind==0?2:1);order++){
            List<BlockPos> positions=new ArrayList<>();List<EnumFacing> directions=new ArrayList<>();
            positions.add(root);directions.add(facing);
            if(kind==0 || kind==1 && variant<4){
                positions.add(root.offset(variant<2?facing:facing.getOpposite()));directions.add((variant&1)==0?facing.rotateYCCW():facing.rotateY());
                if(kind==1){positions.add(root.offset(facing.rotateYCCW()));directions.add(facing);positions.add(root.offset(facing.rotateY()));directions.add(facing);}
            }else{
                int hands=kind==2?variant:(variant-4)/2;
                positions.add(root.offset(facing));directions.add((hands&1)==0?facing.rotateYCCW():facing.rotateY());
                positions.add(root.offset(facing.getOpposite()));directions.add((hands&2)==0?facing.rotateYCCW():facing.rotateY());
                if(kind==2 || (variant-4)%2==0){positions.add(root.offset(facing.rotateYCCW()));directions.add(facing);}
                if(kind==2 || (variant-4)%2!=0){positions.add(root.offset(facing.rotateY()));directions.add(facing);}
            }
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true),reference=new NonRenderingChecks.MemoryWorld(true);
            int a=assignment/3,b=assignment%3;int[] choices={a,b,(a+b)%3,(a+2*b)%3,(a+b+1)%3};
            for(int step=0;step<positions.size();step++){
                int i=order==0?step:positions.size()-1-step;
                put(world,positions.get(i),diagonalState(materials[choices[i]],directions.get(i),inverted),1);
                ((TileEntityAnimatedScreenSelector)world.getTileEntity(positions.get(i))).setJoinPortholes(true);
                put(reference,positions.get(i),diagonalState(materials[0],directions.get(i),inverted),1);
            }
            for(BlockPos pos:positions){
                IBlockState actual=world.getBlockState(pos),expected=reference.getBlockState(pos);
                BlockProgrammableWall.Corner c=((BlockProgrammableWall)actual.getBlock()).corner(actual,world,pos),r=materials[0].corner(expected,reference,pos);
                require(PanelJoins.cornerDiagonal(actual,world,pos),"full-width mixed diagonal excluded from corner eligibility");
                require((c==null)==(r==null),"mixed diagonal corner absent relative to wall reference");
                if(c!=null)require(Objects.equals(c.frontRight,r.frontRight)&&Objects.equals(c.backRight,r.backRight)&&c.straightLeft==r.straightLeft&&c.straightRight==r.straightRight,"mixed diagonal arm/tail topology differs from wall reference");
                require(collision(world,pos).equals(collision(reference,pos)),"mixed diagonal collision differs from wall reference");
            }
            BlockProgrammableWall.Corner c=((BlockProgrammableWall)world.getBlockState(root).getBlock()).corner(world.getBlockState(root),world,root);
            require(c!=null,"full-width fixture root has no corner");
            int frontIndex=positions.indexOf(root.offset(facing)),backIndex=positions.indexOf(root.offset(facing.getOpposite()));
            Boolean expectedFront=frontIndex<0?null:directions.get(frontIndex)==facing.rotateY();
            Boolean expectedBack=backIndex<0?null:directions.get(backIndex)==facing.rotateY();
            require(Objects.equals(c.frontRight,expectedFront)&&Objects.equals(c.backRight,expectedBack),"full-width mixed root handedness differs from placed directions");
            require(c.straightLeft==positions.contains(root.offset(facing.rotateYCCW()))&&c.straightRight==positions.contains(root.offset(facing.rotateY())),"full-width mixed root loses a placed straight tail");
            for(boolean front:new boolean[]{true,false}){
                Boolean right=front?c.frontRight:c.backRight;if(right==null)continue;
                BlockPos neighbor=root.offset(front?facing:facing.getOpposite());
                for(double y:new double[]{.25,.75}){
                    double near=16*com.vandorlabs.render.DiagonalWallGeometry.near(1,inverted,y);
                    double x=((right?12-near:near)+2)/16;
                    String fixture=" kind="+kind+" variant="+variant+" facing="+facing+" inverted="+inverted+" materials="+Arrays.toString(choices)+" order="+order+" front="+front+" right="+right+" y="+y+" x="+x;
                    require(contains(world,root,localPoint(root,facing,x,y,front?.001:.999)),"root connector does not reach seam"+fixture);
                    require(contains(world,neighbor,localPoint(root,facing,x,y,front?-.001:1.001)),"neighbor connector does not reach reciprocal seam"+fixture);
                }
            }
            if(kind==0){
                BlockPos neighbor=positions.get(1);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(neighbor);
                tile.setDiagonalJoin(1);require(((BlockProgrammableWall)world.getBlockState(root).getBlock()).corner(world.getBlockState(root),world,root)==null,"neighbor Join Off still bends mixed root");tile.setDiagonalJoin(0);
                if(choices[1]==2){tile.setJoinPortholes(false);require(((BlockProgrammableWall)world.getBlockState(root).getBlock()).corner(world.getBlockState(root),world,root)==null,"disabled porthole still bends mixed root");tile.setJoinPortholes(true);}
                tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(root);tile.setDiagonalJoin(1);
                require(((BlockProgrammableWall)world.getBlockState(root).getBlock()).corner(world.getBlockState(root),world,root)==null,"owner Join Off still forms mixed corner");
                tile.setDiagonalJoin(0);
                if(choices[0]==2){tile.setJoinPortholes(false);require(((BlockProgrammableWall)world.getBlockState(root).getBlock()).corner(world.getBlockState(root),world,root)==null,"owner porthole Join Off still forms corner");}
            }
            checked++;
        }
        for(int mode:new int[]{0,2})for(BlockProgrammableWall block:materials){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
            IBlockState state=diagonalState(block,EnumFacing.NORTH,false);put(world,root,state,mode);
            require(PanelJoins.cornerDiagonal(state,world,root)==(mode==0&&!block.isPortholeShape()),"full-width fix changed half-width/half-height eligibility");
        }
        System.out.println("PASS: "+checked+" full-width mixed diagonal L/T/X footprints, reciprocal seams and Join Off cases");
    }
    /** Crossing arms change the bridge envelope at mid-height; a single interpolated strip is wrong. */
    private static void crossedArmSections() {
        BlockProgrammableWall[] blocks={new BlockProgrammableWall("section_wall",BlockProgrammableWall.Shape.DIAGONAL),
                new BlockProgrammableDiagonalGlass(),new BlockProgrammableWall("section_porthole",BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE)};
        BlockPos root=new BlockPos(41,96,-31);int sections=0;
        for(BlockProgrammableWall block:blocks)for(EnumFacing facing:EnumFacing.HORIZONTALS)
            for(boolean inverted:new boolean[]{false,true})for(int hands=0;hands<2;hands++)for(int tails=0;tails<4;tails++) {
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
                IBlockState state=diagonalState(block,facing,inverted);put(world,root,state,1);
                ((TileEntityAnimatedScreenSelector)world.getTileEntity(root)).setJoinPortholes(true);
                put(world,root.offset(facing),diagonalState(block,hands==0?facing.rotateY():facing.rotateYCCW(),inverted),1);
                put(world,root.offset(facing.getOpposite()),diagonalState(block,hands==0?facing.rotateYCCW():facing.rotateY(),inverted),1);
                for(int side=0;side<2;side++)if((tails&(1<<side))!=0)put(world,root.offset(side==0?facing.rotateYCCW():facing.rotateY()),state,1);
                BlockProgrammableWall.Corner corner=block.corner(state,world,root);
                require(corner!=null,"crossed arms lost their core descriptor");
                SectionCapture capture=new SectionCapture();
                TEAnimatedScreenSelector.drawConfiguredDiagonalWall(capture,(TileEntityAnimatedScreenSelector)world.getTileEntity(root),state,SPRITE,SPRITE);
                List<AxisAlignedBB> collision=new ArrayList<>();block.addCollisionBoxToList(state,world,root,new AxisAlignedBB(root).grow(3),collision,null,false);
                EnumFacing inverse=facing==EnumFacing.EAST?EnumFacing.WEST:facing==EnumFacing.WEST?EnumFacing.EAST:facing;
                for(double y:new double[]{6,8,10}) {
                    double near=16*com.vandorlabs.render.DiagonalWallGeometry.near(1,inverted,y/16),left=corner.left(near),right=corner.right(near);
                    double[] mesh=capture.section(y);String fixture=" block="+block.getShape()+" facing="+facing+" inv="+inverted+" hands="+hands+" tails="+tails+" y="+y;
                    require(Math.abs(mesh[0]-left)<1E-5 && Math.abs(mesh[1]-right)<1E-5,"rendered crossed-arm section disagrees with core outline"+fixture+" mesh="+Arrays.toString(mesh)+" expected="+left+".."+right);
                    double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
                    for(AxisAlignedBB box:collision) {
                        AxisAlignedBB local=PanelPlacement.rotateFromNorth(box.offset(-root.getX(),-root.getY(),-root.getZ()),inverse);
                        if(local.minY<=y/16+1E-9 && local.maxY>=y/16-1E-9){min=Math.min(min,16*local.minX);max=Math.max(max,16*local.maxX);}
                    }
                    // Collision conservatively encloses each one-pixel-height slab.
                    require(min<=left+1E-5 && min>=left-1.00001 && max>=right-1E-5 && max<=right+1.00001,"collision section disagrees with exact bridge envelope"+fixture);
                    sections++;
                }
            }
        System.out.println("PASS: "+sections+" crossed-arm mesh/core/collision cross-sections at y6/y8/y10");
    }
    private static final class SectionCapture extends net.minecraft.client.renderer.BufferBuilder {
        final List<double[][]> quads=new ArrayList<>();double[][] current=new double[4][3];int used;
        SectionCapture(){super(16);}
        @Override public net.minecraft.client.renderer.BufferBuilder pos(double x,double y,double z){current[used]=new double[]{x,y,z};return this;}
        @Override public net.minecraft.client.renderer.BufferBuilder tex(double u,double v){return this;}
        @Override public net.minecraft.client.renderer.BufferBuilder color(int r,int g,int b,int a){return this;}
        @Override public net.minecraft.client.renderer.BufferBuilder lightmap(int sky,int block){return this;}
        @Override public net.minecraft.client.renderer.BufferBuilder normal(float x,float y,float z){return this;}
        @Override public void endVertex(){if(++used==4){quads.add(current);current=new double[4][3];used=0;}}
        double[] section(double y) {
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(double[][] quad:quads)for(int[] triangle:new int[][]{{0,1,2},{0,2,3}})for(int edge=0;edge<3;edge++) {
                double[] a=quad[triangle[edge]],b=quad[triangle[(edge+1)%3]];
                if(Math.abs(a[1]-y)<1E-8){min=Math.min(min,a[0]);max=Math.max(max,a[0]);}
                if((a[1]-y)*(b[1]-y)<0){double x=a[0]+(y-a[1])/(b[1]-a[1])*(b[0]-a[0]);min=Math.min(min,x);max=Math.max(max,x);}
            }
            return new double[]{min,max};
        }
    }
    private static IBlockState diagonalState(BlockProgrammableWall block,EnumFacing facing,boolean inverted){return block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);}
    private static List<String> collision(NonRenderingChecks.MemoryWorld world,BlockPos pos){
        List<AxisAlignedBB> boxes=new ArrayList<>();IBlockState state=world.getBlockState(pos);((BlockProgrammableWall)state.getBlock()).addCollisionBoxToList(state,world,pos,new AxisAlignedBB(pos).grow(3),boxes,null,false);
        List<String> result=new ArrayList<>();for(AxisAlignedBB box:boxes)result.add(box.toString());Collections.sort(result);return result;
    }
    private static Vec3d localPoint(BlockPos root,EnumFacing facing,double x,double y,double z){
        AxisAlignedBB point=PanelPlacement.rotateFromNorth(new AxisAlignedBB(x,y,z,x,y,z),facing);
        return new Vec3d(root.getX()+point.minX,root.getY()+point.minY,root.getZ()+point.minZ);
    }
    private static boolean contains(NonRenderingChecks.MemoryWorld world,BlockPos pos,Vec3d point){
        List<AxisAlignedBB> boxes=new ArrayList<>();IBlockState state=world.getBlockState(pos);((BlockProgrammableWall)state.getBlock()).addCollisionBoxToList(state,world,pos,new AxisAlignedBB(pos).grow(3),boxes,null,false);
        // Quarter-height probes sit exactly on slice boundaries; test the closed solid union.
        for(AxisAlignedBB box:boxes)if(box.grow(1E-9).contains(point))return true;return false;
    }
    private static void put(NonRenderingChecks.MemoryWorld world,BlockPos pos,IBlockState state,int mode){world.setBlockState(pos,state,2);((TileEntityAnimatedScreenSelector)world.getTileEntity(pos)).setDiagonalGeometry(mode,0);}
    private static List<String> shape(NonRenderingChecks.MemoryWorld world,List<BlockPos> positions){
        List<String> result=new ArrayList<>();
        for(BlockPos pos:positions){IBlockState state=world.getBlockState(pos);BlockProgrammableWall block=(BlockProgrammableWall)state.getBlock();List<AxisAlignedBB> boxes=new ArrayList<>();block.addCollisionBoxToList(state,world,pos,new AxisAlignedBB(pos).grow(3),boxes,null,false);
            DiagonalWallQuadCapture capture=new DiagonalWallQuadCapture(state.getValue(BlockProgrammableWall.FACING),false);
            TEAnimatedScreenSelector.drawConfiguredDiagonalWall(capture,(TileEntityAnimatedScreenSelector)world.getTileEntity(pos),state,SPRITE,SPRITE);
            for(net.minecraft.client.renderer.block.model.BakedQuad quad:capture.finish())result.add(pos+":mesh:"+Arrays.toString(quad.getVertexData()));
            for(AxisAlignedBB box:boxes){require(box.minX<=box.maxX && box.minY<=box.maxY && box.minZ<=box.maxZ,"inverted collision box");result.add(pos+":"+box);}
        }
        Collections.sort(result);return result;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

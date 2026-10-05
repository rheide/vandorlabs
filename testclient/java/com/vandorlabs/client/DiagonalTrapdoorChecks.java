package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.render.*;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.tiles.*;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.fml.common.registry.*;
import java.util.*;

/** Real block/tile/item and buffer paths, without creating a rendering context. */
final class DiagonalTrapdoorChecks {
    private static BlockProgrammableDiagonalTrapdoor block;
    private static ItemDiagonalTrapdoor item;
    private static int assertions;
    static void run() {
        GameRegistry.registerTileEntity(TileEntityProgrammableDiagonalTrapdoor.class,new ResourceLocation("minecraft:vandorlabs_data_check_diagonal_trapdoor"));
        block=new BlockProgrammableDiagonalTrapdoor();ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR=block;
        ForgeRegistries.BLOCKS.register(block);item=new ItemDiagonalTrapdoor(block);ForgeRegistries.ITEMS.register(item.setRegistryName(block.getRegistryName()));
        require(new TileEntityProgrammableDiagonalTrapdoor().getHousingTexture()==ScreenHousingTextures.DEFAULT_TRAPDOOR,"default diagonal hatch texture missing");
        mesh();clearance();openSelection();placement();groups();copyAndPower();continuedSurfaces();expandedGroups();staggeredModes();partialPatches();slidingStyles();boundaries();recipe();
        System.out.println("PASS: Programmable Diagonal Trapdoor ("+assertions+" assertions; wall alignment, rigid geometry, placement, all square orders, saved settings, power, copying, recipe)");
    }
    private static void mesh() {
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=new net.minecraft.client.renderer.texture.TextureAtlasSprite("diagonal_check"){};
        sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(256,256,32,48,false);
        for(int mode=0;mode<3;mode++)for(boolean inverted:new boolean[]{false,true})for(int facing=0;facing<4;facing++)for(boolean sliding:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true})for(int step=0;step<=18;step++) {
            double pose=step/18D;double[][] closed=DiagonalTrapdoorGeometry.corners(mode,inverted,facing,sliding,reverse,0),v=DiagonalTrapdoorGeometry.corners(mode,inverted,facing,sliding,reverse,pose);
            for(int i=0;i<8;i++)for(int j=i+1;j<8;j++)require(Math.abs(distance(v[i],v[j])-distance(closed[i],closed[j]))<1e-8,"leaf deforms while moving");
            if(sliding)for(int i=1;i<8;i++)for(int axis=0;axis<3;axis++)require(Math.abs((v[i][axis]-closed[i][axis])-(v[0][axis]-closed[0][axis]))<1e-8,"slide/lift deforms leaf");
            net.minecraft.client.renderer.BufferBuilder buffer=new net.minecraft.client.renderer.BufferBuilder(4096);buffer.begin(7,BlockSurfaceFormat.get());
            TEProgrammableTrapdoor.drawDiagonalLeaf(buffer,sprite,mode,inverted,facing,sliding,reverse,pose,0xF000A0);buffer.finishDrawing();require(buffer.getVertexCount()==24,"mesh face count");
            java.nio.ByteBuffer bytes=buffer.getByteBuffer();net.minecraft.client.renderer.vertex.VertexFormat format=buffer.getVertexFormat();
            int stride=format.getNextOffset(),normal=format.getNormalOffset(),uv=format.getUvOffsetById(0),light=format.getUvOffsetById(1);
            double[] center={0,0,0};for(double[] point:v)for(int a=0;a<3;a++)center[a]+=point[a]/8;
            for(int f=0;f<6;f++) {
                int[] indices=TrapdoorGeometry.FACES[f];double[] p=v[indices[0]];
                double dot=0;for(int a=0;a<3;a++)dot+=(p[a]-center[a])*bytes.get(f*4*stride+normal+a)/127D;
                require(dot>0,"mesh normal faces inward");
                for(int i=0;i<4;i++) {
                    int offset=(f*4+i)*stride;require(bytes.getShort(offset+light)==160 && bytes.getShort(offset+light+2)==240,"mesh lightmap");
                    double u=bytes.getFloat(offset+uv),w=bytes.getFloat(offset+uv+4);require(u>=sprite.getMinU()-1e-6 && u<=sprite.getMaxU()+1e-6 && w>=sprite.getMinV()-1e-6 && w<=sprite.getMaxV()+1e-6,"texture atlas bleed");
                    for(int a=0;a<3;a++)require(Math.abs(bytes.getFloat(offset+a*4)-v[indices[i]][a])<1e-6,"submitted vertex mismatch");
                }
            }
            buffer.reset();
        }
        for(int mode=0;mode<3;mode++)for(boolean inverted:new boolean[]{false,true}) {
            double[][] v=DiagonalTrapdoorGeometry.corners(mode,inverted,0,false,false,0);double span=mode==1?1:.5;
            for(int i=0;i<8;i++) {
                double along=mode==2?v[i][2]:v[i][1],near=(inverted?span*(1-along):span*along)+(mode==2 && inverted?.5:0)-.125;
                double depth=mode==2?v[i][1]:v[i][2];int bit=mode==2?2:4;
                require(Math.abs(depth-near-((i&bit)==0?1/16D:3/16D))<1e-9,"one-pixel wall inset differs");
            }
        }
    }
    private static void clearance() {
        for(int mode=0;mode<3;mode++)for(boolean inverted:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true})for(boolean slide:new boolean[]{false,true}) {
            double[] b=DiagonalTrapdoorGeometry.bounds(DiagonalTrapdoorGeometry.corners(mode,inverted,0,slide,reverse,1));
            require(reverse?b[0]<=15/16D+TrapdoorGeometry.EDGE_CLEARANCE+1e-8:b[3]>=1/16D-TrapdoorGeometry.EDGE_CLEARANCE-1e-8,"open diagonal leaf disappeared into neighbor");
            if(slide) {
                double[][] closed=DiagonalTrapdoorGeometry.corners(mode,inverted,0,true,reverse,0),lifting=DiagonalTrapdoorGeometry.corners(mode,inverted,0,true,reverse,.2);
                for(int i=0;i<8;i++)require(Math.abs(closed[i][0]-lifting[i][0])<1e-8,"diagonal slide enters wall before lifting clear");
                double[][] vertices=DiagonalTrapdoorGeometry.corners(mode,inverted,0,true,reverse,1);
                double span=mode==1?1:.5;
                for(double[] point:vertices){double along=mode==2?point[2]:point[1],near=(inverted?span*(1-along):span*along)+(mode==2 && inverted?.5:0)-.125,depth=mode==2?point[1]:point[2];require(depth-near>=5/16D-1e-8,"sliding leaf intersects continuation wall");}
            }
            if(slide)require(Math.abs((reverse?b[0]:b[3])-(reverse?15/16D+TrapdoorGeometry.EDGE_CLEARANCE:1/16D-TrapdoorGeometry.EDGE_CLEARANCE))<1e-8,"diagonal slide clearance");
        }
    }
    private static void openSelection() {
        BlockPos pos=new BlockPos(8,100,8);
        for(int mode=0;mode<3;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true}) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            IBlockState state=block.getDefaultState().withProperty(BlockTrapDoor.FACING,facing)
                    .withProperty(BlockTrapDoor.HALF,inverted?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM)
                    .withProperty(BlockTrapDoor.OPEN,true);
            world.setBlockState(pos,state,2);
            TileEntityProgrammableDiagonalTrapdoor tile=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(pos);
            tile.configure(0,mode,true,SpaceDoorData.TRIGGER_DISABLED,0);
            world.setBlockState(pos,state,2);
            double[][] vertices=BlockProgrammableDiagonalTrapdoor.corners(state,tile,1);
            int[] face=TrapdoorGeometry.FACES[mode==2?0:2];
            Vec3d center=Vec3d.ZERO;
            for(int vertex:face)center=center.add(new Vec3d(vertices[vertex][0],vertices[vertex][1],vertices[vertex][2]).scale(.25));
            Vec3d a=new Vec3d(vertices[face[0]][0],vertices[face[0]][1],vertices[face[0]][2]),b=new Vec3d(vertices[face[1]][0],vertices[face[1]][1],vertices[face[1]][2]),c=new Vec3d(vertices[face[2]][0],vertices[face[2]][1],vertices[face[2]][2]);
            Vec3d normal=b.subtract(a).crossProduct(c.subtract(a)).normalize();
            center=center.add(new Vec3d(pos));
            RayTraceResult hit=OffsetTrapdoorInteractions.trace(world,center.add(normal.scale(.5)),center.subtract(normal.scale(.5)));
            require(hit!=null && hit.getBlockPos().equals(pos),"open diagonal leaf cannot be selected away from its owner cell");
        }
        System.out.println("PASS: open over-wall diagonal leaf selection, all shapes, slopes and directions");
    }

    private static void placement() {
        BlockPos p=new BlockPos(8,100,8);
        for(int mode=0;mode<3;mode++)for(EnumFacing side:EnumFacing.values())for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inv:new boolean[]{false,true}) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos support=p.offset(side.getOpposite());
            BlockProgrammableWall wall=(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;
            world.setBlockState(support,wall.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inv),2);
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(support)).setDiagonalGeometry(mode,0);
            EntityPlayer player=player(world);player.rotationYaw=facing.getHorizontalAngle();player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(item));
            IBlockState expected=wall.getStateForPlacement(world,p,side,.1F,.7F,.9F,0,player),actual=block.getStateForPlacement(world,p,side,.1F,.7F,.9F,0,player);
            if(mode==1 && side==EnumFacing.UP) {
                require(expected.getValue(BlockProgrammableWall.FACING)==facing
                        && expected.getValue(BlockProgrammableWall.INVERTED)==inv,
                        "stacked full diagonal reverses clicked orientation");
            }
            require(actual.getValue(BlockTrapDoor.FACING)==expected.getValue(BlockProgrammableWall.FACING),"placement facing differs from wall");
            require((actual.getValue(BlockTrapDoor.HALF)==BlockTrapDoor.DoorHalf.TOP)==expected.getValue(BlockProgrammableWall.INVERTED),"placement inversion differs from wall");
            require(item.placeBlockAt(new ItemStack(item),player,world,p,side,.1F,.7F,.9F,actual),"wall-adjacent placement failed");
            TileEntityProgrammableDiagonalTrapdoor tile=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(p);require(tile.getPosition()==mode,"placed tile lost wall geometry");
            // Accurate ray intersection through a broad panel face.
            double[][] v=BlockProgrammableDiagonalTrapdoor.corners(actual,tile,0);double[] center={0,0,0};for(double[] point:v)for(int a=0;a<3;a++)center[a]+=point[a]/8;
            Vec3d c=new Vec3d(p.getX()+center[0],p.getY()+center[1],p.getZ()+center[2]);
            Vec3d normal=mode==2?new Vec3d(0,1,0):new Vec3d(facing.getDirectionVec());
            require(block.collisionRayTrace(actual,world,p,c.add(normal.scale(2)),c.subtract(normal.scale(2)))!=null,"closed mesh cannot be selected");
        }
        for(int mode=0;mode<3;mode++)for(int texture=0;texture<ScreenHousingTextures.IDS.length;texture++) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);TileEntityProgrammableDiagonalTrapdoor tile=place(world,p,mode,EnumFacing.NORTH,true,true,texture);
            require(tile.getPosition()==mode && tile.getHousingTexture()==ScreenHousingTextures.clamp(texture) && tile.isSliding() && tile.isInverted(),"configured client placement lost settings");
            ItemStack drop=block.configuredDrop(tile);require(!drop.getSubCompound("BlockEntityTag").hasKey("DiagonalReverse"),"item retained opening side");
        }
    }
    private static void groups() {
        List<int[]> orders=new ArrayList<>();permutations(new int[]{0,1,2,3},0,orders);
        for(int mode=0;mode<3;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inv:new boolean[]{false,true})for(boolean slide:new boolean[]{false,true})for(int[] order:orders) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos base=new BlockPos(10,100,10);
            EnumFacing width=facing.rotateYCCW(),along=mode==2?facing.getOpposite():EnumFacing.UP;
            BlockPos[] cells={base,base.offset(width),base.offset(along),base.offset(width).offset(along)};
            for(int i:order)place(world,cells[i],mode,facing,inv,slide,0);
            for(BlockPos cell:cells) {
                TileEntityProgrammableDiagonalTrapdoor leaf=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(cell);
                require(leaf.group().size()==4,"square missed order "+Arrays.toString(order));
                require(leaf.mate() instanceof TileEntityProgrammableDiagonalTrapdoor && leaf.isReverse()!=((TileEntityProgrammableDiagonalTrapdoor)leaf.mate()).isReverse(),"opposite sides missing");
                require(world.getBlockState(cell).getValue(BlockTrapDoor.FACING)==facing,"pair flipped closed surface");
                leaf.requestOpen(true);for(BlockPos other:cells)require(open(world,other),"group open failed");
                leaf.requestOpen(false);for(BlockPos other:cells)require(!open(world,other),"group close failed");
                NBTTagCompound saved=leaf.writeToNBT(new NBTTagCompound());leaf.readFromNBT(saved);require(leaf.group().size()==4,"saved group lost");
            }
            TileEntityProgrammableDiagonalTrapdoor first=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(base);
            IBlockState removed=world.getBlockState(cells[3]);
            // Chunk.setBlockState replaces the state before calling oldBlock.breakBlock.
            world.states.put(cells[3],Blocks.AIR.getDefaultState());block.breakBlock(world,cells[3],removed);
            world.setBlockState(cells[3],Blocks.AIR.getDefaultState(),2);
            require(first.group().size()<=2,"broken square remained");place(world,cells[3],mode,facing,inv,slide,0);require(first.group().size()==4,"replacement failed");
            // The two width halves must move away from the shared seam.
            double[][] a=BlockProgrammableDiagonalTrapdoor.corners(world.getBlockState(cells[0]),first,1);
            TileEntityProgrammableDiagonalTrapdoor second=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(cells[1]);
            double[][] b=BlockProgrammableDiagonalTrapdoor.corners(world.getBlockState(cells[1]),second,1);
            if(slide){double ax=0,bx=0;for(int i=0;i<8;i++){ax+=a[i][0]*width.getFrontOffsetX()+a[i][2]*width.getFrontOffsetZ();bx+=b[i][0]*width.getFrontOffsetX()+b[i][2]*width.getFrontOffsetZ();}require(bx>ax,"pair slid inward");}
        }
    }
    private static void copyAndPower() {
        for(int mode=0;mode<3;mode++) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos base=new BlockPos(10,100,10);EnumFacing along=mode==2?EnumFacing.SOUTH:EnumFacing.UP;
            BlockPos[] cells={base,base.west(),base.offset(along),base.west().offset(along)};
            for(BlockPos p:cells)place(world,p,mode,EnumFacing.NORTH,false,true,0);
            TileEntityProgrammableDiagonalTrapdoor first=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(base);
            BlockPos power=cells[3].west();world.setBlockState(power,Blocks.REDSTONE_BLOCK.getDefaultState(),2);
            ((TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(cells[3])).localInputChanged();for(BlockPos p:cells)require(open(world,p),"group local power failed");
            world.setBlockState(power,Blocks.AIR.getDefaultState(),2);first.localInputChanged();for(BlockPos p:cells)require(!open(world,p),"group unpower failed");
            NBTTagCompound settings=ProgrammableSettings.capture(world,base);require(settings.hasKey(ProgrammableSettings.DIAGONAL_GEOMETRY,10) && !settings.hasKey(ProgrammableSettings.TRAPDOOR_POSITION),"geometry copy schema");
            settings.setInteger(ProgrammableSettings.WALL_TEXTURE,7);settings.setInteger(ProgrammableSettings.CHANNEL,18);require(ProgrammableSettings.apply(world,base,settings),"group copy failed");
            for(BlockPos p:cells){TileEntityProgrammableDiagonalTrapdoor tile=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(p);require(tile.getHousingTexture()==7 && tile.getRedstoneChannel()==18 && tile.group().size()==4,"copy broke group");}
            ItemStack crafted=ProgrammableSettings.applyToItem(new ItemStack(item),settings);require(!crafted.isEmpty() && crafted.getSubCompound("BlockEntityTag").getInteger("TrapdoorPosition")==mode,"crafted geometry lost");
            TileEntityProgrammableDiagonalTrapdoor sender=place(world,base.east(10),mode,EnumFacing.NORTH,false,false,0);sender.setRedstoneChannel(18);
            world.setBlockState(sender.getPos().down(),Blocks.REDSTONE_BLOCK.getDefaultState(),2);sender.localInputChanged();for(BlockPos p:cells)require(open(world,p),"virtual power failed");sender.invalidate();
        }
    }
    private static void continuedSurfaces() {
        List<int[]> orders=new ArrayList<>();permutations(new int[]{0,1,2,3},0,orders);
        for(int mode:new int[]{0,2})for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean slide:new boolean[]{false,true})for(int[] order:orders) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos base=new BlockPos(10,100,10);
            EnumFacing along=mode==2?facing.getOpposite():EnumFacing.UP,width=facing.rotateYCCW();
            BlockPos[] cells={base,base.offset(width),base.offset(along),base.offset(width).offset(along)};
            for(int i:order)place(world,cells[i],mode,i<2?facing:facing.getOpposite(),i>=2,slide,0);
            for(BlockPos p:cells) {
                TileEntityProgrammableDiagonalTrapdoor tile=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(p);
                require(tile.group().size()==4,"coplanar reversed row not grouped "+mode+Arrays.toString(order));
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());tile.readFromNBT(saved);require(tile.group().size()==4,"coplanar group's saved basis lost");
                tile.requestOpen(true);for(BlockPos other:cells)require(open(world,other),"coplanar square opening failed");
                if(mode!=2) {
                    double[][] closed=BlockProgrammableDiagonalTrapdoor.corners(world.getBlockState(p),tile,0),opened=BlockProgrammableDiagonalTrapdoor.corners(world.getBlockState(p),tile,1);
                    double outward=0;for(int i=0;i<8;i++)outward+=(opened[i][0]-closed[i][0])*facing.getFrontOffsetX()+(opened[i][2]-closed[i][2])*facing.getFrontOffsetZ();
                    require(outward>0,"reversed coplanar row opens inside the continued surface");
                }
            }
            TileEntityProgrammableDiagonalTrapdoor first=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(cells[0]);
            NBTTagCompound settings=ProgrammableSettings.capture(world,cells[0]);settings.setInteger(ProgrammableSettings.WALL_TEXTURE,16);
            require(ProgrammableSettings.apply(world,cells[0],settings),"coplanar group settings rejected");
            for(BlockPos p:cells)require(((TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(p)).group().size()==4,"coplanar texture edit dissolved group");
            for(TileEntityProgrammableTrapdoor leaf:new ArrayList<>(first.group())) {
                TileEntityProgrammableDiagonalTrapdoor diagonal=(TileEntityProgrammableDiagonalTrapdoor)leaf;diagonal.setInverted(!diagonal.isInverted());
            }
            require(first.group().size()==4,"group slope toggle dissolved group");
            if(mode!=2)for(int nextWidth:new int[]{1,0,1,0}) {
                first.configureGroup(16,nextWidth,slide,0,0,first.isInverted(),false,false,first.facing());
                require(first.group().size()==4,"combined width toggle split square");
                for(TileEntityProgrammableTrapdoor member:first.group())require(member.getPosition()==nextWidth && !member.isTileTexture(),"combined width edit missed member");
            }
            for(TileEntityProgrammableTrapdoor leaf:new ArrayList<>(first.group())) {
                TileEntityProgrammableDiagonalTrapdoor diagonal=(TileEntityProgrammableDiagonalTrapdoor)leaf;diagonal.setInverted(!diagonal.isInverted());
            }
            IBlockState removed=world.getBlockState(cells[3]);world.states.put(cells[3],Blocks.AIR.getDefaultState());block.breakBlock(world,cells[3],removed);world.setBlockState(cells[3],Blocks.AIR.getDefaultState(),2);
            require(first.group().size()<=2,"coplanar square remained after break");place(world,cells[3],mode,facing.getOpposite(),true,slide,0);require(first.group().size()==4,"coplanar square repair failed");
        }
    }
    private static void staggeredModes() {
        List<int[]> orders=new ArrayList<>();permutations(new int[]{0,1,2,3},0,orders);
        for(int mode=0;mode<3;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(boolean sliding:new boolean[]{false,true}) {
            BlockPos base=new BlockPos(20,100,20);EnumFacing across=facing.rotateY();
            BlockPos step=mode==2?new BlockPos(facing.getOpposite().getDirectionVec()).add(0,inverted?-1:1,0)
                    :new BlockPos((inverted?facing:facing.getOpposite()).getDirectionVec()).up();
            BlockPos[] cells={base,base.offset(across),base.add(step),base.add(step).offset(across)};
            for(int[] order:orders) {
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
                for(int i:order)place(world,cells[i],mode,facing,inverted,sliding,0);
                TileEntityProgrammableDiagonalTrapdoor root=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(base);
                require(root.group().size()==4,"staggered mode "+mode+" did not join in order "+Arrays.toString(order));
                root.requestOpen(true);
                for(BlockPos cell:cells) {
                    TileEntityProgrammableDiagonalTrapdoor leaf=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(cell);
                    require(open(world,cell) && leaf.group().size()==4,"staggered mode missed group opening");
                    leaf.readFromNBT(leaf.writeToNBT(new NBTTagCompound()));require(leaf.group().size()==4,"staggered mode lost saved links");
                }
                root.configureGroup(7,mode,sliding,0,19,inverted,false,false,facing);
                for(TileEntityProgrammableTrapdoor leaf:root.group())require(leaf.getHousingTexture()==7 && leaf.getRedstoneChannel()==19 && !leaf.isTileTexture(),"staggered mode configuration missed member");
            }
        }
    }
    private static void expandedGroups() {
        for(boolean slide:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.HORIZONTALS) {
            BlockPos base=new BlockPos(20,100,20);EnumFacing width=facing.rotateYCCW();
            NonRenderingChecks.MemoryWorld vworld=new NonRenderingChecks.MemoryWorld(false);
            List<int[]> orders=new ArrayList<>();permutations(new int[]{0,1,2,3},0,orders);
            for(boolean invertedBase:new boolean[]{false,true})for(int[] order:orders) {
                vworld.clear();BlockPos[] cells={base,base.offset(width),base.up(),base.up().offset(width)};
                for(int i:order)place(vworld,cells[i],0,facing,(i>=2)^invertedBase,slide,0);
                TileEntityProgrammableDiagonalTrapdoor first=(TileEntityProgrammableDiagonalTrapdoor)vworld.getTileEntity(base);
                require(first.group().size()==4,"opposite slope V group missed placement order");
                for(TileEntityProgrammableTrapdoor raw:first.group()) {
                    TileEntityProgrammableDiagonalTrapdoor leaf=(TileEntityProgrammableDiagonalTrapdoor)raw;
                    double[][] closed=BlockProgrammableDiagonalTrapdoor.corners(vworld.getBlockState(leaf.getPos()),leaf,0);
                    double[][] opened=BlockProgrammableDiagonalTrapdoor.corners(vworld.getBlockState(leaf.getPos()),leaf,1);
                    for(double pose:new double[]{.2,.25,.5,.75,1}) {
                        double[][] moving=BlockProgrammableDiagonalTrapdoor.corners(vworld.getBlockState(leaf.getPos()),leaf,pose);
                        double outward=0;for(int i=0;i<8;i++)outward+=(moving[i][0]-closed[i][0])*facing.getFrontOffsetX()+(moving[i][2]-closed[i][2])*facing.getFrontOffsetZ();
                        require(invertedBase?outward>0:outward<0,"opposite-slope row moves inside the convex bend");
                    }
                }
            }
            for(int mode:new int[]{0,1}) {
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
                for(int row=0;row<2;row++)for(int col=0;col<5;col++)place(world,base.offset(width,col).up(row),mode,facing,row==1,slide,0);
                TileEntityProgrammableDiagonalTrapdoor root=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(base);
                require(root.group().size()==10,"opposite-slope rectangle did not join");
                for(TileEntityProgrammableTrapdoor raw:root.group()) {
                    TileEntityProgrammableDiagonalTrapdoor leaf=(TileEntityProgrammableDiagonalTrapdoor)raw;
                    IBlockState state=world.getBlockState(leaf.getPos());
                    double[][] closed=BlockProgrammableDiagonalTrapdoor.corners(state,leaf,0),opened=BlockProgrammableDiagonalTrapdoor.corners(state,leaf,1);
                    double outward=0;for(int i=0;i<8;i++)outward+=(opened[i][0]-closed[i][0])*facing.getFrontOffsetX()+(opened[i][2]-closed[i][2])*facing.getFrontOffsetZ();
                    require(outward<0,"joined rectangle row moves inside convex bend");
                    leaf.readFromNBT(leaf.writeToNBT(new NBTTagCompound()));
                    require(leaf.rotationReverse()==(slide?leaf.isReverse():leaf.isReverse() ^ !leaf.isInverted()),"rectangle outside direction lost after reload");
                }
            }
            for(boolean stagger:new boolean[]{false,true}) {
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
                BlockPos step=stagger?new BlockPos(facing.getOpposite().getDirectionVec()).up():new BlockPos(0,1,0);
                for(int row=0;row<2;row++)for(int col=0;col<5;col++)place(world,base.offset(width,col).add(step.getX()*row,row,step.getZ()*row),1,facing,false,slide,0);
                TileEntityProgrammableDiagonalTrapdoor first=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(base);
                require(first.group().size()==10,"5x2 diagonal rectangle did not join "+facing+" stagger="+stagger+" size="+first.group().size());first.requestOpen(true);
                for(TileEntityProgrammableTrapdoor leaf:first.group()){require(open(world,leaf.getPos()),"rectangle missed leaf");leaf.readFromNBT(leaf.writeToNBT(new NBTTagCompound()));require(leaf.group().size()==10,"diagonal rectangle save lost");}
            }
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            TileEntityProgrammableDiagonalTrapdoor first=place(world,base,1,facing,false,slide,0);
            place(world,base.up().offset(facing.getOpposite()),1,facing,false,slide,0);
            require(first.group().size()==2,"staggered full-width panels did not connect");
        }
    }
    private static void partialPatches() {
        List<int[]> orders=new ArrayList<>();permutations(new int[]{0,1,2},0,orders);
        for(int mode:new int[]{0,2})for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(boolean sliding:new boolean[]{false,true})for(int layout=0;layout<3;layout++)for(int variant=0;variant<3;variant++)for(int[] order:orders) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos base=new BlockPos(10,100,10);
            EnumFacing across=facing.rotateY(),depth=facing.getOpposite(),along=mode==2?depth:EnumFacing.UP;
            BlockPos step=layout==0?new BlockPos(depth.getDirectionVec()):layout==1?new BlockPos(along.getDirectionVec())
                    :mode==2?new BlockPos(depth.getDirectionVec()).add(0,inverted?-1:1,0):new BlockPos((inverted?facing:depth).getDirectionVec()).up();
            BlockPos[] cells={base,base.offset(across),base.offset(across).add(step)};
            for(int i:order)place(world,cells[i],mode,i==2 && variant==2?facing.getOpposite():facing,inverted ^ (i==2 && variant!=0),sliding,0);
            for(BlockPos cell:cells) {
                TileEntityProgrammableDiagonalTrapdoor leaf=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(cell);
                require(leaf.group().size()==3,"partial mode "+mode+" layout "+layout+" missed third member: "+Arrays.toString(order));
                leaf.requestOpen(true);for(BlockPos other:cells)require(open(world,other),"partial patch did not open from every member");
                leaf.requestOpen(false);for(BlockPos other:cells)require(!open(world,other),"partial patch did not close from every member");
            }
            for(BlockPos cell:cells){TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)world.getTileEntity(cell);leaf.readFromNBT(leaf.writeToNBT(new NBTTagCompound()));}
            TileEntityProgrammableDiagonalTrapdoor root=(TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(base);
            require(root.group().size()==3,"partial patch lost saved links");
            // Reload the old pair-plus-single save representation, then use any member.
            for(int i=0;i<cells.length;i++) {
                TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)world.getTileEntity(cells[i]);NBTTagCompound old=leaf.writeToNBT(new NBTTagCompound());
                old.removeTag("TrapdoorAssembly");old.removeTag("TrapdoorSquare");old.removeTag("TrapdoorPartner");
                if(i<2)old.setLong("TrapdoorPartner",cells[1-i].toLong());leaf.readFromNBT(old);
            }
            require(root.group().size()==2,"legacy split-pair fixture did not reload");
            EntityPlayer owner=player(world);block.onBlockActivated(world,cells[2],world.getBlockState(cells[2]),owner,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
            require(root.group().size()==3,"ordinary use did not repair old split patch");
            for(BlockPos cell:cells)require(open(world,cell),"old patch activation missed member");
            root.configureGroup(7,mode,sliding,0,19,inverted,false,false,facing);
            for(TileEntityProgrammableTrapdoor leaf:root.group())require(leaf.getHousingTexture()==7 && leaf.getRedstoneChannel()==19 && !leaf.isTileTexture(),"partial patch configuration missed member");
            block.breakBlock(world,cells[1],world.getBlockState(cells[1]));world.setBlockState(cells[1],Blocks.AIR.getDefaultState(),2);
            for(BlockPos cell:new BlockPos[]{cells[0],cells[2]})require(((TileEntityProgrammableTrapdoor)world.getTileEntity(cell)).group().size()==1,"removed bridge retained stale membership");
            place(world,cells[1],mode,facing,inverted,sliding,0);require(root.group().size()==3,"replaced bridge did not rebuild patch");
            TileEntityProgrammableDiagonalTrapdoor perpendicular=place(world,base.down(),mode,facing.rotateY(),inverted,sliding,0);require(perpendicular.group().size()==1 && root.group().size()==3,"perpendicular neighbor joined patch");
        }
        System.out.println("PASS: three-leaf half-width/half-height patches, horizontal/ordinary/staggered layouts and every placement order");
    }
    private static void slidingStyles() {
        for(int mode=0;mode<3;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}) {
            int turns=BlockProgrammableTrapdoor.quarterTurns(facing);EnumFacing across=facing.rotateY();
            double[][] closed=DiagonalTrapdoorGeometry.corners(mode,inverted,turns,true,reverse,0);
            for(int step=0;step<=16;step++) {
                double pose=step/16D;
                double[][] inset=DiagonalTrapdoorGeometry.corners(mode,inverted,turns,true,reverse,pose,reverse?15/16D:1/16D,15/16D,1,true);
                double[][] old=DiagonalTrapdoorGeometry.corners(mode,inverted,turns,true,reverse,pose);
                double[][] over=DiagonalTrapdoorGeometry.corners(mode,inverted,turns,true,reverse,pose,reverse?15/16D:1/16D,15/16D,1,false);
                for(int v=0;v<8;v++)for(int axis=0;axis<3;axis++) {
                    double shift=(reverse?1:-1)*pose*15/16D*(axis==0?across.getFrontOffsetX():axis==2?across.getFrontOffsetZ():0);
                    require(Math.abs(inset[v][axis]-closed[v][axis]-shift)<1e-8,"into-wall slider lifts or delays sideways motion");
                    require(Math.abs(old[v][axis]-over[v][axis])<1e-8,"over-wall slider changed legacy motion");
                }
            }
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos base=new BlockPos(10,100,10);
            TileEntityProgrammableDiagonalTrapdoor source=place(world,base,mode,facing,inverted,true,0);source.setSlideIntoWall(true);
            ItemStack configured=block.configuredDrop(source);require(configured.getSubCompound("BlockEntityTag").getBoolean("TrapdoorSlideIntoWall"),"configured item lost sliding style");
            place(world,base.offset(across),mode,facing,inverted,true,0);
            for(TileEntityProgrammableTrapdoor leaf:source.group())require(leaf.isSlideIntoWall(),"joining lost sliding style");
            source.configureGroup(0,mode,true,0,0,inverted,false,true,facing,false);
            for(TileEntityProgrammableTrapdoor leaf:source.group())require(!leaf.isSlideIntoWall(),"group switch did not restore over-wall mode");
            source.configureGroup(0,mode,true,0,0,inverted,false,true,facing,true);
            NBTTagCompound captured=ProgrammableSettings.capture(world,source.getPos());
            require(ProgrammableSettings.applyToItem(new ItemStack(item),captured).getSubCompound("BlockEntityTag").getBoolean("TrapdoorSlideIntoWall"),"Duplifier configured item lost sliding style");
            TileEntityProgrammableDiagonalTrapdoor target=place(world,base.offset(across,5),mode,facing,inverted,false,0);
            require(ProgrammableSettings.apply(world,target.getPos(),captured) && target.isSliding() && target.isSlideIntoWall(),"Duplifier lost sliding style");
            int movement=0;while(!DuplifierApplyOptions.OPTIONS[movement].key.equals(ProgrammableSettings.DOOR_SLIDING))movement++;
            NBTTagCompound filtered=DuplifierApplyOptions.selected(captured,DuplifierApplyOptions.ALL & ~(1L<<movement));
            target.setSlideIntoWall(false);target.configure(0,mode,false,0,0);ProgrammableSettings.apply(world,target.getPos(),filtered);
            require(!target.isSliding() && !target.isSlideIntoWall(),"disabled movement copy overwrote sliding style");
            NBTTagCompound saved=source.writeToNBT(new NBTTagCompound());source.readFromNBT(saved);require(source.isSlideIntoWall(),"saved sliding style lost");
            saved.removeTag("TrapdoorSlideIntoWall");source.readFromNBT(saved);require(!source.isSlideIntoWall(),"legacy slider no longer uses over-wall mode");
        }
        io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();
        com.vandorlabs.network.MessageProgrammableTrapdoor packet=new com.vandorlabs.network.MessageProgrammableTrapdoor(new BlockPos(1,2,3),0,2,true,0,0,false,false,true,EnumFacing.NORTH,true);
        packet.toBytes(bytes);com.vandorlabs.network.MessageProgrammableTrapdoor decoded=new com.vandorlabs.network.MessageProgrammableTrapdoor();decoded.fromBytes(bytes);
        io.netty.buffer.ByteBuf roundTrip=io.netty.buffer.Unpooled.buffer();decoded.toBytes(roundTrip);require(net.minecraftforge.fml.relauncher.ReflectionHelper.<Boolean,com.vandorlabs.network.MessageProgrammableTrapdoor>getPrivateValue(com.vandorlabs.network.MessageProgrammableTrapdoor.class,decoded,"slideIntoWall"),"packet lost into-wall style");bytes.release();roundTrip.release();
        System.out.println("PASS: both diagonal sliding styles, all poses, saved items, joining, group edits, copying and packet round trip");
    }
    private static void boundaries() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos a=new BlockPos(10,100,10),b=a.west();
        TileEntityProgrammableDiagonalTrapdoor first=place(world,a,0,EnumFacing.NORTH,false,true,0);
        place(world,b,1,EnumFacing.NORTH,false,true,0);require(first.mate()==null,"different modes paired");
        block.breakBlock(world,b,world.getBlockState(b));world.setBlockState(b,Blocks.AIR.getDefaultState(),2);
        place(world,b,0,EnumFacing.NORTH,true,true,0);require(first.mate()==null,"different slopes paired");
        block.breakBlock(world,b,world.getBlockState(b));world.setBlockState(b,Blocks.AIR.getDefaultState(),2);
        TileEntityProgrammableDiagonalTrapdoor second=place(world,b,0,EnumFacing.NORTH,false,true,0);require(first.mate()==second,"matching pair rejected");
        first.requestOpen(true);double[][] av=BlockProgrammableDiagonalTrapdoor.corners(world.getBlockState(a),first,1),bv=BlockProgrammableDiagonalTrapdoor.corners(world.getBlockState(b),second,1);
        require(av[0][0]>0 && bv[0][0]<0,"two-leaf slide goes inward");
        EntityPlayer denied=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"DeniedDiagonal")) {
            @Override public boolean isSpectator(){return false;}@Override public boolean isCreative(){return false;}
            @Override public boolean canPlayerEdit(BlockPos p,EnumFacing face,ItemStack stack){return !p.equals(b);}
        };
        NBTTagCompound settings=new NBTTagCompound();settings.setInteger(ProgrammableSettings.WALL_TEXTURE,15);
        require(!ProgrammableSettings.apply(world,a,settings,denied) && first.getHousingTexture()==0 && second.getHousingTexture()==0,"copy bypassed partner permission");
        first.requestOpen(false);block.onBlockActivated(world,a,world.getBlockState(a),denied,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
        require(!open(world,a) && !open(world,b),"manual bypassed partner permission");
        Vec3d start=new Vec3d(a.getX()-.5,a.getY()+.1,a.getZ()+.5),end=start.addVector(2,0,0);
        require(block.collisionRayTrace(world.getBlockState(a),world,a,start,end)==null,"ray hit empty diagonal bounding volume");
        java.util.List<AxisAlignedBB> boxes=new ArrayList<>();
        block.addCollisionBoxToList(world.getBlockState(a),world,a,new AxisAlignedBB(a.getX()+.4,a.getY()+.095,a.getZ()+.495,a.getX()+.6,a.getY()+.105,a.getZ()+.505),boxes,null,false);
        require(boxes.isEmpty(),"collision filled empty diagonal bounding volume");
        // Every opened mesh center remains collidable for each mode/direction.
        for(int mode=0;mode<3;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inv:new boolean[]{false,true})for(boolean slide:new boolean[]{false,true}) {
            NonRenderingChecks.MemoryWorld isolated=new NonRenderingChecks.MemoryWorld(false);
            TileEntityProgrammableDiagonalTrapdoor tile=place(isolated,a,mode,facing,inv,slide,0);tile.requestOpen(true);
            double[][] v=BlockProgrammableDiagonalTrapdoor.corners(isolated.getBlockState(a),tile,1);double[] c={0,0,0};for(double[] point:v)for(int axis=0;axis<3;axis++)c[axis]+=point[axis]/8;
            boxes.clear();block.addCollisionBoxToList(isolated.getBlockState(a),isolated,a,new AxisAlignedBB(a.getX()+c[0]-.01,a.getY()+c[1]-.01,a.getZ()+c[2]-.01,a.getX()+c[0]+.01,a.getY()+c[1]+.01,a.getZ()+c[2]+.01),boxes,null,false);
            require(!boxes.isEmpty(),"opened leaf has no collision");
        }
        NonRenderingChecks.MemoryWorld border=new NonRenderingChecks.MemoryWorld(false);BlockPos edge=new BlockPos(15,100,15);
        TileEntityProgrammableDiagonalTrapdoor loaded=place(border,edge,2,EnumFacing.SOUTH,false,true,0);place(border,edge.east(),2,EnumFacing.SOUTH,false,true,0);border.chunkLimit=true;
        loaded.localInputChanged();require(loaded.group().size()==1,"unloaded partner visited");
        // Height settings from flat trapdoors must not silently choose a diagonal mode.
        NBTTagCompound flat=new NBTTagCompound();flat.setInteger(ProgrammableSettings.TRAPDOOR_POSITION,2);
        require(!ProgrammableSettings.apply(world,a,flat) && first.getPosition()==0,"flat placement height copied as diagonal geometry");
        settings=ProgrammableSettings.capture(world,a);settings.getCompoundTag(ProgrammableSettings.DIAGONAL_GEOMETRY).setInteger("mode",1);
        require(ProgrammableSettings.apply(world,a,settings) && first.mate()==second && second.getPosition()==1,"tall width change broke pair");
        settings.getCompoundTag(ProgrammableSettings.DIAGONAL_GEOMETRY).setInteger("mode",2);
        require(ProgrammableSettings.apply(world,a,settings) && first.mate()==null && second.mate()==null && second.getPosition()==2,"layout change kept stale links");
        // Off-hand configured placement must match the wall's geometry/facing rules too.
        EntityPlayer placer=player(world);TileEntityProgrammableDiagonalTrapdoor config=new TileEntityProgrammableDiagonalTrapdoor();config.configure(0,2,false,1,0);
        ItemStack configured=new ItemStack(item);configured.setTagInfo("BlockEntityTag",config.itemSettings());placer.setHeldItem(EnumHand.OFF_HAND,configured);
        IBlockState expected=((BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_WALL).getStateForPlacement(world,a,EnumFacing.UP,.1F,.5F,.9F,0,placer,EnumHand.OFF_HAND);
        IBlockState actual=block.getStateForPlacement(world,a,EnumFacing.UP,.1F,.5F,.9F,0,placer,EnumHand.OFF_HAND);
        require(actual.getValue(BlockTrapDoor.FACING)==expected.getValue(BlockProgrammableWall.FACING),"off-hand configured placement differs");
    }
    private static void recipe() {
        try(java.io.InputStream stream=DiagonalTrapdoorChecks.class.getResourceAsStream("/assets/vandorlabs/recipes/programmable_diagonal_trapdoor.json")) {
            require(stream!=null,"recipe resource missing");com.google.gson.JsonObject data=new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(stream,"UTF-8")).getAsJsonObject();
            net.minecraft.item.crafting.IRecipe recipe=net.minecraftforge.common.crafting.CraftingHelper.getRecipe(data,new net.minecraftforge.common.crafting.JsonContext("vandorlabs"));
            net.minecraft.inventory.InventoryCrafting grid=new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container(){@Override public boolean canInteractWith(EntityPlayer p){return true;}},3,3);
            grid.setInventorySlotContents(0,new ItemStack(ModBlocks.PROGRAMMABLE_TRAPDOOR));grid.setInventorySlotContents(4,new ItemStack(ModItems.INDUSTRIAL_ALLOY_INGOT));
            require(recipe.matches(grid,new NonRenderingChecks.MemoryWorld(false)),"recipe match");require(recipe.getCraftingResult(grid).getItem()==item,"recipe result");grid.setInventorySlotContents(8,new ItemStack(ModItems.INDUSTRIAL_ALLOY_INGOT));require(!recipe.matches(grid,new NonRenderingChecks.MemoryWorld(false)),"recipe accepts extra ingredient");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static EntityPlayer player(NonRenderingChecks.MemoryWorld world){return new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"DiagonalChecks")){
        @Override public boolean isSpectator(){return false;}@Override public boolean isCreative(){return capabilities.isCreativeMode;}
    };}
    private static TileEntityProgrammableDiagonalTrapdoor place(NonRenderingChecks.MemoryWorld world,BlockPos p,int mode,EnumFacing facing,boolean inverted,boolean slide,int texture) {
        TileEntityProgrammableDiagonalTrapdoor isolated=new TileEntityProgrammableDiagonalTrapdoor();isolated.configure(texture,mode,slide,SpaceDoorData.TRIGGER_REDSTONE_ON,0);
        ItemStack stack=new ItemStack(item);stack.setTagInfo("BlockEntityTag",isolated.itemSettings());
        IBlockState state=block.getDefaultState().withProperty(BlockTrapDoor.FACING,facing).withProperty(BlockTrapDoor.HALF,inverted?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM);
        require(item.placeBlockAt(stack,null,world,p,EnumFacing.UP,.5F,.5F,.5F,state),"placement failed");return (TileEntityProgrammableDiagonalTrapdoor)world.getTileEntity(p);
    }
    private static boolean open(NonRenderingChecks.MemoryWorld world,BlockPos p){return world.getBlockState(p).getValue(BlockTrapDoor.OPEN);}
    private static double distance(double[] a,double[] b){double d=0;for(int i=0;i<3;i++)d+=(a[i]-b[i])*(a[i]-b[i]);return Math.sqrt(d);}
    private static void permutations(int[] v,int at,List<int[]> result){if(at==v.length){result.add(v.clone());return;}for(int i=at;i<v.length;i++){int old=v[at];v[at]=v[i];v[i]=old;permutations(v,at+1,result);old=v[at];v[at]=v[i];v[i]=old;}}
    private static void require(boolean condition,String detail){assertions++;if(!condition)throw new IllegalStateException(detail);}
}

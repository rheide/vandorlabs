package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/** Real item placement supplies snapshots for front, oblique and inside-edge rendered regressions. */
public final class FrameEdgeRuntimeChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("frame edges: "+why);}
    private static final class Scene {final String name;final VehicleStructure structure;Scene(String name,VehicleStructure structure){this.name=name;this.structure=structure;}}
    public static void run(Minecraft mc,File output)throws Exception{
        crops();paddedBorders();doorUvs();glassFrameUvs();independentSides(mc);diagonalFrameCache(mc);int choices=0;
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if("Frame".equals(ScreenHousingTextures.category(i))){
            FrameAppearance a=new FrameAppearance();a.interior=i;TextureAtlasSprite base=mc.getTextureMapBlocks().getAtlasSprite(a.interior(""));
            for(int edge=0;edge<4;edge++){TextureAtlasSprite strip=FrameEdgeTextures.edge(base,edge);require(strip.getIconWidth()==strip.getIconHeight()&&strip.getIconWidth()<=(FrameAppearance.border(i)?64:128),"native strip or whole opaque material size");for(int pixel:strip.getFrameTextureData(0)[0])require((pixel>>>24)==255,"transparent center sampled by physical frame: "+strip.getIconName());choices++;}
        }
        List<Scene> scenes=mc.getIntegratedServer().callFromMainThread(()->place(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID()))).get();
        int captures=0;
        for(Scene scene:scenes){VehicleWorld view=new VehicleWorld(mc.world,scene.structure);
            try{for(int angle:new int[]{0,55,88}){
                BufferedImage image=capture(mc,view,scene.structure,angle);ImageIO.write(image,"png",new File(output,"shot_frame_edges_"+scene.name+"_"+angle+".png"));
                int painted=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)&0xffffff)!=0x203040)painted++;
                require(painted>60,"actual tile renderer emitted no frame at player angle: "+scene.name+" "+angle);captures++;
                if(!scene.name.contains("door")){
                    BufferedImage close=capture(mc,view,scene.structure,angle,true);ImageIO.write(close,"png",new File(output,"shot_frame_edges_"+scene.name+"_closeup_"+angle+".png"));
                    if(angle==55&&(scene.name.equals("glass")||scene.name.equals("cyan_glass")||scene.name.equals("opaque_glass")))straightInnerWalls(close,view,scene.structure,angle,scene.name);
                    captures++;
                }
            }}finally{TEAnimatedScreenSelector.unloadGroups(view);}
        }
        System.out.println("[vandorlabs][reprolab] frame-edges PASS opaque-native64-strips="+choices+" authored-four-edge-colors real-item-door-placement single-paired-large saved-geometry glass-wall-porthole player-angle-captures="+captures);
    }
    private static void independentSides(Minecraft mc)throws Exception{
        int inner=ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1"),outer=ScreenHousingTextures.screenIndex("frames/frames-door-window/gunmetal-1");
        java.lang.reflect.Field vertices=StaticSurfaceMesh.class.getDeclaredField("vertices");vertices.setAccessible(true);
        TileEntityProgrammableGlass glass=new TileEntityProgrammableGlass();glass.frameAppearance.interior=inner;glass.frameAppearance.exterior=-1;
        StaticSurfaceMesh before=GlassFrameMesh.get(glass,new GlassSurfaceGroups.Layout(0,0,1,1));glass.frameAppearance.exterior=outer;StaticSurfaceMesh after=GlassFrameMesh.get(glass,new GlassSurfaceGroups.Layout(0,0,1,1));
        independentUvs((float[])vertices.get(before),(float[])vertices.get(after),"flat glass");
        int variants=0;
        for(Block block:Block.REGISTRY)if(block instanceof BlockProgrammableWall&&(((BlockProgrammableWall)block).isPortholeShape()||block instanceof BlockProgrammableDiagonalGlass))for(int meta=0;meta<8;meta++){
            net.minecraft.block.state.IBlockState state=block.getStateFromMeta(meta);TileEntityAnimatedScreenSelector source=(TileEntityAnimatedScreenSelector)block.createTileEntity(null,state);source.frameAppearance.interior=inner;source.frameAppearance.exterior=-1;source.setGlassAppearance(1,true);
            VehicleStructure structure=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,source.writeToNBT(new NBTTagCompound()))));VehicleWorld view=new VehicleWorld(mc.world,structure);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)view.getTileEntity(BlockPos.ORIGIN);
            try{
                TextureAtlasSprite pane=TEAnimatedScreenSelector.wallSprite(tile),metal=mc.getTextureMapBlocks().getAtlasSprite(tile.frameAppearance.interior("vandorlabs:blocks/programmable_glass/metal_side"));
                PortholeHex.Slice slice=((BlockProgrammableWall)block).isPortholeShape()?TEAnimatedScreenSelector.portholeGroup(tile,state).slice(BlockPos.ORIGIN,state.getValue(BlockProgrammableWall.FACING).rotateY(),EnumFacing.UP):null;
                before=block instanceof BlockProgrammableDiagonalGlass?DiagonalWallMeshCache.frame(tile,state,pane):DiagonalWallMeshCache.get(tile,state,pane,metal,slice);
                tile.frameAppearance.exterior=outer;
                after=block instanceof BlockProgrammableDiagonalGlass?DiagonalWallMeshCache.frame(tile,state,pane):DiagonalWallMeshCache.get(tile,state,pane,metal,slice);
                independentUvs((float[])vertices.get(before),(float[])vertices.get(after),block.getRegistryName()+" metadata="+meta);variants++;
            }finally{DiagonalWallMeshCache.invalidate(view,-1,-1,-1,1,1,1);TEAnimatedScreenSelector.unloadGroups(view);}
        }
        require(variants>0,"independent side geometry covers portholes and diagonal glass");
        System.out.println("[vandorlabs][reprolab] independent-frame-sides PASS flat-glass porthole-diagonal-variants="+variants+" exterior-UV-change interior-preserved geometry-unchanged");
    }
    private static void independentUvs(float[] before,float[] after,String name){
        require(before.length==after.length,"Exterior changed geometry count: "+name);int changed=0,stable=0;
        for(int q=0;q<before.length;q+=32){boolean uvChanged=false;for(int v=0;v<4;v++)for(int field=0;field<8;field++){int at=q+v*8+field;if(field==3||field==4)uvChanged|=before[at]!=after[at];else require(before[at]==after[at],"Exterior changed position/normal: "+name);}if(uvChanged)changed++;else stable++;}
        require(changed>0&&stable>0,"Exterior must change its own emitted faces and retain other faces: "+name);
    }
    private static void diagonalFrameCache(Minecraft mc)throws Exception{
        // Find actual catalog pairs that collided in the old 31*front+interior key.
        Map<Integer,int[]> hashes=new HashMap<>();int[] first=null,second=null;
        outer:for(int front=-1;front<ScreenHousingTextures.IDS.length;front++)if(FrameAppearance.valid(front))
            for(int inner=-1;inner<ScreenHousingTextures.IDS.length;inner++)if(FrameAppearance.valid(inner)){
                int[] old=hashes.putIfAbsent(31*front+inner,new int[]{front,inner});
                if(old!=null&&old[0]!=front){first=old;second=new int[]{front,inner};break outer;}
            }
        require(first!=null,"catalog supplies colliding frame-key regression");
        TileEntityAnimatedScreenSelector original=new TileEntityAnimatedScreenSelector();original.frameAppearance.front=first[0];original.frameAppearance.interior=first[1];
        net.minecraft.block.state.IBlockState state=ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS.getDefaultState();
        VehicleStructure structure=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,original.writeToNBT(new NBTTagCompound()))));
        VehicleWorld view=new VehicleWorld(mc.world,structure);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)view.getTileEntity(BlockPos.ORIGIN);
        TextureAtlasSprite glass=mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");
        try{
            StaticSurfaceMesh before=DiagonalWallMeshCache.frame(tile,state,glass);
            tile.frameAppearance.front=second[0];tile.frameAppearance.interior=second[1];
            StaticSurfaceMesh after=DiagonalWallMeshCache.frame(tile,state,glass);
            require(before!=null&&after!=null&&before!=after,"carried diagonal frame reused colliding finish pair");
            java.lang.reflect.Field vertices=StaticSurfaceMesh.class.getDeclaredField("vertices");vertices.setAccessible(true);
            require(!Arrays.equals((float[])vertices.get(before),(float[])vertices.get(after)),"changed diagonal frame finishes retained old emitted UVs");
            require(after==DiagonalWallMeshCache.frame(tile,state,glass),"unchanged exact finish pair lost cache reuse");
            System.out.println("[vandorlabs][reprolab] diagonal-frame-cache PASS colliding-finish-pairs carried-view emitted-UV-change exact-pair-reuse");
        }finally{DiagonalWallMeshCache.invalidate(view,-1,-1,-1,1,1,1);TEAnimatedScreenSelector.unloadGroups(view);}
    }
    private static void doorUvs(){
        Block block=Block.getBlockFromName("vandorlabs:programmable_door");TileEntitySpaceDoor tile=new TileEntitySpaceDoor();tile.configure(2,1,true);
        net.minecraft.client.renderer.block.model.IBakedModel base=DoorRenderModels.get(block,tile.metadata(false,false,0)).model;
        FrameAppearance appearance=new FrameAppearance();appearance.front=ScreenHousingTextures.screenIndex("frames/frames-door-window/gunmetal-1");appearance.interior=ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1");
        net.minecraft.client.renderer.block.model.IBakedModel selected=FrameDoorModel.get(base,appearance);java.util.Set<String> strips=new java.util.HashSet<>();int checked=0;
        require(FrameDoorModel.get(base,new FrameAppearance())==base,"Original selection preserves exact default model");
        for(int face=-1;face<6;face++){
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> before=base.getQuads(null,face<0?null:EnumFacing.getFront(face),0),after=selected.getQuads(null,face<0?null:EnumFacing.getFront(face),0);require(before.size()==after.size(),"frame UV selection changed door geometry");
            for(int q=0;q<before.size();q++){int[] a=before.get(q).getVertexData(),b=after.get(q).getVertexData();require(a.length==b.length,"extended door vertex format lost");int stride=a.length/4;for(int v=0;v<4;v++)for(int field=0;field<stride;field++)if(field!=4&&field!=5)require(a[v*stride+field]==b[v*stride+field],"frame selection changed vertex geometry or extended payload");
                TextureAtlasSprite sprite=after.get(q).getSprite();if(sprite.getIconName().contains("/frames/")){strips.add(sprite.getIconName());for(int v=0;v<4;v++){double u=sprite.getUnInterpolatedU(Float.intBitsToFloat(b[v*stride+4])),tv=sprite.getUnInterpolatedV(Float.intBitsToFloat(b[v*stride+5]));require(u>=-.001&&u<=16.001&&tv>=-.001&&tv<=16.001,"door edge UV leaves opaque crop");}checked++;}
            }
        }
        require(checked>0&&strips.size()>=4,"door does not select distinct authored frame edges");
    }
    private static void glassFrameUvs()throws Exception{
        TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.frameAppearance.front=ScreenHousingTextures.screenIndex("frames/frames-door-window/door_interior_dark");tile.frameAppearance.interior=ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1");
        TextureAtlasSprite front=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(tile.frameAppearance.front("")),inner=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(tile.frameAppearance.interior(""));
        StaticSurfaceMesh mesh=GlassFrameMesh.get(tile,new GlassSurfaceGroups.Layout(0,0,1,1));
        java.lang.reflect.Field field=StaticSurfaceMesh.class.getDeclaredField("vertices");field.setAccessible(true);float[] data=(float[])field.get(mesh);int checked=0,directions=0;
        for(int q=0;q<data.length;q+=32){
            double[][] points=new double[4][3];double minX=16,maxX=0,minY=16,maxY=0,minU=1,maxU=0,minV=1,maxV=0;
            for(int v=0;v<4;v++){int at=q+v*8;points[v]=new double[]{data[at],data[at+1],data[at+2]};minX=Math.min(minX,data[at]);maxX=Math.max(maxX,data[at]);minY=Math.min(minY,data[at+1]);maxY=Math.max(maxY,data[at+1]);minU=Math.min(minU,data[at+3]);maxU=Math.max(maxU,data[at+3]);minV=Math.min(minV,data[at+4]);maxV=Math.max(maxV,data[at+4]);}
            if(Math.abs(data[q+7])>.9){
                require(Math.abs((maxU-minU)/(front.getMaxU()-front.getMinU())*16-(maxX-minX))<.001,"opaque square front stretched across a narrow rail");
                require(Math.abs((maxV-minV)/(front.getMaxV()-front.getMinV())*16-(maxY-minY))<.001,"opaque square front stretched vertically");checked++;
            }else{
                TextureAtlasSprite edge=FrameEdgeTextures.edge(inner,points);
                require(minU>=edge.getMinU()-.00001&&maxU<=edge.getMaxU()+.00001&&minV>=edge.getMinV()-.00001&&maxV<=edge.getMaxV()+.00001,"inner rim sampled wrong authored edge");
                for(int kind=0;kind<4;kind++)if(edge==FrameEdgeTextures.edge(inner,kind))directions|=1<<kind;
            }
        }
        require(checked==8&&directions==15,"actual emitted frame lacks complete opaque fronts or four directional interiors");
    }
    private static void paddedBorders()throws Exception{
        BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int y=2;y<62;y++)for(int x=2;x<62;x++)if(x<5||x>=59||y<5||y>=55)image.setRGB(x,y,0xff30b8df);
        require(ScreenFrameBorders.extent(image,0,64,64)==5,"outer transparent padding hid a frame");
        require(ScreenFrameBorders.extent(image,3,64,64)==9,"asymmetric bottom thickness was discarded");
        for(String source:new String[]{"frames/frames-screen-full/frame_lightbars_thin_1254","frames/frames-screen-full/frame_lightbars_1254","frames/frames-screen-full/frame_cyan_rim_1254","frames/frames-screen-full/frame_cyan_rim_thin_1254","frames/frames-screen-full/frame_bolted_thin_1254","frames/frames-company-half-full/ashiro/ashiro_thick_full_1254x1254","frames/frames-company-half-full/ashiro/ashiro_thin_full_1254x1254","frames/frames-company-half-full/seyr/seyr_thick_full_1254x1254","frames/frames-company-half-full/seyr/seyr_thin_full_1254x1254","frames/frames-company-half-full/pinnacle/pinnacle_thin_full_1254x1254"}){
            int choice=ScreenHousingTextures.screenIndex(source);require(choice>0,"reported frame fixture missing: "+source);
            for(double width:ScreenFrameBorders.get(choice))require(width>0,"reported frame has no emitted border: "+source);
        }
        int pinnacle=ScreenHousingTextures.screenIndex("frames/frames-company-half-full/pinnacle/pinnacle_thin_full_1254x1254");double[] widths=ScreenFrameBorders.get(pinnacle);
        require(widths[3]>widths[2],"Pinnacle lower crest did not enlarge the content inset");
        for(int edge=0;edge<4;edge++){
            BufferedImage strip=FrameEdgeTextures.crop(image,edge);
            require((strip.getRGB(32,32)&0xffffff)==0x30b8df,"padded edge crop stretched canvas padding into interior");
        }
        FrameAppearance interior=new FrameAppearance();interior.interior=pinnacle;
        TextureAtlasSprite rail=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(interior.interior(""));
        double[][] porthole={ {3,3,6,0,0},{3,13,6,10,0},{3,13,10,10,4},{3,3,10,0,4} };
        FrameEdgeTextures.map(rail,porthole);
        require(porthole[0][4]==0&&porthole[1][4]==0&&porthole[2][4]==16&&porthole[3][4]==16,"vertical porthole stretched along its rim instead of across wall depth");
        double[][] side={ {3,3,6,0,13},{3,13,6,0,3},{3,13,10,4,3},{3,3,10,4,13} };
        FrameEdgeTextures.map(rail,side);
        require(side[0][4]==0&&side[1][4]==0&&side[2][4]==16&&side[3][4]==16,"box side lost its distinct legacy UV orientation");
        // Inspect actual emitted geometry, including a fitted two-block frame.
        TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.frameAppearance.front=pinnacle;
        java.lang.reflect.Field vertices=StaticSurfaceMesh.class.getDeclaredField("vertices");vertices.setAccessible(true);
        for(int rows:new int[]{1,2}){
            float[] data=(float[])vertices.get(GlassFrameMesh.get(tile,new GlassSurfaceGroups.Layout(0,0,1,rows)));
            double bottom=widths[3]*rows*16;boolean found=false;
            for(int q=0;q<data.length;q+=32)if(Math.abs(data[q+6])>.9&&Math.abs(data[q+1]-bottom)<.0001)found=true;
            require(found,"emitted glass bottom wall ignored asymmetric/fitted frame thickness");
        }
    }
    private static void crops(){
        BufferedImage authored=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);int[] colors={0xffed3010,0xff21d030,0xff2040ef,0xffeac020};
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){int edge=y<4?0:y>=60?1:x<4?2:x>=60?3:-1;if(edge>=0)authored.setRGB(x,y,colors[edge]);}
        for(int edge=0;edge<4;edge++){BufferedImage strip=FrameEdgeTextures.crop(authored,edge);require(strip.getRGB(32,32)==colors[edge],"wrong authored edge selected: "+edge);for(int y=0;y<64;y++)for(int x=0;x<64;x++)require((strip.getRGB(x,y)>>>24)==255,"synthetic hollow center leaked into edge");}
    }
    private static List<Scene> place(WorldServer world,EntityPlayerMP actor){
        List<Scene> result=new ArrayList<>();BlockPos base=new BlockPos(758,178,296);ItemStack held=actor.getHeldItemMainhand();double px=actor.posX,py=actor.posY,pz=actor.posZ;float yaw=actor.rotationYaw;
        int frame=ScreenHousingTextures.screenIndex("frames/frames-door-window/gunmetal-1"),inner=ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1");require(frame>0&&inner>0,"supplied source artwork");
        try{for(String name:new String[]{"single_door","paired_door","large_door","glass","cyan_glass","opaque_glass","diagonal_glass","wall","porthole","diagonal_porthole"}){
            for(BlockPos at:BlockPos.getAllInBox(base.add(-3,-1,-3),base.add(5,5,3))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
            for(int x=-3;x<=5;x++)for(int z=-2;z<=2;z++)world.setBlockState(base.add(x,-1,z),Blocks.STONE.getDefaultState(),2);
            boolean door=name.contains("door");Block block=Block.getBlockFromName("vandorlabs:"+(door?(name.equals("large_door")?"large_programmable_door":"programmable_door"):(name.equals("glass")||name.equals("cyan_glass")||name.equals("opaque_glass"))?"programmable_glass":name.equals("diagonal_glass")?"programmable_diagonal_glass":name.equals("diagonal_porthole")?"programmable_diagonal_porthole":name.equals("wall")?"programmable_wall":"programmable_porthole_wall"));require(block!=null,"registered fixture block");
            actor.rotationYaw=180;actor.setPosition(base.getX()+.5,base.getY(),base.getZ()+2);int count=name.equals("paired_door")?2:1;
            for(int i=0;i<count;i++){
                ItemStack stack=new ItemStack(block,1);actor.setHeldItem(EnumHand.MAIN_HAND,stack);BlockPos support=base.east(i).down();
                require(stack.getItem().onItemUse(actor,world,support,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary inventory placement "+name);
            }
            List<VehicleStructure.Cell> cells=new ArrayList<>();int members=0;
            for(BlockPos at:BlockPos.getAllInBox(base.add(-3,0,-1),base.add(5,4,1))){TileEntity tile=world.getTileEntity(at);if(tile==null||world.getBlockState(at).getBlock()!=block)continue;
                if(tile instanceof TileEntitySpaceDoor){TileEntitySpaceDoor d=(TileEntitySpaceDoor)tile;d.configure(2,1,true);d.frameAppearance.front=frame;d.frameAppearance.interior=inner;}
                else if(tile instanceof TileEntityProgrammableGlass){TileEntityProgrammableGlass g=(TileEntityProgrammableGlass)tile;g.setAppearance(1,true);g.frameAppearance.front=name.equals("cyan_glass")?inner:name.equals("opaque_glass")?ScreenHousingTextures.screenIndex("frames/frames-door-window/door_interior_dark"):frame;g.frameAppearance.interior=inner;}
                else if(tile instanceof TileEntityAnimatedScreenSelector){TileEntityAnimatedScreenSelector w=(TileEntityAnimatedScreenSelector)tile;w.frameAppearance.interior=inner;if(name.equals("diagonal_glass")){w.frameAppearance.front=frame;w.setGlassAppearance(1,true);}}
                NBTTagCompound saved=VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(base));cells.add(new VehicleStructure.Cell(at.subtract(base),world.getBlockState(at),saved));members++;
            }
            require(!door||members==(name.equals("large_door")?9:count*2),"actual door placement retains all upper/lower members: "+name+" "+members);
            int minX=0,minY=0,minZ=0;for(VehicleStructure.Cell cell:cells){minX=Math.min(minX,cell.pos.getX());minY=Math.min(minY,cell.pos.getY());minZ=Math.min(minZ,cell.pos.getZ());}
            BlockPos shift=new BlockPos(-minX,-minY,-minZ);List<VehicleStructure.Cell> rebased=new ArrayList<>();for(VehicleStructure.Cell cell:cells)rebased.add(new VehicleStructure.Cell(cell.pos.add(shift),cell.state,VehicleTileData.translated(cell.tileData(),shift)));
            result.add(new Scene(name,VehicleStructure.display(rebased)));
        }}finally{for(BlockPos at:BlockPos.getAllInBox(base.add(-3,-1,-3),base.add(5,5,3)))world.setBlockToAir(at);actor.setHeldItem(EnumHand.MAIN_HAND,held);actor.rotationYaw=yaw;actor.setPosition(px,py,pz);}
        return result;
    }
    private static BufferedImage capture(Minecraft mc,VehicleWorld view,VehicleStructure structure,int angle){return capture(mc,view,structure,angle,false);}
    private static final class CloseCamera {
        final double cx,cy,cz,half,sin,cos,tiltSin=Math.sin(Math.toRadians(12)),tiltCos=Math.cos(Math.toRadians(12));
        CloseCamera(VehicleStructure structure,int angle){
            cx=(structure.bounds.minX+structure.bounds.maxX)/2;cy=(structure.bounds.minY+structure.bounds.maxY)/2;cz=(structure.bounds.minZ+structure.bounds.maxZ)/2;sin=Math.sin(Math.toRadians(angle));cos=Math.cos(Math.toRadians(angle));
            double extent=0;for(int i=0;i<8;i++){
                double x=((i&1)==0?structure.bounds.minX:structure.bounds.maxX)-cx,y=((i&2)==0?structure.bounds.minY:structure.bounds.maxY)-cy,z=((i&4)==0?structure.bounds.minZ:structure.bounds.maxZ)-cz;
                extent=Math.max(extent,Math.abs(cos*x+sin*z));extent=Math.max(extent,Math.abs(tiltCos*y-tiltSin*(-sin*x+cos*z)));
            }half=Math.max(.3,extent*1.25);
        }
        int[] pixel(double x,double y,double z){x-=cx;y-=cy;z-=cz;return new int[]{(int)Math.round(256+(cos*x+sin*z)*256/half),(int)Math.round(256-(tiltCos*y-tiltSin*(-sin*x+cos*z))*256/half)};}
    }
    /** Probe only the opaque inner wall at mid-depth; the glass opening stays transparent. */
    private static void straightInnerWalls(BufferedImage image,VehicleWorld view,VehicleStructure structure,int angle,String name){
        CloseCamera camera=new CloseCamera(structure,angle);int checked=0;
        for(TileEntity raw:view.tiles.values())if(raw instanceof TileEntityProgrammableGlass){
            net.minecraft.block.state.IBlockState state=view.getBlockState(raw.getPos());double offset=PanelDepth.offset(state.getValue(BlockGlassWall.DEPTH))/16D;
            for(int side=0;side<2;side++)for(int along=3;along<=13;along++){
                double x=(side==0?1D:15D)/16,y=along/16D,z=.5+offset;
                if(state.getValue(BlockGlassWall.ROTATED)){double previousX=x;x=1-z;z=previousX;}
                int[] pixel=camera.pixel(raw.getPos().getX()+x,raw.getPos().getY()+y,raw.getPos().getZ()+z);
                require(pixel[0]>=0&&pixel[0]<512&&pixel[1]>=0&&pixel[1]<512,"closeup wall probe outside framebuffer");
                require((image.getRGB(pixel[0],pixel[1])&0xffffff)!=0x203040,"background slit in "+name+" straight inner wall at "+Arrays.toString(pixel)+" side="+side+" along="+along);checked++;
            }
        }
        require(checked>=22,"closeup did not exercise actual glass inner walls");
        System.out.println("[vandorlabs][reprolab] frame-inner-wall-closeup PASS "+name+" opaque-pixel-probes="+checked);
    }
    private static BufferedImage capture(Minecraft mc,VehicleWorld view,VehicleStructure structure,int angle,boolean closeup){
        int size=512;Framebuffer f=new Framebuffer(size,size,true);int oldPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
        CloseCamera camera=closeup?new CloseCamera(structure,angle):null;
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();if(closeup)GlStateManager.ortho(-camera.half,camera.half,-camera.half,camera.half,.1,40);else org.lwjgl.util.glu.GLU.gluPerspective(45,1,.1F,40);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            f.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.depthFunc(GL11.GL_LEQUAL);GlStateManager.disableCull();GlStateManager.disableFog();GlStateManager.disableLighting();GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.01F);GlStateManager.color(1,1,1,1);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
            GlStateManager.translate(0,0,-6);GlStateManager.rotate(12,1,0,0);GlStateManager.rotate(angle,0,1,0);if(closeup)GlStateManager.translate(-camera.cx,-camera.cy,-camera.cz);else GlStateManager.translate(-structure.bounds.maxX/2,-structure.bounds.maxY/2,-.5);
            for(int pass=0;pass<=1;pass++){ForgeHooksClient.setRenderPass(pass);for(TileEntity tile:view.tiles.values())if(tile.shouldRenderInPass(pass)){
                @SuppressWarnings("unchecked") net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer<TileEntity> renderer=(net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer<TileEntity>)(Object)TileEntityRendererDispatcher.instance.getRenderer(tile);if(renderer==null)continue;renderer.render(tile,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ(),0,-1,1);
            }}GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(size,size,f);
        }finally{ForgeHooksClient.setRenderPass(oldPass);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);f.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.viewport(0,0,mc.displayWidth,mc.displayHeight);}
    }
    private FrameEdgeRuntimeChecks(){}
}

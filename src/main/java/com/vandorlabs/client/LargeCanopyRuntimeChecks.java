package com.vandorlabs.client;
import com.vandorlabs.canopy.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.init.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.ItemStack;
import java.util.*;
/** Extreme shape, shared geometry, ownership rejection, persistence, palette and projected icon bounds. */
public final class LargeCanopyRuntimeChecks {
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("large-canopy: "+text);}
    public static void run(EntityPlayerMP player){
        materialUvs();
        for(int width:new int[]{3,5})for(int depth:new int[]{0,1,15,16,17,79,80})for(int scale:new int[]{10,71,100}){
            LargeCanopySettings settings=new LargeCanopySettings();settings.depth=depth;settings.scale=scale;List<CanopyMesh.Face> faces=LargeCanopyGeometry.faces(width,settings);require(!faces.isEmpty(),"empty shape");
            for(CanopyMesh.Face face:faces)for(Vec3d v:face.vertices)require(Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z) && v.x>=0 && v.x<=width && v.y>=0 && v.y<=width && v.z>=0 && v.z<=Math.max(1D/64,depth/16D),"shape bounds");
            for(EnumFacing facing:EnumFacing.HORIZONTALS){List<AxisAlignedBB> boxes=CanopyMesh.collision(faces,"front_canopy_glass",width,0,facing,BlockPos.ORIGIN);require(!boxes.isEmpty(),"empty collision");for(AxisAlignedBB box:boxes)require(box.maxX>box.minX && box.maxY>box.minY && box.maxZ>box.minZ,"zero-volume collision");
                Vec3d start=CanopyMesh.rotate(new Vec3d(width/2D,width/2D,-1),facing),end=CanopyMesh.rotate(new Vec3d(width/2D,width/2D,6),facing);require(CanopyMesh.trace(faces,"front_canopy_glass",width,0,facing,BlockPos.ORIGIN,start,end)!=null,"center ray missed");}
        }
        for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<3;tint++){LargeCanopySettings settings=new LargeCanopySettings();settings.shade=shade;settings.tint=tint;NBTTagCompound tag=new NBTTagCompound();settings.write(tag);LargeCanopySettings restored=new LargeCanopySettings();restored.read(tag);require(settings.key().equals(restored.key()),"settings NBT");}
        BlockCanopy block=(BlockCanopy)com.vandorlabs.blocks.ModBlocks.BLOCKS.stream().filter(b->b.getRegistryName().getResourcePath().equals("large_front_canopy_glass")).findFirst().get();
        BlockPos pos=new BlockPos(40,20,40);for(int x=0;x<5;x++)for(int y=0;y<5;y++)for(int z=0;z<5;z++)player.world.setBlockToAir(pos.add(x,y,z));
        player.world.setBlockState(pos,block.getDefaultState(),3);block.onBlockPlacedBy(player.world,pos,player.world.getBlockState(pos),player,new ItemStack(block));TileEntityCanopy tile=TileEntityCanopy.at(player.world,pos);require(tile.cells().contains(pos),"default anchor footprint");int originalCells=tile.cells().size();
        TileEntityCanopy altered=TileEntityCanopy.at(player.world,pos.east());altered.anchor=pos.add(20,0,0);require(!LargeCanopyOwnership.apply(tile,player,80,10,12,2) && tile.large.depth==16,"foreign member was accepted");altered.anchor=pos;
        LargeCanopySettings expanded=new LargeCanopySettings();expanded.depth=80;expanded.scale=10;BlockPos obstruct=tile.cells(expanded).stream().filter(cell->!tile.cells().contains(cell)).findFirst().get();player.world.setBlockState(obstruct,Blocks.IRON_BLOCK.getDefaultState(),3);require(!LargeCanopyOwnership.apply(tile,player,80,10,12,2) && tile.cells().size()==originalCells && tile.large.depth==16,"blocked expansion mutated assembly");player.world.setBlockToAir(obstruct);
        require(LargeCanopyOwnership.apply(tile,player,80,10,12,2) && tile.cells().size()<125 && tile.cells().size()>originalCells,"hollow maximum depth footprint");for(BlockPos cell:tile.cells())require(TileEntityCanopy.at(player.world,cell).owner()==tile,"member ownership");
        LargeCanopySettings wider=new LargeCanopySettings();wider.depth=80;wider.scale=100;BlockPos interior=tile.cells(wider).stream().filter(cell->!tile.cells().contains(cell)).findFirst().get();
        player.world.setBlockState(interior,Blocks.IRON_BLOCK.getDefaultState(),3);
        require(LargeCanopyOwnership.apply(tile,player,80,10,13,1) && player.world.getBlockState(interior).getBlock()==Blocks.IRON_BLOCK,"interior block survives palette edit");
        require(!LargeCanopyOwnership.apply(tile,player,80,100,13,1) && tile.large.scale==10,"resize through interior block accepted");
        player.world.setBlockToAir(interior);
        ItemStack item=block.configuredStack(tile);require(item.hasTagCompound() && item.getTagCompound().getInteger("CanopyDepth")==80,"configured item settings");
        require(LargeCanopyOwnership.apply(tile,player,0,100,0,0) && tile.cells().size()<=25 && player.world.isAirBlock(pos.add(0,0,4)),"shrinking leaves stale cells");
        for(net.minecraft.block.Block raw:ModBlocks.BLOCKS)if(raw instanceof BlockCanopy){
            BlockCanopy canopy=(BlockCanopy)raw;List<CanopyMesh.Face> base=CanopyMesh.MODELS.get(canopy.kind.equals("regular_canopy_glass")?"regular_1x1_00":canopy.kind);
            for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<3;tint++)for(CanopyMesh.Face face:CanopyFinishes.faces(base,shade,tint))if(face.baseMaterial.equals("glass"))require(face.material.equals(GlassTextures.texture(shade,1,tint)),"canopy palette "+canopy.kind);
            if(LargeCanopySettings.supports(canopy.kind))for(EnumFacing facing:EnumFacing.HORIZONTALS){BlockPos clicked=new BlockPos(10,80,10);BlockPos placed=canopy.placementAnchor(clicked,facing);require(placed.offset(facing.rotateY(),canopy.width/2).up(canopy.height/2).equals(clicked),"large canopy centered placement");}
        }
        System.out.println("[vandorlabs][reprolab] large-canopy-runtime PASS shapes=42 poses=168 palette=57 resize-protection NBT configured-items");
    }
    private static double area(List<CanopyMesh.Face> faces){double result=0;for(CanopyMesh.Face face:faces)for(int i=1;i<face.vertices.length-1;i++)result+=face.vertices[i].subtract(face.vertices[0]).crossProduct(face.vertices[i+1].subtract(face.vertices[0])).lengthVector()/2;return result;}
    static void materialUvs(){
        List<List<CanopyMesh.Face>> models=new ArrayList<>();
        for(int width:new int[]{1,2,3})for(int angle=0;angle<3;angle++)models.add(OpeningCanopyGeometry.faces(width,angle,false));
        for(int width:new int[]{3,5}){LargeCanopySettings settings=new LargeCanopySettings();settings.depth=80;models.add(LargeCanopyGeometry.faces(width,settings,true));}
        for(List<CanopyMesh.Face> source:models)for(boolean tiled:new boolean[]{false,true}){
            List<CanopyMesh.Face> material=CanopyFinishes.faces(source,0,1,8,tiled),draw=CanopyMaterialUv.drawable(material);
            require(Math.abs(area(draw)-area(source))<1e-7*Math.max(1,area(source)),"tiling preserves complete panel area model="+models.indexOf(source)+" tiled="+tiled+" before="+area(source)+" after="+area(draw));
            for(CanopyMesh.Face face:draw)for(double[] uv:face.uv)if(face.baseMaterial.equals("panel"))require(uv[0]>=-1e-8 && uv[0]<=1+1e-8 && uv[1]>=-1e-8 && uv[1]<=1+1e-8,"material UV stays within atlas sprite");
            if(!tiled)for(CanopyMesh.Face face:material)if(face.baseMaterial.equals("panel"))for(int axis=0;axis<2;axis++){
                double min=1,max=0;for(double[] uv:face.uv){min=Math.min(min,uv[axis]);max=Math.max(max,uv[axis]);}require(min<1e-8 && max>1-1e-8,"Fit shows complete artwork on each panel");
            }
        }
        CanopyMesh.Face panel=new CanopyMesh.Face();panel.vertices=new Vec3d[]{new Vec3d(0,0,0),new Vec3d(3,0,0),new Vec3d(3,2,0),new Vec3d(0,2,0)};panel.uv=new double[4][2];panel.material=panel.baseMaterial="glass";panel.group="shell";
        List<CanopyMesh.Face> tiles=CanopyMaterialUv.drawable(CanopyFinishes.faces(Collections.singletonList(panel),0,1,8,true));
        require(tiles.size()==6,"Tile repeats a 3x2 panel exactly six times at block scale");
        System.out.println("[vandorlabs][reprolab] canopy-material-uv PASS Fit-full-artwork Tile-block-scale atlas-clipping area-preserved profiles=3 widths=1,2,3 front-frames=3,5");
    }
    public static void icons(Minecraft mc){
        for(net.minecraft.block.Block block:ModBlocks.BLOCKS)if(block instanceof BlockCanopy && LargeCanopySettings.supports(((BlockCanopy)block).kind))for(int depth:new int[]{0,16,80})for(int scale:new int[]{10,71,100})for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<3;tint++){
            LargeCanopySettings settings=new LargeCanopySettings();settings.depth=depth;settings.scale=scale;settings.shade=shade;settings.tint=tint;checkIcon(mc,block,settings);
        }
        net.minecraft.block.Block canopy=net.minecraft.block.Block.getBlockFromName("vandorlabs:canopy");
        for(int angle=0;angle<3;angle++)for(int motion=0;motion<2;motion++){
            for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<3;tint++){
                LargeCanopySettings settings=new LargeCanopySettings();settings.angle=angle;settings.motion=motion;settings.shade=shade;settings.tint=tint;checkIcon(mc,canopy,settings);
            }
            LargeCanopySettings settings=new LargeCanopySettings();settings.angle=angle;settings.motion=motion;settings.material=9;checkIcon(mc,canopy,settings);settings.tileMaterial=true;checkIcon(mc,canopy,settings);
        }
        System.out.println("[vandorlabs][reprolab] large-canopy-icons PASS large-variants=1026 opening-variants=348 projected-padding");
    }
    private static void checkIcon(Minecraft mc,net.minecraft.block.Block block,LargeCanopySettings settings){
        ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();settings.write(tag);stack.setTagCompound(tag);IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);ItemTransformVec3f gui=model.getItemCameraTransforms().gui;
        for(BakedQuad quad:model.getQuads(null,null,0)){int[] data=quad.getVertexData();int stride=data.length/4;for(int i=0;i<4;i++){Vec3d point=ControlItemPadding.project(new Vec3d(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2])),gui).addVector(gui.translation.x,gui.translation.y,0);require(Math.abs(point.x)<.49 && Math.abs(point.y)<.49,"icon lacks padding "+settings.key());}}
    }
}

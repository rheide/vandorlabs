package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.copier.*;
import com.vandorlabs.surface.SquareLayout;
import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.TurntableGeometry;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import java.io.File;

/** Real rectangular copies use the asynchronous journal and retain exact chest contents. */
final class CopyRectangleLiveChecks {
    private static final BlockPos BASE=new BlockPos(660,130,240);
    private static int fixture,stage,ticks;
    private static CopyPlan expected;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("copy-rectangle face="+fixture+" stage="+stage+": "+why);}
    private static EnumFacing normal(){return EnumFacing.values()[fixture];}
    private static EnumFacing front(){return normal().getAxis()==EnumFacing.Axis.X?EnumFacing.EAST:EnumFacing.NORTH;}
    private static CopyControllerTile owner(World world){return (CopyControllerTile)world.getTileEntity(BASE);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(fixture==6)return true;mc.displayGuiScreen(null);
        mc.getIntegratedServer().callFromMainThread(()->{step(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));return null;}).get();
        if(stage==3){CopyControllerTile client=owner(mc.world);if(client==null||client.output==null)return false;TileEntityTurntableSurface tile=(TileEntityTurntableSurface)mc.world.getTileEntity(client.output.root);if(tile==null)return false;SquareLayout layout=tile.appearanceLayout();require(client.output.width==2&&client.output.height==3,"controller retains rectangle dimensions");for(BlockPos member:client.output.cells){TileEntityTurntableSurface seen=(TileEntityTurntableSurface)mc.world.getTileEntity(member);if(seen==null||!seen.appearanceReady())return false;require(seen.appearanceLayout().cells.size()==1,"non-square Fit Aspect renders each block separately");}aspectMesh(mc,tile);java.awt.image.BufferedImage image=SurfaceClientChecks.damagedFace(mc,tile,normal());javax.imageio.ImageIO.write(image,"png",new File(output,"shot_copy_rectangle_"+normal().getName()+".png"));stage=4;ticks=0;}
        return false;
    }
    private static void aspectMesh(Minecraft mc,TileEntityTurntableSurface tile){
        net.minecraft.client.renderer.BufferBuilder buffer=new net.minecraft.client.renderer.BufferBuilder(4096);net.minecraft.client.renderer.vertex.VertexFormat format=BlockSurfaceFormat.get();buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,format);new TETurntableSurface().renderTileEntityFast(tile,0,0,0,0,-1,1,buffer);buffer.finishDrawing();
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(com.vandorlabs.turntable.TurntableTextures.texture(tile.selectedTexture(),false));java.nio.ByteBuffer data=buffer.getByteBuffer();int stride=format.getNextOffset(),uv=format.getUvOffsetById(0);double area=0;
        for(int q=0;q<buffer.getVertexCount();q+=4){boolean art=true;for(int n=0;n<4;n++){double u=data.getFloat((q+n)*stride+uv),v=data.getFloat((q+n)*stride+uv+4);art&=u>=sprite.getMinU()-1e-7&&u<=sprite.getMaxU()+1e-7&&v>=sprite.getMinV()-1e-7&&v<=sprite.getMaxV()+1e-7;}if(!art)continue;double[] a=new double[3],b=new double[3];for(int axis=0;axis<3;axis++){a[axis]=data.getFloat((q+1)*stride+axis*4)-data.getFloat(q*stride+axis*4);b[axis]=data.getFloat((q+2)*stride+axis*4)-data.getFloat(q*stride+axis*4);}double x=a[1]*b[2]-a[2]*b[1],y=a[2]*b[0]-a[0]*b[2],z=a[0]*b[1]-a[1]*b[0];area+=Math.sqrt(x*x+y*y+z*z)/2;}
        require(Math.abs(area-1)<1e-5,"individual Aspect artwork covers whole native block without exposed margins");System.out.println("[vandorlabs][reprolab] surface-aspect PASS normal="+normal()+" non-square ownership retained individual faces full artwork area no invented border");
    }
    private static void step(WorldServer world,EntityPlayerMP player)throws Exception{
        require(++ticks<400,"bounded asynchronous copy");
        if(stage==0){
            if(fixture==0)CopyPlanRuntimeChecks.centering();
            for(BlockPos p:BlockPos.getAllInBox(BASE.add(-7,-5,-7),BASE.add(7,7,7))){world.getChunkFromBlockCoords(p);world.setBlockToAir(p);}
            // Clearing the preceding fixture drops chest contents; these are test debris, not output occupants.
            for(net.minecraft.entity.Entity entity:world.getEntitiesWithinAABBExcludingEntity(player,new net.minecraft.util.math.AxisAlignedBB(BASE.getX()-8,BASE.getY()-8,BASE.getZ()-8,BASE.getX()+9,BASE.getY()+9,BASE.getZ()+9)))if(!(entity instanceof net.minecraft.entity.player.EntityPlayer))entity.setDead();
            player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.setPositionAndUpdate(BASE.getX()+6,BASE.getY()+6,BASE.getZ()+6);
            world.setBlockState(BASE,Block.getBlockFromName("vandorlabs:copy_controller").getDefaultState().withProperty(BlockVandorDirectional.FACING,front()),3);CopyControllerTile copier=owner(world);copier.height=1;copier.actor=player.getUniqueID();WorldActionAuthorization.authorize(copier,player);build(world,-1,2,3);build(world,1,2,3);stage=1;ticks=0;return;
        }
        CopyControllerTile copier=owner(world);
        if(stage==1){if(ticks<20)return;copier.refreshPads(player);require(copier.input.width==2&&copier.input.height==3&&copier.output.width==2&&copier.output.height==3,"matching rectangles claimed");
            BlockPos source=copier.input.cell(0,0,1);world.setBlockState(source,Blocks.CHEST.getDefaultState(),2);TileEntityChest chest=(TileEntityChest)world.getTileEntity(source);chest.setCustomName("Rectangular source");ItemStack named=new ItemStack(Items.DIAMOND,11);named.setStackDisplayName("Exact rectangular cargo");chest.setInventorySlotContents(7,named);world.setBlockState(copier.input.cell(1,2,1),Blocks.GOLD_BLOCK.getDefaultState(),2);
            expected=CopyPlan.prepare(copier,player);require(expected.original.matchesExact(world,expected.sourceOrigin),"source preflight retains chest data");copier.requestCopy(player);require(copier.busy(),"real async copy queued");stage=2;ticks=0;return;
        }
        if(stage==2){if(copier.busy()||ticks<5)return;require(copier.error.isEmpty(),"copy failed: "+copier.error);require(expected.original.matchesExact(world,expected.sourceOrigin)&&expected.result.matchesExact(world,expected.targetOrigin),"exact source and copied rectangle including chest contents");CopyControllerTile reload=new CopyControllerTile();reload.readFromNBT(copier.writeToNBT(new NBTTagCompound()));require(reload.input.width==2&&reload.input.height==3&&reload.output.width==2&&reload.output.height==3,"rectangular controller NBT round trip");stage=3;ticks=0;return;}
        if(stage==4){
            for(BlockPos p:copier.output.cells)world.setBlockToAir(p);build(world,1,3,2);boolean denied=false;try{copier.refreshPads(player);}catch(IllegalArgumentException invalid){denied=invalid.getMessage().contains("matching rectangle dimensions");}require(denied&&copier.input==null&&copier.output==null,"transposed rectangle refuses and releases stale claims");require(expected.original.matchesExact(world,expected.sourceOrigin),"mismatch preserves original inventory and blocks");
            copier.useChip=true;EnumFacing right=front().rotateYCCW();for(BlockPos at:BlockPos.getAllInBox(BASE.add(-7,-5,-7),BASE.add(7,7,7)))if((at.getX()-BASE.getX())*right.getFrontOffsetX()+(at.getZ()-BASE.getZ())*right.getFrontOffsetZ()<0&&world.getBlockState(at).getBlock() instanceof BlockTurntableSurface)world.setBlockToAir(at);
            copier.refreshPads(player);require(copier.input==null&&copier.output!=null&&copier.output.width==3&&copier.output.height==2,"Chip mode claims output alone with no input pad");
            System.out.println("[vandorlabs][reprolab] copy-rectangle PASS normal="+normal()+" 2x3 exact-inventory async-journal reload transpose-refusal");fixture++;stage=ticks=0;
        }
    }
    private static void build(WorldServer world,int sign,int width,int height){
        TurntableGeometry.Face basis=TurntableGeometry.Face.valueOf(normal().name());EnumFacing right=front().rotateYCCW(),u=EnumFacing.getFacingFromVector(basis.ux,basis.uy,basis.uz),v=EnumFacing.getFacingFromVector(basis.vx,basis.vy,basis.vz);if(dot(u,right)*sign<0)u=u.getOpposite();if(dot(v,right)*sign<0)v=v.getOpposite();BlockPos seed=BASE.offset(right,sign);
        for(int x=0;x<width;x++)for(int y=0;y<height;y++)world.setBlockState(seed.offset(u,x).offset(v,y),Block.getBlockFromName("vandorlabs:turntable_surface").getDefaultState().withProperty(BlockTurntableSurface.FACING,normal()),3);
    }
    private static int dot(EnumFacing a,EnumFacing b){return a.getFrontOffsetX()*b.getFrontOffsetX()+a.getFrontOffsetY()*b.getFrontOffsetY()+a.getFrontOffsetZ()*b.getFrontOffsetZ();}
    private CopyRectangleLiveChecks(){}
}

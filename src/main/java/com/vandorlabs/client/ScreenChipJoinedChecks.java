package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.items.ModItems;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;

/** Render a real joined rectangle with one attached reader and immutable preview reuse. */
final class ScreenChipJoinedChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("joined chip: "+why);}
    static void run(Minecraft mc,File output)throws Exception{
        Block renamed=Block.getBlockFromName(com.vandorlabs.persistence.RenamedBlocks.NEW);require(renamed!=null&&renamed.getLocalizedName().equals("Programmable Diagonal Half Screen"),"renamed screen registry and display name");for(int meta=0;meta<16;meta++){int old=CustomBlockMaterials.identifier(com.vandorlabs.persistence.RenamedBlocks.OLD,meta),current=CustomBlockMaterials.identifier(renamed,meta);require(CustomBlockTextures.sprite(old,false)==CustomBlockTextures.sprite(current,false),"renamed sampled artwork metadata "+meta);}

        for(int speed=0;speed<3;speed++){Object owner=new Object();require(VehicleShapeRenderer.angleAt(owner,100,speed)==0,"rotation origin");double angle=VehicleShapeRenderer.angleAt(owner,110,speed);require(Math.abs(angle-6*(1<<speed))<1e-9,"slow/normal/fast rotation rates");require(VehicleShapeRenderer.angleAt(owner,110,(speed+1)%3)==angle,"speed change/paused frame jumped phase");}
        BlockPos base=new BlockPos(120,120,120);Block block=Block.getBlockFromName("vandorlabs:programmable_viewscreen");
        for(BlockPos at:BlockPos.getAllInBox(base.add(-1,-1,-1),base.add(4,4,1)))mc.world.setBlockToAir(at);
        for(int y=0;y<2;y++)for(int x=0;x<2;x++){BlockPos at=base.add(x,y,0);mc.world.setBlockState(at,block.getDefaultState(),3);((TileEntityAnimatedScreenSelector)mc.world.getTileEntity(at)).setSurfaceTexture(0,TileEntityAnimatedScreenSelector.CHIP_SURFACE);}
        BlockPos mount=base.east().up().north();mc.world.setBlockState(mount,Block.getBlockFromName("vandorlabs:data_chip_slot").getDefaultState().withProperty(BlockTokenSlot.FACING,EnumFacing.NORTH),3);
        TileEntityTokenSlot reader=(TileEntityTokenSlot)mc.world.getTileEntity(mount);ItemStack chip=new ItemStack(ModItems.DATA_CHIP);VehicleChip.blank(chip);
        VehicleShape shape=VehicleShape.of(VehicleStructure.display(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,net.minecraft.init.Blocks.IRON_BLOCK.getDefaultState(),null),new VehicleStructure.Cell(BlockPos.ORIGIN.east(),net.minecraft.init.Blocks.IRON_BLOCK.getDefaultState(),null),new VehicleStructure.Cell(BlockPos.ORIGIN.up(),net.minecraft.init.Blocks.GLASS.getDefaultState(),null))));shape.write(chip.getTagCompound());chip.getTagCompound().setString("VehicleName","Joined vehicle preview");reader.setInventorySlotContents(0,chip);
        TileEntityAnimatedScreenSelector first=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(base);ScreenChipGroups.Group group=ScreenChipGroups.group(first);require(group.columns==2&&group.rows==2&&group.members.size()==4&&group.reader==reader,"2x2 shares a reader on a non-owner cell");
        for(TileEntityAnimatedScreenSelector tile:group.members)require(ScreenChipSupport.reader(tile)==reader&&ScreenChipSupport.available(tile,0),"every panel sees attached chip");
        int builds=ScreenChipGroups.builds;for(int i=0;i<100;i++)for(TileEntityAnimatedScreenSelector tile:group.members)require(ScreenChipGroups.group(tile)==group,"cached group identity");require(ScreenChipGroups.builds==builds,"per-frame group discovery");
        int before=ScreenChipRenderer.submissions;render(mc,group,new File(output,"screen-chip-joined-vehicle.png"));require(ScreenChipRenderer.submissions-before==1,"2x2 must submit one stretched canvas");
        int meshes=VehicleShapeRenderer.meshBuilds;long start=System.nanoTime();for(int i=0;i<100;i++)render(mc,group,null);long micros=(System.nanoTime()-start)/1000;require(VehicleShapeRenderer.meshBuilds==meshes,"rotation rebuilt immutable voxel geometry");
        int[] previewColors={0xeeeeee,0xffd34d,0xff6868,0x8aff75,0xa78bff,0xff9b45,0xa5e0e6};
        for(int company=0;company<7;company++){
            ItemStack colored=reader.getStackInSlot(0);colored.setItemDamage(company);require(VehicleShapeRenderer.chipColor(colored)==previewColors[company],"preview company palette "+company);
            File picture=new File(output,"screen-chip-vehicle-color-"+company+".png");render(mc,ScreenChipGroups.group(first),picture);java.awt.image.BufferedImage pixels=ImageIO.read(picture);int matches=0;
            for(int y=130;y<350;y++)for(int x=130;x<350;x++){int rgb=pixels.getRGB(x,y);boolean close=true;for(int shift:new int[]{0,8,16})if(Math.abs(((rgb>>shift)&255)-((previewColors[company]>>shift)&255))>8)close=false;if(close)matches++;}require(matches>10,"company preview color missing from rendered voxels "+company+" pixels="+matches);
        }
        int coloredBuilds=VehicleShapeRenderer.meshBuilds;for(int company=0;company<7;company++){reader.getStackInSlot(0).setItemDamage(company);render(mc,ScreenChipGroups.group(first),null);}require(VehicleShapeRenderer.meshBuilds==coloredBuilds,"company switching rebuilt cached geometry");
        for(int company=1;company<7;company++){ItemStack master=new ItemStack(ModItems.DATA_CHIP,1,company);require(ProgrammerJournalRenderer.companyChoice(master)>=0,"Corp logo for company "+company);reader.setInventorySlotContents(0,master);render(mc,ScreenChipGroups.group(first),new File(output,"screen-chip-company-"+company+".png"));}
        reader.takeForDrop();require(ScreenChipSupport.stack(first).isEmpty(),"removal updates joined source");mc.world.setBlockToAir(mount);require(!ScreenChipSupport.available(first,0),"removed mount invalidates shared lookup");
        orientations(mc,base.up(4),output);
        removedScreens(mc,base.up(4));
        System.out.println("[vandorlabs][reprolab] screen-chip-joined PASS 2x2 one-canvas four-panel-shared-reader cached-group six-Corp-logos removed-reader mesh-builds-for-100-rotations=0 render-us="+micros);
    }
    private static void orientations(Minecraft mc,BlockPos base,File output)throws Exception{
        Block block=Block.getBlockFromName("vandorlabs:programmable_viewscreen");
        for(EnumFacing facing:EnumFacing.values()){
            for(BlockPos at:BlockPos.getAllInBox(base.add(-3,-3,-3),base.add(3,3,3)))mc.world.setBlockToAir(at);
            mc.world.setBlockState(base,block.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing),3);TileEntityAnimatedScreenSelector first=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(base);DisplayJoinGeometry.Basis basis=DisplayJoinGeometry.basis(first,mc.world.getBlockState(base));
            for(int y=0;y<2;y++)for(int x=0;x<2;x++){BlockPos at=base.add(basis.steps[0].getX()*x+basis.steps[2].getX()*y,basis.steps[0].getY()*x+basis.steps[2].getY()*y,basis.steps[0].getZ()*x+basis.steps[2].getZ()*y);mc.world.setBlockState(at,block.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing),3);((TileEntityAnimatedScreenSelector)mc.world.getTileEntity(at)).setSurfaceTexture(0,TileEntityAnimatedScreenSelector.CHIP_SURFACE);}
            BlockPos mount=base.offset(facing);mc.world.setBlockState(mount,Block.getBlockFromName("vandorlabs:data_chip_slot").getDefaultState().withProperty(BlockTokenSlot.FACING,facing),3);((TileEntityTokenSlot)mc.world.getTileEntity(mount)).setInventorySlotContents(0,new ItemStack(ModItems.DATA_CHIP,1,1));ScreenChipGroups.Group group=ScreenChipGroups.group(first);require(group.columns==2&&group.rows==2,"joined orientation "+facing);render(mc,group,new File(output,"screen-chip-company-facing-"+facing.getName()+".png"));
        }
    }
    private static void removedScreens(Minecraft mc,BlockPos base){
        Block block=Block.getBlockFromName("vandorlabs:programmable_viewscreen");int cases=0;
        for(EnumFacing facing:EnumFacing.values())for(boolean removeOwner:new boolean[]{true,false}){
            for(BlockPos at:BlockPos.getAllInBox(base.add(-3,-3,-3),base.add(3,3,3)))mc.world.setBlockToAir(at);
            mc.world.setBlockState(base,block.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing),3);TileEntityAnimatedScreenSelector first=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(base);DisplayJoinGeometry.Basis basis=DisplayJoinGeometry.basis(first,mc.world.getBlockState(base));
            for(int y=0;y<2;y++)for(int x=0;x<2;x++){BlockPos at=base.add(basis.steps[0].getX()*x+basis.steps[2].getX()*y,basis.steps[0].getY()*x+basis.steps[2].getY()*y,basis.steps[0].getZ()*x+basis.steps[2].getZ()*y);mc.world.setBlockState(at,block.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing),3);((TileEntityAnimatedScreenSelector)mc.world.getTileEntity(at)).setSurfaceTexture(0,TileEntityAnimatedScreenSelector.CHIP_SURFACE);}
            ScreenChipGroups.Group before=ScreenChipGroups.group(first);TileEntityAnimatedScreenSelector removed=removeOwner?before.owner:before.members.stream().filter(tile->tile!=before.owner).findFirst().get();removed.getRenderBoundingBox();BlockPos at=removed.getPos();mc.world.setBlockToAir(at);
            // OptiFine can ask for the old tile's bounds while publishing a block removal.
            removed.getRenderBoundingBox();require(ScreenChipGroups.group(removed)==null,"removed tile retained a Chip group");require(DisplayEditGroups.members(removed).size()==1,"removed tile entered shared display discovery");
            for(TileEntityAnimatedScreenSelector survivor:before.members)if(survivor!=removed){ScreenChipGroups.Group after=ScreenChipGroups.group(survivor);require(after!=null&&!after.members.contains(removed),"survivors retained removed owner/member");survivor.getRenderBoundingBox();}
            mc.world.setBlockState(at,block.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing),3);TileEntityAnimatedScreenSelector replacement=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(at);replacement.setSurfaceTexture(0,TileEntityAnimatedScreenSelector.CHIP_SURFACE);require(ScreenChipGroups.group(replacement)!=null&&ScreenChipGroups.group(removed)==null,"old tile stole replacement group");removed.getRenderBoundingBox();cases++;
        }
        System.out.println("[vandorlabs][reprolab] screen-chip-removal PASS cases="+cases+" all-six-faces old-tile-bounds owner/member removal replacement-identity survivors");
    }
    private static void render(Minecraft mc,ScreenChipGroups.Group group,File output)throws Exception{
        Framebuffer frame=new Framebuffer(480,480,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-1.25,1.25,-1.25,1.25,-10,10);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(.08F,.12F,.16F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableCull();EnumFacing face=group.owner.getWorld().getBlockState(group.owner.getPos()).getValue(BlockAnimatedScreenSelector.FACING);switch(face){case NORTH:GlStateManager.rotate(180,0,1,0);break;case EAST:GlStateManager.rotate(-90,0,1,0);break;case WEST:GlStateManager.rotate(90,0,1,0);break;case UP:GlStateManager.rotate(90,1,0,0);break;case DOWN:GlStateManager.rotate(-90,1,0,0);break;default:break;}GlStateManager.translate(group.owner.getPos().getX()-(group.bounds.minX+group.bounds.maxX)/2,group.owner.getPos().getY()-(group.bounds.minY+group.bounds.maxY)/2,group.owner.getPos().getZ()-(group.bounds.minZ+group.bounds.maxZ)/2);
            for(TileEntityAnimatedScreenSelector tile:group.members){BlockPos delta=tile.getPos().subtract(group.owner.getPos());new TEAnimatedScreenSelector().render(tile,delta.getX(),delta.getY(),delta.getZ(),0,-1,1);}
            if(output!=null){java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(480,480,frame);if(output.getName().contains("company")){int ink=0;for(int y=110;y<370;y++)for(int x=110;x<370;x++)if((image.getRGB(x,y)&0xffffff)!=0)ink++;require(ink>200,"company logo rendered blank: "+output.getName());if(output.getName().contains("facing")){int framePixels=0;for(int y=100;y<370;y++)for(int x=230;x<250;x++){int color=image.getRGB(x,y);if((color&255)-((color>>16)&255)>25)framePixels++;}for(int y=230;y<250;y++)for(int x=100;x<370;x++){int color=image.getRGB(x,y);if((color&255)-((color>>16)&255)>25)framePixels++;}require(framePixels<10,"internal frame crosses joined Chip canvas: "+output.getName());}if(output.getName().contains("facing"))for(int y:new int[]{144,336})for(int x:new int[]{144,336})require((image.getRGB(x,y)&0xffffff)!=0,"joined canvas missed panel centre: "+output.getName()+" "+x+","+y);}ImageIO.write(image,"png",output);}
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private ScreenChipJoinedChecks(){}
}

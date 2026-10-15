package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Focused real-world transactions followed by actual textured TESR pose captures. */
final class MoatVisualChecks {
    private static final int SIZE=320;
    private static boolean done;
    private static void require(boolean yes,String message){if(!yes)throw new IllegalStateException("Moat visual: "+message);}
    static void begin(Minecraft mc)throws Exception{
        done=false;
        mc.getIntegratedServer().addScheduledTask(()->{MoatRuntimeChecks.run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));RampGlassRuntimeChecks.run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));StairRampRuntimeChecks.run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));}).get();
    }
    private static VehicleWorld fixture(Minecraft mc,EnumFacing face,double pose,int start,int end){
        return fixture(mc,face,pose,start,end,null);
    }
    private static VehicleWorld fixture(Minecraft mc,EnumFacing face,double pose,int start,int end,IBlockState sourceState){
        return fixture(mc,face,pose,start,end,sourceState,6,true);
    }
    private static VehicleWorld fixture(Minecraft mc,EnumFacing face,double pose,int start,int end,IBlockState sourceState,int mode,boolean tiledSides){
        return fixture(mc,face,pose,start,end,sourceState,mode,tiledSides,8);
    }
    private static VehicleWorld fixture(Minecraft mc,EnumFacing face,double pose,int start,int end,IBlockState sourceState,int mode,boolean tiledSides,int pixels){
        List<VehicleStructure.Cell> originals=new ArrayList<>();BlockPos root=new BlockPos(5,2,5);
        originals.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        TileEntityRampController controller=new TileEntityRampController();controller.setPos(root);controller.moat=mode==6;controller.elevator=mode==2||mode==3;controller.extendSegments=mode==1||mode==3;
        NBTTagCompound data=controller.writeToNBT(new NBTTagCompound());data.setInteger("TreadPixels",pixels);data.setInteger("StartOffsetHalfSteps",start);data.setInteger("EndOffsetHalfSteps",end);
        originals.add(new VehicleStructure.Cell(root,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,face),data));
        for(int row=1;row<=3;row++)for(int lane=0;lane<2;lane++){
            BlockPos p=(mode==6?root.offset(face).up(row-1):root.offset(face,row)).offset(face.rotateY(),lane);TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setPos(p);tile.setHousingTexture(CustomBlockMaterials.choice(new net.minecraft.item.ItemStack(Blocks.GOLD_BLOCK)));tile.setSlabTileSides(tiledSides);
            if(sourceState!=null&&sourceState.getBlock() instanceof BlockProgrammableStairs)tile.setSideTexture(CustomBlockMaterials.choice(new net.minecraft.item.ItemStack(Blocks.BRICK_BLOCK)));
            originals.add(new VehicleStructure.Cell(p,sourceState==null?ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState():sourceState,sourceState==null||sourceState.getBlock() instanceof BlockProgrammableStairs?tile.writeToNBT(new NBTTagCompound()):null));
        }
        VehicleWorld staging=new VehicleWorld(mc.world,new VehicleStructure(originals,BlockPos.ORIGIN,EnumFacing.NORTH,1));
        TileEntityRampController ramp=(TileEntityRampController)staging.getTileEntity(root);
        // Endpoint cells cover the full arc only after joining the endpoint journals.
        java.util.Map<BlockPos,VehicleStructure.Cell> cells=new java.util.LinkedHashMap<>();
        for(VehicleStructure.Cell cell:ramp.vehicleEndpoint(false))cells.put(cell.pos,cell);
        for(VehicleStructure.Cell cell:ramp.vehicleEndpoint(true))cells.put(cell.pos,cell);
        // Include every cell through the middle pose; generated tile origin/material data is identical.
        VehicleStructure.Cell template=cells.values().iterator().next();
        NBTTagCompound partData=template.tileData();
        for(BlockPos p:BlockPos.getAllInBox(root.add(-4,-2,-4),root.add(4,7,4))){
            if(mode==6){boolean reserved=false;for(AxisAlignedBB swept:ramp.vehicleMoatSweep())if(swept.grow(-1e-8).intersects(new AxisAlignedBB(p))){reserved=true;break;}if(!reserved)continue;}
            NBTTagCompound copy=partData.copy();copy.setInteger("x",p.getX());copy.setInteger("y",p.getY());copy.setInteger("z",p.getZ());
            copy.removeTag(com.vandorlabs.ramp.RampCopyData.ORIGIN);
            int min=Integer.MAX_VALUE;for(int row=1;row<=3;row++){BlockPos source=mode==6?root.offset(face):root.offset(face,row);min=Math.min(min,source.getX()*face.getFrontOffsetX()+source.getZ()*face.getFrontOffsetZ());}
            copy.setInteger("Row",p.getX()*face.getFrontOffsetX()+p.getZ()*face.getFrontOffsetZ()-min);
            cells.put(p,new VehicleStructure.Cell(p,ModBlocks.CONTROLLED_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,face),copy));
        }
        cells.put(BlockPos.ORIGIN,new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        VehicleWorld view=new VehicleWorld(mc.world,new VehicleStructure(new ArrayList<>(cells.values()),BlockPos.ORIGIN,EnumFacing.NORTH,1));
        int sourceMin=Integer.MAX_VALUE;for(int row=1;row<=3;row++){BlockPos p=mode==6?root.offset(face):root.offset(face,row);sourceMin=Math.min(sourceMin,p.getX()*face.getFrontOffsetX()+p.getZ()*face.getFrontOffsetZ());}
        AxisAlignedBB total=null;
        for(net.minecraft.tileentity.TileEntity raw:view.tiles.values())if(raw instanceof TileEntityControlledRamp){TileEntityControlledRamp part=(TileEntityControlledRamp)raw;part.row=part.getPos().getX()*face.getFrontOffsetX()+part.getPos().getZ()*face.getFrontOffsetZ()-sourceMin;part.escalator=mode==4?1:mode==5?2:0;part.origins.clear();part.sourceTileTags.clear();for(VehicleStructure.Cell source:originals)if(!source.pos.equals(root)&&!source.pos.equals(BlockPos.ORIGIN)){part.origins.add(source.pos);NBTTagCompound saved=com.vandorlabs.compat.RampStairs.supported(source.state)?com.vandorlabs.compat.RampStairs.capture(staging,source.pos):source.tileData();if(saved!=null)part.sourceTileTags.put(source.pos,saved);}part.move(true,pose,view.getTotalWorldTime(),60,false);
            require(part.moat==(mode==6)&&part.sourceY==2&&part.getPos().getX()*face.getFrontOffsetX()+part.getPos().getZ()*face.getFrontOffsetZ()-part.row==sourceMin,"synthetic parts retain one source hinge and height");
            if(mode==6)for(AxisAlignedBB b:part.boxes(0)){AxisAlignedBB absolute=b.offset(part.getPos());total=total==null?absolute:total.union(absolute);}
        }
        if(mode==6&&start==0&&end==0&&(pose==0||pose==1)&&!com.vandorlabs.compat.RampStairs.supported(sourceState==null?ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState():sourceState)){
            require(total!=null,"Moat endpoint has geometry");double along=face.getAxis()==EnumFacing.Axis.X?total.maxX-total.minX:total.maxZ-total.minZ,width=face.getAxis()==EnumFacing.Axis.X?total.maxZ-total.minZ:total.maxX-total.minX;
            require(Math.abs(width-2)<1e-6&&Math.abs(along-(pose==0?1:3))<1e-6&&Math.abs(total.maxY-total.minY-(pose==0?3:1))<1e-6,"Moat endpoint is upright2x3 wall or horizontal2x3 deck: "+total);
        }
        view.origin=mc.player.getPosition();return view;
    }
    private static BufferedImage draw(Minecraft mc,VehicleWorld view,EnumFacing face){
        return draw(mc,view,face,true);
    }
    private static boolean gold(int color){int r=color>>16&255,g=color>>8&255,b=color&255;return r>70&&g>55&&r>b*1.3&&g>b*1.2;}
    private static int checker(int x,int y){return ((x/16+y/16)&1)==0?0x1E4682:0x3773B9;}
    private static void background(){
        GlStateManager.loadIdentity();GlStateManager.disableTexture2D();GlStateManager.disableLighting();GlStateManager.disableDepth();GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
        for(int y=0;y<20;y++)for(int x=0;x<20;x++){int c=checker(x*16,y*16);double a=-3.5+x*.35,d=3.5-y*.35;int r=c>>16&255,g=c>>8&255,blue=c&255;
            b.pos(a,d-.35,-20).color(r,g,blue,255).endVertex();b.pos(a+.35,d-.35,-20).color(r,g,blue,255).endVertex();b.pos(a+.35,d,-20).color(r,g,blue,255).endVertex();b.pos(a,d,-20).color(r,g,blue,255).endVertex();}
        Tessellator.getInstance().draw();GlStateManager.enableDepth();GlStateManager.enableTexture2D();
    }
    private static BufferedImage draw(Minecraft mc,VehicleWorld view,EnumFacing face,boolean expectGold){
        Framebuffer target=new Framebuffer(SIZE,SIZE,true);TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;
        net.minecraft.world.World previous=dispatcher.world;double oldX=dispatcher.entityX,oldY=dispatcher.entityY,oldZ=dispatcher.entityZ;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-3.5,3.5,-3.5,3.5,-30,30);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{
            target.bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clearColor(.04F,.05F,.07F,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GlStateManager.enableDepth();GlStateManager.enableTexture2D();
            background();GlStateManager.loadIdentity();GlStateManager.rotate(22,1,0,0);GlStateManager.rotate(28-face.getHorizontalAngle(),0,1,0);GlStateManager.translate(-5.5,-4,-5.5);
            dispatcher.world=view;dispatcher.entityX=12;dispatcher.entityY=10;dispatcher.entityZ=12;
            int nonempty=0;for(net.minecraft.tileentity.TileEntity raw:view.tiles.values())if(raw instanceof TileEntityControlledRamp){TileEntityControlledRamp part=(TileEntityControlledRamp)raw;if(part.geometry(0).isEmpty())continue;nonempty++;TEControlledRamp renderer=(TEControlledRamp)(Object)dispatcher.getRenderer(part);require(renderer!=null,"TESR registered");renderer.render(part,part.getPos().getX(),part.getPos().getY(),part.getPos().getZ(),0,-1,1);}
            require(nonempty>0,"fixture has visible geometry");GL11.glFinish();BufferedImage result=ScreenShotHelper.createScreenshot(SIZE,SIZE,target);int gold=0;
            for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(gold(result.getRGB(x,y)))gold++;if(expectGold)require(gold>100,"actual saved gold surface rendered pixels="+gold);return result;
        }finally{dispatcher.world=previous;dispatcher.entityX=oldX;dispatcher.entityY=oldY;dispatcher.entityZ=oldZ;GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(done)return true;if(!CanopyChunkOrderChecks.tick(mc))return false;if(!SharedCanopyRetractionChecks.tick(mc))return false;if(!VehicleMoatTransferRuntimeChecks.tick(mc))return false;glCheckpoint("matrix entry / preexisting");GuiOptionCycleRuntimeChecks.checkMoatMode(mc);BufferedImage montage=new BufferedImage(SIZE*5,(SIZE+24)*4,BufferedImage.TYPE_INT_RGB);Graphics2D g=montage.createGraphics();g.setColor(Color.WHITE);int row=0;
        for(EnumFacing face:EnumFacing.HORIZONTALS){BufferedImage closed=null;for(int col=0;col<5;col++){double pose=col<3?col*.5:col==3?0:1;int start=col==3?-1:0,end=col==4?3:0;BufferedImage image=draw(mc,fixture(mc,face,pose,start,end),face);if(col==0)closed=image;else{int changed=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(closed.getRGB(x,y)!=image.getRGB(x,y))changed++;require(changed>100,"pose or signed offset visibly changed: "+face+" column="+col);}g.drawImage(image,col*SIZE,row*(SIZE+24)+24,null);g.drawString(face+" "+new String[]{"closed","midpoint","open","stored -0.5","deployed +1.5"}[col],col*SIZE+6,row*(SIZE+24)+17);}row++;}
        g.dispose();javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_moat_cardinal_endpoints.png"));glCheckpoint("gold endpoint matrix complete");
        Exception[] failure={null};collect("vertical tread sections",()->verticalSections(mc,output),failure);collect("ordinary Ramp endpoint parity",()->endpointParity(mc,output),failure);collect("glass transmission",()->glass(mc,output),failure);collect("captured glass order",()->capturedGlass(mc,output),failure);collect("stair shapes",()->stairs(mc,output),failure);collect("stair side mapping",()->stairFinishes(mc,output),failure);if(failure[0]!=null)throw failure[0];
        done=true;System.out.println("[vandorlabs][reprolab] moat-client PASS textured-TESR cardinal closed-mid-open signed-offsets source-material screenshots");return true;
    }
    private interface VisualCheck{void run()throws Exception;}
    private static void verticalSections(Minecraft mc,File output)throws Exception{
        BufferedImage montage=new BufferedImage(SIZE*4,(SIZE+24)*2,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=montage.createGraphics();graphics.setColor(Color.WHITE);int row=0;
        for(double pose:new double[]{.35,.65}){
            BufferedImage coarse=draw(mc,fixture(mc,EnumFacing.SOUTH,pose,0,0,null,6,true,16),EnumFacing.SOUTH);int col=0;
            for(int pixels:new int[]{1,4,8,16}){
                VehicleWorld view=fixture(mc,EnumFacing.SOUTH,pose,0,0,null,6,true,pixels);double maximumHeight=0;int pieces=0;
                for(net.minecraft.tileentity.TileEntity raw:view.tiles.values())if(raw instanceof TileEntityControlledRamp)for(com.vandorlabs.ramp.RampGeometry.Box box:((TileEntityControlledRamp)raw).geometry(0)){maximumHeight=Math.max(maximumHeight,box.maxY-box.minY);pieces++;}
                require(pieces>0,"mid-motion selected tread sections have actual geometry");if(pixels<16)require(maximumHeight<.99,"fine mid-motion TESR consumes thin section bounds instead of whole blocks");
                BufferedImage image=draw(mc,view,EnumFacing.SOUTH);int changed=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(image.getRGB(x,y)!=coarse.getRGB(x,y))changed++;
                if(pixels<16)require(changed>100,"selected vertical tread size visibly differs from whole moving blocks: "+pixels+" changed="+changed);
                graphics.drawImage(image,col*SIZE,row*(SIZE+24)+24,null);graphics.drawString("Tread "+pixels+"px / motion "+pose,col*SIZE+6,row*(SIZE+24)+17);col++;
            }
            row++;
        }
        graphics.dispose();javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_moat_vertical_tread_sections_midmotion.png"));System.out.println("[vandorlabs][reprolab] moat-tread-render PASS actual-TESR vertical-sections 1-4-8-16px two-midmotion-poses visible-chunk-differences");
    }
    private static void endpointParity(Minecraft mc,File output)throws Exception{
        BufferedImage montage=new BufferedImage(SIZE*2,(SIZE+24)*12,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=montage.createGraphics();graphics.setColor(Color.WHITE);int row=0,maxDifferent=0;
        for(EnumFacing face:EnumFacing.HORIZONTALS)for(int material=0;material<3;material++){
            IBlockState state=material==0?null:material==1?Blocks.STONE_SLAB.getStateFromMeta(0):Blocks.OAK_STAIRS.getDefaultState().withProperty(net.minecraft.block.BlockStairs.FACING,face);
            BufferedImage moat=draw(mc,fixture(mc,face,1,0,-2,state,6,true),face,material==0),ramp=draw(mc,fixture(mc,face,1,0,-2,state,0,true),face,material==0);int different=0;
            for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){int a=moat.getRGB(x,y),b=ramp.getRGB(x,y);if(Math.abs((a>>16&255)-(b>>16&255))>8||Math.abs((a>>8&255)-(b>>8&255))>8||Math.abs((a&255)-(b&255))>8)different++;}
            maxDifferent=Math.max(maxDifferent,different);graphics.drawImage(moat,0,row*(SIZE+24)+24,null);graphics.drawImage(ramp,SIZE,row*(SIZE+24)+24,null);graphics.drawString(face+" material="+material+" Moat End -1",6,row*(SIZE+24)+17);graphics.drawString("Ordinary Ramp End -1",SIZE+6,row*(SIZE+24)+17);row++;
        }
        graphics.dispose();javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_moat_ordinary_ramp_end_minus_one.png"));require(maxDifferent<=32,"deployed Moat End -1 visually matches ordinary Ramp; differing pixels="+maxDifferent);
        System.out.println("[vandorlabs][reprolab] moat-endpoint-render PASS actual-TESR ordinary-Ramp End-minus-one cardinal full-slab-stair pixel-parity maxDifferent="+maxDifferent);
    }
    private static void collect(String label,VisualCheck check,Exception[] failure){glCheckpoint("before "+label+" / preexisting");try{check.run();}catch(Exception error){System.err.println("[vandorlabs][reprolab] moat-visual-subcheck FAIL "+label);error.printStackTrace();if(failure[0]==null)failure[0]=error;else failure[0].addSuppressed(error);}finally{glCheckpoint("after "+label);}}
    private static void glCheckpoint(String label){int count=0,code;while((code=GL11.glGetError())!=GL11.GL_NO_ERROR&&count++<16){String meaning=code==1280?"invalid-enum":code==1281?"invalid-value":code==1282?"invalid-operation":code==1283?"stack-overflow":code==1284?"stack-underflow":code==1285?"out-of-memory":code==1286?"invalid-framebuffer":"unknown";System.out.println("[vandorlabs][reprolab] moat-gl-check context="+label+" code="+code+" hex=0x"+Integer.toHexString(code)+" "+meaning);}if(count==0)System.out.println("[vandorlabs][reprolab] moat-gl-check context="+label+" code=0");}
    private static void glass(Minecraft mc,File output)throws Exception{
        BufferedImage montage=new BufferedImage(SIZE*3,(SIZE+24)*2,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=montage.createGraphics();graphics.setColor(Color.WHITE);
        for(int row=0;row<2;row++){
            double pose=row;EnumFacing face=EnumFacing.SOUTH;BufferedImage opaque=draw(mc,fixture(mc,face,pose,0,0),face),plain=draw(mc,fixture(mc,face,pose,0,0,Blocks.GLASS.getDefaultState()),face,false),stained=draw(mc,fixture(mc,face,pose,0,0,Blocks.STAINED_GLASS.getStateFromMeta(14)),face,false);
            int covered=0,transmitted=0,tinted=0,dark=0,light=0;long darkGreen=0,lightGreen=0;
            for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(gold(opaque.getRGB(x,y))){covered++;int expected=checker(x,y),p=plain.getRGB(x,y)&0xFFFFFF,c=stained.getRGB(x,y),r=c>>16&255,green=c>>8&255;
                if(p==expected)transmitted++;
                if(r>green*1.15&&r>(expected>>16&255)+5){tinted++;if(((x/16+y/16)&1)==0){dark++;darkGreen+=green;}else{light++;lightGreen+=green;}}
            }
            require(covered>200&&transmitted>covered/5,"plain glass transmits checker within real bridge silhouette pose="+pose+" transmitted="+transmitted+" covered="+covered);
            require(tinted>covered/5&&dark>10&&light>10&&Math.abs(darkGreen/(double)dark-lightGreen/(double)light)>3,"stained glass preserves tint and checker variation pose="+pose+" tinted="+tinted+" covered="+covered);
            for(int col=0;col<3;col++){graphics.drawImage(new BufferedImage[]{opaque,plain,stained}[col],col*SIZE,row*(SIZE+24)+24,null);graphics.drawString((row==0?"Stored wall / ":"Deployed bridge / ")+new String[]{"saved Gold","plain Glass","red Stained Glass"}[col],col*SIZE+6,row*(SIZE+24)+17);}
        }
        graphics.dispose();javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_moat_glass_transmission.png"));System.out.println("[vandorlabs][reprolab] moat-glass-render PASS plain-transmission stained-tint checker-variation closed-open");
    }
    private static EntityGroundVehicle capturedFixture(Minecraft mc,boolean glazing,boolean reverse){
        VehicleWorld source=fixture(mc,EnumFacing.SOUTH,0,0,0,Blocks.STAINED_GLASS.getStateFromMeta(14));List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell cell:source.structure.cells){net.minecraft.tileentity.TileEntity raw=source.getTileEntity(cell.pos);if(raw instanceof TileEntityControlledRamp){if(!glazing||((TileEntityControlledRamp)raw).geometry(0).isEmpty())continue;cells.add(new VehicleStructure.Cell(cell.pos,cell.state,raw.writeToNBT(new NBTTagCompound())));}else cells.add(cell);}
        for(int x=4;x<=5;x++)for(int y=3;y<=5;y++){BlockPos p=new BlockPos(x,y,5);TileEntityAnimatedScreenSelector wall=new TileEntityAnimatedScreenSelector();wall.setPos(p);wall.setHousingTexture(CustomBlockMaterials.choice(new net.minecraft.item.ItemStack(Blocks.LAPIS_BLOCK)));cells.add(new VehicleStructure.Cell(p,ModBlocks.PROGRAMMABLE_WALL.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.SOUTH),wall.writeToNBT(new NBTTagCompound())));}
        if(reverse)java.util.Collections.reverse(cells);
        EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.install(new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));craft.setPosition(0,0,0);return craft;
    }
    private static void stairs(Minecraft mc,File output)throws Exception{
        BufferedImage montage=new BufferedImage(SIZE*3,(SIZE+24)*4,BufferedImage.TYPE_INT_RGB);Graphics2D g=montage.createGraphics();g.setColor(Color.WHITE);int row=0;
        BufferedImage[] cubes=new BufferedImage[3];for(int pose=0;pose<3;pose++)cubes[pose]=draw(mc,fixture(mc,EnumFacing.SOUTH,pose*.5,0,0,Blocks.PLANKS.getDefaultState()),EnumFacing.SOUTH,false);
        for(net.minecraft.block.BlockStairs.EnumHalf half:net.minecraft.block.BlockStairs.EnumHalf.values())for(EnumFacing sourceFacing:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.EAST}){
            int maxChanged=0;for(int pose=0;pose<3;pose++){
                IBlockState stair=Blocks.OAK_STAIRS.getDefaultState().withProperty(net.minecraft.block.BlockStairs.FACING,sourceFacing).withProperty(net.minecraft.block.BlockStairs.HALF,half);BufferedImage image=draw(mc,fixture(mc,EnumFacing.SOUTH,pose*.5,0,0,stair),EnumFacing.SOUTH,false);int changed=0;
                for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(image.getRGB(x,y)!=cubes[pose].getRGB(x,y))changed++;maxChanged=Math.max(maxChanged,changed);g.drawImage(image,pose*SIZE,row*(SIZE+24)+24,null);g.drawString("Oak stair "+sourceFacing+" "+half+" / "+new String[]{"stored","midpoint","deployed"}[pose],pose*SIZE+6,row*(SIZE+24)+17);
            }require(maxChanged>100,"actual stair TESR retains voids rather than rendering full cubes: "+sourceFacing+" "+half);row++;
        }
        g.dispose();javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_moat_stair_shapes.png"));System.out.println("[vandorlabs][reprolab] ramp-stairs-render PASS real-TESR source-orientations upside-down closed-mid-open missing-quarter screenshots");
    }
    private static void stairFinishes(Minecraft mc,File output)throws Exception{
        BufferedImage montage=new BufferedImage(SIZE*2,(SIZE+24)*7,BufferedImage.TYPE_INT_RGB);Graphics2D g=montage.createGraphics();g.setColor(Color.WHITE);String[] modes={"Ramp","Filled ramp","Lift","Extend","Escalator up","Escalator down","Moat"};
        for(int mode=0;mode<7;mode++){
            BufferedImage[] images=new BufferedImage[2];IBlockState material=ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(net.minecraft.block.BlockStairs.FACING,EnumFacing.NORTH);
            double[] volumes=new double[2];for(int option=0;option<2;option++){VehicleWorld view=fixture(mc,EnumFacing.SOUTH,1,0,6,material,mode,option==1);for(net.minecraft.tileentity.TileEntity raw:view.tiles.values())if(raw instanceof TileEntityControlledRamp)for(com.vandorlabs.ramp.RampGeometry.Box b:((TileEntityControlledRamp)raw).geometry(0))volumes[option]+=(b.maxX-b.minX)*(b.maxY-b.minY)*(b.maxZ-b.minZ);images[option]=draw(mc,view,EnumFacing.SOUTH,false);g.drawImage(images[option],option*SIZE,mode*(SIZE+24)+24,null);g.drawString(modes[mode]+" / sides "+(option==0?"stretch":"tile"),option*SIZE+6,mode*(SIZE+24)+17);}
            require(Math.abs(volumes[0]-volumes[1])<1e-8,"side texture flag leaves stair solids unchanged mode="+mode);int changed=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(images[0].getRGB(x,y)!=images[1].getRGB(x,y))changed++;require(changed>50,"actual stair side finish respects stretch/tile mode="+modes[mode]+" changed="+changed);
        }
        g.dispose();javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_ramp_stair_side_mapping.png"));System.out.println("[vandorlabs][reprolab] ramp-stair-sides-render PASS seven-modes programmable-brick-side stretch-vs-tile same-solid screenshots");
    }
    private static BufferedImage drawCaptured(Minecraft mc,EntityGroundVehicle craft,File output,String label)throws Exception{
        Framebuffer target=new Framebuffer(SIZE,SIZE,true);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-3.5,3.5,-3.5,3.5,-30,30);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{
            net.minecraft.client.renderer.entity.Render<EntityGroundVehicle> renderer=mc.getRenderManager().getEntityRenderObject(craft);require(renderer instanceof RenderGroundVehicle,"actual vehicle renderer registered");
            for(int frame=0;frame<3;frame++){
                glCheckpoint("captured "+label+" frame="+frame+" before / preexisting");
                target.bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clearColor(.04F,.05F,.07F,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);background();GlStateManager.loadIdentity();GlStateManager.rotate(22,1,0,0);GlStateManager.rotate(28,0,1,0);GlStateManager.translate(-5.5,-4,-5.5);
                RenderGroundVehicle.beginFrame();renderer.doRender(craft,0,0,0,0,1);
                glCheckpoint("captured "+label+" frame="+frame+" after renderer");
            }
            GL11.glFinish();BufferedImage result=ScreenShotHelper.createScreenshot(SIZE,SIZE,target);javax.imageio.ImageIO.write(result,"png",new File(output,"shot_moat_captured_"+label+".png"));int draws=RenderGroundVehicle.tileDraws(),opaque=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if((result.getRGB(x,y)&0xFFFFFF)!=checker(x,y))opaque++;
            require(draws>=6&&opaque>100,"actual vehicle opaque TESR backdrop reaches framebuffer label="+label+" tileDraws="+draws+" covered="+opaque);System.out.println("[vandorlabs][reprolab] moat-captured-fixture label="+label+" actual-TESR-draws="+draws+" covered="+opaque);return result;
        }finally{glCheckpoint("captured "+label+" before cleanup");GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);craft.setDead();RenderGroundVehicle.cleanup(mc.world);glCheckpoint("captured "+label+" after cleanup");}
    }
    private static void capturedGlass(Minecraft mc,File output)throws Exception{
        BufferedImage backdrop=drawCaptured(mc,capturedFixture(mc,false,false),output,"backdrop"),normal=drawCaptured(mc,capturedFixture(mc,true,false),output,"glass"),reverse=drawCaptured(mc,capturedFixture(mc,true,true),output,"glass_reversed");int glassPixels=0,orderPixels=0;
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){if(backdrop.getRGB(x,y)!=normal.getRGB(x,y))glassPixels++;if(normal.getRGB(x,y)!=reverse.getRGB(x,y))orderPixels++;}
        require(glassPixels>100,"captured stained wall visibly blends over opaque TESR backdrop pixels="+glassPixels);require(orderPixels<=12,"captured glass is independent of tile insertion order pixels="+orderPixels);
        BufferedImage montage=new BufferedImage(SIZE*3,SIZE+24,BufferedImage.TYPE_INT_RGB);Graphics2D g=montage.createGraphics();g.setColor(Color.WHITE);BufferedImage[] images={backdrop,normal,reverse};String[] labels={"Actual vehicle opaque TESR backdrop","Captured Moat glass over backdrop","Reversed snapshot insertion order"};for(int i=0;i<3;i++){g.drawImage(images[i],i*SIZE,24,null);g.drawString(labels[i],i*SIZE+6,17);}g.dispose();javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_moat_captured_glass_order.png"));System.out.println("[vandorlabs][reprolab] moat-captured-glass-render PASS actual-RenderGroundVehicle opaque-TESR-backdrop insertion-order transmission");
    }
    private MoatVisualChecks(){}
}

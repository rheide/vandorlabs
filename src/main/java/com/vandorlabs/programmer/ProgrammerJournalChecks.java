package com.vandorlabs.programmer;

import com.vandorlabs.items.ModItems;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Actual authored-screen activation, synchronized pages and read-only retained output. */
public final class ProgrammerJournalChecks {
    private static int checks;
    private static void require(boolean test,String reason){checks++;if(!test)throw new IllegalStateException("Programmer physical Journal: "+reason);}
    private static RayTraceResult click(WorldServer world,ProgrammerTile tile,EntityPlayer actor,int direction,boolean blocked,boolean distant){
        double u=direction<0?40:279,v=124;Vec3d target=ProgrammerJournalSurface.world(tile,u,v);ProgrammerTransform.Affine a=ProgrammerGeometry.transform(tile.getProgrammerFacing());Vec3d n=ProgrammerJournalSurface.NORMAL,normal=new Vec3d(a.xx*n.x+a.xz*n.z,n.y,a.zx*n.x+a.zz*n.z),eye=target.add(normal.scale(distant?20:2)),look=target.subtract(eye);
        actor.setPosition(eye.x,eye.y-actor.getEyeHeight(),eye.z);actor.rotationYaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z));actor.rotationPitch=(float)-Math.toDegrees(Math.atan2(look.y,Math.sqrt(look.x*look.x+look.z*look.z)));actor.rotationYawHead=actor.rotationYaw;
        RayTraceResult hit=world.rayTraceBlocks(eye,target.subtract(normal.scale(.01)),false,false,true);require(hit!=null&&hit.subHit==3,"actual native screen is first visible ray hit "+tile.getProgrammerFacing());
        double[] uv=ProgrammerJournalSurface.hit(tile.getProgrammerOrigin(),tile.getProgrammerFacing(),eye,target.subtract(normal.scale(.01)));require(uv!=null&&Math.abs(uv[0]-u)<1e-4&&Math.abs(uv[1]-v)<1e-4&&ProgrammerJournalSurface.action(uv[0],uv[1])==direction,"native surface and button pixels share exact hit region");
        BlockPos obstruction=new BlockPos(target.add(normal.scale(1)));if(blocked){require(world.isAirBlock(obstruction),"occlusion fixture does not overwrite programmer");world.setBlockState(obstruction,net.minecraft.init.Blocks.STONE.getDefaultState(),2);}
        net.minecraft.inventory.Container before=actor.openContainer;
        try{Block block=world.getBlockState(hit.getBlockPos()).getBlock();require(block.onBlockActivated(world,hit.getBlockPos(),world.getBlockState(hit.getBlockPos()),actor,EnumHand.MAIN_HAND,hit.sideHit,0,0,0),"physical screen click consumed");require(actor.openContainer==before,"page click never opens configuration dialog");}
        finally{if(blocked)world.setBlockToAir(obstruction);}
        return hit;
    }
    public static void run(WorldServer world,BlockPos root){
        checks=0;Block block=Block.getBlockFromName("vandorlabs:data_chip_programmer");net.minecraftforge.common.util.FakePlayer actor=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);double x=actor.posX,y=actor.posY,z=actor.posZ;float yaw=actor.rotationYaw,pitch=actor.rotationPitch,head=actor.rotationYawHead;ItemStack held=actor.getHeldItemMainhand();boolean creative=actor.capabilities.isCreativeMode;
        try{actor.capabilities.isCreativeMode=true;actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            for(EnumFacing facing:EnumFacing.HORIZONTALS){
                for(int part=0;part<4;part++)world.setBlockState(ProgrammerBlock.cell(root,facing,part),block.getDefaultState().withProperty(ProgrammerBlock.FACING,facing).withProperty(ProgrammerBlock.PART,part),2);
                ProgrammerTile tile=ProgrammerBlock.at(world,root);
                try{
                    String[] entries={String.join("",java.util.Collections.nCopies(512,"W")),"Second entry",String.join("\n",java.util.Collections.nCopies(25,"Line"))};ItemStack journal=new ItemStack(ModItems.DATA_CHIP,1,1);ChipJournal.write(journal,true,"World Journal",entries);tile.setInventorySlotContents(1,journal);
                    require(tile.journalDisplay()!=null&&tile.journalDisplay().source.equals("Input")&&tile.journalDisplay().pages.size()>=6,"all authored long/newline pages produce reachable screen views");
                    StringBuilder text=new StringBuilder();for(ProgrammerJournalView.Page page:tile.journalDisplay().pages){require(page.lines.size()<=10,"bounded rows");for(String line:page.lines){require(line.length()<=50,"bounded columns");text.append(line);}}require(text.toString().equals(String.join("",entries).replace("\n","")),"screen pagination retains every character");
                    NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());click(world,tile,actor,1,false,false);require(tile.journalDisplayPage()==1&&saved.equals(tile.writeToNBT(new NBTTagCompound())),"input page navigation does not edit persistent draft or chip");
                    ProgrammerTile client=new ProgrammerTile();client.readFromNBT(tile.getUpdateTag());require(client.journalDisplayPage()==1,"page synchronizes through real tile update tag");ProgrammerTile reloaded=new ProgrammerTile();reloaded.readFromNBT(saved);require(reloaded.journalDisplayPage()==0,"saved reload resets viewing page");
                    click(world,tile,actor,-1,false,false);require(tile.journalDisplayPage()==0,"Previous works on actual screen");click(world,tile,actor,1,true,false);require(tile.journalDisplayPage()==0,"occluded forged screen activation cannot navigate");click(world,tile,actor,1,false,true);require(tile.journalDisplayPage()==0,"out-of-reach activation cannot navigate");
                    click(world,tile,actor,1,false,false);tile.configure(true,"Changed title",entries);require(tile.journalDisplayPage()==0,"content change resets page");click(world,tile,actor,1,false,false);
                    require(tile.program()&&tile.getStackInSlot(1).isEmpty()&&tile.journalDisplay().source.equals("Output")&&tile.journalDisplayPage()==0,"Program moves Journal to read-only physical Output display and resets source page");
                    ItemStack output=tile.getStackInSlot(2).copy();saved=tile.writeToNBT(new NBTTagCompound());click(world,tile,actor,1,false,false);require(tile.journalDisplayPage()==1&&ItemStack.areItemStacksEqual(output,tile.getStackInSlot(2))&&saved.equals(tile.writeToNBT(new NBTTagCompound())),"Output paging cannot mutate any result or inventory NBT");
                    tile.setInventorySlotContents(1,new ItemStack(ModItems.DATA_CHIP,1,1));tile.configure(true,"New input draft",new String[]{"Draft text"});require(tile.journalDisplay().source.equals("Input")&&tile.journalDisplay().title.equals("New input draft")&&tile.journalDisplayPage()==0,"new input Journal draft takes display precedence");
                    tile.clear();require(tile.journalDisplay()==null,"no Journal leaves original black screen");
                }finally{for(int part=0;part<4;part++)world.removeTileEntity(ProgrammerBlock.cell(root,facing,part));for(int part=3;part>=0;part--)world.setBlockToAir(ProgrammerBlock.cell(root,facing,part));}
            }
        }finally{actor.setHeldItem(EnumHand.MAIN_HAND,held);actor.capabilities.isCreativeMode=creative;actor.setPosition(x,y,z);actor.rotationYaw=yaw;actor.rotationPitch=pitch;actor.rotationYawHead=head;}
        System.out.println("[vandorlabs][reprolab] programmer-world-journal PASS checks="+checks+" all4 native screen planes real Previous/Next block activations bounded complete text update sync source/content reset read-onlyOutput occlusion/reach no-dialog");
    }
    private ProgrammerJournalChecks(){}
}

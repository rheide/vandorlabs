package com.vandorlabs.neoforge;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/** Checks the actual native housing states, harvest tags, loot and canonical matter recipe. */
public final class BasicHousingRuntimeChecks {
    public static final BlockPos MATTER_POS=new BlockPos(2,80,0);
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Modern runtime check: housing "+message);}
    public static void run(ServerLevel level) {
        level.setBlock(MATTER_POS,VandorLabsModern.MATTER_BLOCK.get().defaultBlockState(),3);
        for(var pos:List.of(new BlockPos(0,80,0),MATTER_POS)) {
            var state=level.getBlockState(pos);
            require(state.getDestroySpeed(level,pos)==5&&state.getBlock().getExplosionResistance()==6,"hardness/blast reference mismatch");
            require(state.getSoundType()==SoundType.METAL&&state.getMapColor(level,pos)==MapColor.METAL&&state.getLightBlock(level,pos)==15,"sound/map color/opacity mismatch");
            require(state.requiresCorrectToolForDrops()&&!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state)&&new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state),"stone harvest boundary mismatch");
        }
        var matter=level.getBlockState(MATTER_POS);
        var drops=Block.getDrops(matter,level,MATTER_POS,null,null,new ItemStack(Items.STONE_PICKAXE));
        require(drops.size()==1&&drops.getFirst().is(VandorLabsModern.MATTER_BLOCK_ITEM.get())&&drops.getFirst().getComponentsPatch().isEmpty(),"plain matter loot mismatch");
        var recipe=level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("vandorlabs","programmable_matter_block")).orElseThrow().value();
        @SuppressWarnings("unchecked") var crafting=(net.minecraft.world.item.crafting.Recipe<CraftingInput>)recipe;
        var input=CraftingInput.of(3,3,java.util.Collections.nCopies(9,new ItemStack(VandorLabsModern.MATTER_INGOT.get())));
        require(crafting.matches(input,level)&&crafting.assemble(input,level.registryAccess()).is(VandorLabsModern.MATTER_BLOCK_ITEM.get()),"nine-ingot recipe mismatch");
        System.out.println("[vandorlabs][modern] basic-housing-server PASS canonical hardness/blast/mapcolor/opacity, stone harvest boundary, plain matter loot and actual nine-ingot recipe");
    }
    public static void read(ServerLevel level){require(level.getBlockState(MATTER_POS).is(VandorLabsModern.MATTER_BLOCK.get()),"matter disk state missing");System.out.println("[vandorlabs][modern] basic-housing-read PASS plain matter block disk state");}
    private BasicHousingRuntimeChecks(){}
}

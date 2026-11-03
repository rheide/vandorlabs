import com.vandorlabs.vehicle.VehicleWeight;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Bootstrap;
import java.lang.reflect.Method;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Generate from the estimator using Java 8 and the project development runtime classpath.
 * Pass the Markdown output path. No world, renderer or inventories are needed. */
public final class VanillaVehicleWeights {
    private static Method estimate;
    private static BigDecimal weight(IBlockState state)throws Exception{return BigDecimal.valueOf((Double)estimate.invoke(null,state)).setScale(8,RoundingMode.HALF_UP).stripTrailingZeros();}
    private static String number(BigDecimal value){return value.toPlainString();}
    private static String variants(List<IBlockState> states,List<IProperty<?>> relevant){
        SortedSet<String> labels=new TreeSet<>();
        for(IBlockState state:states){List<String> values=new ArrayList<>();for(IProperty<?> property:relevant)values.add(property.getName()+"="+state.getValue(property));labels.add(String.join(", ",values));}
        return "`"+String.join("`, `",labels)+"`";
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Markdown output path required");
        Bootstrap.register();estimate=VehicleWeight.class.getDeclaredMethod("blockUnits",IBlockState.class);estimate.setAccessible(true);
        List<Block> blocks=new ArrayList<>();for(Block block:Block.REGISTRY)if(block.getRegistryName()!=null&&block.getRegistryName().getResourceDomain().equals("minecraft"))blocks.add(block);
        blocks.sort(Comparator.comparing(block->block.getRegistryName().toString()));
        StringBuilder out=new StringBuilder("# Vanilla vehicle block weights\n\nVanilla Minecraft **1.12.2**. ");
        out.append("Current estimate rules: **version ").append(VehicleWeight.VERSION).append("**. One full stone block is **1 weight unit**. Values are game estimates per occupied block cell, including each half of a multi-block structure separately. They are not kilograms and do not affect movement yet.\n\n");
        out.append("The table is generated from the implemented estimator. The default-state weight is shown first; variants with a different estimate are listed separately. Facing, colour, opening and other states are omitted when they do not change weight. Values are rounded to eight decimal places. Registry entries include technical and unassemblable blocks: this is a weight reference, not a list of supported vehicle blocks.\n\n");
        out.append("Full-block examples: wood 0.25, ordinary stone/glass 1, iron 3 and gold 7.5. Slabs normally use half the material weight; stairs use three quarters. Other recognised thin shapes have smaller factors. Blocks without a recognised material or shape use the material fallback and full-block factor. Selected artwork and custom names do not change the result.\n\n");
        out.append("This table covers block bodies. Inventory stacks and tank contents are added separately when scanning: for example, 1,000 mB of ordinary water in a tank adds 0.4 units. See [vehicle weight and cargo rules](pilot-seat-taxi.md#estimated-vehicle-weight). Source: [VehicleWeight.java](../src/main/java/com/vandorlabs/vehicle/VehicleWeight.java).\n\n");
        out.append("| Vanilla block ID | Default weight / cell | Variants with different weights |\n| --- | ---: | --- |\n");
        int statesChecked=0,variable=0;
        for(Block block:blocks){
            List<IBlockState> states=block.getBlockState().getValidStates();Map<BigDecimal,List<IBlockState>> groups=new TreeMap<>();Map<IBlockState,BigDecimal> weights=new IdentityHashMap<>();
            for(IBlockState state:states){BigDecimal value=weight(state);weights.put(state,value);groups.computeIfAbsent(value,key->new ArrayList<>()).add(state);statesChecked++;}
            BigDecimal base=weight(block.getDefaultState());List<String> differences=new ArrayList<>();
            if(groups.size()>1){variable++;List<IProperty<?>> relevant=new ArrayList<>();
                for(IProperty<?> property:block.getDefaultState().getPropertyKeys()){
                    Map<String,BigDecimal> seen=new HashMap<>();boolean changes=false;
                    for(IBlockState state:states){StringBuilder key=new StringBuilder();for(IProperty<?> other:state.getPropertyKeys())if(other!=property)key.append(other.getName()).append('=').append(state.getValue(other)).append(';');BigDecimal previous=seen.putIfAbsent(key.toString(),weights.get(state));if(previous!=null&&previous.compareTo(weights.get(state))!=0){changes=true;break;}}
                    if(changes)relevant.add(property);
                }
                relevant.sort(Comparator.comparing(IProperty::getName));
                for(Map.Entry<BigDecimal,List<IBlockState>> group:groups.entrySet())if(group.getKey().compareTo(base)!=0)differences.add(number(group.getKey())+": "+variants(group.getValue(),relevant));
            }
            out.append("| `").append(block.getRegistryName()).append("` | ").append(number(base)).append(" | ").append(differences.isEmpty()?"\u2014":String.join("; ",differences)).append(" |\n");
        }
        out.append("\nCoverage: **").append(blocks.size()).append(" vanilla block IDs**, **").append(statesChecked).append(" block states** checked; **").append(variable).append(" IDs** have weight-changing variants. Regenerate after changing estimate rules.\n");
        Files.write(Paths.get(args[0]),out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Vanilla weight reference generated: blocks="+blocks.size()+" states="+statesChecked+" variable="+variable);
    }
}

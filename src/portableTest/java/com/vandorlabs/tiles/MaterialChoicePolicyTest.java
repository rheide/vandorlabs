package com.vandorlabs.tiles;

/** Saved IDs survive absent external artwork without admitting unassigned namespace gaps. */
public final class MaterialChoicePolicyTest {
    private MaterialChoicePolicyTest() { }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        require(MaterialChoicePolicy.CUSTOM_BASE == 536870912, "Saved custom namespace moved");
        require(MaterialChoicePolicy.FILESYSTEM_BASE == 1073741824, "Saved filesystem namespace moved");
        for (int count : new int[]{0, 1, 100, 4096}) {
            require(!MaterialChoicePolicy.valid(Integer.MIN_VALUE, count), "Signed overflow accepted");
            require(!MaterialChoicePolicy.valid(-1, count), "Inherit sentinel accepted as artwork");
            require(!MaterialChoicePolicy.valid(count, count), "Past-end catalog index accepted");
            require(!MaterialChoicePolicy.valid(536870911, count), "Unassigned namespace accepted");
            if (count > 0) {
                require(MaterialChoicePolicy.valid(0, count), "First builtin rejected");
                require(MaterialChoicePolicy.valid(count - 1, count), "Last builtin rejected");
            }
            for (int external : new int[]{536870912, 1073741823, 1073741824, Integer.MAX_VALUE}) {
                require(MaterialChoicePolicy.valid(external, count), "Unavailable external artwork rejected");
            }
        }
        require(!MaterialChoicePolicy.isCustom(-1), "Negative ID classified as custom");
        require(!MaterialChoicePolicy.isCustom(536870911), "Gap classified as custom");
        require(MaterialChoicePolicy.isCustom(536870912), "First custom ID rejected");
        require(MaterialChoicePolicy.isCustom(1073741823), "Last custom ID rejected");
        require(!MaterialChoicePolicy.isCustom(1073741824), "Filesystem ID classified as copied block");
        require(!MaterialChoicePolicy.isCustom(Integer.MAX_VALUE), "Filesystem upper edge classified as copied block");
        System.out.println("Material choice namespace contracts PASS");
    }
}

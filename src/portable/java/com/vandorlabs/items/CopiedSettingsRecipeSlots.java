package com.vandorlabs.items;

import java.util.function.IntUnaryOperator;

/** Shapeless copied-settings recipe: exactly one tool and one eligible input. */
public final class CopiedSettingsRecipeSlots {
    public static final int EMPTY=0, TOOL=1, INPUT=2, OTHER=3;
    public static final class Selection {
        public final int tool,input;
        private Selection(int tool,int input){this.tool=tool;this.input=input;}
    }
    public static Selection find(int size,IntUnaryOperator kind){
        int tool=-1,input=-1;
        for(int i=0;i<size;i++){
            switch(kind.applyAsInt(i)){
                case EMPTY:break;
                case TOOL:if(tool>=0)return null;tool=i;break;
                case INPUT:if(input>=0)return null;input=i;break;
                default:return null;
            }
        }
        return tool<0||input<0?null:new Selection(tool,input);
    }
    private CopiedSettingsRecipeSlots(){}
}

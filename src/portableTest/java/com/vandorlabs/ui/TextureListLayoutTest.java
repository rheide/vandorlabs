package com.vandorlabs.ui;

import java.util.*;

/** Independent picker row goldens: hierarchy order differs intentionally from filtered search order. */
public final class TextureListLayoutTest {
    private TextureListLayoutTest() { }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void equal(Object actual,Object expected,String message){require(actual.equals(expected),message+": "+actual);}
    public static void main(String[] args){
        List<String> categories=TextureListLayout.categories(Arrays.asList("Bridge/Sub","Frame","Bridge"));
        equal(categories,Arrays.asList("Bridge","Bridge/Sub","Frame"),"Completed category hierarchy");
        equal(TextureListLayout.categories(Arrays.asList("alpha","Alpha","alpha/Sub")),Arrays.asList("Alpha","alpha","alpha/Sub"),"Category case tie uses natural order");
        require(TextureListLayout.compareOptions("Alpha",7,"alpha",2)>0&&TextureListLayout.compareOptions("alpha",2,"Zulu",1)<0,"Option label case tie uses saved row ID");
        Map<String,List<Integer>> groups=new HashMap<String,List<Integer>>();groups.put("Bridge",Arrays.asList(2,7));groups.put("Bridge/Sub",Arrays.asList(11,23));groups.put("Frame",Arrays.asList(-1));
        Set<String> expanded=new HashSet<String>();TextureListLayout.expandAncestors(expanded,"Bridge/Sub");TextureListLayout.expandAncestors(expanded,"Frame");
        List<Integer> rows=new ArrayList<Integer>();TextureListLayout.appendCategories(rows,categories,groups,expanded,id->id);
        equal(rows,Arrays.asList(-200000,-200001,11,23,2,7,-200002,-1),"Child rows precede sorted direct options; negative artwork remains an option");
        expanded.remove("Bridge");rows.clear();TextureListLayout.appendCategories(rows,categories,groups,expanded,id->id);
        equal(rows,Arrays.asList(-200000,-200002,-1),"Collapsed parent hides expanded descendants");
        require(!TextureListLayout.parentsExpanded(expanded,"Bridge/Sub"),"Collapsed ancestor predicate");
        TextureListLayout.expandAncestors(expanded,"Bridge/Sub");require(TextureListLayout.parentsExpanded(expanded,"Bridge/Sub"),"Revealed ancestors");
        String query=TextureListLayout.normalizeQuery("  BRIDGE  ");equal(query,"bridge","Query normalization");
        Map<Integer,String[]> data=new LinkedHashMap<Integer,String[]>();data.put(11,new String[]{"blue plate","Bridge/Sub","tex/zeta"});data.put(-1,new String[]{"Original artwork","Frame","frame/original"});data.put(7,new String[]{"Alpha","Bridge","tex/red"});data.put(2,new String[]{"alpha","Bridge","tex/blue"});data.put(23,new String[]{"Pilot","Bridge/Sub","tex/green"});
        List<Integer> matches=new ArrayList<Integer>();for(Map.Entry<Integer,String[]> item:data.entrySet()){String[] e=item.getValue();if(TextureListLayout.matches(query,e[0],e[1],e[2]))matches.add(item.getKey());}
        equal(matches,Arrays.asList(11,7,2,23),"Search retains input order rather than grouped display order");
        require(TextureListLayout.matches("plate zeta","blue plate","Bridge/Sub","tex/zeta")&&!TextureListLayout.matches("plate red","blue plate","Bridge/Sub","tex/zeta"),"Search tokens AND across presentation fields");
        require(!TextureListLayout.matches("changed","Blue","Bridge","old")&&TextureListLayout.matches("changed","Blue","Bridge","changed"),"Current thumbnail/search text must remain live");
        require(TextureListLayout.pinCategory(0,2,true)&&!TextureListLayout.pinCategory(0,1,true)&&!TextureListLayout.pinCategory(1,2,true)&&!TextureListLayout.pinCategory(0,2,false),"Pinned first-row conditions");
        require(TextureListLayout.categoryRow(2)==-200002&&TextureListLayout.categoryIndex(-200002)==2,"Header row encoding");
        require(TextureListLayout.centeredScroll(8,4,10)==6&&TextureListLayout.centeredScroll(-1,4,10)==0&&TextureListLayout.centeredScroll(20,4,10)==10,"Center clamping");
        require(TextureListLayout.needsReveal(4,4,3)&&!TextureListLayout.needsReveal(5,4,3)&&TextureListLayout.needsReveal(7,4,3)&&!TextureListLayout.needsReveal(-1,4,3),"Pinned heading consumes the first visible selection row");
        require(TextureListLayout.indent("a/b/c")==16&&TextureListLayout.indent("a/b/c/d/e/f/g/h")==48&&TextureListLayout.categoryLabel("a/b/c").equals("c"),"Category presentation helper");
        require(!TextureListLayout.matches("vand","Blue plate","Materials","vandorlabs:blocks/blue_plate"),"Common mod namespace must not match ordinary searches");
        require(TextureListLayout.matches("vand","Vandor panel","Materials","vandorlabs:blocks/panel"),"Visible names remain searchable");
        require(TextureListLayout.matches("vand","Logo","Materials","vandorlabs:blocks/vandor_logo"),"Authored texture paths remain searchable");
        require(TextureListLayout.matches("vandorlabs:","Blue plate","Materials","vandorlabs:blocks/blue_plate"),"Explicit namespace searches remain available");
        require(!TextureListLayout.matches("mine","Stone","Materials","minecraft:blocks/stone"),"Other namespaces must not leak into ordinary terms");
        System.out.println("Material picker row policy PASS hierarchy/search order, case ties, collapsed ancestors, negative options, pin/reveal");
    }
}

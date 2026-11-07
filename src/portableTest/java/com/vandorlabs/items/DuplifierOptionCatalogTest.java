package com.vandorlabs.items;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** All dialog pages, labels and stable bit meanings captured from the actual Forge options. */
public final class DuplifierOptionCatalogTest {
    public static void main(String[] args)throws Exception{
        String[] pages=DuplifierOptionCatalog.pages();DuplifierOptionCatalog.Option[] options=DuplifierOptionCatalog.options();
        StringBuilder json=new StringBuilder("[[");
        for(int i=0;i<pages.length;i++){if(i!=0)json.append(',');quote(json,pages[i]);}
        json.append("],[");
        for(int i=0;i<options.length;i++){
            if(i!=0)json.append(',');DuplifierOptionCatalog.Option option=options[i];
            check(option.page>=0&&option.page<pages.length,"valid page");
            json.append("{\"key\":");quote(json,option.key);json.append(",\"label\":");quote(json,option.label);json.append(",\"page\":").append(option.page).append('}');
        }
        json.append("]]" );
        String source=new String(Files.readAllBytes(Paths.get("testclient/fixtures/copifier-option-catalog-source.json")),StandardCharsets.UTF_8);
        check(source.equals(json.toString()),"actual Source catalog changed");
        pages[0]="mutated";options[0]=null;
        check(DuplifierOptionCatalog.pages()[0].equals("Common")&&DuplifierOptionCatalog.options()[0]!=null,"catalog arrays isolated");
        check(DuplifierOptionCatalog.size()==74,"stable Source option count");
        System.out.println("Copifier catalog PASS actual Source pages=5 choices=74 labels/order/keys and array isolation");
    }
    private static void quote(StringBuilder out,String value){out.append('"').append(value.replace("\\","\\\\").replace("\"","\\\"")).append('"');}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

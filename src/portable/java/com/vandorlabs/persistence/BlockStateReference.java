package com.vandorlabs.persistence;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Canonical, mapping-independent block id plus sorted string properties. */
public final class BlockStateReference {
    public final String id;
    public final Map<String,String> properties;

    public BlockStateReference(String id,Map<String,String> properties) {
        if (!validId(id)) throw new IllegalArgumentException("invalid block id");
        TreeMap<String,String> copy=new TreeMap<>();
        for (Map.Entry<String,String> entry:properties.entrySet()) {
            if (!validToken(entry.getKey())||!validToken(entry.getValue()))
                throw new IllegalArgumentException("invalid block-state property");
            copy.put(entry.getKey(),entry.getValue());
        }
        this.id=id;
        this.properties=Collections.unmodifiableMap(copy);
    }

    public String encode() {
        if (properties.isEmpty()) return id;
        StringBuilder result=new StringBuilder(id).append('[');
        boolean first=true;
        for (Map.Entry<String,String> entry:properties.entrySet()) {
            if (!first) result.append(',');
            result.append(entry.getKey()).append('=').append(entry.getValue());
            first=false;
        }
        return result.append(']').toString();
    }

    public static BlockStateReference parse(String encoded) {
        if (encoded==null) throw new IllegalArgumentException("missing block state");
        int bracket=encoded.indexOf('[');
        if (bracket<0) return new BlockStateReference(encoded,Collections.emptyMap());
        if (!encoded.endsWith("]")||encoded.indexOf('[',bracket+1)>=0)
            throw new IllegalArgumentException("invalid block state");
        String body=encoded.substring(bracket+1,encoded.length()-1);
        Map<String,String> properties=new TreeMap<>();
        if (!body.isEmpty()) for (String pair:body.split(",",-1)) {
            int equals=pair.indexOf('=');
            if (equals<=0||equals==pair.length()-1||pair.indexOf('=',equals+1)>=0)
                throw new IllegalArgumentException("invalid block-state property");
            String previous=properties.put(pair.substring(0,equals),pair.substring(equals+1));
            if (previous!=null) throw new IllegalArgumentException("duplicate block-state property");
        }
        return new BlockStateReference(encoded.substring(0,bracket),properties);
    }

    private static boolean validId(String value) {
        if (value==null||value.isEmpty()||value.indexOf(':')<=0) return false;
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if (!(c>='a'&&c<='z')&&!(c>='0'&&c<='9')&&c!='_'&&c!='-'&&c!='.'&&c!='/'&&c!=':')
                return false;
        }
        return true;
    }

    private static boolean validToken(String value) {
        if (value==null||value.isEmpty()) return false;
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if (!(c>='a'&&c<='z')&&!(c>='0'&&c<='9')&&c!='_'&&c!='-'&&c!='.') return false;
        }
        return true;
    }
}

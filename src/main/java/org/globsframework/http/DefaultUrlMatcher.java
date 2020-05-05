package org.globsframework.http;

import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.model.Glob;
import org.globsframework.model.MutableGlob;
import org.globsframework.utils.StringConverter;

import java.util.HashMap;
import java.util.Map;

class DefaultUrlMatcher implements UrlMatcher {
    private final GlobType globType;
    private final String fullUrl; // /titi/{AZE}/SD/{XYZ}
    private final Map<Integer, StringConverter.FromStringConverter> argByPosition = new HashMap<>();

    public DefaultUrlMatcher(GlobType globType, String fullUrl) {
        this.globType = globType;
        this.fullUrl = fullUrl;
        String[] split = fullUrl.split("/");
        for (int i = 0; i < split.length; i++) {
            String s = split[i];
            if (s.startsWith("{") && s.endsWith("}")) {
                Field field = globType.getField(s.substring(1, s.length() - 1));
                StringConverter.FromStringConverter fromStringConverter = StringConverter.createConverter(field, "");
                argByPosition.put(i, fromStringConverter);
            }
        }
    }

    boolean match(String fullUrl) {
        return true;
    }

    public Glob parse(String url) {
        String[] split = url.split("/");
        MutableGlob instantiate = globType.instantiate();
        for (Map.Entry<Integer, StringConverter.FromStringConverter> integerFieldEntry : argByPosition.entrySet()) {
            integerFieldEntry.getValue().convert(instantiate, split[integerFieldEntry.getKey()]);
        }
        return instantiate;
    }

}

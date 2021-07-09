package org.globsframework.http;

import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.model.Glob;
import org.globsframework.model.MutableGlob;

import java.util.HashMap;
import java.util.Map;

class DefaultUrlMatcher implements UrlMatcher {
    private final GlobType globType;
    private final String fullUrl; // /titi/{AZE}/SD/{XYZ}
    private final Map<Integer, GlobHttpUtils.FromStringConverter> argByPosition = new HashMap<>();

    public DefaultUrlMatcher(GlobType globType, String fullUrl) {
        this.globType = globType;
        this.fullUrl = fullUrl;
        String[] split = fullUrl.split("/");
        for (var i = 0; i < split.length; i++) {
            String s = split[i];
            if (s.startsWith("{") && s.endsWith("}")) {
                var field = globType.getField(s.substring(1, s.length() - 1));
                var fromStringConverter = GlobHttpUtils.createConverter(field, "");
                argByPosition.put(i, fromStringConverter);
            }
        }
    }

//    boolean match(String fullUrl) {
//        return true;
//    }

    /**
     * Parses the url and returns a glob with the path params.
     * @param url - the url passed to the http server
     * @return a glob with the path params
     */
    public Glob parse(String url) {
        String[] split = url.split("/");
        MutableGlob instantiate = globType.instantiate();
        for (Map.Entry<Integer, GlobHttpUtils.FromStringConverter> integerFieldEntry : argByPosition.entrySet()) {
            integerFieldEntry.getValue().convert(instantiate, split[integerFieldEntry.getKey()]);
        }
        return instantiate;
    }

}

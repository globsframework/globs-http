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
    private final GlobHttpUtils.FromStringConverter[] argByPosition;

    public DefaultUrlMatcher(GlobType globType, String fullUrl) {
        this.globType = globType;
        this.fullUrl = fullUrl;
        String[] split;
        if (fullUrl.startsWith("/")) {
            split = fullUrl.substring(1).split("/");
        }else {
            split = fullUrl.split("/");
        }
        argByPosition = new GlobHttpUtils.FromStringConverter[split.length];
        for (int i = 0; i < split.length; i++) {
            String s = split[i];
            if (s.startsWith("{") && s.endsWith("}")) {
                Field field = globType.getField(s.substring(1, s.length() - 1));
                argByPosition[i] = GlobHttpUtils.createConverter(field, "");
            }
            else {
                argByPosition[i] = null;
            }
        }
    }

//    boolean match(String fullUrl) {
//        return true;
//    }

    /**
     * Parses the url and returns a glob with the path params.
     *
     * @return a glob with the path params
     * @param split
     */
    public Glob parse(String[] split) {
        MutableGlob instantiate = globType.instantiate();
        for (int i = 0, argByPositionLength = argByPosition.length; i < argByPositionLength; i++) {
            GlobHttpUtils.FromStringConverter fromStringConverter = argByPosition[i];
            if (fromStringConverter != null) {
                fromStringConverter.convert(instantiate, split[i]);
            }
        }
        return instantiate;
    }

}

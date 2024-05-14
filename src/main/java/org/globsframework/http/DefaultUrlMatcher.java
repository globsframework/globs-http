package org.globsframework.http;

import org.globsframework.metamodel.fields.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.fields.StringArrayField;
import org.globsframework.model.Glob;
import org.globsframework.model.MutableGlob;

import java.util.Arrays;

class DefaultUrlMatcher {


    public static UrlMatcher create(GlobType globType, String fullUrl) {
        if (globType == null) {
            return new UrlMatcher() {
                public Glob parse(String[] split) {
                    return null;
                }

                public boolean withWildCard() {
                    return false;
                }
            };
        }
        String[] split;
        if (fullUrl.startsWith("/")) {
            split = fullUrl.substring(1).split("/");
        } else {
            split = fullUrl.split("/");
        }
        GlobHttpUtils.FromStringConverter[] argByPosition = new GlobHttpUtils.FromStringConverter[split.length];
        for (int i = 0; i < split.length; i++) {
            String s = split[i];
            if (s.startsWith("{") && s.endsWith("}")) {
                Field field = globType.getField(s.substring(1, s.length() - 1));
                if (field instanceof StringArrayField) {
                    if (i < split.length - 1) {
                        throw new RuntimeException("array field are expected only at end of path");
                    }
                    argByPosition[i] = null;
                    return new UrlWithWildard(globType, (StringArrayField) field, argByPosition);
                } else {
                    argByPosition[i] = GlobHttpUtils.createConverter(field, "");
                }
            } else {
                argByPosition[i] = null;
            }
        }
        return new SimpleUrlMatcher(globType, argByPosition);
    }

    public static class UrlWithWildard implements UrlMatcher {
        private final GlobType globType;
        private final GlobHttpUtils.FromStringConverter[] argByPosition;
        private final StringArrayField fields;

        public UrlWithWildard(GlobType globType, StringArrayField fields, GlobHttpUtils.FromStringConverter[] argByPosition) {
            this.globType = globType;
            this.fields = fields;
            this.argByPosition = argByPosition;
        }

        public Glob parse(String[] split) {
            MutableGlob instantiate = globType.instantiate();
            for (int i = 0, argByPositionLength = argByPosition.length; i < argByPositionLength; i++) {
                GlobHttpUtils.FromStringConverter fromStringConverter = argByPosition[i];
                if (fromStringConverter != null) {
                    fromStringConverter.convert(instantiate, split[i]);
                }
            }
            String[] pending = Arrays.copyOfRange(split, argByPosition.length - 1, split.length);
            instantiate.set(fields, pending);
            return instantiate;
        }

        public boolean withWildCard() {
            return true;
        }
    }

    public static class SimpleUrlMatcher implements UrlMatcher {
        private final GlobType globType;
        private final GlobHttpUtils.FromStringConverter[] argByPosition;

        public SimpleUrlMatcher(GlobType globType, GlobHttpUtils.FromStringConverter[] argByPosition) {
            this.globType = globType;
            this.argByPosition = argByPosition;
        }

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

        public boolean withWildCard() {
            return false;
        }
    }

}

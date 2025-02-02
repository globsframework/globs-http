package org.globsframework.http;

import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.json.GSonUtils;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public class GlobHttpUtils {

    public static String createRoute(String route, Glob urlParam) {
        String[] split = route.split("/");
        StringBuilder r = new StringBuilder();
        r.append("/");
        for (String s : split) {
            if (s.length() != 0) {
                if (s.startsWith("{") && s.endsWith("}")) {
                    String param = s.substring(1, s.length() - 1);
                    Field field = urlParam.getType().getField(param);
                    Object value = urlParam.getValue(field);
                    if (value == null) {
                        throw new RuntimeException("Invalide url " + route + " " + GSonUtils.encode(urlParam, true));
                    }
                    r.append(value);
                } else {
                    r.append(s);
                }
                r.append("/");
            }
        }
        return r.deleteCharAt(r.length() - 1).toString();
    }

    public static HttpPost createPost(String route, Glob parameters) {
        String format = formatURL(parameters);
        return new HttpPost(createURL(route, format));
    }

    public static HttpPut createPut(String route, Glob parameters) {
        String format = formatURL(parameters);
        return new HttpPut(createURL(route, format));
    }

    public static HttpDelete createDelete(String route, Glob parameters) {
        String format = formatURL(parameters);
        return new HttpDelete(createURL(route, format));
    }

    public static HttpPut createPut(String route, Glob parameters, Glob body) {
        String format = formatURL(parameters);
        HttpPut httpPut = new HttpPut(createURL(route, format));
        String encode = GSonUtils.encode(body, true);
        httpPut.setEntity(new StringEntity(encode, ContentType.APPLICATION_JSON));
        return httpPut;
    }

    private static String createURL(String route, String format) {
        return route + (format.isEmpty() ? "" : ("?" + format));
    }

    public static HttpPost createPost(String route, Glob parameters, Glob body) {
        String format = formatURL(parameters);
        HttpPost httpPost = new HttpPost(createURL(route, format));
        String encode = GSonUtils.encode(body, true);
        httpPost.setEntity(new StringEntity(encode, ContentType.APPLICATION_JSON));
        return httpPost;
    }

    public static HttpGet createGet(String route, Glob parameters) {
        String format = formatURL(parameters);
        return new HttpGet(createURL(route, format));
    }

    public static String formatURL(Glob parameters) {
        return URLEncodedUtils.format(glob2ValuePairList(parameters), StandardCharsets.UTF_8);
    }

    static List<NameValuePair> glob2ValuePairList(Glob parameters) {
        List<NameValuePair> nameValuePairList = new ArrayList<>();
        if (parameters == null) {
            return nameValuePairList;
        }

        for (Field field : parameters.getType().getFields()) {
            if (!parameters.isNull(field)) {
                if (!field.getDataType().isPrimive()) {
                    var visitor = new FieldValueVisitor.AbstractWithErrorVisitor() {
                        String out;

                        public void visitGlob(GlobField field, Glob value) throws Exception {
                            String encode = GSonUtils.encode(value, true);
                            out = Base64.getUrlEncoder().encodeToString(encode.getBytes(StandardCharsets.UTF_8));
                        }

                        public void visitGlobArray(GlobArrayField field, Glob[] value) throws Exception {
                            String encode = GSonUtils.encode(value, true);
                            out = Base64.getUrlEncoder().encodeToString(encode.getBytes(StandardCharsets.UTF_8));
                        }

                        public void visitUnionGlob(GlobUnionField field, Glob value) throws Exception {
                            String encode = GSonUtils.encode(value, true);
                            out = Base64.getUrlEncoder().encodeToString(encode.getBytes(StandardCharsets.UTF_8));
                        }

                        public void visitUnionGlobArray(GlobArrayUnionField field, Glob[] value) throws Exception {
                            String encode = GSonUtils.encode(value, true);
                            out = Base64.getUrlEncoder().encodeToString(encode.getBytes(StandardCharsets.UTF_8));
                        }
                    };
                    field.safeAccept(visitor, parameters.getValue(field));
                    nameValuePairList.add(new BasicNameValuePair(field.getName(), visitor.out));
                } else if (field.getDataType().isArray()) {
                    if (field instanceof StringArrayField) {
                        nameValuePairList.add(new BasicNameValuePair(field.getName(), String.join(",", parameters.get((StringArrayField) field))));
                    } else if (field instanceof LongArrayField) {
                        nameValuePairList.add(new BasicNameValuePair(field.getName(),
                                Arrays.stream(parameters.get((LongArrayField) field))
                                        .mapToObj(Long::toString).collect(Collectors.joining(","))));
                    } else if (field instanceof DoubleArrayField) {
                        nameValuePairList.add(new BasicNameValuePair(field.getName(),
                                Arrays.stream(parameters.get((DoubleArrayField) field))
                                        .mapToObj(Double::toString).collect(Collectors.joining(","))));
                    } else {
                        throw new RuntimeException("Field type " + field.getDataType() + " not managed " + field.getFullName());
                    }
                } else {
                    nameValuePairList.add(new BasicNameValuePair(field.getName(),
                            Objects.toString(parameters.getValue(field))));
                }
            }
        }

        return nameValuePairList;
    }

    public static FromStringConverter createConverter(Field field, String arraySeparator) {
        return field.safeAccept(new FieldVisitor.AbstractWithErrorVisitor() {
            FromStringConverter fromStringConverter1;

            public void visitInteger(IntegerField field1) throws Exception {
                fromStringConverter1 = new ToIntegerConverter(field1);
            }

            public void visitLong(LongField field1) throws Exception {
                fromStringConverter1 = new ToLongConverter(field1);
            }

            public void visitString(StringField field1) throws Exception {
                fromStringConverter1 = new ToStringConverter(field1);
            }

            public void visitStringArray(StringArrayField field1) throws Exception {
                fromStringConverter1 = new ToStringArrayConverter(field1, arraySeparator);
            }

            public void visitLongArray(LongArrayField field1) throws Exception {
                fromStringConverter1 = new ToLongArrayConverter(field1, arraySeparator);
            }

            public void visitDateTime(DateTimeField field1) throws Exception {
                fromStringConverter1 = new ToDateTimeConverter(field1);
            }

            public void visitDate(DateField field1) throws Exception {
                fromStringConverter1 = new ToDateConverter(field1);
            }

            public void visitBoolean(BooleanField field) throws Exception {
                fromStringConverter1 = new ToBooleanConverter(field);
            }

            public void visitGlob(GlobField field) throws Exception {
                fromStringConverter1 = new FromStringConverter() {
                    public void convert(MutableGlob glob, String str) {
                        glob.set(field, GSonUtils.decode(new String(Base64.getUrlDecoder().decode(str), StandardCharsets.UTF_8), field.getTargetType()));
                    }
                };
            }

            public void visitGlobArray(GlobArrayField field) throws Exception {
                fromStringConverter1 = new FromStringConverter() {
                    public void convert(MutableGlob glob, String str) {
                        glob.set(field, GSonUtils.decodeArray(new String(Base64.getUrlDecoder().decode(str), StandardCharsets.UTF_8), field.getTargetType()));
                    }
                };
            }
        }).fromStringConverter1;
    }

    public interface FromStringConverter {
        void convert(MutableGlob glob, String str);
    }

    public static class ToStringConverter implements FromStringConverter {
        final StringField field;

        public ToStringConverter(StringField field) {
            this.field = field;
        }

        public void convert(MutableGlob glob, String str) {
            if (str != null) {
                glob.set(field, str);
            }
        }
    }

    public static class ToIntegerConverter implements FromStringConverter {
        final IntegerField field;

        public ToIntegerConverter(IntegerField field) {
            this.field = field;
        }

        public void convert(MutableGlob glob, String str) {
            if (str != null) {
                glob.set(field, Integer.parseInt(str));
            }
        }
    }

    public static class ToBooleanConverter implements FromStringConverter {
        private BooleanField field;

        public ToBooleanConverter(BooleanField field) {
            this.field = field;
        }

        public void convert(MutableGlob glob, String str) {
            if (str != null) {
                glob.set(field, Boolean.valueOf(str));
            }
        }
    }

    public static class ToDateTimeConverter implements FromStringConverter {
        private DateTimeField dateTimeField;

        public ToDateTimeConverter(DateTimeField dateTimeField) {
            this.dateTimeField = dateTimeField;
        }

        public void convert(MutableGlob glob, String str) {
            if (str != null) {
                final int indexOfT = str.indexOf("T");
                if (indexOfT != -1) {
                    if (str.contains("+") || str.lastIndexOf("-") > indexOfT || str.endsWith("Z")) {
                        glob.set(dateTimeField, ZonedDateTime.parse(str));
                    } else {
                        glob.set(dateTimeField, ZonedDateTime.of(LocalDateTime.parse(str), ZoneId.systemDefault()));
                    }
                } else {
                    LocalDate parse = LocalDate.parse(str);
                    glob.set(dateTimeField, ZonedDateTime.of(parse, LocalTime.MIDNIGHT, ZoneId.systemDefault()));
                }
            }
        }
    }

    public static class ToDateConverter implements FromStringConverter {
        private DateField dateField;

        public ToDateConverter(DateField dateField) {
            this.dateField = dateField;
        }

        public void convert(MutableGlob glob, String str) {
            if (str != null) {
                final int indexOfT = str.indexOf("T");
                if (indexOfT != -1) {
                    if (str.contains("+") || str.lastIndexOf("-") > indexOfT || str.endsWith("Z")) {
                        glob.set(dateField, ZonedDateTime.parse(str).toLocalDate());
                    } else {
                        glob.set(dateField, LocalDateTime.parse(str).toLocalDate());
                    }
                } else {
                    LocalDate parse = LocalDate.parse(str);
                    glob.set(dateField, parse);
                }
            }
        }
    }

    public static class ToLongConverter implements FromStringConverter {
        final LongField field;

        public ToLongConverter(LongField field) {
            this.field = field;
        }

        public void convert(MutableGlob glob, String str) {
            if (str != null) {
                glob.set(field, Long.parseLong(str));
            }
        }
    }

    public static class ToStringArrayConverter implements FromStringConverter {
        final StringArrayField field;
        private String arraySeparator;

        public ToStringArrayConverter(StringArrayField field, String arraySeparator) {
            this.field = field;
            this.arraySeparator = arraySeparator;
        }

        public void convert(MutableGlob glob, String str) {
            if (str != null) {
                String[] data;
                if (arraySeparator != null) {
                    data = str.split(arraySeparator);
                } else {
                    data = new String[]{str};
                }
                String[] actual = glob.getOrEmpty(field);
                String[] newValue = new String[actual.length + data.length];
                System.arraycopy(actual, 0, newValue, 0, actual.length);
                for (int i = 0; i < data.length; i++) {
                    String d = data[i];
                    newValue[actual.length + i] = d;
                }
                glob.set(field, newValue);
            }
        }
    }

    public static class ToLongArrayConverter implements FromStringConverter {
        final LongArrayField field;
        private String arraySeparator;

        public ToLongArrayConverter(LongArrayField field, String arraySeparator) {
            this.field = field;
            this.arraySeparator = arraySeparator;
        }

        public void convert(MutableGlob glob, String str) {
            if (str != null) {
                String[] data;
                if (arraySeparator != null) {
                    data = str.split(arraySeparator);
                } else {
                    data = new String[]{str};
                }
                long[] actual = glob.getOrEmpty(field);
                long[] newValue = new long[actual.length + data.length];
                System.arraycopy(actual, 0, newValue, 0, actual.length);
                for (int i = 0; i < data.length; i++) {
                    String d = data[i];
                    newValue[actual.length + i] = Long.parseLong(d);
                }
                glob.set(field, newValue);
            }
        }
    }

}

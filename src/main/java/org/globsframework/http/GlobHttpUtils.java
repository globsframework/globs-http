package org.globsframework.http;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.globsframework.json.GSonUtils;
import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.fields.*;
import org.globsframework.model.Glob;
import org.globsframework.model.MutableGlob;
import org.globsframework.utils.StringConverter;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class GlobHttpUtils {

    public static HttpPost createPost(String route, Glob parameters) {
        String format = formatURL(parameters);
        return new HttpPost(createURL(route, format));
    }

    private static String createURL(String route, String format) {
        return route + (format != null ? ("?" + format) : "");
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

    private static String formatURL(Glob parameters) {
        if (parameters == null) {
            return null;
        }
        List<NameValuePair> nameValuePairList = new ArrayList<>();
        for (Field field : parameters.getType().getFields()) {
            if (parameters.isSet(field)) {
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
                    field.safeVisit(visitor, parameters.getValue(field));
                    nameValuePairList.add(new BasicNameValuePair(field.getName(), visitor.out));
                }
                else if (field.getDataType().isArray()) {
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
                    }
                    else {
                        throw new RuntimeException("Field type " + field.getDataType() + " not managed " + field.getFullName());
                    }
                } else {
                    nameValuePairList.add(new BasicNameValuePair(field.getName(),
                            Objects.toString(parameters.getValue(field))));
                }
            }
        }
        return URLEncodedUtils.format(nameValuePairList, StandardCharsets.UTF_8);
    }

    public static FromStringConverter createConverter(Field field, String arraySeparator) {
        return field.safeVisit(new FieldVisitor.AbstractWithErrorVisitor() {
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

            public void visitDateTime(DateTimeField field1) throws Exception {
                fromStringConverter1 = new ToDateTimeConverter(field1);
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
                if (str.contains("T")) {
                    glob.set(dateTimeField, ZonedDateTime.parse(str));
                }
                else {
                    if (str.length() > 6) {
                        ZoneId zoneId = ZoneId.systemDefault();
                        ZonedDateTime.parse(str);
                    }
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

}

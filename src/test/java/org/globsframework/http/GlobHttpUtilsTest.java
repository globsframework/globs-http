package org.globsframework.http;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.FieldNameAnnotation;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.fields.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class GlobHttpUtilsTest {

    @Test
    public void name() {
        HttpPost path = GlobHttpUtils.createPost("path", PARAM.TYPE.instantiate()
                .set(PARAM.aLong, 23)
                .set(PARAM.bool, true)
                .set(PARAM.str, "Some String with &=sdfsfd")
        );
        String s = path.getURI().toString();
        Assert.assertEquals("path?str=Some+String+with+%26%3Dsdfsfd&bool=true&aLong=23", s);
    }

    @Test
    public void testGlobInGlobInURL() {
        HttpPost path = GlobHttpUtils.createPost("path", PARAM.TYPE.instantiate()
                .set(PARAM.aLong, 23)
                .set(PARAM.bool, true)
                .set(PARAM.param, PARAM.TYPE.instantiate()
                    .set(PARAM.str, "some other info")
                    .set(PARAM.aLong, 42)
                )
        );
        String s = path.getURI().toString();
        Assert.assertEquals("path?bool=true&aLong=23&param=eyJfa2luZCI6InBBUkFNIiwic3RyIjoic29tZSBvdGhlciBpbmZvIiwiYUxvbmciOjQyfQ%3D%3D", s);
    }

    @Test
    public void createPost_should_return_basic_route_when_params_are_null() {
        HttpPost path = GlobHttpUtils.createPost("path", null);
        Assert.assertEquals("path", path.getURI().toString());
    }

    @Test
    public void glob2ValuePairList_should_return_a_list_representing_the_glob_fields() {
        List<NameValuePair> list = GlobHttpUtils.glob2ValuePairList(PARAM.TYPE.instantiate()
                .set(PARAM.aLong, 23)
                .set(PARAM.bool, true)
                .set(PARAM.param, PARAM.TYPE.instantiate()
                        .set(PARAM.str, "some other info")
                        .set(PARAM.aLong, 42)
                )
                .set(PARAM.composedName, "John")
        );
        Assert.assertEquals(4, list.size());
        Assert.assertEquals(new BasicNameValuePair("bool", "true"), list.get(0));
        Assert.assertEquals(new BasicNameValuePair("aLong", "23"), list.get(1));
        Assert.assertEquals(new BasicNameValuePair("client.name", "John"), list.get(2));
        Assert.assertEquals(new BasicNameValuePair("param",
                "eyJfa2luZCI6InBBUkFNIiwic3RyIjoic29tZSBvdGhlciBpbmZvIiwiYUxvbmciOjQyfQ=="), list.get(3));
    }

    @Test
    public void glob2ValuePairList_should_return_empty_list_when_params_are_null() {
        List<NameValuePair> list = GlobHttpUtils.glob2ValuePairList(null);
        Assert.assertTrue(list.isEmpty());
    }

    public static class TEST {
        public static GlobType TYPE;

        public static DateTimeField datetime;

        static {
            GlobTypeLoaderFactory.create(TEST.class).load();
        }

    }

    @Test
    public void testTime() {
        GlobHttpUtils.FromStringConverter converter = GlobHttpUtils.createConverter(TEST.datetime, ",");
        converter.convert(TEST.TYPE.instantiate(), "2021-09-01T14:55:43+02:00");
        converter.convert(TEST.TYPE.instantiate(), "2021-09-01T14:55:43");
        converter.convert(TEST.TYPE.instantiate(), "2021-09-01");
    }

    @Test
    public void createRoute() {
        String r = GlobHttpUtils.createRoute("/xxx/{code}/yy/{userId}", Url.TYPE.instantiate().set(Url.code, "aCode").set(Url.userId, 33));
        Assert.assertEquals("/xxx/aCode/yy/33", r);

        String r2 = GlobHttpUtils.createRoute("xxx//{code}/yy/{userId}", Url.TYPE.instantiate().set(Url.code, "aCode").set(Url.userId, 33));
        Assert.assertEquals("/xxx/aCode/yy/33", r2);
        String r3 = GlobHttpUtils.createRoute("{code}/{userId}", Url.TYPE.instantiate().set(Url.code, "aCode").set(Url.userId, 33));
        Assert.assertEquals("/aCode/33", r3);
    }

    static public class Url {
        public static GlobType TYPE;

        public static StringField code;

        public static LongField userId;

        static {
            GlobTypeLoaderFactory.create(Url.class).load();
        }
    }

    static public class PARAM {
        public static GlobType TYPE;

        public static StringField str;

        public static BooleanField bool;

        public static LongField aLong;

        @FieldNameAnnotation("client.name")
        public static StringField composedName;

        @Target(PARAM.class)
        public static GlobField param;

        static {
            GlobTypeLoaderFactory.create(PARAM.class).load();
        }
    }
}
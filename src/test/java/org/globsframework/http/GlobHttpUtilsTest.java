package org.globsframework.http;

import org.apache.http.client.methods.HttpPost;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.fields.BooleanField;
import org.globsframework.metamodel.fields.GlobField;
import org.globsframework.metamodel.fields.LongField;
import org.globsframework.metamodel.fields.StringField;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class GlobHttpUtilsTest {

    @Test
    public void name() {
        HttpPost path = GlobHttpUtils.createPost("path", PARAM.TYPE.instantiate().set(PARAM.aLong, 23)
                .set(PARAM.bool, true).set(PARAM.str, "Some String with &=" +
                        "sdfsfd"));
        String s = path.getURI().toString();
        Assert.assertEquals("path?str=Some+String+with+%26%3Dsdfsfd&bool=true&aLong=23", s);
    }

    @Test
    public void testGlobInGlobInURL() {
        HttpPost path = GlobHttpUtils.createPost("path", PARAM.TYPE.instantiate().set(PARAM.aLong, 23)
                .set(PARAM.bool, true).set(PARAM.param, PARAM.TYPE.instantiate()
                        .set(PARAM.str, "some other info")
                        .set(PARAM.aLong, 42)));
        String s = path.getURI().toString();
        Assert.assertEquals("path?bool=true&aLong=23&param=eyJfa2luZCI6InBBUkFNIiwic3RyIjoic29tZSBvdGhlciBpbmZvIiwiYUxvbmciOjQyfQ%3D%3D", s);

    }


    static public class PARAM {
        public static GlobType TYPE;

        public static StringField str;

        public static BooleanField bool;

        public static LongField aLong;

        @Target(PARAM.class)
        public static GlobField param;

        static {
            GlobTypeLoaderFactory.create(PARAM.class).load();
        }
    }
}
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
import org.globsframework.model.Glob;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GlobHttpUtils {

    public static HttpPost createPost(String route, Glob parameters) {
        String format = formatURL(parameters);
        return new HttpPost(route + "?" + format);
    }

    public static HttpPost createPost(String route, Glob parameters, Glob body) {
        String format = formatURL(parameters);
        HttpPost httpPost = new HttpPost(route + "?" + format);
        String encode = GSonUtils.encode(body, true);
        httpPost.setEntity(new StringEntity(encode, ContentType.APPLICATION_JSON));
        return httpPost;
    }

    public static HttpGet createGet(String route, Glob parameters) {
        String format = formatURL(parameters);
        return new HttpGet(route + "?" + format);
    }

    private static String formatURL(Glob parameters) {
        List<NameValuePair> nameValuePairList = new ArrayList<>();
        for (Field field : parameters.getType().getFields()) {
            if (parameters.isSet(field)) {
                nameValuePairList.add(new BasicNameValuePair(field.getName(),
                        Objects.toString(parameters.getValue(field))));
            }
        }
        return URLEncodedUtils.format(nameValuePairList, StandardCharsets.UTF_8);
    }
}

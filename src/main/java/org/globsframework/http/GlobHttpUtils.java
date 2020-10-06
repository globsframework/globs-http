package org.globsframework.http;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.globsframework.metamodel.Field;
import org.globsframework.model.Glob;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GlobHttpUtils {

    public static HttpPost createPost(String route, Glob parameters) {
        List<NameValuePair> nameValuePairList = new ArrayList<>();
        for (Field field : parameters.getType().getFields()) {
            if (parameters.isSet(field)){
                nameValuePairList.add(new BasicNameValuePair(field.getName(),
                        Objects.toString(parameters.getValue(field))));
            }
        }
        String format = URLEncodedUtils.format(nameValuePairList, StandardCharsets.UTF_8);
        return new HttpPost(route + "?" + format);
    }
}

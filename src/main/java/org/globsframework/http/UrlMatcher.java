package org.globsframework.http;

import org.globsframework.model.Glob;

public interface UrlMatcher {

    Glob parse(String[] split);

    boolean withWildCard();
}

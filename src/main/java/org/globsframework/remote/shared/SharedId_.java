package org.globsframework.remote.shared;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.annotations.AutoIncrementAnnotationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({ElementType.FIELD})
public @interface SharedId_ {
    GlobType TYPE = SharedId.TYPE;
}

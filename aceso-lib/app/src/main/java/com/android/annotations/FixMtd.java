package com.android.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;

/**
 * Created by wangzhi on 16/12/21.
 */

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({METHOD})
public @interface FixMtd {
}

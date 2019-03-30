package app.aiyun.mvcframework.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@BobbyComponent
public @interface BobbyController {
    String value() default "";
}




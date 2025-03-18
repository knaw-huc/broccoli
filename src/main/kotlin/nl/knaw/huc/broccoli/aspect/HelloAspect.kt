package io.github.serpro69.kotlinacj;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
 class HelloAspect {

    @Before("execution(* *(..)) && @annotation(HelloAnnotation)")
    public fun before(joinPoint: JoinPoint){
        println("Before Hello!");
    }

    @After("execution(* *(..)) && @annotation(HelloAnnotation)")
    public fun after(joinPoint: JoinPoint){
        println("After Hello!");
    }
}

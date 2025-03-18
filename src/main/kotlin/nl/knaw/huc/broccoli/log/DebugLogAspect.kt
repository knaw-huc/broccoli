package nl.knaw.huc.broccoli.log;

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import java.util.logging.Logger

@Aspect
class DebugLogAspect {
    private val mapper = ObjectMapper().registerKotlinModule()
    private val log: Logger = Logger.getLogger(DebugLogAspect::class.java.name)

    @Before("@annotation(DebugLog) && execution(* *(..))")
    public fun before(joinPoint: JoinPoint){
        val methodName = joinPoint.signature.toShortString()
        val args = mapper.writeValueAsString(joinPoint.args)
//        log.info("$methodName called with: $args");
        println("$methodName called with: $args");
    }

}

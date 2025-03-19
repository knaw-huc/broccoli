package nl.knaw.huc.broccoli.log;

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.slf4j.LoggerFactory

@Aspect
class DebugLogAspect {
    private val log = LoggerFactory.getLogger(DebugLog::class.java.name)
    private val mapper = ObjectMapper().registerKotlinModule()

    @Before("@annotation(DebugLog) && execution(* *(..))")
    fun before(joinPoint: JoinPoint){
        if (!log.isDebugEnabled) {
            return;
        }
        val methodName = joinPoint.signature.toShortString()
        val args = mapper.writeValueAsString(joinPoint.args)
        log.debug("$methodName called with: $args");
    }

}

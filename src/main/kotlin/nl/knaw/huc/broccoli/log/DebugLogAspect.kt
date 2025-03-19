package nl.knaw.huc.broccoli.log;

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory

@Aspect
class DebugLogAspect {
    private val log = LoggerFactory.getLogger(DebugLog::class.java.simpleName)
    private val mapper = ObjectMapper().registerKotlinModule()

    @Around("@annotation(DebugLog) && execution(* *(..))")
    fun around(joinPoint: ProceedingJoinPoint): Any {
        val methodName = joinPoint.signature.toShortString()
        if (log.isDebugEnabled) {
            val args = mapper.writeValueAsString(joinPoint.args)
            log.atDebug()
                .addKeyValue("args", args)
                .log("$methodName called:")
        }
        val result = joinPoint.proceed()
        if (log.isDebugEnabled) {
            val resultAsJson = mapper.writeValueAsString(result)
            log.atDebug()
                .addKeyValue("result", resultAsJson)
                .log("$methodName returned:")
        }
        return result
    }

}

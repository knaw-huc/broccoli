package nl.knaw.huc.broccoli.log;

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory

@Aspect
class TraceLogAspect {
    private val log = LoggerFactory.getLogger(TraceLog::class.java.simpleName)
    private val mapper = ObjectMapper().registerKotlinModule()

    @Around("@annotation(TraceLog) && execution(* *(..))")
    fun around(joinPoint: ProceedingJoinPoint): Any {
        val methodName = joinPoint.signature.toShortString()
        if (log.isTraceEnabled) {
            val args = mapper.writeValueAsString(joinPoint.args)
            log.atTrace()
                .addKeyValue("args", args)
                .log("$methodName called:")
        }
        val result = joinPoint.proceed()
        if (log.isTraceEnabled) {
            val resultAsString =
                if (result is String) result
                else mapper.writeValueAsString(result)

            log.atTrace()
                .addKeyValue("result", resultAsString)
                .log("$methodName returned:")
        }
        return result
    }

}

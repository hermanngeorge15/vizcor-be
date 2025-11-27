package com.jh.proj.coroutineviz.extension

import kotlinx.coroutines.CoroutineScope
import org.slf4j.Logger

fun CoroutineScope.logAllContext(logger: Logger) {
    val ctx = coroutineContext
    ctx.fold("") { acc, element -> "$acc$element " }
        .also {
            logger.info("coroutine-context: $it")
        }
}
package com.jh.proj.coroutineviz.extension

import com.jh.proj.coroutineviz.events.CoroutineEvent
import com.jh.proj.coroutineviz.events.VizEvent

fun VizEvent.getLabel(): String? =
    (this as? CoroutineEvent)?.label
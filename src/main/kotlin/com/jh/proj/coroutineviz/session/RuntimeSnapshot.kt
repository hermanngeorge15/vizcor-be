package com.jh.proj.coroutineviz.session

import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

class RuntimeSnapshot {
    val coroutines: MutableMap<String, CoroutineNode> = mutableMapOf()
    private val jobToCoroutineId = ConcurrentHashMap<Job, String>()

    fun registerJob(job: Job, coroutineId: String) {
        jobToCoroutineId[job] = coroutineId
    }

    fun getCoroutineIdFromJob(job: Job): String? {
        return jobToCoroutineId[job]
    }
}
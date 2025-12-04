package com.jh.proj.coroutineviz.scenarios

import com.jh.proj.coroutineviz.wrappers.*
import kotlinx.coroutines.*

/**
 * Real-world synchronization scenario examples.
 * 
 * These scenarios demonstrate practical uses of Mutex and Semaphore
 * in concurrent Kotlin applications.
 */
object SyncScenarios {
    
    // ========================================================================
    // MUTEX SCENARIOS
    // ========================================================================
    
    /**
     * Scenario: Thread-Safe Counter
     * 
     * Real-world use case: Tracking active users, request counts, etc.
     * Without synchronization, concurrent increments can lose updates.
     */
    suspend fun threadSafeCounter(scope: VizScope) {
        val mutex = scope.vizMutex("counter-lock")
        var counter = 0
        
        // Launch 10 coroutines that each increment 100 times
        val jobs = (1..10).map { workerId ->
            scope.vizLaunch("worker-$workerId") {
                repeat(100) {
                    mutex.withLock {
                        val current = counter
                        // Simulate some work
                        vizDelay(1)
                        counter = current + 1
                    }
                }
            }
        }
        
        jobs.forEach { it.join() }
        // Counter should be exactly 1000
        println("Final counter value: $counter")
    }
    
    /**
     * Scenario: Bank Account Transfer
     * 
     * Real-world use case: Financial transactions requiring atomicity.
     * Must ensure balance checks and updates happen atomically.
     */
    suspend fun bankAccountTransfer(scope: VizScope) {
        data class Account(var balance: Double, val mutex: VizMutex)
        
        val accountA = Account(1000.0, scope.vizMutex("account-A-lock"))
        val accountB = Account(500.0, scope.vizMutex("account-B-lock"))
        
        suspend fun transfer(from: Account, to: Account, amount: Double, label: String) {
            // Always acquire locks in consistent order to prevent deadlock
            val (first, second) = if (from.mutex.mutexId < to.mutex.mutexId) {
                from to to
            } else {
                to to from
            }
            
            first.mutex.withLock {
                second.mutex.withLock {
                    if (from.balance >= amount) {
                        from.balance -= amount
                        // Simulate processing time
                        delay(50)
                        to.balance += amount
                        println("$label: Transferred $$amount")
                    } else {
                        println("$label: Insufficient funds")
                    }
                }
            }
        }
        
        // Concurrent transfers
        val jobs = listOf(
            scope.vizLaunch("transfer-A-to-B-1") { 
                transfer(accountA, accountB, 100.0, "Transfer 1") 
            },
            scope.vizLaunch("transfer-B-to-A-1") { 
                transfer(accountB, accountA, 50.0, "Transfer 2") 
            },
            scope.vizLaunch("transfer-A-to-B-2") { 
                transfer(accountA, accountB, 200.0, "Transfer 3") 
            }
        )
        
        jobs.forEach { it.join() }
        println("Account A: $${accountA.balance}, Account B: $${accountB.balance}")
    }
    
    /**
     * Scenario: Cache with Read-Through
     * 
     * Real-world use case: Thread-safe caching with lazy initialization.
     * Only one coroutine should fetch from backend on cache miss.
     */
    suspend fun cacheWithReadThrough(scope: VizScope) {
        val cacheLock = scope.vizMutex("cache-lock")
        val cache = mutableMapOf<String, String>()
        
        suspend fun getOrFetch(key: String): String {
            // Check cache first (without lock for read-only access)
            cache[key]?.let { return it }
            
            // Cache miss - need to fetch
            return cacheLock.withLock {
                // Double-check after acquiring lock
                val cached = cache[key]
                if (cached != null) {
                    cached
                } else {
                    // Simulate fetching from database
                    delay(100)
                    val value = "value-for-$key-${System.currentTimeMillis()}"
                    cache[key] = value
                    value
                }
            }
        }
        
        // Multiple coroutines requesting same keys
        val jobs = (1..5).flatMap { round ->
            listOf("user-1", "user-2", "user-3").map { key ->
                scope.vizLaunch("reader-$round-$key") {
                    val value = getOrFetch(key)
                    println("Got $key = $value")
                }
            }
        }
        
        jobs.forEach { it.join() }
    }
    
    /**
     * Scenario: Deadlock Demonstration
     * 
     * This intentionally creates a deadlock to demonstrate detection.
     * DO NOT use this pattern in real code!
     */
    suspend fun deadlockDemonstration(scope: VizScope) {
        val mutexA = scope.vizMutex("resource-A")
        val mutexB = scope.vizMutex("resource-B")
        
        // This WILL deadlock
        val job1 = scope.vizLaunch("worker-1") {
            mutexA.withLock {
                println("Worker 1: acquired A")
                vizDelay(100) // Give worker 2 time to acquire B
                mutexB.withLock {
                    println("Worker 1: acquired B") // Never reached
                }
            }
        }
        
        val job2 = scope.vizLaunch("worker-2") {
            mutexB.withLock {
                println("Worker 2: acquired B")
                vizDelay(100) // Give worker 1 time to acquire A
                mutexA.withLock {
                    println("Worker 2: acquired A") // Never reached
                }
            }
        }
        
        // These will hang forever without timeout
        withTimeoutOrNull(2000) {
            job1.join()
            job2.join()
        } ?: println("Deadlock detected - timeout!")
    }
    
    // ========================================================================
    // SEMAPHORE SCENARIOS
    // ========================================================================
    
    /**
     * Scenario: Database Connection Pool
     * 
     * Real-world use case: Limiting concurrent database connections.
     * Too many connections can overwhelm the database server.
     */
    suspend fun databaseConnectionPool(scope: VizScope) {
        val connectionPool = scope.vizSemaphore("db-connections", permits = 3)
        
        // 10 concurrent queries, but only 3 connections available
        val jobs = (1..10).map { queryId ->
            scope.vizLaunch("query-$queryId") {
                connectionPool.withPermit {
                    println("Query $queryId: Acquired connection")
                    // Simulate query execution
                    vizDelay((50..200).random().toLong())
                    println("Query $queryId: Released connection")
                }
            }
        }
        
        jobs.forEach { it.join() }
    }
    
    /**
     * Scenario: API Rate Limiter
     * 
     * Real-world use case: Limiting concurrent API calls to external service.
     * Prevents overwhelming the API and getting rate-limited.
     */
    suspend fun apiRateLimiter(scope: VizScope) {
        val rateLimiter = scope.vizSemaphore("api-rate-limit", permits = 5)
        
        // Batch of 20 API calls
        val results = (1..20).map { requestId ->
            scope.vizAsync("api-call-$requestId") {
                rateLimiter.withPermit {
                    println("Request $requestId: Calling API...")
                    // Simulate API call latency
                    vizDelay((100..300).random().toLong())
                    "Response for request $requestId"
                }
            }
        }
        
        results.forEach { deferred ->
            println(deferred.await())
        }
    }
    
    /**
     * Scenario: Parallel File Processor
     * 
     * Real-world use case: Processing files with limited I/O parallelism.
     * Too many concurrent file operations can saturate disk I/O.
     */
    suspend fun parallelFileProcessor(scope: VizScope) {
        val ioSemaphore = scope.vizSemaphore("io-parallelism", permits = 4)
        
        data class FileTask(val name: String, val sizeKb: Int)
        
        val files = listOf(
            FileTask("report.pdf", 500),
            FileTask("data.csv", 1200),
            FileTask("image1.jpg", 800),
            FileTask("image2.jpg", 750),
            FileTask("log.txt", 200),
            FileTask("backup.zip", 2000),
            FileTask("config.json", 10),
            FileTask("video.mp4", 5000)
        )
        
        val jobs = files.map { file ->
            scope.vizLaunch("process-${file.name}") {
                ioSemaphore.withPermit {
                    println("Processing ${file.name}...")
                    // Simulate I/O time proportional to file size
                    vizDelay((file.sizeKb / 10).toLong())
                    println("Completed ${file.name}")
                }
            }
        }
        
        jobs.forEach { it.join() }
    }
    
    /**
     * Scenario: Resource Pool with Timeout
     * 
     * Real-world use case: Acquiring resources with timeout to prevent
     * indefinite waiting in overloaded systems.
     */
    suspend fun resourcePoolWithTimeout(scope: VizScope) {
        val resourcePool = scope.vizSemaphore("limited-resources", permits = 2)
        
        // Rapid requests that will cause some timeouts
        val results = (1..8).map { userId ->
            scope.vizAsync("user-$userId") {
                val acquired = withTimeoutOrNull(500) {
                    resourcePool.acquire()
                    true
                } ?: false
                
                if (acquired) {
                    try {
                        println("User $userId: Using resource")
                        vizDelay(300) // Use resource
                        true
                    } finally {
                        resourcePool.release()
                        println("User $userId: Released resource")
                    }
                } else {
                    println("User $userId: Timeout waiting for resource")
                    false
                }
            }
        }
        
        val successCount = results.count { it.await() }
        println("Successful: $successCount / ${results.size}")
    }
    
    /**
     * Scenario: Producer-Consumer with Bounded Buffer
     * 
     * Real-world use case: Message queue with limited buffer.
     * Producers wait when buffer is full, consumers wait when empty.
     */
    suspend fun producerConsumerBuffer(scope: VizScope) {
        val bufferSize = 5
        val emptySlots = scope.vizSemaphore("buffer-empty-slots", permits = bufferSize)
        val fullSlots = scope.vizSemaphore("buffer-full-slots", permits = 0)
        val bufferLock = scope.vizMutex("buffer-access")
        
        val buffer = ArrayDeque<Int>()
        
        suspend fun produce(item: Int) {
            emptySlots.acquire() // Wait for empty slot
            bufferLock.withLock {
                buffer.addLast(item)
                println("Produced: $item, buffer size: ${buffer.size}")
            }
            fullSlots.release() // Signal item available
        }
        
        suspend fun consume(): Int {
            fullSlots.acquire() // Wait for item
            val item = bufferLock.withLock {
                buffer.removeFirst().also {
                    println("Consumed: $it, buffer size: ${buffer.size}")
                }
            }
            emptySlots.release() // Signal slot available
            return item
        }
        
        // Start producers
        val producers = (1..3).map { producerId ->
            scope.vizLaunch("producer-$producerId") {
                repeat(5) { item ->
                    produce(producerId * 100 + item)
                    vizDelay((10..50).random().toLong())
                }
            }
        }
        
        // Start consumers
        val consumers = (1..2).map { consumerId ->
            scope.vizLaunch("consumer-$consumerId") {
                repeat(7) {
                    consume()
                    vizDelay((20..80).random().toLong())
                }
            }
        }
        
        producers.forEach { it.join() }
        consumers.forEach { it.join() }
    }
    
    // ========================================================================
    // COMBINED SCENARIOS
    // ========================================================================
    
    /**
     * Scenario: E-commerce Order Processing
     * 
     * Real-world use case: Processing orders with inventory management.
     * Combines mutex for inventory updates and semaphore for payment processing.
     */
    suspend fun ecommerceOrderProcessing(scope: VizScope) {
        val inventory = mutableMapOf(
            "laptop" to 5,
            "phone" to 10,
            "tablet" to 3
        )
        val inventoryLock = scope.vizMutex("inventory-lock")
        val paymentGateway = scope.vizSemaphore("payment-gateway", permits = 2)
        
        data class Order(val id: Int, val product: String, val quantity: Int)
        
        suspend fun processOrder(order: Order): Boolean {
            // Check and reserve inventory (mutex)
            val reserved = inventoryLock.withLock {
                val available = inventory[order.product] ?: 0
                if (available >= order.quantity) {
                    inventory[order.product] = available - order.quantity
                    println("Order ${order.id}: Reserved ${order.quantity} ${order.product}")
                    true
                } else {
                    println("Order ${order.id}: Insufficient stock for ${order.product}")
                    false
                }
            }
            
            if (!reserved) return false
            
            // Process payment (semaphore-limited)
            val paid = try {
                paymentGateway.withPermit {
                    println("Order ${order.id}: Processing payment...")
                    delay((100..300).random().toLong())
                    // Simulate 90% success rate
                    kotlin.random.Random.nextDouble() < 0.9
                }
            } catch (e: Exception) {
                false
            }
            
            if (!paid) {
                // Rollback inventory
                inventoryLock.withLock {
                    inventory[order.product] = (inventory[order.product] ?: 0) + order.quantity
                    println("Order ${order.id}: Payment failed, inventory restored")
                }
                return false
            }
            
            println("Order ${order.id}: Completed successfully!")
            return true
        }
        
        val orders = listOf(
            Order(1, "laptop", 2),
            Order(2, "phone", 3),
            Order(3, "tablet", 1),
            Order(4, "laptop", 2),
            Order(5, "phone", 5),
            Order(6, "tablet", 3),
            Order(7, "laptop", 3) // This will fail - not enough stock
        )
        
        val results = orders.map { order ->
            scope.vizAsync("order-${order.id}") {
                processOrder(order)
            }
        }
        
        val successCount = results.count { it.await() }
        println("Successfully processed: $successCount / ${orders.size} orders")
        println("Final inventory: $inventory")
    }
}

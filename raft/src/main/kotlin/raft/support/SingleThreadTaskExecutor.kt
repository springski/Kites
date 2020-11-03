package raft.support

import com.google.common.base.Preconditions
import com.google.common.util.concurrent.FutureCallback
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


class SingleThreadTaskExecutor(private var executorService: ExecutorService) : AbstractTaskExecutor() {

    override fun submit(task: Runnable): Future<*> {
        Preconditions.checkNotNull(task);
        return executorService.submit(task);

    }

    override fun submit(task: Callable<*>): Future<*> {
        Preconditions.checkNotNull(task);
        return executorService.submit(task);
    }

    override fun submit(task: Runnable, callbacks: Collection<FutureCallback<*>>) {
        Preconditions.checkNotNull<Any>(task)
        Preconditions.checkNotNull<Any>(callbacks)
        executorService.submit {
            try {
                task.run()
                callbacks.forEach { c -> c.onSuccess(null) }
            } catch (e: Exception) {
                callbacks.forEach { c -> c.onFailure(e) }
            }
        }
    }

    override fun shutdown() {
        executorService.shutdown()
        executorService.awaitTermination(1, TimeUnit.SECONDS)
    }
}
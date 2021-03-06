package raft.rpc.nio

import com.google.common.eventbus.EventBus
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.slf4j.LoggerFactory
import raft.node.NodeEndpoint
import raft.node.NodeId
import raft.rpc.Channel
import raft.rpc.ChannelConnectException
import raft.rpc.Connector
import raft.rpc.message.*
import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.Executors
import javax.annotation.concurrent.ThreadSafe


@ThreadSafe
class NioConnector(
    workerNioEventLoopGroup: NioEventLoopGroup, workerGroupShared: Boolean,
    selfNodeId: NodeId?, eventBus: EventBus?,
    port: Int, logReplicationInterval: Int
) : Connector {
    private val bossNioEventLoopGroup: NioEventLoopGroup = NioEventLoopGroup(1)
    private val workerNioEventLoopGroup: NioEventLoopGroup = workerNioEventLoopGroup
    private val workerGroupShared: Boolean = workerGroupShared
    private val eventBus: EventBus? = eventBus
    private val port: Int = port
    private val inboundChannelGroup: InboundChannelGroup = InboundChannelGroup()
    private val outboundChannelGroup: OutboundChannelGroup
    private val executorService = Executors.newCachedThreadPool { r: Runnable? ->
        val thread = Thread(r)
        thread.uncaughtExceptionHandler = UncaughtExceptionHandler { t: Thread?, e: Throwable ->
            logException(
                e
            )
        }
        thread
    }

    constructor(selfNodeId: NodeId?, eventBus: EventBus?, port: Int, logReplicationInterval: Int) : this(
        NioEventLoopGroup(),
        false,
        selfNodeId,
        eventBus,
        port,
        logReplicationInterval
    ) {
    }

    constructor(
        workerNioEventLoopGroup: NioEventLoopGroup,
        selfNodeId: NodeId?,
        eventBus: EventBus?,
        port: Int,
        logReplicationInterval: Int
    ) : this(workerNioEventLoopGroup, true, selfNodeId, eventBus, port, logReplicationInterval) {
    }

    // should not call more than once
    override fun initialize() {
        val serverBootstrap: ServerBootstrap = ServerBootstrap()
            .group(bossNioEventLoopGroup, workerNioEventLoopGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel?>() {
                //                @Throws(Exception::class)
                override protected fun initChannel(ch: SocketChannel?) {
                    val pipeline: ChannelPipeline = ch!!.pipeline()
                    pipeline.addLast(Decoder())
                    pipeline.addLast(Encoder())
                    pipeline.addLast(eventBus?.let { FromRemoteHandler(inboundChannelGroup, it) })
                }
            })
        logger.debug("node listen on port {}", port)
        try {
            serverBootstrap.bind(port).sync()
        } catch (e: InterruptedException) {
            throw ConnectorException("failed to bind port", e)
        }
    }

    override fun sendRequestVote(rpc: RequestVoteRpc, destinationEndpoints: Collection<NodeEndpoint>) {
        for (endpoint in destinationEndpoints) {
            logger.debug("send {} to node {}", rpc, endpoint.id)
            executorService.execute {
                getChannel(endpoint).writeRequestVoteRpc(rpc)
            }
        }
    }

    private fun logException(e: Throwable) {
        if (e is ChannelConnectException) {
            logger.warn(e.message)
        } else {
            logger.warn("failed to process channel", e)
        }
    }

    override fun replyRequestVote(result: RequestVoteResult, rpcMessage: RequestVoteRpcMessage) {
        logger.debug("reply {} to node {}", result, rpcMessage.sourceNodeId)
        try {
            rpcMessage.channel?.writeRequestVoteResult(result)
        } catch (e: Exception) {
            logException(e)
        }
    }

    override fun sendAppendEntries(rpc: AppendEntriesRpc, destinationEndpoint: NodeEndpoint) {
        logger.debug("send {} to node {}", rpc, destinationEndpoint.id)
        executorService.execute {
            getChannel(destinationEndpoint).writeAppendEntriesRpc(rpc)
        }
    }

    override fun replyAppendEntries(result: AppendEntriesResult, rpcMessage: AppendEntriesRpcMessage) {
        logger.debug("reply {} to node {}", result, rpcMessage.sourceNodeId)
        try {
            rpcMessage.channel?.writeAppendEntriesResult(result)
        } catch (e: Exception) {
            logException(e)
        }
    }

    override fun sendInstallSnapshot(rpc: InstallSnapshotRpc, destinationEndpoint: NodeEndpoint) {
        logger.debug("send {} to node {}", rpc, destinationEndpoint.id)
        try {
            getChannel(destinationEndpoint).writeInstallSnapshotRpc(rpc)
        } catch (e: java.lang.Exception) {
            logException(e)
        }
    }

    /**
     * Reply install snapshot result.
     *
     * @param result result
     * @param rpcMessage rpc message
     */
    override fun replyInstallSnapshot(result: InstallSnapshotResult, rpcMessage: InstallSnapshotRpcMessage) {
        logger.debug("reply {} to node {}", result, rpcMessage.sourceNodeId)
        try {
            rpcMessage.channel!!.writeInstallSnapshotResult(result)
        } catch (e: java.lang.Exception) {
            logException(e)
        }
    }

    override fun resetChannels() {
        inboundChannelGroup.closeAll()
    }

    private fun getChannel(endpoint: NodeEndpoint): Channel {
        return outboundChannelGroup.getOrConnect(endpoint.id, endpoint.address)
    }

    override fun close() {
        logger.debug("close connector")
        inboundChannelGroup.closeAll()
        outboundChannelGroup.closeAll()
        bossNioEventLoopGroup.shutdownGracefully()
        if (!workerGroupShared) {
            workerNioEventLoopGroup.shutdownGracefully()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NioConnector::class.java)
    }

    init {
        outboundChannelGroup =
            eventBus?.let { selfNodeId?.let { it1 ->
                OutboundChannelGroup(
                    workerNioEventLoopGroup, it,
                    it1, logReplicationInterval
                )
            } }!!
    }
}


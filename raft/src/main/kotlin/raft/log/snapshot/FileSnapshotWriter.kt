package raft.log.snapshot

import raft.node.NodeEndpoint
import java.io.*


class FileSnapshotWriter constructor(
    output: OutputStream?,
    lastIncludedIndex: Int,
    lastIncludedTerm: Int,
    lastConfig: Set<NodeEndpoint>
) :
    AutoCloseable {
    private val output: DataOutputStream

    constructor(file: File?, lastIncludedIndex: Int, lastIncludedTerm: Int, lastConfig: Set<NodeEndpoint>) : this(
        DataOutputStream(FileOutputStream(file)),
        lastIncludedIndex,
        lastIncludedTerm,
        lastConfig
    ) {
    }

    fun getOutput(): OutputStream {
        return output
    }

    @Throws(IOException::class)
    fun write(data: ByteArray?) {
        output.write(data)
    }

    @Throws(IOException::class)
    override fun close() {
        output.close()
    }

    init {
        this.output = DataOutputStream(output)
        val headerBytes: ByteArray = ByteArray(1)
//            Protos.SnapshotHeader.newBuilder()
//            .setLastIndex(lastIncludedIndex)
//            .setLastTerm(lastIncludedTerm)
//            .addAllLastConfig(
//                lastConfig.stream()
//                    .map(Function<NodeEndpoint, Any> { e: NodeEndpoint ->
//                        Protos.NodeEndpoint.newBuilder()
//                            .setId(e.getId().getValue())
//                            .setHost(e.getHost())
//                            .setPort(e.getPort())
//                            .build()
//                    })
//                    .collect(Collectors.toList())
//            )
//            .build().toByteArray()
        this.output.writeInt(headerBytes.size)
        this.output.write(headerBytes)
    }
}


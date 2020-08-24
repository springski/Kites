import java.lang.IllegalStateException

class GroupMember(endpoint: NodeEndpoint) {

    var endpoint = endpoint // todo change to val
    var replicatingState = ReplicatingState()

    init {
        endpoint
    }

    constructor(endpoint: NodeEndpoint, replicatingState: ReplicatingState) {
       this.endpoint = endpoint
       this.replicatingState = replicatingState
    }

    fun getNextIndex(): Int {
        return ensureReplicatingState().getNextIndex()
    }

    fun getMatchIndex(): Int {
        return ensureReplicatingState().getMatchIndex()
    }

    fun ensureReplicatingState() : ReplicatingState {
        if (replicatingState = null) {
            throw IllegalStateException("Replication state not set.")
        }
        return replicatingState
    }
}
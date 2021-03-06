package raft.node

class NodeEndpoint(id: String, host: String, port: Int) {
    val id = NodeId(id)
    val address = Address(host, port)
}

class Address(host: String, port: Int) {

    val host: String

    var port: Int = 0

    init {
        this.host = host
        this.port = port
    }

    public override fun toString(): String {
        return ("Address{" +
                "host='" + host + '\''.toString() +
                ", port=" + port +
                '}'.toString())
    }
}
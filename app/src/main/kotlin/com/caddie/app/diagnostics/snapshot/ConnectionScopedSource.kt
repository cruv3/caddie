package com.caddie.app.diagnostics.snapshot

/** Reuses a derived source for the lifetime of one exact connection instance. */
class ConnectionScopedSource<C : Any, S : Any>(
    private val factory: (C) -> S,
) {
    private var connection: C? = null
    private var source: S? = null

    @Synchronized
    fun sourceFor(requestedConnection: C): S {
        if (requestedConnection !== connection || source == null) {
            connection = requestedConnection
            source = factory(requestedConnection)
        }
        return requireNotNull(source)
    }

    @Synchronized
    fun clear(disconnectedConnection: C) {
        if (connection === disconnectedConnection) {
            connection = null
            source = null
        }
    }
}

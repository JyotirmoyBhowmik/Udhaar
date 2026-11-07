package com.jyotirmoyb.udhaar.sync

class HttpSyncCloudTransport(
    private val apiClient: ApiClient
) : SyncCloudTransport {

    override suspend fun pushMutations(request: SyncPushRequest): SyncPushResponse {
        return apiClient.pushSync(request)
    }

    override suspend fun pullDeltas(cursor: Long, limit: Int): SyncPullResponse {
        return apiClient.pullSync(cursor, limit)
    }
}

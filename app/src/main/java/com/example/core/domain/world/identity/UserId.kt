package com.example.core.domain.world.identity

/**
 * Strongly-typed user identity identifier.
 * Prevents stringly-typed IDs and hardcoded placeholders.
 */
@JvmInline
value class UserId(val value: String) {
    init {
        require(value.isNotBlank()) { "UserId cannot be blank" }
        require(!value.contains(" ")) { "UserId cannot contain whitespace: '$value'" }
        require(value.length in 3..64) { "UserId length must be between 3 and 64 characters: '$value'" }
    }

    override fun toString(): String = value

    companion object {
        val DEFAULT_LOCAL = UserId("user_local_owner")
    }
}

/**
 * Pluggable provider for retrieving active user identity context.
 */
interface UserIdentityProvider {
    suspend fun getActiveUserId(): UserId
    suspend fun getAvailableUserIds(): Set<UserId>
}

/**
 * Default local-first single-tenant identity provider.
 */
class DefaultUserIdentityProvider(
    private val activeUserId: UserId = UserId.DEFAULT_LOCAL
) : UserIdentityProvider {
    override suspend fun getActiveUserId(): UserId = activeUserId
    override suspend fun getAvailableUserIds(): Set<UserId> = setOf(activeUserId)
}

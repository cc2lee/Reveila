package com.reveila.system

expect interface Principal {
    fun getName(): String
}

expect class UserPrincipal(name: String) : Principal {
    override fun getName(): String
}

expect class RolePrincipal(name: String) : Principal {
    override fun getName(): String
}

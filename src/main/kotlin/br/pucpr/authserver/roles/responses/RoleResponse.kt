package br.pucpr.authserver.roles.responses

import br.pucpr.authserver.roles.Role

data class RoleResponse(
    val name: String,
    val description: String,
) {
    constructor(role: Role) : this(role.name, role.description)
}

package br.pucpr.authserver.roles.requests

import br.pucpr.authserver.roles.Role
import jakarta.validation.constraints.NotBlank

data class CreateRoleRequest(
    @NotBlank
    val name: String?,

    @NotBlank
    val description: String?
) {
    fun toRole() = Role(name = name!!, description = description!!)
}
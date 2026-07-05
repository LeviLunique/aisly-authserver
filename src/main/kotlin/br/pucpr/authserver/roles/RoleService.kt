package br.pucpr.authserver.roles

import br.pucpr.authserver.exceptions.BadRequestException
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class RoleService(val repository: RoleRepository) {
    fun insert(role: Role): Role {
        role.name = role.name.uppercase()
        if (repository.findByName(role.name) != null) {
            throw BadRequestException("Role ${role.name} already exists.")
        }
        return repository.save(role)
    }

    fun findAll() = repository.findAll(Sort.by("name").ascending())
}
package br.pucpr.authserver.users

import br.pucpr.authserver.exceptions.NotFoundException
import br.pucpr.authserver.exceptions.UnauthorizedException
import br.pucpr.authserver.exceptions.BadRequestException
import br.pucpr.authserver.integration.AccountDeletionNotifier
import br.pucpr.authserver.roles.RoleRepository
import br.pucpr.authserver.security.Jwt
import br.pucpr.authserver.security.PasswordHasher
import br.pucpr.authserver.users.responses.LoginResponse
import br.pucpr.authserver.users.responses.UserResponse
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class UserService(
    val repository: UserRepository,
    val roleRepository: RoleRepository,
    val jwt: Jwt,
    val passwordHasher: PasswordHasher,
    val accountDeletionNotifier: AccountDeletionNotifier,
) {
    fun insert(user: User): User {
        if (repository.findByEmail(user.email) != null) {
            throw BadRequestException("User already exists")
        }
        // Aisly deviation: never persist the plain-text password.
        user.password = passwordHasher.hash(user.password)
        return repository.save(user)
    }

    fun findAll(dir: SortDir = SortDir.ASC) = when (dir) {
        SortDir.ASC -> repository.findAll(Sort.by("name").ascending())
        SortDir.DESC -> repository.findAll(Sort.by("name").descending())
    }

    fun findByIdOrNull(id: Long) = repository.findByIdOrNull(id)
    fun findById(id: Long) = repository.findByIdOrNull(id) ?: throw NotFoundException(id)

    fun delete(id: Long) {
        val user = findById(id)
        if (user.isAdmin() && repository.findByRole("ADMIN").size == 1) {
            throw BadRequestException("Cannot delete the last admin")
        }
        // Aisly deviation: confirm the backend removed the user's data before
        // deleting the account. A webhook failure propagates and aborts the
        // deletion so it can be retried without orphaning data.
        accountDeletionNotifier.notifyUserDeleted(user.id.toString())
        repository.delete(user)
        log.info("User $id deleted successfully")
    }

    fun findByRole(role: String) = repository.findByRole(role)

    fun addRole(id: Long, roleName: String): Boolean {
        val upperRole = roleName.uppercase()
        val user = findById(id)
        if (user.roles.any { it.name == upperRole }) return false

        val role = roleRepository.findByName(upperRole) ?: throw BadRequestException("Role $upperRole not found")

        user.roles.add(role)
        repository.save(user)
        log.info("User $id successfully added to role $role")
        return true
    }

    fun update(id: Long, name: String): User? {
        val user = findById(id)
        if (user.name == name) {
            return null
        }
        user.name = name
        repository.save(user)
        return user
    }

    fun login(email: String, password: String): LoginResponse {
        val user = repository.findByEmail(email) ?: throw UnauthorizedException("User $email not found")

        if (!passwordHasher.verify(password, user.password))
            throw UnauthorizedException("Invalid password")

        log.info("User ${user.id} is logged in")
        return LoginResponse(
            token = jwt.createToken(user),
            UserResponse(user)
        )
    }

    companion object {
        val log = LoggerFactory.getLogger(UserService::class.java)
    }
}
package br.pucpr.authserver.users

import br.pucpr.authserver.exceptions.ForbiddenException
import br.pucpr.authserver.security.UserToken
import br.pucpr.authserver.users.requests.CreateUserRequest
import br.pucpr.authserver.users.requests.LoginRequest
import br.pucpr.authserver.users.requests.UpdateUserRequest
import br.pucpr.authserver.users.responses.UserResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users")
class UserController(val service: UserService) {
    @GetMapping
    fun list(
        @RequestParam sortDir: String? = null,
        @RequestParam role: String? = null
    ): ResponseEntity<List<UserResponse>> {
        val users = if (role != null) service.findByRole(role)
        else service.findAll(SortDir.find(sortDir ?: "ASC"))
        return users
            .map { UserResponse(it) }
            .let { ResponseEntity.ok(it) }
    }

    @PostMapping
    fun insert(
        @Valid @RequestBody user: CreateUserRequest
    ) = service.insert(user.toUser())
        .let { UserResponse(it) }
        .let { ResponseEntity.status(HttpStatus.CREATED).body(it) }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody login: LoginRequest
    ) = service.login(login.email!!, login.password!!)

    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: Long
    ) = service.findById(id)
        .let { UserResponse(it) }
        .let { ResponseEntity.ok(it) }

    @SecurityRequirement(name = "jwt-auth")
    @PatchMapping("/{id}")
    fun updateUser(
        @PathVariable id: Long,
        @Valid @RequestBody user: UpdateUserRequest,
        auth: Authentication
    ): ResponseEntity<UserResponse> {
        val token = auth.principal as? UserToken ?: throw ForbiddenException()
        if (token.id != id && !token.isAdmin) {
            throw ForbiddenException("Update is not allowed")
        }
        return service.update(id, user.name!!)
            ?.let { UserResponse(it) }
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.noContent().build()
    }

    @SecurityRequirement(name = "jwt-auth")
    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
        auth: Authentication
    ): ResponseEntity<Void> {
        // Aisly deviation: allow self-deletion (token.id == id) OR an admin.
        val token = auth.principal as? UserToken ?: throw ForbiddenException()
        if (token.id != id && !token.isAdmin) {
            throw ForbiddenException("Delete is not allowed")
        }
        service.delete(id)
        return ResponseEntity.ok().build()
    }

    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "jwt-auth")
    @PutMapping("/{id}/roles/{role}")
    fun grant(
        @PathVariable id: Long,
        @PathVariable role: String
    ): ResponseEntity<Void> = service.addRole(id, role)
        .let {
            if (it) ResponseEntity.ok().build()
            else ResponseEntity.noContent().build()
        }
}
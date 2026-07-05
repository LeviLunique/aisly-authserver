package br.pucpr.authserver

import br.pucpr.authserver.roles.Role
import br.pucpr.authserver.roles.RoleRepository
import br.pucpr.authserver.security.PasswordHasher
import br.pucpr.authserver.users.User
import br.pucpr.authserver.users.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component

@Component
class Bootstrapper(
    val rolesRepository: RoleRepository,
    val userRepository: UserRepository,
    val passwordHasher: PasswordHasher,
    @Value("\${aisly.bootstrap.admin-email:}") private val bootstrapAdminEmail: String,
    @Value("\${aisly.bootstrap.admin-password:}") private val bootstrapAdminPassword: String,
) : ApplicationListener<ContextRefreshedEvent> {
    override fun onApplicationEvent(event: ContextRefreshedEvent) {
        val adminRole =
            rolesRepository.findByName("ADMIN") ?: rolesRepository
                .save(Role(name = "ADMIN", description = "System Administrator"))
        rolesRepository.findByName("PREMIUM") ?: rolesRepository
            .save(Role(name = "PREMIUM", description = "Premium user"))

        if (userRepository.findByRole("ADMIN").isEmpty()) {
            val email = bootstrapAdminEmail.trim()
            val password = bootstrapAdminPassword.trim()

            if (email.isEmpty() || password.isEmpty()) {
                log.warn("Skipping bootstrap admin creation: AISLY_BOOTSTRAP_ADMIN_EMAIL/PASSWORD are not both set.")
                return
            }

            val admin = User(
                email = email,
                password = passwordHasher.hash(password),
                name = "Auth Server Administrator",
            )
            admin.roles.add(adminRole)
            userRepository.save(admin)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(Bootstrapper::class.java)
    }
}

package br.pucpr.authserver.users

import br.pucpr.authserver.roles.Role
import jakarta.persistence.*

@Entity
@Table(name = "UserTable")
class User (
    @Id @GeneratedValue
    var id: Long? = null,

    @Column(nullable = false)
    var email: String,

    var password: String,
    var name: String = "",

    @ManyToMany
    @JoinTable(
        name = "UserRole",
        joinColumns = [JoinColumn(name = "idUser")],
        inverseJoinColumns = [JoinColumn(name = "idRole")]
    )
    var roles: MutableSet<Role> = mutableSetOf()
) {
    @Transient
    fun isAdmin() = roles.any { it.name == "ADMIN" }
}
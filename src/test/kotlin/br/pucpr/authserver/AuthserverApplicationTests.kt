package br.pucpr.authserver

import br.pucpr.authserver.users.UserRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration tests over the Aisly-adapted AuthServer. MockMvc does not apply
 * the servlet context-path (`/api`), so paths here are relative to it
 * (e.g. `/users`, not `/api/users`). The `local` profile runs on in-memory H2.
 * AISLY_BASE_URL is unset, so the delete-data webhook is skipped (not an error).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(
    properties = [
        "aisly.bootstrap.admin-email=admin@example.test",
        "aisly.bootstrap.admin-password=test-admin-password-not-a-secret",
    ]
)
class AuthserverApplicationTests {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    private fun createUserBody(email: String, password: String = "s3cret@Pass", name: String = "Test User") =
        """{"email":"$email","password":"$password","name":"$name"}"""

    private fun loginBody(email: String, password: String) =
        """{"email":"$email","password":"$password"}"""

    @Test
    fun contextLoads() {
    }

    @Test
    fun `create user returns 201 with UserResponse`() {
        mockMvc.perform(
            post("/users").contentType(MediaType.APPLICATION_JSON)
                .content(createUserBody("alice@aisly.dev"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.email").value("alice@aisly.dev"))
            .andExpect(jsonPath("$.name").value("Test User"))
            .andExpect(jsonPath("$.id").isNumber)
    }

    @Test
    fun `login returns 200 with a non-empty token`() {
        val email = "bob@aisly.dev"
        mockMvc.perform(
            post("/users").contentType(MediaType.APPLICATION_JSON)
                .content(createUserBody(email))
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "s3cret@Pass"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value(email))
    }

    @Test
    fun `login with wrong password returns 401`() {
        val email = "carol@aisly.dev"
        mockMvc.perform(
            post("/users").contentType(MediaType.APPLICATION_JSON)
                .content(createUserBody(email))
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "wrong@Pass9"))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET user by id returns 200`() {
        val email = "dave@aisly.dev"
        val created = mockMvc.perform(
            post("/users").contentType(MediaType.APPLICATION_JSON)
                .content(createUserBody(email))
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val id = Regex("\"id\":(\\d+)").find(created)!!.groupValues[1]

        mockMvc.perform(get("/users/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(email))
    }

    @Test
    fun `self-deletion works and the account can no longer log in`() {
        val email = "erin@aisly.dev"
        val password = "s3cret@Pass"
        val created = mockMvc.perform(
            post("/users").contentType(MediaType.APPLICATION_JSON)
                .content(createUserBody(email, password))
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val id = Regex("\"id\":(\\d+)").find(created)!!.groupValues[1]

        val loginJson = mockMvc.perform(
            post("/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password))
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val token = Regex("\"token\":\"([^\"]+)\"").find(loginJson)!!.groupValues[1]

        // Self-deletion: authenticated as the very user being deleted. The
        // webhook is skipped because AISLY_BASE_URL is unset.
        mockMvc.perform(delete("/users/$id").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)

        // The account is gone: login now fails with 401.
        mockMvc.perform(
            post("/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `bootstrapper creates an admin user`() {
        assertTrue(userRepository.findByRole("ADMIN").isNotEmpty())
    }
}

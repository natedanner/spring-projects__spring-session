package sample;

import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestContainersConfig.class)
public class JdbcJsonAttributeTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper objectMapper;

	ObjectMapper objectMapperWithModules;

	@Autowired
	JdbcTemplate jdbcClient;

	@BeforeEach
	void setup() {
		ObjectMapper copy = this.objectMapper.copy();
		copy.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
		this.objectMapperWithModules = copy;
		this.jdbcClient.execute("DELETE FROM spring_session_attributes");
		this.jdbcClient.execute("DELETE FROM spring_session");
	}

	@Test
	void loginShouldSaveSecurityContextAsJson() throws Exception {
		Cookie sessionCookie = this.mvc.perform(formLogin().user("user").password("password"))
			.andExpect(authenticated())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");
		String sessionId = new String(Base64.getDecoder().decode(sessionCookie.getValue()));
		Object attributeBytes = this.jdbcClient.queryForObject("""
				SELECT attribute_bytes::text FROM spring_session_attributes
				INNER JOIN spring_session s ON s.primary_id = session_primary_id
				WHERE attribute_name = 'SPRING_SECURITY_CONTEXT'
				AND s.session_id = ?
				""", Object.class, sessionId);
		SecurityContext securityContext = this.objectMapperWithModules.readValue((String) attributeBytes,
				SecurityContext.class);
		assertThat(securityContext).isNotNull();
		assertThat(securityContext.getAuthentication().getName()).isEqualTo("user");
	}

	@Test
	void loginWhenQueryUsingJsonbOperatorThenReturns() throws Exception {
		this.mvc.perform(formLogin().user("user").password("password")).andExpect(authenticated());
		Object attributeBytes = this.jdbcClient.queryForObject("""
				SELECT attribute_bytes::text FROM spring_session_attributes
				WHERE attribute_bytes -> 'authentication' -> 'principal' ->> 'username' = 'user'
				""", Object.class);
		SecurityContext securityContext = this.objectMapperWithModules.readValue((String) attributeBytes,
				SecurityContext.class);
		assertThat(securityContext).isNotNull();
		assertThat(securityContext.getAuthentication().getName()).isEqualTo("user");
	}

}
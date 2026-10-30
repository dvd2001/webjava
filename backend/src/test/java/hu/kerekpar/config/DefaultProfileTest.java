package hu.kerekpar.config;

import static hu.kerekpar.support.TestAuth.MANAGER;
import static hu.kerekpar.support.TestAuth.bearer;
import static hu.kerekpar.support.TestAuth.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.kerekpar.application.port.out.BikeRepository;
import hu.kerekpar.application.port.out.CredentialsRepository;
import hu.kerekpar.application.port.out.UnitOfWork;
import hu.kerekpar.adapter.out.memory.InMemoryBikeRepository;
import hu.kerekpar.adapter.out.memory.LockingUnitOfWork;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/** Az alapértelmezett (profil nélküli) futás: memóriabeli tároló, adatbázis nélkül. */
@SpringBootTest(properties = "kerekpar.demo-data.enabled=false")
@AutoConfigureMockMvc
class DefaultProfileTest {

    @Autowired
    private ApplicationContext context;
    @Autowired
    private MockMvc mvc;

    @Test
    void usesTheInMemoryAdaptersAndNoDatabase() {
        assertThat(context.getBean(BikeRepository.class)).isInstanceOf(InMemoryBikeRepository.class);
        assertThat(context.getBean(UnitOfWork.class)).isInstanceOf(LockingUnitOfWork.class);
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBean(CredentialsRepository.class)).isNotNull();
    }

    @Test
    void demoDataCanBeSwitchedOff() throws Exception {
        String manager = login(mvc, MANAGER);

        mvc.perform(get("/api/bikes").with(bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/rentals").with(bearer(manager)))
                .andExpect(jsonPath("$.length()").value(0));
    }
}

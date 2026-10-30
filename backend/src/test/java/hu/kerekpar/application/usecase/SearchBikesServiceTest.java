package hu.kerekpar.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import hu.kerekpar.adapter.out.memory.InMemoryBikeRepository;
import hu.kerekpar.adapter.out.memory.InMemoryUserRepository;
import hu.kerekpar.application.port.in.SearchBikesUseCase;
import hu.kerekpar.application.port.in.SearchBikesUseCase.Query;
import hu.kerekpar.application.port.in.SearchBikesUseCase.SearchResult;
import hu.kerekpar.domain.exception.InvalidInputException;
import hu.kerekpar.domain.exception.UnauthenticatedException;
import hu.kerekpar.domain.model.Bike;
import hu.kerekpar.domain.model.BikeStatus;
import hu.kerekpar.domain.model.BikeType;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SearchBikesServiceTest {

    private static final String RENTER = "u2";
    private static final String MANAGER = "u1";

    private final InMemoryBikeRepository bikes = new InMemoryBikeRepository();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(SearchBikesService.class);
    private SearchBikesUseCase search;

    @BeforeEach
    void setUp() {
        bikes.save(new Bike("b1", "Hegyi-02", BikeType.MOUNTAIN, BikeStatus.MAINTENANCE));
        bikes.save(new Bike("b2", "city-10", BikeType.CITY, BikeStatus.AVAILABLE));
        bikes.save(new Bike("b3", "City-02", BikeType.CITY, BikeStatus.IN_USE));
        bikes.save(new Bike("b4", "E-bike-01", BikeType.ELECTRIC, BikeStatus.AVAILABLE));
        bikes.save(new Bike("b5", "City-01", BikeType.CITY, BikeStatus.AVAILABLE));
        search = new SearchBikesService(bikes, new InMemoryUserRepository());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private static Query query(String actor, String text, BikeType type, BikeStatus status, Integer limit) {
        return new Query(actor, text, type, status, limit);
    }

    private static List<String> names(SearchResult result) {
        return result.items().stream().map(Bike::name).toList();
    }

    @Test
    void withoutCriteriaEveryBikeIsReturnedSortedByNameIgnoringCase() {
        SearchResult result = search.execute(query(RENTER, null, null, null, null));

        assertThat(names(result)).containsExactly("City-01", "City-02", "city-10", "E-bike-01", "Hegyi-02");
        assertThat(result.total()).isEqualTo(5);
    }

    @Test
    void nameFilterIsCaseInsensitive() {
        assertThat(names(search.execute(query(RENTER, "CITY", null, null, null))))
                .containsExactly("City-01", "City-02", "city-10");
    }

    @Test
    void filtersCombine() {
        SearchResult result = search.execute(query(RENTER, "city", BikeType.CITY, BikeStatus.AVAILABLE, null));

        assertThat(names(result)).containsExactly("City-01", "city-10");
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void typeOnlyAndStatusOnly() {
        assertThat(names(search.execute(query(RENTER, null, BikeType.ELECTRIC, null, null)))).containsExactly("E-bike-01");
        assertThat(names(search.execute(query(RENTER, null, null, BikeStatus.IN_USE, null)))).containsExactly("City-02");
    }

    @Test
    void noMatchGivesAnEmptyResultNotAnError() {
        SearchResult result = search.execute(query(RENTER, "nincs-ilyen", null, null, null));

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void limitTruncatesButTotalKeepsTheFullCount() {
        SearchResult result = search.execute(query(RENTER, "city", null, null, 2));

        assertThat(names(result)).containsExactly("City-01", "City-02");
        assertThat(result.total()).isEqualTo(3);
    }

    @Test
    void resultListCannotBeUsedToChangeTheStore() {
        SearchResult result = search.execute(query(RENTER, null, null, null, null));

        assertThatThrownBy(() -> result.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(bikes.findAll()).hasSize(5);
    }

    @Test
    void searchIsReadOnly() {
        List<Bike> before = bikes.findAll();

        search.execute(query(MANAGER, "city", BikeType.CITY, null, 1));

        assertThat(bikes.findAll()).isEqualTo(before);
    }

    @Test
    void bothRolesMaySearch() {
        assertThat(search.execute(query(MANAGER, null, null, null, null)).total()).isEqualTo(5);
        assertThat(search.execute(query(RENTER, null, null, null, null)).total()).isEqualTo(5);
    }

    @Test
    void unknownOrMissingActorIsUnauthenticated() {
        for (String actor : new String[] {"u999", null, "", " "}) {
            assertThatThrownBy(() -> search.execute(query(actor, null, null, null, null)))
                    .isInstanceOf(UnauthenticatedException.class);
        }
    }

    @Test
    void invalidCriteriaAreRejectedByTheDomainRules() {
        assertThatThrownBy(() -> search.execute(query(RENTER, "x".repeat(51), null, null, null)))
                .isInstanceOfSatisfying(InvalidInputException.class, e -> assertThat(e.code()).isEqualTo("INVALID_SEARCH_TEXT"));
        assertThatThrownBy(() -> search.execute(query(RENTER, null, null, null, 0)))
                .isInstanceOfSatisfying(InvalidInputException.class, e -> assertThat(e.code()).isEqualTo("INVALID_LIMIT"));
        assertThatThrownBy(() -> search.execute(query(RENTER, null, null, null, 101)))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void actorIsCheckedBeforeTheCriteria() {
        assertThatThrownBy(() -> search.execute(query("u999", "x".repeat(51), null, null, 0)))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void specialCharactersInTheTextAreLiteral() {
        bikes.save(new Bike("b6", "Teszt_50%", BikeType.CITY, BikeStatus.AVAILABLE));

        assertThat(names(search.execute(query(RENTER, "%", null, null, null)))).containsExactly("Teszt_50%");
        assertThat(names(search.execute(query(RENTER, "_5", null, null, null)))).containsExactly("Teszt_50%");
        assertThat(search.execute(query(RENTER, "' OR 1=1 --", null, null, null)).items()).isEmpty();
    }

    @Test
    void theSearchIsLoggedWithoutControlCharactersFromTheUserInput() {
        search.execute(query(RENTER, "ci\r\nty\u001b[31m", null, null, null));

        assertThat(appender.list).singleElement().satisfies(event -> {
            String line = event.getFormattedMessage();
            assertThat(line).contains("actor=u2").contains("találat");
            assertThat(line).doesNotContain("\n").doesNotContain("\r").doesNotContain("\u001b");
        });
    }

    @Test
    void longSearchTextIsTruncatedInTheLog() {
        search.execute(query(RENTER, "a".repeat(50), null, null, null));

        assertThat(appender.list.get(0).getFormattedMessage()).doesNotContain("a".repeat(50));
    }
}

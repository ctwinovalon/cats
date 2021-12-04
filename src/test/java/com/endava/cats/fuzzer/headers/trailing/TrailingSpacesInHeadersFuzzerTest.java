package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TrailingSpacesInHeadersFuzzerTest {
    private TrailingSpacesInHeadersFuzzer trailingSpacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingSpacesInHeadersFuzzer = new TrailingSpacesInHeadersFuzzer(new CatsUtil(null), null, null);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingSpacesInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo(" ");
    }
}

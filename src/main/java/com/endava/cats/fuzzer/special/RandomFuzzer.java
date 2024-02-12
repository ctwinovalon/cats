package com.endava.cats.fuzzer.special;

import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.fuzzer.special.mutators.api.CustomMutator;
import com.endava.cats.fuzzer.special.mutators.api.CustomMutatorConfig;
import com.endava.cats.fuzzer.special.mutators.api.CustomMutatorKeywords;
import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Iterators;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fuzzer intended for continuous fuzzing. It will randomly choose fields to fuzz and mutators to apply.
 * The Fuzzer will stop after one of the supplied stopXXX conditions is met: time elapsed, errors occurred or tests executed.
 */
@Singleton
@SpecialFuzzer
public class RandomFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(RandomFuzzer.class);
    private final Iterator<String> cycle = Iterators.cycle("\\", "|", "/", "-");
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;
    private final ExecutionStatisticsListener executionStatisticsListener;
    private final MatchArguments matchArguments;
    private final StopArguments stopArguments;
    private final ReportingArguments reportingArguments;
    private final FilesArguments filesArguments;
    private final CatsUtil catsUtil;
    private final Instance<Mutator> mutators;

    @Inject
    public RandomFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener,
                        ExecutionStatisticsListener executionStatisticsListener,
                        MatchArguments matchArguments, Instance<Mutator> mutators,
                        StopArguments stopArguments, ReportingArguments reportingArguments,
                        FilesArguments filesArguments, CatsUtil catsUtil) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
        this.executionStatisticsListener = executionStatisticsListener;
        this.matchArguments = matchArguments;
        this.mutators = mutators;
        this.stopArguments = stopArguments;
        this.reportingArguments = reportingArguments;
        this.filesArguments = filesArguments;
        this.catsUtil = catsUtil;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.error("Skipping fuzzer as payload is empty");
            return;
        }
        List<Mutator> mutatorsToRun = this.getMutators();

        if (mutatorsToRun.isEmpty()) {
            logger.error("No Mutators to run! Enable debug for more details.");
            return;
        }

        long startTime = System.currentTimeMillis();

        boolean shouldStop = false;
        Set<String> allCatsFields = data.getAllFieldsByHttpMethod();

        this.startProgress(data);

        while (!shouldStop) {
            String targetField = CatsUtil.selectRandom(allCatsFields);

            Mutator selectedRandomMutator = CatsUtil.selectRandom(mutatorsToRun);
            String mutatedPayload = selectedRandomMutator.mutate(data.getPayload(), targetField);

            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .fuzzer(this)
                            .fuzzingData(data)
                            .logger(logger)
                            .payload(mutatedPayload)
                            .scenario("Send a random payload mutating field [%s] with [%s] mutator".formatted(targetField, selectedRandomMutator.name()))
                            .expectedSpecificResponseCode("a response that doesn't shouldStop given arguments")
                            .responseProcessor(this::processResponse)
                            .build());

            updateProgress(data);
            shouldStop = stopArguments.shouldStop(executionStatisticsListener.getErrors(), testCaseListener.getCurrentTestCaseNumber(), startTime);
        }
    }

    private void updateProgress(FuzzingData data) {
        if (!reportingArguments.isSummaryInConsole()) {
            return;
        }
        if (testCaseListener.getCurrentTestCaseNumber() % 20 == 0) {
            ConsoleUtils.renderSameRow(data.getPath() + "  " + data.getMethod(), cycle.next());
        }
    }

    private void startProgress(FuzzingData data) {
        if (!reportingArguments.isSummaryInConsole()) {
            return;
        }
        testCaseListener.notifySummaryObservers(data.getPath(), data.getMethod().name(), 0d);
        ConsoleUtils.renderSameRow(data.getPath() + "  " + data.getMethod(), cycle.next());
    }

    void processResponse(CatsResponse catsResponse, FuzzingData fuzzingData) {
        if (matchArguments.isMatchResponse(catsResponse)) {
            testCaseListener.reportResultError(logger, fuzzingData, "Response matches arguments", "Response matches" + matchArguments.getMatchString());
        } else {
            testCaseListener.skipTest(logger, "Skipping test as response does not match given matchers!");
        }
    }

    private List<Mutator> getMutators() {
        if (filesArguments.getMutatorsFolder() == null) {
            return mutators.stream().toList();
        }

        return this.parseMutators();
    }

    private List<Mutator> parseMutators() {
        List<Mutator> customMutators = new ArrayList<>();

        File mutatorsFolder = filesArguments.getMutatorsFolder();
        File[] customMutatorsFiles = mutatorsFolder.listFiles();

        if (customMutatorsFiles == null) {
            logger.error("Invalid custom Mutators folder {}", filesArguments.getMutatorsFolder().getAbsolutePath());
            return Collections.emptyList();
        }

        for (File customMutatorFile : Objects.requireNonNull(customMutatorsFiles)) {
            try {
                Map<String, Object> customMutator = parseYamlAsSimpleMap(customMutatorFile.getCanonicalPath());

                CustomMutatorConfig config = this.createConfig(customMutator);
                customMutators.add(new CustomMutator(config, catsUtil));
            } catch (Exception e) {
                logger.debug("There was a problem parsing {}: {}", customMutatorFile.getAbsolutePath(), e.toString());
            }
        }

        return customMutators;
    }

    CustomMutatorConfig createConfig(Map<String, Object> customMutator) {
        String name = String.valueOf(
                customMutator.get(
                        CustomMutatorKeywords.NAME.name().toLowerCase(Locale.ROOT)
                )
        );

        CustomMutatorConfig.Type type = CustomMutatorConfig.Type.valueOf(
                String.valueOf(
                        customMutator.get(
                                CustomMutatorKeywords.TYPE.name().toLowerCase(Locale.ROOT)
                        )
                ).toUpperCase(Locale.ROOT)
        );

        List<Object> values = (List<Object>) customMutator.get(
                CustomMutatorKeywords.VALUES.name().toLowerCase(Locale.ROOT)
        );

        return new CustomMutatorConfig(name, type, values);
    }

    static Map<String, Object> parseYamlAsSimpleMap(String yaml) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (Reader reader = new InputStreamReader(new FileInputStream(yaml), StandardCharsets.UTF_8)) {
            JsonNode node = mapper.reader().readTree(reader);
            return mapper.convertValue(node, new TypeReference<>() {
            });
        }
    }

    @Override
    public String description() {
        return "continuously fuzz random fields with random values based on registered mutators";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@HeaderFuzzer
@ConditionalOnProperty(value = "fuzzer.headers.ControlCharsOnlyInHeadersFuzzer.enabled", havingValue = "true")
public class ControlCharsOnlyInHeadersFuzzer extends InvisibleCharsOnlyFuzzer {

    @Autowired
    public ControlCharsOnlyInHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        super(sc, lr);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "replace value with control chars";
    }

    @Override
    List<String> getInvisibleChars() {
        return CatsUtil.CONTROL_CHARS_HEADERS;
    }

    @Override
    protected boolean matchResponseSchema() {
        return false;
    }
}
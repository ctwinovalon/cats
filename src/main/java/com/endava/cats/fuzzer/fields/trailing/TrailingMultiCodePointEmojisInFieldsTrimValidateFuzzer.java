package com.endava.cats.fuzzer.fields.trailing;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.EmojiFuzzer;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.TrimAndValidate;
import com.endava.cats.fuzzer.fields.base.InvisibleCharsBaseTrimValidateFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import javax.inject.Singleton;
import java.util.List;

@Singleton
@FieldFuzzer
@EmojiFuzzer
@TrimAndValidate
public class TrailingMultiCodePointEmojisInFieldsTrimValidateFuzzer extends InvisibleCharsBaseTrimValidateFuzzer {

    protected TrailingMultiCodePointEmojisInFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values trailed with multi code point emojis";
    }

    @Override
    public List<String> getInvisibleChars() {
        return catsUtil.getMultiCodePointEmojis();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.trail();
    }
}
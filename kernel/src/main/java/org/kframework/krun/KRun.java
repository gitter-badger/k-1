// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.krun;

import com.google.common.io.Files;
import org.kframework.Rewriter;
import org.kframework.attributes.Source;
import org.kframework.kil.Attributes;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kore.K;
import org.kframework.transformation.Transformation;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.definition.Module;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.Function;

/**
 * The KORE-based KRun
 */
public class KRun implements Transformation<Void, Void> {

    private final KExceptionManager kem;
    private final FileUtil files;

    public KRun(KExceptionManager kem, FileUtil files) {
        this.kem = kem;
        this.files = files;
    }

    public int run(CompiledDefinition compiledDef, KRunOptions options, Function<Module, Rewriter> rewriterGenerator) {
        try {
            String pgmFileName = options.configurationCreation.pgm();
            if (!options.configurationCreation.term()) {
                KExceptionManager.criticalError("Unsupported options: term=false");
            }

            String pgm = Files.toString(new File(pgmFileName), Charset.defaultCharset());
            K program = compiledDef.getProgramParser().apply(pgm, Source.apply(pgm));

            Rewriter rewriter = rewriterGenerator.apply(compiledDef.executionModule());

            K result = rewriter.execute(program);

            System.err.println("result:" + result);
            return 0;
        } catch (IOException e) {
            KExceptionManager.criticalError(e.getMessage(), e);
            return 1;
        }
    }

    @Override
    public Void run(Void aVoid, Attributes attrs) {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }
}

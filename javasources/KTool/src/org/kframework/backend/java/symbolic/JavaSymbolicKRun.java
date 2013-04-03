package org.kframework.backend.java.symbolic;

import edu.uci.ics.jung.graph.DirectedGraph;
import org.kframework.compile.utils.RuleCompilerSteps;
import org.kframework.kil.Definition;
import org.kframework.kil.Rule;
import org.kframework.kil.Term;
import org.kframework.kil.matchers.MatcherException;
import org.kframework.krun.api.*;
import org.kframework.utils.BinaryLoader;

import java.io.*;
import java.util.Set;


/**
 * Created with IntelliJ IDEA.
 * User: andrei
 * Date: 3/15/13
 * Time: 12:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class JavaSymbolicKRun implements KRun {
    @Override
    public KRunResult<KRunState> run(Term cfg) throws Exception {
        try {
            InputStream inputStream = new BufferedInputStream(
                    new FileInputStream(JavaSymbolicBackend.DEFINITION_FILENAME));
            Definition definition = (Definition) BinaryLoader.fromBinary(inputStream);
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            SymbolicRewriter symbolicRewriter = new SymbolicRewriter(definition);
            return new KRunResult<KRunState>(new KRunState(symbolicRewriter.rewrite(cfg)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (MatcherException e) {
            e.printStackTrace();
        }

        return null;
    }

	@Override
	public KRunResult<SearchResults> search(Integer bound, Integer depth, SearchType searchType, Rule pattern, Term cfg, RuleCompilerSteps compilationInfo) throws Exception {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

//	@Override
    public KRunResult<SearchResults> search(
            Integer bound,
            Integer depth,
            SearchType searchType,
            Rule pattern,
            Term cfg,
            Set<String> varNames) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public KRunResult<DirectedGraph<KRunState, Transition>> modelCheck(Term formula, Term cfg) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public KRunResult<KRunState> step(Term cfg, int steps) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public KRunDebugger debug(Term cfg) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public KRunDebugger debug(SearchResults searchResults) {
        throw new UnsupportedOperationException();
    }
}

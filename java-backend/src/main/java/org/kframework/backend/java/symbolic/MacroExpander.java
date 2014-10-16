// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kframework.backend.java.kil.CellLabel;
import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.JavaSymbolicObject;
import org.kframework.backend.java.kil.KItem;
import org.kframework.backend.java.kil.Rule;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;

import com.google.common.collect.Lists;


/**
 * Expands the macros in each rule of a definition and those in the initial
 * configuration.
 *
 * @author AndreiS
 *
 */
public class MacroExpander extends CopyOnWriteTransformer {

    public MacroExpander(TermContext context) {
        super(context);
    }

    public Definition processDefinition() {
        Definition processedDefinition = new Definition(definition.context(), definition.indexingData);
        processedDefinition.addKLabelCollection(definition.kLabels());
        processedDefinition.addFrozenKLabelCollection(definition.frozenKLabels());
        for (Rule rule : definition.rules()) {
            processedDefinition.addRule(processRule(rule));
        }
        for (Rule rule : definition.functionRules().values()) {
            processedDefinition.addRule(processRule(rule));
        }
        for (Rule rule : definition.anywhereRules().values()) {
            processedDefinition.addRule(processRule(rule));
        }
        for (Rule rule : definition.patternRules().values()) {
            processedDefinition.addRule(processRule(rule));
        }
        for (Rule rule : definition.patternFoldingRules()) {
            processedDefinition.addRule(processRule(rule));
        }
        processedDefinition.addRuleCollection(definition.macros());
        return processedDefinition;
    }

    public Rule processRule(Rule rule) {
        Term processedLeftHandSide = processTerm(rule.leftHandSide());
        Term processedRightHandSide = processTerm(rule.rightHandSide());
        List<Term> processedRequires = Lists.newArrayListWithCapacity(rule.requires().size());
        for (Term conditionItem : rule.requires()) {
            processedRequires.add(processTerm(conditionItem));
        }
        List<Term> processedEnsures = Lists.newArrayListWithCapacity(rule.ensures().size());
        for (Term conditionItem : rule.ensures()) {
            processedEnsures.add(processTerm(conditionItem));
        }
        UninterpretedConstraint processedLookups
            = (UninterpretedConstraint) expandMacro(rule.lookups());

        Map<CellLabel, Term> processedLhsOfReadCell = null;
        Map<CellLabel, Term> processedRhsOfWriteCell = null;
        if (rule.isCompiledForFastRewriting()) {
            processedLhsOfReadCell = new HashMap<>();
            for (Map.Entry<CellLabel, Term> entry : rule.lhsOfReadCell().entrySet()) {
                processedLhsOfReadCell.put(entry.getKey(), processTerm(entry.getValue()));
            }
            processedRhsOfWriteCell = new HashMap<>();
            for (Map.Entry<CellLabel, Term> entry : rule.rhsOfWriteCell().entrySet()) {
                processedRhsOfWriteCell.put(entry.getKey(), processTerm(entry.getValue()));
            }
        }

        return new Rule(
                rule.label(),
                processedLeftHandSide,
                processedRightHandSide,
                processedRequires,
                processedEnsures,
                rule.freshConstants(),
                rule.freshVariables(),
                processedLookups,
                rule.isCompiledForFastRewriting(),
                processedLhsOfReadCell,
                processedRhsOfWriteCell,
                rule.cellsToCopy(),
                rule.instructions(),
                rule,
                definition);
    }

    public Term processTerm(Term term) {
        return (Term) expandMacro(term);
    }

    /**
     * Private helper method that keeps expanding macros in a specified node
     * until no macro is found.
     *
     * @param node
     *            the specified node
     * @return the expanded node
     */
    private JavaSymbolicObject expandMacro(JavaSymbolicObject node) {
        JavaSymbolicObject expandedNode = (JavaSymbolicObject) node.accept(this);
        while (node != expandedNode) {
            node = expandedNode;
            expandedNode = (JavaSymbolicObject) node.accept(this);
        }

        return node;
    }

    @Override
    public Term transform(KItem kItem) {
        Term term = (Term) super.transform(kItem);
        return applyMacroRule(term);
    }

    private Term applyMacroRule(Term term) {
        for (Rule rule : definition.macros()) {
            Map<Variable, Term> subst = NonACPatternMatcher.match(term, rule, context);
            if (subst != null) {
                return rule.rightHandSide().substituteAndEvaluate(subst, context);
            }
        }

        return term;
    }

}

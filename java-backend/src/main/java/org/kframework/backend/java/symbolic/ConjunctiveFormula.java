// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.kframework.backend.java.builtins.BoolToken;
import org.kframework.backend.java.kil.BuiltinMap;
import org.kframework.backend.java.kil.KItem;
import org.kframework.backend.java.kil.KList;
import org.kframework.backend.java.kil.Kind;
import org.kframework.backend.java.kil.Sort;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.util.Utils;
import org.kframework.kil.ASTNode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.Pair;

import static org.kframework.backend.java.util.RewriteEngineUtils.isSubsortedEq;

/**
 * A conjunction of equalities (between terms with variables) and disjunctions
 *
 * @see org.kframework.backend.java.symbolic.Equality
 * @see org.kframework.backend.java.symbolic.DisjunctiveFormula
 */
public class ConjunctiveFormula extends Term {

    public static final String SEPARATOR = " /\\ ";

    public static ConjunctiveFormula trueFormula(TermContext context) {
        return new ConjunctiveFormula(
                Substitution.empty(),
                PersistentUniqueList.<Equality>empty(),
                PersistentUniqueList.<DisjunctiveFormula>empty(),
                TruthValue.TRUE,
                context);
    }

    private final Substitution<Variable, Term> substitution;
    private final PersistentUniqueList<Equality> equalities;
    private final PersistentUniqueList<DisjunctiveFormula> disjunctions;

    private final TruthValue truthValue;

    private final TermContext context;

    public ConjunctiveFormula(ConjunctiveFormula formula) {
        this(formula.substitution,
             formula.equalities,
             formula.disjunctions,
             formula.truthValue,
             formula.context);
    }

    public ConjunctiveFormula(
            Substitution<Variable, Term> substitution,
            PersistentUniqueList<Equality> equalities,
            PersistentUniqueList<DisjunctiveFormula> disjunctions,
            TruthValue truthValue, TermContext context) {
        super(Kind.KITEM);

        this.substitution = substitution;
        this.equalities = equalities;
        this.disjunctions = disjunctions;
        this.truthValue = truthValue;
        this.context = context;
    }

    public Substitution<Variable, Term> substitution() {
        return substitution;
    }

    public PersistentUniqueList<Equality> equalities() {
        return equalities;
    }

    public PersistentUniqueList<DisjunctiveFormula> disjunctions() {
        return disjunctions;
    }

    public TermContext termContext() {
        return context;
    }

    /**
     * Adds the side condition of a rule to this constraint. The side condition is represented
     * as a list of {@code Term}s that are expected to be equal to {@code BoolToken#TRUE}.
     */
    public ConjunctiveFormula addAll(List<Term> condition) {
        ConjunctiveFormula result = this;
        for (Term term : condition) {
            result = result.add(term, BoolToken.TRUE);
            if (result == null) {
                return null;
            }
        }
        return result;
    }

    public ConjunctiveFormula addAndSimplify(Object term) {
        return add(term).simplify();
    }

    public ConjunctiveFormula add(ConjunctiveFormula conjunction) {
        return add(conjunction.substitution)
                .addAll(conjunction.equalities)
                .addAll(conjunction.disjunctions);
    }

    public ConjunctiveFormula add(Substitution<Variable, Term> substitution) {
        return addAll(substitution.equalities(context));
    }

    public ConjunctiveFormula add(Equality equality) {
        return new ConjunctiveFormula(
                substitution,
                equalities.plus(equality),
                disjunctions,
                truthValue,
                context);
    }

    public ConjunctiveFormula add(Term leftHandSide, Term rightHandSide) {
        return add(new Equality(leftHandSide, rightHandSide, context));
    }

    public ConjunctiveFormula add(DisjunctiveFormula disjunction) {
        return new ConjunctiveFormula(
                substitution,
                equalities,
                disjunctions.plus(disjunction),
                truthValue,
                context);
    }

    @SuppressWarnings("unchecked")
    public ConjunctiveFormula add(Object term) {
        if (term instanceof ConjunctiveFormula) {
            return add((ConjunctiveFormula) term);
        } else if (term instanceof Substitution) {
            return add((Substitution) term);
        } else if (term instanceof Equality) {
            return add((Equality) term);
        } else if (term instanceof DisjunctiveFormula) {
            return add((DisjunctiveFormula) term);
        } else {
            assert false : "invalid argument found: " + term;
            return null;
        }
    }

    public ConjunctiveFormula addAll(Iterable<? extends Object> terms) {
        ConjunctiveFormula result = this;
        for (Object term : terms) {
            result = result.add(term);
            if (result == null) {
                return null;
            }
        }
        return result;
    }

    public TruthValue truthValue() {
        return truthValue;
    }

    public boolean isTrue() {
        return truthValue == TruthValue.TRUE;
    }

    public boolean isFalse() {
        return truthValue == TruthValue.FALSE;
    }

    public boolean isUnknown() {
        return truthValue == TruthValue.UNKNOWN;
    }

    /**
     * Removes specified variable bindings from this constraint.
     * <p>
     * Note: this method should only be used to garbage collect useless
     * bindings. It is called to remove all bindings of the rewrite rule
     * variables after building the rewrite result.
     */
    public ConjunctiveFormula removeBindings(Set<Variable> variablesToRemove) {
        return new ConjunctiveFormula(
                substitution.minusAll(variablesToRemove),
                equalities,
                disjunctions,
                truthValue,
                context);
    }

    /**
     * Simplifies this conjunctive formula as much as possible.
     * Decomposes equalities by using unification.
     */
    public ConjunctiveFormula simplify() {
        return simplify(false, true);
    }

    /**
     * Similar to {@link ConjunctiveFormula#simplify()} except that equalities
     * between builtin data structures will remain intact if they cannot be
     * resolved completely.
     */
    public ConjunctiveFormula simplifyBeforePatternFolding() {
        return simplify(false, false);
    }

    public ConjunctiveFormula simplifyModuloPatternFolding() {
        return simplify(true, true);
    }

    /**
     * Simplifies this conjunctive formula as much as possible.
     * Decomposes equalities by using unification.
     */
    public ConjunctiveFormula simplify(boolean patternFolding, boolean partialSimplification) {
        Substitution<Variable, Term> substitution = this.substitution;
        PersistentUniqueList<Equality> equalities = this.equalities;
        PersistentUniqueList<DisjunctiveFormula> disjunctions = this.disjunctions;

        boolean change;
        do {
            change = false;
            PersistentUniqueList<Equality> pendingEqualities = PersistentUniqueList.empty();
            for (int i = 0; i < equalities.size(); ++i) {
                Equality equality = equalities.get(i);
                Term leftHandSide = equality.leftHandSide().substituteAndEvaluate(substitution, context);
                Term rightHandSide = equality.rightHandSide().substituteAndEvaluate(substitution, context);
                equality = new Equality(leftHandSide, rightHandSide, context);
                if (equality.isTrue()) {
                    // delete
                } else if (equality.truthValue() == TruthValue.FALSE) {
                    // conflict
                    return new ConjunctiveFormula(
                            substitution,
                            equalities,
                            disjunctions,
                            TruthValue.FALSE,
                            context);
                } else {
                    if (equality.isSimplifiableByCurrentAlgorithm()) {
                        // (decompose + conflict)*
                        SymbolicUnifier unifier = new SymbolicUnifier(
                                patternFolding,
                                partialSimplification,
                                context);
                        if (!unifier.symbolicUnify(leftHandSide, rightHandSide)) {
                            return new ConjunctiveFormula(
                                    substitution,
                                    equalities,
                                    disjunctions,
                                    TruthValue.FALSE,
                                    context);
                        }
                        equalities = equalities.plusAll(i + 1, unifier.constraint().equalities);
                        equalities = equalities.plusAll(i + 1, unifier.constraint().substitution.equalities(context));
                        disjunctions = disjunctions.plusAll(unifier.constraint().disjunctions);
                    } else if (leftHandSide instanceof Variable
                            && !rightHandSide.variableSet().contains(leftHandSide)) {
                        // eliminate
                        substitution = Substitution.composeAndEvaluate(
                                substitution,
                                Substitution.singleton((Variable) leftHandSide, rightHandSide),
                                context);
                        change = true;
                        if (substitution.isFalse(context)) {
                            return new ConjunctiveFormula(
                                    substitution,
                                    equalities,
                                    disjunctions,
                                    TruthValue.FALSE,
                                    context);
                        }
                    } else if (rightHandSide instanceof Variable
                            && !leftHandSide.variableSet().contains(rightHandSide)) {
                        // swap + eliminate
                        substitution = Substitution.composeAndEvaluate(
                                substitution,
                                Substitution.singleton((Variable) rightHandSide, leftHandSide),
                                context);
                        change = true;
                        if (substitution.isFalse(context)) {
                            return new ConjunctiveFormula(
                                    substitution,
                                    equalities,
                                    disjunctions,
                                    TruthValue.FALSE,
                                    context);
                        }
                    } else if (leftHandSide instanceof Variable
                            && rightHandSide.isNormal()
                            && rightHandSide.variableSet().contains(leftHandSide)) {
                        return new ConjunctiveFormula(
                                substitution,
                                equalities,
                                disjunctions,
                                TruthValue.FALSE,
                                context);
                    } else if (rightHandSide instanceof Variable
                            && leftHandSide.isNormal()
                            && leftHandSide.variableSet().contains(rightHandSide)) {
                        return new ConjunctiveFormula(
                                substitution,
                                equalities,
                                disjunctions,
                                TruthValue.FALSE,
                                context);
                    } else {
                        // unsimplified equation
                        pendingEqualities.add(equality);
                    }
                }
            }
            equalities = pendingEqualities;
        } while(change);

        return new ConjunctiveFormula(
                substitution,
                equalities,
                disjunctions,
                truthValue,
                context);
    }

    /**
     * Checks if this constraint is a substitution of the given variables.
     * <p>
     * This method is useful for checking if narrowing happens.
     */
    public boolean isMatching(Set<Variable> variables) {
        return isSubstitution() && substitution.keySet().equals(variables);
    }

    public boolean isSubstitution() {
        return equalities.isEmpty() && disjunctions.isEmpty();
    }

    public ConjunctiveFormula orientSubstitution(Set<Variable> variables) {
        if (substitution.keySet().containsAll(variables)) {
            return this;
        }

        /* compute the preimages of each variable in the codomain of the substitution */
        Multimap<Variable, Variable> equivalenceClasses = HashMultimap.create();
        substitution.entrySet().stream()
                .filter(e -> e.getValue() instanceof Variable)
                .forEach(e -> equivalenceClasses.put((Variable) e.getValue(), e.getKey()));

        Substitution<Variable, Variable> orientationSubstitution = Substitution.empty();
        for (Map.Entry<Variable, Collection<Variable>> entry : equivalenceClasses.asMap().entrySet()) {
            if (variables.contains(entry.getKey())) {
                Optional<Variable> replacement = entry.getValue().stream()
                        .filter(v -> v.sort().equals(entry.getKey().sort()))
                        .filter(v -> !variables.contains(v))
                        .findAny();
                if (replacement.isPresent()) {
                    orientationSubstitution = orientationSubstitution
                            .plus(entry.getKey(), replacement.get())
                            .plus(replacement.get(), entry.getKey());
                } else {
                    return null;
                }
            }
        }

        return (ConjunctiveFormula) this.substituteWithBinders(orientationSubstitution, context);
    }

    public ConjunctiveFormula expandPatternsAndSimplify(boolean narrowing) {
//        Map<Variable, Term> oldSubst = null;
//        Set<Equality> oldEqualities = null;
//
//        while (truthValue == TruthValue.UNKNOWN) {
//            assert isNormal;
//            if (oldSubst != null && oldEqualities != null
//                    && substitution.equals(oldSubst)
//                    && equalities.equals(oldEqualities)) {
//                break;
//            }
//            oldSubst = Maps.newHashMap(substitution);
//            oldEqualities = Sets.newHashSet(equalities);
//
//            // TODO(AndreiS): patterns should be expanded before are put in the substitution
//            Set<Variable> keys = Sets.newLinkedHashSet(substitution.keySet());
//            writeProtected = true;
//            for (Variable variable : keys) {
//                Term term = substitution.get(variable);
//                Term expandedTerm = term.expandPatterns(this, narrowing);
//                if (term != expandedTerm) {
//                    substitution.put(variable, expandedTerm);
//                }
//            }
//
//            LinkedHashSet<Equality> expandedEqualities = Sets.newLinkedHashSet();
//            for (Equality equality : equalities) {
//                Equality expandedEquality = equality.expandPatterns(this, narrowing);
//                expandedEqualities.add(expandedEquality);
//            }
//            equalities = expandedEqualities;
//            writeProtected = false;
//
//            // TODO(AndreiS): move folding from here (this is way too fragile)
//            /* simplify with pattern folding if not performing narrowing */
//            if (!narrowing) {
//                return simplifyModuloPatternFolding();
//            } else {
//                return simplify();
//            }
//        }
        return null;
    }

    public DisjunctiveFormula getDisjunctiveNormalForm() {
        if (disjunctions.isEmpty()) {
            return new DisjunctiveFormula(PersistentUniqueList.singleton(this));
        }

        ConjunctiveFormula result = new ConjunctiveFormula(
                substitution,
                equalities,
                PersistentUniqueList.empty(),
                truthValue,
                context);

        List<Set<ConjunctiveFormula>> collect = disjunctions.stream()
                .map(disjunction -> ImmutableSet.<ConjunctiveFormula>copyOf(disjunction.conjunctions()))
                .collect(Collectors.toList());
        List<ConjunctiveFormula> collect1 = Sets.cartesianProduct(collect).stream()
                .map(result::addAll)
                .collect(Collectors.toList());

        return new DisjunctiveFormula(PersistentUniqueList.from(collect1));
    }

    public boolean checkUnsat() {
        return context.global().constraintOps.checkUnsat(this);
    }

    public boolean implies(ConjunctiveFormula constraint, Set<Variable> rightOnlyVariables) {
        // TODO(AndreiS): this can prove "stuff -> false", it needs fixing
        assert !constraint.isFalse();

//        LinkedList<Pair<SymbolicConstraint, SymbolicConstraint>> implications = new LinkedList<>();
//        implications.add(Pair.of(this, constraint));
//        while (!implications.isEmpty()) {
//            Pair<SymbolicConstraint, SymbolicConstraint> implication = implications.remove();
//
//            SymbolicConstraint left = implication.getLeft();
//            SymbolicConstraint right = implication.getRight();
//            if (left.isFalse()) continue;
//
//            if (context.definition().context().globalOptions.debug) {
//                System.err.println("Attempting to prove: \n\t" + left + "\n  implies \n\t" + right);
//            }
//
//            right.orientSubstitution(rightOnlyVariables);
//            right = left.simplifyConstraint(right);
//            right.orientSubstitution(rightOnlyVariables);
//            if (right.isTrue() || (right.equalities().isEmpty() && rightOnlyVariables.containsAll(right.substitution().keySet()))) {
//                if (context.definition().context().globalOptions.debug) {
//                    System.err.println("Implication proved by simplification");
//                }
//                continue;
//            }
//            IfThenElseFinder ifThenElseFinder = new IfThenElseFinder(context);
//            right.accept(ifThenElseFinder);
//            if (!ifThenElseFinder.result.isEmpty()) {
//                KItem ite = ifThenElseFinder.result.get(0);
//                // TODO (AndreiS): handle KList variables
//                Term condition = ((KList) ite.kList()).get(0);
//                if (context.definition().context().globalOptions.debug) {
//                    System.err.println("Split on " + condition);
//                }
//                SymbolicConstraint left1 = new SymbolicConstraint(left);
//                left1.add(condition, BoolToken.TRUE);
//                implications.add(Pair.of(left1, new SymbolicConstraint(right)));
//                SymbolicConstraint left2 = new SymbolicConstraint(left);
//                left2.add(condition, BoolToken.FALSE);
//                implications.add(Pair.of(left2, new SymbolicConstraint(right)));
//                continue;
//            }
////            if (DEBUG) {
////                System.out.println("After simplification, verifying whether\n\t" + left.toString() + "\nimplies\n\t" + right.toString());
////            }
//            if (!impliesSMT(left,right, rightOnlyVariables)) {
//                if (context.definition().context().globalOptions.debug) {
//                    System.err.println("Failure!");
//                }
//                return false;
//            } else {
//                if (context.definition().context().globalOptions.debug) {
//                    System.err.println("Proved!");
//                }
//            }
//        }
        return true;
    }

    private static boolean impliesSMT(
            ConjunctiveFormula left,
            ConjunctiveFormula right,
            Set<Variable> rightOnlyVariables) {
        assert left.context == right.context;
        return left.context.global().constraintOps.impliesSMT(left, right, rightOnlyVariables);
    }

    public boolean hasMapEqualities() {
        for (Equality equality : equalities) {
            if (equality.leftHandSide() instanceof BuiltinMap
                    && equality.rightHandSide() instanceof BuiltinMap) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isExactSort() {
        return true;
    }

    @Override
    public boolean isSymbolic() {
        return true;
    }

    @Override
    public Sort sort() {
        return Sort.BOOL;
    }

    @Override
    protected boolean computeMutability() {
        return false;
    }

    @Override
    protected int computeHash() {
        int hashCode = 1;
        hashCode = hashCode * Utils.HASH_PRIME + substitution.hashCode();
        hashCode = hashCode * Utils.HASH_PRIME + equalities.hashCode();
        return hashCode;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ConjunctiveFormula)) {
            return false;
        }

        ConjunctiveFormula conjunction = (ConjunctiveFormula) object;
        return substitution.equals(conjunction.substitution)
                && equalities.equals(conjunction.equalities)
                && disjunctions.equals(conjunction.disjunctions);
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public ASTNode accept(Transformer transformer) {
        return transformer.transform(this);
    }

    @Override
    public void accept(Matcher matcher, Term pattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void accept(Unifier unifier, Term pattern) {
        throw new UnsupportedOperationException();
    }

}

// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import org.kframework.backend.java.kil.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Computes the free variables of a term.
 *
 * @author TraianSF
 */
public class UnboundedVariablesCollector extends PrePostVisitor {
    private final TermContext context;
    private final Multiset<Term> boundVariables;
    private final Set<Term> unboundedVariables;

    /**
     * Computes the set of unbounded variables of a given term
     * @param term --- the term to compute the set of unbounded vars for
     * @param context
     * @return the set of unbounded vars in {@code term}
     */
    public static Set<Term> getUnboundedVars(Term term, TermContext context) {
        UnboundedVariablesCollector collector = new UnboundedVariablesCollector(context);
        term.accept(collector);
        return collector.unboundedVariables;
    }

    private UnboundedVariablesCollector(TermContext context) {
        this.context = context;
        boundVariables = HashMultiset.create();
        unboundedVariables = new HashSet<>();
        preVisitor.addVisitor(new LocalVisitor() {
            @Override
            public void visit(KItem kItem) {
                handleBinderVariables(kItem, true);
            }
        });
        postVisitor.addVisitor(new LocalVisitor() {
            @Override
            public void visit(KItem kItem) {
                handleBinderVariables(kItem, false);
            }
        });
        preVisitor.addVisitor(new LocalVisitor(){
            @Override
            public void visit(Term node) {
                if (context.definition().subsorts().isSubsortedEq(Sort.VARIABLE, node.sort())) {
                    if (!boundVariables.contains(node)) {
                        unboundedVariables.add(node);
                    }
                } else {
                    super.visit(node);
                }
            }
        });
     }

    private void handleBinderVariables(KItem kItem, boolean add) {
        // TODO(AndreiS): fix binder when dealing with KLabel variables and non-concrete KLists
        if (!(kItem.kLabel() instanceof KLabel) || !(kItem.kList() instanceof KList)) {
            return;
        }

        KLabel kLabel = (KLabel) kItem.kLabel();
        KList kList = (KList) kItem.kList();
        if (kLabel instanceof KLabelConstant) {
            KLabelConstant kLabelConstant = (KLabelConstant) kLabel;
            if (kLabelConstant.isBinder()) {  // if label is a binder rename all bound variables
                Multimap<Integer, Integer> binderMap = kLabelConstant.getBinderMap();
                for (Integer keyIndex : binderMap.keySet()) {
                    if (add) {
                        boundVariables.addAll(kList.get(keyIndex).userVariableSet(context));
                    } else {
                        boundVariables.removeAll(kList.get(keyIndex).userVariableSet(context));
                    }
                }
            }
        }
    }


}

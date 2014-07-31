// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.compile.sharing;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;

import org.apache.commons.collections4.comparators.ComparableComparator;
import org.apache.commons.collections4.comparators.NullComparator;
import org.kframework.kil.Location;
import org.kframework.kil.Module;
import org.kframework.kil.ModuleItem;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.CopyOnWriteTransformer;

public class SortRulesNormalizer extends CopyOnWriteTransformer {

    public SortRulesNormalizer(Context context) {
        super("Sort rules deterministically", context);
    }

    @Override
    public Module visit(Module module, Void _) {
        Collections.sort(module.getItems(), new Comparator<ModuleItem>() {
            @Override
            public int compare(ModuleItem arg0, ModuleItem arg1) {
                ComparableComparator<File> fcc = ComparableComparator.comparableComparator();
                NullComparator<File> nullFcc = new NullComparator<>(fcc);
                int x;
                if ((x = nullFcc.compare(arg0.getFilename(), arg1.getFilename())) != 0) {
                    return x;
                }

                ComparableComparator<Location> lcc = ComparableComparator.comparableComparator();
                NullComparator<Location> nullLcc = new NullComparator<>(lcc);
                if ((x = nullLcc.compare(arg0.getLocation(), arg1.getLocation())) != 0) {
                    return x;
                }
                return arg0.toString().compareTo(arg1.toString());
            }
        });
        return module;
    }
}

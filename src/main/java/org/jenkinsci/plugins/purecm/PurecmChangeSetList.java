package org.jenkinsci.plugins.purecm;

import hudson.scm.ChangeLogSet;
import hudson.model.AbstractBuild;

import java.util.List;
import java.util.Collections;
import java.util.Iterator;

public class PurecmChangeSetList extends ChangeLogSet<PurecmChangeSet> {
    private final List<PurecmChangeSet> changeSets;

    PurecmChangeSetList(AbstractBuild build, List<PurecmChangeSet> logs) {
        super(build);
        Collections.reverse(logs);
        this.changeSets = Collections.unmodifiableList(logs);
        for (PurecmChangeSet log : logs)
            log.setParent(this);
    }

    public boolean isEmptySet() {
        return changeSets.isEmpty();
    }

    public Iterator<PurecmChangeSet> iterator() {
        return changeSets.iterator();
    }

    public List<PurecmChangeSet> getLogs() {
        return changeSets;
    }

    public @Override String getKind() {
        return "hg";
    }

}
package org.jenkinsci.plugins.purecm;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.util.Digester2;
import hudson.util.IOException2;

import java.io.File;
import java.io.IOException;

import java.util.List;
import java.util.ArrayList;

import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

public class PurecmChangeLogParser extends ChangeLogParser {
    @Override
    public ChangeLogSet<? extends ChangeLogSet.Entry> parse( AbstractBuild build, File changelogFile ) throws IOException, SAXException {
        ArrayList<PurecmChangeSet> changesetList = new ArrayList<PurecmChangeSet>();

        Digester digester = new Digester2();

        digester.push(changesetList);
        digester.addObjectCreate("Changesets/Changeset", PurecmChangeSet.class);
        digester.addBeanPropertySetter( "Changesets/Changeset/RefNo", "changeId");
        digester.addBeanPropertySetter( "Changesets/Changeset/Description", "description");
        digester.addBeanPropertySetter( "Changesets/Changeset/Client", "user");
        digester.addBeanPropertySetter( "Changesets/Changeset/Date", "PCMTimestamp");

        digester.addObjectCreate("Changesets/Changeset/Items/Item", PurecmChangeSetItem.class);
        digester.addBeanPropertySetter( "Changesets/Changeset/Items/Item/FileName", "path");
        digester.addBeanPropertySetter( "Changesets/Changeset/Items/Item/Revision", "revision");
        digester.addBeanPropertySetter( "Changesets/Changeset/Items/Item/ChangeType", "changeType");
        digester.addBeanPropertySetter( "Changesets/Changeset/Items/Item/RenamePath", "renamePath");
        digester.addSetNext( "Changesets/Changeset/Items/Item", "addChangeItem");

        digester.addSetNext( "Changesets/Changeset", "add" );

        try {
            digester.parse(changelogFile);
        } catch ( IOException e ) {
            throw new IOException("Failed to read " + changelogFile, e);
        } catch (SAXException e ) {
            throw new IOException("Failed to parse " + changelogFile, e);
        }

        return new PurecmChangeSetList(build, changesetList);
    }
}
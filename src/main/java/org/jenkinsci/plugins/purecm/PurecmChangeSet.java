package org.jenkinsci.plugins.purecm;

import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.AffectedFile;

import java.util.Collection;
import java.util.ArrayList;

public class PurecmChangeSet extends ChangeLogSet.Entry {
    private String changeId;
    private String description;
    private String user;
    private String timestamp;
    private ArrayList<PurecmChangeSetItem> items = new ArrayList<PurecmChangeSetItem>();
    private volatile ArrayList<String> affectedPaths;

    public void setChangeId(String value) {
        changeId = value;
    }    

    public void setDescription(String value) {
        description = value;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String value) {
       	user = value;
    }

    public void setPCMTimestamp(String value) {
        timestamp = value;
    }

    public String getPCMTimestamp() {
        return timestamp;
    }

    public String getChangeId() {
        return changeId;
    }

    public void addChangeItem(PurecmChangeSetItem item) {
        items.add(item);
    }

    @Override
	public String getMsg() {
        return description;
    }

    @Override
	public User getAuthor() {
        return User.get(user);
    }

	@Override 
    protected void setParent(ChangeLogSet parent) {
    	super.setParent(parent);
    }

    @Override
    public Collection<String> getAffectedPaths() {
        ArrayList<String> paths = new ArrayList<String>();

        for( PurecmChangeSetItem item : items ) {
            switch( item.getType() ) {
                case pcmItemAdd:
                case pcmItemEdit:
                case pcmItemDelete:
                    paths.add(item.getNewPath());
                    break;
                default:
                    // Ignore folder change items
                    break;
            }
        }

    	return paths;
    }

    @Override
    public Collection<? extends AffectedFile> getAffectedFiles() {
        ArrayList<PurecmAffectedFile> affected = new ArrayList<PurecmAffectedFile>();

        for( PurecmChangeSetItem item : items ) {
            switch( item.getType() ) {
                case pcmItemAdd:
                case pcmItemEdit:
                case pcmItemDelete:
                    affected.add(new PurecmAffectedFile(item));
                    break;
                default:
                    // Ignore folder change items
                    break;
            }
        }

        return affected;
    }

    public ArrayList<PurecmChangeSetItem> getItems() {
        return items;
    }
};
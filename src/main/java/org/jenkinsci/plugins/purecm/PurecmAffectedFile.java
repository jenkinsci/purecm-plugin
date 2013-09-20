package org.jenkinsci.plugins.purecm;

import hudson.scm.EditType;
import hudson.scm.ChangeLogSet.AffectedFile;

public final class PurecmAffectedFile implements AffectedFile {
	private EditType editType;
	private String path;

	public PurecmAffectedFile( PurecmChangeSetItem item ) {
		path = item.getNewPath();

		path = path.replace('\\', '/');

		if ( path.charAt(0) == '/' ) {
			path = path.substring(1);
		}

		switch( item.getType() ) {
			case pcmItemAdd:
				editType = EditType.ADD;
				break;
			case pcmItemEdit:
				editType = EditType.EDIT;
				break;
			case pcmItemDelete:
				editType = EditType.DELETE;
				break;
			default:
				// Folder types should really be unsupported
				editType = EditType.EDIT;
				break;
		}
	}

	public EditType getEditType() {
		return editType;
	}

	public String getPath() {
		return path;
	}
}
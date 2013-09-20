package org.jenkinsci.plugins.purecm;

import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.util.Digester2;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.PollingResult;
import hudson.scm.ChangeLogParser;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

import java.util.ArrayList;

import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

public class PurecmScm extends SCM {

    private String repository;
    private String stream;
    private boolean cleanOnCheckout;

    @DataBoundConstructor
    public PurecmScm(String repository, String stream, boolean cleanOnCheckout) {
        this.repository = repository;
        this.stream = stream;
        this.cleanOnCheckout = cleanOnCheckout;
    }

    public String getRepository() {
        return repository;
    }

    public String getStream() {
        return stream;
    }

    public boolean getCleanOnCheckout() {
        return cleanOnCheckout;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends SCMDescriptor {
        private String exe = "pcm";

        public DescriptorImpl() {
            super(PurecmScm.class, null);
            load();
        }

        public FormValidation doCheckRepository(@QueryParameter String value) throws IOException, ServletException {
            if(value.length()==0)
                return FormValidation.error("Please enter the repository name");
            return FormValidation.ok();
        }

        public FormValidation doCheckStream(@QueryParameter String value) throws IOException, ServletException {
            if(value.length()==0)
                return FormValidation.error("Please enter the stream path");
            if(value.indexOf("/") == -1 && value.indexOf("\\") == -1)
                return FormValidation.warning("This should contain the stream path and name (e.g. 'Project 1/Version 1/Version 1')");
            return FormValidation.ok();
        }

        public FormValidation doCheckCleanOnCheckout(@QueryParameter boolean value) throws IOException, ServletException {
            if(value)
                return FormValidation.warning("Creating a new workspace for each build can be very inefficient for large workspaces");
            return FormValidation.ok();
        }

        public FormValidation doCheckExe(@QueryParameter String value) throws IOException, ServletException {
            if(value.length()==0)
                return FormValidation.error("Please enter the PureCM executable");
            return FormValidation.validateExecutable(value);
        }

        public String getDisplayName() {
            return "PureCM";
        }

        public String getExe() {
            return exe;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            exe = formData.getString("exe");

            save();

            return super.configure(req,formData);
        }
    }

    private int createWorkspace( Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile, boolean updateLog ) throws IOException, InterruptedException {
        int cmdResult;

        // Delete all files from the workspace directory
        {
            Launcher.ProcStarter proc = launcher.launch();

            proc.pwd(workspace);
            proc.stderr(listener.getLogger());

            if ( launcher.isUnix() ) {
                proc.cmds("rm", "-rf", "*");
            }
            else {
                proc.cmds("cmd.exe", "/C", "del", "/F", "/S", "/Q", "*");
            }

            proc.join();            
        }

        {
            Launcher.ProcStarter proc = launcher.launch();

            proc.pwd(workspace);
            proc.cmds(getDescriptor().exe, "workspace", "add", getRepository(), getStream(), ".");
            proc.stderr(listener.getLogger());
            proc.stdout(listener.getLogger());
            cmdResult = proc.join();
        }

        if (cmdResult == 0) {
            if ( updateLog )
            {
                Launcher.ProcStarter proc = launcher.launch();

                proc.pwd(workspace);
                proc.cmds(getDescriptor().exe, "changes", "list", "-x");
                proc.stderr(listener.getLogger());
                proc.stdout(new FileOutputStream(changelogFile));
                cmdResult = proc.join();
            }
        }
        else {
            listener.getLogger().println("Failed to create workspace. Please check that PureCM is correctly configured.");
        }  

        return cmdResult;
    }

    private int deleteWorkspace( Launcher launcher, FilePath workspace, BuildListener listener ) throws IOException, InterruptedException {
        Launcher.ProcStarter proc = launcher.launch();

        proc.pwd(workspace);
        proc.cmds(getDescriptor().exe, "workspace", "delete", ".");
        proc.stderr(listener.getLogger());
        proc.stdout(listener.getLogger());
        return proc.join();
    }

    @Override
    public boolean checkout( AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile ) throws IOException, InterruptedException {
        listener.getLogger().println("Performing checkout");

        int cmdResult;
        boolean createWorkspace = true;
        boolean deleteWorkspace = false;

        // Run pcm workspace whatsnew to check for new changes
        {
            Launcher.ProcStarter proc = launcher.launch();

            proc.pwd(workspace);
            proc.cmds(getDescriptor().exe, "workspace", "whatsnew", "-x", "-i");
            proc.stderr(listener.getLogger());
            proc.stdout(new FileOutputStream(changelogFile));
            cmdResult = proc.join();
        }

        if (cmdResult == 0) {
            if ( cleanOnCheckout ) {
                deleteWorkspace(launcher, workspace, listener);
                cmdResult = createWorkspace(launcher, workspace, listener, changelogFile, false);
            }
            else {
                // Update the workspace
                {
                    Launcher.ProcStarter proc = launcher.launch();

                    proc.pwd(workspace);
                    proc.cmds(getDescriptor().exe, "workspace", "update");
                    proc.stderr(listener.getLogger());
                    proc.stdout(listener.getLogger());
                    cmdResult = proc.join();
                }

                if ( cmdResult != 0 ) {
                    listener.getLogger().println("Failed to update workspace. Will create new workspace.");
                    deleteWorkspace(launcher, workspace, listener);
                    cmdResult = createWorkspace(launcher, workspace, listener, changelogFile, false);
                }
            }
        }
        else {
            listener.getLogger().println("Failed to check purecm workspace for new changesets. Will try assume the workspace needs creating.");
            cmdResult = createWorkspace(launcher, workspace, listener, changelogFile, true);
        }

        return (cmdResult == 0);
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new PurecmChangeLogParser();
    }

    @Override
    public boolean pollChanges( AbstractProject<?,?> project, Launcher launcher, FilePath workspace, TaskListener listener ) {
        listener.getLogger().println("Polling changes.");

        int cmdResult;
        File whatsNewLog;

        try {
            whatsNewLog = File.createTempFile("whatsnew", "log");    
        }
        catch(IOException e) {
             listener.getLogger().println("Failed to create temporary file to write change log.");           
             return true;
        }

        // Run pcm workspace whatsnew to check for new changes        
        {
            Launcher.ProcStarter proc = launcher.launch();

            proc.pwd(workspace);
            proc.cmds(getDescriptor().exe, "workspace", "whatsnew", "-x");
            proc.stderr(listener.getLogger());

            try {
                proc.stdout(new FileOutputStream(whatsNewLog));
            }
            catch(FileNotFoundException e) {
                listener.getLogger().println("Failed to begin writing to change log.");           
                return true;
            }
        
            try {
                cmdResult = proc.join();
            }
            catch(IOException e) {
                 listener.getLogger().println("Failed to run whatsnew command.");
                 return true;
            }
            catch(InterruptedException e) {
                 listener.getLogger().println("Failed to run whatsnew command.");
                 return true;
            }
        }

        // Parse the file to get list of changes
        if ( cmdResult == 0 ) {
            ArrayList<PurecmChangeSet> changesetList = new ArrayList<PurecmChangeSet>();
            Digester digester = new Digester2();

            digester.push(changesetList);
            digester.addObjectCreate("Changesets/Changeset", PurecmChangeSet.class);
            digester.addSetNext( "Changesets/Changeset", "add" );

            try {
                digester.parse(whatsNewLog);
            } catch ( IOException e ) {
                listener.getLogger().println("Failed to read whatsnew log.");
                return true;
            } catch (SAXException e ) {
                listener.getLogger().println("Failed to parse whatsnew log.");
                return true;
            }

            return ( !changesetList.isEmpty() );
        }
        else {
            return true;
        }
    }

    @Override
    public PollingResult compareRemoteRevisionWith( AbstractProject<?,?> project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline ) {
        listener.getLogger().println("Comparing remote revision with.");

        if ( pollChanges(project, launcher, workspace, listener) ) {
            return PollingResult.SIGNIFICANT;
        }
        else {
            return PollingResult.NO_CHANGES;
        }
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild( AbstractBuild<?,?> build, Launcher launcher, TaskListener listener) {
        listener.getLogger().println("Calculating revisions from build.");

        return SCMRevisionState.NONE;
    }
}


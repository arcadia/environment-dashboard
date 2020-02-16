package org.jenkinsci.plugins.environmentdashboard;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;

import hudson.model.Hudson;
import java.io.File;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import java.util.logging.Logger;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.environmentdashboard.utils.DBConnection;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Class to create Dashboard view.
 * @author vipin
 * @date 15/10/2014
 */
public class DashboardBuilder extends BuildWrapper {

    private final String nameOfEnv;
    private final String componentName;
    private final String buildNumber;
    private final String buildJob;
    private final String packageName;
    private ArrayList<ListItem> data = new ArrayList<ListItem>();
    public boolean addColumns = false;

    @DataBoundConstructor
    public DashboardBuilder(String nameOfEnv, String componentName, String buildNumber, String buildJob, String packageName, boolean addColumns, ArrayList<ListItem> data) {
        this.nameOfEnv = nameOfEnv;
        this.componentName = componentName;
        this.buildNumber = buildNumber;
        this.buildJob = buildJob;
        this.packageName = packageName;
        if (addColumns){
            this.addColumns = addColumns;
        }else {
            this.addColumns=false;
        }
        //this.data = Collections.emptyList();
        if(this.addColumns){
            for (ListItem i: data){
                if(!i.getColumnName().isEmpty()){ 
                    this.data.add(i);
                }
            }
        }
    }

    public String getNameOfEnv() {
        return nameOfEnv;
    }
    public String getComponentName() {
        return componentName;
    }
    public String getBuildNumber() {
        return buildNumber;
    }
    public String getBuildJob() {
        return buildJob;
    }
    public String getPackageName() {
        return packageName;
    }
    public List<ListItem> getData(){
        return data;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
	
		//System.out.println("-----------------------------------------at setup function------------------------------");
		
        // PreBuild
        final Integer numberOfDays = ( (getDescriptor().getNumberOfDays() == null) ? 30 : getDescriptor().getNumberOfDays() );
        String passedBuildNumber = build.getEnvironment(listener).expand(buildNumber);
        String passedEnvName = build.getEnvironment(listener).expand(nameOfEnv);
        String passedCompName = build.getEnvironment(listener).expand(componentName);
        String passedBuildJob = build.getEnvironment(listener).expand(buildJob);
        String passedPackageName = build.getEnvironment(listener).expand(packageName);
        List<ListItem> passedColumnData = new ArrayList<ListItem>();
        if (addColumns){
            for (ListItem item : data){
                passedColumnData.add(
                        new ListItem(
                            build.getEnvironment(listener).expand(item.columnName),
                            build.getEnvironment(listener).expand(item.contents)
                            )
                        );
            }
        }
        String returnComment = null;

        if (passedPackageName== null){
            passedPackageName = "";
        }

        if (!(passedBuildNumber.matches("^\\s*$") || passedEnvName.matches("^\\s*$") || passedCompName.matches("^\\s*$"))) {
            returnComment = writeToDB(build, listener, passedEnvName, passedCompName, passedBuildNumber, "PRE", passedBuildJob, numberOfDays, passedPackageName, passedColumnData);
            listener.getLogger().println("Pre-Build Update: " + returnComment);
        } else {
            listener.getLogger().println("Environment dashboard not updated: one or more required values were blank");
        }
        // TearDown - This runs post all build steps
        class TearDownImpl extends Environment {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                String passedBuildNumber = build.getEnvironment(listener).expand(buildNumber);
                String passedEnvName = build.getEnvironment(listener).expand(nameOfEnv);
                String passedCompName = build.getEnvironment(listener).expand(componentName);
                String passedBuildJob = build.getEnvironment(listener).expand(buildJob);
                String passedPackageName = build.getEnvironment(listener).expand(packageName);
                String doDeploy = build.getEnvironment(listener).expand("$UPDATE_ENV_DASH");
                List<ListItem> passedColumnData = new ArrayList<ListItem>();
                if (addColumns){
                    for (ListItem item : data){
                        passedColumnData.add(
                                new ListItem(
                                    build.getEnvironment(listener).expand(item.columnName),
                                    build.getEnvironment(listener).expand(item.contents)
                                    )
                                );
                    }
                }

                String returnComment = null;
                
                if (passedPackageName== null){
                    passedPackageName = "";
                }
                if (doDeploy == null || (!doDeploy.equals("true") && !doDeploy.equals("false"))){
                    doDeploy = "true";
                }

                if (doDeploy.equals("true")){
                    if (!(passedBuildNumber.matches("^\\s*$") || passedEnvName.matches("^\\s*$") || passedCompName.matches("^\\s*$"))) {
                        returnComment = writeToDB(build, listener, passedEnvName, passedCompName, passedBuildNumber, "POST", passedBuildJob, numberOfDays, passedPackageName, passedColumnData);
                        listener.getLogger().println("Post-Build Update: " + returnComment);
                    }
                }else{
                    if (!(passedBuildNumber.matches("^\\s*$") || passedEnvName.matches("^\\s*$") || passedCompName.matches("^\\s*$"))) {
                        returnComment = writeToDB(build, listener, passedEnvName, passedCompName, passedBuildNumber, "NODEPLOY", passedBuildJob, numberOfDays, passedPackageName, passedColumnData);
                        listener.getLogger().println("Post-Build Update: " + returnComment);
                    }
                    
                }

                return super.tearDown(build, listener);
            }
        }
        return new TearDownImpl();
    }

    @SuppressWarnings("rawtypes")
    private String writeToDB(AbstractBuild build, BuildListener listener, String envName, String compName, String currentBuildNum, String runTime, String buildJob, Integer numberOfDays, String packageName, List<ListItem> passedColumnData) {
        
		
		//System.out.println("-----------------------------------------at writeToDB function------------------------------");
		
		
		String returnComment = null;
        if (envName.matches("^\\s*$") || compName.matches("^\\s*$")) {
            returnComment = "WARN: Either Environment name or Component name is empty.";
            return returnComment;
        }

        //Get DB connection
        Connection conn = DBConnection.getConnection();

        Statement stat = null;
        try {
            stat = conn.createStatement();
        } catch (SQLException e) {
            returnComment = "WARN: Could not execute statement." + e;
            return returnComment;
        }
        try {
            stat.execute("CREATE TABLE IF NOT EXISTS env_dashboard (envComp VARCHAR(255), jobUrl VARCHAR(255), buildNum VARCHAR(255), buildStatus VARCHAR(255), envName VARCHAR(255), compName VARCHAR(255), created_at TIMESTAMP,  buildJobUrl VARCHAR(255), packageName VARCHAR(255));");
        } catch (SQLException e) {
            returnComment = "WARN: Could not create table env_dashboard." + e;
            return returnComment;
        }
        try {
            stat.execute("ALTER TABLE env_dashboard ADD IF NOT EXISTS packageName VARCHAR(255);");
        } catch (SQLException e) {
            returnComment = "WARN: Could not alter table env_dashboard." + e;
            return returnComment;
        }
        String columns = "";
        String contents = "";
        String setContents = "";
        for (ListItem item : passedColumnData){
            columns = columns + ", " +  item.columnName;
            contents = contents + "', '" + item.contents;
            setContents = setContents + ", " +  item.columnName + "=" + "'" + item.contents + "' ";
            try {
                stat.execute("ALTER TABLE env_dashboard ADD IF NOT EXISTS " + item.columnName + " VARCHAR;");
            } catch (SQLException e) {
                returnComment = "WARN: Could not alter table env_dashboard to add column " + item.columnName + "." + e;
                return returnComment;
            }
        }
        String indexValueofTable = envName + '=' + compName;
        String currentBuildResult = "UNKNOWN";
        if (build.getResult() == null && runTime.equals("PRE")) {
            currentBuildResult = "RUNNING";
        } else if (build.getResult() == null && runTime.equals("POST")) {
            currentBuildResult = "SUCCESS";
        } else if (runTime.equals("NODEPLOY")){
            currentBuildResult = "NODEPLOY";   
        }else {
            currentBuildResult = build.getResult().toString();
        }
        String currentBuildUrl = build.getUrl();

        String buildJobUrl;
        //Build job is an optional configuration setting
        if (buildJob.isEmpty()) {
            buildJobUrl = "";
        } else {
            buildJobUrl = "job/" + buildJob + "/" + currentBuildNum;
        }

        String runQuery = null;
        if (runTime.equals("PRE")) {
            runQuery = "INSERT INTO env_dashboard (envComp, jobUrl, buildNum, buildStatus, envName, compName, created_at, buildJobUrl, packageName" + columns +") VALUES( '" + indexValueofTable + "', '" + currentBuildUrl + "', '" + currentBuildNum + "', '" + currentBuildResult + "', '" + envName + "', '" + compName + "' , + current_timestamp, '" + buildJobUrl + "' , '" + packageName + contents + "');";
        } else {
            if (runTime.equals("POST")) {
                runQuery = "UPDATE env_dashboard SET buildStatus = '" + currentBuildResult + "', created_at = current_timestamp" + setContents + " WHERE envComp = '" + indexValueofTable +"' AND joburl = '" + currentBuildUrl + "';";
            }else {
                if (runTime.equals("NODEPLOY")){
                    runQuery = "DELETE FROM env_dashboard where envComp = '" + indexValueofTable +"' AND joburl = '" + currentBuildUrl + "';";
                }
            }
        }
        try {
            stat.execute(runQuery);
        } catch (SQLException e) {
            returnComment = "Error running query " + runQuery + "." + e;
            return returnComment;
        }
        if ( numberOfDays > 0 ) {
            runQuery = "DELETE FROM env_dashboard where created_at <= current_timestamp - " + numberOfDays + " and created_at != (select max(created_at) from env_dashboard e2 where e2.compName = env_dashboard.compName and e2.envName = env_dashboard.envName)";
            try {
                stat.execute(runQuery);
            } catch (SQLException e) {
                returnComment = "Error running delete query " + runQuery + "." + e;
                return returnComment;
            }
        }

        // create Backend entry
		runQuery = "INSERT INTO env_dashboard (envComp, jobUrl, buildNum, buildStatus, envName, compName, created_at, buildJobUrl, packageName) select distinct  '', '', 1, 1, '', concat(compName, ' Backend'), '2000-01-01', '', '' from env_dashboard a where not exists (select 1 from env_dashboard b where concat(a.compName, ' Backend') = b.compName) and compName not like '%Backend' and compName not like '%CRjob' and compName not like '%Web'";
        try {
            stat.execute(runQuery);
        } catch (SQLException e) {
            returnComment = "Error running backend query " + runQuery + "." + e;
            return returnComment;
        }
		
		// create CRjob entry
		//System.out.println("About to enter entry for " + compName + " CRjob...");
        runQuery = "INSERT INTO env_dashboard (envComp, jobUrl, buildNum, buildStatus, envName, compName, created_at, buildJobUrl, packageName) select distinct  '', '', 1, 1, '', concat(compName, ' CRjob'), '2000-01-01', '', '' from env_dashboard a where not exists (select 1 from env_dashboard b where concat(a.compName, ' CRjob') = b.compName) and compName not like '%CRjob' and compName not like '%Backend' and compName not like '%Web'";
        try {
            stat.execute(runQuery);
			//System.out.println("Succeeded entering it");
        } catch (SQLException e) {
            returnComment = "Error running CRjob query " + runQuery + "." + e;
            return returnComment;
        }
		
		
		// create Web entry
		//System.out.println("About to enter entry for " + compName + " Web...");
        runQuery = "INSERT INTO env_dashboard (envComp, jobUrl, buildNum, buildStatus, envName, compName, created_at, buildJobUrl, packageName) select distinct  '', '', 1, 1, '', concat(compName, ' Web'), '2000-01-01', '', '' from env_dashboard a where not exists (select 1 from env_dashboard b where concat(a.compName, ' Web') = b.compName) and compName not like '%CRjob' and compName not like '%Backend' and compName not like '%Web'";
        try {
            stat.execute(runQuery);
			//System.out.println("Succeeded entering it");
        } catch (SQLException e) {
            returnComment = "Error running Web query " + runQuery + "." + e;
            return returnComment;
        }
		
		
        // create and rotate database backups
        File f = null;
        File[] paths;

        try
        {
            boolean takeBackup = false;      
            // create new file
            f = new File(Hudson.getInstance().root.toString() + File.separator + "Backup1.zip");

            if(f.exists()) { 

                long d1 = Calendar.getInstance().getTime().getTime();
                long d2 = f.lastModified();
                if (TimeUnit.HOURS.convert(d1-d2,TimeUnit.MILLISECONDS) >= 24) {
                    takeBackup = true;
                }
            }
            else {
                takeBackup = true;
            }

            if (takeBackup) {

                for (int i = 5; i >= 1; i--) {
                    f = new File(Hudson.getInstance().root.toString() + File.separator + "Backup" + i + ".zip");
                    File f2 = new File(Hudson.getInstance().root.toString() + File.separator + "Backup" + (i+1) + ".zip");
                    if (f.exists())  f.renameTo(f2);
                    if (i == 5) f2.delete();
                }
                stat.execute("BACKUP TO '" + Hudson.getInstance().root.toString() + File.separator + "Backup1.zip'");
            }


        }
        catch(Exception e)
        {
           // if any error occurs
           e.printStackTrace();
        }

        try {
            stat.close();
            conn.close();
        } catch (SQLException e) {
            returnComment = "Error closing connection.";
            return returnComment;
        }
        return "Updated Dashboard DB";
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        private String numberOfDays = "30";
        private Integer parseNumberOfDays;
        public DescriptorImpl() {
            load();
        }

        @Override
        public String getDisplayName() {
            return "Details for Environment dashboard";
        }

        public FormValidation doCheckNameOfEnv(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set an Environment name.");
            return FormValidation.ok();
        }

        public FormValidation doCheckComponentName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a Component name.");
            return FormValidation.ok();
        }

        public FormValidation doCheckBuildNumber(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set the Build variable e.g: ${BUILD_NUMBER}.");
            return FormValidation.ok();
        }

        public FormValidation doCheckNumberOfDays(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please set the number of days to retain the DB data.");
            } else {
                try {
                    parseNumberOfDays = Integer.parseInt(value);
                } catch(Exception parseEx) {
                    return FormValidation.error("Please provide an integer value.");
                }
            }
            return FormValidation.ok();
        }

        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            numberOfDays = formData.getString("numberOfDays");
            if (numberOfDays == null || numberOfDays.equals(""))
            {
                numberOfDays = "30";
            }
            save();
            return super.configure(req,formData);
        }

        public Integer getNumberOfDays() {
            return parseNumberOfDays;
        }

    }
}

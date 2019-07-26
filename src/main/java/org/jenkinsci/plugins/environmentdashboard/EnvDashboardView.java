package org.jenkinsci.plugins.environmentdashboard;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.TopLevelItem;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.View;
import hudson.model.ViewDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.environmentdashboard.utils.DBConnection;
import org.jenkinsci.plugins.environmentdashboard.utils.CustomDBConnection;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import java.text.SimpleDateFormat;


/**
 * Class to provide build wrapper for Dashboard.
 * @author vipin
 * @date 15/10/2014
 */
public class EnvDashboardView extends View {

    private String envOrder = null;

    private String compOrder = null;

    private String tags = null;

    private String betaCustomers = null;

    private String deployHistory = null;
	
	private String dbUser = null;
	
	private Secret dbPassword = null;

    @DataBoundConstructor
    public EnvDashboardView(final String name, final String envOrder, final String compOrder, final String tags, final String betaCustomers, final String deployHistory, final String dbUser, final String dbPassword) {
        super(name, Hudson.getInstance());
        this.envOrder = envOrder;
        this.compOrder = compOrder;
        this.tags = tags;
        this.betaCustomers = betaCustomers;
        this.deployHistory = deployHistory;
		this.dbUser = dbUser;
		this.dbPassword = Secret.fromString(dbPassword);
    }

    static {
        ensureCorrectDBSchema();
    }

   private static void ensureCorrectDBSchema(){
       String returnComment = "";
       Connection conn = null;
       Statement stat = null;
       conn = DBConnection.getConnection();
       try {
           assert conn != null;
           stat = conn.createStatement();
       } catch (SQLException e) {
           System.out.println("E13" + e.getMessage());
       }
       try {
           stat.execute("ALTER TABLE env_dashboard ADD IF NOT EXISTS packageName VARCHAR(255);");
       } catch (SQLException e) {
           System.out.println("ensureCorrectDBSchema: Could not alter table to add package column to table env_dashboard.\n" + e.getMessage());
       } finally { 
           DBConnection.closeConnection(conn);
       }
       return;
   }

    @Override
    protected void submit(final StaplerRequest req) throws IOException, ServletException, FormException {
        req.bindJSON(this, req.getSubmittedForm());
    }

    @RequirePOST
    public void doSqlSubmit(final StaplerRequest req, StaplerResponse res) throws IOException, ServletException, FormException {
        checkPermission(Jenkins.ADMINISTER);

        Connection conn = null;
        Statement stat = null;
        conn = DBConnection.getConnection();
        try {
            assert conn != null;
            stat = conn.createStatement();
            String sql = req.getSubmittedForm().getString("sql");
            stat.execute(sql);
        } catch (SQLException e) {
            System.out.println("doSqlSubmit: Could not truncate table env_dashboard.\n" + e.getMessage());
        } finally { 
            DBConnection.closeConnection(conn);
        }
        res.forwardToPreviousPage(req);
    }

    @RequirePOST
    public void doPurgeSubmit(final StaplerRequest req, StaplerResponse res) throws IOException, ServletException, FormException {
        checkPermission(Jenkins.ADMINISTER);

        Connection conn = null;
        Statement stat = null;
        conn = DBConnection.getConnection();
        try {
            assert conn != null;
            stat = conn.createStatement();
            stat.execute("TRUNCATE TABLE env_dashboard");
        } catch (SQLException e) {
            System.out.println("doPurgeSubmit: Could not truncate table env_dashboard.\n" + e.getMessage());
        } finally { 
            DBConnection.closeConnection(conn);
        }
        res.forwardToPreviousPage(req);
    }

    @Override
    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        return Hudson.getInstance().doCreateItem(req, rsp);
    }

    @Extension
    public static final class DescriptorImpl extends ViewDescriptor {

        private String envOrder;
        private String compOrder;
        private String tags;
        private String betaCustomers;
        private String deployHistory;
		private String dbUser;
		private String dbPassword;

        /**
         * descriptor impl constructor This empty constructor is required for stapler. If you remove this constructor, text name of
         * "Build Pipeline View" will be not displayed in the "NewView" page
         */
        public DescriptorImpl() {
            load();
        }

        public static ArrayList<String> getCustomColumns(){
            Connection conn = null;
            Statement stat = null;
            ArrayList<String> columns;
            columns = new ArrayList<String>();
            String queryString="SELECT DISTINCT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS where TABLE_NAME='ENV_DASHBOARD';";
            String[] fields = {"envComp", "compName", "envName", "buildstatus", "buildJobUrl", "jobUrl", "buildNum", "created_at", "packageName"};
            boolean columnFound = false;
            try {
                ResultSet rs = null;
                conn = DBConnection.getConnection();

                try {
                    assert conn != null;
                    stat = conn.createStatement();
                } catch (SQLException e) {
                    System.out.println("getCustomColumns stmt:" + e.getMessage());
                }
                try {
                    assert stat != null;
                    rs = stat.executeQuery(queryString);
                } catch (SQLException e) {
                    System.out.println("getCustomColumns exec:" + e.getMessage());
                }
                String col = "";
                while (rs.next()) {
                    columnFound=false;
                    col = rs.getString("COLUMN_NAME");
                    for (String presetColumn : fields){
                        if (col.toLowerCase().equals(presetColumn.toLowerCase())){
                            columnFound = true;
                            break;
                        }
                    }
                    if (!columnFound){
                        columns.add(col.toLowerCase());
                    }
                }
                DBConnection.closeConnection(conn);
            } catch (SQLException e) {
                System.out.println("getCustomColumns:" + e.getMessage());
                return null;
            }
            return columns;
        }


        public ListBoxModel doFillColumnItems() {
            ListBoxModel m = new ListBoxModel();
            ArrayList<String> columns = getCustomColumns();
            int position = 0;
            m.add("Select column to remove", "");
            for (String col : columns){
                m.add(col, col);
            }
            return m;
        }

        @SuppressWarnings("unused")
        public FormValidation doDropColumn(@QueryParameter("column") final String column){
            Connection conn = null;
            Statement stat = null;
            if ("".equals(column)){
                return FormValidation.ok(); 
            }
            String queryString = "ALTER TABLE ENV_DASHBOARD DROP COLUMN " + column + ";";
            //Get DB connection
            conn = DBConnection.getConnection();

            try {
                assert conn != null;
                stat = conn.createStatement();
            } catch (SQLException e) {
                return FormValidation.error("Failed to create statement."); 
            }
            try {
                assert stat != null;
                stat.execute(queryString);
            } catch (SQLException e) {
                DBConnection.closeConnection(conn);
                return FormValidation.error("Failed to remove column: " + column + "\nThis column may have already been removed. Refresh to update the list of columns to remove."); 
            } 
            DBConnection.closeConnection(conn);

            return FormValidation.ok("Successfully removed column " + column + ".");
        }

        /**
         * get the display name
         *
         * @return display name
         */
        @Override
        public String getDisplayName() {
            return "Environment Dashboard";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            envOrder = formData.getString("envOrder");
            compOrder = formData.getString("compOrder");
            tags = formData.getString("tags");
            betaCustomers = formData.getString("betaCustomers");
            deployHistory = formData.getString("deployHistory");
			dbUser = formData.getString("dbUser");
			dbPassword = formData.getString("dbPassword");
            save();
            return super.configure(req,formData);
        }
    }


    public ArrayList<String> splitEnvOrder(String envOrder) {
        ArrayList<String> orderOfEnvs = new ArrayList<String>();
        if (! "".equals(envOrder)) {
            orderOfEnvs = new ArrayList<String>(Arrays.asList(envOrder.split("\\s*,\\s*")));
        }
        return orderOfEnvs;
    }

    public ArrayList<String> splitCompOrder(String compOrder) {
        ArrayList<String> orderOfComps = new ArrayList<String>();
        if (! "".equals(compOrder)) {
            orderOfComps = new ArrayList<String>(Arrays.asList(compOrder.split("\\s*,\\s*")));
        }
        return orderOfComps;
    }

    public ArrayList<String> splitTags(String tags) {
        ArrayList<String> orderOfTags = new ArrayList<String>();
        if (! "".equals(tags)) {
            orderOfTags = new ArrayList<String>(Arrays.asList(tags.split("\\s*,\\s*")));
        }
        return orderOfTags;
    }

    public ArrayList<String> splitBetaCustomers(String betaCustomers) {
        ArrayList<String> orderOfBetaCustomers = new ArrayList<String>();
        if (betaCustomers != null && ! "".equals(betaCustomers)) {
            orderOfBetaCustomers = new ArrayList<String>(Arrays.asList(betaCustomers.split("\\s*,\\s*")));
        }
        return orderOfBetaCustomers;
    }

    public ResultSet runQuery(Connection conn, String queryString) {
        Statement stat = null;

        ResultSet rs = null;
        
        try {
            assert conn != null;
            stat = conn.createStatement();
        } catch (SQLException e) {
            System.err.println("runQuery stmt:" + e.getMessage());
            e.printStackTrace();
        }
        try {
            assert stat != null;
            rs = stat.executeQuery(queryString);
        } catch (SQLException e) {
            System.err.println("runQuery exec:" + e.getMessage());
            e.printStackTrace();
        }
        return rs;
    }

    public ArrayList<String> getOrderOfEnvs() {
        ArrayList<String> orderOfEnvs;
        orderOfEnvs = splitEnvOrder(envOrder);
        if (orderOfEnvs == null || orderOfEnvs.isEmpty()){
            String queryString="select distinct envname from env_dashboard order by envname;";
            try {
                Connection conn = DBConnection.getConnection();
                ResultSet rs = runQuery(conn, queryString);
                if (rs == null ) {
                    return null;
                }
                while (rs.next()) {
                    if (orderOfEnvs != null) {
                        orderOfEnvs.add(rs.getString("envName"));
                    }
                }
                DBConnection.closeConnection(conn);
            } catch (SQLException e) {
                System.out.println("getOrderOfEnvs:" + e.getMessage());
                return null;
            }
        }
        return orderOfEnvs;
    }

    public ArrayList<String> getOrderOfComps() {
        ArrayList<String> orderOfComps;
        orderOfComps = splitCompOrder(compOrder);
        if (orderOfComps == null || orderOfComps.isEmpty()){
            String queryString="select distinct compname from env_dashboard order by compname;";
            try {
                Connection conn = DBConnection.getConnection();
                ResultSet rs = runQuery(conn, queryString);
                while (rs.next()) {
                    if (orderOfComps != null) {
                        orderOfComps.add(rs.getString("compName"));
                    }
                }
                DBConnection.closeConnection(conn);
            } catch (SQLException e) {
                System.out.println("getOrderOfComps:" + e.getMessage());
                return null;
            }
        }
        return orderOfComps;
    }

    public ArrayList<String> getOrderOfTags(String client, String env) {
        ArrayList<String> orderOfTags = splitTags(tags);
        ArrayList<String> betaCustomersList = splitBetaCustomers(betaCustomers);

        // alpha and beta tags must be approved
        for (Iterator<String> it=orderOfTags.iterator(); it.hasNext();) {
            String tag = it.next();
            if (tag.contains("beta") || tag.contains("alpha") || (tag.contains("temp") && env.equals("PRD"))) {
                boolean delete = true;
                for(String customer : betaCustomersList) {
                    if ( (client.replace(" Backend", ":") + tag).startsWith(customer)) {
                        delete = false;
                        break;
                    }
                }
                if (delete) {
                    it.remove();
                }
                // temp tags will only be deleted prd
                // if (delete && (env.equals("PRD") || tag.contains("beta") || tag.contains("alpha"))) {
                //     it.remove();
                // }
            }
        }

        return orderOfTags;
    }

    public Integer getLimitDeployHistory() {
        Integer lastDeploy;
        if ( deployHistory == null || deployHistory.equals("") ) {
            return 10;
        } else {
            try {
                lastDeploy = Integer.parseInt(deployHistory);
            } catch (NumberFormatException e) {
                return 10;
            }
        }
        return lastDeploy;
    }

    public ArrayList<String> getDeployments(String env, Integer lastDeploy) {
        if ( lastDeploy <= 0 ) {
            lastDeploy = 10;
        }
        ArrayList<String> deployments;
        deployments = new ArrayList<String>();
        String queryString="select top " + lastDeploy + " created_at from env_dashboard where 1=0 and envName ='" + env + "' order by created_at desc;";
            try {
                Connection conn = DBConnection.getConnection();
                ResultSet rs = runQuery(conn, queryString);
                while (rs.next()) {
                    deployments.add(rs.getString("created_at"));
                }
                DBConnection.closeConnection(conn);
            } catch (SQLException e) {
                System.out.println("getDeployments:" + e.getMessage());
                return null;
            }
        return deployments;
    }

    public String anyJobsConfigured() {
        ArrayList<String> orderOfEnvs;
        orderOfEnvs = getOrderOfEnvs();
        if (orderOfEnvs == null || orderOfEnvs.isEmpty()){
            return "NONE";
        } else {
            return "ENVS";
        }
    }

    public String getNiceTimeStamp(String timeStamp) {
        return timeStamp.substring(0,19);
    }

    public HashMap getCompDeployed(String env, String time) {
        HashMap<String, String> deployment;
        deployment = new HashMap<String, String>();
        String[] fields = {"buildstatus", "compName", "buildJobUrl", "jobUrl", "buildNum", "packageName"};
        String queryString = "select " + StringUtils.join(fields, ", ").replace(".$","") + " from env_dashboard where 1=0 and envName = '" + env + "' and created_at = '" + time + "';";
        try {
            Connection conn = DBConnection.getConnection();
            ResultSet rs = runQuery(conn, queryString);
            rs.next();
            for (String field : fields) {
                deployment.put(field, rs.getString(field));
            }
            DBConnection.closeConnection(conn);
        } catch (SQLException e) {
            System.out.println("getCompDeployed:" + e.getMessage());
            System.out.println("Error executing: " + queryString);
        }
        return deployment;
    }

    public ArrayList<String> getCustomDBColumns(){
        return DescriptorImpl.getCustomColumns();
    }

    public ArrayList<HashMap<String, String>> getDeploymentsByComp(String comp, Integer lastDeploy) {
        if ( lastDeploy <= 0 ) {
            lastDeploy = 10;
        }
        ArrayList<HashMap<String, String>> deployments;
        deployments = new ArrayList<HashMap<String, String>>();
        HashMap<String, String> hash;
        String[] fields = {"envName", "buildstatus", "buildJobUrl", "jobUrl", "buildNum", "created_at", "packageName"};
        ArrayList<String> allDBFields = getCustomDBColumns();
        for (String field : fields ){
            allDBFields.add(field);
        }
        String queryString="select top " + lastDeploy + " * from env_dashboard where compName='" + comp + "' and 1=0 order by created_at desc;";
            try {
                Connection conn = DBConnection.getConnection();
                ResultSet rs = runQuery(conn, queryString);
                while (rs.next()) {
                    hash = new HashMap<String, String>();
                    for (String field : allDBFields) {
                        hash.put(field, rs.getString(field));
                    }
                    deployments.add(hash);
                }
                DBConnection.closeConnection(conn);
            } catch (SQLException e) {
                System.out.println("getDeploymentsByComp:" + e.getMessage());
                return null;
            }
        return deployments;
    }

    public ArrayList<HashMap<String, String>> getDeploymentsByCompEnv(String comp, String env, Integer lastDeploy) {
        if ( lastDeploy <= 0 ) {
            lastDeploy = 10;
        }
        ArrayList<HashMap<String, String>> deployments;
        deployments = new ArrayList<HashMap<String, String>>();
        HashMap<String, String> hash;
        String[] fields = {"envName", "buildstatus", "buildJobUrl", "jobUrl", "buildNum", "created_at", "packageName"};
        ArrayList<String> allDBFields = getCustomDBColumns();
        for (String field : fields ){
            allDBFields.add(field);
        }
        String queryString="select top " + lastDeploy + " " +  StringUtils.join(allDBFields, ", ").replace(".$","") + " from env_dashboard where compName='" + comp + "' and envName='" + env + "' order by created_at desc;";
            try {
                Connection conn = DBConnection.getConnection();
                ResultSet rs = runQuery(conn, queryString);
                while (rs.next()) {
                    hash = new HashMap<String, String>();
                    for (String field : allDBFields) {
                        hash.put(field, rs.getString(field));
                    }
                    deployments.add(hash);
                }
                DBConnection.closeConnection(conn);
            } catch (SQLException e) {
                System.out.println("getDeploymentsByCompEnv:" + e.getMessage());
                return null;
            }
        return deployments;
    }

    public HashMap getCompLastDeployed(String env, String comp) {
        HashMap<String, String> deployment;
        deployment = new HashMap<String, String>();
        String[] fields = {"buildstatus", "buildJobUrl", "jobUrl", "buildNum", "created_at", "packageName"};
        ArrayList<String> allDBFields = getCustomDBColumns();
        for (String field : fields ){
            allDBFields.add(field);
        }
        String queryString = "select top 1 " + StringUtils.join(allDBFields, ", ").replace(".$","") + " from env_dashboard where envName = '" + env + "' and compName = '" + comp + "' order by created_at desc;";
        try {
            Connection conn = DBConnection.getConnection();
            ResultSet rs = runQuery(conn, queryString);
            rs.next();
            for (String field : allDBFields) {
                deployment.put(field, rs.getString(field));
            }
            DBConnection.closeConnection(conn);
        } catch (SQLException e) {
            if (e.getErrorCode() == 2000) {
                //We'll assume this comp has never been deployed to this env            }
            } else {
                System.err.println("getCompLastDeployed:" + e.getMessage());
                e.printStackTrace();
                System.out.println("Error executing: " + queryString);
            }
        }
        return deployment;
    }

    @Override
    public Collection<TopLevelItem> getItems() {
        return null;
    }

    public String getEnvOrder() {
        return envOrder;
    }

    public void setEnvOrder(final String envOrder) {
        this.envOrder = envOrder;
    }

    public String getCompOrder() {
        return compOrder;
    }

    public String getBetaCustomers() {
        return betaCustomers;
    }

    public String getTags() {
        return tags;
    }

    public void setCompOrder(final String compOrder) {
        this.compOrder = compOrder;
    }

    public void setBetaCustomers(final String betaCustomers) {
        this.betaCustomers = betaCustomers;
    }

    public void setTags(final String tags) {
        this.tags = tags;
    }

    public String getDeployHistory() {
        return deployHistory;
    }

	public void setDeployHistory(final String deployHistory) {
        this.deployHistory = deployHistory;
    }
	
    public void setdbUser(final String dbUser) {
        this.dbUser = dbUser;
    }
	
	public String getdbUser() {
        return dbUser;
    }
	
	
	public void setdbPassword(final String dbPassword) {
        this.dbPassword = Secret.fromString(dbPassword);
    }
	
	public String getdbPassword() {
        return Secret.toString(dbPassword);
    }
	

    //@JavaScriptMethod
	//public String getdbPasswordJavaScript() {
    //    return Secret.toString(dbPassword);
    //}

	@JavaScriptMethod
	public String getTestDataFromLocalSQLserver() {
	
	   String timeStamp = new SimpleDateFormat("yyyyMMdd-hh:mm:ss-aaa-z").format(new java.util.Date());
	   System.out.println(timeStamp + ": At getTestDataFromLocalSQLserver function");
	
       Connection conn = null;
       Statement stat = null;
	   
	   String someString = null;
	   
       //conn = CustomDBConnection.getConnection("adoskara-pc2", "1433", "test", getdbUser(), getdbPassword());
	   //String SQL = "select * from dbo.persons where name = 'john';";
	   
	   System.out.println(timeStamp + ": Getting the user executing Jenkins...");
	   String user = System.getProperty("user.name");
	   System.out.println(user);
	   
	   //conn = CustomDBConnection.getConnection("TESTSQLTST04", "1433", "test_warehouse_dev04", getdbUser(), getdbPassword());
	   //String SQL = "select age_range_id, age_range from dbo.age_range where age_range_id = 1;";
	   
	   conn = CustomDBConnection.getConnection("mydbserver1", "1433", "tutorialdb", getdbUser(), getdbPassword());
	   String SQL = "select customerid, name from customers where name = 'orlando';";
	   
	   
       try {
           assert conn != null;
           stat = conn.createStatement();
       } catch (SQLException e) {
           System.out.println("E13" + e.getMessage());
       }
       try {
	       System.out.println(timeStamp + ": About to execute SQL query...");
           ResultSet rs = stat.executeQuery(SQL);
		   //Iterate through the data in the result set and display it.
           while (rs.next()) {
                //System.out.println(rs.getString("PersonID") + " " + rs.getString("name"));
				//someString = rs.getString("PersonID") + " " + rs.getString("name");
				
				//System.out.println(rs.getString("age_range_id") + " " + rs.getString("age_range"));
				//someString = rs.getString("age_range_id") + " " + rs.getString("age_range");
				
				System.out.println(rs.getString("customerid") + " " + rs.getString("name"));
				someString = rs.getString("customerid") + " " + rs.getString("name");
           }
		   
       } catch (SQLException e) {
           System.out.println(timeStamp + ": Something went wrong in getTestDataFromLocalSQLserver function.\n" + e.getMessage());
       } finally { 
           CustomDBConnection.closeConnection(conn);
       }
	   
	   
       //return "success";
	   
	   return someString;
	   
    }
	
	public Secret getdbPasswordSecret() {
        return dbPassword;
    }

    @Override
    public boolean contains(TopLevelItem topLevelItem) {
        return false;
    }

    @Override
    public void onJobRenamed(Item item, String s, String s2) {

    }
}

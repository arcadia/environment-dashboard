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

import hudson.model.User;

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
import java.util.Date;

import javax.servlet.ServletException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonArrayBuilder;
import javax.json.JsonArray;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import javax.json.JsonObjectBuilder;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.environmentdashboard.utils.DBConnection;
import org.jenkinsci.plugins.environmentdashboard.utils.CustomDBConnection;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.HttpResponses;

import java.text.SimpleDateFormat;


//Needed for AD connectivity
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import jenkins.model.ModelObjectWithContextMenu.ContextMenu;



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
	
	private Boolean SQLauth = null;
	
	
	private String LDAPserver = null;
	
	private String LDAPuser = null;
	
	private Secret LDAPpassword = null;
	
	private String webtags = null;
	
	private String binderyfronttags = null;

	
    @DataBoundConstructor
    public EnvDashboardView(final String name, final String envOrder, final String compOrder, final String tags, final String betaCustomers, final String deployHistory, final String dbUser, final String dbPassword, final Boolean SQLauth, final String LDAPserver, final String LDAPuser, final String LDAPpassword, final String webtags, final String binderyfronttags) {
        super(name, Hudson.getInstance());
        this.envOrder = envOrder;
        this.compOrder = compOrder;
        this.tags = tags;
        this.betaCustomers = betaCustomers;
        this.deployHistory = deployHistory;
		this.dbUser = dbUser;
		this.dbPassword = Secret.fromString(dbPassword);
		this.SQLauth = SQLauth;
		
		this.LDAPserver = LDAPserver;
		this.LDAPuser = LDAPuser;
		this.LDAPpassword = Secret.fromString(LDAPpassword);
		
		this.webtags = webtags;
		this.binderyfronttags = binderyfronttags;
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
		private Boolean SQLauth;
		
		private String LDAPserver;
		private String LDAPuser;
		private String LDAPpassword;
		
		private String webtags;
		private String binderyfronttags;

		
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
			SQLauth = formData.optBoolean("SQLauth");
			
			LDAPserver = formData.getString("LDAPserver");
			LDAPuser = formData.getString("LDAPuser");
			LDAPpassword = formData.getString("LDAPpassword");
			
			webtags = formData.getString("webtags");
			binderyfronttags = formData.getString("binderyfronttags");
			
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
		
		//System.err.println("At getOrderOfEnvs function...");
	
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
						
						//System.err.println(rs.getString("envName"));
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
						//System.out.println(rs.getString("compName"));
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
	
	
	public ArrayList<String> getOrderOfWebOrBinderyFrontTags(String type) {
		
		//System.out.println("At getOrderOfWebOrBinderyFrontTags function");
		//System.out.println(type);
		
		ArrayList<String> orderOfTags;
		if (type.equals("web"))
		{
			//System.out.println("Getting web tags now...");
			orderOfTags = splitTags(webtags);
		}
		else
		{
			orderOfTags = splitTags(binderyfronttags);
		}
		
		//System.out.println(orderOfTags);
		
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
		
		//if (comp.equals("test Backend"))
		/*
		if (comp.equals("test CRjob"))
		{
			System.out.println("At getCompLastDeployed");
			System.out.println("Passed arguments:");
			System.out.println(env);
			System.out.println(comp);
		}
		*/
		
		
        HashMap<String, String> deployment;
        deployment = new HashMap<String, String>();
        String[] fields = {"buildstatus", "buildJobUrl", "jobUrl", "buildNum", "created_at", "packageName"};
        ArrayList<String> allDBFields = getCustomDBColumns();
        for (String field : fields ){
            allDBFields.add(field);
        }
        String queryString = "select top 1 " + StringUtils.join(allDBFields, ", ").replace(".$","") + " from env_dashboard where envName = '" + env + "' and compName = '" + comp + "' order by created_at desc;";
		
		//if (comp.equals("test Backend"))
		/*
		if (comp.equals("test CRjob"))
		{
			//System.out.println("Built SQL query:");
			//System.out.println(queryString);
		}
		*/
		
        try {
            Connection conn = DBConnection.getConnection();
            ResultSet rs = runQuery(conn, queryString);
            rs.next();
            for (String field : allDBFields) {
                deployment.put(field, rs.getString(field));
				
				//if (comp.equals("test Backend"))
				/*
				if (comp.equals("test CRjob"))
				{
					System.out.println(rs.getString(field));
				}
				*/
				
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
	
	public String getwebtags() {
        return webtags;
    }
	
	public String getbinderyfronttags() {
        return binderyfronttags;
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
	
	public void setwebtags(final String webtags) {
        this.webtags = webtags;
    }
	
	public void setbinderyfronttags(final String binderyfronttags) {
        this.binderyfronttags = binderyfronttags;
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
	
	
	
	public void setLDAPserver(final String LDAPserver) {
        this.LDAPserver = LDAPserver;
    }
	
	public void setLDAPuser(final String LDAPuser) {
        this.LDAPuser = LDAPuser;
    }
	
	public String getdbUser() {
        return dbUser;
    }
	
	
	public String getLDAPserver() {
        return LDAPserver;
    }
	
	public String getLDAPuser() {
        return LDAPuser;
    }
	
	
	public void setSQLauth(final Boolean SQLauth) {
        this.SQLauth = SQLauth;
    }
	
	public Boolean getSQLauth() {
        return SQLauth;
    }
	
	public void setdbPassword(final String dbPassword) {
        this.dbPassword = Secret.fromString(dbPassword);
    }
	
	public String getdbPassword() {
        return Secret.toString(dbPassword);
    }
	
	
	public void setLDAPpassword(final String LDAPpassword) {
        this.LDAPpassword = Secret.fromString(LDAPpassword);
    }
	
	public String getLDAPpassword() {
        return Secret.toString(LDAPpassword);
    }



	@JavaScriptMethod
	public String getTestDataFromLocalSQLserver() {
	
	   System.out.println(getCurentDateTime() + ": At getTestDataFromLocalSQLserver function");
	
       Connection conn = null;
       Statement stat = null;
	   
	   String someString = new String();
	  	   
	   System.out.println(getCurentDateTime() + ": Getting the user executing Jenkins...");
	   String user = System.getProperty("user.name");
	   System.out.println(user);
	   
	   conn = CustomDBConnection.getConnection("mycomputer", "1433", "test", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "select * from dbo.persons where name = 'john';";
	   String SQL = "select * from dbo.persons;";
	   
	   //conn = CustomDBConnection.getConnection("TESTSQLTST04", "1433", "test_warehouse_dev04", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "select age_range_id, age_range from dbo.age_range where age_range_id = 1;";
	   
	   //conn = CustomDBConnection.getConnection("mydbserver1", "1433", "tutorialdb", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "select customerid, name from customers where name = 'orlando';";
	   
	   
       try {
           assert conn != null;
           stat = conn.createStatement();
       } catch (SQLException e) {
           System.out.println("E13" + e.getMessage());
       }
       try {
	       System.out.println(getCurentDateTime() + ": About to execute SQL query...");
           ResultSet rs = stat.executeQuery(SQL);
		   //Iterate through the data in the result set and display it.
           while (rs.next()) {
                System.out.println(rs.getString("PersonID") + " " + rs.getString("name"));
				
				//someString = rs.getString("PersonID") + " " + rs.getString("name");
				someString += rs.getString("PersonID") + " " + rs.getString("name") + "\n";
				
				//System.out.println(rs.getString("age_range_id") + " " + rs.getString("age_range"));
				//someString = rs.getString("age_range_id") + " " + rs.getString("age_range");
				
				//System.out.println(rs.getString("customerid") + " " + rs.getString("name"));
				//someString = rs.getString("customerid") + " " + rs.getString("name");
           }
		   
       } catch (SQLException e) {
           System.out.println(getCurentDateTime() + ": Something went wrong in getTestDataFromLocalSQLserver function.\n" + e.getMessage());
       } finally { 
           CustomDBConnection.closeConnection(conn);
       }
	   
	   
       //return "success";
	   
	   return someString;
	   
    }
	
	
	@JavaScriptMethod
	public String parseSQLquery(String SQL, String server) {
	
	   System.out.println(getCurentDateTime() + ": At parseSQLquery function");
	   System.out.println(getCurentDateTime() + ": Here is the SQl query passed to parseSQLquery function");
	   System.out.println(SQL);
	   System.out.println(server);
	   
	   
	    String convertedSQL = new String();
        String newLineInd = "\n";
		String[] arrOfStr = SQL.split("\n");
		for (String a: arrOfStr)
		{
		
			 //check if the go statement is by itself
			if (a.trim().toUpperCase().matches("GO($)"))
			{
					//System.out.println(a.trim().toUpperCase().matches("GO($)"));
					convertedSQL = convertedSQL + ";" + newLineInd;
			}
			//check if the go statement is followed by integer
			else if (a.trim().toUpperCase().matches("GO(\\s+\\d+)"))
			{
					//System.out.println(a.trim().toUpperCase().matches("GO(\\s+\\d+)"));
					convertedSQL = convertedSQL + ";" + newLineInd;
			}
			else
			{
					convertedSQL = convertedSQL + a + newLineInd;
			}
			
		}

	    SQL = convertedSQL;
		
		//System.out.println(arrOfStr[0]);
		//System.out.println(arrOfStr[1]);
		System.out.println(getCurentDateTime() + ": Here is the SQl query after being processed for GO statements");
		System.out.println(SQL);
		
	
       Connection conn = null;
       Statement stat = null;
	   
	   String returnString = null;
	  	   
	   System.out.println(getCurentDateTime() + ": Getting the user executing Jenkins...");
	   String user = System.getProperty("user.name");
	   System.out.println(user);
	   
	   
	   System.out.println(getCurentDateTime() + ": Getting the SQL version used by " + server + "...");
	   String returnValue = getSQLserverVersion(server, "version");
	   if(returnValue.contains("failed"))
	   {
			System.out.println(returnValue);
			returnString = returnValue;
			return returnString;
	   }
	   else if(returnValue.contains("Microsoft SQL Server 2008"))
	   {
		   //Append the response format
		   SQL = "SET FMTONLY ON;\n" +
		   SQL + "\n" +
		   "SET FMTONLY OFF;";
	   }
	   else
	   {
		   //Append the response format
		   SQL = SQL.replace("'","''");
		   SQL = "sp_describe_first_result_set @tsql = N'" + SQL + "'";
		  
	   }
	   
	   System.out.println(SQL);
	   
	   
	   
	   //conn = CustomDBConnection.getConnection("mycomputer", "1433", "test", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "select * from dbo.persons where name = 'john';";
	   //String SQL = "select * from dbo.persons;";
	   
	   conn = CustomDBConnection.getConnection(server, "1433", "placeholderForDB", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "select age_range_id, age_range from dbo.age_range where age_range_id = 1;";
	   
	   //conn = CustomDBConnection.getConnection("mydbserver1", "1433", "tutorialdb", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "select customerid, name from customers where name = 'orlando';";
	   
	   
	  
	   
       try {
           assert conn != null;
           stat = conn.createStatement();
       } catch (Exception e) {
           System.out.println("E13" + " failed " + e.getMessage());
       }
       try {
	       System.out.println(getCurentDateTime() + ": About to execute SQL query...");
		   stat.execute(SQL);
		   System.out.println(getCurentDateTime() + ": The command was successfully parsed");
		   returnString = "The command was successfully parsed";
       }
	   catch (Exception e) 
	   {
		    System.out.println(getCurentDateTime() + ": Something failed at parseSQLquery function"); 
			System.out.println(e.toString());			
            //e.printStackTrace();  
			returnString = "Something failed at parseSQLquery function: " + e.getMessage();
       } 
	   finally 
	   { 
           CustomDBConnection.closeConnection(conn);
		   	if(returnString == null)
			{
				returnString = "failed";
			}
       }
	  
	  
	   return returnString;
	   
	   
    }
	
	
	@JavaScriptMethod
	  public String AddCRjobSteps(String server, String job_name, JSONObject CRjobData) {
	
	   System.out.println(getCurentDateTime() + ": At AddCRjobSteps function");
	   System.out.println(getCurentDateTime() + ": Here are the arguments passed:");
	   System.out.println(server);
	   System.out.println(job_name);
	   
	   //Prepare SQL statements
	   String SQLdelete = "USE msdb;\n" +
			"EXEC dbo.sp_delete_jobstep  \n" +
			"    @job_name = N'" + job_name + "',  \n" +
			"    @step_id = 0;";
	   
	   String SQLreset = "USE msdb;\n" +
			"EXEC dbo.sp_update_job  \n" +
			"    @job_name = N'" + job_name + "',  \n" +
			"    @start_step_id = 1;";
	   
	   JSONArray array = CRjobData.getJSONArray("CRjobsteps");
		
		job_name = null;
		String step_name = null;
		String subsystem = null;
		String command = null;
		int on_success_action = 0;
		int on_fail_action = 0;
	  
       Connection conn = null;
       Statement stat = null;
	   String error = new String();
	   String returnString = null;
	   
	   
	   
	   
	   
	   
	   //conn = CustomDBConnection.getConnection("mycomputer", "1433", "test", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "select * from dbo.persons where name = 'john';";
	   //String SQL = "select * from dbo.persons;";
	   
	   conn = CustomDBConnection.getConnection(server, "1433", "placeholderForDB", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "select age_range_id, age_range from dbo.age_range where age_range_id = 1;";
	   
	   //conn = CustomDBConnection.getConnection("mydbserver1", "1433", "tutorialdb", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "select customerid, name from customers where name = 'orlando';";
	   
	   
	   //Check if server is reachable
	   if (!testServerConnection(server))
	   {
			error = "failed " + server + " is not reachable";
			System.out.println(getCurentDateTime() + ": " + error);
			returnString = error;
			return returnString;
	   }
	  
	   
       try {
           assert conn != null;
           stat = conn.createStatement();
       } catch (Exception e) {
           System.out.println("E13" + " failed " + e.getMessage());
       }
       try {
	   
	   	   	System.out.println(getCurentDateTime() + ": About to execute SQL queries...");
		    System.out.println(SQLdelete);
			System.out.println(SQLreset);
			stat.execute(SQLdelete);
			stat.execute(SQLreset);
			System.out.println(getCurentDateTime() + ": Successfully deleted CR job steps and performed a reset");
	   
	   	   for(int i = 0 ; i < array.size(); i++)
		   {
		   
				//System.out.println(array.getJSONObject(i).getString("job_name"));  
				//System.out.println(array.getJSONObject(i).getString("step_name")); 
				//System.out.println(array.getJSONObject(i).getString("subsystem")); 
				//System.out.println(array.getJSONObject(i).getString("command")); 
				//System.out.println(array.getJSONObject(i).getString("on_success_action")); 
				//System.out.println(array.getJSONObject(i).getString("on_fail_action"));
				
				job_name = array.getJSONObject(i).getString("job_name");
				step_name = array.getJSONObject(i).getString("step_name");
				subsystem = array.getJSONObject(i).getString("subsystem");
				command = array.getJSONObject(i).getString("command");
				on_success_action = Integer.parseInt(array.getJSONObject(i).getString("on_success_action"));
				on_fail_action = Integer.parseInt(array.getJSONObject(i).getString("on_fail_action"));
		   
			   //Prepare SQL statement
			   step_name = step_name.replace("'","''");
			   command = command.replace("'","''");
			   
			   
			   String SQL = "USE msdb;\n" +
					"EXEC dbo.sp_add_jobstep  \n" +
					"    @job_name = N'" + job_name + "',  \n" +
					"    @step_name = N'" + step_name + "', \n" +
					"	@subsystem = N'" + subsystem + "', \n" +
					"	@command = N'" + command + "', \n" +
					"	@on_success_action = " + on_success_action + ", \n" +
					"	@on_fail_action = " + on_fail_action + ";";
			   
			   
				System.out.println(getCurentDateTime() + ": About to execute SQL query...");
				System.out.println(SQL);
				stat.execute(SQL);
				
				
				
		   }
	   
		   System.out.println(getCurentDateTime() + ": CR job was successfully updated");
		   returnString = "CR job was successfully updated";
       }
	   catch (Exception e) 
	   {
		    System.out.println(getCurentDateTime() + ": Something failed at AddCRjobSteps function"); 
			System.out.println(e.toString());			
            //e.printStackTrace();  
			returnString = "Something failed at AddCRjobSteps function: " + e.getMessage();
       } 
	   finally 
	   { 
           CustomDBConnection.closeConnection(conn);
		   	if(returnString == null)
			{
				returnString = "failed";
			}
       }
	  
	  
	   return returnString;
	   
	   
    }
	
	
	@JavaScriptMethod
	public String maintainConnectivityToProxy() {
	
	   //System.out.println(getCurentDateTime() + ": At DummyFunction function");
	   String returnString = "success";
	   return returnString;
	   
    }
	
	
	@JavaScriptMethod
	public String getCRjobStepsSQLquery(String job, String customer, String env) 
	{
	
	   String returnString = null;
	   String error = new String();
	   String activeDB = null;
	   String activeServer = null;
		
	   System.out.println(getCurentDateTime() + ": At getCRjobStepsSQLquery function");
	   System.out.println(getCurentDateTime() + ": Here is the change request job passed to getCRjobStepsSQLquery function:");
	   System.out.println(job);
	   
		
		String returnValue = getActiveDBprovEnvAndServerSQLquery(customer, env);
		if(returnValue.contains("failed"))
		{
			System.out.println(returnValue);
			returnString = returnValue + " failedAtgetCRjobStepsSQLquery";
			return returnString;
		}
		else
		{

			String[] arrOfStr = returnValue.split(",");
			//for (String a: arrOfStr)
			  //  System.out.println(a);
			
			activeDB = arrOfStr[0];
			System.out.println(activeDB);
			
			activeServer = arrOfStr[2];
			System.out.println(activeServer);
			
		}			
	  
	  
	  	//String activeServer = "TESTSQLTST04";
		
	   
	   //Check if server is reachable
	   if (!testServerConnection(activeServer))
	   {
			error = "failed " + activeServer + " is not reachable";
			System.out.println(getCurentDateTime() + ": " + error);
			returnString = error + " failedAtgetCRjobStepsSQLquery";
			return returnString;
	   }
	  
       Connection conn = null;
       Statement stat = null;
	   
	   String CRjobSteps = new String();
	   String CRjobInfo = new String();
	   
	   
	   //conn = CustomDBConnection.getConnection("mycomputer", "1433", "msdb", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "EXEC dbo.sp_help_job @job_name = N'" + job + "',  @job_aspect = N'steps';";
	   //System.out.println(getCurentDateTime() + ": Here is the built change request:");
	   //System.out.println(SQL);
	   
	   //conn = CustomDBConnection.getConnection("TESTSQLTST04", "1433", "test_warehouse_dev04", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "select age_range_id, age_range from dbo.age_range where age_range_id = 1;";
	   
	   conn = CustomDBConnection.getConnection(activeServer, "1433", "placeholderForDB", getdbUser(), getdbPassword(), getSQLauth());
	   //SQL = "EXEC dbo.sp_help_job @job_name = N'" + job + "',  @job_aspect = N'steps';";
	   String SQL = "use msdb; EXEC dbo.sp_help_job @job_name = N'" + job + "',  @job_aspect = N'steps';";
	   
	   //conn = CustomDBConnection.getConnection("mydbserver1", "1433", "tutorialdb", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "select customerid, name from customers where name = 'orlando';";
	   
	   
	   
	   
       try 
	   {
           if (conn == null){throw new Exception("Failed to create connection to database");};
           stat = conn.createStatement();
       } 
	   catch (Exception e)
	   {
		   error = "E13" + " failed " + e.getMessage();
           System.out.println(error);
		   
		   returnString = error + " failedAtgetCRjobStepsSQLquery";
		   return returnString;

       }
	   
	   
       try 
	   {
	   
		   JsonArrayBuilder jarr = Json.createArrayBuilder();
		   JsonArray arr = null;
		   JsonObject joSteps = null;
		   JsonObject joInfo = null;
		
		   String retrievedJob = null;

			//Check if the NJ Start exists
			System.out.println(getCurentDateTime() + ": Check if " + job + " exists...");
			ResultSet rs = stat.executeQuery("use msdb; SELECT name FROM dbo.sysjobs WHERE name = '" + job + "'");
			retrievedJob = null;
			while (rs.next())
			{
				System.out.println(rs.getString("name"));
				retrievedJob = rs.getString("name");
			}

			if (retrievedJob == null || retrievedJob.isEmpty())
			{
				System.out.println(getCurentDateTime() + ": " + job + " doesn't exist");
				  jarr.add(Json.createObjectBuilder()
						  .add("name", "doesnotexist")
					  .build());
					  
				arr = jarr.build();
			    joInfo = Json.createObjectBuilder().add("info", arr).build();
			    System.out.println(joInfo);
			   
			    returnString = joInfo.toString();	
			}
			else
			{
				System.out.println(getCurentDateTime() + ": " + job + " exists");
				
				System.out.println(getCurentDateTime() + ": About to execute SQL query for retrieving CR job steps...");
			   rs = stat.executeQuery(SQL);
			   
			   //Iterate through the data in the result set and display it.
			   
			   while (rs.next()) {
					
					String failAction = rs.getString("on_fail_action");
					String cleanedUpFailAction = failAction.substring(failAction.indexOf("(")+1,failAction.indexOf(")"));
					
					String successAction = rs.getString("on_success_action");
					String cleanedUpSuccessAction = successAction.substring(successAction.indexOf("(")+1,successAction.indexOf(")"));
					
					System.out.println(rs.getString("step_id") + " " + rs.getString("step_name") + " " + cleanedUpSuccessAction + " " + cleanedUpFailAction);
					//CRjobSteps += rs.getString("step_id") + " " + rs.getString("step_name") + " " + cleanedUpFailAction + "\n";
					
					//System.out.println(rs.getString("age_range_id") + " " + rs.getString("age_range"));
					//CRjobSteps = rs.getString("age_range_id") + " " + rs.getString("age_range");
					
					//System.out.println(rs.getString("customerid") + " " + rs.getString("name"));
					//CRjobSteps = rs.getString("customerid") + " " + rs.getString("name");
					
					jarr.add(Json.createObjectBuilder()
						  .add("step_id", rs.getString("step_id"))
						  .add("step_name", rs.getString("step_name"))
						  .add("subsystem", rs.getString("subsystem"))
						  .add("command", rs.getString("command"))
						  .add("on_success_action", cleanedUpSuccessAction)
						  .add("on_fail_action", cleanedUpFailAction)
					  .build());
			   }
			   
			   arr = jarr.build();
			   joSteps = Json.createObjectBuilder().add("steps", arr).build();
			   //System.out.println(joSteps);
				
				
				//Let's append job info itself
				//SQL = "EXEC dbo.sp_help_job @job_name = N'" + job + "',  @job_aspect = N'job';";
				SQL = "use msdb; EXEC dbo.sp_help_job @job_name = N'" + job + "',  @job_aspect = N'job';";
				System.out.println(getCurentDateTime() + ": About to execute SQL query for retriving CR job status info...");
				rs = stat.executeQuery(SQL);
				
				while (rs.next()) {
				
					String mappedLastRunOutcome = null;
					switch (rs.getInt("last_run_outcome")) {
					  case 0:
						mappedLastRunOutcome = "Failed";
						break;
					  case 1:
						mappedLastRunOutcome = "Succeeded";
						break;
					  case 3:
						mappedLastRunOutcome = "Canceled";
						break;
					  case 5:
						mappedLastRunOutcome = "Unknown";
						break;
					  default:
						mappedLastRunOutcome = "Undetermined";	
					}
					
					
					String mappedCurrentExecutionStatus = null;
					switch (rs.getInt("current_execution_status")) {
					  case 1:
						mappedCurrentExecutionStatus = "Executing";
						break;
					  case 2:
						mappedCurrentExecutionStatus = "Waiting for thread";
						break;
					  case 3:
						mappedCurrentExecutionStatus = "Between retries";
						break;
					  case 4:
						mappedCurrentExecutionStatus = "Idle";
						break;
					  case 5:
						mappedCurrentExecutionStatus = "Suspended";
						break;
					  case 7:
						mappedCurrentExecutionStatus = "Performing completion actions";
						break;
					  default:
						mappedCurrentExecutionStatus = "Undetermined";	
					}
					
				
					System.out.println(rs.getString("start_step_id") + " " + rs.getString("date_modified") + " " + rs.getString("last_run_date") + " " + rs.getString("last_run_time") + " " + mappedLastRunOutcome + " " + mappedCurrentExecutionStatus + " " + rs.getString("current_execution_step"));
					//CRjobInfo += rs.getString("start_step_id") + " " + rs.getString("date_modified") + " " + rs.getString("last_run_date") + "\n";
					
					//System.out.println(rs.getString("age_range_id") + " " + rs.getString("age_range"));
					//CRjobInfo = rs.getString("age_range_id") + " " + rs.getString("age_range");
					
					//System.out.println(rs.getString("customerid") + " " + rs.getString("name"));
					//CRjobInfo = rs.getString("customerid") + " " + rs.getString("name");
					
					jarr.add(Json.createObjectBuilder()
						  .add("name", "exists")
						  .add("start_step_id", rs.getString("start_step_id"))
						  .add("date_modified", rs.getString("date_modified"))
						  .add("last_run_date", rs.getString("last_run_date"))
						  .add("last_run_time", rs.getString("last_run_time"))
						  .add("last_run_outcome", mappedLastRunOutcome)
						  .add("current_execution_status", mappedCurrentExecutionStatus)
						  .add("current_execution_step", rs.getString("current_execution_step"))
						  .add("activeDB", activeDB)
						  .add("activeServer", activeServer)
					  .build());
			   }
			   
			   arr = jarr.build();
			   joInfo = Json.createObjectBuilder().add("info", arr).build();
			   //System.out.println(joInfo);
				
				//Combine two json objects
			   JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();

				for (String key : joSteps.keySet()) {
					jsonObjectBuilder.add(key, joSteps.get(key));
				}
				for (String key : joInfo.keySet()) {
					jsonObjectBuilder.add(key, joInfo.get(key));
				}
				 
				JsonObject combinedStepsAndInfo = jsonObjectBuilder.build();
				System.out.println(combinedStepsAndInfo);
				
				
			
				returnString = combinedStepsAndInfo.toString();
				
				
				
			}
	   
	   
		   
       } 
	   catch (Exception e) 
	   {
		    System.out.println(getCurentDateTime() + ": Something failed at getCRjobStepsSQLquery function"); 
			System.out.println(e.toString());			
            //e.printStackTrace();  
			returnString = "Something failed at getCRjobStepsSQLquery function: " + e.getMessage() + " failedAtgetCRjobStepsSQLquery";
       } 
	   finally 
	   { 
           CustomDBConnection.closeConnection(conn);
		   	if(returnString == null)
			{
				returnString = "failed" + " failedAtgetCRjobStepsSQLquery";
			}
       }
	  
	   return returnString;
	   
    }
	
	
	@JavaScriptMethod
	public String getActiveDBprovEnvAndServerSQLquery(String customer, String env) 
	{
	
		String returnString = null;
		String activeDB = null;
		String provEnv = null;
		String activeServer = null;

		System.out.println(getCurentDateTime() + ": At getActiveDBprovEnvAndServerSQLquery function");


		//identify the active database
		String SQL = "use " + getOpsDB() + ";\n" +
			"select c.acronym as 'client_acronym', d.name as 'db_name', p.name as 'prov_name' from dbo.[database] d inner join dbo.client c on d.client_id = c.client_id\n" +
			"										  inner join dbo.provisioning_environment p on d.provisioning_environment_id = p.provisioning_environment_id\n" +
			"										  inner join dbo.environment e on e.environment_id = p.environment_id\n" +
			"										  inner join dbo.type t on t.type_id = d.type_id\n" +
			"										  inner join dbo.db_instance_database dbi on dbi.database_id = d.database_id\n" +
			"										  inner join dbo.status s on s.status_id = dbi.status_id\n" +
			"where c.acronym = '" + customer + "' and e.name = '" + env + "' and t.name = 'WAREHOUSE' and s.name = 'Active';";

		String returnValue = getRequestedInfo(customer, env, SQL, "db_name");
		if(returnValue.contains("failed"))
		{
			System.out.println(returnValue);
			returnString = returnValue;
			return returnString;
		}
		else
		{
			activeDB = returnValue;
			//System.out.println(activeDB);
			
		}


		returnValue = getRequestedInfo(customer, env, SQL, "prov_name");
		if(returnValue.contains("failed"))
		{
			System.out.println(returnValue);
			returnString = returnValue;
			return returnString;
		}
		else
		{
			provEnv = returnValue;
			//System.out.println(provEnv);
			
		}


		String job = customer + " Nightly Job " + provEnv;
		System.out.println(getCurentDateTime() + ": Here is the nightly job generated:");
		System.out.println(job);

		//identify the active server
		SQL = "use " + getOpsDB() + ";\n" +
		"select name from dbo.db_instance where db_instance_id in\n" +
		"(\n" +
		"select dbinst.db_instance_id from dbo.[database] db inner join dbo.db_instance_database dbinst on db.database_id = dbinst.database_id\n" +
		"where db.name = '" + activeDB + "' and dbinst.status_id = (select status_id from dbo.status where name = 'Active')\n" +
		");";

		returnValue = getRequestedInfo(customer, env, SQL, "name");
		if(returnValue.contains("failed"))
		{
			System.out.println(returnValue);
			returnString = returnValue;
			return returnString;
		}
		else
		{
			activeServer = returnValue;
			//System.out.println(activeServer);
			
		}
		
		returnString = activeDB + "," + provEnv + "," + activeServer;
		
		return returnString;
	
	}
	
	
	@JavaScriptMethod
	public String retrieveWebBinderyFrontEndVersionsSQLquery(String customer, String env) 
	{
	
		String returnString = null;
		String webVersion = null;
		String binderyFrontendVersion = null;

		System.out.println(getCurentDateTime() + ": At retrieveWebBinderyFrontEndVersionsSQLquery function");


		//identify the active database
		String SQL = "use " + getOpsDB() + ";\n" +
		"select [web_version], [binderyfrontend_version]\n" +
		"from (\n" +
		"select d.version as 'web_version', d.client_revision as 'binderyfrontend_version',\n" +
		"		RANK() OVER (PARTITION BY c.acronym, e.name, p.name, di.name, t.name ORDER BY d.start_timestamp DESC) AS Rank\n" +
		"                                         from OpsDB.dbo.deployment d inner join OpsDB.dbo.status s on d.status_id = s.status_id \n" +
		"                                                                     inner join OpsDB.dbo.client c on d.client_id = c.client_id\n" +
		"                                                                     inner join OpsDB.dbo.provisioning_environment p on d.provisioning_environment_id = p.provisioning_environment_id\n" +
		"																	 inner join OpsDB.dbo.environment e on e.environment_id = p.environment_id\n" +
		"                                                                     inner join OpsDB.dbo.db_instance di on d.db_instance_id = di.db_instance_id\n" +
		"                                                                     inner join OpsDB.dbo.product pr on d.product_id = pr.product_id\n" +
		"                                                                     inner join OpsDB.dbo.type t on d.type_id = t.type_id\n" +
		"																	 inner join OpsDB.dbo.db_instance_database dbi on dbi.database_id = d.database_id\n" +
		"where pr.name = 'Analytics' and t.name in ('WEB') and dbi.status_id <> (select status_id from OpsDB.dbo.status where name = 'Decomissioned') and di.name not like 'qdwsql%' and c.acronym = '" + customer + "' and s.name = 'Success' and e.name = '" + env + "') a\n" +
		"where a.Rank = 1;";

		
		String returnValue = getRequestedInfo(customer, env, SQL, "web_version");
		if(returnValue.contains("failed"))
		{
			System.out.println(returnValue);
			returnString = returnValue;
			return returnString;
		}
		else
		{
			webVersion = returnValue;
			//System.out.println(webVersion);
			
		}


		returnValue = getRequestedInfo(customer, env, SQL, "binderyfrontend_version");
		if(returnValue.contains("failed"))
		{
			System.out.println(returnValue);
			returnString = returnValue;
			return returnString;
		}
		else
		{
			binderyFrontendVersion = returnValue;
			//System.out.println(binderyFrontendVersion);
			
		}

		
		returnString = webVersion + "," + binderyFrontendVersion;
		
		return returnString;
	
	}

	
	@JavaScriptMethod
	public String getWebBinderyFrontEndVersionsSQLquery(String customer, String env) 
	{
	
	   String returnString = null;
	   String error = new String();
	   String activeDB = null;
	   String provEnv = null;
	   String activeServer = null;
	   String webVersion = null;
	   String binderyFrontendVersion = null;
		
	   System.out.println(getCurentDateTime() + ": At getWebBinderyFrontEndVersionsSQLquery function");
	   
	   String returnValue = getActiveDBprovEnvAndServerSQLquery(customer, env);
	   if(returnValue.contains("failed"))
	   {
			System.out.println(returnValue);
			returnString = returnValue;
			return returnString;
	   }
	   else
	   {
	   
	        String[] arrOfStr = returnValue.split(",");
            //for (String a: arrOfStr)
              //  System.out.println(a);
			
			activeDB = arrOfStr[0];
			System.out.println(activeDB);
			
			provEnv = arrOfStr[1];
			System.out.println(provEnv);
			
			activeServer = arrOfStr[2];
			System.out.println(activeServer);
			
		}
			
		
	   String returnValue2 = retrieveWebBinderyFrontEndVersionsSQLquery(customer, env);
	   if(returnValue2.contains("failed"))
	   {
			System.out.println(returnValue2);
			returnString = returnValue2;
			return returnString;
	   }
	   else
	   {
	   
	        String[] arrOfStr2 = returnValue2.split(",");
            //for (String a: arrOfStr2)
              //  System.out.println(a);
			
			webVersion = arrOfStr2[0];
			System.out.println(webVersion);
			
			binderyFrontendVersion = arrOfStr2[1];
			System.out.println(binderyFrontendVersion);
			
		}
	  
	  
	  	//String activeServer = "TESTSQLTST04";
		
	   
	   //Check if server is reachable
	   if (!testServerConnection(activeServer))
	   {
			error = "failed " + activeServer + " is not reachable";
			System.out.println(getCurentDateTime() + ": " + error);
			returnString = error;
			return returnString;
	   }
	  
	   	   	   
       try 
	   {
	   
			//Iterate through the data in the result set and display it.
		   JsonArrayBuilder jarr = Json.createArrayBuilder();
		   JsonArray arr = null;
		   JsonObject joInfo = null;
		   
			if (webVersion == null || webVersion.isEmpty() || binderyFrontendVersion == null || binderyFrontendVersion.isEmpty())
			{
			
				System.out.println(getCurentDateTime() + ": Either web or bindery frontend version is empty");
				returnString = "failed";
			
			}
			else
			{
				System.out.println(getCurentDateTime() + ": Building JSON object...");
				  jarr.add(Json.createObjectBuilder()
						  .add("webVersion", webVersion)
						  .add("binderyFrontendVersion", binderyFrontendVersion)
						  .add("activeServer", activeServer)
					  .build());
					  
				arr = jarr.build();
			    joInfo = Json.createObjectBuilder().add("info", arr).build();
			    System.out.println(joInfo);
			   
			    returnString = joInfo.toString();
			   
			}

		   
       } 
	   catch (Exception e) 
	   {
		    System.out.println(getCurentDateTime() + ": Something failed at getWebBinderyFrontEndVersionsSQLquery function"); 
			System.out.println(e.toString());			
            //e.printStackTrace();  
			returnString = "Something failed at getWebBinderyFrontEndVersionsSQLquery function: " + e.getMessage();
       } 
	   finally 
	   { 
		   	if(returnString == null)
			{
				returnString = "failed";
			}
       }
	  
	   return returnString;
	   
    }

	
	@JavaScriptMethod
	public String getNightlyjobStepsSQLquery(String customer, String env) 
	{
	
	   String returnString = null;
	   String error = new String();
	   String activeDB = null;
	   String provEnv = null;
	   String activeServer = null;
		
	   System.out.println(getCurentDateTime() + ": At getNightlyjobStepsSQLquery function");
	   
	   String returnValue = getActiveDBprovEnvAndServerSQLquery(customer, env);
	   if(returnValue.contains("failed"))
	   {
			System.out.println(returnValue);
			returnString = returnValue;
			return returnString;
	   }
	   else
	   {
	   
	        String[] arrOfStr = returnValue.split(",");
            //for (String a: arrOfStr)
              //  System.out.println(a);
			
			activeDB = arrOfStr[0];
			System.out.println(activeDB);
			
			provEnv = arrOfStr[1];
			System.out.println(provEnv);
			
			activeServer = arrOfStr[2];
			System.out.println(activeServer);
			
		}
			
	  
	   String job = customer + " Nightly Job " + provEnv;
	   System.out.println(getCurentDateTime() + ": Here is the nightly job generated:");
	   System.out.println(job);
	  
	   
	  	//String activeServer = "TESTSQLTST04";
		
	   
	   //Check if server is reachable
	   if (!testServerConnection(activeServer))
	   {
			error = "failed " + activeServer + " is not reachable";
			System.out.println(getCurentDateTime() + ": " + error);
			returnString = error;
			return returnString;
	   }
	  
       Connection conn = null;
       Statement stat = null;
	   
	   String NightlyJobSteps = new String();
	   String NightlyJobInfo = new String();
	   String retrievedJob = null;
	   
	   //conn = CustomDBConnection.getConnection("mycomputer", "1433", "msdb", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "EXEC dbo.sp_help_job @job_name = N'" + job + "',  @job_aspect = N'steps';";
	   //System.out.println(getCurentDateTime() + ": Here is the built change request:");
	   //System.out.println(SQL);
	   
	   //conn = CustomDBConnection.getConnection("TESTSQLTST04", "1433", "test_warehouse_dev04", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "select age_range_id, age_range from dbo.age_range where age_range_id = 1;";
	   
	   conn = CustomDBConnection.getConnection(activeServer, "1433", "placeholderForDB", getdbUser(), getdbPassword(), getSQLauth());
	   //SQL = "EXEC dbo.sp_help_job @job_name = N'" + job + "',  @job_aspect = N'steps';";
	   String SQL = "use msdb; EXEC dbo.sp_help_job @job_name = N'" + job + "',  @job_aspect = N'steps';";
	   
	   //conn = CustomDBConnection.getConnection("mydbserver1", "1433", "tutorialdb", getdbUser(), getdbPassword(), getSQLauth());
	   //String SQL = "select customerid, name from customers where name = 'orlando';";
	   
	   
	   
	   
       try 
	   {
           if (conn == null){throw new Exception("Failed to create connection to database");};
           stat = conn.createStatement();
       } 
	   catch (Exception e)
	   {
		   error = "E13" + " failed " + e.getMessage();
           System.out.println(error);
		   
		   returnString = error;
		   return returnString;

       }
	   
	   
       try 
	   {
	   
			//Iterate through the data in the result set and display it.
		   JsonArrayBuilder jarr = Json.createArrayBuilder();
		   JsonArray arr = null;
		   JsonObject joSteps = null;
		   JsonObject joInfo = null;
		   JsonObject joInfoStart = null;
		   Date next_run_date_time_for_conv = null;
		   String next_run_date_time = null;
		   String next_run_time = null;
		   
		   //Check if the NJ Start exists
			System.out.println(getCurentDateTime() + ": Check if " + job + " exists...");
			ResultSet rs = stat.executeQuery("use msdb; SELECT name FROM dbo.sysjobs WHERE name = '" + job + "'");
			retrievedJob = null;
			while (rs.next())
			{
				System.out.println(rs.getString("name"));
				retrievedJob = rs.getString("name");
			}

			if (retrievedJob == null || retrievedJob.isEmpty())
			{
				System.out.println(getCurentDateTime() + ": " + job + " doesn't exist");
				  jarr.add(Json.createObjectBuilder()
						  .add("name", "doesnotexist")
						  .add("activeDB", activeDB)
						  .add("activeServer", activeServer)
					  .build());
					  
				arr = jarr.build();
			    joInfo = Json.createObjectBuilder().add("info", arr).build();
			    System.out.println(joInfo);
			   
			    returnString = joInfo.toString();
			}
			else
			{
				System.out.println(getCurentDateTime() + ": " + job + " exists");
				
			    System.out.println(getCurentDateTime() + ": About to execute SQL query for retrieving nightly job steps...");
			    rs = stat.executeQuery(SQL);
			   
			   
			   
			    while (rs.next()) {
					
					
					//System.out.println(rs.getString("step_id") + " " + rs.getString("step_name"));
					//NightlyJobSteps += rs.getString("step_id") + " " + rs.getString("step_name") + " " + cleanedUpFailAction + "\n";
					
					//System.out.println(rs.getString("age_range_id") + " " + rs.getString("age_range"));
					//NightlyJobSteps = rs.getString("age_range_id") + " " + rs.getString("age_range");
					
					//System.out.println(rs.getString("customerid") + " " + rs.getString("name"));
					//NightlyJobSteps = rs.getString("customerid") + " " + rs.getString("name");
					
					jarr.add(Json.createObjectBuilder()
						  .add("step_id", rs.getString("step_id"))
						  .add("step_name", rs.getString("step_name"))
					  .build());
			   }
			   
			   arr = jarr.build();
			   joSteps = Json.createObjectBuilder().add("steps", arr).build();
			   //System.out.println(joSteps);
			   
			   
			   //Let's append job info itself
				//SQL = "EXEC dbo.sp_help_job @job_name = N'" + job + "',  @job_aspect = N'job';";
				SQL = "use msdb; EXEC dbo.sp_help_job @job_name = N'" + job + "',  @job_aspect = N'job';";
				System.out.println(getCurentDateTime() + ": About to execute SQL query for retriving nightly job start time info...");
				rs = stat.executeQuery(SQL);
				
				while (rs.next()) {
				
					String mappedCurrentExecutionStatus = null;
					switch (rs.getInt("current_execution_status")) {
					  case 1:
						mappedCurrentExecutionStatus = "Executing";
						break;
					  case 2:
						mappedCurrentExecutionStatus = "Waiting for thread";
						break;
					  case 3:
						mappedCurrentExecutionStatus = "Between retries";
						break;
					  case 4:
						mappedCurrentExecutionStatus = "Idle";
						break;
					  case 5:
						mappedCurrentExecutionStatus = "Suspended";
						break;
					  case 7:
						mappedCurrentExecutionStatus = "Performing completion actions";
						break;
					  default:
						mappedCurrentExecutionStatus = "Undetermined";	
					}
					
					
					String enabledStatus = null;
					switch (rs.getInt("enabled")) {
					  case 1:
						enabledStatus = "enabled";
						break;
					  case 0:
						enabledStatus = "disabled";
						break;
					  default:
						enabledStatus = "undetermined";	
					}
				
					
					if (rs.getInt("next_run_schedule_id") != 0 && rs.getInt("next_run_date") != 0)
					{
						next_run_time = rs.getString("next_run_time");
							
						while (next_run_time.length() != 6)
						{

						  next_run_time = "0" + next_run_time;

						}
					
						next_run_date_time_for_conv = new SimpleDateFormat("yyyyMMdd HHmm").parse(rs.getString("next_run_date") + " " + next_run_time.substring(0,4));
						next_run_date_time = next_run_date_time_for_conv.toString();
						
						//System.out.println(next_run_date_time);
					}
					else
					{
						next_run_date_time = "0";
					}
					
					
					
					
					System.out.println(next_run_date_time);
					
					System.out.println(rs.getString("next_run_schedule_id") + 
								 " " + rs.getString("next_run_date") + 
								 " " + rs.getString("next_run_time") + 
								 " " + enabledStatus + 
								 " " + mappedCurrentExecutionStatus + 
								 " " + job +
								 " " + activeDB +
								 " " + activeServer +
								 " " + next_run_date_time);
								 
					//CRjobInfo += rs.getString("start_step_id") + " " + rs.getString("date_modified") + " " + rs.getString("last_run_date") + "\n";
					
					//System.out.println(rs.getString("age_range_id") + " " + rs.getString("age_range"));
					//CRjobInfo = rs.getString("age_range_id") + " " + rs.getString("age_range");
					
					//System.out.println(rs.getString("customerid") + " " + rs.getString("name"));
					//CRjobInfo = rs.getString("customerid") + " " + rs.getString("name");
					
					jarr.add(Json.createObjectBuilder()
						  .add("name", "exists")
						  .add("next_run_schedule_id", rs.getString("next_run_schedule_id"))
						  .add("next_run_date", rs.getString("next_run_date"))
						  .add("next_run_time", rs.getString("next_run_time"))
						  .add("enabledStatus", enabledStatus)
						  .add("current_execution_status", mappedCurrentExecutionStatus)
						  .add("job", job)
						  .add("activeDB", activeDB)
						  .add("activeServer", activeServer)
						  .add("next_run_date_time", next_run_date_time)
					  .build());
			   }
			   
			   arr = jarr.build();
			   joInfo = Json.createObjectBuilder().add("info", arr).build();
			   //System.out.println(joInfo);
			   
			   
			   //Let's append job start info
				job = job + " Start";
				
			   //Check if the NJ Start exists
			   System.out.println(getCurentDateTime() + ": Check if " + job + " exists...");
			   rs = stat.executeQuery("use msdb; SELECT name FROM dbo.sysjobs WHERE name = '" + job + "'");
			   retrievedJob = null;
			   while (rs.next())
			   {
					System.out.println(rs.getString("name"));
					retrievedJob = rs.getString("name");
			   }
			   
			   if (retrievedJob == null || retrievedJob.isEmpty())
			   {
					System.out.println(getCurentDateTime() + ": " + job + " doesn't exist");
					  jarr.add(Json.createObjectBuilder()
							  .add("name", "doesnotexist")
						  .build());
			   }
			   else
			   {
					System.out.println(getCurentDateTime() + ": " + job + " exists");
					
					//SQL = "EXEC dbo.sp_help_job @job_name = N'" + job + "',  @job_aspect = N'job';";
					SQL = "use msdb; EXEC dbo.sp_help_job @job_name = N'" + job + "',  @job_aspect = N'job';";
					System.out.println(getCurentDateTime() + ": About to execute SQL query for retriving nightly job Start start time info...");
					rs = stat.executeQuery(SQL);
					
					while (rs.next()) {
					
						String mappedCurrentExecutionStatus = null;
						switch (rs.getInt("current_execution_status")) {
						  case 1:
							mappedCurrentExecutionStatus = "Executing";
							break;
						  case 2:
							mappedCurrentExecutionStatus = "Waiting for thread";
							break;
						  case 3:
							mappedCurrentExecutionStatus = "Between retries";
							break;
						  case 4:
							mappedCurrentExecutionStatus = "Idle";
							break;
						  case 5:
							mappedCurrentExecutionStatus = "Suspended";
							break;
						  case 7:
							mappedCurrentExecutionStatus = "Performing completion actions";
							break;
						  default:
							mappedCurrentExecutionStatus = "Undetermined";	
						}
						
						
						String enabledStatus = null;
						switch (rs.getInt("enabled")) {
						  case 1:
							enabledStatus = "enabled";
							break;
						  case 0:
							enabledStatus = "disabled";
							break;
						  default:
							enabledStatus = "undetermined";	
						}
					

						if (rs.getInt("next_run_schedule_id") != 0 && rs.getInt("next_run_date") != 0)
						{
							next_run_time = rs.getString("next_run_time");
							
							while (next_run_time.length() != 6)
							{

							  next_run_time = "0" + next_run_time;

							}
						
							next_run_date_time_for_conv = new SimpleDateFormat("yyyyMMdd HHmm").parse(rs.getString("next_run_date") + " " + next_run_time.substring(0,4));
							next_run_date_time = next_run_date_time_for_conv.toString();
							
							System.out.println(next_run_date_time);
						}
						else
						{
							next_run_date_time = "0";
						}
					
						//System.out.println(next_run_date_time);
					
						/*
						System.out.println(rs.getString("next_run_schedule_id") + 
									 " " + rs.getString("next_run_date") + 
									 " " + rs.getString("next_run_time") + 
									 " " + enabledStatus + 
									 " " + mappedCurrentExecutionStatus + 
									 " " + job +
									 " " + next_run_date_time);	
						*/			 
									 
						//CRjobInfo += rs.getString("start_step_id") + " " + rs.getString("date_modified") + " " + rs.getString("last_run_date") + "\n";
						
						//System.out.println(rs.getString("age_range_id") + " " + rs.getString("age_range"));
						//CRjobInfo = rs.getString("age_range_id") + " " + rs.getString("age_range");
						
						//System.out.println(rs.getString("customerid") + " " + rs.getString("name"));
						//CRjobInfo = rs.getString("customerid") + " " + rs.getString("name");
						
						jarr.add(Json.createObjectBuilder()
							  .add("name", "exists")
							  .add("next_run_schedule_id", rs.getString("next_run_schedule_id"))
							  .add("next_run_date", rs.getString("next_run_date"))
							  .add("next_run_time", rs.getString("next_run_time"))
							  .add("enabledStatus", enabledStatus)
							  .add("current_execution_status", mappedCurrentExecutionStatus)
							  .add("job", job)
							  .add("next_run_date_time", next_run_date_time)
						  .build());
				   }
			  
			   }		  
			   arr = jarr.build();
			   joInfoStart = Json.createObjectBuilder().add("infoStart", arr).build();
			   //System.out.println(joInfoStart);
				
				//Combine three json objects
			   JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();

				for (String key : joSteps.keySet()) {
					jsonObjectBuilder.add(key, joSteps.get(key));
				}
				for (String key : joInfo.keySet()) {
					jsonObjectBuilder.add(key, joInfo.get(key));
				}
				for (String key : joInfoStart.keySet()) {
					jsonObjectBuilder.add(key, joInfoStart.get(key));
				}
				 
				JsonObject combinedStepsAndInfoAndInfoStart = jsonObjectBuilder.build();
				//System.out.println(combinedStepsAndInfoAndInfoStart);
				
						
				returnString = combinedStepsAndInfoAndInfoStart.toString();
			   
			}

		   
       } 
	   catch (Exception e) 
	   {
		    System.out.println(getCurentDateTime() + ": Something failed at getNightlyjobStepsSQLquery function"); 
			System.out.println(e.toString());			
            //e.printStackTrace();  
			returnString = "Something failed at getNightlyjobStepsSQLquery function: " + e.getMessage();
       } 
	   finally 
	   { 
           CustomDBConnection.closeConnection(conn);
		   	if(returnString == null)
			{
				returnString = "failed";
			}
       }
	  
	   return returnString;
	   
    }

	
	
	@JavaScriptMethod
	public String checkIfUserIsInJenkinsPRDgroup() 
	{
	
		 String returnString = null;
		 
	   
	   //Checking connectivity to AD/LDAP server
	    try 
		{
			
		   System.out.println(getCurentDateTime() + ": At checkIfUserIsInJenkinsPRDgroup function");
		   System.out.println(getCurentDateTime() + ": Getting the user executing Jenkins...");
		   String user = System.getProperty("user.name");
		   System.out.println(user);
		   
		   System.out.println(getCurentDateTime() + ": Getting the user who logged in to Jenkins...");
		   String builduser = User.current().getId();
		   System.out.println(builduser);
		   
		   System.out.println(getCurentDateTime() + ": Getting java version used by Jenkins...");
		   String javaVersion = System.getProperty("java.version");
		   System.out.println(javaVersion);
			
            // Create a LDAP Context
            Hashtable env = new Hashtable();  
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");  
            env.put(Context.SECURITY_AUTHENTICATION, "simple");  
			
			env.put(Context.SECURITY_PRINCIPAL, getLDAPuser());  
            env.put(Context.SECURITY_CREDENTIALS, getLDAPpassword());  
			env.put(Context.PROVIDER_URL, getLDAPserver());
						
			
            LdapContext ctx = new InitialLdapContext(env, null);  
            System.out.println(getCurentDateTime() + ": Successfully connected to LDAP server");
 
            String str = getLDAPserver();
            String[] arrOfStr = str.split("\\.");
           
            String preptopleveldomain = arrOfStr[arrOfStr.length - 1];
            String topleveldomain = preptopleveldomain.split(":")[0];
         
            //System.out.println(topleveldomain);
            String subdomain = arrOfStr[arrOfStr.length - 2];
            //System.out.println(subdomain);
            
			String searchBase = "DC=" + subdomain + ",DC=" + topleveldomain;
			System.out.println(searchBase);
			
			String FILTER = "(&(samAccountName=" + builduser + "))";
			
			String PRD_Group = "Jenkins_PRD_Dep_Group";
			
			SearchControls ctls = new SearchControls();
			ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
			NamingEnumeration<SearchResult> answer = ctx.search(searchBase, FILTER, ctls);
			SearchResult result = answer.next();
			//Attribute email = result.getAttributes().get("mail");
			Attribute cn = result.getAttributes().get("cn");
			System.out.println(cn);
			
			//Get all the groups this user is member of and check whether he/she is in Jenkins PRD group
			
			Attribute memberOf = result.getAttributes().get("memberOf");
			//System.out.println("right after getting memberof attributes");
			
			
			boolean isMemberOfGroup = false;
			if(memberOf != null)
			{
				//System.out.println(memberOf.size());
				
				for (int i=0; i < memberOf.size(); i++) 
				{                
				   // print out each group that user belongs to
					//System.out.println("memberOf: " + memberOf.get(i));
					if (String.valueOf(memberOf.get(i)).contains("CN=" + PRD_Group))
					{
						isMemberOfGroup = true;
						break;
					}
				}
			}
			
			if(!isMemberOfGroup)
			{
				System.out.println(builduser + " user is not a member of " + PRD_Group);
				returnString = "unauthorized";
			}
			else
			{
				System.out.println(builduser + " user is a member of " + PRD_Group);
				returnString = "authorized";
			}
			
			
			
			ctx.close();
			
        } 
		catch (Exception e) 
		{
            System.out.println(getCurentDateTime() + ": Something failed at checkIfUserIsInJenkinsPRDgroup function"); 
			System.out.println(e.toString());			
            //e.printStackTrace();  
			returnString = "Something failed at checkIfUserIsInJenkinsPRDgroup function: " + e.getMessage();	
			
        }
		finally
		{
			
			if(returnString == null)
			{
				returnString = "failed";
			}
			
		}
		
		return returnString;
		
	}
	
	public String getCurentDateTime()
	{

		String timeStamp = new SimpleDateFormat("yyyyMMdd-hh:mm:ss-aaa-z").format(new Date());
		return timeStamp;
    }
	
	public String getOpsDBinstance()
	{
		String dbinstance = "qdwsqlops01";
		return dbinstance;
    }
	
	public String getOpsDBinstancePort()
	{
		String dbinstanceport = "1433";
		return dbinstanceport;
    }
	
	public String getOpsDB()
	{
		//String db = "opsdb_dev";
		String db = "opsdb";
		return db;
    }
	
	@JavaScriptMethod
	public String getRequestedInfo(String customer, String env, String SQL, String property) {
	
	   System.out.println(getCurentDateTime() + ": At getRequestedInfo function");
	   System.out.println(customer);
	   System.out.println(env);
	   System.out.println(property);
	   System.out.println(SQL);
	   
		
	   String returnString = null;
	   String error = new String();
	   	  	   
	   String opsdbServer = getOpsDBinstance();
		//Check if server is reachable
		if (!testServerConnection(opsdbServer))
		{
			error = "failed " + opsdbServer + " is not reachable";
			System.out.println(getCurentDateTime() + ": " + error);
			returnString = error;
			return returnString;
		}
	   
	   
	   Connection conn = null;
       Statement stat = null;

	   conn = CustomDBConnection.getConnection(opsdbServer, getOpsDBinstancePort(), "placeholderForDB", getdbUser(), getdbPassword(), getSQLauth());	   

	   
       try {
           assert conn != null;
           stat = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
       } catch (Exception e) {
           System.out.println("E13" + " failed " + e.getMessage());
		   returnString = "E13" + " failed " + e.getMessage();
		   return returnString;
       }
	   
	   
       try {
	       System.out.println(getCurentDateTime() + ": About to execute SQL query...");
           ResultSet rs = stat.executeQuery(SQL);
		   
		   System.out.println("after executing query");
		   System.out.println(rs);
		   
		    int size = 0;
			if (rs != null) 
			{
			  rs.last();    // moves cursor to the last row
			  size = rs.getRow(); // get row id 
			  
			  System.out.println("Show the size...");
			  System.out.println(size);
			  
			  if (size == 1)
			  {
				System.out.println("Only one row is returned as expected");
			  }
			  else if (size > 1)
			  {
				System.out.println("Only one row is expected to be returned but there was more than one.");
				throw new Exception("Only one row is expected to be returned but there was more than one.");
			  }
			  else if (size == 0)
			  {
				System.out.println("No rows were returned.");
				throw new Exception("No rows were returned.");
			  }
			  else
			  {
				System.out.println("Number of rows is negative for some reason.");
				throw new Exception("Number of rows is negative for some reason.");
			  }
			  
			}
			else
			{
				throw new Exception("No result set was produced for some reason");
			
			}
		   
		   System.out.println("Should not get here if number of rows is not equal one");
		   
		   //Iterate through the data in the result set and display it.
		   rs.beforeFirst();    // moves cursor to the beginning
           while (rs.next()) {
                System.out.println(rs.getString(property));
				returnString = rs.getString(property);
				
           }
		   
       } catch (Exception e) {
	   
			System.out.println("In the catch section");
			
            System.out.println(getCurentDateTime() + ": Something failed at getRequestedInfo function"); 
			System.out.println(e.toString());			
            //e.printStackTrace();  
			returnString = "Something failed at getRequestedInfo function.\n" + e.getMessage();			
       } finally { 
           CustomDBConnection.closeConnection(conn);
		   	if(returnString == null)
			{
				System.out.println("returnString is null");
				returnString = "failed";
			}
       }
	   
	 
	   return returnString;
	   
    }
	
	
	@JavaScriptMethod
	public String getSQLserverVersion(String server, String property) {
	
	   System.out.println(getCurentDateTime() + ": At getSQLserverVersion function");
	   System.out.println(server);
	   System.out.println(property);

		
	   String returnString = null;
	   String error = new String();
	   	  	   
		//Check if server is reachable
		if (!testServerConnection(server))
		{
			error = "failed " + server + " is not reachable";
			System.out.println(getCurentDateTime() + ": " + error);
			returnString = error;
			return returnString;
		}
	   
	   
	   Connection conn = null;
       Statement stat = null;

	   conn = CustomDBConnection.getConnection(server, "1433", "placeholderForDB", getdbUser(), getdbPassword(), getSQLauth());	   

	   
       try {
           assert conn != null;
           stat = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
       } catch (Exception e) {
           System.out.println("E13" + " failed " + e.getMessage());
		   returnString = "E13" + " failed " + e.getMessage();
		   return returnString;
       }
	   
	   
       try {
	       System.out.println(getCurentDateTime() + ": About to execute SQL query...");
           ResultSet rs = stat.executeQuery("select @@version as 'version'");
		   
		    int size = 0;
			if (rs != null) 
			{
			  rs.last();    // moves cursor to the last row
			  size = rs.getRow(); // get row id 
			  if (size > 1)
			  {
				throw new Exception("Only one row is expected to be returned but there was more than one.");
			  }
			}
			else
			{
				throw new Exception("No rows were returned.");
			
			}
		   
		   //Iterate through the data in the result set and display it.
		   rs.beforeFirst();    // moves cursor to the beginning
           while (rs.next()) {
                System.out.println(rs.getString(property));
				returnString = rs.getString(property);
				
           }
		   
       } catch (Exception e) {
            System.out.println(getCurentDateTime() + ": Something failed at getSQLserverVersion function"); 
			System.out.println(e.toString());			
            //e.printStackTrace();  
			returnString = "Something failed at getSQLserverVersion function.\n" + e.getMessage();			
       } finally { 
           CustomDBConnection.closeConnection(conn);
		   	if(returnString == null)
			{
				returnString = "failed";
			}
       }
	   
	 
	   return returnString;
	   
    }
	
	@JavaScriptMethod
	public boolean testServerConnection(String hostname) {
		boolean result = false;
            
		//If running on Windows platform use "-n" option
		String pingCommand;
		if(System.getProperty("os.name").startsWith("Windows")) 
		{
			pingCommand = "ping -n 2 " + hostname;
		} 
		//Else other platform, use "-c" option
		else
		{
			pingCommand = "ping -c 2 " + hostname;
		}

		try
		{   
			//Call Runtime Exec and give it command to run
			Process myProcess = Runtime.getRuntime().exec(pingCommand);

			//Wait for a response or return
			myProcess.waitFor();


			if(myProcess.exitValue() == 0) 
			{ 
				result = true; 
			}
			else
			{ 
				result = false; 
			}

			//Cleanup this Runtime process
			myProcess.destroy();
		}
		catch(Exception ex)
		{
			System.out.println("Exception:"+ex.toString());
		}

		//Return the result
		return result;
	   
	   
    }
	
	@JavaScriptMethod
	public String getServerDateTime() {
	   String timeStamp = new SimpleDateFormat("MM/dd/yyyy HH:mm").format(new Date());
	   return timeStamp;
	 
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
	
	
	@WebMethod(name="getServerDateTimeLocal")
	public HttpResponse getServerDateTimeLocal() {
		String timeStamp = new SimpleDateFormat("MM/dd/yyyy HH:mm").format(new Date());
		return HttpResponses.text(timeStamp);
	}
	
	
	public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
	  ContextMenu menu = new ContextMenu();
	  menu.add("getServerDateTimeLocal","Jenkins Server Time");
	  return menu;
	}
	
	  
}

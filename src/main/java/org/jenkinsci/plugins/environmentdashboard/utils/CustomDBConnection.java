package org.jenkinsci.plugins.environmentdashboard.utils;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import java.io.File;

import java.text.SimpleDateFormat;

/**
 * Singleton class to represent a single DB connection. 
 * @author robertnorthard
 * @date 18/10/2014
 */
public class CustomDBConnection {

	
	/**
	 * Return a database connection object.
	 * @return a database connection object
	 */
	public static Connection getConnection(String server, String port, String db, String userName, String userPassword, Boolean SQLauthentication){
		
		String timeStamp = new SimpleDateFormat("yyyyMMdd-hh:mm:ss-aaa-z").format(new java.util.Date());
		System.out.println(timeStamp + ": At getConnection function");
		String dbConnectionString = null;
		
		// Generate connection String for DB driver
		if (SQLauthentication)
		{
			//Use this string for manual testing on your local computer
			//dbConnectionString = "jdbc:sqlserver://" + server + ":" + port + ";databaseName=" + db + ";user=" + userName + ";password=" + userPassword;
			dbConnectionString = "jdbc:sqlserver://" + server + ":" + port + ";user=" + userName + ";password=" + userPassword + ";queryTimeout=1800";
		}
		else
		{
			//Try using Windows Integrated Authentication once on the domain
			//dbConnectionString = "jdbc:sqlserver://" + server + ":" + port + ";databaseName=" + db + ";integratedSecurity=true";
			dbConnectionString = "jdbc:sqlserver://" + server + ":" + port + ";integratedSecurity=true;queryTimeout=1800";
		}
		
		//System.out.println(dbConnectionString);
		
		Connection con = null;

		//Load driver and connect to DB
		try { 
			Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
			con = DriverManager.getConnection(dbConnectionString);
			System.out.println(timeStamp + ": Successfully connected to " + server + " SQL server");
		} catch (ClassNotFoundException e) {
			System.err.println("WARN: Could not acquire Class com.microsoft.sqlserver.jdbc.SQLServerDriver.");
		} catch (SQLException e){
			System.err.println("WARN: Could not acquire connection to SQL Server at " + server + ". " + e.getMessage());
		}
		
		return con;
	}
	
	/**
	 * Close Database Connection
	 * @return true if database connection closed successful,
	 * 			 else false if connection not closed or SQLException.
	 */
	public static boolean closeConnection(Connection con){
		
		//Prevent unchecked NullPointerException
		if(con != null){
			try {
				con.close();
				return true;
			} catch (SQLException error) { System.err.println("E5"); return false; }
		}
		//default  - failed to close
		return false;
	}
}
	

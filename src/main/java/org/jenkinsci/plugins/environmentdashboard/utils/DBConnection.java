package org.jenkinsci.plugins.environmentdashboard.utils;

import hudson.model.Hudson;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.h2.jdbcx.JdbcConnectionPool;

import java.io.File;

/**
 * Singleton class to represent a single DB connection. 
 * @author robertnorthard
 * @date 18/10/2014
 */
public class DBConnection {

	private static Connection con = null;
	private static JdbcConnectionPool pool = null;
	
	/**
	 * Return a database connection object.
	 * @return a database connection object
	 */
	public static Connection getConnection(){
		
		// Generate connection String for DB driver.

		if (con == null) {
			//Load driver and connect to DB
			try { 
				String dbConnectionString = "jdbc:h2:" + Hudson.getInstance().root.toString() +
										 File.separator + "jenkins_dashboard" + ";MVCC=true;TRACE_LEVEL_FILE=0;DB_CLOSE_DELAY=-1";
				Class.forName("org.h2.Driver");
				//DBConnection.pool = JdbcConnectionPool.create(dbConnectionString, "", "");
				//DBConnection.pool.setMaxConnections(200);

				try {
					DBConnection.con = DriverManager.getConnection(dbConnectionString);
				} catch (SQLException e){
					System.err.println("WARN: Could not acquire connection to H2 DB.\n" + e.getMessage());
				}

			} catch (ClassNotFoundException e) {
				System.err.println("WARN: Could not acquire Class org.h2.Driver.\n" + e.getMessage());
			}
		}

//		try {
//			DBConnection.con = DBConnection.pool.getConnection();
//		} catch (SQLException e){
//			System.err.println("WARN: Could not acquire connection to H2 DB.\n" + e.getMessage());
//		}

		return DBConnection.con;
	}
	
	/**
	 * Close Database Connection
	 * @return true if database connection closed successful,
	 * 			 else false if connection not closed or SQLException.
	 */
	public static boolean closeConnection(){
		
		//Prevent unchecked NullPointerException
		if(DBConnection.con != null){
			try {
				DBConnection.con.close();
				DBConnection.con = null;
				return true;
			} catch (SQLException error) { System.err.println("E5"); return false; }
		}
		//default  - failed to close
		return false;
	}
}
	

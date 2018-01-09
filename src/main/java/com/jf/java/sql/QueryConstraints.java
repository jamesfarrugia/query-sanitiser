package com.jf.java.sql;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An object defining the constrains of the query to run
 * 
 * @author james
 *
 */
public class QueryConstraints
{
	/** Whitelist of tables to allow */
	private Set<String> tableWhitelist;
	/** Whitelist of functions to allow */
	private Set<String> functionWhitelist;
	
	/**
	 * Default constructor for QueryConstraints
	 */
	public QueryConstraints()
	{
		this.tableWhitelist = new HashSet<>();
		this.functionWhitelist = new HashSet<>();
	}

	/**
	 * @return the tableWhitelist
	 */
	public final Set<String> getTableWhitelist()
	{
		return tableWhitelist;
	}

	/**
	 * @param tableWhitelist the tableWhitelist to set
	 */
	public final void setTableWhitelist(Set<String> tableWhitelist)
	{
		this.tableWhitelist = tableWhitelist;
	}
	
	/**
	 * @param tableWhitelist the tableWhitelist to set
	 */
	public final void setTableWhitelist(List<String> tableWhitelist)
	{
		this.tableWhitelist = new HashSet<String>(tableWhitelist);
	}

	/**
	 * @return the functionWhitelist
	 */
	public final Set<String> getFunctionWhitelist()
	{
		return functionWhitelist;
	}

	/**
	 * @param functionWhitelist the functionWhitelist to set
	 */
	public final void setFunctionWhitelist(Set<String> functionWhitelist)
	{
		this.functionWhitelist = functionWhitelist;
	}
	
	/**
	 * @param functionWhitelist the tableWhitelist to set
	 */
	public final void setFunctionWhitelist(List<String> functionWhitelist)
	{
		this.functionWhitelist = new HashSet<String>(functionWhitelist);
	}
	
	/**
	 * Return true if the passed table name is allowed in the query
	 * @param table
	 * @return true if the passed table name is allowed in the query
	 */
	public boolean isTableAllowed(String table)
	{
		return tableWhitelist.contains(table);
	}
	
	/**
	 * Return true if the passed function name is allowed in the query
	 * @param function
	 * @return true if the passed function name is allowed in the query
	 */
	public boolean isFunctionAllowed(String function)
	{
		return functionWhitelist.contains(function);
	}
}

package com.jf.java.sql;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.JsonExpression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SubSelect;

/**
 * Service to sanitise queries using the passed constraints
 *
 */
@Service
public class QuerySanitiser
{
	/** Class logger */
	private Logger log;
	/** Map of error codes */
	private Map<String, String> errorMap;
	
	/**
	 * Default constructor for QuerySanitiser
	 */
	public QuerySanitiser()
	{
		log = LoggerFactory.getLogger(getClass());
		init();
		log.info("query sanitiser service created");
	}
	
	/**
	 * Initialises the sanitiser and errors
	 */
	public void init()
	{
		errorMap = new HashMap<String, String>();
		errorMap.put("B001", "Query is not a SELECT");
		errorMap.put("B002", "Joined expression is not a TABLE or SELECT");
		errorMap.put("B003", "Unkown table or alias");
		errorMap.put("B004", "Illegal expressions");
		errorMap.put("B005", "Table not allowed in query");
		errorMap.put("B006", "Illegal selections");
		errorMap.put("B007", "Function not allowed in query");
		errorMap.put("S001", "Failed to parse query");
	}
	
	/**
	 * Sanitise the passed raw query using the passed constraints.  The system
	 * uses a least privilege and fail-fast approach so any missing value in 
	 * the constraints will immediately fail.
	 * 
	 * @param query
	 * @param constraints
	 */
	public void doSanitise(String query, QueryConstraints constraints)
	{
		Map<String, Table> tblIndex = new HashMap<String, Table>();
		Map<String, SubSelect> subSelIndex = new HashMap<String, SubSelect>();
		Set<String> aliases = new HashSet<>();
		
		Statement stmt = null;
		try
		{
			log.info("Parsing " + query);
			stmt = CCJSqlParserUtil.parse(query);
			if (!(stmt instanceof Select))
				error("B001");
		}
		catch (JSQLParserException e) 
		{
			error("S001");
		}
		
		Select selection = (Select) stmt;
		SelectBody selBody = selection.getSelectBody();
		
		doProcessSelect(selBody, tblIndex, subSelIndex, aliases, constraints);
	}
	
	/**
	 * Processes a SELECT body to verify the tables are whitelisted.  All tables
	 * are added to the index of aliases, as well as the full name.
	 * 
	 * @param selBody
	 * @param tblIndex
	 * @param subSelIndex
	 * @param aliases 
	 * @param constraints 
	 */
	private void doProcessSelect(
			SelectBody selBody, 
			Map<String, Table> tblIndex, 
			Map<String, SubSelect> subSelIndex,
			Set<String> aliases,
			QueryConstraints constraints)
	{
		log.trace("Processing SELECT");
		if (!(selBody instanceof PlainSelect))
			error("B001");
		
		PlainSelect select = (PlainSelect) selBody;
		
		log.trace("Processing SELECTed aliases");
		for (SelectItem si : select.getSelectItems())
		{
			if (si instanceof SelectExpressionItem)
			{
				SelectExpressionItem sei = (SelectExpressionItem) si;
				if (sei.getExpression() instanceof Column)
					aliases.add(((Column)sei.getExpression()).getColumnName());
				if (sei.getAlias() != null)
					aliases.add(sei.getAlias().getName());
			}
		}
		
		if (select.getFromItem() != null)
		{
			log.trace("Processing FROM");
			FromItem from = select.getFromItem();
			doProcessFrom(from, tblIndex, subSelIndex, aliases, constraints);
		}
		
		if (select.getJoins() != null)
		{
			log.trace("Processing JOINs");
			for (Join join : select.getJoins())
			{
				log.trace(join.toString());
				
				FromItem from = join.getRightItem();
				doProcessFrom(from, tblIndex, subSelIndex, aliases, constraints);
				
				if (join.getOnExpression() != null)
					doProcessExpression(
							join.getOnExpression(), 
							tblIndex, 
							subSelIndex,
							aliases,
							constraints);
			}
		}
		
		if (select.getWhere() != null)
		{
			log.trace("Processing WHERE");
			doProcessExpression(select.getWhere(), tblIndex, 
					subSelIndex, aliases, constraints);
		}
		
		if (select.getGroupByColumnReferences() != null)
		{
			log.trace("Processing GROUP BY");
			for (Expression gb : select.getGroupByColumnReferences())
				doProcessExpression(gb, tblIndex, subSelIndex, aliases, constraints);
		}
		
		if (select.getHaving() != null)
		{
			log.trace("Processing HAVING");
			doProcessExpression(select.getHaving(), tblIndex, 
					subSelIndex, aliases, constraints);
		}
		
		log.trace("Processing FIELDS");
		for (SelectItem si : select.getSelectItems())
		{
			if (si instanceof SelectExpressionItem)
			{
				SelectExpressionItem sei = (SelectExpressionItem) si;
				Expression exp = sei.getExpression();
				doProcessExpression(exp, tblIndex, subSelIndex, aliases, constraints);
			}
			else if (si instanceof AllColumns)
			{
				log.trace("All columns");
			}
			else
			{
				error("B006");
			}
		}
		
		if (select.getDistinct() != null)
		{
			log.trace("Processing DISTINCT");
			if (select.getDistinct().getOnSelectItems() != null)
			{
				for (SelectItem distinctItem : select.getDistinct().getOnSelectItems())
				{
					SelectExpressionItem sei = (SelectExpressionItem) distinctItem;
					Expression exp = sei.getExpression();
					doProcessExpression(exp, tblIndex, subSelIndex, aliases, constraints);
				}
			}
		}
		
		if (select.getOrderByElements() != null)
		{
			log.trace("Processing ORDER BY");
			for (OrderByElement oe : select.getOrderByElements())
				doProcessExpression(oe.getExpression(), tblIndex, 
						subSelIndex, aliases, constraints);
		}
	}
	
	/**
	 * Processes an item from which we are selecting, a TABLE or another SELECT
	 * query
	 * 
	 * @param from item to process
	 * @param tblIndex Index of table aliases
	 * @param subSelIndex Index of sub-select aliases
	 * @param aliases 
	 * @param constraints constraints for the query
	 */
	private void doProcessFrom(
			FromItem from, 
			Map<String, Table> tblIndex, 
			Map<String, SubSelect> subSelIndex,
			Set<String> aliases,
			QueryConstraints constraints)
	{
		if (from instanceof Table)
		{
			Table tbl = (Table) from;
			if (!constraints.isTableAllowed(tbl.getFullyQualifiedName()))
				error("B005", tbl.getName());
				
			log.trace("From of type table, " + tbl.getName() + " AS " + tbl.getAlias());
			if (tbl.getAlias() != null)
				tblIndex.put(tbl.getAlias().getName(), tbl);
			tblIndex.put(tbl.getFullyQualifiedName(), tbl);
		}
		else if (from instanceof SubSelect)
		{
			SubSelect sub= (SubSelect) from;
			log.trace("From of type sub-select");
			doProcessSelect(sub.getSelectBody(), tblIndex, subSelIndex, aliases, constraints);
			
			if (from.getAlias() != null)
				subSelIndex.put(from.getAlias().getName(), sub);
		}
		else
			error("B002", from.toString());
	}
	
	/**
	 * Processes an expression in the query.  Functions are checked against the
	 * white list and every column reference is checked against the used tables
	 * and their aliases.  
	 * 
	 * @param exp
	 * @param tblIndex
	 * @param subselIndex
	 * @param aliases 
	 * @param constraints 
	 */
	private void doProcessExpression(
			Expression exp, 
			Map<String, Table> tblIndex, 
			Map<String, SubSelect> subselIndex,
			Set<String> aliases,
			QueryConstraints constraints)
	{
		log.trace("Processing expression " + exp);
		if (exp instanceof Column)
		{
			Column col = (Column) exp;
			String tbl = col.getTable().getFullyQualifiedName();
			log.trace("COLM:" + col.getColumnName() + " OF " + tbl);
			
			if (!tblIndex.containsKey(tbl) && 
					!subselIndex.containsKey(tbl) && 
					!aliases.contains(col.getColumnName()))
				error("B003", tbl);
			
			if (tblIndex.containsKey(tbl))
				log.trace("table (" + tblIndex.get(tbl).getFullyQualifiedName() + ")");
			if (subselIndex.containsKey(tbl))
				log.trace("sub-select (" + subselIndex.get(tbl).getAlias() + ")");
		}
		else if (exp instanceof JsonExpression)
		{
			JsonExpression json = (JsonExpression) exp;
			doProcessExpression(json.getColumn(), tblIndex, subselIndex, aliases, constraints);
			log.trace("JSON:" + json + " - on column " + json.getColumn());
		}
		else if (exp instanceof Function)
		{
			Function func = (Function) exp;
			
			if (!constraints.isFunctionAllowed(func.getName()))
				error("B007", func.getName());
			
			ExpressionList args = func.getParameters();
			if (args != null)
				for (Expression e : args.getExpressions())
					doProcessExpression(e, tblIndex, subselIndex, aliases, constraints);
			
			log.trace("FUNC:" + func.getName() + " (" + args + ")");
		}
		else if (exp instanceof BinaryExpression)
		{
			BinaryExpression bin = (BinaryExpression) exp;
			Expression left = bin.getLeftExpression();
			Expression right = bin.getRightExpression();
			doProcessExpression(left, tblIndex, subselIndex, aliases, constraints);
			doProcessExpression(right, tblIndex, subselIndex, aliases, constraints);
			log.trace("BINR: " + bin.getStringExpression() + "(" + left + "," + right + ")");
		}
		else if (exp instanceof LongValue || exp instanceof DoubleValue)
		{
			log.trace("NMBR:" + exp.toString());
		}
		else if (exp instanceof SignedExpression)
		{
			SignedExpression signed = (SignedExpression) exp;
			doProcessExpression(signed.getExpression(), tblIndex, subselIndex, aliases, constraints);
			log.trace("SIGN:" + signed.getExpression() + "(" + signed.getSign() + ")");
		}
		else if (exp instanceof StringValue)
		{
			StringValue sv = (StringValue) exp;
			log.trace("STRG:" + sv.getNotExcapedValue() + " => " + sv.getValue());
		}
		else if (exp instanceof Parenthesis)
		{
			Parenthesis par = (Parenthesis) exp;
			doProcessExpression(par.getExpression(), tblIndex, subselIndex, aliases, constraints);
			log.trace("PRTS:" + par.getExpression());
		}
		else if (exp instanceof IsNullExpression)
		{
			IsNullExpression isNull = (IsNullExpression) exp;
			doProcessExpression(isNull.getLeftExpression(), tblIndex, subselIndex, aliases, constraints);
			log.trace("INUL:" + isNull.getLeftExpression());
		}
		else if (exp instanceof CastExpression)
		{
			CastExpression cast = (CastExpression) exp;
			doProcessExpression(cast.getLeftExpression(), tblIndex, subselIndex, aliases, constraints);
			log.trace("CAST:" + cast.getLeftExpression() + " to " + cast.getType().getDataType());
		}
		else
			error("B004", exp.getClass().toString());
	}
	
	
	/**
	 * Fails the check using the passed error code
	 * @param code
	 * @param params optional values that indicate the error
	 */
	private void error(String code, String ... params)
	{
		log.info("Throwing error " + code);
		String message = "[" + code + "] - ";
		message += errorMap.get(code);
		
		if (params != null)
			message += " (";
		for (String param : params)
			message += param + ",";
		if (params != null)
			message = message.substring(0, message.length() - 1) + ")";
		
		throw new IllegalArgumentException(message);
	}
}

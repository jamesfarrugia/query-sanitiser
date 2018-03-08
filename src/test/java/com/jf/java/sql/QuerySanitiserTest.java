package com.jf.java.sql;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Unit test for simple App.
 */
public class QuerySanitiserTest
{
	/** CUT */
	private QuerySanitiser sanitser;
	
	/** 
	 * Set up
	 */
	@BeforeEach
	public void setup()
	{
		sanitser = new QuerySanitiser();
	}
	
	/**
	 * Test a blank query, should fail
	 */
	@Test
	public void doTest_Blank_ShouldThrow()
	{
		Executable test = () -> {
			sanitser.doSanitise("", new QueryConstraints());
		};
		
		assertThrows(IllegalArgumentException.class, test, "Blank fails");
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_Insert_ShouldThrow()
	{
		String q = "insert into operations.business_transaction(id) values(1);";
		Executable test = () -> {
			sanitser.doSanitise(q, new QueryConstraints());
		};
		
		assertThrows(IllegalArgumentException.class, test, "Insert fails");
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_SelectFromAllowedTable_ShouldPass()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList("transactions"));
		sanitser.doSanitise("select * from transactions", constraints);
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_SelectFromDisallowedTable_ShouldThrow()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList("transactions"));
		
		Executable test = () -> {
			sanitser.doSanitise("select * from transaction_lines", constraints);
		};
		assertThrows(IllegalArgumentException.class, test, "Table blocked");
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_SelectNow_ShouldPass()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setFunctionWhitelist(Arrays.asList("now"));
		sanitser.doSanitise("select now()", constraints);
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_SelectFromAllowedTableJoinAllowedTable_ShouldPass()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList("transaction", "transaction_line"));
		
		sanitser.doSanitise(
				"select * from transaction "
				+ "join transaction_line on transaction.id = transaction_line.txn", 
				constraints);
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_SelectAliasedFromAllowedTableJoinAllowedTable_ShouldPass()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList("transaction", "transaction_line"));
		
		sanitser.doSanitise(
				"select * from transaction txn "
				+ "join transaction_line ln on txn.id = ln.txn", 
				constraints);
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_SelectWrongAliasedFromAllowedTableJoinAllowedTable_ShouldThrow()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList("transaction", "transaction_line"));
		
		Executable test = () -> {
			sanitser.doSanitise(
					"select * from transaction txn "
					+ "join transaction_line ln on txn.id = lx.txn", 
					constraints);
		};
		assertThrows(IllegalArgumentException.class, test, "Alias blocked");
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_SelectFromAllowedTableJoinDisallowedTable_ShouldThrow()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList("transaction"));
		
		Executable test = () -> {
			sanitser.doSanitise(
					"select * from transaction "
					+ "join transaction_line on transaction.id = transaction_line.txn", 
					constraints);
		};
		assertThrows(IllegalArgumentException.class, test, "Alias blocked");
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_SelectFromAllowedTableJoinSubSelect_ShouldPass()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList("transaction", "transaction_line"));
		
		sanitser.doSanitise(
				"select * from transaction txn "
				+ "join (select * from transaction_line) ln on txn.id = ln.txn", 
				constraints);
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_SelectFromAllowedTableJoinInsertReturn_ShouldThrow()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList("transaction"));
		
		Executable test = () -> {
			sanitser.doSanitise(
					"select * from transaction txn "
					+ "join (insert into transaction_line(id) values(1) returning id) ln on txn.id = ln.txn", 
					constraints);
		};
		assertThrows(IllegalArgumentException.class, test, "Alias blocked");
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_SetVariable_ShouldThrow()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList("transaction"));
		
		Executable test = () -> {
			sanitser.doSanitise(
					"SET SESSION AUTHORIZATION 'regular_user';", 
					constraints);
		};
		assertThrows(IllegalArgumentException.class, test, "Non select blocked");
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_DropTable_ShouldThrow()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList("transaction"));
		
		Executable test = () -> {
			sanitser.doSanitise(
					"DROP TABLE users;", 
					constraints);
		};
		assertThrows(IllegalArgumentException.class, test, "Non select blocked");
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_EmbeddedExecute_ShouldThrow()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList("transaction"));
		constraints.setFunctionWhitelist(Arrays.asList("now"));
		
		Executable test = () -> {
			sanitser.doSanitise(
					"SELECT execute('select now()')", 
					constraints);
		};
		assertThrows(IllegalArgumentException.class, test, "Non select blocked");
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_SelectsAndJoins_ShouldPass()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList(
				"operations.business_transaction", 
				"operations.business_transaction_line",
				"operations.composite",
				"frontoffice.product"));
		constraints.setFunctionWhitelist(Arrays.asList(
				"now"));
		
		sanitser.doSanitise(
				"select DISTINCT fp.name, l.id + 5, l.urid, fp.details->>'qty' from operations.business_transaction t " + 
				"join operations.business_transaction_line l on t.id = l.transaction " + 
				"join operations.composite c on l.product = c.urid " + 
				"join frontoffice.product fp on fp.composite = c.urid " + 
				"join (select now()) " + 
				"where fp.category ='t1' and operations.business_transaction.urid = 'xtz' and fp.id < 8.8 ", 
				constraints);
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_DeepSelectsAndJoins_ShouldPass()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList(
				"operations.business_transaction", 
				"operations.business_transaction_line",
				"operations.composite",
				"frontoffice.product"));
		constraints.setFunctionWhitelist(Arrays.asList(
				"now"));
		
		sanitser.doSanitise(
				"select DISTINCT g.y, fp.name, l.id + 5, l.urid, fp.details->>'qty' from operations.business_transaction t " + 
				"join operations.business_transaction_line l on t.id = l.transaction " + 
				"join ("
				+ "select * from frontoffice.product dfp "
				+ "join operations.business_transaction_line fpl on dfp.id = fpl.y "
				+ "join ("
				+ "select now(), 1 from operations.business_transaction_line"
				+ ") xx on xx.id = dfp.id) g " + 
				"join frontoffice.product fp on fp.composite = g.urid " + 
				"join (select now()) " + 
				"where fp.category ='t1' and operations.business_transaction.urid = 'xtz' and fp.id < 8.8 ", 
				constraints);
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_CastingAndSums_ShouldPass()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList(
				"transaction", 
				"transaction_line",
				"operations.composite",
				"product"));
		constraints.setFunctionWhitelist(Arrays.asList(
				"now", "sum"));
		
		sanitser.doSanitise(
				"select p.composite, sum(cast(l.line_params->>'quantity' as numeric)) as qty, t.details->>'ship' as ship\n" + 
				"from transaction_line l\n" + 
				"join product p on p.composite = l.product\n" + 
				"join transaction t on l.transaction = t.urid and t.closed is not null and t.type = 'e8033084-b14c-4ecf-aa9d-d838e0516414' and t.void is null\n" + 
				"where p.category = '1bfbcca7-6fa7-4026-9926-93e1443c2b83'\n" + 
				"group by p.composite,ship ", 
				constraints);
	}
	
	/**
	 * Test a valid insert, should fail
	 */
	@Test
	public void doTest_Union_ShouldPass()
	{
		QueryConstraints constraints = new QueryConstraints();
		constraints.setTableWhitelist(Arrays.asList(
				"transaction", 
				"transaction_line",
				"operations.composite",
				"product"));
		constraints.setFunctionWhitelist(Arrays.asList(
				"now", "sum"));
		
		sanitser.doSanitise(
				"select p.composite\n" + 
				"from transaction_line l\n" + 
				"union\n" + 
				"select p.composite\n" + 
				"from transaction_line l\n", 
				constraints);
	}
	
	/*select p.composite, sum(cast(l.line_params->>'quantity' as numeric)) as qty, t.details->>'ship' as ship
from transaction_line l
join product p on p.composite = l.product
join transaction t on l.transaction = t.urid and t.closed is not null and t.type = 'e8033084-b14c-4ecf-aa9d-d838e0516414' and t.void is null
where p.category = '1bfbcca7-6fa7-4026-9926-93e1443c2b83'
group by p.composite,ship;*/
}

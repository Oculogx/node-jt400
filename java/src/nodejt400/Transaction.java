package nodejt400;

import java.sql.Connection;

public class Transaction implements ConnectionProvider {

	private final ConnectionProvider connectionProvider;
	private final Connection connection;

	private final JdbcJsonClient client;

	public Transaction(ConnectionProvider connectionProvider) throws Exception {
		this.connectionProvider = connectionProvider;
		this.connection = connectionProvider.getConnection();
		this.connection.setAutoCommit(false);
		this.client = new JdbcJsonClient(this);
	}

	public void commit() throws Exception {
		this.connection.commit();
	}

	public void rollback() throws Exception {
		this.connection.rollback();
	}

	public void end() throws Exception {
		try {
			this.connection.setAutoCommit(true);
		} catch (Exception e) {
			// The connection is likely broken (e.g. socket timeout). Don't
			// rethrow: end() runs in the caller's finally, so an error here
			// would mask the original failure — and the connection must still
			// be returned so the pool can pretest and discard it.
			System.out.println("[node-jt400] Failed to reset autocommit on transaction end: " + e);
		}
		this.connectionProvider.returnConnection(this.connection);
	}

	public String query(String sql, String paramsJson, boolean trim)
			throws Exception {
		return client.query(sql, paramsJson, trim);
	}

	public String query(String sql, String paramsJson, boolean trim, int queryTimeoutSeconds)
			throws Exception {
		return client.query(sql, paramsJson, trim, queryTimeoutSeconds);
	}

	public ResultStream queryAsStream(String sql, String paramsJson,
			int bufferSize) throws Exception {
		return client.queryAsStream(sql, paramsJson, bufferSize);
	}

	public StatementWrap execute(String sql, String paramsJson)
			throws Exception {
		return client.execute(sql, paramsJson);
	}

	public TablesReadStream getTablesAsStream(String catalog, String schema, String table) throws Exception {
		return client.getTablesAsStream(catalog, schema, table);
	}

	public String getColumns(String catalog, String schema, String tableNamePattern, String columnNamePattern)
			throws Exception {
		return client.getColumns(catalog, schema, tableNamePattern, columnNamePattern);
	}

	public int update(String sql, String paramsJson)
			throws Exception {
		return client.update(sql, paramsJson);
	}

	public int update(String sql, String paramsJson, int queryTimeoutSeconds)
			throws Exception {
		return client.update(sql, paramsJson, queryTimeoutSeconds);
	}

	public double insertAndGetId(String sql, String paramsJson)
			throws Exception {
		return client.insertAndGetId(sql, paramsJson);
	}

	public int[] batchUpdate(String sql, String paramsListJson)
			throws Exception {
		return client.batchUpdate(sql, paramsListJson);
	}

	@Override
	public Connection getConnection() throws Exception {
		return connection;
	}

	@Override
	public void returnConnection(Connection c) throws Exception {
	}

	@Override
	public int getQueryTimeout() {
		return connectionProvider.getQueryTimeout();
	}

	@Override
	public void close() {
		try {
			connectionProvider.returnConnection(connection);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}

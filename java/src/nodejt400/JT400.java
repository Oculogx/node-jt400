package nodejt400;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCConnectionHandle;
import com.ibm.as400.access.AS400JDBCConnectionPool;
import com.ibm.as400.access.AS400JDBCConnectionPoolDataSource;
import com.ibm.as400.access.AS400JDBCDriver;
import com.ibm.as400.access.IFSFile;

public class JT400 {
	private final ConnectionProvider connectionProvider;

	private final JdbcJsonClient client;

	public JT400(ConnectionProvider connectionProvider) {
		this.connectionProvider = connectionProvider;
		this.client = new JdbcJsonClient(connectionProvider);
	}

	public static final JT400 createConnection(String jsonConf)
			throws Exception {
		JSONObject conf = (JSONObject) JSONValue.parse(jsonConf);
		return new JT400(new SimpleConnection(conf));
	}

	public static final JT400 createPool(String jsonConf) {
		JSONObject conf = (JSONObject) JSONValue.parse(jsonConf);
		return new JT400(new Pool(conf));
	}

	/**
	 * Parses the optional "query timeout" config value (in SECONDS). Returns 0
	 * (disabled) when absent or unparseable.
	 *
	 * The cancel this enables only reaches a server that is alive but slow or
	 * lock-blocked (it is delivered over a separate connection). Pair it with
	 * the "socket timeout" JDBC property (MILLISECONDS) as a backstop for
	 * unreachable-host scenarios, keeping query timeout below socket timeout
	 * so the graceful cancel fires first.
	 */
	static int parseQueryTimeout(JSONObject conf) {
		return parsePositiveIntConfig(conf, "query timeout");
	}

	/**
	 * Parses an optional integer config value. Returns 0 when the key is
	 * absent; warns and returns 0 when unparseable so a typo degrades to the
	 * previous behavior loudly instead of silently.
	 */
	static int parsePositiveIntConfig(JSONObject conf, String key) {
		Object raw = conf.get(key);
		if (raw == null) {
			return 0;
		}
		try {
			return Integer.parseInt(raw.toString().trim());
		} catch (NumberFormatException e) {
			System.out.println("[node-jt400] Ignoring invalid \"" + key + "\" config value: " + raw);
			return 0;
		}
	}

	public String query(String sql, String paramsJson, boolean trim)
			throws Exception {
		return client.query(sql, paramsJson, trim);
	}

	public ResultStream queryAsStream(String sql, String paramsJson,
			int bufferSize) throws Exception {
		return client.queryAsStream(sql, paramsJson, bufferSize);
	}

	public int[] batchUpdate(String sql, String paramsListJson)
			throws Exception {
		return client.batchUpdate(sql, paramsListJson);
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

	public String getPrimaryKeys(String catalog, String schema, String table)
			throws Exception {
		return client.getPrimaryKeys(catalog, schema, table);
	}

	public int update(String sql, String paramsJson)
			throws Exception {
		return client.update(sql, paramsJson);
	}

	public double insertAndGetId(String sql, String paramsJson)
			throws Exception {
		return client.insertAndGetId(sql, paramsJson);
	}

	public Transaction createTransaction() throws Exception {
		return new Transaction(connectionProvider);
	}

	public Pgm pgm(String programName, String paramsSchemaJsonStr, String libraryName, Integer ccsid) {
		return new Pgm(connectionProvider, programName, paramsSchemaJsonStr, libraryName, ccsid);
	}

	public MessageQ openMessageQ(String name, Boolean isPath) throws Exception {
		return new MessageQ(connectionProvider, name, isPath);
	}

	public KeyedDataQ createKeyedDataQ(String name) throws Exception {
		return new KeyedDataQ(connectionProvider, name);
	}

	public MessageFileHandler openMessageFile(String path) throws Exception {
		return new MessageFileHandler(connectionProvider, path);
	}

	public IfsReadStream createIfsReadStream(String fileName) throws Exception {
		return new IfsReadStream(connectionProvider, fileName);
	}

	public IfsWriteStream createIfsWriteStream(String folderPath, String fileName, boolean append, Integer ccsid)
			throws Exception {
		return new IfsWriteStream(connectionProvider, folderPath, fileName, append, ccsid);
	}

	public boolean deleteIfsFile(String fileName) throws Exception {
		Connection connection = connectionProvider.getConnection();
		AS400JDBCConnectionHandle handle = (AS400JDBCConnectionHandle) connection;
		AS400 as400 = handle.getSystem();
		IFSFile file = new IFSFile(as400, fileName);
		boolean res = file.delete();
		connectionProvider.returnConnection(connection);
		return res;
	}

	public String getIfsFileMetadata(String fileName) throws Exception {
		Connection connection = connectionProvider.getConnection();
		AS400JDBCConnectionHandle handle = (AS400JDBCConnectionHandle) connection;
		AS400 as400 = handle.getSystem();
		IFSFile file = new IFSFile(as400, fileName);
		JSONObject metadata = new JSONObject();
		metadata.put("length", file.length());
		metadata.put("exists", file.exists());
		connectionProvider.returnConnection(connection);
		return metadata.toJSONString();
	}

	public void close() {
		connectionProvider.close();
	}

}

class SimpleConnection implements ConnectionProvider {
	private final Connection connection;
	private final int queryTimeout;

	public SimpleConnection(JSONObject jsonConf)
			throws Exception {
		Properties connectionProps = new Properties();
		connectionProps.putAll(jsonConf);

		this.queryTimeout = JT400.parseQueryTimeout(jsonConf);
		connectionProps.remove("query timeout");
		if (this.queryTimeout > 0 && !connectionProps.containsKey("query timeout mechanism")) {
			connectionProps.setProperty("query timeout mechanism", "cancel");
		}

		DriverManager.registerDriver(new AS400JDBCDriver());
		connection = DriverManager.getConnection("jdbc:as400://" + jsonConf.get("host"), connectionProps);
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
		return queryTimeout;
	}

	@Override
	public void close() {
		try {
			connection.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}

class Pool implements ConnectionProvider {
	private final AS400JDBCConnectionPool sqlPool;
	private final long logConnectionTimeThreshold;
	private final int queryTimeout;

	public Pool(JSONObject jsonConf) {
		Properties connectionProps = new Properties();
		connectionProps.putAll(jsonConf);
		connectionProps.remove("host");
		connectionProps.remove("user");
		connectionProps.remove("password");
		// Not a JDBC property; consumed below via setMaxConnections. The
		// driver ignores unknown keys, but keep the properties clean anyway.
		connectionProps.remove("connectionLimit");

		this.queryTimeout = JT400.parseQueryTimeout(jsonConf);
		connectionProps.remove("query timeout");
		if (this.queryTimeout > 0 && !connectionProps.containsKey("query timeout mechanism")) {
			connectionProps.setProperty("query timeout mechanism", "cancel");
		}

		String conTimeThresshold = System.getenv("LOG_CONNECTION_TIME_THRESHOLD");
		if (conTimeThresshold == null) {
			conTimeThresshold = "10000";
		}
		logConnectionTimeThreshold = Long.parseLong(conTimeThresshold);

		AS400JDBCConnectionPoolDataSource ds = new AS400JDBCConnectionPoolDataSource();
		ds.setServerName((String) jsonConf.get("host"));
		ds.setUser((String) jsonConf.get("user"));
		ds.setPassword((String) jsonConf.get("password"));
		ds.setProperties(connectionProps);

        try {
			if(jsonConf.containsKey("login timeout")) {
				int timeout = Integer.parseInt((String) jsonConf.get("login timeout"));
				ds.setLoginTimeout(timeout);
			}
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        this.sqlPool = new AS400JDBCConnectionPool(ds);
		this.sqlPool.setPretestConnections(true);
		this.sqlPool.setRunMaintenance(true);

		// Historically "connectionLimit" was accepted in the config but never
		// applied, leaving the pool unbounded. Honor it when present. NOTE:
		// once the limit is reached, getConnection() throws
		// ConnectionPoolException(MAX_CONNECTIONS_REACHED) rather than queuing.
		int connectionLimit = JT400.parsePositiveIntConfig(jsonConf, "connectionLimit");
		if (connectionLimit > 0) {
			this.sqlPool.setMaxConnections(connectionLimit);
			System.out.println("[node-jt400] Pool max connections: " + connectionLimit);
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.out.println("close connectionpool.");
				sqlPool.close();
			}
		});
	}

	@Override
	public Connection getConnection() throws Exception {
		long t = System.currentTimeMillis();
		Connection c = sqlPool.getConnection();

		t = System.currentTimeMillis() - t;
		if (t >= logConnectionTimeThreshold) {
			System.out.println("Connect time: " + t);
		}
		return c;
	}

	@Override
	public void returnConnection(Connection c) throws Exception {
		try {
			c.close();
		} catch (Exception e) {
			// Never let a failed return mask the caller's original exception.
			// Pretesting/maintenance will discard a broken connection.
			System.out.println("[node-jt400] Failed to return connection to pool: " + e);
		}
	}

	@Override
	public int getQueryTimeout() {
		return queryTimeout;
	}

	@Override
	public void close() {
		sqlPool.close();
	}
}

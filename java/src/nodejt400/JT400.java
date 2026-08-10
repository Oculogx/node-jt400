package nodejt400;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.HashSet;
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
		return (int) parseLongConfig(conf, key, 0);
	}

	/**
	 * Parses an optional long config value. Returns the fallback when the key
	 * is absent; warns and returns the fallback when unparseable.
	 */
	static long parseLongConfig(JSONObject conf, String key, long fallback) {
		Object raw = conf.get(key);
		if (raw == null) {
			return fallback;
		}
		try {
			return Long.parseLong(raw.toString().trim());
		} catch (NumberFormatException e) {
			System.out.println("[node-jt400] Ignoring invalid \"" + key + "\" config value: " + raw);
			return fallback;
		}
	}

	/**
	 * Parses an optional boolean config value ("true"/"false", any casing, or
	 * JSON booleans). Returns the fallback when absent; warns and returns the
	 * fallback when unparseable.
	 */
	static boolean parseBooleanConfig(JSONObject conf, String key, boolean fallback) {
		Object raw = conf.get(key);
		if (raw == null) {
			return fallback;
		}
		String value = raw.toString().trim().toLowerCase();
		if (value.equals("true")) {
			return true;
		}
		if (value.equals("false")) {
			return false;
		}
		System.out.println("[node-jt400] Ignoring invalid \"" + key + "\" config value: " + raw);
		return fallback;
	}

	/**
	 * Copies the JSON config into Properties, coercing every value to a
	 * String. The driver reads values via Properties.getProperty(), which
	 * returns null for non-String values — so without coercion a JSON number
	 * (e.g. "socket timeout": 45000) would be silently dropped.
	 */
	static Properties toProperties(JSONObject conf) {
		Properties props = new Properties();
		for (Object key : conf.keySet()) {
			Object value = conf.get(key);
			if (key != null && value != null) {
				props.setProperty(key.toString(), value.toString());
			}
		}
		return props;
	}

	/**
	 * Best-effort validation: warns for config keys the JDBC driver does not
	 * recognize, because the driver ignores unknown properties silently (a
	 * misspelled option would otherwise just not take effect).
	 */
	static void warnUnknownProperties(Properties props, String host) {
		try {
			DriverPropertyInfo[] known = new AS400JDBCDriver()
					.getPropertyInfo("jdbc:as400://" + host, new Properties());
			HashSet<String> names = new HashSet<String>();
			for (DriverPropertyInfo info : known) {
				names.add(info.name);
			}
			for (Object key : props.keySet()) {
				if (!names.contains(key.toString())) {
					System.out.println("[node-jt400] Config option not recognized by the JDBC driver (ignored): " + key);
				}
			}
		} catch (Exception e) {
			// Validation is best-effort — never fail pool creation over it.
			System.out.println("[node-jt400] Config validation skipped (could not read driver properties): " + e);
		}
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

	public int update(String sql, String paramsJson, int queryTimeoutSeconds)
			throws Exception {
		return client.update(sql, paramsJson, queryTimeoutSeconds);
	}

	/**
	 * Point-in-time pool counters for observability. Returns {} for
	 * non-pooled providers.
	 */
	public String getPoolStats() {
		if (connectionProvider instanceof Pool) {
			return ((Pool) connectionProvider).getStatsJson();
		}
		return "{}";
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
		Properties connectionProps = JT400.toProperties(jsonConf);

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
	// Wrapper-level pool options (values in ms unless noted), not JDBC
	// properties. Consumed in the constructor and stripped from the
	// properties handed to the driver.
	private static final String[] POOL_OPTION_KEYS = {
		"pool cleanup interval",
		"pool max inactivity",
		"pool max lifetime",
		"pool max use count", // count, not ms
		"pool max use time",
		"pool pretest connections", // boolean
		"pool run maintenance", // boolean
		"pool thread used", // boolean
	};

	private final AS400JDBCConnectionPool sqlPool;
	private final long logConnectionTimeThreshold;
	private final int queryTimeout;

	public Pool(JSONObject jsonConf) {
		Properties connectionProps = JT400.toProperties(jsonConf);
		connectionProps.remove("host");
		connectionProps.remove("user");
		connectionProps.remove("password");
		// Not a JDBC property; consumed below via setMaxConnections. The
		// driver ignores unknown keys, but keep the properties clean anyway.
		connectionProps.remove("connectionLimit");
		for (String poolKey : POOL_OPTION_KEYS) {
			connectionProps.remove(poolKey);
		}

		this.queryTimeout = JT400.parseQueryTimeout(jsonConf);
		connectionProps.remove("query timeout");
		if (this.queryTimeout > 0 && !connectionProps.containsKey("query timeout mechanism")) {
			connectionProps.setProperty("query timeout mechanism", "cancel");
		}

		JT400.warnUnknownProperties(connectionProps, (String) jsonConf.get("host"));

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
		// setThreadUsed must run before the pool is in use; keep it first.
		if (jsonConf.containsKey("pool thread used")) {
			this.sqlPool.setThreadUsed(JT400.parseBooleanConfig(jsonConf, "pool thread used", true));
		}
		this.sqlPool.setPretestConnections(JT400.parseBooleanConfig(jsonConf, "pool pretest connections", true));
		this.sqlPool.setRunMaintenance(JT400.parseBooleanConfig(jsonConf, "pool run maintenance", true));

		long cleanupInterval = JT400.parseLongConfig(jsonConf, "pool cleanup interval", 0);
		if (cleanupInterval > 0) {
			this.sqlPool.setCleanupInterval(cleanupInterval);
		}
		long maxInactivity = JT400.parseLongConfig(jsonConf, "pool max inactivity", 0);
		if (maxInactivity > 0) {
			this.sqlPool.setMaxInactivity(maxInactivity);
		}
		long maxLifetime = JT400.parseLongConfig(jsonConf, "pool max lifetime", 0);
		if (maxLifetime > 0) {
			this.sqlPool.setMaxLifetime(maxLifetime);
		}
		int maxUseCount = JT400.parsePositiveIntConfig(jsonConf, "pool max use count");
		if (maxUseCount > 0) {
			this.sqlPool.setMaxUseCount(maxUseCount);
		}
		long maxUseTime = JT400.parseLongConfig(jsonConf, "pool max use time", 0);
		if (maxUseTime > 0) {
			this.sqlPool.setMaxUseTime(maxUseTime);
		}

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

	/**
	 * Point-in-time pool counters for observability.
	 */
	public String getStatsJson() {
		JSONObject stats = new JSONObject();
		stats.put("activeConnections", sqlPool.getActiveConnectionCount());
		stats.put("availableConnections", sqlPool.getAvailableConnectionCount());
		stats.put("maxConnections", sqlPool.getMaxConnections());
		return stats.toJSONString();
	}

	@Override
	public void close() {
		sqlPool.close();
	}
}

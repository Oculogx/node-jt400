package nodejt400;

import java.sql.Connection;

public interface ConnectionProvider
{
	Connection getConnection() throws Exception;

	void returnConnection(Connection c) throws Exception;

	/**
	 * Query timeout in seconds applied to every statement created by
	 * JdbcJsonClient. 0 disables it (default). Requires the connection's
	 * "query timeout mechanism" to be "cancel" for it to interrupt a running
	 * / lock-blocked query rather than only estimated-runtime queries.
	 */
	int getQueryTimeout();

  void close();
}

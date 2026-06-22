package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;

@Immutable
class Migration50_51 implements Migration<Connection> {

	private static final Logger LOG = getLogger(Migration50_51.class.getName());

	private final DatabaseTypes dbTypes;

	Migration50_51(DatabaseTypes dbTypes) {
		this.dbTypes = dbTypes;
	}

	@Override
	public int getStartVersion() {
		return 50;
	}

	@Override
	public int getEndVersion() {
		return 51;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			s.execute(dbTypes.replaceTypes("CREATE TABLE callLogs"
					+ " (callId _COUNTER,"
					+ " contactId INT NOT NULL,"
					+ " timestamp BIGINT NOT NULL,"
					+ " duration BIGINT NOT NULL,"
					+ " type INT NOT NULL,"
					+ " video BOOLEAN NOT NULL,"
					+ " PRIMARY KEY (callId),"
					+ " FOREIGN KEY (contactId)"
					+ " REFERENCES contacts (contactId)"
					+ " ON DELETE CASCADE)"));
			s.close();
		} catch (SQLException e) {
			JdbcUtils.tryToClose(s, LOG, WARNING);
			throw new DbException(e);
		}
	}
}

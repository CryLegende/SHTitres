package org.Cry_Legende.sHTitres.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.Cry_Legende.sHTitres.config.Settings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

/**
 * Table shtitres_players : un titre par joueur. Toutes les méthodes bloquent, à appeler hors du thread principal.
 */
public final class Storage implements AutoCloseable {

    /** Titre enregistré d'un joueur. */
    public record Choice(String titleId, boolean variant) {
    }

    private final HikariDataSource dataSource;

    public Storage(Settings.Database database) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("SHTitres");
        // Source de données du pilote (chargé par libraries:) : DriverManager ne le voit pas depuis ce classloader
        config.setDataSourceClassName("org.mariadb.jdbc.MariaDbDataSource");
        config.addDataSourceProperty("url", "jdbc:mariadb://" + database.host() + ":" + database.port() + "/" + database.name());
        config.setUsername(database.username());
        config.setPassword(database.password());
        config.setMaximumPoolSize(database.poolSize());
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setMaxLifetime(1_800_000);
        this.dataSource = new HikariDataSource(config);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS shtitres_players ("
                    + "uuid CHAR(36) NOT NULL PRIMARY KEY,"
                    + "title VARCHAR(64) NOT NULL,"
                    + "variant TINYINT(1) NOT NULL DEFAULT 0,"
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") CHARACTER SET utf8mb4");
        } catch (SQLException e) {
            dataSource.close();
            throw e;
        }
    }

    public Choice load(UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT title, variant FROM shtitres_players WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new Choice(result.getString(1), result.getBoolean(2)) : null;
            }
        }
    }

    public void save(UUID uuid, Choice choice) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO shtitres_players (uuid, title, variant) VALUES (?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE title = VALUES(title), variant = VALUES(variant)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, choice.titleId());
            statement.setBoolean(3, choice.variant());
            statement.executeUpdate();
        }
    }

    public void delete(UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM shtitres_players WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }

    /**
     * Ajoute des titres sans toucher aux joueurs qui en ont déjà un.
     *
     * @return nombre de lignes ajoutées
     */
    public int insertMissing(Map<UUID, Choice> choices) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT IGNORE INTO shtitres_players (uuid, title, variant) VALUES (?, ?, ?)")) {
            int before = count(connection);
            for (Map.Entry<UUID, Choice> entry : choices.entrySet()) {
                statement.setString(1, entry.getKey().toString());
                statement.setString(2, entry.getValue().titleId());
                statement.setBoolean(3, entry.getValue().variant());
                statement.addBatch();
            }
            statement.executeBatch();
            return count(connection) - before;
        }
    }

    private static int count(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM shtitres_players")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}

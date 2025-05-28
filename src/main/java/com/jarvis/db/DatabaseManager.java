package com.jarvis.db;

/*
 * Handles SQLite database operations for:
 * - chat persistence
 * - message history
 * - session management
 */

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages SQLite database for conversation history, chat sessions,
 * and command usage tracking.
 */
public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:jarvis.db";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private Connection connection;

    public DatabaseManager() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(DB_URL);
            initializeTables();
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
        }
    }

    private void initializeTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS chats (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  title TEXT," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS messages (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  chat_id INTEGER," +
                "  role TEXT," +
                "  content TEXT," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS command_usage (" +
                "  command TEXT PRIMARY KEY," +
                "  count INTEGER NOT NULL DEFAULT 0," +
                "  last_used TEXT NOT NULL" +
                ")"
            );

            // ── Migration: add mode column if it doesn't exist yet ──
            try {
                stmt.execute("ALTER TABLE messages ADD COLUMN mode TEXT DEFAULT 'sarcastic'");
            } catch (SQLException ignored) {
                // Column already exists — safe to ignore
            }
        }
    }

    // ══════════════════════════════════════
    //  Chat + Message Operations
    // ══════════════════════════════════════

    public static class ChatSummary {
        private final int id;
        private final String title;

        public ChatSummary(int id, String title) {
            this.id = id;
            this.title = title;
        }

        public int getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }
    }

    public static class MessageRecord {
        private final String role;
        private final String content;
        private final String mode;

        public MessageRecord(String role, String content, String mode) {
            this.role = role;
            this.content = content;
            this.mode = mode;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        public String getMode() {
            return mode != null ? mode : "sarcastic";
        }
    }

    public int createChat(String title) {
        String sql = "INSERT INTO chats (title) VALUES (?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (title == null || title.trim().isEmpty()) {
                pstmt.setNull(1, Types.VARCHAR);
            } else {
                pstmt.setString(1, title.trim());
            }
            pstmt.executeUpdate();
            ResultSet keys = pstmt.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Failed to create chat: " + e.getMessage());
        }
        return -1;
    }

    public List<ChatSummary> getAllChats() {
        List<ChatSummary> chats = new ArrayList<>();
        String sql = "SELECT id, COALESCE(title, 'New chat') AS title FROM chats ORDER BY created_at DESC";
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                chats.add(new ChatSummary(
                    rs.getInt("id"),
                    rs.getString("title")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch chats: " + e.getMessage());
        }
        return chats;
    }

    /** Backward-compatible overload — defaults to sarcastic mode. */
    public void saveMessage(int chatId, String role, String content) {
        saveMessage(chatId, role, content, "sarcastic");
    }

    public void saveMessage(int chatId, String role, String content, String mode) {
        String sql = "INSERT INTO messages (chat_id, role, content, mode) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, chatId);
            pstmt.setString(2, role);
            pstmt.setString(3, content);
            pstmt.setString(4, mode != null ? mode : "sarcastic");
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to save message: " + e.getMessage());
        }

        if ("user".equalsIgnoreCase(role)) {
            updateTitleIfEmpty(chatId, content);
        }
    }
// Multi-chat database operations
    public List<MessageRecord> getMessages(int chatId) {
        List<MessageRecord> history = new ArrayList<>();
        String sql = "SELECT role, content, mode FROM messages WHERE chat_id = ? ORDER BY id ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                history.add(new MessageRecord(
                    rs.getString("role"),
                    rs.getString("content"),
                    rs.getString("mode")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch chat messages: " + e.getMessage());
        }
        return history;
    }

    private void updateTitleIfEmpty(int chatId, String content) {
        String currentTitle = null;
        try (PreparedStatement pstmt = connection.prepareStatement(
            "SELECT title FROM chats WHERE id = ?")) {
            pstmt.setInt(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                currentTitle = rs.getString("title");
            }
        } catch (SQLException e) {
            System.err.println("Failed to read chat title: " + e.getMessage());
            return;
        }

        if (currentTitle != null && !currentTitle.trim().isEmpty() && !"New chat".equalsIgnoreCase(currentTitle.trim())) {
            return;
        }

        String title = content == null ? "" : content.trim();
        if (title.length() > 30) {
            title = title.substring(0, 30);
        }

        if (title.isEmpty()) {
            return;
        }

        try (PreparedStatement pstmt = connection.prepareStatement(
            "UPDATE chats SET title = ? WHERE id = ?")) {
            pstmt.setString(1, title);
            pstmt.setInt(2, chatId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to update chat title: " + e.getMessage());
        }
    }

    /**
     * Permanently deletes a chat and all its messages.
     * Messages are deleted first to avoid orphaned rows.
     *
     * @param chatId the chat to delete
     * @return true if the chat existed and was removed
     */
    // Removes chat sessions and associated message history
    public boolean deleteChat(int chatId) {
        try {
            // Delete messages first (no orphans)
            try (PreparedStatement pstmt = connection.prepareStatement(
                "DELETE FROM messages WHERE chat_id = ?")) {
                pstmt.setInt(1, chatId);
                pstmt.executeUpdate();
            }

            // Delete the chat row
            try (PreparedStatement pstmt = connection.prepareStatement(
                "DELETE FROM chats WHERE id = ?")) {
                pstmt.setInt(1, chatId);
                int affected = pstmt.executeUpdate();
                return affected > 0;
            }
        } catch (SQLException e) {
            System.err.println("Failed to delete chat: " + e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════
    //  Command Usage (unchanged)
    // ══════════════════════════════════════

    public void incrementCommandUsage(String command) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String sql = "INSERT INTO command_usage (command, count, last_used) VALUES (?, 1, ?) " +
                     "ON CONFLICT(command) DO UPDATE SET count = count + 1, last_used = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, command);
            pstmt.setString(2, timestamp);
            pstmt.setString(3, timestamp);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to update command usage: " + e.getMessage());
        }
    }

    public int getCommandCount(String command) {
        String sql = "SELECT count FROM command_usage WHERE command = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, command);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            System.err.println("Failed to get command count: " + e.getMessage());
        }
        return 0;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database: " + e.getMessage());
        }
    }
}

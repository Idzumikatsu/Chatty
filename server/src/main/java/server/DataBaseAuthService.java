package server;

import java.sql.*;

public class DataBaseAuthService implements AuthService {

    private static Connection connection;
    private static Statement stmt;

    private static final String REG_NEW_USER = "INSERT INTO users_table (login, password, nickname) VALUES (?, ?, ?);";
    private static final String AUTH = "SELECT * FROM users_table WHERE login = ? AND password = ?;";
    private static final String USER_LOG_NICK_SET = "SELECT login, nickname FROM users_table;";
    private static final String USER_NICK_SET = "SELECT nickname FROM users_table;";
    private static final String UPGRADE_NICK = "UPDATE users_table SET nickname = ? WHERE nickname = ?;";

    private static String login;
    private static String password;
    private static String nickname;

    public DataBaseAuthService() {
        connect();
    }

    public static void connect(){
        if (connection == null) {
            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:users.db");
                stmt = connection.createStatement();
            } catch (SQLException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public static void disconnect() {
        try {
            stmt.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        try {
            connection.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

    }

    public static void setValues(ResultSet resultSet) {
        try {
            connect();
            if (resultSet.next()) {
                login = resultSet.getString("login");
                password = resultSet.getString("password");
                nickname = resultSet.getString("nickname");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getNicknameByLoginAndPassword(String login, String password) {
        try {
            connect();
            PreparedStatement preparedStatement = connection.prepareStatement(AUTH);
            preparedStatement.setString(1, login);
            preparedStatement.setString(2, password);
            ResultSet resultSet = preparedStatement.executeQuery();
            setValues(resultSet);

            if (login.equals(resultSet.getString("login")) && password.equals(resultSet.getString("password"))) {
                return resultSet.getString("nickname");
            }
            preparedStatement.execute();
            preparedStatement.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean registration(String login, String password, String nickname) {
        try {
            connect();
            PreparedStatement preparedStatement = connection.prepareStatement(REG_NEW_USER);
            preparedStatement.setString(1, login);
            preparedStatement.setString(2, password);
            preparedStatement.setString(3, nickname);
            ResultSet rs = stmt.executeQuery(USER_LOG_NICK_SET);
            while (rs.next()) {
                String tabLogin = rs.getString("login");
                String tabNickname = rs.getString("nickname");
                if (login.equals(tabLogin) || nickname.equals(tabNickname)) {
                    return false;
                }
            }
            rs.close();
            preparedStatement.execute();
            preparedStatement.close();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }


    @Override
    public boolean changeNickname(String oldNickname, String newNickname) {
        try {
            connect();
            PreparedStatement preparedStatement = connection.prepareStatement(UPGRADE_NICK);
            preparedStatement.setString(1, newNickname.trim());
            preparedStatement.setString(2, oldNickname.trim());
            preparedStatement.execute();
            ResultSet rs = stmt.executeQuery(USER_NICK_SET);
            while (rs.next()) {
                String tabNickname = rs.getString("nickname");
                if (!oldNickname.equals(tabNickname) && !(newNickname.equals(oldNickname))) {
                    stmt.executeUpdate(UPGRADE_NICK);
                    rs.close();
                    preparedStatement.close();
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}

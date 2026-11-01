package quizgame.server.service;

import quizgame.server.dao.Account;
import quizgame.server.dao.AccountDao;
import quizgame.server.dao.Database;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Pattern;

/** Đăng ký và xác thực thông tin tài khoản. */
public final class AuthService {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,20}");
    private static final String DUMMY_HASH = new PasswordHasher().hash("dummy-password-not-valid".toCharArray());

    private final Database database;
    private final AccountDao accounts;
    private final PasswordHasher hasher;

    public AuthService(Database database, AccountDao accounts, PasswordHasher hasher) {
        this.database = database;
        this.accounts = accounts;
        this.hasher = hasher;
    }

    public static boolean validUsername(String username) {
        return username != null && USERNAME.matcher(username).matches();
    }

    public static boolean validPassword(String password) {
        return password != null && password.length() >= 6 && password.length() <= 64;
    }

    public static boolean validPassword(char[] password) {
        return password != null && password.length >= 6 && password.length <= 64;
    }

    public void register(String username, char[] password) {
        if (!validUsername(username) || !validPassword(password)) {
            throw new ServiceException("INVALID_INPUT", "Tên đăng nhập hoặc mật khẩu không hợp lệ");
        }
        String hash = hasher.hash(password);
        try {
            database.transaction(connection -> accounts.create(connection, username, hash));
        } catch (SQLException ex) {
            if (ex.getSQLState() != null && ex.getSQLState().startsWith("23")) {
                throw new ServiceException("USERNAME_TAKEN", "Tên đăng nhập đã được sử dụng");
            }
            throw new ServiceException("INTERNAL", "Không thể tạo tài khoản lúc này");
        }
    }

    public Account authenticate(String username, char[] password) {
        boolean inputValid = validUsername(username) && validPassword(password);
        Account account = null;
        if (inputValid) {
            try (var connection = database.openConnection()) {
                Optional<Account> found = accounts.findByUsername(connection, username);
                account = found.orElse(null);
            } catch (SQLException ex) {
                throw new ServiceException("INTERNAL", "Không thể đăng nhập lúc này");
            }
        }
        String encoded = account == null ? DUMMY_HASH : account.passwordHash();
        boolean matches = validPassword(password) && hasher.verify(password, encoded);
        if (!inputValid || account == null || !matches) {
            throw new ServiceException("WRONG_CREDENTIALS", "Sai tên đăng nhập hoặc mật khẩu");
        }
        return account;
    }
}
